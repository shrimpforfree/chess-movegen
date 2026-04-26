package chesslab.core

object Legal:

  def isLegal(board: Board, move: Move): Boolean =
    val after = board.makeMove(move)
    !Attacks.isInCheck(after, board.sideToMove)

  def legalMoves(board: Board): Vector[Move] =
    MoveGen.pseudoLegalMoves(board).filter(isLegal(board, _))
