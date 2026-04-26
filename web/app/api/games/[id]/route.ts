import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";

// GET /api/games/[id] — get game state
export async function GET(
  _req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const game = gameStore.get(id);

  if (!game) {
    return Response.json({ error: "Game not found" }, { status: 404 });
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
  });
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
