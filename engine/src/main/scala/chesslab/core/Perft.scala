package chesslab.core

object Perft:

  def perft(board: Board, depth: Int): Long =
    if depth == 0 then 1L
    else
      Legal.legalMoves(board).foldLeft(0L) { (nodes, move) =>
        nodes + perft(board.makeMove(move), depth - 1)
      }

  def divide(board: Board, depth: Int): Map[String, Long] =
    if depth == 0 then Map.empty
    else
      val results = Legal.legalMoves(board).map { move =>
        FenCodec.moveToUci(move) -> perft(board.makeMove(move), depth - 1)
      }.toMap
      val total = results.values.foldLeft(0L)(_ + _)
      results + ("TOTAL" -> total)
