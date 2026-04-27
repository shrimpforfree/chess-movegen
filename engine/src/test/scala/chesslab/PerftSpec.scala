package chesslab

import munit.FunSuite
import chesslab.core.*

class PerftSpec extends FunSuite:

  private def bb(fen: String): BitBoard =
    FenCodec.fromFen(fen).getOrElse(throw IllegalArgumentException(s"Bad FEN: $fen"))

  // Starting position
  test("startpos perft depth 1") { assertEquals(Perft.perft(FenCodec.startPos, 1), 20L) }
  test("startpos perft depth 2") { assertEquals(Perft.perft(FenCodec.startPos, 2), 400L) }
  test("startpos perft depth 3") { assertEquals(Perft.perft(FenCodec.startPos, 3), 8902L) }
  test("startpos perft depth 4") { assertEquals(Perft.perft(FenCodec.startPos, 4), 197281L) }

  // Kiwipete
  val kiwipeteFen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
  test("kiwipete perft depth 1") { assertEquals(Perft.perft(bb(kiwipeteFen), 1), 48L) }
  test("kiwipete perft depth 2") { assertEquals(Perft.perft(bb(kiwipeteFen), 2), 2039L) }
  test("kiwipete perft depth 3") { assertEquals(Perft.perft(bb(kiwipeteFen), 3), 97862L) }

  // Position 3
  val pos3Fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
  test("position 3 perft depth 1") { assertEquals(Perft.perft(bb(pos3Fen), 1), 14L) }
  test("position 3 perft depth 2") { assertEquals(Perft.perft(bb(pos3Fen), 2), 191L) }
  test("position 3 perft depth 3") { assertEquals(Perft.perft(bb(pos3Fen), 3), 2812L) }
  test("position 3 perft depth 4") { assertEquals(Perft.perft(bb(pos3Fen), 4), 43238L) }

  // Position 4
  val pos4Fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
  test("position 4 perft depth 1") { assertEquals(Perft.perft(bb(pos4Fen), 1), 6L) }
  test("position 4 perft depth 2") { assertEquals(Perft.perft(bb(pos4Fen), 2), 264L) }
  test("position 4 perft depth 3") { assertEquals(Perft.perft(bb(pos4Fen), 3), 9467L) }
  test("position 4 perft depth 4") { assertEquals(Perft.perft(bb(pos4Fen), 4), 422333L) }
