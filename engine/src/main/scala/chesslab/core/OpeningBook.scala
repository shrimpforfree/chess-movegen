package chesslab.core

import scala.util.Random

object OpeningBook:

  // Map of FEN (position only, no clocks) → weighted book moves
  // Multiple moves per position for variety
  private val book: Map[String, Vector[String]] = Map(

    // === Starting position ===
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -" -> Vector(
      "e2e4", "d2d4", "g1f3", "c2c4"
    ),

    // === 1. e4 responses ===
    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3" -> Vector(
      "e7e5", "c7c5", "e7e6", "c7c6", "d7d5"
    ),

    // === 1. e4 e5 — Open Game ===
    "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6" -> Vector(
      "g1f3" // King's Knight
    ),

    // 1. e4 e5 2. Nf3 — Black responses
    "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq -" -> Vector(
      "b8c6", "g8f6" // Nc6 (main) or Petroff
    ),

    // 1. e4 e5 2. Nf3 Nc6 — Italian/Spanish
    "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq -" -> Vector(
      "f1c4", "f1b5" // Italian or Ruy Lopez
    ),

    // Italian: 1. e4 e5 2. Nf3 Nc6 3. Bc4
    "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq -" -> Vector(
      "f8c5", "g8f6" // Giuoco Piano or Two Knights
    ),

    // Ruy Lopez: 1. e4 e5 2. Nf3 Nc6 3. Bb5
    "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq -" -> Vector(
      "a7a6", "g8f6", "f8c5"
    ),

    // === 1. e4 c5 — Sicilian ===
    "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6" -> Vector(
      "g1f3"
    ),

    // Sicilian 2. Nf3
    "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq -" -> Vector(
      "d7d6", "b8c6", "e7e6"
    ),

    // Sicilian Najdorf setup: 2...d6 3. d4
    "rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq -" -> Vector(
      "d2d4"
    ),

    // === 1. e4 e6 — French ===
    "rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq -" -> Vector(
      "d2d4"
    ),

    // French 2. d4
    "rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3" -> Vector(
      "d7d5"
    ),

    // === 1. e4 c6 — Caro-Kann ===
    "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq -" -> Vector(
      "d2d4"
    ),

    // === 1. d4 responses ===
    "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3" -> Vector(
      "d7d5", "g8f6"
    ),

    // 1. d4 d5 — Queen's Gambit
    "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq d6" -> Vector(
      "c2c4"
    ),

    // QG: 2. c4
    "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3" -> Vector(
      "e7e6", "c7c6", "d5c4" // QGD, Slav, QGA
    ),

    // 1. d4 Nf6 — Indian systems
    "rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq -" -> Vector(
      "c2c4"
    ),

    // 1. d4 Nf6 2. c4
    "rnbqkb1r/pppppppp/5n2/8/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3" -> Vector(
      "e7e6", "g7g6" // Nimzo/QID or King's Indian
    ),

    // King's Indian: 2...g6
    "rnbqkb1r/pppppp1p/5np1/8/2PP4/8/PP2PPPP/RNBQKBNR w KQkq -" -> Vector(
      "b1c3"
    ),

    // === 1. Nf3 ===
    "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq -" -> Vector(
      "d7d5", "g8f6", "c7c5"
    ),

    // === 1. c4 — English ===
    "rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq c3" -> Vector(
      "e7e5", "g8f6", "c7c5"
    ),
  )

  /** Normalize FEN to position-only key (drop halfmove clock and fullmove number) */
  private def fenKey(fen: String): String =
    fen.split(' ').take(4).mkString(" ")

  /**
   * Look up a book move for the given position.
   * Returns a random choice from available book moves, or None if not in book.
   */
  def lookup(bb: BitBoard): Option[String] =
    val fen = FenCodec.toFen(bb)
    val key = fenKey(fen)
    book.get(key).map { moves =>
      moves(Random.nextInt(moves.size))
    }
