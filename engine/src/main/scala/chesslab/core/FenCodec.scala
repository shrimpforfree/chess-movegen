package chesslab.core

object FenCodec:

  val StartPosFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  // Use def (not val) so these pick up runtime-registered pieces
  private def charToId: Map[Char, PieceId] = PieceTypes.charToId
  private def idToChar: Map[PieceId, Char] = PieceTypes.idToChar

  private case class FenAcc(
    boards: Array[Long],   // 0-5 = standard, 6+ = custom
    white: Long = 0L, black: Long = 0L,
    rank: Int = 7, file: Int = 0
  )

  /** Parse a FEN string into a BitBoard. */
  def fromFen(fen: String): Either[String, BitBoard] =
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

          val acc = parts(0).foldLeft(FenAcc(new Array[Long](PieceTypes.totalCount))) {
            case (a, '/') =>
              a.copy(rank = a.rank - 1, file = 0)
            case (a, ch) if ch.isDigit =>
              a.copy(file = a.file + ch.asDigit)
            case (a, ch) =>
              val bit = 1L << (a.rank * 8 + a.file)
              val isWhite = ch.isUpper
              val idx = charToId.get(ch.toLower)
                .flatMap(id => PieceTypes.idToIndex.get(id))
                .getOrElse(-1)
              if idx >= 0 && idx < a.boards.length then a.boards(idx) |= bit
              val withColor =
                if idx < 0 then a
                else if isWhite then a.copy(white = a.white | bit)
                else a.copy(black = a.black | bit)
              withColor.copy(file = a.file + 1)
          }

          val b = acc.boards
          val extras =
            if PieceTypes.customs.length == 0 then BitBoard.EmptyExtras
            else IArray.unsafeFromArray(b.drop(6))
          val bb = BitBoard(
            b(0), b(1), b(2), b(3), b(4), b(5),
            acc.white, acc.black, side, castling, epSquare, halfmoveClock, fullmoveNumber,
            0L, extras
          )
          Right(bb.copy(hash = Zobrist.hash(bb)))

  /** Convert a BitBoard to a FEN string. */
  def toFen(bb: BitBoard): String =
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

  /** Convert a Move (Sq64 coordinates) to UCI notation (e.g. "e2e4", "a7a8q"). */
  def moveToUci(move: Move): String =
    val fromStr = s"${('a' + move.from % 8).toChar}${move.from / 8 + 1}"
    val toStr = s"${('a' + move.to % 8).toChar}${move.to / 8 + 1}"
    val promoStr = move.promo.flatMap(idToChar.get).map(_.toString).getOrElse("")
    s"$fromStr$toStr$promoStr"

  /** Starting position as a BitBoard. */
  def startPos: BitBoard = fromFen(StartPosFen).getOrElse(
    throw IllegalStateException("Failed to parse starting position FEN")
  )
