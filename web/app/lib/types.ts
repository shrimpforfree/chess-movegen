export type GameMode = "human-vs-human" | "human-vs-ai" | "auto" | "fusion";

export interface FusionUpgrade {
  key: string;
  name: string;
  description: string;
  results: Record<string, string>; // pieceKind → resultPieceName (omits redundant pieces)
}

export interface BoardJson {
  pieces: Record<string, { kind: string; color: string }>;
  sideToMove: string;
  castling: { whiteKingside: boolean; whiteQueenside: boolean; blackKingside: boolean; blackQueenside: boolean };
  epSquare: string | null;
  halfmoveClock: number;
  fullmoveNumber: number;
}

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
  skillLevel?: number; // 1-99, default 99. Lower = more mistakes.
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
  fusionUpgrade?: FusionUpgrade; // the rolled upgrade for fusion mode
  fusionDraftDone?: boolean; // true after player assigns their upgrade
  boardJson?: BoardJson; // JSON board state for fusion mode (avoids FEN char issues)
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
