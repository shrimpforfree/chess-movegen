package chesslab

import munit.FunSuite
import chesslab.core.*

class AttacksSpec extends FunSuite:

  // -------------------------------------------------------------------------
  // Pawn attacks
  // -------------------------------------------------------------------------

  test("white pawn attacks diagonally") {
    val fen = "8/8/8/8/4P3/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("d5"), Color.White))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("f5"), Color.White))
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e5"), Color.White)) // forward is not an attack
  }

  test("black pawn attacks diagonally") {
    val fen = "4k3/8/8/4p3/8/8/8/4K3 b - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("d4"), Color.Black))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("f4"), Color.Black))
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e4"), Color.Black))
  }

  // -------------------------------------------------------------------------
  // Knight attacks
  // -------------------------------------------------------------------------

  test("knight attacks L-shaped squares") {
    val fen = "8/8/8/8/4N3/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val e4 = Squares.fromAlgebraic("e4")
    // All 8 knight targets from e4
    val targets = Vector("d6", "f6", "c5", "g5", "c3", "g3", "d2", "f2")
    for t <- targets do
      assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic(t), Color.White),
        s"Knight on e4 should attack $t")
    // Not attacked
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e5"), Color.White))
  }

  // -------------------------------------------------------------------------
  // Sliding piece attacks
  // -------------------------------------------------------------------------

  test("rook attacks along rank and file") {
    val fen = "8/8/8/8/4R3/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e8"), Color.White))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("a4"), Color.White))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("h4"), Color.White))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e1"), Color.White))
    // Diagonal not attacked
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("d5"), Color.White))
  }

  test("bishop attacks along diagonals") {
    val fen = "8/8/8/8/4B3/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("b1"), Color.White))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("h7"), Color.White))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("a8"), Color.White))
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("h1"), Color.White))
    // Orthogonal not attacked
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e5"), Color.White))
  }

  test("sliding piece blocked by friendly piece") {
    val fen = "8/8/4P3/8/4R3/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    // Rook on e4 is blocked by pawn on e6
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e5"), Color.White)) // before pawn
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e7"), Color.White)) // behind pawn
  }

  test("sliding piece blocked by enemy piece") {
    val fen = "8/8/4p3/8/4R3/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    // Rook attacks e6 (can capture) but not e7 (blocked)
    assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e6"), Color.White))
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e7"), Color.White))
  }

  // -------------------------------------------------------------------------
  // King attacks
  // -------------------------------------------------------------------------

  test("king attacks adjacent squares") {
    val fen = "8/8/8/4K3/8/8/8/7k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val adjacent = Vector("d6", "e6", "f6", "d5", "f5", "d4", "e4", "f4")
    for t <- adjacent do
      assert(Attacks.isSquareAttacked(board, Squares.fromAlgebraic(t), Color.White),
        s"King on e5 should attack $t")
    // Two squares away — not attacked
    assert(!Attacks.isSquareAttacked(board, Squares.fromAlgebraic("e7"), Color.White))
  }

  // -------------------------------------------------------------------------
  // isInCheck
  // -------------------------------------------------------------------------

  test("king not in check at start") {
    val board = FenCodec.startPos
    assert(!Attacks.isInCheck(board, Color.White))
    assert(!Attacks.isInCheck(board, Color.Black))
  }

  test("king in check from rook") {
    val fen = "4k3/8/8/8/4r3/8/8/4K3 w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    assert(Attacks.isInCheck(board, Color.White))
  }

  test("king in check from knight") {
    val fen = "4k3/8/8/8/8/5n2/8/4K3 w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    assert(Attacks.isInCheck(board, Color.White))
  }

  test("king in check from pawn") {
    val fen = "4k3/8/8/8/8/3p4/8/4K3 w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    // d3 pawn attacks c2 and e2, not e1
    assert(!Attacks.isInCheck(board, Color.White))
  }

  test("king in check from pawn - correct diagonal") {
    val fen = "4k3/8/8/8/8/8/3p4/4K3 w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    // Black pawn on d2 attacks c1 and e1
    assert(Attacks.isInCheck(board, Color.White))
  }

  test("king not in check when piece blocks") {
    val fen = "4k3/8/8/8/4r3/8/4P3/4K3 w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    // Rook on e4 blocked by white pawn on e2
    assert(!Attacks.isInCheck(board, Color.White))
  }
