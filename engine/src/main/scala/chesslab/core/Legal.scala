package chesslab.core

object Legal:

  def isLegal(board: Board, move: Move): Boolean =
    val after = board.makeMove(move)
    !Attacks.isInCheck(after, board.sideToMove)

  /**
   * Generate all legal moves using the bitboard pipeline for speed.
   * Moves are returned with mailbox indices for backward compatibility
   * with Search, Eval, and Routes.
   */
  def legalMoves(board: Board): Vector[Move] =
    val sq64Moves = BitLegal.legalMoves(board.bb)
    sq64Moves.map { m =>
      Move(Sq64.toMailbox(m.from), Sq64.toMailbox(m.to), m.promo, m.flag)
    }
