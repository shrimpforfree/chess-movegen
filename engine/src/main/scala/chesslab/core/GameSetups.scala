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
    )
  )

  def findByKey(key: String): Option[Setup] = all.find(_.key == key)
