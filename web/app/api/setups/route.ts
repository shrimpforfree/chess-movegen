import { NextResponse } from "next/server";
import { getSetups } from "../../lib/engine-client";

// GET /api/setups — proxy to engine's /setups endpoint
export async function GET() {
  try {
    const setups = await getSetups();
    return NextResponse.json({ setups });
  } catch {
    return NextResponse.json(
      { error: "Failed to fetch setups from engine" },
      { status: 502 }
    );
  }
}
