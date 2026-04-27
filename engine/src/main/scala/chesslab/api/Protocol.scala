package chesslab.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// --- Requests ---

case class FenRequest(fen: String) derives Decoder

case class MakeMoveRequest(fen: String, move: String) derives Decoder

case class EngineConfig(
  depth: Option[Int] = None,                  // max search depth (default 5)
  timeMs: Option[Int] = None,                 // time limit in ms (not yet implemented)
  useBook: Option[Boolean] = None,            // use opening book (default true)
  useHash: Option[Boolean] = None,            // use transposition table (default true)
  hashSizeMb: Option[Int] = None,             // transposition table size in MB (default 16)
  contempt: Option[Int] = None,               // draw avoidance in centipawns (default 0)
  useNullMove: Option[Boolean] = None,        // null move pruning (default true)
  nullMoveDepthReduction: Option[Int] = None, // depth reduction for null move (default 2)
  nullMoveThreshold: Option[Int] = None       // min eval advantage in centipawns to try null move (default 0)
) derives Decoder, Encoder.AsObject

object EngineConfig:
  val default: EngineConfig = EngineConfig()

case class AiMoveRequest(fen: String, config: Option[EngineConfig] = None) derives Decoder

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

case class BoardAiMoveRequest(board: BoardJson, config: Option[EngineConfig] = None) derives Decoder

case class BoardAiMoveResponse(move: String, board: BoardJson, status: String, eval: Int) derives Encoder.AsObject

case class BoardValidateResponse(valid: Boolean, error: Option[String]) derives Encoder.AsObject
