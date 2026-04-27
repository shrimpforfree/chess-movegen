import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";
import { getLegalMoves } from "@/app/lib/engine-client";

// GET /api/games/[id] — get game state + legal moves
export async function GET(
  _req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const game = gameStore.get(id);

  if (!game) {
    return Response.json({ error: "Game not found" }, { status: 404 });
  }

  // Fetch legal moves for click-to-move highlighting
  let legalMovesList: string[] = [];
  try {
    if (game.mode === "fusion" && game.boardJson && game.fusionDraftDone) {
      // Fusion mode: use board JSON endpoint for legal moves
      const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";
      const res = await fetch(`${ENGINE_URL}/board/legal-moves`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(game.boardJson),
      });
      if (res.ok) {
        const data = await res.json();
        legalMovesList = data.moves || [];
      }
    } else {
      legalMovesList = await getLegalMoves(game.fen);
    }
  } catch {
    // Engine may not be running — not critical
  }

  return Response.json({
    id: game.id,
    fen: game.fen,
    moves: game.moves,
    mode: game.mode,
    status: game.status,
    winner: game.winner,
    hasWhite: game.white !== null,
    hasBlack: game.black !== null,
    eval: game.eval,
    legalMoves: legalMovesList,
    fusionUpgrade: game.fusionUpgrade,
    fusionDraftDone: game.fusionDraftDone,
    boardJson: game.boardJson,
  });
}

// PATCH /api/games/[id] — reclaim a seat (take control)
export async function PATCH(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const body = await req.json();
  const { color, playerToken } = body;

  if (color !== "white" && color !== "black") {
    return Response.json({ error: "Invalid color" }, { status: 400 });
  }

  const game = gameStore.reclaim(id, color, playerToken);
  if (!game) {
    return Response.json({ error: "Cannot reclaim this seat" }, { status: 400 });
  }

  return Response.json({ gameId: game.id, playerToken, color });
}

// POST /api/games/[id] — join a game as black
export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const body = await req.json();
  const playerToken =
    body.playerToken || Math.random().toString(36).substring(2, 12);

  const game = gameStore.join(id, playerToken);
  if (!game) {
    return Response.json(
      { error: "Game not found or already full" },
      { status: 400 }
    );
  }

  return Response.json({
    gameId: game.id,
    playerToken,
    color: "black",
  });
}
