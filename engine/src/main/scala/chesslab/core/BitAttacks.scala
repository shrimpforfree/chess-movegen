package chesslab.core

/**
 * Bitboard attack detection using the super-piece technique.
 *
 * To check if a square is attacked by a given color, we pretend a
 * "super piece" sits on that square and can move like every piece type.
 * If its attack bitboard intersects an actual enemy piece of that type,
 * the square is attacked. Each check is a single AND — no loops.
 *
 * Example: "is E4 attacked by a white knight?"
 *   KnightAttacks(E4) & white & knights  →  non-zero means yes
 *
 * Pawn check uses the REVERSE color's attack pattern: to find white pawns
 * attacking E4, look SE/SW from E4 (that's the Black pawn pattern) because
 * white pawns attack NE/NW from their position.
 */
object BitAttacks:

  /**
   * Is the given square attacked by any piece of `byColor`?
   *
   * Used by the legal move filter: after making a move, check if the
   * moving side's king square is attacked by the opponent. If yes,
   * the move is illegal (leaves king in check).
   *
   * @param sq   Sq64 index (0..63) of the square to check
   * @param byColor  the attacking color
   * @param bb   the board state
   * @return true if at least one piece of byColor attacks sq
   */
  def isAttacked(sq: Int, byColor: Color, bb: BitBoard): Boolean =
    val enemy = bb.colorBB(byColor)
    val occ = bb.occupied

    // Pawns — use opponent's attack pattern (reversed direction)
    val pawnIdx = if byColor == Color.White then 1 else 0
    if (Magics.PawnAttacks(pawnIdx)(sq) & enemy & bb.pawns) != 0L then return true

    // Knights — fixed jump pattern, no occupancy needed
    if (Magics.KnightAttacks(sq) & enemy & bb.knights) != 0L then return true

    // King — adjacent squares only (catches illegal king-next-to-king)
    if (Magics.KingAttacks(sq) & enemy & bb.kings) != 0L then return true

    // Bishops and queens — diagonal slider attacks (occupancy-dependent)
    if (Magics.bishopAttacks(sq, occ) & enemy & (bb.bishops | bb.queens)) != 0L then return true

    // Rooks and queens — orthogonal slider attacks (occupancy-dependent)
    (Magics.rookAttacks(sq, occ) & enemy & (bb.rooks | bb.queens)) != 0L

  /**
   * Is the given color's king currently in check?
   *
   * Convenience wrapper: finds the king square, then checks if the
   * opponent attacks it. Used after move generation to filter illegal moves.
   *
   * @param color  the color whose king we're checking
   * @param bb     the board state
   * @return true if the king of `color` is in check
   */
  def isInCheck(color: Color, bb: BitBoard): Boolean =
    isAttacked(bb.kingSq(color), color.opponent, bb)

  /**
   * Bitboard of all pieces (either color) that attack the given square.
   *
   * Useful for evaluation (e.g. counting attackers on a square for SEE —
   * static exchange evaluation) and for finding pinners/blockers.
   *
   * @param sq   Sq64 index of the target square
   * @param bb   the board state
   * @return a Long with bits set for every piece that attacks sq
   */
  def attacksTo(sq: Int, bb: BitBoard): Long =
    val occ = bb.occupied
    (Magics.PawnAttacks(1)(sq) & bb.pawns & bb.white) |   // white pawns attacking sq
    (Magics.PawnAttacks(0)(sq) & bb.pawns & bb.black) |   // black pawns attacking sq
    (Magics.KnightAttacks(sq) & bb.knights) |
    (Magics.KingAttacks(sq) & bb.kings) |
    (Magics.bishopAttacks(sq, occ) & (bb.bishops | bb.queens)) |
    (Magics.rookAttacks(sq, occ) & (bb.rooks | bb.queens))
