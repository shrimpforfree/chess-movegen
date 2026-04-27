const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

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

export async function getLegalMoves(fen: string): Promise<string[]> {
  const res = await fetch(`${ENGINE_URL}/legal-moves`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ fen }),
  });
  if (!res.ok) throw new Error(`Engine error: ${res.statusText}`);
  const data = await res.json();
  return data.moves;
}

export async function makeMove(
  fen: string,
  move: string
): Promise<{ fen: string; status: string }> {
  const res = await fetch(`${ENGINE_URL}/make-move`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ fen, move }),
  });
  if (!res.ok) {
    const err = await res.json();
    throw new Error(err.error || "Invalid move");
  }
  return res.json();
}

export interface GameSetup {
  key: string;
  name: string;
  description: string;
  fen: string;
}

export async function getSetups(): Promise<GameSetup[]> {
  const res = await fetch(`${ENGINE_URL}/setups`);
  if (!res.ok) throw new Error(`Engine error: ${res.statusText}`);
  const data = await res.json();
  return data.setups;
}

export async function getAiMove(
  fen: string,
  config?: EngineConfig
): Promise<{ move: string; fen: string; status: string; eval: number }> {
  const res = await fetch(`${ENGINE_URL}/ai-move`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ fen, config }),
  });
  if (!res.ok) {
    const err = await res.json();
    throw new Error(err.error || "AI error");
  }
  return res.json();
}
