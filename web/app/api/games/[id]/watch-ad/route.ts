import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";

/** Parse FEN piece placement into a set of occupied squares. */
function occupiedFromFen(fen: string): Set<string> {
  const placement = fen.split(" ")[0];
  const occupied = new Set<string>();
  const rows = placement.split("/");
  for (let r = 0; r < 8; r++) {
    let file = 0;
    for (const ch of rows[r]) {
      if (ch >= "1" && ch <= "8") {
        file += parseInt(ch);
      } else {
        const sq = String.fromCharCode(97 + file) + (8 - r);
        occupied.add(sq);
        file++;
      }
    }
  }
  return occupied;
}

/** Insert a white pawn into a FEN string at the given square. */
function addPawnToFen(fen: string, square: string): string {
  const parts = fen.split(" ");
  const rows = parts[0].split("/");
  const rank = 8 - parseInt(square[1]); // FEN row index (0 = rank 8)
  const file = square.charCodeAt(0) - 97;

  // Expand the row into an array of characters (pieces and '.' for empty)
  const expanded: string[] = [];
  for (const ch of rows[rank]) {
    if (ch >= "1" && ch <= "8") {
      for (let i = 0; i < parseInt(ch); i++) expanded.push(".");
    } else {
      expanded.push(ch);
    }
  }
  expanded[file] = "P"; // white pawn

  // Compress back to FEN notation
  let compressed = "";
  let empty = 0;
  for (const ch of expanded) {
    if (ch === ".") {
      empty++;
    } else {
      if (empty > 0) { compressed += empty; empty = 0; }
      compressed += ch;
    }
  }
  if (empty > 0) compressed += empty;

  rows[rank] = compressed;
  parts[0] = rows.join("/");
  return parts.join(" ");
}

// POST /api/games/[id]/watch-ad — spawn a free pawn on a random empty square
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

  // Find empty squares on ranks 3-6
  const occupied = game.boardJson
    ? new Set(Object.keys(game.boardJson.pieces))
    : occupiedFromFen(game.fen);

  const candidates: string[] = [];
  for (let rank = 3; rank <= 6; rank++) {
    for (let file = 0; file < 8; file++) {
      const sq = String.fromCharCode(97 + file) + rank;
      if (!occupied.has(sq)) candidates.push(sq);
    }
  }

  if (candidates.length === 0) {
    return Response.json({ error: "No empty squares available" }, { status: 400 });
  }

  const square = candidates[Math.floor(Math.random() * candidates.length)];

  // Update the game state
  if (game.boardJson) {
    game.boardJson.pieces[square] = { kind: "pawn", color: "white" };
    return Response.json({ square, boardJson: game.boardJson, fen: game.fen });
  } else {
    game.fen = addPawnToFen(game.fen, square);
    return Response.json({ square, fen: game.fen });
  }
}
