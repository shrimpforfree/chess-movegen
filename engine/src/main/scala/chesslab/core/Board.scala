package chesslab.core

import Square.*

case class Board(
  squares: Vector[Square],
  sideToMove: Color,
  castling: CastlingRights,
  epSquare: Option[Int],
  halfmoveClock: Int,
  fullmoveNumber: Int,
  whiteKingSq: Int,
  blackKingSq: Int
):

  def pieceAt(sq: Int): Square = squares(sq)

  def makeMove(move: Move): Board =
    val from = move.from
    val to   = move.to
    val piece = squares(from) match
      case Occupied(p) => p
      case _ => throw IllegalStateException(s"No piece at ${Squares.toAlgebraic(from)}")

    val pd = piece.pieceDef

    // Basic piece movement
    val movedSquares = squares
      .updated(from, Empty)
      .updated(to, Occupied(piece))

    // Promotion
    val afterPromo = move.promo match
      case Some(promoKind) => movedSquares.updated(to, Occupied(Piece(promoKind, piece.color)))
      case None            => movedSquares

    // En passant capture — remove the captured pawn
    val afterEp = if move.flag == MoveFlag.EnPassant then
      val capturedSq = if piece.color == Color.White then to + 10 else to - 10
      afterPromo.updated(capturedSq, Empty)
    else afterPromo

    // Castling — move the rook
    val newSquares = if move.flag == MoveFlag.Castling then
      val fromRow = Squares.row(from)
      val toCol   = Squares.col(to)
      val fromCol = Squares.col(from)
      if toCol > fromCol then // Kingside
        val rookFrom = fromRow * 10 + 8
        val rookTo   = fromRow * 10 + 6
        afterEp.updated(rookTo, afterEp(rookFrom)).updated(rookFrom, Empty)
      else // Queenside
        val rookFrom = fromRow * 10 + 1
        val rookTo   = fromRow * 10 + 4
        afterEp.updated(rookTo, afterEp(rookFrom)).updated(rookFrom, Empty)
    else afterEp

    // Update king position
    val (newWhiteKingSq, newBlackKingSq) =
      if pd.isRoyal then
        if piece.color == Color.White then (to, blackKingSq) else (whiteKingSq, to)
      else (whiteKingSq, blackKingSq)

    // Update en passant square
    val newEpSquare =
      if pd.isPawn && math.abs(Squares.row(from) - Squares.row(to)) == 2 then
        val epRow = (Squares.row(from) + Squares.row(to)) / 2
        Some(epRow * 10 + Squares.col(from))
      else
        None

    // Update castling rights — piece moves
    val castlingAfterMove =
      if pd.isRoyal then castling.removeKing(piece.color)
      else if piece.kind == PieceId.Rook then
        if from == Squares.H1 then castling.removeKingside(Color.White)
        else if from == Squares.A1 then castling.removeQueenside(Color.White)
        else if from == Squares.H8 then castling.removeKingside(Color.Black)
        else if from == Squares.A8 then castling.removeQueenside(Color.Black)
        else castling
      else castling

    // Rook captured (by landing on its home square)
    val newCastling =
      if to == Squares.H1 then castlingAfterMove.removeKingside(Color.White)
      else if to == Squares.A1 then castlingAfterMove.removeQueenside(Color.White)
      else if to == Squares.H8 then castlingAfterMove.removeKingside(Color.Black)
      else if to == Squares.A8 then castlingAfterMove.removeQueenside(Color.Black)
      else castlingAfterMove

    // Switch side, update counters
    val newSide = sideToMove.opponent
    val newFullmove = if sideToMove == Color.Black then fullmoveNumber + 1 else fullmoveNumber

    Board(
      squares = newSquares,
      sideToMove = newSide,
      castling = newCastling,
      epSquare = newEpSquare,
      halfmoveClock =
        if pd.isPawn || squares(to) != Empty then 0
        else halfmoveClock + 1,
      fullmoveNumber = newFullmove,
      whiteKingSq = newWhiteKingSq,
      blackKingSq = newBlackKingSq
    )

  override def toString: String =
    val lines = new StringBuilder
    lines.append("  a b c d e f g h\n")
    for rank <- 8 to 1 by -1 do
      val row = 10 - rank
      lines.append(s"$rank ")
      for file <- 1 to 8 do
        val sq = row * 10 + file
        val ch = squares(sq) match
          case Occupied(p) => FenCodec.pieceFenChar(p)
          case _           => '.'
        lines.append(ch)
        if file < 8 then lines.append(' ')
      lines.append('\n')
    lines.append(s"\nSide to move: ${if sideToMove == Color.White then "w" else "b"}")
    lines.append(s"\nCastling: ${castling.toFen}")
    lines.append(s"\nEn passant: ${epSquare.map(Squares.toAlgebraic).getOrElse("-")}")
    lines.toString


object Board:
  def empty: Board =
    val squares = Vector.tabulate(120) { i =>
      if Squares.isOnBoard(i) then Square.Empty else Square.Offboard
    }
    Board(squares, Color.White, CastlingRights.none, None, 0, 1, -1, -1)
