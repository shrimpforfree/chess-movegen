package chesslab

import munit.FunSuite
import chesslab.core.*

class FenSpec extends FunSuite:

  test("startpos FEN round-trip") {
    val board = FenCodec.startPos
    val fen = FenCodec.boardToFen(board)
    assertEquals(fen, FenCodec.StartPosFen)
  }

  test("FEN round-trip with en passant") {
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("Failed to parse FEN"))
    assertEquals(FenCodec.boardToFen(board), fen)
  }

  test("FEN round-trip with partial castling") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w Kq - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("Failed to parse FEN"))
    assertEquals(FenCodec.boardToFen(board), fen)
  }

  test("FEN round-trip no castling") {
    val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
    val board = FenCodec.boardFromFen(fen).getOrElse(fail("Failed to parse FEN"))
    assertEquals(FenCodec.boardToFen(board), fen)
  }

  test("startpos king positions") {
    val board = FenCodec.startPos
    assertEquals(board.whiteKingSq, Squares.fromAlgebraic("e1"))
    assertEquals(board.blackKingSq, Squares.fromAlgebraic("e8"))
  }

  test("startpos has correct pieces") {
    val board = FenCodec.startPos
    // White back rank
    assertEquals(board.pieceAt(Squares.fromAlgebraic("a1")), Square.Occupied(Piece(PieceId.Rook, Color.White)))
    assertEquals(board.pieceAt(Squares.fromAlgebraic("e1")), Square.Occupied(Piece(PieceId.King, Color.White)))
    // Black back rank
    assertEquals(board.pieceAt(Squares.fromAlgebraic("a8")), Square.Occupied(Piece(PieceId.Rook, Color.Black)))
    assertEquals(board.pieceAt(Squares.fromAlgebraic("e8")), Square.Occupied(Piece(PieceId.King, Color.Black)))
    // Pawns
    assertEquals(board.pieceAt(Squares.fromAlgebraic("e2")), Square.Occupied(Piece(PieceId.Pawn, Color.White)))
    assertEquals(board.pieceAt(Squares.fromAlgebraic("e7")), Square.Occupied(Piece(PieceId.Pawn, Color.Black)))
    // Empty center
    assertEquals(board.pieceAt(Squares.fromAlgebraic("e4")), Square.Empty)
  }

  test("invalid FEN returns Left") {
    assert(FenCodec.boardFromFen("bad fen").isLeft)
  }
