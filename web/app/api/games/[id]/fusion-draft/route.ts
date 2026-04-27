import { NextRequest } from "next/server";
import { gameStore } from "@/app/lib/game-store";

const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

// POST /api/games/[id]/fusion-draft — apply upgrade to a piece, complete draft
export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> }
) {
  const { id } = await ctx.params;
  const body = await req.json();
  const { square, playerToken, skip } = body;

  const game = gameStore.get(id);
  if (!game) return Response.json({ error: "Game not found" }, { status: 404 });
  if (game.white !== playerToken) return Response.json({ error: "Not your game" }, { status: 403 });
  if (game.fusionDraftDone) return Response.json({ error: "Draft already completed" }, { status: 400 });

  // Skip — start with standard board, no upgrade applied
  if (skip) {
    const startBoard = {
      pieces: {
        a1: { kind: "rook", color: "white" }, b1: { kind: "knight", color: "white" },
        c1: { kind: "bishop", color: "white" }, d1: { kind: "queen", color: "white" },
        e1: { kind: "king", color: "white" }, f1: { kind: "bishop", color: "white" },
        g1: { kind: "knight", color: "white" }, h1: { kind: "rook", color: "white" },
        a2: { kind: "pawn", color: "white" }, b2: { kind: "pawn", color: "white" },
        c2: { kind: "pawn", color: "white" }, d2: { kind: "pawn", color: "white" },
        e2: { kind: "pawn", color: "white" }, f2: { kind: "pawn", color: "white" },
        g2: { kind: "pawn", color: "white" }, h2: { kind: "pawn", color: "white" },
        a8: { kind: "rook", color: "black" }, b8: { kind: "knight", color: "black" },
        c8: { kind: "bishop", color: "black" }, d8: { kind: "queen", color: "black" },
        e8: { kind: "king", color: "black" }, f8: { kind: "bishop", color: "black" },
        g8: { kind: "knight", color: "black" }, h8: { kind: "rook", color: "black" },
        a7: { kind: "pawn", color: "black" }, b7: { kind: "pawn", color: "black" },
        c7: { kind: "pawn", color: "black" }, d7: { kind: "pawn", color: "black" },
        e7: { kind: "pawn", color: "black" }, f7: { kind: "pawn", color: "black" },
        g7: { kind: "pawn", color: "black" }, h7: { kind: "pawn", color: "black" },
      } as Record<string, { kind: string; color: string }>,
      sideToMove: "white",
      castling: { whiteKingside: true, whiteQueenside: true, blackKingside: true, blackQueenside: true },
      epSquare: null,
      halfmoveClock: 0,
      fullmoveNumber: 1,
    };
    gameStore.completeFusionDraft(id, startBoard);
    return Response.json({ board: startBoard, skipped: true });
  }

  if (!game.fusionUpgrade) return Response.json({ error: "No upgrade rolled" }, { status: 400 });

  // Build the standard starting board as JSON
  const startBoard = {
    pieces: {
      a1: { kind: "rook", color: "white" }, b1: { kind: "knight", color: "white" },
      c1: { kind: "bishop", color: "white" }, d1: { kind: "queen", color: "white" },
      e1: { kind: "king", color: "white" }, f1: { kind: "bishop", color: "white" },
      g1: { kind: "knight", color: "white" }, h1: { kind: "rook", color: "white" },
      a2: { kind: "pawn", color: "white" }, b2: { kind: "pawn", color: "white" },
      c2: { kind: "pawn", color: "white" }, d2: { kind: "pawn", color: "white" },
      e2: { kind: "pawn", color: "white" }, f2: { kind: "pawn", color: "white" },
      g2: { kind: "pawn", color: "white" }, h2: { kind: "pawn", color: "white" },
      a8: { kind: "rook", color: "black" }, b8: { kind: "knight", color: "black" },
      c8: { kind: "bishop", color: "black" }, d8: { kind: "queen", color: "black" },
      e8: { kind: "king", color: "black" }, f8: { kind: "bishop", color: "black" },
      g8: { kind: "knight", color: "black" }, h8: { kind: "rook", color: "black" },
      a7: { kind: "pawn", color: "black" }, b7: { kind: "pawn", color: "black" },
      c7: { kind: "pawn", color: "black" }, d7: { kind: "pawn", color: "black" },
      e7: { kind: "pawn", color: "black" }, f7: { kind: "pawn", color: "black" },
      g7: { kind: "pawn", color: "black" }, h7: { kind: "pawn", color: "black" },
    } as Record<string, { kind: string; color: string }>,
    sideToMove: "white",
    castling: { whiteKingside: true, whiteQueenside: true, blackKingside: true, blackQueenside: true },
    epSquare: null,
    halfmoveClock: 0,
    fullmoveNumber: 1,
  };

  // Check the piece is white and not king
  const piece = startBoard.pieces[square];
  if (!piece) return Response.json({ error: `No piece on ${square}` }, { status: 400 });
  if (piece.color !== "white") return Response.json({ error: "Can only upgrade your own pieces" }, { status: 400 });
  if (piece.kind === "king") return Response.json({ error: "Cannot upgrade the king" }, { status: 400 });

  // Call the engine to apply the upgrade
  try {
    const res = await fetch(`${ENGINE_URL}/fusion/apply`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        board: startBoard,
        square,
        upgradeKey: game.fusionUpgrade.key,
      }),
    });

    if (!res.ok) {
      const err = await res.json();
      return Response.json({ error: err.error || "Upgrade failed" }, { status: 400 });
    }

    const data = await res.json();
    gameStore.completeFusionDraft(id, data.board);

    return Response.json({
      board: data.board,
      pieceName: data.pieceName,
      value: data.value,
      description: data.description,
      movesFrom: data.movesFrom,
    });
  } catch {
    return Response.json({ error: "Engine unavailable" }, { status: 502 });
  }
}
