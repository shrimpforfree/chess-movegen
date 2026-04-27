import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";

type WheelOutcome =
  | { type: "coins"; amount: number }
  | { type: "spawn"; piece: string; fenChar: string }
  | { type: "teleport" }
  | { type: "lose_pawn" }
  | { type: "disaster" };

const OUTCOMES: { outcome: WheelOutcome; weight: number }[] = [
  { outcome: { type: "coins", amount: 15 }, weight: 20 },
  { outcome: { type: "coins", amount: 20 }, weight: 8 },
  { outcome: { type: "spawn", piece: "knight", fenChar: "N" }, weight: 10 },
  { outcome: { type: "spawn", piece: "bishop", fenChar: "B" }, weight: 8 },
  { outcome: { type: "spawn", piece: "rook", fenChar: "R" }, weight: 4 },
  { outcome: { type: "spawn", piece: "queen", fenChar: "Q" }, weight: 2 },
  { outcome: { type: "teleport" }, weight: 15 },
  { outcome: { type: "lose_pawn" }, weight: 20 },
  { outcome: { type: "disaster" }, weight: 13 },
];

function pickOutcome(): WheelOutcome {
  const totalWeight = OUTCOMES.reduce((s, o) => s + o.weight, 0);
  let roll = Math.random() * totalWeight;
  for (const entry of OUTCOMES) {
    roll -= entry.weight;
    if (roll <= 0) return entry.outcome;
  }
  return OUTCOMES[0].outcome;
}

/** Parse FEN into a map of square → piece char. */
function parseFen(fen: string): Map<string, string> {
  const pieces = new Map<string, string>();
  const rows = fen.split(" ")[0].split("/");
  for (let r = 0; r < 8; r++) {
    let file = 0;
    for (const ch of rows[r]) {
      if (ch >= "1" && ch <= "8") { file += parseInt(ch); }
      else { pieces.set(String.fromCharCode(97 + file) + (8 - r), ch); file++; }
    }
  }
  return pieces;
}

/** Rebuild FEN placement from piece map, keeping other FEN parts. */
function rebuildFen(fen: string, pieces: Map<string, string>): string {
  const parts = fen.split(" ");
  const rows: string[] = [];
  for (let rank = 8; rank >= 1; rank--) {
    let row = "";
    let empty = 0;
    for (let file = 0; file < 8; file++) {
      const sq = String.fromCharCode(97 + file) + rank;
      const p = pieces.get(sq);
      if (p) { if (empty > 0) { row += empty; empty = 0; } row += p; }
      else { empty++; }
    }
    if (empty > 0) row += empty;
    rows.push(row);
  }
  parts[0] = rows.join("/");
  return parts.join(" ");
}

function findEmptySquares(pieces: Map<string, string>, ranks: number[]): string[] {
  const result: string[] = [];
  for (const rank of ranks) {
    for (let file = 0; file < 8; file++) {
      const sq = String.fromCharCode(97 + file) + rank;
      if (!pieces.has(sq)) result.push(sq);
    }
  }
  return result;
}

function shuffle<T>(arr: T[]): T[] {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

function pick<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

// POST /api/games/[id]/mystery-wheel
export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const body = await req.json();
  const { playerToken } = body;

  const game = gameStore.get(id);
  if (!game) return Response.json({ error: "Game not found" }, { status: 404 });
  if (game.white !== playerToken) return Response.json({ error: "Not your game" }, { status: 403 });

  const outcome = pickOutcome();
  const pieces = parseFen(game.fen);

  if (outcome.type === "coins") {
    return Response.json({ outcome: "coins", amount: outcome.amount, fen: game.fen });
  }

  if (outcome.type === "spawn") {
    const empty = findEmptySquares(pieces, [3, 4, 5, 6]);
    if (empty.length === 0) {
      return Response.json({ outcome: "coins", amount: 15, fen: game.fen });
    }
    const sq = pick(empty);
    pieces.set(sq, outcome.fenChar);
    game.fen = rebuildFen(game.fen, pieces);
    return Response.json({ outcome: "spawn", piece: outcome.piece, square: sq, fen: game.fen });
  }

  if (outcome.type === "teleport") {
    // Pick a random white non-king, non-pawn piece and move it to a random empty square
    const movable = [...pieces.entries()].filter(
      ([, ch]) => ch >= "A" && ch <= "Z" && ch !== "K" && ch !== "P"
    );
    const empty = findEmptySquares(pieces, [1, 2, 3, 4, 5, 6, 7, 8]);
    if (movable.length === 0 || empty.length === 0) {
      return Response.json({ outcome: "coins", amount: 10, fen: game.fen });
    }
    const [fromSq, pieceChar] = pick(movable);
    const toSq = pick(empty);
    pieces.delete(fromSq);
    pieces.set(toSq, pieceChar);
    game.fen = rebuildFen(game.fen, pieces);
    const pieceName = { N: "knight", B: "bishop", R: "rook", Q: "queen" }[pieceChar] || "piece";
    return Response.json({ outcome: "teleport", piece: pieceName, from: fromSq, to: toSq, fen: game.fen });
  }

  if (outcome.type === "lose_pawn") {
    const pawns = [...pieces.entries()].filter(([, ch]) => ch === "P");
    if (pawns.length === 0) {
      return Response.json({ outcome: "coins", amount: 10, fen: game.fen });
    }
    const [sq] = pick(pawns);
    pieces.delete(sq);
    game.fen = rebuildFen(game.fen, pieces);
    return Response.json({ outcome: "lose_pawn", square: sq, fen: game.fen });
  }

  // Disaster: spawn up to 10 black queens
  const empty = shuffle(findEmptySquares(pieces, [3, 4, 5, 6, 7, 8]));
  const count = Math.min(10, empty.length);
  const squares: string[] = [];
  for (let i = 0; i < count; i++) {
    pieces.set(empty[i], "q");
    squares.push(empty[i]);
  }
  game.fen = rebuildFen(game.fen, pieces);
  return Response.json({ outcome: "disaster", squares, count, fen: game.fen });
}
