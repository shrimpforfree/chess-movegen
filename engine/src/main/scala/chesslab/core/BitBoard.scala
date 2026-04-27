package chesslab.core

/**
 * Immutable board state using bitboards.
 *
 * Each piece type gets a Long (64 bits = 64 squares). Each color gets a Long.
 * Combining them gives any piece-color set: `pawns & white` = all white pawns.
 *
 * epSquare uses Sq64 indexing (0..63), or -1 if no en passant is available.
 */
case class BitBoard(
  pawns: Long,
  knights: Long,
  bishops: Long,
  rooks: Long,
  queens: Long,
  kings: Long,
  white: Long,
  black: Long,
  turn: Color,
  castling: CastlingRights,
  epSquare: Int,
  halfmoveClock: Int,
  fullmoveNumber: Int
):

  // =========================================================================
  // Derived occupancy — used everywhere in move gen and attack detection.
  //   occupied = all pieces on the board (pass to Magics slider lookups)
  //   colorBB  = all pieces of one color (mask out friendly for legal moves)
  // =========================================================================

  /** All occupied squares. Pass this to Magics.bishopAttacks/rookAttacks. */
  def occupied: Long = white | black

  /** All pieces of the given color. Use to mask out friendly pieces from attacks. */
  def colorBB(c: Color): Long = if c == Color.White then white else black

  // =========================================================================
  // Piece lookup — used for display, FEN generation, and evaluation.
  // Not performance-critical; checks each bitboard sequentially.
  // =========================================================================

  /** What piece (if any) is on this Sq64 square? Returns (pieceKind, color). */
  def pieceAt(sq: Int): Option[(PieceId, Color)] =
    val bit = 1L << sq
    if (occupied & bit) == 0L then None
    else
      val color = if (white & bit) != 0L then Color.White else Color.Black
      val kind =
        if (pawns & bit) != 0L then PieceId.Pawn
        else if (knights & bit) != 0L then PieceId.Knight
        else if (bishops & bit) != 0L then PieceId.Bishop
        else if (rooks & bit) != 0L then PieceId.Rook
        else if (queens & bit) != 0L then PieceId.Queen
        else PieceId.King
      Some((kind, color))

  // =========================================================================
  // King square — used by legal move checker to test if king is in check.
  // numberOfTrailingZeros is a single CPU instruction (BSF/TZCNT), so
  // deriving it from the bitboard is effectively free.
  // =========================================================================

  /** Sq64 index of the given color's king. */
  def kingSq(c: Color): Int =
    java.lang.Long.numberOfTrailingZeros(kings & colorBB(c))

  // =========================================================================
  // makeMove — apply a move and return the new board.
  //
  // Expects `move.from` and `move.to` in Sq64 coordinates (0..63).
  // Handles normal moves, captures, en passant, castling, and promotion
  // all via bitwise operations on the piece-type and color boards.
  //
  // The core pattern for each piece-type board is:
  //   (board & ~fromBit & ~toBit) | (if dest is this type, toBit, else 0)
  // This clears the origin (piece leaves), clears the destination (captured
  // piece removed), and sets the destination (moving/promoted piece placed).
  // =========================================================================

  def makeMove(move: Move): BitBoard =
    val from = move.from
    val to = move.to
    val fromBit = 1L << from
    val toBit = 1L << to

    // --- Identify the moving piece ---
    val isWhiteMoving = (white & fromBit) != 0L
    val isPawn = (pawns & fromBit) != 0L
    val isKing = (kings & fromBit) != 0L
    val isRookMoving = (rooks & fromBit) != 0L
    val isCapture = (occupied & toBit) != 0L

    // --- En passant: captured pawn is behind the destination, not on it ---
    val epClearBit =
      if move.flag == MoveFlag.EnPassant then 1L << (if isWhiteMoving then to - 8 else to + 8)
      else 0L

    // --- Castling: the rook also moves (king move handled by normal logic) ---
    //   Kingside:  rook from +3 (H-file) to +1 (F-file) relative to king origin
    //   Queenside: rook from -4 (A-file) to -1 (D-file) relative to king origin
    val (rookFromBit, rookToBit) =
      if move.flag == MoveFlag.Castling then
        if to > from then (1L << (from + 3), 1L << (from + 1))    // kingside
        else              (1L << (from - 4), 1L << (from - 1))    // queenside
      else (0L, 0L)
    val rookMoveMask = rookFromBit | rookToBit  // XOR this to flip rook from/to

    // --- What piece type ends up at the destination? ---
    //   Normally the same piece that moved. For promotion, the promo piece.
    val promoKind = move.promo
    val destIsPawn   = isPawn && promoKind.isEmpty
    val destIsKnight = (knights & fromBit) != 0L || promoKind.contains(PieceId.Knight)
    val destIsBishop = (bishops & fromBit) != 0L || promoKind.contains(PieceId.Bishop)
    val destIsRook   = isRookMoving              || promoKind.contains(PieceId.Rook)
    val destIsQueen  = (queens & fromBit) != 0L  || promoKind.contains(PieceId.Queen)
    val destIsKing   = isKing

    // --- Update piece-type boards ---
    //   For each: clear from (piece leaves), clear to (capture), set to (piece arrives).
    //   epClearBit only applies to pawns (only pawns can be captured en passant).
    //   rookMoveMask only applies to rooks (XOR flips the castling rook).
    val newPawns   = (pawns   & ~fromBit & ~toBit & ~epClearBit) | (if destIsPawn   then toBit else 0L)
    val newKnights = (knights & ~fromBit & ~toBit)               | (if destIsKnight then toBit else 0L)
    val newBishops = (bishops & ~fromBit & ~toBit)               | (if destIsBishop then toBit else 0L)
    val newRooks   = ((rooks  & ~fromBit & ~toBit)               | (if destIsRook   then toBit else 0L)) ^ rookMoveMask
    val newQueens  = (queens  & ~fromBit & ~toBit)               | (if destIsQueen  then toBit else 0L)
    val newKings   = (kings   & ~fromBit & ~toBit)               | (if destIsKing   then toBit else 0L)

    // --- Update color boards ---
    //   Mover: clear from (^fromBit), set to (|toBit), flip castling rook (^rookMoveMask).
    //   Opponent: clear captured piece at to (&~toBit), clear EP capture (&~epClearBit).
    val newWhite =
      if isWhiteMoving then ((white ^ fromBit) | toBit) ^ rookMoveMask
      else white & ~toBit & ~epClearBit
    val newBlack =
      if isWhiteMoving then black & ~toBit & ~epClearBit
      else ((black ^ fromBit) | toBit) ^ rookMoveMask

    // --- Castling rights ---
    //   Revoked when a king or rook moves from its home square,
    //   or when a rook is captured on its home square.
    val castling1 =
      if isKing then castling.removeKing(turn)
      else if isRookMoving then
        if from == 7 then castling.removeKingside(Color.White)        // H1
        else if from == 0 then castling.removeQueenside(Color.White)  // A1
        else if from == 63 then castling.removeKingside(Color.Black)  // H8
        else if from == 56 then castling.removeQueenside(Color.Black) // A8
        else castling
      else castling
    val newCastling =
      if to == 7 then castling1.removeKingside(Color.White)        // captured rook on H1
      else if to == 0 then castling1.removeQueenside(Color.White)  // A1
      else if to == 63 then castling1.removeKingside(Color.Black)  // H8
      else if to == 56 then castling1.removeQueenside(Color.Black) // A8
      else castling1

    // --- En passant square ---
    //   Set when a pawn double-pushes (16 squares = 2 ranks in Sq64).
    //   The EP target is the square the pawn skipped over.
    val newEpSquare =
      if isPawn && math.abs(to - from) == 16 then (from + to) / 2
      else -1

    // --- Clocks ---
    val newHalfmoveClock =
      if isPawn || isCapture || move.flag == MoveFlag.EnPassant then 0
      else halfmoveClock + 1
    val newFullmoveNumber =
      if turn == Color.Black then fullmoveNumber + 1 else fullmoveNumber

    BitBoard(
      pawns = newPawns, knights = newKnights, bishops = newBishops,
      rooks = newRooks, queens = newQueens, kings = newKings,
      white = newWhite, black = newBlack,
      turn = turn.opponent,
      castling = newCastling,
      epSquare = newEpSquare,
      halfmoveClock = newHalfmoveClock,
      fullmoveNumber = newFullmoveNumber
    )

  // =========================================================================
  // Debug display — same layout as the existing Board.toString.
  // Uppercase = White, lowercase = Black, '.' = empty.
  // =========================================================================

  override def toString: String =
    val sb = new StringBuilder
    sb.append("  a b c d e f g h\n")
    for rank <- 7 to 0 by -1 do
      sb.append(s"${rank + 1} ")
      for file <- 0 to 7 do
        val sq = rank * 8 + file
        val ch = pieceAt(sq) match
          case Some((kind, color)) =>
            val base =
              if kind == PieceId.Pawn then 'p'
              else if kind == PieceId.Knight then 'n'
              else if kind == PieceId.Bishop then 'b'
              else if kind == PieceId.Rook then 'r'
              else if kind == PieceId.Queen then 'q'
              else 'k'
            if color == Color.White then base.toUpper else base
          case None => '.'
        sb.append(ch)
        if file < 7 then sb.append(' ')
      sb.append('\n')
    sb.append(s"\nSide: ${if turn == Color.White then "w" else "b"}")
    sb.append(s"  Castling: ${castling.toFen}")
    val epStr =
      if epSquare < 0 then "-"
      else s"${('a' + epSquare % 8).toChar}${epSquare / 8 + 1}"
    sb.append(s"  EP: $epStr")
    sb.toString

// ===========================================================================
// Companion — construction helpers
// ===========================================================================
object BitBoard:

  /** Empty board — no pieces, White to move, no castling, no EP. */
  val empty: BitBoard = BitBoard(
    0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
    Color.White, CastlingRights.none, -1, 0, 1
  )

  /**
   * Convert an existing mailbox Board to a BitBoard.
   * Useful as a bridge for testing — lets us reuse FenCodec.boardFromFen
   * to create bitboard positions until FenCodec is updated in a later step.
   *
   * Usage:
   *   val board = FenCodec.boardFromFen(fen).getOrElse(...)
   *   val bb = BitBoard.fromBoard(board)
   */
  def fromBoard(board: Board): BitBoard =
    val base = empty.copy(
      turn = board.sideToMove,
      castling = board.castling,
      epSquare = board.epSquare.map(Sq64.fromMailbox(_)).getOrElse(-1),
      halfmoveClock = board.halfmoveClock,
      fullmoveNumber = board.fullmoveNumber
    )
    (0 until 120).foldLeft(base) { (bb, mbxSq) =>
      board.pieceAt(mbxSq) match
        case Square.Occupied(piece) =>
          val sq64 = Sq64.fromMailbox(mbxSq)
          if sq64 >= 0 then addPiece(bb, sq64, piece.kind, piece.color)
          else bb
        case _ => bb
    }

  /** Set a single piece on the board. Used by fromBoard during construction. */
  private def addPiece(bb: BitBoard, sq: Int, kind: PieceId, color: Color): BitBoard =
    val bit = 1L << sq
    val withType =
      if kind == PieceId.Pawn then bb.copy(pawns = bb.pawns | bit)
      else if kind == PieceId.Knight then bb.copy(knights = bb.knights | bit)
      else if kind == PieceId.Bishop then bb.copy(bishops = bb.bishops | bit)
      else if kind == PieceId.Rook then bb.copy(rooks = bb.rooks | bit)
      else if kind == PieceId.Queen then bb.copy(queens = bb.queens | bit)
      else if kind == PieceId.King then bb.copy(kings = bb.kings | bit)
      else bb
    color match
      case Color.White => withType.copy(white = withType.white | bit)
      case Color.Black => withType.copy(black = withType.black | bit)
