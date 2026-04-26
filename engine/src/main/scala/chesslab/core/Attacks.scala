package chesslab.core

import scala.annotation.tailrec
import Square.*
import Directions.*

object Attacks:

  /** Scan along a direction. If we hit a piece of byColor, check if it slides along this dir. */
  @tailrec
  private def slidingAttack(board: Board, sq: Int, dir: Int, byColor: Color): Boolean =
    board.squares(sq) match
      case Offboard    => false
      case Occupied(p) =>
        p.color == byColor && p.pieceDef.effectiveAttackSlides.contains(dir)
      case _ => slidingAttack(board, sq + dir, dir, byColor)

  /**
   * Check if a square is attacked by the given color.
   * Works from the defender's perspective: looks outward from the square
   * to see if an attacking piece is in position.
   */
  def isSquareAttacked(board: Board, square: Int, byColor: Color): Boolean =
    // Pawn attacks — special-cased because attack direction depends on color
    val pawnAttackDirs = if byColor == Color.White then Vector(SE, SW) else Vector(NE, NW)
    val pawnAttack = pawnAttackDirs.exists { offset =>
      board.squares(square + offset) match
        case Occupied(p) => p.color == byColor && p.pieceDef.isPawn
        case _ => false
    }

    // Jump attacks — iterate precomputed (pieceId, offsets) pairs
    val jumpAttack = PieceRegistry.jumpChecks.exists { (id, offsets) =>
      offsets.exists { offset =>
        board.squares(square + offset) match
          case Occupied(p) => p.color == byColor && p.kind == id
          case _ => false
      }
    }

    // Slide attacks — scan precomputed distinct directions
    val slideAttack = PieceRegistry.slideDirs.exists { dir =>
      slidingAttack(board, square + dir, dir, byColor)
    }

    pawnAttack || jumpAttack || slideAttack

  /** Check if the given color's king is in check. */
  def isInCheck(board: Board, color: Color): Boolean =
    val kingSq = if color == Color.White then board.whiteKingSq else board.blackKingSq
    if kingSq < 0 then false
    else isSquareAttacked(board, kingSq, color.opponent)
