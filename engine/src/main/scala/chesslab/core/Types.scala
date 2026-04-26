package chesslab.core

import scala.language.strictEquality

// ---------------------------------------------------------------------------
// Colors
// ---------------------------------------------------------------------------
enum Color derives CanEqual:
  case White, Black

  def opponent: Color = this match
    case White => Black
    case Black => White

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------
case class Piece(kind: PieceId, color: Color) derives CanEqual:
  def pieceDef: PieceDef = PieceRegistry(kind)

// ---------------------------------------------------------------------------
// Square contents on the 10×12 mailbox
// ---------------------------------------------------------------------------
enum Square derives CanEqual:
  case Empty
  case Offboard
  case Occupied(piece: Piece)

// ---------------------------------------------------------------------------
// Game status
// ---------------------------------------------------------------------------
enum DrawReason:
  case Stalemate
  case FiftyMoveRule
  case InsufficientMaterial
  case ThreefoldRepetition
  case Agreement

enum GameStatus:
  case InProgress(inCheck: Boolean = false)
  case Checkmate(winner: Color)
  case Draw(reason: DrawReason)

// ---------------------------------------------------------------------------
// Moves
// ---------------------------------------------------------------------------
enum MoveFlag derives CanEqual:
  case Normal, EnPassant, Castling

case class Move(
  from: Int,
  to: Int,
  promo: Option[PieceId] = None,
  flag: MoveFlag = MoveFlag.Normal
)

// ---------------------------------------------------------------------------
// Castling rights
// ---------------------------------------------------------------------------
case class CastlingRights(
  whiteKingside: Boolean,
  whiteQueenside: Boolean,
  blackKingside: Boolean,
  blackQueenside: Boolean
):
  def toFen: String =
    val sb = new StringBuilder
    if whiteKingside  then sb.append('K')
    if whiteQueenside then sb.append('Q')
    if blackKingside  then sb.append('k')
    if blackQueenside then sb.append('q')
    if sb.isEmpty then "-" else sb.toString

  def removeKing(color: Color): CastlingRights = color match
    case Color.White => copy(whiteKingside = false, whiteQueenside = false)
    case Color.Black => copy(blackKingside = false, blackQueenside = false)

  def removeKingside(color: Color): CastlingRights = color match
    case Color.White => copy(whiteKingside = false)
    case Color.Black => copy(blackKingside = false)

  def removeQueenside(color: Color): CastlingRights = color match
    case Color.White => copy(whiteQueenside = false)
    case Color.Black => copy(blackQueenside = false)

object CastlingRights:
  val none = CastlingRights(false, false, false, false)

  def fromFen(s: String): CastlingRights =
    if s == "-" then none
    else CastlingRights(
      whiteKingside  = s.contains('K'),
      whiteQueenside = s.contains('Q'),
      blackKingside  = s.contains('k'),
      blackQueenside = s.contains('q')
    )

// ---------------------------------------------------------------------------
// 10×12 mailbox square utilities & direction offsets
// ---------------------------------------------------------------------------
object Squares:
  // Square index = row * 10 + col
  // Rows 2-9 are the board, cols 1-8 are the board
  // Row 2 = rank 8 (black back rank), row 9 = rank 1 (white back rank)

  def row(sq: Int): Int = sq / 10
  def col(sq: Int): Int = sq % 10

  def isOnBoard(sq: Int): Boolean =
    val r = row(sq); val c = col(sq)
    r >= 2 && r <= 9 && c >= 1 && c <= 8

  def fromAlgebraic(s: String): Int =
    val file = s(0) - 'a' + 1   // a=1 .. h=8
    val rank = s(1).asDigit      // 1-8
    val r = 10 - rank            // rank 8 -> row 2, rank 1 -> row 9
    r * 10 + file

  def toAlgebraic(sq: Int): String =
    val r = row(sq); val c = col(sq)
    val file = ('a' + c - 1).toChar
    val rank = 10 - r
    s"$file$rank"

  // Named squares for castling
  val E1 = fromAlgebraic("e1") // 95
  val G1 = fromAlgebraic("g1") // 97
  val C1 = fromAlgebraic("c1") // 93
  val F1 = fromAlgebraic("f1") // 96
  val D1 = fromAlgebraic("d1") // 94
  val B1 = fromAlgebraic("b1") // 92
  val A1 = fromAlgebraic("a1") // 91
  val H1 = fromAlgebraic("h1") // 98
  val E8 = fromAlgebraic("e8") // 25
  val G8 = fromAlgebraic("g8") // 27
  val C8 = fromAlgebraic("c8") // 23
  val F8 = fromAlgebraic("f8") // 26
  val D8 = fromAlgebraic("d8") // 24
  val B8 = fromAlgebraic("b8") // 22
  val A8 = fromAlgebraic("a8") // 21
  val H8 = fromAlgebraic("h8") // 28

object Directions:
  val N  = -10
  val S  =  10
  val E  =   1
  val W  =  -1
  val NE =  -9
  val NW = -11
  val SE =  11
  val SW =   9

  val KnightOffsets = Vector(-21, -19, -12, -8, 8, 12, 19, 21)
  val KingOffsets   = Vector(N, S, E, W, NE, NW, SE, SW)
  val BishopDirs    = Vector(NE, NW, SE, SW)
  val RookDirs      = Vector(N, S, E, W)
  val QueenDirs     = KingOffsets
