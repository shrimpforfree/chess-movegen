package chesslab.core

import scala.util.Random

/**
 * Pool of upgrades that can be rolled for the fusion game mode.
 * Each upgrade is a PieceTrait that gets added to an existing piece.
 */
object UpgradePool:

  import PieceCombiner.{PieceTrait, SlideDir}
  import PieceTrait.*
  import SlideDir.*

  case class Upgrade(
    key: String,
    name: String,
    description: String,
    traitDef: PieceTrait
  )

  val all: Vector[Upgrade] = Vector(
    Upgrade("knight_jump",      "Knight Jump",      "Add knight (2,1) leap",                 Jumps(2, 1)),
    Upgrade("camel_jump",       "Camel Jump",       "Add camel (1,3) leap",                  Jumps(1, 3)),
    Upgrade("zebra_jump",       "Zebra Jump",       "Add zebra (2,3) leap",                  Jumps(2, 3)),
    Upgrade("diagonal_slide",   "Diagonal Slide",   "Add unlimited diagonal sliding",        Slides(Diagonal)),
    Upgrade("orthogonal_slide", "Orthogonal Slide",  "Add unlimited orthogonal sliding",      Slides(Orthogonal)),
    Upgrade("diagonal_step",    "Diagonal Step",    "Add 1-square diagonal movement",        Slides(Diagonal, 1)),
    Upgrade("orthogonal_step",  "Orthogonal Step",  "Add 1-square orthogonal movement",      Slides(Orthogonal, 1)),
    Upgrade("short_diagonal",   "Short Diagonal",   "Add diagonal sliding up to 2 squares",  Slides(Diagonal, 2)),
    Upgrade("short_orthogonal", "Short Orthogonal",  "Add orthogonal sliding up to 2 squares", Slides(Orthogonal, 2)),
  )

  private val rng = Random()

  /** Roll one random upgrade. */
  def roll(): Upgrade = all(rng.nextInt(all.size))

  /** Look up an upgrade by key. */
  def get(key: String): Option[Upgrade] = all.find(_.key == key)
