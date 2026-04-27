package chesslab.api

import chesslab.core.*

object BoardCodec:

  /** Sq64 index to algebraic string. */
  private def toAlg(sq: Int): String =
    s"${('a' + sq % 8).toChar}${sq / 8 + 1}"

  /** Algebraic string to Sq64 index. */
  private def fromAlg(s: String): Int =
    val file = s(0) - 'a'
    val rank = s(1).asDigit - 1
    rank * 8 + file

  def toJson(bb: BitBoard): BoardJson =
    val pieces = (0 until 64).flatMap { sq =>
      bb.pieceAt(sq).map { (kind, color) =>
        toAlg(sq) -> PieceJson(
          kind.value,
          if color == Color.White then "white" else "black"
        )
      }
    }.toMap

    BoardJson(
      pieces = pieces,
      sideToMove = if bb.turn == Color.White then "white" else "black",
      castling = CastlingJson(
        bb.castling.whiteKingside,
        bb.castling.whiteQueenside,
        bb.castling.blackKingside,
        bb.castling.blackQueenside
      ),
      epSquare =
        if bb.epSquare < 0 then None
        else Some(toAlg(bb.epSquare)),
      halfmoveClock = bb.halfmoveClock,
      fullmoveNumber = bb.fullmoveNumber
    )

  def fromJson(json: BoardJson): Either[String, BitBoard] =
    val sideToMove = json.sideToMove match
      case "white" => Right(Color.White)
      case "black" => Right(Color.Black)
      case other   => Left(s"Invalid side to move: $other")

    sideToMove.flatMap { side =>
      val castling = CastlingRights(
        json.castling.whiteKingside,
        json.castling.whiteQueenside,
        json.castling.blackKingside,
        json.castling.blackQueenside
      )
      val epSquare = json.epSquare.map(fromAlg).getOrElse(-1)

      // Build piece bitboards from JSON
      val totalPieces = PieceTypes.totalCount
      val boards = new Array[Long](totalPieces)
      var white = 0L
      var black = 0L
      var error: Option[String] = None

      json.pieces.foreach { case (sqStr, pj) =>
        if error.isEmpty then
          val sq = fromAlg(sqStr)
          val bit = 1L << sq
          val pieceId = PieceId(pj.kind)
          PieceTypes.idToIndex.get(pieceId) match
            case Some(idx) if idx < totalPieces =>
              boards(idx) |= bit
              pj.color match
                case "white" => white |= bit
                case "black" => black |= bit
                case other => error = Some(s"Invalid color: $other")
            case _ =>
              error = Some(s"Unknown piece type: ${pj.kind}")
      }

      error match
        case Some(err) => Left(err)
        case None =>
          val extras =
            if PieceTypes.customs.length == 0 then BitBoard.EmptyExtras
            else IArray.unsafeFromArray(boards.drop(6))
          val bb = BitBoard(
            boards(0), boards(1), boards(2), boards(3), boards(4), boards(5),
            white, black,
            side, castling, epSquare, json.halfmoveClock, json.fullmoveNumber,
            0L, extras
          )
          Right(bb.copy(hash = Zobrist.hash(bb)))
    }
