package chesslab.core

import scala.annotation.tailrec

/**
 * Bitboard-native pseudo-legal move generation.
 *
 * Instead of looping through 120 mailbox squares and testing offsets one at a
 * time, we work with entire piece sets at once via bitwise operations:
 *   1. Get all friendly knights: `bb.knights & friendly`
 *   2. For each (via bit scan), look up the full attack bitboard in one step
 *   3. AND with `~friendly` to exclude self-captures
 *   4. Scan the resulting target bits to emit Move objects
 *
 * Moves are accumulated via List prepend (O(1) per move, no var) and
 * converted to Vector at the end for API compatibility.
 */
object BitMoveGen:

  // =========================================================================
  // Castling path masks — squares between king and rook that must be empty.
  // B1 is included for queenside because the rook passes through it.
  // =========================================================================
  private val WhiteKingsidePath  = (1L << 5) | (1L << 6)                       // F1, G1
  private val WhiteQueensidePath = (1L << 3) | (1L << 2) | (1L << 1)           // D1, C1, B1
  private val BlackKingsidePath  = (1L << 61) | (1L << 62)                     // F8, G8
  private val BlackQueensidePath = (1L << 59) | (1L << 58) | (1L << 57)        // D8, C8, B8

  // =========================================================================
  // Main entry point — generate all pseudo-legal moves for the side to move.
  //
  // "Pseudo-legal" means castling checks king/transit squares, but other
  // moves don't verify whether they leave the king in check. The legality
  // filter (BitLegal) handles that by making the move and checking.
  // =========================================================================

  def pseudoLegalMoves(bb: BitBoard): Vector[Move] =
    val friendly = bb.colorBB(bb.turn)
    val enemy = bb.colorBB(bb.turn.opponent)
    val occ = bb.occupied

    // Knights — fixed jump pattern, occupancy-independent
    val acc1 = generatePieceMoves(bb.knights & friendly,
      sq => Magics.KnightAttacks(sq) & ~friendly, Nil)

    // Bishops — diagonal slider, occupancy-dependent
    val acc2 = generatePieceMoves(bb.bishops & friendly,
      sq => Magics.bishopAttacks(sq, occ) & ~friendly, acc1)

    // Rooks — orthogonal slider, occupancy-dependent
    val acc3 = generatePieceMoves(bb.rooks & friendly,
      sq => Magics.rookAttacks(sq, occ) & ~friendly, acc2)

    // Queens — combined bishop + rook
    val acc4 = generatePieceMoves(bb.queens & friendly,
      sq => Magics.queenAttacks(sq, occ) & ~friendly, acc3)

    // King — fixed jump pattern (castling handled separately)
    val acc5 = generatePieceMoves(bb.kings & friendly,
      sq => Magics.KingAttacks(sq) & ~friendly, acc4)

    // Pawns — pushes, captures, promotions, en passant
    val acc6 = pawnMoves(bb, friendly, enemy, occ, acc5)

    // Castling — king + rook multi-piece move with attack checks
    val acc7 = castlingMoves(bb, occ, acc6)

    acc7.toVector

  // =========================================================================
  // Generic piece-move generator — works for any non-pawn piece.
  //
  // Scans through all set bits in `pieces`, calls `getTargets(sq)` to get
  // the attack/move bitboard for that square, then scans the targets to
  // emit Move objects. All via @tailrec — no var.
  // =========================================================================

  /** For each piece in `pieces`, generate moves to all squares in getTargets(sq). */
  @tailrec
  private def generatePieceMoves(pieces: Long, getTargets: Int => Long, acc: List[Move]): List[Move] =
    if pieces == 0L then acc
    else
      val sq = java.lang.Long.numberOfTrailingZeros(pieces)
      val withMoves = extractMoves(sq, getTargets(sq), acc)
      generatePieceMoves(pieces & (pieces - 1), getTargets, withMoves)

  /** Scan target bits, emitting a simple Move(from, to) for each. */
  @tailrec
  private def extractMoves(from: Int, targets: Long, acc: List[Move]): List[Move] =
    if targets == 0L then acc
    else
      val to = java.lang.Long.numberOfTrailingZeros(targets)
      extractMoves(from, targets & (targets - 1), Move(from, to) :: acc)

  // =========================================================================
  // Pawn move generation
  //
  // Pawns are special: they push forward (not an attack), capture diagonally,
  // double-push from the start rank, promote on the back rank, and capture
  // en passant. Each sub-case uses bulk bitwise shifts:
  //
  //   Single push:  shift all pawns one rank forward, AND with ~occupied
  //   Double push:  shift the rank-3/6 subset forward again, AND with ~occupied
  //   Captures:     shift diagonally, AND with enemy pieces
  //   Promotions:   any move landing on rank 8 (white) or rank 1 (black)
  //   En passant:   diagonal shift intersects the EP square
  //
  // The `offset` parameter derives the source square from each destination:
  //   source = destination + offset
  //   (e.g., white single push: offset = -8, so source = dest - 8)
  // =========================================================================

  private def pawnMoves(bb: BitBoard, friendly: Long, enemy: Long, occ: Long, acc: List[Move]): List[Move] =
    val myPawns = bb.pawns & friendly
    if myPawns == 0L then return acc

    val isWhite = bb.turn == Color.White

    // Direction-dependent constants
    val promoRank = if isWhite then Sq64.Rank8 else Sq64.Rank1

    // Single pushes — shift one rank, blocked by any piece
    val singlePush =
      if isWhite then (myPawns << 8) & ~occ
      else (myPawns >>> 8) & ~occ

    // Double pushes — only pawns that single-pushed to rank 3/6 can double-push
    val doublePush =
      if isWhite then ((singlePush & Sq64.Rank3) << 8) & ~occ
      else ((singlePush & Sq64.Rank6) >>> 8) & ~occ

    // Diagonal captures — shift diagonally, mask file wraps, AND with enemy
    val (capLeft, capRight) =
      if isWhite then
        ( ((myPawns << 7) & ~Sq64.FileH) & enemy,    // NW: file decreases
          ((myPawns << 9) & ~Sq64.FileA) & enemy )    // NE: file increases
      else
        ( ((myPawns >>> 7) & ~Sq64.FileA) & enemy,   // SE: file increases
          ((myPawns >>> 9) & ~Sq64.FileH) & enemy )   // SW: file decreases

    // Source offsets: source = dest + offset
    val (pushOff, dblOff, leftOff, rightOff) =
      if isWhite then (-8, -16, -7, -9)
      else (8, 16, 7, 9)

    // Extract moves from each target bitboard
    val acc1 = extractPawnMoves(singlePush, pushOff, promoRank, acc)
    val acc2 = extractPawnMoves(doublePush, dblOff, 0L, acc1)       // no promos on double push
    val acc3 = extractPawnMoves(capLeft, leftOff, promoRank, acc2)
    val acc4 = extractPawnMoves(capRight, rightOff, promoRank, acc3)

    // En passant captures
    epMoves(bb, myPawns, isWhite, acc4)

  /**
   * Scan pawn target bits, emitting moves. If a target is on the promo rank,
   * emit 4 moves (Q/R/B/N). The offset derives the source: from = to + offset.
   */
  @tailrec
  private def extractPawnMoves(targets: Long, offset: Int, promoRank: Long, acc: List[Move]): List[Move] =
    if targets == 0L then acc
    else
      val to = java.lang.Long.numberOfTrailingZeros(targets)
      val from = to + offset
      val newAcc =
        if ((1L << to) & promoRank) != 0L then
          Move(from, to, Some(PieceId.Queen))  ::
          Move(from, to, Some(PieceId.Rook))   ::
          Move(from, to, Some(PieceId.Bishop)) ::
          Move(from, to, Some(PieceId.Knight)) :: acc
        else
          Move(from, to) :: acc
      extractPawnMoves(targets & (targets - 1), offset, promoRank, newAcc)

  /**
   * En passant captures. At most 2 pawns can capture to the EP square
   * (one from each diagonal). Check each direction independently.
   */
  private def epMoves(bb: BitBoard, myPawns: Long, isWhite: Boolean, acc: List[Move]): List[Move] =
    if bb.epSquare < 0 then acc
    else
      val epBit = 1L << bb.epSquare
      val (leftAttack, rightAttack, leftOff, rightOff) =
        if isWhite then
          ( (myPawns << 7) & ~Sq64.FileH,    // NW targets
            (myPawns << 9) & ~Sq64.FileA,    // NE targets
            -7, -9 )
        else
          ( (myPawns >>> 7) & ~Sq64.FileA,   // SE targets
            (myPawns >>> 9) & ~Sq64.FileH,   // SW targets
            7, 9 )

      val acc1 =
        if (leftAttack & epBit) != 0L then
          Move(bb.epSquare + leftOff, bb.epSquare, flag = MoveFlag.EnPassant) :: acc
        else acc

      if (rightAttack & epBit) != 0L then
        Move(bb.epSquare + rightOff, bb.epSquare, flag = MoveFlag.EnPassant) :: acc1
      else acc1

  // =========================================================================
  // Castling
  //
  // Pseudo-legal with attack checks on king, transit, and destination squares.
  // The path between king and rook must be empty. Unlike other pseudo-legal
  // moves, castling checks attacks here because "castling out of/through
  // check" is never legal — no point generating these moves.
  // =========================================================================

  private def castlingMoves(bb: BitBoard, occ: Long, acc: List[Move]): List[Move] =
    val enemy = bb.turn.opponent

    if bb.turn == Color.White then
      val acc1 =
        if bb.castling.whiteKingside &&
          (occ & WhiteKingsidePath) == 0L &&
          !BitAttacks.isAttacked(4, enemy, bb) &&     // E1 — king not in check
          !BitAttacks.isAttacked(5, enemy, bb) &&     // F1 — transit square safe
          !BitAttacks.isAttacked(6, enemy, bb)        // G1 — destination safe
        then Move(4, 6, flag = MoveFlag.Castling) :: acc
        else acc

      if bb.castling.whiteQueenside &&
        (occ & WhiteQueensidePath) == 0L &&
        !BitAttacks.isAttacked(4, enemy, bb) &&       // E1
        !BitAttacks.isAttacked(3, enemy, bb) &&       // D1
        !BitAttacks.isAttacked(2, enemy, bb)          // C1
      then Move(4, 2, flag = MoveFlag.Castling) :: acc1
      else acc1
    else
      val acc1 =
        if bb.castling.blackKingside &&
          (occ & BlackKingsidePath) == 0L &&
          !BitAttacks.isAttacked(60, enemy, bb) &&    // E8
          !BitAttacks.isAttacked(61, enemy, bb) &&    // F8
          !BitAttacks.isAttacked(62, enemy, bb)       // G8
        then Move(60, 62, flag = MoveFlag.Castling) :: acc
        else acc

      if bb.castling.blackQueenside &&
        (occ & BlackQueensidePath) == 0L &&
        !BitAttacks.isAttacked(60, enemy, bb) &&      // E8
        !BitAttacks.isAttacked(59, enemy, bb) &&      // D8
        !BitAttacks.isAttacked(58, enemy, bb)         // C8
      then Move(60, 58, flag = MoveFlag.Castling) :: acc1
      else acc1
