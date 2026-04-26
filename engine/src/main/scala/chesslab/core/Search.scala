package chesslab.core

import Square.*

object Search:

  private val MateScore = 1_000_000
  private val Infinity  = 2_000_000

  /**
   * Score a move for ordering. Higher = try first.
   * Captures scored by MVV-LVA (Most Valuable Victim - Least Valuable Attacker).
   */
  private def moveScore(board: Board, move: Move): Int =
    val victimValue = board.squares(move.to) match
      case Occupied(p) => p.pieceDef.value
      case _ => 0
    val attackerValue = board.squares(move.from) match
      case Occupied(p) => p.pieceDef.value
      case _ => 0

    if victimValue > 0 then
      victimValue * 10 - attackerValue + 10_000 // captures first, prefer PxQ over QxQ
    else if move.promo.isDefined then 9_000      // promotions next
    else 0

  /** Order moves: captures (by MVV-LVA) > promotions > quiet moves */
  private def orderMoves(board: Board, moves: Vector[Move]): Vector[Move] =
    moves.sortBy(m => -moveScore(board, m))

  /**
   * Quiescence search — continue searching captures at depth 0
   * to avoid horizon effect (e.g. capturing a pawn then losing the queen).
   */
  private def quiescence(board: Board, alpha: Int, beta: Int): Int =
    // Stand pat: the side to move can choose not to capture
    val standPat = Eval.evaluate(board)
    if standPat >= beta then beta
    else
      val newAlpha = math.max(alpha, standPat)

      // Only search captures and promotions
      val moves = Legal.legalMoves(board)
      val captures = moves.filter { m =>
        board.squares(m.to) != Empty || m.promo.isDefined || m.flag == MoveFlag.EnPassant
      }
      val ordered = orderMoves(board, captures)

      ordered.foldLeft(newAlpha) { (currentAlpha, move) =>
        if currentAlpha >= beta then currentAlpha
        else
          val score = -quiescence(board.makeMove(move), -beta, -currentAlpha)
          math.max(currentAlpha, score)
      }

  /**
   * Negamax with alpha-beta pruning and move ordering.
   */
  private def negamax(board: Board, depth: Int, alpha: Int, beta: Int): Int =
    val moves = Legal.legalMoves(board)

    if moves.isEmpty then
      if Attacks.isInCheck(board, board.sideToMove) then
        -(MateScore - (100 - depth))
      else
        0

    else if depth == 0 then
      quiescence(board, alpha, beta)

    else
      val ordered = orderMoves(board, moves)
      ordered.foldLeft(alpha) { (currentAlpha, move) =>
        if currentAlpha >= beta then currentAlpha
        else
          val score = -negamax(board.makeMove(move), depth - 1, -beta, -currentAlpha)
          math.max(currentAlpha, score)
      }

  /**
   * Find the best move using iterative deepening.
   * Searches depth 1, then 2, ... up to maxDepth.
   * Each iteration's best move is tried first in the next iteration.
   */
  def bestMove(board: Board, maxDepth: Int): Option[(Move, Int)] =
    val moves = Legal.legalMoves(board)
    if moves.isEmpty then None
    else
      // Check opening book first
      val bookMove = OpeningBook.lookup(board).flatMap { uci =>
        moves.find(m => FenCodec.moveToUci(m) == uci).map(m => (m, 0))
      }
      if bookMove.isDefined then bookMove
      else
        // Iterative deepening search
        (1 to maxDepth).foldLeft(Option.empty[(Move, Int)]) { (prev, depth) =>
          val ordered = prev match
            case Some((prevBest, _)) =>
              val (first, rest) = moves.partition(m =>
                m.from == prevBest.from && m.to == prevBest.to && m.promo == prevBest.promo)
              first ++ orderMoves(board, rest)
            case None =>
              orderMoves(board, moves)

          val (best, score, _) = ordered.foldLeft((ordered.head, -Infinity, -Infinity)) {
            case ((bestMove, bestScore, alpha), move) =>
              if alpha >= Infinity then (bestMove, bestScore, alpha)
              else
                val score = -negamax(board.makeMove(move), depth - 1, -Infinity, -alpha)
                if score > bestScore then (move, score, math.max(alpha, score))
                else (bestMove, bestScore, alpha)
          }
          Some((best, score))
        }
