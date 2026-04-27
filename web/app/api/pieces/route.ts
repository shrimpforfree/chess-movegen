import { NextResponse } from "next/server";

const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

// GET /api/pieces — proxy to engine's /pieces endpoint
export async function GET() {
  try {
    const res = await fetch(`${ENGINE_URL}/pieces`);
    const data = await res.json();
    return NextResponse.json(data);
  } catch {
    return NextResponse.json(
      { error: "Failed to fetch piece info from engine" },
      { status: 502 }
    );
  }
}
