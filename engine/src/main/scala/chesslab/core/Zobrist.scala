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

  /** Compute the full hash of a mailbox Board from scratch. */
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

  // =========================================================================
  // Sq64-indexed keys for the bitboard pipeline
  //
  // Separate RNG seed so existing mailbox keys are unchanged.
  // Flat IArray for O(1) lookup: index = pieceIdx * 128 + colorOrd * 64 + sq
  //   pieceIdx: Pawn=0, Knight=1, Bishop=2, Rook=3, Queen=4, King=5
  //   colorOrd: White=0, Black=1
  //   sq: 0..63 (Sq64 index)
  // =========================================================================

  private val rng64 = Random(0xBB640B215FL)

  /** 6 pieces × 2 colors × 64 squares = 768 random keys. */
  private val sq64Keys: IArray[Long] = IArray.tabulate(768)(_ => rng64.nextLong())

  /** XOR this when Black is to move (same role as sideKey but separate value). */
  val sq64SideKey: Long = rng64.nextLong()

  /** Castling keys: index 0=WK, 1=WQ, 2=BK, 3=BQ. */
  val sq64CastlingKeys: IArray[Long] = IArray.tabulate(4)(_ => rng64.nextLong())

  /** EP file keys: index 0..7 = files a..h. */
  val sq64EpFileKeys: IArray[Long] = IArray.tabulate(8)(_ => rng64.nextLong())

  /** Piece-type index for Sq64 key lookup. */
  private val pieceIdxMap: Map[PieceId, Int] = Map(
    PieceId.Pawn -> 0, PieceId.Knight -> 1, PieceId.Bishop -> 2,
    PieceId.Rook -> 3, PieceId.Queen -> 4, PieceId.King -> 5
  )

  /**
   * Sq64 piece key lookup. Used for incremental hash updates in makeMove:
   *   newHash = oldHash ^ sq64PieceKey(id, color, fromSq) ^ sq64PieceKey(id, color, toSq)
   */
  def sq64PieceKey(id: PieceId, color: Color, sq: Int): Long =
    pieceIdxMap.get(id) match
      case Some(idx) => sq64Keys(idx * 128 + color.ordinal * 64 + sq)
      case None => 0L

  /**
   * Compute the full hash of a BitBoard from scratch.
   * Scans each piece-type × color bitboard and XORs the corresponding keys.
   */
  def hash(bb: BitBoard): Long =
    import scala.annotation.tailrec

    @tailrec
    def hashBits(pieces: Long, pieceIdx: Int, colorOrd: Int, acc: Long): Long =
      if pieces == 0L then acc
      else
        val sq = java.lang.Long.numberOfTrailingZeros(pieces)
        val key = sq64Keys(pieceIdx * 128 + colorOrd * 64 + sq)
        hashBits(pieces & (pieces - 1), pieceIdx, colorOrd, acc ^ key)

    val pieceHash =
      hashBits(bb.pawns   & bb.white, 0, 0, 0L) ^
      hashBits(bb.pawns   & bb.black, 0, 1, 0L) ^
      hashBits(bb.knights & bb.white, 1, 0, 0L) ^
      hashBits(bb.knights & bb.black, 1, 1, 0L) ^
      hashBits(bb.bishops & bb.white, 2, 0, 0L) ^
      hashBits(bb.bishops & bb.black, 2, 1, 0L) ^
      hashBits(bb.rooks   & bb.white, 3, 0, 0L) ^
      hashBits(bb.rooks   & bb.black, 3, 1, 0L) ^
      hashBits(bb.queens  & bb.white, 4, 0, 0L) ^
      hashBits(bb.queens  & bb.black, 4, 1, 0L) ^
      hashBits(bb.kings   & bb.white, 5, 0, 0L) ^
      hashBits(bb.kings   & bb.black, 5, 1, 0L)

    val sideHash = if bb.turn == Color.Black then sq64SideKey else 0L

    val castlingHash =
      (if bb.castling.whiteKingside  then sq64CastlingKeys(0) else 0L) ^
      (if bb.castling.whiteQueenside then sq64CastlingKeys(1) else 0L) ^
      (if bb.castling.blackKingside  then sq64CastlingKeys(2) else 0L) ^
      (if bb.castling.blackQueenside then sq64CastlingKeys(3) else 0L)

    val epHash =
      if bb.epSquare >= 0 then sq64EpFileKeys(bb.epSquare % 8) else 0L

    pieceHash ^ sideHash ^ castlingHash ^ epHash
