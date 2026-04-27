package chesslab.core

import Square.*

object FenCodec:

  val StartPosFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  private val charToId: Map[Char, PieceId] = Map(
    'p' -> PieceId.Pawn,
    'n' -> PieceId.Knight,
    'b' -> PieceId.Bishop,
    'r' -> PieceId.Rook,
    'q' -> PieceId.Queen,
    'k' -> PieceId.King
  )

  private val idToChar: Map[PieceId, Char] = charToId.map(_.swap)

  def pieceFromFenChar(c: Char): Option[Piece] =
    val color = if c.isUpper then Color.White else Color.Black
    charToId.get(c.toLower).map(id => Piece(id, color))

  def pieceFenChar(piece: Piece): Char =
    val base = idToChar.getOrElse(piece.kind,
      throw IllegalArgumentException(s"No FEN char for piece: ${piece.kind.value}"))
    if piece.color == Color.White then base.toUpper else base

  def moveToUci(move: Move): String =
    val base = Squares.toAlgebraic(move.from) + Squares.toAlgebraic(move.to)
    move.promo match
      case Some(id) =>
        val ch = idToChar.getOrElse(id,
          throw IllegalArgumentException(s"No FEN char for promo piece: ${id.value}"))
        base + ch
      case None => base

  def boardFromFen(fen: String): Either[String, Board] =
    val parts = fen.split(' ')
    if parts.length < 4 then Left(s"Invalid FEN: too few parts")
    else
      val sideToMove = parts(1) match
        case "w" => Right(Color.White)
        case "b" => Right(Color.Black)
        case s   => Left(s"Invalid side to move: $s")

      sideToMove.map { side =>
        val piecePlacement = parts(0)
        val castling = CastlingRights.fromFen(parts(2))

        val epSquare =
          if parts(3) == "-" then None
          else Some(Squares.fromAlgebraic(parts(3)))

        val halfmoveClock = if parts.length > 4 then parts(4).toIntOption.getOrElse(0) else 0
        val fullmoveNumber = if parts.length > 5 then parts(5).toIntOption.getOrElse(1) else 1

        val emptyBoard = Vector.tabulate(120) { i =>
          if Squares.isOnBoard(i) then Square.Empty else Square.Offboard
        }

        val (squares, _, _, whiteKingSq, blackKingSq) =
          piecePlacement.foldLeft((emptyBoard, 8, 1, -1, -1)) {
            case ((sqs, rank, file, wk, bk), '/') =>
              (sqs, rank - 1, 1, wk, bk)
            case ((sqs, rank, file, wk, bk), ch) if ch.isDigit =>
              (sqs, rank, file + ch.asDigit, wk, bk)
            case ((sqs, rank, file, wk, bk), ch) =>
              pieceFromFenChar(ch) match
                case Some(piece) =>
                  val row = 10 - rank
                  val sq = row * 10 + file
                  val newSqs = sqs.updated(sq, Occupied(piece))
                  val newWk = if piece.pieceDef.isRoyal && piece.color == Color.White then sq else wk
                  val newBk = if piece.pieceDef.isRoyal && piece.color == Color.Black then sq else bk
                  (newSqs, rank, file + 1, newWk, newBk)
                case None =>
                  (sqs, rank, file, wk, bk)
          }

        // Also parse into BitBoard for the fast path
        val bb = bitBoardFromFen(fen).getOrElse(BitBoard.empty)

        Board(
          squares = squares,
          sideToMove = side,
          castling = castling,
          epSquare = epSquare,
          halfmoveClock = halfmoveClock,
          fullmoveNumber = fullmoveNumber,
          whiteKingSq = whiteKingSq,
          blackKingSq = blackKingSq,
          bb = bb
        )
      }

  def boardToFen(board: Board): String =
    val piecePart = (2 to 9).map { row =>
      val (rankStr, trailing) = (1 to 8).foldLeft(("", 0)) { case ((acc, empty), col) =>
        board.squares(row * 10 + col) match
          case Occupied(p) =>
            val prefix = if empty > 0 then acc + empty.toString else acc
            (prefix + pieceFenChar(p), 0)
          case _ =>
            (acc, empty + 1)
      }
      if trailing > 0 then rankStr + trailing.toString else rankStr
    }.mkString("/")

    val sidePart = if board.sideToMove == Color.White then "w" else "b"
    val castlingPart = board.castling.toFen
    val epPart = board.epSquare.map(Squares.toAlgebraic).getOrElse("-")

    s"$piecePart $sidePart $castlingPart $epPart ${board.halfmoveClock} ${board.fullmoveNumber}"

  def startPos: Board = boardFromFen(StartPosFen).getOrElse(
    throw IllegalStateException("Failed to parse starting position FEN")
  )

  // =========================================================================
  // BitBoard FEN support
  // =========================================================================

  /** Accumulator for FEN piece placement parsing into bitboards. */
  private case class FenAcc(
    pawns: Long = 0L, knights: Long = 0L, bishops: Long = 0L,
    rooks: Long = 0L, queens: Long = 0L, kings: Long = 0L,
    white: Long = 0L, black: Long = 0L,
    rank: Int = 7, file: Int = 0  // FEN starts at rank 8 (index 7), file a (index 0)
  )

  /**
   * Parse a FEN string directly into a BitBoard.
   * No intermediate mailbox — sets bits on piece/color Longs during the fold.
   */
  def bitBoardFromFen(fen: String): Either[String, BitBoard] =
    val parts = fen.split(' ')
    if parts.length < 4 then Left("Invalid FEN: too few parts")
    else
      parts(1) match
        case "w" => Right(Color.White)
        case "b" => Right(Color.Black)
        case s   => Left(s"Invalid side to move: $s")
      match
        case Left(e) => Left(e)
        case Right(side) =>
          val castling = CastlingRights.fromFen(parts(2))
          val epSquare =
            if parts(3) == "-" then -1
            else
              val f = parts(3)(0) - 'a'
              val r = parts(3)(1).asDigit - 1
              r * 8 + f
          val halfmoveClock = if parts.length > 4 then parts(4).toIntOption.getOrElse(0) else 0
          val fullmoveNumber = if parts.length > 5 then parts(5).toIntOption.getOrElse(1) else 1

          val acc = parts(0).foldLeft(FenAcc()) {
            case (a, '/') =>
              a.copy(rank = a.rank - 1, file = 0)
            case (a, ch) if ch.isDigit =>
              a.copy(file = a.file + ch.asDigit)
            case (a, ch) =>
              val bit = 1L << (a.rank * 8 + a.file)
              val isWhite = ch.isUpper
              val withPiece = ch.toLower match
                case 'p' => a.copy(pawns = a.pawns | bit)
                case 'n' => a.copy(knights = a.knights | bit)
                case 'b' => a.copy(bishops = a.bishops | bit)
                case 'r' => a.copy(rooks = a.rooks | bit)
                case 'q' => a.copy(queens = a.queens | bit)
                case 'k' => a.copy(kings = a.kings | bit)
                case _   => a
              val withColor =
                if isWhite then withPiece.copy(white = withPiece.white | bit)
                else withPiece.copy(black = withPiece.black | bit)
              withColor.copy(file = a.file + 1)
          }

          Right(BitBoard(
            acc.pawns, acc.knights, acc.bishops, acc.rooks, acc.queens, acc.kings,
            acc.white, acc.black, side, castling, epSquare, halfmoveClock, fullmoveNumber
          ))

  /** Convert a BitBoard back to a FEN string. */
  def bitBoardToFen(bb: BitBoard): String =
    val piecePart = (7 to 0 by -1).map { rank =>
      val (rankStr, empty) = (0 to 7).foldLeft(("", 0)) { case ((acc, empty), file) =>
        bb.pieceAt(rank * 8 + file) match
          case Some((kind, color)) =>
            val base = idToChar.getOrElse(kind, '?')
            val ch = if color == Color.White then base.toUpper else base
            val prefix = if empty > 0 then acc + empty.toString else acc
            (prefix + ch, 0)
          case None =>
            (acc, empty + 1)
      }
      if empty > 0 then rankStr + empty.toString else rankStr
    }.mkString("/")

    val sidePart = if bb.turn == Color.White then "w" else "b"
    val castlingPart = bb.castling.toFen
    val epPart =
      if bb.epSquare < 0 then "-"
      else s"${('a' + bb.epSquare % 8).toChar}${bb.epSquare / 8 + 1}"

    s"$piecePart $sidePart $castlingPart $epPart ${bb.halfmoveClock} ${bb.fullmoveNumber}"

  /** Convert a Move with Sq64 coordinates to UCI notation (e.g. "e2e4", "a7a8q"). */
  def sq64MoveToUci(move: Move): String =
    val fromStr = s"${('a' + move.from % 8).toChar}${move.from / 8 + 1}"
    val toStr = s"${('a' + move.to % 8).toChar}${move.to / 8 + 1}"
    val promoStr = move.promo.flatMap(idToChar.get).map(_.toString).getOrElse("")
    s"$fromStr$toStr$promoStr"

  /** Starting position as a BitBoard. */
  def bitBoardStartPos: BitBoard = bitBoardFromFen(StartPosFen).getOrElse(
    throw IllegalStateException("Failed to parse starting position FEN")
  )
