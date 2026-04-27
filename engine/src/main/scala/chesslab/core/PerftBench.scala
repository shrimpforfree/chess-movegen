package chesslab.core

object PerftBench:
  def main(args: Array[String]): Unit =
    val depth = args.filterNot(_.startsWith("-")).headOption.map(_.toInt).getOrElse(5)
    val bb = FenCodec.startPos

    println(s"Running perft 1..$depth on startpos\n")

    (1 to depth).foreach { d =>
      val start = System.nanoTime()
      val nodes = Perft.perft(bb, d)
      val ms = (System.nanoTime() - start) / 1_000_000
      val nps = if ms > 0 then nodes * 1000 / ms else 0
      println(f"  perft($d) = $nodes%,12d    ${ms}%,6d ms    $nps%,12d nps")
    }
