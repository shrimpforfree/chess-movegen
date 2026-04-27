import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";
import { getAiMove } from "@/app/lib/engine-client";

// POST /api/games/[id]/auto-move — engine plays one move for the current side
export async function POST(
  _req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const game = gameStore.get(id);
  if (!game) {
    return Response.json({ error: "Game not found" }, { status: 404 });
  }

  if (game.mode !== "auto") {
    return Response.json({ error: "Not an auto game" }, { status: 400 });
  }

  if (game.status === "checkmate" || game.status === "stalemate" || game.status === "draw") {
    return Response.json({
      fen: game.fen,
      status: game.status,
      moves: game.moves,
      winner: game.winner,
      eval: game.eval,
      gameOver: true,
    });
  }

  const sideToMove = game.fen.split(" ")[1];
  const config = sideToMove === "w"
    ? (game.whiteConfig ?? { depth: 4 })
    : (game.blackConfig ?? { depth: 4 });

  try {
    const result = await getAiMove(game.fen, config);
    gameStore.applyMove(id, result.fen, result.move, result.status, result.eval);
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : "Engine error";
    return Response.json({ error: msg }, { status: 500 });
  }

  const updated = gameStore.get(id)!;
  return Response.json({
    fen: updated.fen,
    status: updated.status,
    moves: updated.moves,
    winner: updated.winner,
    eval: updated.eval,
    gameOver: updated.status === "checkmate" || updated.status === "stalemate" || updated.status === "draw",
  });
}
