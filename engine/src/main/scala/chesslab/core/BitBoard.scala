package chesslab.core

import scala.annotation.tailrec

/**
 * Immutable board state using bitboards.
 *
 * The 6 standard piece types are named fields (zero-cost access, no array).
 * Custom pieces (index 6+) go in `extras: IArray[Long]` — only allocated
 * when custom pieces are registered. Standard chess has no extras overhead.
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
  fullmoveNumber: Int,
  hash: Long = 0L,
  extras: IArray[Long] = BitBoard.EmptyExtras
):

  // =========================================================================
  // Piece access by index — standard pieces are direct fields, custom use extras
  // =========================================================================

  /** Get piece bitboard by PieceIdx. */
  def pieces(idx: Int): Long = idx match
    case 0 => pawns
    case 1 => knights
    case 2 => bishops
    case 3 => rooks
    case 4 => queens
    case 5 => kings
    case i => if i - 6 < extras.length then extras(i - 6) else 0L

  // =========================================================================
  // Derived occupancy
  // =========================================================================

  def occupied: Long = white | black
  def colorBB(c: Color): Long = if c == Color.White then white else black

  // =========================================================================
  // Piece lookup
  // =========================================================================

  def pieceAt(sq: Int): Option[(PieceId, Color)] =
    val bit = 1L << sq
    if (occupied & bit) == 0L then None
    else
      val color = if (white & bit) != 0L then Color.White else Color.Black
      val idx = pieceIdAt(bit)
      val kind = if idx >= 0 then PieceTypes.indexToId.getOrElse(idx, PieceId.Pawn) else PieceId.Pawn
      Some((kind, color))

  @tailrec
  private def pieceIdAt(bit: Long, idx: Int = 0): Int =
    if idx >= PieceTypes.totalCount then -1
    else if (pieces(idx) & bit) != 0L then idx
    else pieceIdAt(bit, idx + 1)

  // =========================================================================
  // King square
  // =========================================================================

  def kingSq(c: Color): Int =
    java.lang.Long.numberOfTrailingZeros(kings & colorBB(c))

  // =========================================================================
  // makeMove
  // =========================================================================

  def makeMove(move: Move): BitBoard =
    val from = move.from
    val to = move.to
    val fromBit = 1L << from
    val toBit = 1L << to

    val isWhiteMoving = (white & fromBit) != 0L
    val isPawn = (pawns & fromBit) != 0L
    val isKing = (kings & fromBit) != 0L
    val isRookMoving = (rooks & fromBit) != 0L
    val isCapture = (occupied & toBit) != 0L

    val epClearBit =
      if move.flag == MoveFlag.EnPassant then 1L << (if isWhiteMoving then to - 8 else to + 8)
      else 0L

    val (rookFromBit, rookToBit) =
      if move.flag == MoveFlag.Castling then
        if to > from then (1L << (from + 3), 1L << (from + 1))
        else              (1L << (from - 4), 1L << (from - 1))
      else (0L, 0L)
    val rookMoveMask = rookFromBit | rookToBit

    // What piece type ends up at the destination?
    val isPromo = move.promo.isDefined
    val destIsPawn   = isPawn && !isPromo
    val destIsKnight = (knights & fromBit) != 0L || (isPromo && move.promo.get == PieceId.Knight)
    val destIsBishop = (bishops & fromBit) != 0L || (isPromo && move.promo.get == PieceId.Bishop)
    val destIsRook   = isRookMoving              || (isPromo && move.promo.get == PieceId.Rook)
    val destIsQueen  = (queens & fromBit) != 0L  || (isPromo && move.promo.get == PieceId.Queen)
    val destIsKing   = isKing

    // Update each piece board: clear from+to+ep, set dest, flip castling rook
    val np = (pawns   & ~fromBit & ~toBit & ~epClearBit) | (if destIsPawn   then toBit else 0L)
    val nn = (knights & ~fromBit & ~toBit)               | (if destIsKnight then toBit else 0L)
    val nb = (bishops & ~fromBit & ~toBit)               | (if destIsBishop then toBit else 0L)
    val nr = ((rooks  & ~fromBit & ~toBit)               | (if destIsRook   then toBit else 0L)) ^ rookMoveMask
    val nq = (queens  & ~fromBit & ~toBit)               | (if destIsQueen  then toBit else 0L)
    val nk = (kings   & ~fromBit & ~toBit)               | (if destIsKing   then toBit else 0L)

    // Extras: clear from/to/ep, set dest if custom
    val newExtras =
      if extras.length == 0 then extras
      else
        val clearMask = ~fromBit & ~toBit & ~epClearBit
        val movingExtraIdx = pieceIdAt(fromBit) - 6
        IArray.tabulate(extras.length) { i =>
          val cleared = extras(i) & clearMask
          if i == movingExtraIdx then cleared | toBit else cleared
        }

    val newWhite =
      if isWhiteMoving then ((white ^ fromBit) | toBit) ^ rookMoveMask
      else white & ~toBit & ~epClearBit
    val newBlack =
      if isWhiteMoving then black & ~toBit & ~epClearBit
      else ((black ^ fromBit) | toBit) ^ rookMoveMask

    val castling1 =
      if isKing then castling.removeKing(turn)
      else if isRookMoving then
        if from == 7 then castling.removeKingside(Color.White)
        else if from == 0 then castling.removeQueenside(Color.White)
        else if from == 63 then castling.removeKingside(Color.Black)
        else if from == 56 then castling.removeQueenside(Color.Black)
        else castling
      else castling
    val newCastling =
      if to == 7 then castling1.removeKingside(Color.White)
      else if to == 0 then castling1.removeQueenside(Color.White)
      else if to == 63 then castling1.removeKingside(Color.Black)
      else if to == 56 then castling1.removeQueenside(Color.Black)
      else castling1

    val newEpSquare =
      if isPawn && math.abs(to - from) == 16 then (from + to) / 2
      else -1

    val newHalfmoveClock =
      if isPawn || isCapture || move.flag == MoveFlag.EnPassant then 0
      else halfmoveClock + 1
    val newFullmoveNumber =
      if turn == Color.Black then fullmoveNumber + 1 else fullmoveNumber

    // Incremental Zobrist hash update
    val movingPieceIdx = pieceIdAt(fromBit)
    val colorOrd = if isWhiteMoving then 0 else 1
    val destPieceIdx =
      if isPromo then PieceTypes.idToIndex.getOrElse(move.promo.get, movingPieceIdx)
      else movingPieceIdx
    var h = hash
    // Remove moving piece from origin
    h ^= Zobrist.sq64PieceKeyByIdx(movingPieceIdx, colorOrd, from)
    // Place destination piece at target
    h ^= Zobrist.sq64PieceKeyByIdx(destPieceIdx, colorOrd, to)
    // Remove captured piece (if any)
    if isCapture then
      val capIdx = pieceIdAt(toBit, if movingPieceIdx == 0 then 1 else 0)
      if capIdx >= 0 then h ^= Zobrist.sq64PieceKeyByIdx(capIdx, 1 - colorOrd, to)
    // EP capture
    if move.flag == MoveFlag.EnPassant then
      val epCapSq = if isWhiteMoving then to - 8 else to + 8
      h ^= Zobrist.sq64PieceKeyByIdx(0, 1 - colorOrd, epCapSq) // captured pawn
    // Castling rook
    if rookMoveMask != 0L then
      val rookFrom = if to > from then from + 3 else from - 4
      val rookTo = if to > from then from + 1 else from - 1
      h ^= Zobrist.sq64PieceKeyByIdx(3, colorOrd, rookFrom)
      h ^= Zobrist.sq64PieceKeyByIdx(3, colorOrd, rookTo)
    // Flip side
    h ^= Zobrist.sq64SideKey
    // Castling rights change
    h ^= Zobrist.castlingHash(castling) ^ Zobrist.castlingHash(newCastling)
    // EP square change
    if epSquare >= 0 then h ^= Zobrist.sq64EpFileKeys(epSquare % 8)
    if newEpSquare >= 0 then h ^= Zobrist.sq64EpFileKeys(newEpSquare % 8)

    BitBoard(
      np, nn, nb, nr, nq, nk, newWhite, newBlack,
      turn.opponent, newCastling, newEpSquare, newHalfmoveClock, newFullmoveNumber,
      h, newExtras
    )

  // =========================================================================
  // Display
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
            val base = PieceTypes.idToChar.getOrElse(kind, kind.value.head)
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

object BitBoard:

  val EmptyExtras: IArray[Long] =
    if PieceTypes.customs.length == 0 then IArray.empty[Long]
    else IArray.fill(PieceTypes.customs.length)(0L)

  val empty: BitBoard = BitBoard(
    0L, 0L, 0L, 0L, 0L, 0L,
    0L, 0L, Color.White, CastlingRights.none, -1, 0, 1
  )

  private def addPiece(bb: BitBoard, sq: Int, kind: PieceId, color: Color): BitBoard =
    val bit = 1L << sq
    val idx = PieceTypes.idToIndex.getOrElse(kind, -1)
    if idx < 0 then bb
    else
      val withPiece = idx match
        case 0 => bb.copy(pawns = bb.pawns | bit)
        case 1 => bb.copy(knights = bb.knights | bit)
        case 2 => bb.copy(bishops = bb.bishops | bit)
        case 3 => bb.copy(rooks = bb.rooks | bit)
        case 4 => bb.copy(queens = bb.queens | bit)
        case 5 => bb.copy(kings = bb.kings | bit)
        case i =>
          val ei = i - 6
          if ei < bb.extras.length then
            val newExtras = IArray.tabulate(bb.extras.length)(j =>
              if j == ei then bb.extras(j) | bit else bb.extras(j))
            bb.copy(extras = newExtras)
          else bb
      color match
        case Color.White => withPiece.copy(white = withPiece.white | bit)
        case Color.Black => withPiece.copy(black = withPiece.black | bit)
