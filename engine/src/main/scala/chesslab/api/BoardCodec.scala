package chesslab.api

import chesslab.core.*
import Square.*

object BoardCodec:

  def toJson(board: Board): BoardJson =
    val pieces = (for
      sq <- 0 until 120
      if Squares.isOnBoard(sq)
      piece <- board.squares(sq) match
        case Occupied(p) => Some(p)
        case _ => None
    yield Squares.toAlgebraic(sq) -> PieceJson(
      piece.kind.value,
      if piece.color == Color.White then "white" else "black"
    )).toMap

    BoardJson(
      pieces = pieces,
      sideToMove = if board.sideToMove == Color.White then "white" else "black",
      castling = CastlingJson(
        board.castling.whiteKingside,
        board.castling.whiteQueenside,
        board.castling.blackKingside,
        board.castling.blackQueenside
      ),
      epSquare = board.epSquare.map(Squares.toAlgebraic),
      halfmoveClock = board.halfmoveClock,
      fullmoveNumber = board.fullmoveNumber
    )

  def fromJson(json: BoardJson): Either[String, Board] =
    val sideToMove = json.sideToMove match
      case "white" => Right(Color.White)
      case "black" => Right(Color.Black)
      case other   => Left(s"Invalid side to move: $other")

    sideToMove.flatMap { side =>
      val emptyBoard = Vector.tabulate(120) { i =>
        if Squares.isOnBoard(i) then Square.Empty else Square.Offboard
      }

      val result = json.pieces.foldLeft[Either[String, (Vector[Square], Int, Int)]](
        Right((emptyBoard, -1, -1))
      ) {
        case (Left(err), _) => Left(err)
        case (Right((sqs, wk, bk)), (sqStr, pj)) =>
          val colorEither = pj.color match
            case "white" => Right(Color.White)
            case "black" => Right(Color.Black)
            case other   => Left(s"Invalid color: $other")
          colorEither.flatMap { color =>
            val pieceId = PieceId(pj.kind)
            PieceRegistry.get(pieceId) match
              case None => Left(s"Unknown piece type: ${pj.kind}")
              case Some(pd) =>
                val sq = Squares.fromAlgebraic(sqStr)
                val piece = Piece(pieceId, color)
                val newSqs = sqs.updated(sq, Occupied(piece))
                val newWk = if pd.isRoyal && color == Color.White then sq else wk
                val newBk = if pd.isRoyal && color == Color.Black then sq else bk
                Right((newSqs, newWk, newBk))
          }
      }

      result.map { (squares, wk, bk) =>
        Board(
          squares = squares,
          sideToMove = side,
          castling = CastlingRights(
            json.castling.whiteKingside,
            json.castling.whiteQueenside,
            json.castling.blackKingside,
            json.castling.blackQueenside
          ),
          epSquare = json.epSquare.map(Squares.fromAlgebraic),
          halfmoveClock = json.halfmoveClock,
          fullmoveNumber = json.fullmoveNumber,
          whiteKingSq = wk,
          blackKingSq = bk
        )
      }
    }
