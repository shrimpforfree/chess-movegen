import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";
import { makeMove, getAiMove } from "@/app/lib/engine-client";
import { GameSession } from "@/app/lib/types";

const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

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

  // Fusion mode uses board JSON endpoints (dynamic pieces don't have stable FEN chars)
  if (game.mode === "fusion" && game.boardJson) {
    return handleFusionMove(id, game, move);
  }

  // Standard FEN-based move
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
    (updatedGame.mode === "human-vs-ai") &&
    updatedGame.status !== "checkmate" &&
    updatedGame.status !== "stalemate"
  ) {
    await tryAiMove(id, updatedGame);
  }

  const finalGame = gameStore.get(id)!;
  return Response.json({
    fen: finalGame.fen,
    status: finalGame.status,
    moves: finalGame.moves,
    winner: finalGame.winner,
    eval: finalGame.eval,
    boardJson: finalGame.boardJson,
  });
}

/** Handle a move in fusion mode using board JSON endpoints. */
async function handleFusionMove(id: string, game: GameSession, move: string) {
  try {
    // Make the player's move
    const moveRes = await fetch(`${ENGINE_URL}/board/make-move`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ board: game.boardJson, move }),
    });
    if (!moveRes.ok) {
      const err = await moveRes.json();
      return Response.json({ error: err.error || "Invalid move" }, { status: 400 });
    }
    const moveData = await moveRes.json();

    game.boardJson = moveData.board;
    game.moves.push(move);
    const { status, winner } = parseEngineStatus(moveData.status);
    game.status = status;
    if (winner) game.winner = winner;

    // AI responds
    if (game.status !== "checkmate" && game.status !== "stalemate" && game.status !== "draw") {
      const aiConfig = game.aiConfig ?? { depth: game.aiDepth };
      const aiRes = await fetch(`${ENGINE_URL}/board/ai-move`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ board: game.boardJson, config: aiConfig }),
      });
      if (aiRes.ok) {
        const aiData = await aiRes.json();
        game.boardJson = aiData.board;
        game.moves.push(aiData.move);
        game.eval = aiData.eval;
        const aiStatus = parseEngineStatus(aiData.status);
        game.status = aiStatus.status;
        if (aiStatus.winner) game.winner = aiStatus.winner;
      }
    }

    return Response.json({
      fen: game.fen,
      status: game.status,
      moves: game.moves,
      winner: game.winner,
      eval: game.eval,
      boardJson: game.boardJson,
    });
  } catch {
    return Response.json({ error: "Engine unavailable" }, { status: 502 });
  }
}

function parseEngineStatus(engineStatus: string): { status: "in_progress" | "check" | "checkmate" | "stalemate" | "draw"; winner?: "white" | "black" } {
  if (engineStatus.startsWith("checkmate:")) return { status: "checkmate", winner: engineStatus.split(":")[1] as "white" | "black" };
  if (engineStatus === "stalemate") return { status: "stalemate" };
  if (engineStatus === "check") return { status: "check" };
  return { status: "in_progress" };
}

async function tryAiMove(id: string, game: NonNullable<ReturnType<typeof gameStore.get>>) {
  const sideToMove = game.fen.split(" ")[1];
  const aiColor = game.black === "ai" ? "b" : "w";
  if (sideToMove === aiColor) {
    try {
      const aiConfig = game.aiConfig ?? { depth: game.aiDepth };
      const aiResult = await getAiMove(game.fen, aiConfig);
      gameStore.applyMove(id, aiResult.fen, aiResult.move, aiResult.status, aiResult.eval);
    } catch {
      // AI failed — game continues
    }
  }
}
