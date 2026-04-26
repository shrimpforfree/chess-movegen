package chesslab.core

import Square.*

object Eval:

  // -------------------------------------------------------------------------
  // Piece-square tables (from White's perspective, indexed by mailbox square)
  // Bonus/penalty in centipawns for each square.
  // Black's tables are mirrored vertically.
  // -------------------------------------------------------------------------

  // Encourages pawns to advance and control the center
  private val PawnTable: Map[Int, Int] = Map(
    // rank 8 (row 2) — never occupied by a pawn
    // rank 7 (row 3) — about to promote
    31 -> 50, 32 -> 50, 33 -> 50, 34 -> 50, 35 -> 50, 36 -> 50, 37 -> 50, 38 -> 50,
    // rank 6 (row 4)
    41 -> 10, 42 -> 10, 43 -> 20, 44 -> 30, 45 -> 30, 46 -> 20, 47 -> 10, 48 -> 10,
    // rank 5 (row 5)
    51 ->  5, 52 ->  5, 53 -> 10, 54 -> 25, 55 -> 25, 56 -> 10, 57 ->  5, 58 ->  5,
    // rank 4 (row 6)
    61 ->  0, 62 ->  0, 63 ->  0, 64 -> 20, 65 -> 20, 66 ->  0, 67 ->  0, 68 ->  0,
    // rank 3 (row 7)
    71 ->  5, 72 -> -5, 73 -> -10, 74 ->  0, 75 ->  0, 76 -> -10, 77 -> -5, 78 ->  5,
    // rank 2 (row 8) — starting position
    81 ->  5, 82 -> 10, 83 -> 10, 84 -> -20, 85 -> -20, 86 -> 10, 87 -> 10, 88 ->  5,
    // rank 1 (row 9) — never occupied by a pawn
  ).withDefaultValue(0)

  // Knights are best in the center, terrible on the rim
  private val KnightTable: Map[Int, Int] = Map(
    21 -> -50, 22 -> -40, 23 -> -30, 24 -> -30, 25 -> -30, 26 -> -30, 27 -> -40, 28 -> -50,
    31 -> -40, 32 -> -20, 33 ->   0, 34 ->   0, 35 ->   0, 36 ->   0, 37 -> -20, 38 -> -40,
    41 -> -30, 42 ->   0, 43 ->  10, 44 ->  15, 45 ->  15, 46 ->  10, 47 ->   0, 48 -> -30,
    51 -> -30, 52 ->   5, 53 ->  15, 54 ->  20, 55 ->  20, 56 ->  15, 57 ->   5, 58 -> -30,
    61 -> -30, 62 ->   0, 63 ->  15, 64 ->  20, 65 ->  20, 66 ->  15, 67 ->   0, 68 -> -30,
    71 -> -30, 72 ->   5, 73 ->  10, 74 ->  15, 75 ->  15, 76 ->  10, 77 ->   5, 78 -> -30,
    81 -> -40, 82 -> -20, 83 ->   0, 84 ->   5, 85 ->   5, 86 ->   0, 87 -> -20, 88 -> -40,
    91 -> -50, 92 -> -40, 93 -> -30, 94 -> -30, 95 -> -30, 96 -> -30, 97 -> -40, 98 -> -50,
  ).withDefaultValue(0)

  // Bishops like long diagonals, avoid corners/edges
  private val BishopTable: Map[Int, Int] = Map(
    21 -> -20, 22 -> -10, 23 -> -10, 24 -> -10, 25 -> -10, 26 -> -10, 27 -> -10, 28 -> -20,
    31 -> -10, 32 ->   0, 33 ->   0, 34 ->   0, 35 ->   0, 36 ->   0, 37 ->   0, 38 -> -10,
    41 -> -10, 42 ->   0, 43 ->   5, 44 ->  10, 45 ->  10, 46 ->   5, 47 ->   0, 48 -> -10,
    51 -> -10, 52 ->   5, 53 ->   5, 54 ->  10, 55 ->  10, 56 ->   5, 57 ->   5, 58 -> -10,
    61 -> -10, 62 ->   0, 63 ->  10, 64 ->  10, 65 ->  10, 66 ->  10, 67 ->   0, 68 -> -10,
    71 -> -10, 72 ->  10, 73 ->  10, 74 ->  10, 75 ->  10, 76 ->  10, 77 ->  10, 78 -> -10,
    81 -> -10, 82 ->   5, 83 ->   0, 84 ->   0, 85 ->   0, 86 ->   0, 87 ->   5, 88 -> -10,
    91 -> -20, 92 -> -10, 93 -> -10, 94 -> -10, 95 -> -10, 96 -> -10, 97 -> -10, 98 -> -20,
  ).withDefaultValue(0)

  // Rooks like open files, 7th rank
  private val RookTable: Map[Int, Int] = Map(
    21 ->   0, 22 ->   0, 23 ->   0, 24 ->   0, 25 ->   0, 26 ->   0, 27 ->   0, 28 ->   0,
    31 ->   5, 32 ->  10, 33 ->  10, 34 ->  10, 35 ->  10, 36 ->  10, 37 ->  10, 38 ->   5,
    41 ->  -5, 42 ->   0, 43 ->   0, 44 ->   0, 45 ->   0, 46 ->   0, 47 ->   0, 48 ->  -5,
    51 ->  -5, 52 ->   0, 53 ->   0, 54 ->   0, 55 ->   0, 56 ->   0, 57 ->   0, 58 ->  -5,
    61 ->  -5, 62 ->   0, 63 ->   0, 64 ->   0, 65 ->   0, 66 ->   0, 67 ->   0, 68 ->  -5,
    71 ->  -5, 72 ->   0, 73 ->   0, 74 ->   0, 75 ->   0, 76 ->   0, 77 ->   0, 78 ->  -5,
    81 ->  -5, 82 ->   0, 83 ->   0, 84 ->   5, 85 ->   5, 86 ->   0, 87 ->   0, 88 ->  -5,
    91 ->   0, 92 ->   0, 93 ->   0, 94 ->   5, 95 ->   5, 96 ->   0, 97 ->   0, 98 ->   0,
  ).withDefaultValue(0)

  // Queen follows similar pattern to bishop + rook combined
  private val QueenTable: Map[Int, Int] = Map(
    21 -> -20, 22 -> -10, 23 -> -10, 24 ->  -5, 25 ->  -5, 26 -> -10, 27 -> -10, 28 -> -20,
    31 -> -10, 32 ->   0, 33 ->   0, 34 ->   0, 35 ->   0, 36 ->   0, 37 ->   0, 38 -> -10,
    41 -> -10, 42 ->   0, 43 ->   5, 44 ->   5, 45 ->   5, 46 ->   5, 47 ->   0, 48 -> -10,
    51 ->  -5, 52 ->   0, 53 ->   5, 54 ->   5, 55 ->   5, 56 ->   5, 57 ->   0, 58 ->  -5,
    61 ->   0, 62 ->   0, 63 ->   5, 64 ->   5, 65 ->   5, 66 ->   5, 67 ->   0, 68 ->  -5,
    71 -> -10, 72 ->   5, 73 ->   5, 74 ->   5, 75 ->   5, 76 ->   5, 77 ->   0, 78 -> -10,
    81 -> -10, 82 ->   0, 83 ->   5, 84 ->   0, 85 ->   0, 86 ->   0, 87 ->   0, 88 -> -10,
    91 -> -20, 92 -> -10, 93 -> -10, 94 ->  -5, 95 ->  -5, 96 -> -10, 97 -> -10, 98 -> -20,
  ).withDefaultValue(0)

  // King in middlegame: stay on back rank, castle, avoid center
  private val KingTable: Map[Int, Int] = Map(
    21 -> -30, 22 -> -40, 23 -> -40, 24 -> -50, 25 -> -50, 26 -> -40, 27 -> -40, 28 -> -30,
    31 -> -30, 32 -> -40, 33 -> -40, 34 -> -50, 35 -> -50, 36 -> -40, 37 -> -40, 38 -> -30,
    41 -> -30, 42 -> -40, 43 -> -40, 44 -> -50, 45 -> -50, 46 -> -40, 47 -> -40, 48 -> -30,
    51 -> -30, 52 -> -40, 53 -> -40, 54 -> -50, 55 -> -50, 56 -> -40, 57 -> -40, 58 -> -30,
    61 -> -20, 62 -> -30, 63 -> -30, 64 -> -40, 65 -> -40, 66 -> -30, 67 -> -30, 68 -> -20,
    71 -> -10, 72 -> -20, 73 -> -20, 74 -> -20, 75 -> -20, 76 -> -20, 77 -> -20, 78 -> -10,
    81 ->  20, 82 ->  20, 83 ->   0, 84 ->   0, 85 ->   0, 86 ->   0, 87 ->  20, 88 ->  20,
    91 ->  20, 92 ->  30, 93 ->  10, 94 ->   0, 95 ->   0, 96 ->  10, 97 ->  30, 98 ->  20,
  ).withDefaultValue(0)

  private val pieceSquareTables: Map[PieceId, Map[Int, Int]] = Map(
    PieceId.Pawn   -> PawnTable,
    PieceId.Knight -> KnightTable,
    PieceId.Bishop -> BishopTable,
    PieceId.Rook   -> RookTable,
    PieceId.Queen  -> QueenTable,
    PieceId.King   -> KingTable,
  )

  /** Mirror a square vertically (swap rank). Used for black piece-square lookup. */
  private def mirror(sq: Int): Int =
    val row = Squares.row(sq)
    val col = Squares.col(sq)
    val mirroredRow = 11 - row  // row 2 <-> row 9, row 3 <-> row 8, etc.
    mirroredRow * 10 + col

  /**
   * Evaluate a position from the side-to-move's perspective.
   * Positive = good for side to move, negative = bad.
   */
  def evaluate(board: Board): Int =
    val score = (0 until 120).foldLeft(0) { (acc, sq) =>
      board.squares(sq) match
        case Occupied(piece) =>
          val pd = piece.pieceDef
          val material = pd.value

          // Piece-square bonus (tables are from White's perspective)
          val psq = pieceSquareTables.get(piece.kind) match
            case Some(table) =>
              if piece.color == Color.White then table(sq)
              else table(mirror(sq))
            case None => 0

          val pieceScore = material + psq
          if piece.color == Color.White then acc + pieceScore
          else acc - pieceScore
        case _ => acc
    }

    // Return from side-to-move's perspective
    if board.sideToMove == Color.White then score else -score
