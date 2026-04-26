package chesslab.api

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import chesslab.core.*

object Routes:

  /** Parse a UCI string (e.g. "e2e4", "e7e8q") by matching against legal moves. */
  private def parseUciMove(board: Board, uci: String): Option[Move] =
    Legal.legalMoves(board).find(m => FenCodec.moveToUci(m) == uci)

  /** Detect game status after a move. */
  private def gameStatus(board: Board): GameStatus =
    val moves = Legal.legalMoves(board)
    val inCheck = Attacks.isInCheck(board, board.sideToMove)
    if moves.isEmpty then
      if inCheck then GameStatus.Checkmate(board.sideToMove.opponent)
      else GameStatus.Draw(DrawReason.Stalemate)
    else GameStatus.InProgress(inCheck)

  private def statusString(status: GameStatus): String = status match
    case GameStatus.InProgress(false) => "in_progress"
    case GameStatus.InProgress(true)  => "check"
    case GameStatus.Checkmate(w)      => s"checkmate:${if w == Color.White then "white" else "black"}"
    case GameStatus.Draw(reason)      => s"draw:${reason.toString.toLowerCase}"

  val chessRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "legal-moves" =>
      for
        body  <- req.as[FenRequest]
        resp  <- FenCodec.boardFromFen(body.fen) match
          case Right(board) =>
            val moves = Legal.legalMoves(board).map(FenCodec.moveToUci).toList
            Ok(LegalMovesResponse(moves))
          case Left(err) =>
            BadRequest(ErrorResponse(err))
      yield resp

    case req @ POST -> Root / "make-move" =>
      for
        body <- req.as[MakeMoveRequest]
        resp <- FenCodec.boardFromFen(body.fen) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(board) =>
            parseUciMove(board, body.move) match
              case None => BadRequest(ErrorResponse(s"Illegal move: ${body.move}"))
              case Some(move) =>
                val after = board.makeMove(move)
                Ok(MakeMoveResponse(FenCodec.boardToFen(after), statusString(gameStatus(after))))
      yield resp

    case req @ POST -> Root / "ai-move" =>
      for
        body <- req.as[AiMoveRequest]
        resp <- FenCodec.boardFromFen(body.fen) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(board) =>
            Search.bestMove(board, body.depth) match
              case None => BadRequest(ErrorResponse("No legal moves available"))
              case Some((move, _)) =>
                val after = board.makeMove(move)
                Ok(AiMoveResponse(FenCodec.moveToUci(move), FenCodec.boardToFen(after), statusString(gameStatus(after))))
      yield resp

    case req @ POST -> Root / "validate" =>
      for
        body <- req.as[FenRequest]
        resp <- FenCodec.boardFromFen(body.fen) match
          case Right(_)  => Ok(ValidateResponse(true, None))
          case Left(err) => Ok(ValidateResponse(false, Some(err)))
      yield resp

    // ----- JSON board endpoints -----

    case req @ POST -> Root / "board" / "legal-moves" =>
      for
        body <- req.as[BoardJson]
        resp <- BoardCodec.fromJson(body) match
          case Right(board) =>
            val moves = Legal.legalMoves(board).map(FenCodec.moveToUci).toList
            Ok(BoardLegalMovesResponse(moves, BoardCodec.toJson(board)))
          case Left(err) =>
            BadRequest(ErrorResponse(err))
      yield resp

    case req @ POST -> Root / "board" / "make-move" =>
      for
        body <- req.as[BoardMakeMoveRequest]
        resp <- BoardCodec.fromJson(body.board) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(board) =>
            parseUciMove(board, body.move) match
              case None => BadRequest(ErrorResponse(s"Illegal move: ${body.move}"))
              case Some(move) =>
                val after = board.makeMove(move)
                Ok(BoardMakeMoveResponse(BoardCodec.toJson(after), statusString(gameStatus(after))))
      yield resp

    case req @ POST -> Root / "board" / "ai-move" =>
      for
        body <- req.as[BoardAiMoveRequest]
        resp <- BoardCodec.fromJson(body.board) match
          case Left(err) => BadRequest(ErrorResponse(err))
          case Right(board) =>
            Search.bestMove(board, body.depth) match
              case None => BadRequest(ErrorResponse("No legal moves available"))
              case Some((move, _)) =>
                val after = board.makeMove(move)
                Ok(BoardAiMoveResponse(FenCodec.moveToUci(move), BoardCodec.toJson(after), statusString(gameStatus(after))))
      yield resp

    case req @ POST -> Root / "board" / "validate" =>
      for
        body <- req.as[BoardJson]
        resp <- BoardCodec.fromJson(body) match
          case Right(_)  => Ok(BoardValidateResponse(true, None))
          case Left(err) => Ok(BoardValidateResponse(false, Some(err)))
      yield resp
  }
