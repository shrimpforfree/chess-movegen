package chesslab.core

import scala.annotation.tailrec

/**
 * Precomputed attack tables for all piece types.
 *
 * Non-sliders (knight, king, pawn): direct 64-entry lookup by square.
 * Sliders (bishop, rook, queen): magic bitboard lookup. For each square,
 * we multiply the relevant occupancy by a "magic number" and shift right
 * to index into a precomputed attack table.
 *
 * Magic numbers are found at init time using a deterministic xorshift64 PRNG
 * (state threaded through @tailrec — no var). Init completes in < 50ms.
 */
object Magics:

  // =========================================================================
  // Direction vectors (rank delta, file delta) used by slider mask/attack
  // computation. Bishop moves diagonally, rook moves along ranks/files.
  // =========================================================================
  private val BishopDirs = IArray((1,1), (1,-1), (-1,1), (-1,-1))
  private val RookDirs   = IArray((1,0), (-1,0), (0,1), (0,-1))

  // =========================================================================
  // Relevant occupancy masks — for each square, the set of squares where a
  // blocker would change the sliding attack pattern. Edge squares at the end
  // of each ray are excluded because a piece there doesn't block anything
  // beyond it (there IS nothing beyond). These masks are ANDed with the
  // board occupancy before the magic multiply-shift to reduce the index space.
  // =========================================================================
  private val bishopMasks: IArray[Long] = IArray.tabulate(64)(sq => computeMask(sq, BishopDirs))
  private val rookMasks: IArray[Long]   = IArray.tabulate(64)(sq => computeMask(sq, RookDirs))

  // =========================================================================
  // Slider init — for each of the 64 squares, finds a magic number via
  // deterministic PRNG and builds the attack lookup table. Each piece type
  // (bishop/rook) gets its own set of magics, shifts, and tables.
  // The baseSeed just needs to be non-zero; different seeds find different
  // (but equally valid) magic numbers.
  // =========================================================================
  private val (bMagics, bShifts, bTables) =
    initSlider(bishopMasks, BishopDirs, 0x615A9BE72CF4D831L)

  private val (rMagics, rShifts, rTables) =
    initSlider(rookMasks, RookDirs, 0xD43E8A1B567FC290L)

  // =========================================================================
  // Non-sliding piece attacks
  //
  // For each of the 64 squares, a Long bitmask of all squares that piece
  // can attack. No occupancy needed — these patterns are fixed.
  //
  // Usage: KnightAttacks(sq) returns the attack bitboard for a knight on sq.
  //        KingAttacks(sq)   returns the attack bitboard for a king on sq.
  // =========================================================================

  /** Attack bitboard for a knight on each square (0..63). */
  val KnightAttacks: IArray[Long] = IArray.tabulate(64) { sq =>
    val r = sq / 8; val f = sq % 8
    IArray((2,1),(2,-1),(-2,1),(-2,-1),(1,2),(1,-2),(-1,2),(-1,-2))
      .foldLeft(0L) { case (acc, (dr, df)) =>
        val nr = r + dr; val nf = f + df
        if nr >= 0 && nr < 8 && nf >= 0 && nf < 8 then acc | (1L << (nr * 8 + nf))
        else acc
      }
  }

  /** Attack bitboard for a king on each square (0..63). */
  val KingAttacks: IArray[Long] = IArray.tabulate(64) { sq =>
    val r = sq / 8; val f = sq % 8
    IArray((1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1))
      .foldLeft(0L) { case (acc, (dr, df)) =>
        val nr = r + dr; val nf = f + df
        if nr >= 0 && nr < 8 && nf >= 0 && nf < 8 then acc | (1L << (nr * 8 + nf))
        else acc
      }
  }

  /**
   * Pawn attack bitboards, indexed by color ordinal then square.
   *   PawnAttacks(0)(sq) = White pawn attacks (northeast + northwest)
   *   PawnAttacks(1)(sq) = Black pawn attacks (southeast + southwest)
   *
   * These are ATTACK squares only, not push squares. A pawn on A2 as White
   * attacks B3 but not A3 (that's a push, not an attack).
   */
  val PawnAttacks: IArray[IArray[Long]] = IArray(
    IArray.tabulate(64) { sq =>
      val r = sq / 8; val f = sq % 8
      (if r < 7 && f < 7 then 1L << (sq + 9) else 0L) |
      (if r < 7 && f > 0 then 1L << (sq + 7) else 0L)
    },
    IArray.tabulate(64) { sq =>
      val r = sq / 8; val f = sq % 8
      (if r > 0 && f < 7 then 1L << (sq - 7) else 0L) |
      (if r > 0 && f > 0 then 1L << (sq - 9) else 0L)
    }
  )

  // =========================================================================
  // Public slider lookup
  //
  // These are the main entry points for move generation and attack detection.
  // Pass the square (0..63) and the full board occupancy (all pieces OR'd).
  // Returns a bitboard of all squares the piece attacks, including squares
  // occupied by friendly pieces (caller must mask those out).
  //
  // Usage:
  //   val occ = whitePieces | blackPieces
  //   val attacks = Magics.bishopAttacks(sq, occ)
  //   val moves = attacks & ~friendlyPieces   // can't capture own pieces
  // =========================================================================

  /** All squares a bishop on `sq` attacks, given board occupancy `occ`. */
  def bishopAttacks(sq: Int, occ: Long): Long =
    val s = sq & 63
    bTables(s)((((occ & bishopMasks(s)) * bMagics(s)) >>> bShifts(s)).toInt)

  /** All squares a rook on `sq` attacks, given board occupancy `occ`. */
  def rookAttacks(sq: Int, occ: Long): Long =
    val s = sq & 63
    rTables(s)((((occ & rookMasks(s)) * rMagics(s)) >>> rShifts(s)).toInt)

  /** All squares a queen on `sq` attacks — just bishop | rook. */
  def queenAttacks(sq: Int, occ: Long): Long =
    bishopAttacks(sq, occ) | rookAttacks(sq, occ)

  // =========================================================================
  // Private — occupancy mask computation
  //
  // For a sliding piece on `sq`, the mask is all squares along its rays
  // EXCEPT the outermost square on each ray (the board edge). We exclude
  // edges because a blocker there doesn't affect the attack pattern — there
  // are no squares beyond the edge to block.
  //
  // Example: rook on A1 looking north → mask includes A2..A7 (not A8).
  // =========================================================================

  /** Build the occupancy mask for a sliding piece on `sq` moving in `dirs`. */
  private def computeMask(sq: Int, dirs: IArray[(Int, Int)]): Long =
    val r = sq / 8; val f = sq % 8
    dirs.foldLeft(0L) { case (acc, (dr, df)) =>
      addRayToMask(r + dr, f + df, dr, df, acc)
    }

  /**
   * Walk one ray starting at (r,f) in direction (dr,df).
   * Add each square to the mask UNLESS it's the last square on the ray
   * (detected by checking if the NEXT step would go off-board).
   */
  @tailrec
  private def addRayToMask(r: Int, f: Int, dr: Int, df: Int, acc: Long): Long =
    if r < 0 || r > 7 || f < 0 || f > 7 then acc
    else if r + dr < 0 || r + dr > 7 || f + df < 0 || f + df > 7 then acc
    else addRayToMask(r + dr, f + df, dr, df, acc | (1L << (r * 8 + f)))

  // =========================================================================
  // Private — actual sliding attacks for a given blocker configuration
  //
  // Unlike the mask (which excludes edges), this computes the REAL attack set:
  // walk each ray until you go off-board or hit a blocker. The blocker square
  // itself IS included (you can capture it).
  //
  // Used during init to precompute every possible attack pattern for every
  // possible blocker configuration. NOT used at runtime — runtime uses the
  // precomputed tables via the magic lookup above.
  // =========================================================================

  /** Compute the full sliding attack bitboard from `sq` with `blockers` on the board. */
  private def slidingAttacks(sq: Int, blockers: Long, dirs: IArray[(Int, Int)]): Long =
    val r = sq / 8; val f = sq % 8
    dirs.foldLeft(0L) { case (acc, (dr, df)) =>
      addRayAttacks(r + dr, f + df, dr, df, blockers, acc)
    }

  /**
   * Walk one ray, adding each square to the attack set.
   * Stop when off-board or when a blocker is hit (blocker square included).
   */
  @tailrec
  private def addRayAttacks(r: Int, f: Int, dr: Int, df: Int, blockers: Long, acc: Long): Long =
    if r < 0 || r > 7 || f < 0 || f > 7 then acc
    else
      val bit = 1L << (r * 8 + f)
      val newAcc = acc | bit
      if (blockers & bit) != 0L then newAcc
      else addRayAttacks(r + dr, f + df, dr, df, blockers, newAcc)

  // =========================================================================
  // Private — deterministic PRNG
  //
  // Xorshift64 with state threaded through return values (no var).
  // sparseRandom AND's three values together so most bits are 0 — this
  // is important because good magic numbers are sparse (few bits set).
  // Returns (randomValue, nextState).
  // =========================================================================

  /** One step of xorshift64. Returns (output, next state). */
  private def xorshift(state: Long): (Long, Long) =
    val s1 = state ^ (state >>> 12)
    val s2 = s1 ^ (s1 << 25)
    val s3 = s2 ^ (s2 >>> 27)
    (s3 * 2685821657736338717L, s3)

  /** Generate a sparse random Long (few bits set) — good magic candidate. */
  private def sparseRandom(state: Long): (Long, Long) =
    val (v1, s1) = xorshift(state)
    val (v2, s2) = xorshift(s1)
    val (v3, s3) = xorshift(s2)
    (v1 & v2 & v3, s3)

  // =========================================================================
  // Private — magic number search
  //
  // A "magic number" is a constant that, when multiplied by the relevant
  // occupancy and right-shifted, produces a perfect (or near-perfect) hash
  // index into the attack table. "Near-perfect" means different blocker
  // configurations that produce the SAME attacks can share an index
  // (constructive collision), but different attacks must NOT collide
  // (destructive collision).
  //
  // We try sparse random candidates until one works. Typically < 1000 tries.
  // =========================================================================

  /**
   * Find a valid magic for a given mask/shift. Returns (magic, next PRNG state).
   * The quick-reject test (bitCount >= 6) filters candidates whose product
   * doesn't spread bits into the upper byte — these almost never work.
   */
  @tailrec
  private def findMagic(mask: Long, shift: Int,
                         attacksFor: Long => Long, rng: Long): (Long, Long) =
    val (candidate, nextRng) = sparseRandom(rng)
    if candidate != 0L &&
       java.lang.Long.bitCount((mask * candidate) & 0xFF00000000000000L) >= 6 &&
       isValidMagic(candidate, mask, shift, attacksFor)
    then (candidate, nextRng)
    else findMagic(mask, shift, attacksFor, nextRng)

  /**
   * Test a magic candidate against ALL subsets of the mask (via Carry-Rippler).
   * Returns true if no two subsets with different attack sets hash to the
   * same index. Uses local Arrays for the check — they're created fresh
   * each call and never escape.
   */
  private def isValidMagic(magic: Long, mask: Long, shift: Int,
                            attacksFor: Long => Long): Boolean =
    val size = 1 << (64 - shift)
    val table = new Array[Long](size)
    val used = new Array[Boolean](size)
    checkAllSubsets(magic, mask, shift, attacksFor, table, used, 0L, started = false)

  /**
   * Carry-Rippler: enumerates every subset of `mask` (including 0).
   * For each subset, compute its hash index and attack set. If the index
   * was already used with a DIFFERENT attack set, the magic is invalid.
   * Same attack set at same index is fine (constructive collision).
   */
  @tailrec
  private def checkAllSubsets(magic: Long, mask: Long, shift: Int, attacksFor: Long => Long,
                               table: Array[Long], used: Array[Boolean],
                               subset: Long, started: Boolean): Boolean =
    if started && subset == 0L then true
    else
      val index = ((subset * magic) >>> shift).toInt
      val attacks = attacksFor(subset)
      if used(index) && table(index) != attacks then false
      else
        if !used(index) then { used(index) = true; table(index) = attacks }
        checkAllSubsets(magic, mask, shift, attacksFor, table, used,
                         (subset - mask) & mask, started = true)

  // =========================================================================
  // Private — attack table construction
  //
  // Once a valid magic is found, we build the final lookup table by writing
  // the correct attack bitboard at each hashed index. Same Carry-Rippler
  // enumeration as validation, but now we just fill the array.
  // =========================================================================

  /** Build the attack table for one square, given its magic/mask/shift. */
  private def buildTable(magic: Long, mask: Long, shift: Int,
                          attacksFor: Long => Long): Array[Long] =
    val size = 1 << (64 - shift)
    val arr = new Array[Long](size)
    fillTable(arr, magic, mask, shift, attacksFor, 0L, started = false)
    arr

  /** Carry-Rippler fill: for each subset of mask, store its attacks at the hashed index. */
  @tailrec
  private def fillTable(arr: Array[Long], magic: Long, mask: Long, shift: Int,
                          attacksFor: Long => Long, subset: Long, started: Boolean): Unit =
    if !(started && subset == 0L) then
      arr(((subset * magic) >>> shift).toInt) = attacksFor(subset)
      fillTable(arr, magic, mask, shift, attacksFor, (subset - mask) & mask, started = true)

  // =========================================================================
  // Private — orchestrate init for one slider type (bishop or rook)
  //
  // Processes all 64 squares sequentially, threading the PRNG state through
  // via foldLeft. For each square: find a magic, build its table, collect
  // the results. Returns parallel arrays of (magics, shifts, tables).
  // =========================================================================

  /** Init all 64 squares for one slider type. Returns (magics, shifts, tables). */
  private def initSlider(masks: IArray[Long], dirs: IArray[(Int, Int)], baseSeed: Long)
    : (Array[Long], Array[Int], Array[Array[Long]]) =

    val (triples, _) = (0 until 64).foldLeft((Vector.empty[(Long, Int, Array[Long])], baseSeed)) {
      case ((acc, rng), sq) =>
        val mask = masks(sq)
        val bits = java.lang.Long.bitCount(mask).toInt
        val shift = 64 - bits
        val attacksFor: Long => Long = blockers => slidingAttacks(sq, blockers, dirs)
        val (magic, nextRng) = findMagic(mask, shift, attacksFor, rng)
        val table = buildTable(magic, mask, shift, attacksFor)
        (acc :+ (magic, shift, table), nextRng)
    }

    (triples.map(_._1).toArray, triples.map(_._2).toArray, triples.map(_._3).toArray)
