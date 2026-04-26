import { gameStore } from "@/app/lib/game-store";
import { GameEvent } from "@/app/lib/types";

// GET /api/sse/[id] — Server-Sent Events stream for game updates
export async function GET(
  req: Request,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;

  const game = gameStore.get(id);
  if (!game) {
    return Response.json({ error: "Game not found" }, { status: 404 });
  }

  const encoder = new TextEncoder();

  const stream = new ReadableStream({
    start(controller) {
      // Send current state immediately
      const initial: GameEvent = {
        type: "update",
        fen: game.fen,
        status: game.status,
      };
      controller.enqueue(
        encoder.encode(`data: ${JSON.stringify(initial)}\n\n`)
      );

      const listener = (event: GameEvent) => {
        try {
          controller.enqueue(
            encoder.encode(`data: ${JSON.stringify(event)}\n\n`)
          );
        } catch {
          // Stream closed
        }
      };

      gameStore.subscribe(id, listener);

      // Clean up when client disconnects
      req.signal.addEventListener("abort", () => {
        gameStore.unsubscribe(id, listener);
        try {
          controller.close();
        } catch {
          // Already closed
        }
      });
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    },
  });
}
