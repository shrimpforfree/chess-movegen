package chesslab.core

object Search:

  /**
   * Negamax with alpha-beta pruning.
   * Returns the best score from the current side-to-move's perspective.
   */
  private def negamax(board: Board, depth: Int, alpha: Int, beta: Int): Int =
    val moves = Legal.legalMoves(board)

    // Terminal: no legal moves
    if moves.isEmpty then
      if Attacks.isInCheck(board, board.sideToMove) then
        -1_000_000 + (100 - depth) // checkmate — prefer faster mates
      else
        0 // stalemate

    // Leaf: evaluate
    else if depth == 0 then
      Eval.evaluate(board)

    // Recurse
    else
      moves.foldLeft(alpha) { (currentAlpha, move) =>
        if currentAlpha >= beta then currentAlpha // beta cutoff
        else
          val score = -negamax(board.makeMove(move), depth - 1, -beta, -currentAlpha)
          math.max(currentAlpha, score)
      }

  /**
   * Find the best move for the current position.
   * Returns the best move and its score.
   */
  def bestMove(board: Board, depth: Int): Option[(Move, Int)] =
    val moves = Legal.legalMoves(board)
    if moves.isEmpty then None
    else
      val (best, score, _) = moves.foldLeft((moves.head, -2_000_000, -2_000_000)) {
        case ((bestMove, bestScore, alpha), move) =>
          if alpha >= 2_000_000 then (bestMove, bestScore, alpha) // already found mate
          else
            val score = -negamax(board.makeMove(move), depth - 1, -2_000_000, -alpha)
            if score > bestScore then (move, score, math.max(alpha, score))
            else (bestMove, bestScore, alpha)
      }
      Some((best, score))
