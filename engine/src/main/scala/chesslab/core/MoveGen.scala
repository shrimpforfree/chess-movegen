package chesslab.core

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import Square.*
import Directions.*

object MoveGen:

  private val PromoPieces = Vector(PieceId.Queen, PieceId.Rook, PieceId.Bishop, PieceId.Knight)

  private def isEnemy(board: Board, sq: Int, friendly: Color): Boolean =
    board.squares(sq) match
      case Occupied(p) => p.color != friendly
      case _           => false

  // -------------------------------------------------------------------------
  // Pawn moves (special-cased — too irregular for generic handling)
  // -------------------------------------------------------------------------
  private def pawnMoves(board: Board, sq: Int, color: Color, buf: ListBuffer[Move]): Unit =
    val (pushDir, captureDirs, startRow, promoRow) =
      if color == Color.White then (N, Vector(NE, NW), 8, 2)
      else (S, Vector(SE, SW), 3, 9)

    def addPawnMove(from: Int, to: Int, flag: MoveFlag = MoveFlag.Normal): Unit =
      if Squares.row(to) == promoRow then
        for kind <- PromoPieces do
          buf += Move(from, to, promo = Some(kind), flag = flag)
      else
        buf += Move(from, to, flag = flag)

    // Single push
    val pushSq = sq + pushDir
    if board.squares(pushSq) == Empty then
      addPawnMove(sq, pushSq)
      // Double push
      if Squares.row(sq) == startRow then
        val doubleSq = pushSq + pushDir
        if board.squares(doubleSq) == Empty then
          buf += Move(sq, doubleSq)

    // Captures
    for dir <- captureDirs do
      val capSq = sq + dir
      if isEnemy(board, capSq, color) then
        addPawnMove(sq, capSq)

    // En passant
    board.epSquare.foreach { epSq =>
      for dir <- captureDirs do
        if sq + dir == epSq then
          buf += Move(sq, epSq, flag = MoveFlag.EnPassant)
    }

  // -------------------------------------------------------------------------
  // Jump moves (generic — handles knight, king normal moves, any jumper)
  // -------------------------------------------------------------------------
  private def jumpMoves(board: Board, sq: Int, color: Color, offsets: Vector[Int], buf: ListBuffer[Move]): Unit =
    for offset <- offsets do
      val to = sq + offset
      board.squares(to) match
        case Offboard    => ()
        case Empty       => buf += Move(sq, to)
        case Occupied(p) => if p.color != color then buf += Move(sq, to)

  // -------------------------------------------------------------------------
  // Sliding moves (generic — handles bishop, rook, queen, any slider)
  // -------------------------------------------------------------------------
  @tailrec
  private def slideInDir(board: Board, from: Int, sq: Int, dir: Int, color: Color, buf: ListBuffer[Move]): Unit =
    board.squares(sq) match
      case Offboard => ()
      case Empty =>
        buf += Move(from, sq)
        slideInDir(board, from, sq + dir, dir, color, buf)
      case Occupied(p) =>
        if p.color != color then buf += Move(from, sq)

  private def slidingMoves(board: Board, sq: Int, color: Color, dirs: Vector[Int], buf: ListBuffer[Move]): Unit =
    for dir <- dirs do
      slideInDir(board, sq, sq + dir, dir, color, buf)

  // -------------------------------------------------------------------------
  // Castling (special-cased — multi-piece interaction)
  // -------------------------------------------------------------------------
  private def castlingMoves(board: Board, sq: Int, color: Color, buf: ListBuffer[Move]): Unit =
    if color == Color.White then
      if board.castling.whiteKingside then
        if board.squares(Squares.F1) == Empty && board.squares(Squares.G1) == Empty then
          if !Attacks.isSquareAttacked(board, Squares.E1, Color.Black)
            && !Attacks.isSquareAttacked(board, Squares.F1, Color.Black)
            && !Attacks.isSquareAttacked(board, Squares.G1, Color.Black) then
            buf += Move(Squares.E1, Squares.G1, flag = MoveFlag.Castling)
      if board.castling.whiteQueenside then
        if board.squares(Squares.D1) == Empty && board.squares(Squares.C1) == Empty && board.squares(Squares.B1) == Empty then
          if !Attacks.isSquareAttacked(board, Squares.E1, Color.Black)
            && !Attacks.isSquareAttacked(board, Squares.D1, Color.Black)
            && !Attacks.isSquareAttacked(board, Squares.C1, Color.Black) then
            buf += Move(Squares.E1, Squares.C1, flag = MoveFlag.Castling)
    else
      if board.castling.blackKingside then
        if board.squares(Squares.F8) == Empty && board.squares(Squares.G8) == Empty then
          if !Attacks.isSquareAttacked(board, Squares.E8, Color.White)
            && !Attacks.isSquareAttacked(board, Squares.F8, Color.White)
            && !Attacks.isSquareAttacked(board, Squares.G8, Color.White) then
            buf += Move(Squares.E8, Squares.G8, flag = MoveFlag.Castling)
      if board.castling.blackQueenside then
        if board.squares(Squares.D8) == Empty && board.squares(Squares.C8) == Empty && board.squares(Squares.B8) == Empty then
          if !Attacks.isSquareAttacked(board, Squares.E8, Color.White)
            && !Attacks.isSquareAttacked(board, Squares.D8, Color.White)
            && !Attacks.isSquareAttacked(board, Squares.C8, Color.White) then
            buf += Move(Squares.E8, Squares.C8, flag = MoveFlag.Castling)

  // -------------------------------------------------------------------------
  // Generate all pseudo-legal moves
  // -------------------------------------------------------------------------
  def pseudoLegalMoves(board: Board): Vector[Move] =
    val buf = ListBuffer[Move]()
    val friendly = board.sideToMove

    for sq <- 0 until 120 do
      board.squares(sq) match
        case Occupied(piece) if piece.color == friendly =>
          val pd = piece.pieceDef
          if pd.isPawn then
            pawnMoves(board, sq, piece.color, buf)
          else
            if pd.jumps.nonEmpty then
              jumpMoves(board, sq, piece.color, pd.jumps, buf)
            if pd.slides.nonEmpty then
              slidingMoves(board, sq, piece.color, pd.slides, buf)
            if pd.isRoyal then
              castlingMoves(board, sq, piece.color, buf)
        case _ => ()

    buf.toVector
