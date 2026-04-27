package chesslab.core

object Perft:

  def perft(bb: BitBoard, depth: Int): Long =
    if depth == 0 then 1L
    else
      val moves = BitLegal.legalMoves(bb)
      if depth == 1 then moves.size.toLong
      else moves.foldLeft(0L) { (nodes, move) =>
        nodes + perft(bb.makeMove(move), depth - 1)
      }

  def divide(bb: BitBoard, depth: Int): Map[String, Long] =
    if depth == 0 then Map.empty
    else
      val results = BitLegal.legalMoves(bb).map { move =>
        FenCodec.moveToUci(move) -> perft(bb.makeMove(move), depth - 1)
      }.toMap
      val total = results.values.foldLeft(0L)(_ + _)
      results + ("TOTAL" -> total)
