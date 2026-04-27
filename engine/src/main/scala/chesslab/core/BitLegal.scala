package chesslab.core

/**
 * Legality filter for the bitboard pipeline.
 *
 * A pseudo-legal move is legal if, after making it, the moving side's
 * king is NOT in check. This catches moves that would expose the king
 * (e.g., moving a pinned piece, or failing to escape check).
 */
object BitLegal:

  /** Is this pseudo-legal move actually legal? Make it and check. */
  def isLegal(bb: BitBoard, move: Move): Boolean =
    val after = bb.makeMove(move)
    !BitAttacks.isInCheck(bb.turn, after)

  /** All legal moves for the side to move. */
  def legalMoves(bb: BitBoard): Vector[Move] =
    BitMoveGen.pseudoLegalMoves(bb).filter(isLegal(bb, _))
