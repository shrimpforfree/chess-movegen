import { NextResponse } from "next/server";

const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

export async function GET() {
  try {
    const res = await fetch(`${ENGINE_URL}/fusion/roll`);
    return NextResponse.json(await res.json());
  } catch {
    return NextResponse.json({ error: "Engine unavailable" }, { status: 502 });
  }
}
