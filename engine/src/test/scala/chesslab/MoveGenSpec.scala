package chesslab

import munit.FunSuite
import chesslab.core.*

class MoveGenSpec extends FunSuite:

  // Helper: get all legal moves as UCI strings
  private def legalUci(fen: String): Set[String] =
    val board = FenCodec.boardFromFen(fen).getOrElse(throw Exception(s"bad FEN: $fen"))
    Legal.legalMoves(board).map(FenCodec.moveToUci).toSet

  // Helper: count legal moves
  private def legalCount(fen: String): Int =
    val board = FenCodec.boardFromFen(fen).getOrElse(throw Exception(s"bad FEN: $fen"))
    Legal.legalMoves(board).size

  // -------------------------------------------------------------------------
  // Starting position
  // -------------------------------------------------------------------------

  test("startpos has 20 legal moves") {
    assertEquals(legalCount(FenCodec.StartPosFen), 20)
  }

  // -------------------------------------------------------------------------
  // Pawn moves
  // -------------------------------------------------------------------------

  test("pawn single and double push from start") {
    val moves = legalUci(FenCodec.StartPosFen)
    assert(moves.contains("e2e3")) // single push
    assert(moves.contains("e2e4")) // double push
  }

  test("pawn cannot double push if blocked") {
    val fen = "rnbqkbnr/pppppppp/8/8/8/4P3/PPPP1PPP/RNBQKBNR w KQkq - 0 1"
    val moves = legalUci(fen)
    // e3 pawn can only go to e4
    assert(moves.contains("e3e4"))
    assert(!moves.contains("e3e5")) // not a double push position
  }

  test("pawn cannot push forward into occupied square") {
    // Pawns face each other on e4 and e5
    val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1"
    val moves = legalUci(fen)
    assert(!moves.contains("e4e5")) // blocked
  }

  test("pawn capture") {
    val fen = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1"
    val moves = legalUci(fen)
    assert(moves.contains("e4d5")) // capture
    assert(!moves.contains("e4f5")) // nothing to capture on f5
  }

  // -------------------------------------------------------------------------
  // Pawn promotion
  // -------------------------------------------------------------------------

  test("pawn promotion generates 4 moves") {
    val fen = "8/4P3/8/8/8/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val moves = Legal.legalMoves(board).filter(_.from == Squares.fromAlgebraic("e7"))
    val promoMoves = moves.filter(_.promo.isDefined)
    assertEquals(promoMoves.size, 4)
    val promoKinds = promoMoves.flatMap(_.promo).toSet
    assertEquals(promoKinds, Set(PieceId.Queen, PieceId.Rook, PieceId.Bishop, PieceId.Knight))
  }

  test("pawn promotion capture generates 4 moves per capture") {
    // Pawn on e7, black rook on d8, e8 is empty
    val fen = "3r4/4P3/8/8/8/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val moves = Legal.legalMoves(board).filter(_.from == Squares.fromAlgebraic("e7"))
    // Capture d8: 4 promo moves
    val captureD8 = moves.filter(_.to == Squares.fromAlgebraic("d8"))
    assertEquals(captureD8.size, 4)
    // Push e8: also 4 promo moves (e8 is empty now)
    val pushE8 = moves.filter(_.to == Squares.fromAlgebraic("e8"))
    assertEquals(pushE8.size, 4)
  }

  test("black pawn promotion") {
    // King out of capture range so only d1 push is a promo
    val fen = "4k3/8/8/8/8/8/3p4/1K6 b - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val moves = Legal.legalMoves(board).filter(_.from == Squares.fromAlgebraic("d2"))
    val promoMoves = moves.filter(_.promo.isDefined)
    assertEquals(promoMoves.size, 4)
  }

  // -------------------------------------------------------------------------
  // En passant
  // -------------------------------------------------------------------------

  test("en passant is a legal move") {
    val fen = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 1"
    val moves = legalUci(fen)
    assert(moves.contains("e5d6"))
  }

  test("en passant not available without ep square") {
    val fen = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val epMoves = Legal.legalMoves(board).filter(_.flag == MoveFlag.EnPassant)
    assertEquals(epMoves.size, 0)
  }

  test("en passant blocked if it exposes king to check") {
    val fen = "8/8/8/KPp4r/8/8/8/4k3 w - c6 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    // White pawn on b5, can ep capture c6. But removing black pawn on c5 exposes king on a5 to rook on h5
    val epMoves = Legal.legalMoves(board).filter(_.flag == MoveFlag.EnPassant)
    assertEquals(epMoves.size, 0) // illegal because it exposes the king
  }

  // -------------------------------------------------------------------------
  // Castling
  // -------------------------------------------------------------------------

  test("castling available when path is clear") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val moves = legalUci(fen)
    assert(moves.contains("e1g1")) // kingside
    assert(moves.contains("e1c1")) // queenside
  }

  test("castling blocked when piece in the way") {
    // Knight on g1 blocks kingside castling
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K1NR w KQkq - 0 1"
    val moves = legalUci(fen)
    assert(!moves.contains("e1g1"))
    assert(moves.contains("e1c1")) // queenside still available
  }

  test("castling not allowed when in check") {
    // Black rook gives check on e-file
    val fen = "4k3/8/8/8/4r3/8/8/R3K2R w KQ - 0 1"
    val moves = legalUci(fen)
    assert(!moves.contains("e1g1"))
    assert(!moves.contains("e1c1"))
  }

  test("castling not allowed through attacked square") {
    // Black rook on f8 attacks f1
    val fen = "5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1"
    val moves = legalUci(fen)
    assert(!moves.contains("e1g1")) // f1 is attacked
    assert(moves.contains("e1c1")) // queenside not affected
  }

  test("castling not allowed without rights") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
    val moves = legalUci(fen)
    assert(!moves.contains("e1g1"))
    assert(!moves.contains("e1c1"))
  }

  // -------------------------------------------------------------------------
  // Check evasion
  // -------------------------------------------------------------------------

  test("must escape check — limited moves") {
    // White king on e1, black rook on e8 giving check. No other pieces except black king.
    val fen = "4r3/8/8/8/8/8/8/4K2k w - - 0 1"
    val moves = legalUci(fen)
    // King can only move to squares not on the e-file and not attacked
    assert(!moves.contains("e1e2")) // still on e-file
    for m <- moves do
      assert(!m.startsWith("e1e"), s"King should not stay on e-file: $m")
  }

  test("blocking a check is legal") {
    // White king on e1, black rook on e8, white rook on a4
    // White rook can block by going to e4
    val fen = "4r2k/8/8/8/R7/8/8/4K3 w - - 0 1"
    val moves = legalUci(fen)
    assert(moves.contains("a4e4")) // block the check
  }

  // -------------------------------------------------------------------------
  // Checkmate and stalemate
  // -------------------------------------------------------------------------

  test("checkmate has zero legal moves") {
    // Fool's mate position
    val mateFen = "rnb1kbnr/pppp1ppp/4p3/8/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1"
    assertEquals(legalCount(mateFen), 0)
    val board = FenCodec.boardFromFen(mateFen).getOrElse(fail("bad FEN"))
    assert(Attacks.isInCheck(board, Color.White)) // is in check with no escape = checkmate
  }

  test("stalemate has zero legal moves but not in check") {
    val realStalemate = "8/8/8/8/8/5k2/5p2/5K2 w - - 0 1"
    // White king on f1, black king f3, black pawn f2. White has no moves.
    assertEquals(legalCount(realStalemate), 0)
    val board = FenCodec.boardFromFen(realStalemate).getOrElse(fail("bad FEN"))
    assert(!Attacks.isInCheck(board, Color.White)) // not in check = stalemate
  }

  // -------------------------------------------------------------------------
  // Pin detection (via legality filter)
  // -------------------------------------------------------------------------

  test("pinned piece cannot move off pin line") {
    // White king on e1, white bishop on e2, black rook on e8
    // Bishop is pinned to the e-file and cannot move
    val fen = "4r2k/8/8/8/8/8/4B3/4K3 w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val bishopMoves = Legal.legalMoves(board).filter(_.from == Squares.fromAlgebraic("e2"))
    assertEquals(bishopMoves.size, 0)
  }

  test("pinned rook can move along pin line") {
    // White king on e1, white rook on e4, black rook on e8
    // Rook is pinned but can move along e-file
    val fen = "4r2k/8/8/8/4R3/8/8/4K3 w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val rookMoves = Legal.legalMoves(board).filter(_.from == Squares.fromAlgebraic("e4"))
    // Can move to e2, e3, e5, e6, e7, e8 (capture) — all on e-file
    for m <- rookMoves do
      assertEquals(Squares.col(m.to), Squares.col(Squares.fromAlgebraic("e4")),
        s"Pinned rook should only move on e-file, but got ${FenCodec.moveToUci(m)}")
    assert(rookMoves.nonEmpty)
  }
