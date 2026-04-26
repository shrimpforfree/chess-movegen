export type GameMode = "human-vs-human" | "human-vs-ai";

export type GameStatus =
  | "waiting" // waiting for opponent to join
  | "in_progress"
  | "check"
  | "checkmate"
  | "stalemate";

export interface GameSession {
  id: string;
  fen: string;
  moves: string[]; // UCI move history
  mode: GameMode;
  status: GameStatus;
  white: string | null; // player token
  black: string | null;
  aiDepth: number;
  winner?: "white" | "black";
}

export interface GameEvent {
  type: "update" | "error";
  fen?: string;
  status?: GameStatus;
  lastMove?: string;
  winner?: string;
  error?: string;
}
