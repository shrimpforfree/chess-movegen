export type GameMode = "human-vs-human" | "human-vs-ai" | "auto";

export type GameStatus =
  | "waiting" // waiting for opponent to join
  | "in_progress"
  | "check"
  | "checkmate"
  | "stalemate"
  | "draw"; // threefold repetition, 50-move rule

export interface EngineConfig {
  depth?: number;
  timeMs?: number;
  useBook?: boolean;
  useHash?: boolean;
  hashSizeMb?: number;
  contempt?: number;
  useNullMove?: boolean;
  nullMoveDepthReduction?: number;
  nullMoveThreshold?: number;
}

export interface GameSession {
  id: string;
  fen: string;
  moves: string[]; // UCI move history
  mode: GameMode;
  status: GameStatus;
  white: string | null; // player token
  black: string | null;
  aiDepth: number;
  aiConfig?: EngineConfig; // for human-vs-ai
  whiteConfig?: EngineConfig; // for auto mode
  blackConfig?: EngineConfig; // for auto mode
  winner?: "white" | "black";
  eval?: number; // centipawns, from white's perspective
  positionHistory: Map<string, number>; // FEN position key → count
}

export interface GameEvent {
  type: "update" | "error";
  fen?: string;
  status?: GameStatus;
  lastMove?: string;
  winner?: string;
  eval?: number;
  error?: string;
}
