import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";

// PUT /api/games/[id]/config — update engine config
export async function PUT(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const body = await req.json();
  const { target, config } = body; // target: "ai" | "white" | "black"

  const game = gameStore.updateConfig(id, target, config);
  if (!game) {
    return Response.json({ error: "Game not found" }, { status: 404 });
  }

  return Response.json({ ok: true });
}
