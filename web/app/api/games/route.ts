import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";
import { GameMode } from "@/app/lib/types";

// POST /api/games — create a new game
export async function POST(req: NextRequest) {
  const body = await req.json();
  const mode: GameMode = body.mode || "human-vs-human";
  const aiDepth: number = body.aiDepth || 6;
  const customFen: string | undefined = body.fen;

  // Generate a player token (simple random string)
  const playerToken =
    body.playerToken || Math.random().toString(36).substring(2, 12);

  const game = gameStore.create(mode, playerToken, aiDepth, customFen);

  return Response.json({
    gameId: game.id,
    playerToken,
    color: "white",
  });
}
