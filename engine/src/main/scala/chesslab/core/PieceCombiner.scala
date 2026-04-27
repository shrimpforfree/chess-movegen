package chesslab.core

import scala.annotation.tailrec

/**
 * Piece trait system — define pieces by combining movement primitives.
 *
 * Traits in, piece definition out. Used by the /pieces/combine endpoint
 * and the upgrade draft system.
 */
object PieceCombiner:

  enum SlideDir:
    case Diagonal, Orthogonal, All

  enum PieceTrait:
    case Slides(dir: SlideDir, maxRange: Int = 0)  // 0 = unlimited
    case Jumps(dr: Int, df: Int)                    // fixed leap, all rotations generated

  import PieceTrait.*
  import SlideDir.*

  /** Result of combining traits (before registration). */
  case class PieceDefinition(
    name: String,
    traits: Vector[PieceTrait],
    attacks: (Int, Long) => Long,
    value: Int,
    pst: IArray[Int],
    description: String,
    movesFrom: Vector[String]
  )

  private val DemoSq = 27  // D4
  private val EmptyOcc = 0L

  private def toAlg(sq: Int): String =
    s"${('a' + sq % 8).toChar}${sq / 8 + 1}"

  @tailrec
  private def bitsToSquares(bb: Long, acc: Vector[String] = Vector.empty): Vector[String] =
    if bb == 0L then acc
    else
      val sq = java.lang.Long.numberOfTrailingZeros(bb)
      bitsToSquares(bb & (bb - 1), acc :+ toAlg(sq))

  // =========================================================================
  // Standard piece traits
  // =========================================================================

  /** Get the traits of a standard piece by name. */
  def traitsOf(pieceName: String): Vector[PieceTrait] = pieceName.toLowerCase match
    case "knight"  => Vector(Jumps(2, 1))
    case "bishop"  => Vector(Slides(Diagonal))
    case "rook"    => Vector(Slides(Orthogonal))
    case "queen"   => Vector(Slides(All))
    case "king"    => Vector(Slides(All, 1))
    case "pawn"    => Vector.empty  // pawn is special, no traits
    case other     =>
      // Try to find it in registered custom pieces
      PieceTypes.getById(PieceId(other)).map(_ => Vector.empty).getOrElse(Vector.empty)

  // =========================================================================
  // Attack function building
  // =========================================================================

  /** Build an attack function from a set of traits. */
  def buildAttackFn(traits: Vector[PieceTrait]): (Int, Long) => Long =
    val fns = traits.map(traitToFn)
    if fns.length == 1 then fns.head
    else (sq: Int, occ: Long) => fns.foldLeft(0L)((acc, fn) => acc | fn(sq, occ))

  private def traitToFn(t: PieceTrait): (Int, Long) => Long = t match
    case Slides(Diagonal, 0)    => (sq, occ) => Magics.bishopAttacks(sq, occ)
    case Slides(Orthogonal, 0)  => (sq, occ) => Magics.rookAttacks(sq, occ)
    case Slides(All, 0)         => (sq, occ) => Magics.queenAttacks(sq, occ)
    case Slides(All, 1)         => (sq, _) => Magics.KingAttacks(sq)
    case Slides(dir, 1)         => val tbl = stepTable(dir); (sq, _) => tbl(sq)
    case Slides(dir, maxRange)  => buildLimitedSlideFn(dir, maxRange)
    case Jumps(dr, df)          => val tbl = leaperTablePublic(dr, df); (sq, _) => tbl(sq)

  /** Precompute a leaper table. Public so PieceTypes can use it for builtins. */
  def leaperTablePublic(dr: Int, df: Int): IArray[Long] =
    val offsets =
      if dr == df then IArray((dr,df),(dr,-df),(-dr,df),(-dr,-df))
      else if dr == 0 || df == 0 then
        val (a, b) = if dr == 0 then (df, dr) else (dr, df)
        IArray((a,b),(-a,b),(a,-b),(-a,-b))
      else IArray((dr,df),(dr,-df),(-dr,df),(-dr,-df),(df,dr),(df,-dr),(-df,dr),(-df,-dr))
    IArray.tabulate(64) { sq =>
      val r = sq / 8; val f = sq % 8
      offsets.foldLeft(0L) { case (acc, (ddr, ddf)) =>
        val nr = r + ddr; val nf = f + ddf
        if nr >= 0 && nr < 8 && nf >= 0 && nf < 8 then acc | (1L << (nr * 8 + nf))
        else acc
      }
    }

  private def stepTable(dir: SlideDir): IArray[Long] =
    val dirs: IArray[(Int, Int)] = dir match
      case Diagonal    => IArray((1,1),(1,-1),(-1,1),(-1,-1))
      case Orthogonal  => IArray((1,0),(-1,0),(0,1),(0,-1))
      case All         => IArray((1,1),(1,-1),(-1,1),(-1,-1),(1,0),(-1,0),(0,1),(0,-1))
    IArray.tabulate(64) { sq =>
      val r = sq / 8; val f = sq % 8
      dirs.foldLeft(0L) { case (acc, (dr, df)) =>
        val nr = r + dr; val nf = f + df
        if nr >= 0 && nr < 8 && nf >= 0 && nf < 8 then acc | (1L << (nr * 8 + nf))
        else acc
      }
    }

  private def buildLimitedSlideFn(dir: SlideDir, maxRange: Int): (Int, Long) => Long =
    val dirs: IArray[(Int, Int)] = dir match
      case Diagonal    => IArray((1,1),(1,-1),(-1,1),(-1,-1))
      case Orthogonal  => IArray((1,0),(-1,0),(0,1),(0,-1))
      case All         => IArray((1,1),(1,-1),(-1,1),(-1,-1),(1,0),(-1,0),(0,1),(0,-1))
    (sq: Int, occ: Long) =>
      val r = sq / 8; val f = sq % 8
      dirs.foldLeft(0L) { case (acc, (dr, df)) =>
        walkRayLimited(r + dr, f + df, dr, df, occ, maxRange, acc)
      }

  @tailrec
  private def walkRayLimited(r: Int, f: Int, dr: Int, df: Int, occ: Long, remaining: Int, acc: Long): Long =
    if remaining <= 0 || r < 0 || r > 7 || f < 0 || f > 7 then acc
    else
      val bit = 1L << (r * 8 + f)
      val newAcc = acc | bit
      if (occ & bit) != 0L then newAcc  // hit a blocker, include it but stop
      else walkRayLimited(r + dr, f + df, dr, df, occ, remaining - 1, newAcc)

  // =========================================================================
  // Value estimation
  // =========================================================================

  def estimateValue(attackFn: (Int, Long) => Long): Int =
    val totalMobility = (0 until 64).foldLeft(0) { (acc, sq) =>
      acc + java.lang.Long.bitCount(attackFn(sq, EmptyOcc))
    }
    // Knight=336→320cp, Bishop=364→330cp, Rook=896→500cp, Queen=1260→900cp
    val raw = (totalMobility * 0.72).toInt
    math.max(100, math.min(1500, raw))

  // =========================================================================
  // PST generation
  // =========================================================================

  def generatePST(attackFn: (Int, Long) => Long): IArray[Int] =
    val mobility = IArray.tabulate(64)(sq => java.lang.Long.bitCount(attackFn(sq, EmptyOcc)))
    val maxMob = mobility.foldLeft(0)(math.max)
    val minMob = mobility.foldLeft(64)(math.min)
    val range = math.max(1, maxMob - minMob)
    IArray.tabulate(64)(sq => ((mobility(sq) - minMob).toDouble / range * 40 - 20).toInt)

  // =========================================================================
  // Description
  // =========================================================================

  def describe(traits: Vector[PieceTrait]): String =
    val parts = traits.map {
      case Slides(Diagonal, 0)    => "slides diagonally"
      case Slides(Orthogonal, 0)  => "slides orthogonally"
      case Slides(All, 0)         => "slides in all directions"
      case Slides(Diagonal, 1)    => "steps diagonally"
      case Slides(Orthogonal, 1)  => "steps orthogonally"
      case Slides(All, 1)         => "steps in all directions"
      case Slides(Diagonal, n)    => s"slides diagonally up to $n"
      case Slides(Orthogonal, n)  => s"slides orthogonally up to $n"
      case Slides(All, n)         => s"slides up to $n squares"
      case Jumps(2, 1)            => "jumps like a knight"
      case Jumps(1, 3)            => "leaps (1,3)"
      case Jumps(2, 3)            => "leaps (2,3)"
      case Jumps(dr, df)          => s"leaps ($dr,$df)"
    }
    if parts.isEmpty then "No movement."
    else
      val joined = parts.mkString(", ")
      joined.head.toUpper + joined.tail + "."

  // =========================================================================
  // Auto-generate name from traits
  // =========================================================================

  def autoName(base: Option[String], traits: Vector[PieceTrait]): String =
    val traitSuffix = traits.map {
      case Slides(Diagonal, 0)    => "diag"
      case Slides(Orthogonal, 0)  => "orth"
      case Slides(All, 0)         => "all"
      case Slides(dir, n)         => s"${dir.toString.take(4).toLowerCase}$n"
      case Jumps(dr, df)          => s"j${dr}${df}"
    }.mkString("+")
    base match
      case Some(b) => s"$b+$traitSuffix"
      case None    => traitSuffix

  // =========================================================================
  // Known trait → standard piece mapping
  // If a trait set matches a known piece exactly, use that instead of creating a new one.
  // =========================================================================

  private val knownPieces: Map[Set[PieceTrait], String] = Map(
    Set(Jumps(2, 1))          -> "knight",
    Set(Slides(Diagonal))     -> "bishop",
    Set(Slides(Orthogonal))   -> "rook",
    Set(Slides(All))          -> "queen",
    Set(Slides(All, 1))       -> "king",
    Set(Slides(Diagonal), Jumps(2, 1))     -> "archbishop",
    Set(Slides(Orthogonal), Jumps(2, 1))   -> "chancellor",
    Set(Slides(All), Jumps(2, 1))          -> "amazon",
    Set(Jumps(1, 3))          -> "camel",
    Set(Jumps(2, 3))          -> "zebra",
  )

  /** Check if a trait set matches a known piece. Returns the known piece name or None. */
  def matchKnownPiece(traits: Vector[PieceTrait]): Option[String] =
    knownPieces.get(traits.toSet)

  // =========================================================================
  // Combine — traits in, piece definition out
  // =========================================================================

  /** Combine traits into a piece definition (not yet registered). */
  def combine(traits: Vector[PieceTrait], name: Option[String] = None): PieceDefinition =
    val attackFn = buildAttackFn(traits)
    val value = estimateValue(attackFn)
    val pst = generatePST(attackFn)
    val desc = describe(traits)
    val moves = bitsToSquares(attackFn(DemoSq, EmptyOcc))
    val pieceName = name.getOrElse(autoName(None, traits))
    PieceDefinition(pieceName, traits, attackFn, value, pst, desc, moves)

  /** Combine a base piece with additional traits. */
  def upgrade(basePiece: String, addTraits: Vector[PieceTrait]): PieceDefinition =
    val baseTraits = traitsOf(basePiece)
    val allTraits = (baseTraits ++ addTraits).distinct
    val name = autoName(Some(basePiece), addTraits)
    combine(allTraits, Some(name))

  /**
   * Combine and register — returns the piece index and FEN char.
   * If a piece with the same name already exists, returns its existing registration.
   */
  def combineAndRegister(traits: Vector[PieceTrait], name: Option[String] = None): Option[(PieceDefinition, Int, Char)] =
    val defn = combine(traits, name)
    PieceTypes.registerFromTraits(defn.name, defn.attacks, defn.value, defn.pst).map { (idx, ch) =>
      (defn, idx, ch)
    }

  def upgradeAndRegister(basePiece: String, addTraits: Vector[PieceTrait]): Option[(PieceDefinition, Int, Char)] =
    val defn = upgrade(basePiece, addTraits)
    PieceTypes.registerFromTraits(defn.name, defn.attacks, defn.value, defn.pst).map { (idx, ch) =>
      (defn, idx, ch)
    }
