package chesslab.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// --- Requests ---

case class FenRequest(fen: String) derives Decoder

case class MakeMoveRequest(fen: String, move: String) derives Decoder

case class EngineConfig(
  depth: Option[Int] = None,
  skillLevel: Option[Int] = None,
  useBook: Option[Boolean] = None,
  useHash: Option[Boolean] = None,
  hashSizeMb: Option[Int] = None,
  contempt: Option[Int] = None,
  useNullMove: Option[Boolean] = None,
  nullMoveDepthReduction: Option[Int] = None,
  nullMoveThreshold: Option[Int] = None
) derives Decoder, Encoder.AsObject:
  // Resolve with defaults — call this once instead of .getOrElse everywhere
  def resolved: ResolvedConfig = ResolvedConfig(
    depth = depth.getOrElse(5),
    skillLevel = skillLevel.getOrElse(99),
    useBook = useBook.getOrElse(true),
    useHash = useHash.getOrElse(true),
    hashSizeMb = hashSizeMb.getOrElse(16),
    contempt = contempt.getOrElse(0),
    useNullMove = useNullMove.getOrElse(true),
    nullMoveDepthReduction = nullMoveDepthReduction.getOrElse(2),
    nullMoveThreshold = nullMoveThreshold.getOrElse(0)
  )

case class ResolvedConfig(
  depth: Int,
  skillLevel: Int,
  useBook: Boolean,
  useHash: Boolean,
  hashSizeMb: Int,
  contempt: Int,
  useNullMove: Boolean,
  nullMoveDepthReduction: Int,
  nullMoveThreshold: Int
)

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

// --- Game setup ---

case class SetupJson(key: String, name: String, description: String, fen: String) derives Encoder.AsObject

case class SetupsResponse(setups: List[SetupJson]) derives Encoder.AsObject

// --- Piece info ---

case class PieceInfoJson(
  id: String,             // piece name (e.g. "archbishop")
  fenChar: String,        // FEN character (e.g. "a")
  value: Int,             // material value in centipawns
  description: String,    // how it moves in plain English
  movesFrom: List[String] // squares reachable from D4 on an empty board (e.g. ["a1", "b2", ...])
) derives Encoder.AsObject

case class PiecesResponse(pieces: List[PieceInfoJson]) derives Encoder.AsObject

// --- Piece combiner ---

case class TraitJson(`type`: String, direction: Option[String] = None, dr: Option[Int] = None, df: Option[Int] = None, maxRange: Option[Int] = None) derives Decoder

case class CombineRequest(base: Option[String] = None, traits: Option[List[TraitJson]] = None, add: Option[List[TraitJson]] = None, name: Option[String] = None) derives Decoder

case class CombineResponse(name: String, fenChar: String, value: Int, description: String, movesFrom: List[String], index: Int) derives Encoder.AsObject

// --- Fusion mode ---

case class UpgradeJson(key: String, name: String, description: String) derives Encoder.AsObject

case class FusionRollResponse(upgrade: UpgradeJson) derives Encoder.AsObject

case class FusionApplyRequest(board: BoardJson, square: String, upgradeKey: String) derives Decoder

case class FusionApplyResponse(board: BoardJson, pieceName: String, value: Int, description: String, movesFrom: List[String]) derives Encoder.AsObject
