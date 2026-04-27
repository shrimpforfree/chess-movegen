package chesslab.core

object PerftBench:

  private def bitPerft(bb: BitBoard, depth: Int): Long =
    if depth == 0 then 1L
    else
      val moves = BitLegal.legalMoves(bb)
      if depth == 1 then moves.size.toLong
      else moves.foldLeft(0L)((acc, move) => acc + bitPerft(bb.makeMove(move), depth - 1))

  def main(args: Array[String]): Unit =
    val useBitboard = args.contains("--bitboard") || args.contains("-b")
    val depth = args.filterNot(_.startsWith("-")).headOption.map(_.toInt).getOrElse(5)
    val engine = if useBitboard then "bitboard" else "mailbox"

    println(s"Running perft 1..$depth on startpos [$engine]\n")

    (1 to depth).foreach { d =>
      val start = System.nanoTime()
      val nodes =
        if useBitboard then bitPerft(FenCodec.bitBoardStartPos, d)
        else Perft.perft(FenCodec.startPos, d)
      val ms = (System.nanoTime() - start) / 1_000_000
      val nps = if ms > 0 then nodes * 1000 / ms else 0
      println(f"  perft($d) = $nodes%,12d    ${ms}%,6d ms    $nps%,12d nps")
    }
