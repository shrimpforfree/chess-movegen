package chesslab.core

import scala.annotation.tailrec

/**
 * Describes how each piece type moves.
 * Used by the /pieces API endpoint so the frontend can show movement patterns.
 */
object PieceInfo:

  case class Info(
    id: String,
    fenChar: Char,
    value: Int,
    description: String,
    movesFrom: Vector[String]  // algebraic squares reachable from D4 on empty board
  )

  /** Sq64 index to algebraic notation. */
  private def toAlg(sq: Int): String =
    s"${('a' + sq % 8).toChar}${sq / 8 + 1}"

  /** Extract set bit positions from a Long as algebraic square names. */
  @tailrec
  private def bitsToSquares(bb: Long, acc: Vector[String] = Vector.empty): Vector[String] =
    if bb == 0L then acc
    else
      val sq = java.lang.Long.numberOfTrailingZeros(bb)
      bitsToSquares(bb & (bb - 1), acc :+ toAlg(sq))

  // D4 = Sq64 27 (rank 3, file 3). Good center square for showing movement.
  private val DemoSq = 27
  private val EmptyOcc = 0L  // empty board occupancy for sliders

  /** Hand-written descriptions for pieces whose movement can't be auto-detected well. */
  private val customDescriptions: Map[String, String] = Map(
    "archbishop" -> "Slides diagonally like a bishop, jumps like a knight.",
    "chancellor" -> "Slides horizontally/vertically like a rook, jumps like a knight.",
    "amazon" -> "Slides in any direction like a queen, jumps like a knight. The ultimate piece.",
    "camel" -> "Leaps in a (1,3) L-shape — like a stretched knight. Cannot be blocked.",
    "zebra" -> "Leaps in a (2,3) L-shape — an even longer jump than a knight. Cannot be blocked.",
    "mann" -> "Moves one square in any direction, like a king. But not royal — can be captured freely."
  )

  /** Info for all piece types (standard + custom). */
  val all: Vector[Info] = Vector(
    Info("pawn", 'p', 100,
      "Pushes forward one square (two from start). Captures diagonally. Promotes on the last rank.",
      // Show pawn attacks + pushes from D4 as white
      bitsToSquares(Magics.PawnAttacks(0)(DemoSq) | (1L << (DemoSq + 8)))
    ),
    Info("knight", 'n', 320,
      "Jumps in an L-shape: 2 squares in one direction, 1 in the perpendicular. Leaps over pieces.",
      bitsToSquares(Magics.KnightAttacks(DemoSq))
    ),
    Info("bishop", 'b', 330,
      "Slides diagonally any number of squares. Cannot jump over pieces.",
      bitsToSquares(Magics.bishopAttacks(DemoSq, EmptyOcc))
    ),
    Info("rook", 'r', 500,
      "Slides horizontally or vertically any number of squares. Cannot jump over pieces.",
      bitsToSquares(Magics.rookAttacks(DemoSq, EmptyOcc))
    ),
    Info("queen", 'q', 900,
      "Slides in any direction (diagonal, horizontal, vertical) any number of squares.",
      bitsToSquares(Magics.queenAttacks(DemoSq, EmptyOcc))
    ),
    Info("king", 'k', 0,
      "Moves one square in any direction. Can castle with a rook under special conditions.",
      bitsToSquares(Magics.KingAttacks(DemoSq))
    )
  ) ++ PieceTypes.customs.map { cp =>
    Info(
      cp.id.value,
      cp.fenChar,
      cp.value,
      customDescriptions.getOrElse(cp.id.value, describeCustom(cp)),
      bitsToSquares(cp.attacks(DemoSq, EmptyOcc))
    )
  }.toVector

  /** Generate a description for a custom piece based on its attack function. */
  private def describeCustom(cp: PieceTypes.CustomPiece): String =
    val attacks = cp.attacks(DemoSq, EmptyOcc)
    val knightAtk = Magics.KnightAttacks(DemoSq)
    val bishopAtk = Magics.bishopAttacks(DemoSq, EmptyOcc)
    val rookAtk = Magics.rookAttacks(DemoSq, EmptyOcc)
    val kingAtk = Magics.KingAttacks(DemoSq)

    val hasKnight = (attacks & knightAtk) == knightAtk
    val hasBishop = (attacks & bishopAtk) == bishopAtk
    val hasRook = (attacks & rookAtk) == rookAtk
    val hasKing = !hasBishop && !hasRook && (attacks & kingAtk) == kingAtk

    val parts = Vector.newBuilder[String]
    if hasBishop && hasRook then parts += "slides like a queen"
    else if hasBishop then parts += "slides diagonally like a bishop"
    else if hasRook then parts += "slides like a rook"
    if hasKnight then parts += "jumps like a knight"
    if hasKing then parts += "moves one square in any direction"

    val result = parts.result()
    if result.isEmpty then s"Custom piece worth ${cp.value / 100.0} pawns."
    else
      val joined = result.mkString(", ")
      joined.head.toUpper + joined.tail + "."
