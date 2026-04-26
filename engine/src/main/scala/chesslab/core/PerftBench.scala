package chesslab.core

object PerftBench:
  def main(args: Array[String]): Unit =
    val depth = if args.nonEmpty then args(0).toInt else 5
    val board = FenCodec.startPos

    println(s"Running perft($depth) on startpos...")
    val start = System.nanoTime()
    val nodes = Perft.perft(board, depth)
    val ms = (System.nanoTime() - start) / 1_000_000
    val nps = if ms > 0 then nodes * 1000 / ms else 0

    println(s"Nodes: $nodes")
    println(s"Time:  ${ms}ms")
    println(s"Speed: $nps nodes/sec")
