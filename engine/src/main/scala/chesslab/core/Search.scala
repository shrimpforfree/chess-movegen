package chesslab.core

import scala.util.boundary, boundary.break
import scala.util.Random

object Search:

  private val MateScore = 1_000_000
  private val Infinity  = 2_000_000
  private var drawScore = 0
  private var useHash = true
  private var nullMoveEnabled = true
  private var nullMoveR = 2
  private var nullMoveThreshold = 0
  private var skillNoise = 0          // centipawns of noise added at root (0 = full strength)
  private val rng = Random()

  // -------------------------------------------------------------------------
  // Piece value lookup for move ordering (indexed by PieceId)
  // -------------------------------------------------------------------------
  private val pieceValues: Map[PieceId, Int] = Map(
    PieceId.Pawn -> 100, PieceId.Knight -> 320, PieceId.Bishop -> 330,
    PieceId.Rook -> 500, PieceId.Queen -> 900, PieceId.King -> 0
  )

  /**
   * Score a move for ordering. Higher = try first.
   * Captures scored by MVV-LVA (Most Valuable Victim - Least Valuable Attacker).
   * Reads piece types from the BitBoard at Sq64 coordinates.
   */
  private def moveScore(bb: BitBoard, move: Move): Int =
    val victimValue = bb.pieceAt(move.to).map((k, _) => pieceValues.getOrElse(k, 0)).getOrElse(0)
    val attackerValue = bb.pieceAt(move.from).map((k, _) => pieceValues.getOrElse(k, 0)).getOrElse(0)

    if victimValue > 0 then
      victimValue * 10 - attackerValue + 10_000
    else if move.promo.isDefined then 9_000
    else 0

  /**
   * Check if side has only king and pawns — null move is unsafe in these positions.
   * With bitboards this is a single AND: (myPieces & ~pawns & ~kings) == 0.
   */
  private def hasOnlyKingAndPawns(bb: BitBoard, color: Color): Boolean =
    (bb.colorBB(color) & ~bb.pawns & ~bb.kings) == 0L

  /** Make a null move on the BitBoard — flip side, clear EP. */
  private def makeNullMove(bb: BitBoard): BitBoard =
    bb.copy(turn = bb.turn.opponent, epSquare = -1)

  /** Order moves, optionally putting a TT best move first. */
  private def orderMoves(bb: BitBoard, moves: Vector[Move], ttFrom: Int = -1, ttTo: Int = -1): Vector[Move] =
    if ttFrom >= 0 then
      val (ttMoves, rest) = moves.partition(m => m.from == ttFrom && m.to == ttTo)
      ttMoves ++ rest.sortBy(m => -moveScore(bb, m))
    else
      moves.sortBy(m => -moveScore(bb, m))

  /**
   * Quiescence search — continue searching captures at depth 0
   * to avoid horizon effect.
   */
  private def quiescence(bb: BitBoard, alpha: Int, beta: Int): Int =
    val standPat = Eval.evaluate(bb)
    if standPat >= beta then beta
    else
      val newAlpha = math.max(alpha, standPat)
      val occ = bb.occupied

      val moves = BitLegal.legalMoves(bb)
      val captures = moves.filter { m =>
        (occ & (1L << m.to)) != 0L || m.promo.isDefined || m.flag == MoveFlag.EnPassant
      }
      val ordered = orderMoves(bb, captures)

      ordered.foldLeft(newAlpha) { (currentAlpha, move) =>
        if currentAlpha >= beta then currentAlpha
        else
          val score = -quiescence(bb.makeMove(move), -beta, -currentAlpha)
          math.max(currentAlpha, score)
      }

  /**
   * Negamax with alpha-beta pruning, move ordering, and transposition table.
   * Operates entirely on BitBoard with Sq64-indexed moves — no mailbox.
   */
  private def negamax(bb: BitBoard, depth: Int, alpha: Int, beta: Int, allowNullMove: Boolean = true): Int =
    boundary:
      if bb.halfmoveClock >= 100 then break(drawScore)

      val hash = if useHash then bb.hash else 0L

      // Probe transposition table
      val ttEntry = if useHash then TransTable.probe(hash) else None
      ttEntry.foreach { entry =>
        if entry.depth >= depth then
          entry.flag match
            case TransTable.Flag.Exact      => break(entry.score)
            case TransTable.Flag.LowerBound => if entry.score >= beta then break(entry.score)
            case TransTable.Flag.UpperBound => if entry.score <= alpha then break(entry.score)
      }

      // Null move pruning
      val inCheck = BitAttacks.isInCheck(bb.turn, bb)
      if nullMoveEnabled
        && allowNullMove
        && !inCheck
        && depth > nullMoveR
        && !hasOnlyKingAndPawns(bb, bb.turn)
        && Eval.evaluate(bb) >= nullMoveThreshold
      then
        val nullScore = -negamax(makeNullMove(bb), depth - 1 - nullMoveR, -beta, -beta + 1, allowNullMove = false)
        if nullScore >= beta then break(beta)

      val moves = BitLegal.legalMoves(bb)

      if moves.isEmpty then
        val score =
          if BitAttacks.isInCheck(bb.turn, bb) then -(MateScore - (100 - depth))
          else drawScore
        if useHash then TransTable.store(hash, depth, score, TransTable.Flag.Exact, -1, -1)
        break(score)

      if depth == 0 then
        break(quiescence(bb, alpha, beta))

      // Order moves — use TT best move if available
      val (ttFrom, ttTo) = ttEntry match
        case Some(e) if e.bestFrom >= 0 => (e.bestFrom, e.bestTo)
        case _ => (-1, -1)
      val ordered = orderMoves(bb, moves, ttFrom, ttTo)

      val (bestScore, bestMove, _) =
        ordered.foldLeft((-Infinity, ordered.head, alpha)) { case ((bestSc, bestMv, currentAlpha), move) =>
          if currentAlpha >= beta then (bestSc, bestMv, currentAlpha)
          else
            val score = -negamax(bb.makeMove(move), depth - 1, -beta, -currentAlpha)
            if score > bestSc then (score, move, math.max(currentAlpha, score))
            else (bestSc, bestMv, currentAlpha)
        }

      // Store in transposition table (Sq64 indices)
      if useHash then
        val flag =
          if bestScore <= alpha then TransTable.Flag.UpperBound
          else if bestScore >= beta then TransTable.Flag.LowerBound
          else TransTable.Flag.Exact
        TransTable.store(hash, depth, bestScore, flag, bestMove.from, bestMove.to)

      bestScore

  /**
   * Find the best move using iterative deepening.
   * Internally uses BitBoard with Sq64 moves for speed.
   * Returns the move in mailbox coordinates for backward compatibility with Routes.
   */
  def bestMove(
    bb: BitBoard,
    maxDepth: Int,
    skillLevel: Int = 99,
    useBook: Boolean = true,
    useHashTable: Boolean = true,
    hashSizeMb: Int = 16,
    contempt: Int = 0,
    useNullMovePruning: Boolean = true,
    nullMoveReduction: Int = 2,
    nullMoveMinAdvantage: Int = 0
  ): Option[(Move, Int)] =
    val moves = BitLegal.legalMoves(bb)
    if moves.isEmpty then None
    else
      // Apply config
      val clampedSkill = math.max(1, math.min(99, skillLevel))
      // Level 99 = 0 noise (perfect), level 1 = 600cp noise (blunders heavily)
      skillNoise = ((99 - clampedSkill) * 600) / 98
      drawScore = -contempt
      useHash = useHashTable
      nullMoveEnabled = useNullMovePruning
      nullMoveR = nullMoveReduction
      nullMoveThreshold = nullMoveMinAdvantage
      if useHash then TransTable.resize(hashSizeMb)

      // Check opening book first
      val bookMove =
        if useBook then
          OpeningBook.lookup(bb).flatMap { uci =>
            moves.find(m => FenCodec.moveToUci(m) == uci).map(m => (m, 0))
          }
        else None

      if bookMove.isDefined then bookMove
      else
        TransTable.clear()

        // Search all depths (iterative deepening)
        val searchResult = (1 to maxDepth).foldLeft(Option.empty[(Move, Int)]) { (prev, depth) =>
          val ordered = prev match
            case Some((prevBest, _)) =>
              val (first, rest) = moves.partition(m =>
                m.from == prevBest.from && m.to == prevBest.to && m.promo == prevBest.promo)
              first ++ orderMoves(bb, rest)
            case None =>
              orderMoves(bb, moves)

          val (best, score, _) = ordered.foldLeft((ordered.head, -Infinity, -Infinity)) {
            case ((bestMove, bestScore, alpha), move) =>
              if alpha >= Infinity then (bestMove, bestScore, alpha)
              else
                val score = -negamax(bb.makeMove(move), depth - 1, -Infinity, -alpha)
                if score > bestScore then (move, score, math.max(alpha, score))
                else (bestMove, bestScore, alpha)
          }
          Some((best, score))
        }

        // Apply skill noise: re-evaluate root moves and pick with fuzzy scores
        if skillNoise > 0 && searchResult.isDefined then
          applySkillNoise(bb, moves, maxDepth)
        else searchResult

  /**
   * Re-score all root moves at the final depth and add random noise.
   * The "best" move under noise might not be the actual best — simulating mistakes.
   * Higher noise = bigger mistakes = lower skill level.
   */
  private def applySkillNoise(bb: BitBoard, moves: Vector[Move], depth: Int): Option[(Move, Int)] =
    val scored = moves.map { move =>
      val realScore = -negamax(bb.makeMove(move), depth - 1, -Infinity, Infinity)
      val noise = rng.nextInt(skillNoise * 2 + 1) - skillNoise
      (move, realScore, realScore + noise)
    }
    // Pick the move with the highest noisy score
    val best = scored.maxBy(_._3)
    Some((best._1, best._2))  // return the real score, not the noisy one
