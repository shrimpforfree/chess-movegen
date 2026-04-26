"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import ChessBoard from "@/app/components/ChessBoard";

export default function GamePage() {
  const params = useParams<{ id: string }>();
  const gameId = params.id;

  const [playerToken, setPlayerToken] = useState<string | null>(null);
  const [playerColor, setPlayerColor] = useState<"white" | "black" | null>(
    null
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Check if we already have a token for this game in sessionStorage
    const storedToken = sessionStorage.getItem(`game-${gameId}-token`);
    const storedColor = sessionStorage.getItem(
      `game-${gameId}-color`
    ) as "white" | "black" | null;

    if (storedToken && storedColor) {
      setPlayerToken(storedToken);
      setPlayerColor(storedColor);
      setLoading(false);
      return;
    }

    // No stored token — try to join the game as black
    const token = Math.random().toString(36).substring(2, 12);
    fetch(`/api/games/${gameId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerToken: token }),
    })
      .then((res) => res.json())
      .then((data) => {
        if (data.error) {
          setError(data.error);
        } else {
          sessionStorage.setItem(`game-${gameId}-token`, data.playerToken);
          sessionStorage.setItem(`game-${gameId}-color`, data.color);
          setPlayerToken(data.playerToken);
          setPlayerColor(data.color);
        }
        setLoading(false);
      })
      .catch(() => {
        setError("Failed to join game");
        setLoading(false);
      });
  }, [gameId]);

  if (loading) return <div style={{ padding: "40px", textAlign: "center" }}>Loading...</div>;
  if (error) return <div style={{ padding: "40px", textAlign: "center", color: "red" }}>{error}</div>;
  if (!playerToken || !playerColor) return <div style={{ padding: "40px" }}>Unable to join game</div>;

  return (
    <div style={{ padding: "20px", display: "flex", flexDirection: "column", alignItems: "center" }}>
      <Link
        href="/"
        style={{
          alignSelf: "flex-start",
          marginBottom: "12px",
          padding: "8px 16px",
          fontSize: "14px",
          border: "1px solid #ccc",
          borderRadius: "6px",
          background: "#fff",
          color: "#333",
          textDecoration: "none",
          cursor: "pointer",
        }}
      >
        &larr; Home
      </Link>
      <h1 style={{ marginBottom: "8px" }}>Chess Game</h1>
      <p style={{ marginBottom: "16px", color: "#666", fontSize: "14px" }}>
        Playing as <strong>{playerColor}</strong> &middot; Game{" "}
        <code>{gameId}</code>
      </p>
      <ChessBoard
        gameId={gameId}
        playerToken={playerToken}
        playerColor={playerColor}
      />
    </div>
  );
}
