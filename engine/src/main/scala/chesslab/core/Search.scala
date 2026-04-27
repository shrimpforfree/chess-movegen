package chesslab.core

import scala.util.boundary, boundary.break
import Square.*

object Search:

  private val MateScore = 1_000_000
  private val Infinity  = 2_000_000
  private var drawScore = 0           // set by bestMove from contempt config
  private var useHash = true          // set by bestMove from config
  private var nullMoveEnabled = true  // null move pruning on/off
  private var nullMoveR = 2           // depth reduction for null move
  private var nullMoveThreshold = 0   // min eval advantage (cp) to try null move

  /**
   * Score a move for ordering. Higher = try first.
   * Captures scored by MVV-LVA (Most Valuable Victim - Least Valuable Attacker).
   */
  private def moveScore(board: Board, move: Move): Int =
    val victimValue = board.squares(move.to) match
      case Occupied(p) => p.pieceDef.value
      case _ => 0
    val attackerValue = board.squares(move.from) match
      case Occupied(p) => p.pieceDef.value
      case _ => 0

    if victimValue > 0 then
      victimValue * 10 - attackerValue + 10_000 // captures first, prefer PxQ over QxQ
    else if move.promo.isDefined then 9_000      // promotions next
    else 0

  /** Check if side has only king and pawns (null move unsafe in these positions) */
  private def hasOnlyKingAndPawns(board: Board, color: Color): Boolean =
    (0 until 120).forall { sq =>
      board.squares(sq) match
        case Occupied(p) if p.color == color =>
          p.pieceDef.isRoyal || p.pieceDef.isPawn
        case _ => true
    }

  /** Make a null move — just flip the side to move and clear ep square */
  private def makeNullMove(board: Board): Board =
    Board(
      squares = board.squares,
      sideToMove = board.sideToMove.opponent,
      castling = board.castling,
      epSquare = None,
      halfmoveClock = board.halfmoveClock,
      fullmoveNumber = board.fullmoveNumber,
      whiteKingSq = board.whiteKingSq,
      blackKingSq = board.blackKingSq
    )

  /** Order moves, optionally putting a TT best move first */
  private def orderMoves(board: Board, moves: Vector[Move], ttFrom: Int = -1, ttTo: Int = -1): Vector[Move] =
    if ttFrom >= 0 then
      val (ttMoves, rest) = moves.partition(m => m.from == ttFrom && m.to == ttTo)
      ttMoves ++ rest.sortBy(m => -moveScore(board, m))
    else
      moves.sortBy(m => -moveScore(board, m))

  /**
   * Quiescence search — continue searching captures at depth 0
   * to avoid horizon effect (e.g. capturing a pawn then losing the queen).
   */
  private def quiescence(board: Board, alpha: Int, beta: Int): Int =
    val standPat = Eval.evaluate(board)
    if standPat >= beta then beta
    else
      val newAlpha = math.max(alpha, standPat)

      val moves = Legal.legalMoves(board)
      val captures = moves.filter { m =>
        board.squares(m.to) != Empty || m.promo.isDefined || m.flag == MoveFlag.EnPassant
      }
      val ordered = orderMoves(board, captures)

      ordered.foldLeft(newAlpha) { (currentAlpha, move) =>
        if currentAlpha >= beta then currentAlpha
        else
          val score = -quiescence(board.makeMove(move), -beta, -currentAlpha)
          math.max(currentAlpha, score)
      }

  /**
   * Negamax with alpha-beta pruning, move ordering, and transposition table.
   */
  private def negamax(board: Board, depth: Int, alpha: Int, beta: Int, allowNullMove: Boolean = true): Int =
    boundary:
      if board.halfmoveClock >= 100 then break(drawScore)

      val hash = if useHash then Zobrist.hash(board) else 0L

      // Probe transposition table
      val ttEntry = if useHash then TransTable.probe(hash) else None
      ttEntry.foreach { entry =>
        if entry.depth >= depth then
          entry.flag match
            case TransTable.Flag.Exact      => break(entry.score)
            case TransTable.Flag.LowerBound => if entry.score >= beta then break(entry.score)
            case TransTable.Flag.UpperBound => if entry.score <= alpha then break(entry.score)
      }

      // Null move pruning — never do two in a row (allowNullMove = false after a null move)
      val inCheck = Attacks.isInCheck(board, board.sideToMove)
      if nullMoveEnabled
        && allowNullMove
        && !inCheck
        && depth > nullMoveR
        && !hasOnlyKingAndPawns(board, board.sideToMove)
        && Eval.evaluate(board) >= nullMoveThreshold
      then
        val nullScore = -negamax(makeNullMove(board), depth - 1 - nullMoveR, -beta, -beta + 1, allowNullMove = false)
        if nullScore >= beta then break(beta)

      val moves = Legal.legalMoves(board)

      if moves.isEmpty then
        val score =
          if Attacks.isInCheck(board, board.sideToMove) then -(MateScore - (100 - depth))
          else drawScore
        if useHash then TransTable.store(hash, depth, score, TransTable.Flag.Exact, -1, -1)
        break(score)

      if depth == 0 then
        break(quiescence(board, alpha, beta))

      // Order moves — use TT best move if available
      val (ttFrom, ttTo) = ttEntry match
        case Some(e) if e.bestFrom >= 0 => (e.bestFrom, e.bestTo)
        case _ => (-1, -1)
      val ordered = orderMoves(board, moves, ttFrom, ttTo)

      val (bestScore, bestMove, _) =
        ordered.foldLeft((-Infinity, ordered.head, alpha)) { case ((bestSc, bestMv, currentAlpha), move) =>
          if currentAlpha >= beta then (bestSc, bestMv, currentAlpha)
          else
            val score = -negamax(board.makeMove(move), depth - 1, -beta, -currentAlpha)
            if score > bestSc then (score, move, math.max(currentAlpha, score))
            else (bestSc, bestMv, currentAlpha)
        }

      // Store in transposition table
      if useHash then
        val flag =
          if bestScore <= alpha then TransTable.Flag.UpperBound
          else if bestScore >= beta then TransTable.Flag.LowerBound
          else TransTable.Flag.Exact
        TransTable.store(hash, depth, bestScore, flag, bestMove.from, bestMove.to)

      bestScore

  /**
   * Find the best move using iterative deepening.
   * Searches depth 1, then 2, ... up to maxDepth.
   * Each iteration's best move is tried first in the next iteration.
   */
  def bestMove(
    board: Board,
    maxDepth: Int,
    useBook: Boolean = true,
    useHashTable: Boolean = true,
    hashSizeMb: Int = 16,
    contempt: Int = 0,
    useNullMovePruning: Boolean = true,
    nullMoveReduction: Int = 2,
    nullMoveMinAdvantage: Int = 0
  ): Option[(Move, Int)] =
    val moves = Legal.legalMoves(board)
    if moves.isEmpty then None
    else
      // Apply config
      drawScore = -contempt
      useHash = useHashTable
      nullMoveEnabled = useNullMovePruning
      nullMoveR = nullMoveReduction
      nullMoveThreshold = nullMoveMinAdvantage
      if useHash then TransTable.resize(hashSizeMb)

      // Check opening book first
      val bookMove =
        if useBook then
          OpeningBook.lookup(board).flatMap { uci =>
            moves.find(m => FenCodec.moveToUci(m) == uci).map(m => (m, 0))
          }
        else None
      if bookMove.isDefined then bookMove
      else
        TransTable.clear()
        // Iterative deepening search
        (1 to maxDepth).foldLeft(Option.empty[(Move, Int)]) { (prev, depth) =>
          val ordered = prev match
            case Some((prevBest, _)) =>
              val (first, rest) = moves.partition(m =>
                m.from == prevBest.from && m.to == prevBest.to && m.promo == prevBest.promo)
              first ++ orderMoves(board, rest)
            case None =>
              orderMoves(board, moves)

          val (best, score, _) = ordered.foldLeft((ordered.head, -Infinity, -Infinity)) {
            case ((bestMove, bestScore, alpha), move) =>
              if alpha >= Infinity then (bestMove, bestScore, alpha)
              else
                val score = -negamax(board.makeMove(move), depth - 1, -Infinity, -alpha)
                if score > bestScore then (move, score, math.max(alpha, score))
                else (bestMove, bestScore, alpha)
          }
          Some((best, score))
        }
