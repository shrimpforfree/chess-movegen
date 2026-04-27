package chesslab.core

import scala.util.Random

/**
 * Zobrist hashing — assigns random 64-bit keys to board features.
 * The hash of a position is the XOR of all its features.
 * This allows incremental updates: flip a piece on/off by XORing its key.
 */
object Zobrist:

  // =========================================================================
  //
  // Separate RNG seed so existing mailbox keys are unchanged.
  // Flat IArray for O(1) lookup: index = pieceIdx * 128 + colorOrd * 64 + sq
  //   pieceIdx: Pawn=0, Knight=1, Bishop=2, Rook=3, Queen=4, King=5
  //   colorOrd: White=0, Black=1
  //   sq: 0..63 (Sq64 index)
  // =========================================================================

  private val rng64 = Random(0xBB640B215FL)

  /** Up to 16 piece types × 2 colors × 64 squares = 2048 random keys. */
  private val MaxPieceTypes = 16
  private val sq64Keys: IArray[Long] = IArray.tabulate(MaxPieceTypes * 128)(_ => rng64.nextLong())

  /** XOR this when Black is to move (same role as sideKey but separate value). */
  val sq64SideKey: Long = rng64.nextLong()

  /** Castling keys: index 0=WK, 1=WQ, 2=BK, 3=BQ. */
  val sq64CastlingKeys: IArray[Long] = IArray.tabulate(4)(_ => rng64.nextLong())

  /** EP file keys: index 0..7 = files a..h. */
  val sq64EpFileKeys: IArray[Long] = IArray.tabulate(8)(_ => rng64.nextLong())

  /** Piece key by index and color ordinal (0=white, 1=black). No boxing. */
  def sq64PieceKeyByIdx(pieceIdx: Int, colorOrd: Int, sq: Int): Long =
    sq64Keys(pieceIdx * 128 + colorOrd * 64 + sq)

  /** Castling hash — XOR of all active castling right keys. */
  def castlingHash(c: CastlingRights): Long =
    (if c.whiteKingside  then sq64CastlingKeys(0) else 0L) ^
    (if c.whiteQueenside then sq64CastlingKeys(1) else 0L) ^
    (if c.blackKingside  then sq64CastlingKeys(2) else 0L) ^
    (if c.blackQueenside then sq64CastlingKeys(3) else 0L)

  /** Sq64 piece key lookup by PieceId. */
  def sq64PieceKey(id: PieceId, color: Color, sq: Int): Long =
    PieceTypes.idToIndex.get(id) match
      case Some(idx) => sq64Keys(idx * 128 + color.ordinal * 64 + sq)
      case None => 0L

  /**
   * Compute the full hash of a BitBoard from scratch.
   * Loops over all piece types (standard + custom) and both colors.
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
      hashBits(bb.kings   & bb.black, 5, 1, 0L) ^
      (0 until bb.extras.length).foldLeft(0L) { (h, ei) =>
        h ^ hashBits(bb.extras(ei) & bb.white, 6 + ei, 0, 0L) ^
            hashBits(bb.extras(ei) & bb.black, 6 + ei, 1, 0L)
      }

    val sideHash = if bb.turn == Color.Black then sq64SideKey else 0L

    val ch = castlingHash(bb.castling)

    val epHash =
      if bb.epSquare >= 0 then sq64EpFileKeys(bb.epSquare % 8) else 0L

    pieceHash ^ sideHash ^ ch ^ epHash
