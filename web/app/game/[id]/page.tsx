"use client";

import { useEffect, useState, useRef } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import ChessBoard from "@/app/components/ChessBoard";
import AutoPlayPanel, { type AutoPlayRef } from "@/app/components/AutoPlayPanel";
import AiConfigPanel from "@/app/components/AiConfigPanel";
import RulesPanel from "@/app/components/RulesPanel";
import FusionDraftPanel from "@/app/components/FusionDraftPanel";
import type { FusionDraftRef } from "@/app/components/FusionDraftPanel";

export default function GamePage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const gameId = params.id;

  const [playerToken, setPlayerToken] = useState<string | null>(null);
  const [playerColor, setPlayerColor] = useState<"white" | "black" | null>(
    null
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [needsReclaim, setNeedsReclaim] = useState(false);
  const [gameMode, setGameMode] = useState<string | null>(null);
  const [autoRunning, setAutoRunning] = useState(false);
  const [currentFen, setCurrentFen] = useState("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
  const [fusionUpgrade, setFusionUpgrade] = useState<{ key: string; name: string; description: string } | null>(null);
  const [fusionDraftDone, setFusionDraftDone] = useState(false);
  const autoRef = useRef<AutoPlayRef>(null);
  const fusionRef = useRef<FusionDraftRef>(null);

  const handleFusionSquareClick = (square: string) => {
    fusionRef.current?.applyToSquare(square);
  };

  const reclaim = async (color: "white" | "black") => {
    const token = Math.random().toString(36).substring(2, 12);
    const res = await fetch(`/api/games/${gameId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ color, playerToken: token }),
    });
    const data = await res.json();
    if (data.error) {
      setError(data.error);
      return;
    }
    localStorage.setItem(`game-${gameId}-token`, data.playerToken);
    localStorage.setItem(`game-${gameId}-color`, data.color);
    setPlayerToken(data.playerToken);
    setPlayerColor(data.color);
    setNeedsReclaim(false);
  };

  const createNewGame = async () => {
    const playerToken = Math.random().toString(36).substring(2, 12);
    const res = await fetch("/api/games", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: gameMode, playerToken, aiDepth: 4 }),
    });
    const data = await res.json();
    localStorage.setItem(`game-${data.gameId}-token`, data.playerToken);
    localStorage.setItem(`game-${data.gameId}-color`, data.color);
    router.push(`/game/${data.gameId}`);
  };

  useEffect(() => {
    // First, fetch game info to check mode
    fetch(`/api/games/${gameId}`)
      .then((res) => res.json())
      .then((gameData) => {
        if (gameData.error) {
          router.replace("/");
          return;
        }

        setGameMode(gameData.mode);

        // Fusion mode — load upgrade info
        if (gameData.fusionUpgrade) {
          setFusionUpgrade(gameData.fusionUpgrade);
          setFusionDraftDone(gameData.fusionDraftDone ?? false);
        }

        // Auto mode — spectator view, no joining needed
        if (gameData.mode === "auto") {
          setPlayerToken("spectator");
          setPlayerColor("white");
          setLoading(false);
          return;
        }

        // Check if we already have a token for this game in localStorage
        const storedToken = localStorage.getItem(`game-${gameId}-token`);
        const storedColor = localStorage.getItem(
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
              setNeedsReclaim(true);
              setLoading(false);
              return;
            }
            localStorage.setItem(`game-${gameId}-token`, data.playerToken);
            localStorage.setItem(`game-${gameId}-color`, data.color);
            setPlayerToken(data.playerToken);
            setPlayerColor(data.color);
            setLoading(false);
          })
          .catch(() => {
            setError("Failed to join game");
            setLoading(false);
          });
      })
      .catch(() => {
        setError("Failed to load game");
        setLoading(false);
      });
  }, [gameId]);

  if (loading) return <div style={{ padding: "40px", textAlign: "center" }}>Loading...</div>;
  if (error) return <div style={{ padding: "40px", textAlign: "center", color: "red" }}>{error}</div>;

  if (needsReclaim) {
    const isAiGame = gameMode === "human-vs-ai";
    return (
      <div style={{ padding: "40px", textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center", gap: "16px" }}>
        <h2>Rejoin Game</h2>
        <p style={{ color: "#666" }}>Choose which side to play.</p>
        <div style={{ display: "flex", gap: "12px" }}>
          <button
            onClick={() => reclaim("white")}
            style={{
              padding: "12px 24px",
              fontSize: "16px",
              cursor: "pointer",
              border: "2px solid #333",
              borderRadius: "8px",
              background: "#fff",
              color: "#333",
            }}
          >
            Play as White
          </button>
          {!isAiGame && (
            <button
              onClick={() => reclaim("black")}
              style={{
                padding: "12px 24px",
                fontSize: "16px",
                cursor: "pointer",
                border: "2px solid #333",
                borderRadius: "8px",
                background: "#333",
                color: "#fff",
              }}
            >
              Play as Black
            </button>
          )}
        </div>
        <Link href="/" style={{ color: "#666", fontSize: "14px" }}>
          &larr; Back to Home
        </Link>
      </div>
    );
  }

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
      <h1 style={{ marginBottom: "8px" }}>
        {gameMode === "fusion" && !fusionDraftDone ? "Fusion Draft" : gameMode === "auto" ? "Auto Play" : "Chess Game"}
      </h1>
      <p style={{ marginBottom: "16px", color: "#666", fontSize: "14px" }}>
        {gameMode === "fusion" && !fusionDraftDone ? (
          <>Select upgrade, click a piece on the board, then Start Game</>
        ) : gameMode === "auto" ? (
          <>Engine vs Engine &middot; Game <code>{gameId}</code></>
        ) : (
          <>Playing as <strong>{playerColor}</strong> &middot; Game{" "}<code>{gameId}</code></>
        )}
      </p>
      <div style={{ display: "flex", gap: "16px", alignItems: "flex-start" }}>
        <ChessBoard
          gameId={gameId}
          playerToken={playerToken}
          playerColor={playerColor}
          readonly={gameMode === "auto" || (gameMode === "fusion" && !fusionDraftDone)}
          onNewGame={createNewGame}
          onFenChange={setCurrentFen}
          onSquareClicked={gameMode === "fusion" && !fusionDraftDone ? handleFusionSquareClick : undefined}
        />
        <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          {gameMode === "fusion" && fusionUpgrade && !fusionDraftDone && playerToken && (
            <FusionDraftPanel
              ref={fusionRef}
              gameId={gameId}
              playerToken={playerToken}
              upgrade={fusionUpgrade}
              onDraftComplete={() => {
                setFusionDraftDone(true);
                fetch(`/api/games/${gameId}`).then(r => r.json()).then(data => {
                  if (data.fen) setCurrentFen(data.fen);
                });
              }}
              onUpgradeApplied={() => {
                // Reload board state to show upgraded piece
                fetch(`/api/games/${gameId}`).then(r => r.json()).then(data => {
                  if (data.fen) setCurrentFen(data.fen);
                });
              }}
            />
          )}
          {gameMode === "auto" && (
            <AutoPlayPanel
              ref={autoRef}
              gameId={gameId}
              onRunningChange={setAutoRunning}
            />
          )}
          {(gameMode === "human-vs-ai" || (gameMode === "fusion" && fusionDraftDone)) && (
            <AiConfigPanel gameId={gameId} />
          )}
          <RulesPanel fen={currentFen} />
        </div>
      </div>
      {gameMode === "auto" && (
        <button
          onClick={() => autoRunning ? autoRef.current?.pause() : autoRef.current?.start()}
          style={{
            marginTop: "20px",
            padding: "12px 48px",
            fontSize: "16px",
            cursor: "pointer",
            border: "2px solid #333",
            borderRadius: "8px",
            background: autoRunning ? "#c0392b" : "#333",
            color: "#fff",
          }}
        >
          {autoRunning ? "Pause" : "Start"}
        </button>
      )}
    </div>
  );
}
