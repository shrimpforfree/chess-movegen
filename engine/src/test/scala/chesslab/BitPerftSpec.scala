package chesslab

import munit.FunSuite
import chesslab.core.*

/**
 * Perft tests for the bitboard pipeline.
 *
 * Uses BitBoard.fromBoard to convert FEN-parsed positions, then runs perft
 * through BitMoveGen → BitLegal → BitBoard.makeMove. The expected node
 * counts are the same as PerftSpec — if these pass, the entire bitboard
 * pipeline is correct.
 */
class BitPerftSpec extends FunSuite:

  private def perft(bb: BitBoard, depth: Int): Long =
    if depth == 0 then 1L
    else
      val moves = BitLegal.legalMoves(bb)
      if depth == 1 then moves.size.toLong
      else moves.foldLeft(0L)((acc, move) => acc + perft(bb.makeMove(move), depth - 1))

  private def bbFromFen(fen: String): BitBoard =
    FenCodec.bitBoardFromFen(fen).getOrElse(
      throw IllegalArgumentException(s"Bad FEN: $fen"))

  // Starting position
  test("bitboard startpos perft depth 1") {
    assertEquals(perft(bbFromFen(FenCodec.StartPosFen), 1), 20L)
  }

  test("bitboard startpos perft depth 2") {
    assertEquals(perft(bbFromFen(FenCodec.StartPosFen), 2), 400L)
  }

  test("bitboard startpos perft depth 3") {
    assertEquals(perft(bbFromFen(FenCodec.StartPosFen), 3), 8902L)
  }

  test("bitboard startpos perft depth 4") {
    assertEquals(perft(bbFromFen(FenCodec.StartPosFen), 4), 197281L)
  }

  // Kiwipete — castling, en passant, promotion
  val kiwipeteFen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"

  test("bitboard kiwipete perft depth 1") {
    assertEquals(perft(bbFromFen(kiwipeteFen), 1), 48L)
  }

  test("bitboard kiwipete perft depth 2") {
    assertEquals(perft(bbFromFen(kiwipeteFen), 2), 2039L)
  }

  test("bitboard kiwipete perft depth 3") {
    assertEquals(perft(bbFromFen(kiwipeteFen), 3), 97862L)
  }

  // Position 3 — en passant and promotion edge cases
  val pos3Fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"

  test("bitboard position 3 perft depth 1") {
    assertEquals(perft(bbFromFen(pos3Fen), 1), 14L)
  }

  test("bitboard position 3 perft depth 2") {
    assertEquals(perft(bbFromFen(pos3Fen), 2), 191L)
  }

  test("bitboard position 3 perft depth 3") {
    assertEquals(perft(bbFromFen(pos3Fen), 3), 2812L)
  }

  test("bitboard position 3 perft depth 4") {
    assertEquals(perft(bbFromFen(pos3Fen), 4), 43238L)
  }

  // Position 4 — complex middlegame with promotion
  val pos4Fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"

  test("bitboard position 4 perft depth 1") {
    assertEquals(perft(bbFromFen(pos4Fen), 1), 6L)
  }

  test("bitboard position 4 perft depth 2") {
    assertEquals(perft(bbFromFen(pos4Fen), 2), 264L)
  }

  test("bitboard position 4 perft depth 3") {
    assertEquals(perft(bbFromFen(pos4Fen), 3), 9467L)
  }

  test("bitboard position 4 perft depth 4") {
    assertEquals(perft(bbFromFen(pos4Fen), 4), 422333L)
  }
