package chesslab.core

import scala.util.Random

/**
 * Zobrist hashing — assigns random 64-bit keys to board features.
 * The hash of a position is the XOR of all its features.
 * This allows incremental updates: flip a piece on/off by XORing its key.
 */
object Zobrist:

  private val rng = Random(0x12345678L) // fixed seed for reproducibility

  // Random key for each (pieceId, color, square) combination
  // Using a Map since PieceId is dynamic — works with custom pieces too
  private val pieceKeys: Map[(PieceId, Color, Int), Long] =
    (for
      pd <- PieceRegistry.all
      color <- Vector(Color.White, Color.Black)
      sq <- 0 until 120
      if Squares.isOnBoard(sq)
    yield (pd.id, color, sq) -> rng.nextLong()).toMap

  // Side to move
  val sideKey: Long = rng.nextLong()

  // Castling rights (4 bits → 16 combinations, but individual keys compose better)
  val whiteKingsideKey: Long  = rng.nextLong()
  val whiteQueensideKey: Long = rng.nextLong()
  val blackKingsideKey: Long  = rng.nextLong()
  val blackQueensideKey: Long = rng.nextLong()

  // En passant file (columns 1-8)
  val epFileKeys: Map[Int, Long] =
    (1 to 8).map(col => col -> rng.nextLong()).toMap

  def pieceKey(id: PieceId, color: Color, sq: Int): Long =
    pieceKeys.getOrElse((id, color, sq), 0L)

  /** Compute the full hash of a board from scratch. */
  def hash(board: Board): Long =
    val pieceHash = (0 until 120).foldLeft(0L) { (h, sq) =>
      board.squares(sq) match
        case Square.Occupied(piece) =>
          h ^ pieceKey(piece.kind, piece.color, sq)
        case _ => h
    }

    val sideHash = if board.sideToMove == Color.Black then sideKey else 0L

    val castlingHash =
      (if board.castling.whiteKingside then whiteKingsideKey else 0L) ^
      (if board.castling.whiteQueenside then whiteQueensideKey else 0L) ^
      (if board.castling.blackKingside then blackKingsideKey else 0L) ^
      (if board.castling.blackQueenside then blackQueensideKey else 0L)

    val epHash = board.epSquare match
      case Some(sq) => epFileKeys.getOrElse(Squares.col(sq), 0L)
      case None => 0L

    pieceHash ^ sideHash ^ castlingHash ^ epHash
