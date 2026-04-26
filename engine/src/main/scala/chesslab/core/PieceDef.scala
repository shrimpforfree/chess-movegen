package chesslab.core

import Directions.*

// ---------------------------------------------------------------------------
// PieceId — opaque type wrapping String for type safety
// ---------------------------------------------------------------------------
opaque type PieceId = String

object PieceId:
  def apply(id: String): PieceId = id
  extension (id: PieceId) def value: String = id
  given CanEqual[PieceId, PieceId] = CanEqual.derived

  // Convenience constants for standard chess pieces
  val Pawn: PieceId   = "pawn"
  val Knight: PieceId = "knight"
  val Bishop: PieceId = "bishop"
  val Rook: PieceId   = "rook"
  val Queen: PieceId  = "queen"
  val King: PieceId   = "king"

// ---------------------------------------------------------------------------
// PieceDef — data-driven piece definition
// ---------------------------------------------------------------------------
case class PieceDef(
  id: PieceId,
  value: Int = 0,
  slides: Vector[Int] = Vector.empty,
  jumps: Vector[Int] = Vector.empty,
  attackSlides: Option[Vector[Int]] = None,
  attackJumps: Option[Vector[Int]] = None,
  isRoyal: Boolean = false,
  isPawn: Boolean = false
):
  def effectiveAttackSlides: Vector[Int] = attackSlides.getOrElse(slides)
  def effectiveAttackJumps: Vector[Int] = attackJumps.getOrElse(jumps)

// ---------------------------------------------------------------------------
// PieceRegistry — maps PieceId → PieceDef
// ---------------------------------------------------------------------------
object PieceRegistry:
  private val standardPieces: Vector[PieceDef] = Vector(
    PieceDef(
      id = PieceId.Pawn,
      value = 100,
      isPawn = true
    ),
    PieceDef(
      id = PieceId.Knight,
      value = 320,
      jumps = KnightOffsets
    ),
    PieceDef(
      id = PieceId.Bishop,
      value = 330,
      slides = BishopDirs
    ),
    PieceDef(
      id = PieceId.Rook,
      value = 500,
      slides = RookDirs
    ),
    PieceDef(
      id = PieceId.Queen,
      value = 900,
      slides = QueenDirs
    ),
    PieceDef(
      id = PieceId.King,
      value = 0,
      jumps = KingOffsets,
      isRoyal = true
    )
  )

  private val registry: Map[PieceId, PieceDef] =
    standardPieces.map(pd => pd.id -> pd).toMap

  def apply(id: PieceId): PieceDef =
    registry.getOrElse(id, throw IllegalArgumentException(s"Unknown piece: ${id.value}"))

  def get(id: PieceId): Option[PieceDef] = registry.get(id)

  def all: Iterable[PieceDef] = registry.values

  // Precomputed for efficient attack detection
  val jumpChecks: Vector[(PieceId, Vector[Int])] =
    registry.values
      .filterNot(_.isPawn)
      .filter(_.effectiveAttackJumps.nonEmpty)
      .map(pd => (pd.id, pd.effectiveAttackJumps))
      .toVector

  val slideDirs: Vector[Int] =
    registry.values
      .filterNot(_.isPawn)
      .flatMap(_.effectiveAttackSlides)
      .toVector
      .distinct
