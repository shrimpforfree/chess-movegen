package chesslab.core

/**
 * Predefined game setups — starting positions for standard and fairy chess variants.
 * Each setup is a FEN string with a name and description.
 * The frontend lists these and lets the player pick before starting a game.
 */
object GameSetups:

  case class Setup(
    key: String,           // URL-safe identifier (e.g. "knightmare")
    name: String,          // display name (e.g. "Knightmare")
    description: String,   // short explanation of what's different
    fen: String            // starting position FEN
  )

  val all: Vector[Setup] = Vector(

    Setup(
      key = "standard",
      name = "Standard Chess",
      description = "Classic chess — the game you know.",
      fen = FenCodec.StartPosFen
    ),

    Setup(
      key = "knightmare",
      name = "Knightmare",
      description = "Knights replaced by Archbishops (bishop + knight combo). Diagonals are deadly.",
      fen = "rabqkbar/pppppppp/8/8/8/8/PPPPPPPP/RABQKBAR w KQkq - 0 1"
    ),

    Setup(
      key = "fortress",
      name = "Fortress",
      description = "Bishops replaced by Chancellors (rook + knight combo). Heavy artillery.",
      fen = "rncqkcnr/pppppppp/8/8/8/8/PPPPPPPP/RNCQKCNR w KQkq - 0 1"
    ),

    Setup(
      key = "supreme",
      name = "Supreme",
      description = "Queens replaced by Amazons (queen + knight combo). The ultimate piece.",
      fen = "rnbzkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBZKBNR w KQkq - 0 1"
    ),

    Setup(
      key = "wild",
      name = "Wild Cavalry",
      description = "Knights replaced by Camels — (1,3) leapers. Longer jumps, fewer squares.",
      fen = "rmbqkbmr/pppppppp/8/8/8/8/PPPPPPPP/RMBQKBMR w KQkq - 0 1"
    ),

    Setup(
      key = "exotic",
      name = "Exotic",
      description = "Knights replaced by Zebras (2,3 leaper), queen by Amazon. Unfamiliar territory.",
      fen = "rxbzkbxr/pppppppp/8/8/8/8/PPPPPPPP/RXBZKBXR w KQkq - 0 1"
    ),

    Setup(
      key = "manns-guard",
      name = "Mann's Guard",
      description = "Bishops replaced by Manns — move like kings but can be captured. Steady defenders.",
      fen = "rnoqkonr/pppppppp/8/8/8/8/PPPPPPPP/RNOQKONR w KQkq - 0 1"
    ),

    Setup(
      key = "chaos",
      name = "Chaos",
      description = "Archbishop, Chancellor, Amazon, Camel, Zebra, Mann — all on one board. Good luck.",
      fen = "raczkmxo/pppppppp/8/8/8/8/PPPPPPPP/RACZKMXO w KQkq - 0 1"
    ),

    // --- Silly standard-piece variants ---

    Setup(
      key = "queens-gambit",
      name = "Queen's Gambit",
      description = "Queens start in the center. The back rank is just vibes.",
      fen = "rnbk1bnr/pppppppp/3q4/8/8/3Q4/PPPPPPPP/RNB1KBNR w KQ - 0 1"
    ),

    Setup(
      key = "backwards",
      name = "Backwards",
      description = "Armies start on the wrong side. Pawns march the wrong way. Chaos.",
      fen = "RNBQKBNR/PPPPPPPP/8/8/8/8/pppppppp/rnbqkbnr w - - 0 1"
    ),

    Setup(
      key = "trench-war",
      name = "Trench War",
      description = "Both armies start in the middle, nose to nose. Immediately violent.",
      fen = "8/8/rnbqkbnr/pppppppp/PPPPPPPP/RNBQKBNR/8/8 w - - 0 1"
    ),

    Setup(
      key = "kings-crossing",
      name = "King's Crossing",
      description = "Kings start in the center of the board, face to face. Nowhere to hide.",
      fen = "rnbq1bnr/pppppppp/8/4k3/4K3/8/PPPPPPPP/RNBQ1BNR w - - 0 1"
    ),

    Setup(
      key = "peasant-revolt",
      name = "Peasant Revolt",
      description = "White has pawns, rooks, and a king. Black has the full army. Viva la revolución.",
      fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQ - 0 1"
    ),

    Setup(
      key = "tower-defense",
      name = "Tower Defense",
      description = "Double pawn wall with rooks and queen behind. Hold the line!",
      fen = "rnbqkbnr/pppppppp/8/8/PPPPPPPP/PPPPPPPP/8/R2QK2R w KQ - 0 1"
    ),

    Setup(
      key = "queen-army",
      name = "Queen Army",
      description = "Every piece is a queen. Total carnage by move 2.",
      fen = "qqqqkqqq/pppppppp/8/8/8/8/PPPPPPPP/QQQQKQQQ w - - 0 1"
    ),

    Setup(
      key = "the-wall",
      name = "The Wall",
      description = "Kings stare at each other across a wall of pawns. Claustrophobic.",
      fen = "4k3/pppppppp/8/PPPPPPPP/pppppppp/8/PPPPPPPP/4K3 w - - 0 1"
    ),

    Setup(
      key = "scramble",
      name = "Scramble",
      description = "Pieces shuffled randomly behind the pawns. No two games alike.",
      fen = "nbrqknrb/pppppppp/8/8/8/8/PPPPPPPP/NBRQKNRB w - - 0 1"
    ),

    Setup(
      key = "horde",
      name = "Horde",
      description = "White has 32 pawns. Black has a normal army. Swarm!",
      fen = "rnbqkbnr/pppppppp/8/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP/4K3 w - - 0 1"
    ),

    Setup(
      key = "boss-fight",
      name = "Boss Fight",
      description = "White has three queens, no pawns. Black has the full army. Go hunting.",
      fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/1Q1QKQ2 w - - 0 1"
    )
  )

  def findByKey(key: String): Option[Setup] = all.find(_.key == key)
