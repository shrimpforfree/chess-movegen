"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

export default function Home() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [joinId, setJoinId] = useState("");
  const [fen, setFen] = useState("");

  const createGame = async (mode: "human-vs-human" | "human-vs-ai" | "auto") => {
    setLoading(true);
    const playerToken = Math.random().toString(36).substring(2, 12);

    const res = await fetch("/api/games", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode, playerToken, aiDepth: 4, fen: fen.trim() || undefined }),
    });
    const data = await res.json();

    // Store token so the game page knows we're white
    localStorage.setItem(`game-${data.gameId}-token`, data.playerToken);
    localStorage.setItem(`game-${data.gameId}-color`, data.color);

    router.push(`/game/${data.gameId}`);
  };

  const joinGame = () => {
    if (joinId.trim()) {
      router.push(`/game/${joinId.trim()}`);
    }
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "100vh",
        gap: "32px",
        padding: "20px",
      }}
    >
      <h1 style={{ fontSize: "48px" }}>ChessLab</h1>

      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: "12px",
          width: "300px",
        }}
      >
        <button
          onClick={() => createGame("human-vs-human")}
          disabled={loading}
          style={{
            padding: "16px",
            fontSize: "18px",
            cursor: "pointer",
            border: "2px solid #333",
            borderRadius: "8px",
            background: "#fff",
            color: "#333",
          }}
        >
          Play vs Human
        </button>

        <button
          onClick={() => createGame("human-vs-ai")}
          disabled={loading}
          style={{
            padding: "16px",
            fontSize: "18px",
            cursor: "pointer",
            border: "2px solid #333",
            borderRadius: "8px",
            background: "#333",
            color: "#fff",
          }}
        >
          Play vs Computer
        </button>

        <button
          onClick={() => createGame("auto")}
          disabled={loading}
          style={{
            padding: "16px",
            fontSize: "18px",
            cursor: "pointer",
            border: "2px solid #666",
            borderRadius: "8px",
            background: "#f5f5f5",
            color: "#666",
          }}
        >
          Auto Play
        </button>
      </div>

      <div style={{ width: "300px" }}>
        <input
          type="text"
          placeholder="Custom FEN (optional)"
          value={fen}
          onChange={(e) => setFen(e.target.value)}
          style={{
            width: "100%",
            padding: "12px",
            fontSize: "13px",
            border: "1px solid #ccc",
            borderRadius: "8px",
            fontFamily: "monospace",
            boxSizing: "border-box",
          }}
        />
      </div>

      <div
        style={{
          display: "flex",
          gap: "8px",
          width: "300px",
        }}
      >
        <input
          type="text"
          placeholder="Game ID to join"
          value={joinId}
          onChange={(e) => setJoinId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && joinGame()}
          style={{
            flex: 1,
            padding: "12px",
            fontSize: "16px",
            border: "1px solid #ccc",
            borderRadius: "8px",
          }}
        />
        <button
          onClick={joinGame}
          style={{
            padding: "12px 20px",
            fontSize: "16px",
            cursor: "pointer",
            border: "2px solid #333",
            borderRadius: "8px",
            background: "#fff",
          }}
        >
          Join
        </button>
      </div>
    </div>
  );
}
