import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";
import { getAiMove } from "@/app/lib/engine-client";

const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

// POST /api/games/[id]/ai-respond — trigger AI to make its move
export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const game = gameStore.get(id);
  if (!game) return Response.json({ error: "Game not found" }, { status: 404 });

  if (game.status === "checkmate" || game.status === "stalemate" || game.status === "draw") {
    return Response.json({ error: "Game is over" }, { status: 400 });
  }

  // Fusion mode — use board JSON
  if (game.mode === "fusion" && game.boardJson) {
    try {
      const aiConfig = game.aiConfig ?? { depth: game.aiDepth };
      const res = await fetch(`${ENGINE_URL}/board/ai-move`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ board: game.boardJson, config: aiConfig }),
      });
      if (!res.ok) return Response.json({ error: "AI failed" }, { status: 500 });
      const data = await res.json();
      game.boardJson = data.board;
      game.moves.push(data.move);
      game.eval = data.eval;
      const { status, winner } = parseStatus(data.status);
      game.status = status;
      if (winner) game.winner = winner;

      return Response.json({
        move: data.move,
        fen: game.fen,
        status: game.status,
        winner: game.winner,
        eval: game.eval,
        boardJson: game.boardJson,
      });
    } catch {
      return Response.json({ error: "Engine unavailable" }, { status: 502 });
    }
  }

  // Standard mode — use FEN
  try {
    const aiConfig = game.aiConfig ?? { depth: game.aiDepth };
    const aiResult = await getAiMove(game.fen, aiConfig);
    gameStore.applyMove(id, aiResult.fen, aiResult.move, aiResult.status, aiResult.eval);

    const final = gameStore.get(id)!;
    return Response.json({
      move: aiResult.move,
      fen: final.fen,
      status: final.status,
      winner: final.winner,
      eval: final.eval,
    });
  } catch {
    return Response.json({ error: "Engine unavailable" }, { status: 502 });
  }
}

function parseStatus(s: string): { status: "in_progress" | "check" | "checkmate" | "stalemate" | "draw"; winner?: "white" | "black" } {
  if (s.startsWith("checkmate:")) return { status: "checkmate", winner: s.split(":")[1] as "white" | "black" };
  if (s === "stalemate") return { status: "stalemate" };
  if (s === "check") return { status: "check" };
  return { status: "in_progress" };
}
