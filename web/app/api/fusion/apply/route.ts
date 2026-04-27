import { NextResponse } from "next/server";

const ENGINE_URL = process.env.ENGINE_URL || "http://localhost:8080";

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const res = await fetch(`${ENGINE_URL}/fusion/apply`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const err = await res.json();
      return NextResponse.json(err, { status: res.status });
    }
    return NextResponse.json(await res.json());
  } catch {
    return NextResponse.json({ error: "Engine unavailable" }, { status: 502 });
  }
}
