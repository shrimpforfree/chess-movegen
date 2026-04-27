package chesslab.core

/**
 * PieceIdx — type-safe index into the BitBoard piece system.
 * Standard pieces get indices 0-5. Custom pieces get 6+.
 */
opaque type PieceIdx = Int

object PieceIdx:
  inline def apply(i: Int): PieceIdx = i
  extension (p: PieceIdx) inline def value: Int = p

  val Pawn: PieceIdx   = 0
  val Knight: PieceIdx = 1
  val Bishop: PieceIdx = 2
  val Rook: PieceIdx   = 3
  val Queen: PieceIdx  = 4
  val King: PieceIdx   = 5

/**
 * Registry for piece types — both standard and custom.
 *
 * Standard pieces (pawn through king) are hardcoded in BitMoveGen/BitAttacks/Eval
 * for maximum speed. Custom pieces are handled via a dynamic loop.
 *
 * Supports runtime registration: call `register(piece)` to add a new piece type.
 * The registry is thread-safe (synchronized). New pieces get the next available
 * index (6, 7, ...) and FEN char.
 */
object PieceTypes:

  case class CustomPiece(
    id: PieceId,
    fenChar: Char,
    value: Int,
    attacks: (Int, Long) => Long,
    pst: IArray[Int]
  )

  // =========================================================================
  // Mutable registry — grows at runtime when new pieces are registered
  // =========================================================================

  private val standardCharToId: Map[Char, PieceId] = Map(
    'p' -> PieceId.Pawn, 'n' -> PieceId.Knight, 'b' -> PieceId.Bishop,
    'r' -> PieceId.Rook, 'q' -> PieceId.Queen, 'k' -> PieceId.King
  )

  private val standardIdToIndex: Map[PieceId, Int] = Map(
    PieceId.Pawn -> 0, PieceId.Knight -> 1, PieceId.Bishop -> 2,
    PieceId.Rook -> 3, PieceId.Queen -> 4, PieceId.King -> 5
  )

  // Internal mutable state — all access goes through synchronized methods
  private var _customs: Vector[CustomPiece] = Vector.empty
  private var _charToId: Map[Char, PieceId] = standardCharToId
  private var _idToChar: Map[PieceId, Char] = standardCharToId.map(_.swap)
  private var _idToIndex: Map[PieceId, Int] = standardIdToIndex
  private var _indexToId: Map[Int, PieceId] = standardIdToIndex.map(_.swap)

  // Used FEN chars (standard + registered custom)
  private var _usedChars: Set[Char] = standardCharToId.keySet

  // =========================================================================
  // Public read accessors (snapshot — safe to read without sync)
  // =========================================================================

  def customs: Vector[CustomPiece] = _customs
  def totalCount: Int = 6 + _customs.length
  def charToId: Map[Char, PieceId] = _charToId
  def idToChar: Map[PieceId, Char] = _idToChar
  def idToIndex: Map[PieceId, Int] = _idToIndex
  def indexToId: Map[Int, PieceId] = _indexToId

  /** Get a custom piece by its index (0-based within customs). */
  def getCustom(customIdx: Int): Option[CustomPiece] =
    if customIdx >= 0 && customIdx < _customs.length then Some(_customs(customIdx))
    else None

  // =========================================================================
  // Registration
  // =========================================================================

  /** Letters available for FEN chars (excluding standard piece chars). */
  private val fenCharPool = "acdefghijlmostuvwxyz"

  /** Pick the next available FEN char. */
  private def nextFenChar(): Option[Char] =
    fenCharPool.find(c => !_usedChars.contains(c))

  /**
   * Register a custom piece at runtime. Returns the assigned index and FEN char.
   * If a piece with the same ID already exists, returns its existing index.
   * Thread-safe.
   */
  def register(id: PieceId, value: Int, attacks: (Int, Long) => Long, pst: IArray[Int],
               preferredChar: Option[Char] = None): Option[(Int, Char)] =
    synchronized {
      // Already registered?
      _idToIndex.get(id) match
        case Some(idx) =>
          val ch = _idToChar.getOrElse(id, '?')
          Some((idx, ch))
        case None =>
          // Use preferred char if available and not taken, otherwise auto-pick
          val ch = preferredChar.filter(c => !_usedChars.contains(c)).orElse(nextFenChar()) match
            case None => return None
            case Some(c) => c
          val idx = 6 + _customs.length
          val piece = CustomPiece(id, ch, value, attacks, pst)
          _customs = _customs :+ piece
          _usedChars = _usedChars + ch
          _charToId = _charToId + (ch -> id)
          _idToChar = _idToChar + (id -> ch)
          _idToIndex = _idToIndex + (id -> idx)
          _indexToId = _indexToId + (idx -> id)
          Some((idx, ch))
    }

  /**
   * Register a custom piece from a PieceCombiner definition.
   * Auto-generates the name from traits if not provided.
   */
  def registerFromTraits(
    name: String,
    attacks: (Int, Long) => Long,
    value: Int,
    pst: IArray[Int]
  ): Option[(Int, Char)] =
    register(PieceId(name), value, attacks, pst)

  /** Look up a piece by ID. Returns its CustomPiece if it's custom, None if standard. */
  def getById(id: PieceId): Option[CustomPiece] =
    _customs.find(_.id == id)

  // =========================================================================
  // Init — register the built-in fairy pieces
  // =========================================================================

  private def initBuiltins(): Unit =
    // Archbishop — bishop + knight (FEN: a)
    register(PieceId("archbishop"), 800,
      (sq, occ) => Magics.bishopAttacks(sq, occ) | Magics.KnightAttacks(sq),
      IArray(-30,-20,-15,-15,-15,-15,-20,-30, -20,-5,5,5,5,5,-5,-20, -15,5,10,15,15,10,5,-15,
        -15,5,15,20,20,15,5,-15, -15,5,15,20,20,15,5,-15, -15,5,10,15,15,10,5,-15,
        -20,-5,5,5,5,5,-5,-20, -30,-20,-15,-15,-15,-15,-20,-30),
      Some('a'))

    // Chancellor — rook + knight (FEN: c)
    register(PieceId("chancellor"), 900,
      (sq, occ) => Magics.rookAttacks(sq, occ) | Magics.KnightAttacks(sq),
      IArray(-5,0,0,5,5,0,0,-5, -5,5,5,10,10,5,5,-5, -5,5,10,10,10,10,5,-5,
        -5,5,10,15,15,10,5,-5, -5,5,10,15,15,10,5,-5, -5,5,10,10,10,10,5,-5,
        -5,5,5,10,10,5,5,-5, -5,0,0,5,5,0,0,-5),
      Some('c'))

    // Amazon — queen + knight (FEN: z)
    register(PieceId("amazon"), 1200,
      (sq, occ) => Magics.queenAttacks(sq, occ) | Magics.KnightAttacks(sq),
      IArray(-20,-10,-10,-5,-5,-10,-10,-20, -10,5,5,10,10,5,5,-10, -10,5,15,15,15,15,5,-10,
        -5,10,15,20,20,15,10,-5, -5,10,15,20,20,15,10,-5, -10,5,15,15,15,15,5,-10,
        -10,5,5,10,10,5,5,-10, -20,-10,-10,-5,-5,-10,-10,-20),
      Some('z'))

    // Camel — (1,3) leaper (FEN: m)
    val camelTbl = PieceCombiner.leaperTablePublic(1, 3)
    register(PieceId("camel"), 250,
      (sq, _) => camelTbl(sq),
      IArray(-20,-10,-10,-10,-10,-10,-10,-20, -10,0,0,5,5,0,0,-10, -10,0,10,10,10,10,0,-10,
        -10,5,10,15,15,10,5,-10, -10,5,10,15,15,10,5,-10, -10,0,10,10,10,10,0,-10,
        -10,0,0,5,5,0,0,-10, -20,-10,-10,-10,-10,-10,-10,-20),
      Some('m'))

    // Zebra — (2,3) leaper (FEN: x)
    val zebraTbl = PieceCombiner.leaperTablePublic(2, 3)
    register(PieceId("zebra"), 200,
      (sq, _) => zebraTbl(sq),
      IArray(-25,-15,-10,-10,-10,-10,-15,-25, -15,-5,0,5,5,0,-5,-15, -10,0,5,10,10,5,0,-10,
        -10,5,10,15,15,10,5,-10, -10,5,10,15,15,10,5,-10, -10,0,5,10,10,5,0,-10,
        -15,-5,0,5,5,0,-5,-15, -25,-15,-10,-10,-10,-10,-15,-25),
      Some('x'))

    // Mann — non-royal king (FEN: o)
    register(PieceId("mann"), 350,
      (sq, _) => Magics.KingAttacks(sq),
      IArray(-10,-5,0,0,0,0,-5,-10, -5,0,5,5,5,5,0,-5, 0,5,10,10,10,10,5,0,
        0,5,10,15,15,10,5,0, 0,5,10,15,15,10,5,0, 0,5,10,10,10,10,5,0,
        -5,0,5,5,5,5,0,-5, -10,-5,0,0,0,0,-5,-10),
      Some('o'))

  // Run init
  initBuiltins()
