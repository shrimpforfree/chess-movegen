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

        Board(
          squares = squares,
          sideToMove = side,
          castling = castling,
          epSquare = epSquare,
          halfmoveClock = halfmoveClock,
          fullmoveNumber = fullmoveNumber,
          whiteKingSq = whiteKingSq,
          blackKingSq = blackKingSq
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
