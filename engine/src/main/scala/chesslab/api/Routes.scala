package chesslab.api

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import chesslab.core.*

object Routes:

  /** Parse a TraitJson into a PieceTrait. */
  private def parseTrait(t: TraitJson): Either[String, PieceCombiner.PieceTrait] =
    import PieceCombiner.{PieceTrait as PT, SlideDir as SD}
    t.`type` match
      case "slides" =>
        val dir = t.direction.getOrElse("all") match
          case "diagonal"    => Right(SD.Diagonal)
          case "orthogonal"  => Right(SD.Orthogonal)
          case "all"         => Right(SD.All)
          case other         => Left(s"Unknown direction: $other")
        dir.map(d => PT.Slides(d, t.maxRange.getOrElse(0)))
      case "jumps" =>
        (t.dr, t.df) match
          case (Some(dr), Some(df)) => Right(PT.Jumps(dr, df))
          case _ => Left("jumps requires dr and df")
      case other => Left(s"Unknown trait type: $other")

  /** Parse and execute a combine request. */
  private def parseCombineRequest(body: CombineRequest)
    : Either[String, (PieceCombiner.PieceDefinition, Int, Char)] =
    // Parse traits from the request
    val traitsResult: Either[String, Vector[PieceCombiner.PieceTrait]] =
      body.base match
        case Some(baseName) =>
          // Upgrade mode: base piece + added traits
          val baseTraits = PieceCombiner.traitsOf(baseName)
          if baseTraits.isEmpty && baseName != "pawn" then Left(s"Unknown base piece: $baseName")
          else
            val addTraits = body.add.getOrElse(Nil).map(parseTrait)
            val errors = addTraits.collect { case Left(e) => e }
            if errors.nonEmpty then Left(errors.mkString(", "))
            else Right(baseTraits ++ addTraits.collect { case Right(t) => t })
        case None =>
          // From-scratch mode: just traits
          val traits = body.traits.getOrElse(Nil).map(parseTrait)
          val errors = traits.collect { case Left(e) => e }
          if errors.nonEmpty then Left(errors.mkString(", "))
          else
            val parsed = traits.collect { case Right(t) => t }
            if parsed.isEmpty then Left("No traits specified")
            else Right(parsed.toVector)

    traitsResult.flatMap { traits =>
      PieceCombiner.combineAndRegister(traits, body.name) match
        case Some(result) => Right(result)
        case None => Left("Out of FEN characters — too many piece types registered")
    }

  /**
   * Apply a fusion upgrade: find the piece on the given square in the JSON board,
   * combine it with the upgrade trait, register the new piece, and return the
   * updated JSON board with the piece kind swapped.
   */
  private def applyFusionUpgrade(body: FusionApplyRequest): Either[String, FusionApplyResponse] =
    for
      upgrade <- UpgradePool.get(body.upgradeKey).toRight(s"Unknown upgrade: ${body.upgradeKey}")
      piece <- body.board.pieces.get(body.square).toRight(s"No piece on ${body.square}")
      _ <- if piece.kind == "king" then Left("Cannot upgrade the king") else Right(())
      baseTraits = PieceCombiner.traitsOf(piece.kind)
      _ <- if piece.kind != "pawn" && PieceCombiner.isRedundant(baseTraits, upgrade.traitDef)
           then Left(s"${piece.kind.capitalize} already has that ability")
           else Right(())
      result <- {
        val allTraits = PieceCombiner.normalizeTraits(
          if piece.kind == "pawn" then Vector(upgrade.traitDef)
          else baseTraits :+ upgrade.traitDef
        )

        // Check if the result matches a known piece (e.g. bishop + orth slide = queen)
        val knownName = PieceCombiner.matchKnownPiece(allTraits)
        val (pieceName, value, description, movesFrom) = knownName match
          case Some(name) =>
            // Use the existing known piece
            val defn = PieceCombiner.combine(allTraits, Some(name))
            (name, defn.value, defn.description, defn.movesFrom)
          case None =>
            // New combination — register it
            PieceCombiner.upgradeAndRegister(piece.kind, Vector(upgrade.traitDef)) match
              case Some((defn, _, _)) => (defn.name, defn.value, defn.description, defn.movesFrom)
              case None => return Left("Too many piece types registered")

        val newPiece = PieceJson(pieceName, piece.color)
        val newPieces = body.board.pieces.updated(body.square, newPiece)
        val newBoard = body.board.copy(pieces = newPieces)
        Right(FusionApplyResponse(newBoard, pieceName, value, description, movesFrom.toList))
      }
    yield result

  /** For each upgradable piece type, compute what it becomes with this trait (omit redundant). */
  private def upgradeResults(traitDef: PieceCombiner.PieceTrait): Map[String, String] =
    val pieces = List("pawn", "knight", "bishop", "rook", "queen")
    pieces.flatMap { kind =>
      val baseTraits = PieceCombiner.traitsOf(kind)
      if kind != "pawn" && PieceCombiner.isRedundant(baseTraits, traitDef) then None
      else
        val allTraits = PieceCombiner.normalizeTraits(
          if kind == "pawn" then Vector(traitDef)
          else baseTraits :+ traitDef
        )
        val resultName = PieceCombiner.matchKnownPiece(allTraits)
          .getOrElse(PieceCombiner.autoName(Some(kind), Vector(traitDef)))
        Some(kind -> resultName)
    }.toMap

  /** Parse a UCI string (e.g. "e2e4", "e7e8q") by matching against legal moves. */
  private def parseUciMove(bb: BitBoard, uci: String): Option[Move] =
    BitLegal.legalMoves(bb).find(m => FenCodec.moveToUci(m) == uci)

  /** Detect game status after a move. */
  private def gameStatus(bb: BitBoard): GameStatus =
    val moves = BitLegal.legalMoves(bb)
    val inCheck = BitAttacks.isInCheck(bb.turn, bb)
    if moves.isEmpty then
      if inCheck then GameStatus.Checkmate(bb.turn.opponent)
      else GameStatus.Draw(DrawReason.Stalemate)
    else if bb.halfmoveClock >= 100 then
      GameStatus.Draw(DrawReason.FiftyMoveRule)
    else GameStatus.InProgress(inCheck)

  private def statusString(status: GameStatus): String = status match
    case GameStatus.InProgress(false) => "in_progress"
    case GameStatus.InProgress(true)  => "check"
    case GameStatus.Checkmate(w)      => s"checkmate:${if w == Color.White then "white" else "black"}"
    case GameStatus.Draw(reason)      => s"draw:${reason.toString.toLowerCase}"

  private def runSearch(bb: BitBoard, cfg: EngineConfig): IO[Option[(Move, Int)]] =
    IO.blocking {
      val c = cfg.resolved
      Search.bestMove(bb,
        maxDepth = c.depth,
        skillLevel = c.skillLevel,
        useBook = c.useBook,
        useHashTable = c.useHash,
        hashSizeMb = c.hashSizeMb,
        contempt = c.contempt,
        useNullMovePruning = c.useNullMove,
        nullMoveReduction = c.nullMoveDepthReduction,
        nullMoveMinAdvantage = c.nullMoveThreshold
      )
    }

  val chessRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // GET /setups → list of available game setups (standard chess + fairy variants)
    case GET -> Root / "setups" =>
      val setups = GameSetups.all.map(s => SetupJson(s.key, s.name, s.description, s.fen)).toList
      Ok(SetupsResponse(setups))

    // GET /pieces → info about every piece type: name, FEN char, value, movement description, reachable squares from D4
    case GET -> Root / "pieces" =>
      val pieces = PieceInfo.all.map(p =>
        PieceInfoJson(p.id, p.fenChar.toString, p.value, p.description, p.movesFrom.toList)
      ).toList
      Ok(PiecesResponse(pieces))

    // GET /fusion/roll → roll one random upgrade + what each piece type would become
    case GET -> Root / "fusion" / "roll" =>
      val upgrade = UpgradePool.roll()
      val results = upgradeResults(upgrade.traitDef)
      Ok(FusionRollResponse(UpgradeJson(upgrade.key, upgrade.name, upgrade.description), results))

    // POST /fusion/apply { board, square, upgradeKey } → apply upgrade to piece, return new board JSON
    case req @ POST -> Root / "fusion" / "apply" =>
      for
        body <- req.as[FusionApplyRequest]
        resp <- applyFusionUpgrade(body) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(r) => Ok(r)
      yield resp

    // POST /pieces/combine — combine traits into a new piece, register it, return its definition
    case req @ POST -> Root / "pieces" / "combine" =>
      for
        body <- req.as[CombineRequest]
        resp <- parseCombineRequest(body) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right((defn, idx, ch)) =>
            Ok(CombineResponse(defn.name, ch.toString, defn.value, defn.description, defn.movesFrom.toList, idx))
      yield resp

    // POST /legal-moves { fen } → list of legal moves in UCI notation
    case req @ POST -> Root / "legal-moves" =>
      for
        body  <- req.as[FenRequest]
        resp  <- FenCodec.fromFen(body.fen) match
          case Right(bb) =>
            val moves = BitLegal.legalMoves(bb).map(FenCodec.moveToUci).toList
            Ok(LegalMovesResponse(moves))
          case Left(err) =>
            BadRequest(ErrorResponse(err))
      yield resp

    // POST /make-move { fen, move } → apply a UCI move, return new FEN + game status
    case req @ POST -> Root / "make-move" =>
      for
        body <- req.as[MakeMoveRequest]
        resp <- FenCodec.fromFen(body.fen) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(bb) =>
            parseUciMove(bb, body.move) match
              case None => BadRequest(ErrorResponse(s"Illegal move: ${body.move}"))
              case Some(move) =>
                val after = bb.makeMove(move)
                Ok(MakeMoveResponse(FenCodec.toFen(after), statusString(gameStatus(after))))
      yield resp

    // POST /ai-move { fen, config? } → engine picks the best move
    case req @ POST -> Root / "ai-move" =>
      for
        body <- req.as[AiMoveRequest]
        resp <- FenCodec.fromFen(body.fen) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(bb) =>
            val cfg = body.config.getOrElse(EngineConfig.default)
            runSearch(bb, cfg).flatMap {
              case None => BadRequest(ErrorResponse("No legal moves available"))
              case Some((move, score)) =>
                val whiteEval = if bb.turn == Color.White then score else -score
                val after = bb.makeMove(move)
                Ok(AiMoveResponse(FenCodec.moveToUci(move), FenCodec.toFen(after), statusString(gameStatus(after)), whiteEval))
            }
      yield resp

    // POST /validate { fen } → check if a FEN string is valid
    case req @ POST -> Root / "validate" =>
      for
        body <- req.as[FenRequest]
        resp <- FenCodec.fromFen(body.fen) match
          case Right(_)  => Ok(ValidateResponse(true, None))
          case Left(err) => Ok(ValidateResponse(false, Some(err)))
      yield resp

    // POST /board/legal-moves { board JSON } → legal moves + board state
    case req @ POST -> Root / "board" / "legal-moves" =>
      for
        body <- req.as[BoardJson]
        resp <- BoardCodec.fromJson(body) match
          case Right(bb) =>
            val moves = BitLegal.legalMoves(bb).map(FenCodec.moveToUci).toList
            Ok(BoardLegalMovesResponse(moves, BoardCodec.toJson(bb)))
          case Left(err) =>
            BadRequest(ErrorResponse(err))
      yield resp

    // POST /board/make-move { board JSON, move } → apply move, return updated board JSON + status
    case req @ POST -> Root / "board" / "make-move" =>
      for
        body <- req.as[BoardMakeMoveRequest]
        resp <- BoardCodec.fromJson(body.board) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(bb) =>
            parseUciMove(bb, body.move) match
              case None => BadRequest(ErrorResponse(s"Illegal move: ${body.move}"))
              case Some(move) =>
                val after = bb.makeMove(move)
                Ok(BoardMakeMoveResponse(BoardCodec.toJson(after), statusString(gameStatus(after))))
      yield resp

    // POST /board/ai-move { board JSON, config? } → engine best move with JSON board input/output
    case req @ POST -> Root / "board" / "ai-move" =>
      for
        body <- req.as[BoardAiMoveRequest]
        resp <- BoardCodec.fromJson(body.board) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(bb) =>
            val cfg = body.config.getOrElse(EngineConfig.default)
            runSearch(bb, cfg).flatMap {
              case None => BadRequest(ErrorResponse("No legal moves available"))
              case Some((move, score)) =>
                val whiteEval = if bb.turn == Color.White then score else -score
                val after = bb.makeMove(move)
                Ok(BoardAiMoveResponse(FenCodec.moveToUci(move), BoardCodec.toJson(after), statusString(gameStatus(after)), whiteEval))
            }
      yield resp

    // POST /board/validate { board JSON } → check if a JSON board is valid
    case req @ POST -> Root / "board" / "validate" =>
      for
        body <- req.as[BoardJson]
        resp <- BoardCodec.fromJson(body) match
          case Right(_)  => Ok(BoardValidateResponse(true, None))
          case Left(err) => Ok(BoardValidateResponse(false, Some(err)))
      yield resp
  }
