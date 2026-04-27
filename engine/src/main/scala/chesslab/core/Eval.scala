package chesslab.core

import scala.annotation.tailrec

object Eval:

  // -------------------------------------------------------------------------
  // Piece values in centipawns
  // -------------------------------------------------------------------------
  private val PawnValue   = 100
  private val KnightValue = 320
  private val BishopValue = 330
  private val RookValue   = 500
  private val QueenValue  = 900

  // -------------------------------------------------------------------------
  // Piece-square tables indexed by Sq64 (from White's perspective).
  // Layout: rank 1 (A1-H1) at index 0-7, rank 8 (A8-H8) at index 56-63.
  // For Black pieces: use pst(sq ^ 56) to mirror vertically.
  // -------------------------------------------------------------------------

  // Encourages pawns to advance and control the center
  private val PawnPST: IArray[Int] = IArray(
    // Rank 1 — never occupied by a pawn
     0,  0,  0,  0,  0,  0,  0,  0,
    // Rank 2 — starting position
     5, 10, 10,-20,-20, 10, 10,  5,
    // Rank 3
     5, -5,-10,  0,  0,-10, -5,  5,
    // Rank 4
     0,  0,  0, 20, 20,  0,  0,  0,
    // Rank 5
     5,  5, 10, 25, 25, 10,  5,  5,
    // Rank 6
    10, 10, 20, 30, 30, 20, 10, 10,
    // Rank 7 — about to promote
    50, 50, 50, 50, 50, 50, 50, 50,
    // Rank 8 — never occupied by a pawn
     0,  0,  0,  0,  0,  0,  0,  0
  )

  // Knights are best in the center, terrible on the rim
  private val KnightPST: IArray[Int] = IArray(
   -50,-40,-30,-30,-30,-30,-40,-50,
   -40,-20,  0,  5,  5,  0,-20,-40,
   -30,  5, 10, 15, 15, 10,  5,-30,
   -30,  0, 15, 20, 20, 15,  0,-30,
   -30,  5, 15, 20, 20, 15,  5,-30,
   -30,  0, 10, 15, 15, 10,  0,-30,
   -40,-20,  0,  0,  0,  0,-20,-40,
   -50,-40,-30,-30,-30,-30,-40,-50
  )

  // Bishops like long diagonals, avoid corners/edges
  private val BishopPST: IArray[Int] = IArray(
   -20,-10,-10,-10,-10,-10,-10,-20,
   -10,  5,  0,  0,  0,  0,  5,-10,
   -10, 10, 10, 10, 10, 10, 10,-10,
   -10,  0, 10, 10, 10, 10,  0,-10,
   -10,  5,  5, 10, 10,  5,  5,-10,
   -10,  0,  5, 10, 10,  5,  0,-10,
   -10,  0,  0,  0,  0,  0,  0,-10,
   -20,-10,-10,-10,-10,-10,-10,-20
  )

  // Rooks like open files, 7th rank
  private val RookPST: IArray[Int] = IArray(
     0,  0,  0,  5,  5,  0,  0,  0,
    -5,  0,  0,  5,  5,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     5, 10, 10, 10, 10, 10, 10,  5,
     0,  0,  0,  0,  0,  0,  0,  0
  )

  // Queen follows similar pattern to bishop + rook combined
  private val QueenPST: IArray[Int] = IArray(
   -20,-10,-10, -5, -5,-10,-10,-20,
   -10,  0,  5,  0,  0,  0,  0,-10,
   -10,  5,  5,  5,  5,  5,  0,-10,
     0,  0,  5,  5,  5,  5,  0, -5,
    -5,  0,  5,  5,  5,  5,  0, -5,
   -10,  0,  5,  5,  5,  5,  0,-10,
   -10,  0,  0,  0,  0,  0,  0,-10,
   -20,-10,-10, -5, -5,-10,-10,-20
  )

  // King in middlegame: stay on back rank, castle, avoid center
  private val KingPST: IArray[Int] = IArray(
    20, 30, 10,  0,  0, 10, 30, 20,
    20, 20,  0,  0,  0,  0, 20, 20,
   -10,-20,-20,-20,-20,-20,-20,-10,
   -20,-30,-30,-40,-40,-30,-30,-20,
   -30,-40,-40,-50,-50,-40,-40,-30,
   -30,-40,-40,-50,-50,-40,-40,-30,
   -30,-40,-40,-50,-50,-40,-40,-30,
   -30,-40,-40,-50,-50,-40,-40,-30
  )

  // -------------------------------------------------------------------------
  // Evaluation — scans each piece bitboard with bit scanning (no loops over
  // 120 mailbox squares). For each piece: material value + PST bonus.
  // -------------------------------------------------------------------------

  /**
   * Evaluate a BitBoard from the side-to-move's perspective.
   * Positive = good for side to move.
   */
  def evaluate(bb: BitBoard): Int =
    val white = evalColor(bb, Color.White, 0)
    val black = evalColor(bb, Color.Black, 56)
    val score = white - black
    if bb.turn == Color.White then score else -score

  /** Sum material + PST for all pieces of one color. mirror = 0 for White, 56 for Black. */
  private def evalColor(bb: BitBoard, color: Color, mirror: Int): Int =
    val mine = bb.colorBB(color)
    evalPieces(bb.pawns   & mine, PawnValue,   PawnPST,   mirror, 0) +
    evalPieces(bb.knights & mine, KnightValue, KnightPST, mirror, 0) +
    evalPieces(bb.bishops & mine, BishopValue, BishopPST, mirror, 0) +
    evalPieces(bb.rooks   & mine, RookValue,   RookPST,   mirror, 0) +
    evalPieces(bb.queens  & mine, QueenValue,  QueenPST,  mirror, 0) +
    evalPieces(bb.kings   & mine, 0,           KingPST,   mirror, 0)

  /** Scan set bits, accumulating material + PST bonus for each piece. */
  @tailrec
  private def evalPieces(pieces: Long, value: Int, pst: IArray[Int], mirror: Int, acc: Int): Int =
    if pieces == 0L then acc
    else
      val sq = java.lang.Long.numberOfTrailingZeros(pieces)
      evalPieces(pieces & (pieces - 1), value, pst, mirror, acc + value + pst(sq ^ mirror))

  /** Backward-compatible wrapper: evaluate a mailbox Board via its embedded BitBoard. */
  def evaluate(board: Board): Int = evaluate(board.bb)
