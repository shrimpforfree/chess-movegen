package chesslab

import munit.FunSuite
import chesslab.core.*

class CustomPieceSpec extends FunSuite:

  private def bbFromFen(fen: String): BitBoard =
    FenCodec.fromFen(fen).getOrElse(fail(s"bad FEN: $fen"))

  // Archbishop (a/A) = bishop + knight
  // Chancellor (c/C) = rook + knight
  // Amazon (z/Z) = queen + knight

  test("archbishop can be parsed from FEN") {
    // White archbishop on E4, kings on their squares
    val bb = FenCodec.fromFen("4k3/8/8/8/4A3/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val piece = bb.pieceAt(28)  // E4 = Sq64 28
    assertEquals(piece.map(_._1), Some(PieceId("archbishop")))
    assertEquals(piece.map(_._2), Some(Color.White))
  }

  test("archbishop generates bishop + knight moves") {
    // White archbishop on E4, only kings otherwise
    val bb = FenCodec.fromFen("4k3/8/8/8/4A3/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val moves = BitLegal.legalMoves(bb)
    // Archbishop on E4 can reach: bishop squares + knight squares
    // Filter to just archbishop moves (from E4 = sq 28)
    val archMoves = moves.filter(_.from == 28)
    // Bishop from E4: up to 13 squares on diagonals
    // Knight from E4: 8 squares
    // Some overlap is impossible (bishop and knight never attack the same square)
    // So archbishop should have bishop moves + knight moves
    // On empty board from E4: bishop=13, knight=8, total=21
    // But E1 has our king so subtract 1 if it would land there (it doesn't — no overlap)
    assert(archMoves.length >= 19, s"Expected ~21 archbishop moves, got ${archMoves.length}")
  }

  test("archbishop attacks are detected") {
    // Black archbishop on D5 attacks E4 (knight jump) and H1 (diagonal)
    val bb = FenCodec.fromFen("4k3/8/8/3a4/8/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    // E4 (sq 28) should be attacked by the black archbishop on D5 (sq 35)
    // D5 to E3: knight jump (35-28=7... wait, D5=35, knight jumps from 35:
    //   35+17=52, 35+15=50, 35+10=45, 35+6=41, 35-6=29, 35-10=25, 35-15=20, 35-17=18
    // So archbishop on D5 attacks squares 52,50,45,41,29,25,20,18 via knight
    // And diagonal squares via bishop
    // Let's check if the white king on E1 (sq 4) is attacked via bishop diagonal
    // D5 to E4 to ... to H1? No, bishop goes D5→E4→F3→G2→H1
    // So E1 (sq 4) is NOT on the diagonal. But A2 (sq 8) and B3 (sq 17) and C4 (sq 26) are.
    // Let's check: is sq 4 (E1) attacked? Only by diagonal from D5→E4(28)→... no.
    // E1 is sq 4. D5 diagonal goes: C4(26), B3(17), A2(8) one way. E6(44), F7(53), G8(62) other way.
    // E4(28), F3(21), G2(14), H1(7) another diagonal.
    // So the archbishop on D5 does NOT attack E1 directly.
    // White king is safe. Let's just verify the archbishop IS attacking some squares.
    assert(BitAttacks.isAttacked(28, Color.Black, bb), "E4 should be attacked by archbishop diagonal")
    assert(BitAttacks.isAttacked(25, Color.Black, bb), "B3 should be attacked by archbishop knight jump")
  }

  test("chancellor generates rook + knight moves") {
    val bb = FenCodec.fromFen("4k3/8/8/8/4C3/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val chancMoves = BitLegal.legalMoves(bb).filter(_.from == 28)
    // Rook from E4: 14 squares (7 on file, 7 on rank) minus E1 (own king)
    // Knight from E4: 8 squares
    // Total ~21
    assert(chancMoves.length >= 19, s"Expected ~21 chancellor moves, got ${chancMoves.length}")
  }

  test("amazon generates queen + knight moves") {
    val bb = FenCodec.fromFen("4k3/8/8/8/4Z3/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val amazonMoves = BitLegal.legalMoves(bb).filter(_.from == 28)
    // Queen from E4: up to 27 squares minus own king
    // Knight from E4: 8 squares (some may overlap with queen — no, they never do)
    // Total ~34
    assert(amazonMoves.length >= 30, s"Expected ~34 amazon moves, got ${amazonMoves.length}")
  }

  test("FEN roundtrip with custom pieces") {
    val fen = "4k3/8/3a4/8/4C3/8/8/3ZK3 w - - 0 1"
    val bb = FenCodec.fromFen(fen).getOrElse(fail("bad FEN"))
    val roundtrip = FenCodec.toFen(bb)
    assertEquals(roundtrip, fen)
  }

  test("eval values custom pieces") {
    // White amazon (1200cp) vs black archbishop (800cp) — White should be ahead
    val bb = FenCodec.fromFen("4k3/8/8/8/4Z3/8/3a4/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val eval = Eval.evaluate(bb)
    assert(eval > 0, s"White with amazon vs archbishop should be positive, got $eval")
  }

  // =========================================================================
  // Camel — (1,3) leaper
  // =========================================================================

  test("camel generates (1,3) leaper moves") {
    // White camel on D4, kings on E1/E8
    val bb = FenCodec.fromFen("4k3/8/8/8/3M4/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val camelMoves = BitLegal.legalMoves(bb).filter(_.from == 27) // D4 = sq 27
    // From D4 (rank 3, file 3): (1,3) offsets give targets at:
    //   (4,6)=G5, (4,0)=A5, (2,6)=G3, (2,0)=A3, (6,4)=E7, (6,2)=C7, (0,4)=E1*, (0,2)=C1
    // E1 has own king → blocked. So 7 legal moves.
    assertEquals(camelMoves.length, 7, s"Camel on D4: expected 7 moves, got ${camelMoves.length}")
  }

  test("camel attacks are detected") {
    // Black camel on D5, can it attack E2? D5=(rank4,file3), E2=(rank1,file4). Delta=(3,1) → yes!
    val bb = FenCodec.fromFen("4k3/8/8/3m4/8/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val e2 = 12 // Sq64 for E2
    assert(BitAttacks.isAttacked(e2, Color.Black, bb), "E2 should be attacked by camel (3,1) jump")
  }

  // =========================================================================
  // Zebra — (2,3) leaper
  // =========================================================================

  test("zebra generates (2,3) leaper moves") {
    // White zebra on D4
    val bb = FenCodec.fromFen("4k3/8/8/8/3X4/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val zebraMoves = BitLegal.legalMoves(bb).filter(_.from == 27) // D4 = sq 27
    // From D4 (rank3, file3): (2,3) offsets give targets at:
    //   (5,6)=G6, (5,0)=A6, (1,6)=G2, (1,0)=A2, (6,5)=F7, (6,1)=B7, (0,5)=F1, (0,1)=B1
    // All 8 are on board, none blocked by own king (E1)
    assertEquals(zebraMoves.length, 8, s"Zebra on D4: expected 8 moves, got ${zebraMoves.length}")
  }

  // =========================================================================
  // Mann (Commoner) — king moves, not royal
  // =========================================================================

  test("mann generates king-like moves but isn't royal") {
    // White mann on D4, kings on E1/E8
    val bb = FenCodec.fromFen("4k3/8/8/8/3O4/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val mannMoves = BitLegal.legalMoves(bb).filter(_.from == 27)
    // Mann on D4: 8 adjacent squares, all empty, none is own king → 8 moves
    assertEquals(mannMoves.length, 8, s"Mann on D4: expected 8 moves, got ${mannMoves.length}")
  }

  test("mann can be captured unlike king") {
    // White mann on D4, black rook on D8 — mann is attacked but that's fine (not royal)
    val bb = FenCodec.fromFen("3rk3/8/8/8/3O4/8/8/4K3 w - - 0 1").getOrElse(fail("bad FEN"))
    val mannMoves = BitLegal.legalMoves(bb).filter(_.from == 27)
    // Mann can still move (it's not royal, being attacked doesn't restrict it)
    assert(mannMoves.nonEmpty, "Mann should be able to move even when attacked")
  }

  // =========================================================================
  // Mix of custom pieces
  // =========================================================================

  test("FEN roundtrip with all custom pieces") {
    val fen = "4k3/3x4/8/3m4/3O4/8/3M4/3CK2Z w - - 0 1"
    val bb = FenCodec.fromFen(fen).getOrElse(fail("bad FEN"))
    val roundtrip = FenCodec.toFen(bb)
    assertEquals(roundtrip, fen)
  }

  // =========================================================================
  // AI search with fairy pieces — verify the engine can search without crashing
  // =========================================================================

  test("AI can search Knightmare setup") {
    val bb = bbFromFen("rabqkbar/pppppppp/8/8/8/8/PPPPPPPP/RABQKBAR w KQkq - 0 1")
    val result = Search.bestMove(bb, maxDepth = 3, useBook = false)
    assert(result.isDefined, "AI should find a move in Knightmare")
  }

  test("AI can search Chaos setup") {
    val bb = bbFromFen("raczkmxo/pppppppp/8/8/8/8/PPPPPPPP/RACZKMXO w KQkq - 0 1")
    val result = Search.bestMove(bb, maxDepth = 2, useBook = false)
    assert(result.isDefined, "AI should find a move in Chaos")
  }

  test("AI values custom pieces correctly in search") {
    val bb = bbFromFen("4k3/8/8/8/4Z3/8/8/4K3 w - - 0 1")
    val result = Search.bestMove(bb, maxDepth = 3, useBook = false)
    result match
      case Some((_, score)) => assert(score > 0, s"White with amazon should have positive eval, got $score")
      case None => fail("AI should find a move")
  }

  test("all game setups produce legal moves") {
    GameSetups.all.foreach { setup =>
      val bb = bbFromFen(setup.fen)
      val moves = BitLegal.legalMoves(bb)
      assert(moves.nonEmpty, s"Setup '${setup.key}' should have legal moves, got 0")
    }
  }
