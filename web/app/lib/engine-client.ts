const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

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

export async function getAiMove(
  fen: string,
  depth: number
): Promise<{ move: string; fen: string; status: string }> {
  const res = await fetch(`${ENGINE_URL}/ai-move`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ fen, depth }),
  });
  if (!res.ok) {
    const err = await res.json();
    throw new Error(err.error || "AI error");
  }
  return res.json();
}
