package chesslab.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// --- Requests ---

case class FenRequest(fen: String) derives Decoder

case class MakeMoveRequest(fen: String, move: String) derives Decoder

case class AiMoveRequest(fen: String, depth: Int) derives Decoder

// --- Responses ---

case class LegalMovesResponse(moves: List[String]) derives Encoder.AsObject

case class MakeMoveResponse(fen: String, status: String) derives Encoder.AsObject

case class AiMoveResponse(move: String, fen: String, status: String, eval: Int) derives Encoder.AsObject

case class ValidateResponse(valid: Boolean, error: Option[String]) derives Encoder.AsObject

case class ErrorResponse(error: String) derives Encoder.AsObject

// --- JSON board format ---

case class PieceJson(kind: String, color: String) derives Decoder, Encoder.AsObject

case class CastlingJson(
  whiteKingside: Boolean,
  whiteQueenside: Boolean,
  blackKingside: Boolean,
  blackQueenside: Boolean
) derives Decoder, Encoder.AsObject

case class BoardJson(
  pieces: Map[String, PieceJson],
  sideToMove: String,
  castling: CastlingJson,
  epSquare: Option[String],
  halfmoveClock: Int,
  fullmoveNumber: Int
) derives Decoder, Encoder.AsObject

// --- Board-based requests/responses ---

case class BoardMakeMoveRequest(board: BoardJson, move: String) derives Decoder

case class BoardLegalMovesResponse(moves: List[String], board: BoardJson) derives Encoder.AsObject

case class BoardMakeMoveResponse(board: BoardJson, status: String) derives Encoder.AsObject

case class BoardAiMoveRequest(board: BoardJson, depth: Int) derives Decoder

case class BoardAiMoveResponse(move: String, board: BoardJson, status: String, eval: Int) derives Encoder.AsObject

case class BoardValidateResponse(valid: Boolean, error: Option[String]) derives Encoder.AsObject
