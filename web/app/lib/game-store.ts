import { GameSession, GameEvent, GameMode, EngineConfig } from "./types";

const games = new Map<string, GameSession>();
const listeners = new Map<string, Set<(event: GameEvent) => void>>();

function generateId(): string {
  return Math.random().toString(36).substring(2, 10);
}

/** Extract position key from FEN (excludes halfmove and fullmove counters) */
function positionKey(fen: string): string {
  return fen.split(" ").slice(0, 4).join(" ");
}

function parseStatus(engineStatus: string): {
  status: GameSession["status"];
  winner?: "white" | "black";
} {
  if (engineStatus.startsWith("checkmate:")) {
    return {
      status: "checkmate",
      winner: engineStatus.split(":")[1] as "white" | "black",
    };
  }
  if (engineStatus === "stalemate") return { status: "stalemate" };
  if (engineStatus === "check") return { status: "check" };
  return { status: "in_progress" };
}

function notify(gameId: string, event: GameEvent) {
  const subs = listeners.get(gameId);
  if (subs) {
    for (const cb of subs) cb(event);
  }
}

export const gameStore = {
  create(mode: GameMode, playerToken: string, aiDepth = 6, customFen?: string): GameSession {
    const id = generateId();
    const startFen = customFen ||
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    const session: GameSession = {
      id,
      fen: startFen,
      moves: [],
      mode,
      status: mode === "human-vs-ai" || mode === "auto" ? "in_progress" : "waiting",
      white: playerToken,
      black: mode === "human-vs-ai" ? "ai" : mode === "auto" ? "auto" : null,
      aiDepth,
      positionHistory: new Map([[positionKey(startFen), 1]]),
    };
    games.set(id, session);
    return session;
  },

  get(id: string): GameSession | undefined {
    return games.get(id);
  },

  join(id: string, playerToken: string): GameSession | null {
    const game = games.get(id);
    if (!game) return null;
    if (game.black !== null) return null; // already full
    game.black = playerToken;
    game.status = "in_progress";
    notify(id, {
      type: "update",
      fen: game.fen,
      status: game.status,
    });
    return game;
  },

  applyMove(
    gameId: string,
    newFen: string,
    moveUci: string,
    engineStatus: string,
    evalScore?: number
  ): GameSession | null {
    const game = games.get(gameId);
    if (!game) return null;

    const { status, winner } = parseStatus(engineStatus);
    game.fen = newFen;
    game.moves.push(moveUci);
    if (evalScore !== undefined) game.eval = evalScore;

    // Check threefold repetition
    const key = positionKey(newFen);
    const count = (game.positionHistory.get(key) || 0) + 1;
    game.positionHistory.set(key, count);

    if (count >= 3) {
      game.status = "draw";
    } else {
      game.status = status;
      if (winner) game.winner = winner;
    }

    notify(gameId, {
      type: "update",
      fen: game.fen,
      status: game.status,
      lastMove: moveUci,
      winner,
      eval: game.eval,
    });

    return game;
  },

  updateConfig(id: string, target: "ai" | "white" | "black", config: EngineConfig): GameSession | null {
    const game = games.get(id);
    if (!game) return null;
    if (target === "ai") game.aiConfig = config;
    else if (target === "white") game.whiteConfig = config;
    else game.blackConfig = config;
    return game;
  },

  subscribe(id: string, cb: (event: GameEvent) => void) {
    if (!listeners.has(id)) listeners.set(id, new Set());
    listeners.get(id)!.add(cb);
  },

  unsubscribe(id: string, cb: (event: GameEvent) => void) {
    listeners.get(id)?.delete(cb);
  },

  /** Reclaim a seat — replaces the existing token for that color */
  reclaim(id: string, color: "white" | "black", newToken: string): GameSession | null {
    const game = games.get(id);
    if (!game) return null;
    if (color === "white") game.white = newToken;
    else if (color === "black" && game.black !== "ai") game.black = newToken;
    else return null;
    return game;
  },

  /** Get which color a player token is in a game */
  playerColor(
    game: GameSession,
    token: string
  ): "white" | "black" | null {
    if (game.white === token) return "white";
    if (game.black === token) return "black";
    return null;
  },

  /** Check if it's a given player's turn */
  isPlayerTurn(game: GameSession, token: string): boolean {
    const color = this.playerColor(game, token);
    if (!color) return false;
    // FEN's side-to-move field: "w" or "b"
    const sideToMove = game.fen.split(" ")[1];
    return (
      (color === "white" && sideToMove === "w") ||
      (color === "black" && sideToMove === "b")
    );
  },
};
