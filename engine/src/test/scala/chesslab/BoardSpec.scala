package chesslab

import munit.FunSuite
import chesslab.core.*

class BoardSpec extends FunSuite:

  // -------------------------------------------------------------------------
  // makeMove basics
  // -------------------------------------------------------------------------

  test("makeMove moves piece and clears origin") {
    val board = FenCodec.startPos
    val move = Move(Squares.fromAlgebraic("e2"), Squares.fromAlgebraic("e4"))
    val after = board.makeMove(move)

    assertEquals(after.pieceAt(Squares.fromAlgebraic("e2")), Square.Empty)
    assertEquals(after.pieceAt(Squares.fromAlgebraic("e4")),
      Square.Occupied(Piece(PieceId.Pawn, Color.White)))
  }

  test("makeMove switches side to move") {
    val board = FenCodec.startPos
    assertEquals(board.sideToMove, Color.White)
    val after = board.makeMove(Move(Squares.fromAlgebraic("e2"), Squares.fromAlgebraic("e4")))
    assertEquals(after.sideToMove, Color.Black)
  }

  test("makeMove increments fullmove after black moves") {
    val board = FenCodec.startPos
    assertEquals(board.fullmoveNumber, 1)
    val afterWhite = board.makeMove(Move(Squares.fromAlgebraic("e2"), Squares.fromAlgebraic("e4")))
    assertEquals(afterWhite.fullmoveNumber, 1) // still 1 after white's move
    val afterBlack = afterWhite.makeMove(Move(Squares.fromAlgebraic("e7"), Squares.fromAlgebraic("e5")))
    assertEquals(afterBlack.fullmoveNumber, 2) // increments after black's move
  }

  // -------------------------------------------------------------------------
  // En passant
  // -------------------------------------------------------------------------

  test("double pawn push sets en passant square") {
    val board = FenCodec.startPos
    val after = board.makeMove(Move(Squares.fromAlgebraic("e2"), Squares.fromAlgebraic("e4")))
    assertEquals(after.epSquare, Some(Squares.fromAlgebraic("e3")))
  }

  test("single pawn push clears en passant square") {
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val after = board.makeMove(Move(Squares.fromAlgebraic("e7"), Squares.fromAlgebraic("e6")))
    assertEquals(after.epSquare, None)
  }

  test("en passant capture removes enemy pawn") {
    // White pawn on e5, black just played d7-d5
    val fen = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val epMove = Move(Squares.fromAlgebraic("e5"), Squares.fromAlgebraic("d6"), flag = MoveFlag.EnPassant)
    val after = board.makeMove(epMove)

    // White pawn is now on d6
    assertEquals(after.pieceAt(Squares.fromAlgebraic("d6")),
      Square.Occupied(Piece(PieceId.Pawn, Color.White)))
    // Black pawn on d5 is removed
    assertEquals(after.pieceAt(Squares.fromAlgebraic("d5")), Square.Empty)
    // Origin is empty
    assertEquals(after.pieceAt(Squares.fromAlgebraic("e5")), Square.Empty)
  }

  test("black en passant capture removes white pawn") {
    // Black pawn on d4, white just played c2-c4
    val fen = "rnbqkbnr/pp1ppppp/8/8/2Pp4/8/PP1PPPPP/RNBQKBNR b KQkq c3 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val epMove = Move(Squares.fromAlgebraic("d4"), Squares.fromAlgebraic("c3"), flag = MoveFlag.EnPassant)
    val after = board.makeMove(epMove)

    assertEquals(after.pieceAt(Squares.fromAlgebraic("c3")),
      Square.Occupied(Piece(PieceId.Pawn, Color.Black)))
    assertEquals(after.pieceAt(Squares.fromAlgebraic("c4")), Square.Empty)
  }

  // -------------------------------------------------------------------------
  // Castling
  // -------------------------------------------------------------------------

  test("white kingside castling moves king and rook") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val castleMove = Move(Squares.E1, Squares.G1, flag = MoveFlag.Castling)
    val after = board.makeMove(castleMove)

    assertEquals(after.pieceAt(Squares.G1), Square.Occupied(Piece(PieceId.King, Color.White)))
    assertEquals(after.pieceAt(Squares.F1), Square.Occupied(Piece(PieceId.Rook, Color.White)))
    assertEquals(after.pieceAt(Squares.E1), Square.Empty)
    assertEquals(after.pieceAt(Squares.H1), Square.Empty)
    assertEquals(after.whiteKingSq, Squares.G1)
  }

  test("white queenside castling moves king and rook") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val castleMove = Move(Squares.E1, Squares.C1, flag = MoveFlag.Castling)
    val after = board.makeMove(castleMove)

    assertEquals(after.pieceAt(Squares.C1), Square.Occupied(Piece(PieceId.King, Color.White)))
    assertEquals(after.pieceAt(Squares.D1), Square.Occupied(Piece(PieceId.Rook, Color.White)))
    assertEquals(after.pieceAt(Squares.E1), Square.Empty)
    assertEquals(after.pieceAt(Squares.A1), Square.Empty)
  }

  test("black kingside castling moves king and rook") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val castleMove = Move(Squares.E8, Squares.G8, flag = MoveFlag.Castling)
    val after = board.makeMove(castleMove)

    assertEquals(after.pieceAt(Squares.G8), Square.Occupied(Piece(PieceId.King, Color.Black)))
    assertEquals(after.pieceAt(Squares.F8), Square.Occupied(Piece(PieceId.Rook, Color.Black)))
    assertEquals(after.pieceAt(Squares.E8), Square.Empty)
    assertEquals(after.pieceAt(Squares.H8), Square.Empty)
    assertEquals(after.blackKingSq, Squares.G8)
  }

  // -------------------------------------------------------------------------
  // Castling rights updates
  // -------------------------------------------------------------------------

  test("king move removes both castling rights") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val kingMove = Move(Squares.E1, Squares.F1)
    val after = board.makeMove(kingMove)

    assertEquals(after.castling.whiteKingside, false)
    assertEquals(after.castling.whiteQueenside, false)
    // Black rights untouched
    assertEquals(after.castling.blackKingside, true)
    assertEquals(after.castling.blackQueenside, true)
  }

  test("rook move from h1 removes white kingside right") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val rookMove = Move(Squares.H1, Squares.G1)
    val after = board.makeMove(rookMove)

    assertEquals(after.castling.whiteKingside, false)
    assertEquals(after.castling.whiteQueenside, true)
  }

  test("rook move from a1 removes white queenside right") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val rookMove = Move(Squares.A1, Squares.B1)
    val after = board.makeMove(rookMove)

    assertEquals(after.castling.whiteKingside, true)
    assertEquals(after.castling.whiteQueenside, false)
  }

  test("capturing rook on h8 removes black kingside right") {
    // White queen captures rook on h8
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3KQ1R w Qkq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val capture = Move(Squares.fromAlgebraic("f1"), Squares.H8)
    val after = board.makeMove(capture)

    assertEquals(after.castling.blackKingside, false)
    assertEquals(after.castling.blackQueenside, true)
  }

  // -------------------------------------------------------------------------
  // Promotion
  // -------------------------------------------------------------------------

  test("promotion changes pawn to promoted piece") {
    val fen = "8/4P3/8/8/8/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val promoMove = Move(Squares.fromAlgebraic("e7"), Squares.fromAlgebraic("e8"),
      promo = Some(PieceId.Queen))
    val after = board.makeMove(promoMove)

    assertEquals(after.pieceAt(Squares.fromAlgebraic("e8")),
      Square.Occupied(Piece(PieceId.Queen, Color.White)))
    assertEquals(after.pieceAt(Squares.fromAlgebraic("e7")), Square.Empty)
  }

  test("underpromotion to knight") {
    val fen = "8/4P3/8/8/8/8/8/4K2k w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val promoMove = Move(Squares.fromAlgebraic("e7"), Squares.fromAlgebraic("e8"),
      promo = Some(PieceId.Knight))
    val after = board.makeMove(promoMove)

    assertEquals(after.pieceAt(Squares.fromAlgebraic("e8")),
      Square.Occupied(Piece(PieceId.Knight, Color.White)))
  }

  test("black promotion") {
    val fen = "4k3/8/8/8/8/8/3p4/4K3 b - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("bad FEN"))
    val promoMove = Move(Squares.fromAlgebraic("d2"), Squares.fromAlgebraic("d1"),
      promo = Some(PieceId.Queen))
    val after = board.makeMove(promoMove)

    assertEquals(after.pieceAt(Squares.fromAlgebraic("d1")),
      Square.Occupied(Piece(PieceId.Queen, Color.Black)))
  }

  // -------------------------------------------------------------------------
  // King position tracking
  // -------------------------------------------------------------------------

  test("king position updated on normal move") {
    val board = FenCodec.startPos
    val moves = Legal.legalMoves(board)
    // e2e4 then e7e5 then Ke2
    val b1 = board.makeMove(Move(Squares.fromAlgebraic("e2"), Squares.fromAlgebraic("e4")))
    val b2 = b1.makeMove(Move(Squares.fromAlgebraic("e7"), Squares.fromAlgebraic("e5")))
    val b3 = b2.makeMove(Move(Squares.E1, Squares.fromAlgebraic("e2")))

    assertEquals(b3.whiteKingSq, Squares.fromAlgebraic("e2"))
    assertEquals(b3.blackKingSq, Squares.E8) // unchanged
  }

  // -------------------------------------------------------------------------
  // Board immutability
  // -------------------------------------------------------------------------

  test("makeMove does not mutate original board") {
    val board = FenCodec.startPos
    val fenBefore = FenCodec.boardToFen(board)
    val _ = board.makeMove(Move(Squares.fromAlgebraic("e2"), Squares.fromAlgebraic("e4")))
    assertEquals(FenCodec.boardToFen(board), fenBefore)
  }
