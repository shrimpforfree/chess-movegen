import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";
import { GameMode, FusionUpgrade } from "@/app/lib/types";

const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

// POST /api/games — create a new game
export async function POST(req: NextRequest) {
  const body = await req.json();
  const mode: GameMode = body.mode || "human-vs-human";
  const aiDepth: number = body.aiDepth || 6;
  const customFen: string | undefined = body.fen;

  const playerToken =
    body.playerToken || Math.random().toString(36).substring(2, 12);

  // For fusion mode, roll an upgrade from the engine
  let fusionUpgrade: FusionUpgrade | undefined;
  if (mode === "fusion") {
    try {
      const res = await fetch(`${ENGINE_URL}/fusion/roll`);
      const data = await res.json();
      fusionUpgrade = { ...data.upgrade, results: data.results };
    } catch {
      return Response.json({ error: "Engine unavailable" }, { status: 502 });
    }
  }

  const game = gameStore.create(mode, playerToken, aiDepth, customFen, fusionUpgrade);

  return Response.json({
    gameId: game.id,
    playerToken,
    color: "white",
  });
}
