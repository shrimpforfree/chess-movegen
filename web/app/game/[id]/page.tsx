"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import StandardGame from "@/app/components/StandardGame";
import FusionGame from "@/app/components/FusionGame";
import { FusionUpgrade } from "@/app/lib/types";

export default function GamePage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const gameId = params.id;

  const [playerToken, setPlayerToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [gameMode, setGameMode] = useState<string | null>(null);
  const [fusionUpgrade, setFusionUpgrade] = useState<FusionUpgrade | null>(null);

  const createNewGame = async () => {
    const token = Math.random().toString(36).substring(2, 12);
    const res = await fetch("/api/games", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: gameMode, playerToken: token, aiDepth: 4 }),
    });
    const data = await res.json();
    localStorage.setItem(`game-${data.gameId}-token`, data.playerToken);
    router.push(`/game/${data.gameId}`);
  };

  useEffect(() => {
    fetch(`/api/games/${gameId}`)
      .then(r => r.json())
      .then(data => {
        if (data.error) { router.replace("/"); return; }
        setGameMode(data.mode);
        if (data.fusionUpgrade) setFusionUpgrade(data.fusionUpgrade);

        const storedToken = localStorage.getItem(`game-${gameId}-token`);
        if (storedToken) {
          setPlayerToken(storedToken);
          setLoading(false);
          return;
        }

        // No token — this shouldn't happen for AI games, redirect home
        router.replace("/");
      })
      .catch(() => { setError("Failed to load game"); setLoading(false); });
  }, [gameId, router]);

  if (loading) return <div style={{ padding: "40px", textAlign: "center" }}>Loading...</div>;
  if (error) return <div style={{ padding: "40px", textAlign: "center", color: "red" }}>{error}</div>;
  if (!playerToken) return <div style={{ padding: "40px" }}>Unable to join game</div>;

  return (
    <div style={{ padding: "20px", display: "flex", flexDirection: "column", alignItems: "center" }}>
      <Link href="/" style={{
        alignSelf: "flex-start", marginBottom: "12px", padding: "8px 16px",
        fontSize: "14px", border: "1px solid #ccc", borderRadius: "6px",
        background: "#fff", color: "#333", textDecoration: "none",
      }}>
        &larr; Home
      </Link>

      {gameMode === "fusion" && fusionUpgrade ? (
        <FusionGame
          gameId={gameId}
          playerToken={playerToken}
          upgrade={fusionUpgrade}
          onNewGame={createNewGame}
        />
      ) : (
        <StandardGame
          gameId={gameId}
          playerToken={playerToken}
          playerColor="white"
          plusMode={gameMode === "human-vs-ai-plus"}
          onNewGame={createNewGame}
        />
      )}
    </div>
  );
}
