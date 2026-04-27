import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";
import { makeMove, getAiMove } from "@/app/lib/engine-client";
// POST /api/games/[id]/move — make a move
export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const body = await req.json();
  const { playerToken, move } = body;

  const game = gameStore.get(id);
  if (!game) {
    return Response.json({ error: "Game not found" }, { status: 404 });
  }

  if (
    game.status === "checkmate" ||
    game.status === "stalemate" ||
    game.status === "draw"
  ) {
    return Response.json({ error: "Game is over" }, { status: 400 });
  }

  if (!gameStore.isPlayerTurn(game, playerToken)) {
    return Response.json({ error: "Not your turn" }, { status: 400 });
  }

  // Validate and apply move via the Scala engine
  let result;
  try {
    result = await makeMove(game.fen, move);
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : "Invalid move";
    return Response.json({ error: msg }, { status: 400 });
  }

  gameStore.applyMove(id, result.fen, move, result.status);

  // If human-vs-ai and it's now the AI's turn, make the AI move
  const updatedGame = gameStore.get(id)!;
  if (
    updatedGame.mode === "human-vs-ai" &&
    updatedGame.status !== "checkmate" &&
    updatedGame.status !== "stalemate"
  ) {
    const sideToMove = updatedGame.fen.split(" ")[1];
    const aiColor = updatedGame.black === "ai" ? "b" : "w";
    if (sideToMove === aiColor) {
      try {
        const aiConfig = updatedGame.aiConfig ?? { depth: updatedGame.aiDepth };
        const aiResult = await getAiMove(updatedGame.fen, aiConfig);
        gameStore.applyMove(
          id,
          aiResult.fen,
          aiResult.move,
          aiResult.status,
          aiResult.eval
        );
      } catch {
        // AI failed — game continues, player can retry
      }
    }
  }

  const finalGame = gameStore.get(id)!;
  return Response.json({
    fen: finalGame.fen,
    status: finalGame.status,
    moves: finalGame.moves,
    winner: finalGame.winner,
    eval: finalGame.eval,
  });
}
