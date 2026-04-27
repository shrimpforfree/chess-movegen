"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";

interface Setup {
  key: string;
  name: string;
  description: string;
  fen: string;
}

export default function Home() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [joinId, setJoinId] = useState("");
  const [fen, setFen] = useState("");
  const [setups, setSetups] = useState<Setup[]>([]);
  const [selectedSetup, setSelectedSetup] = useState<string>("standard");

  // Fetch available game setups from the engine
  useEffect(() => {
    fetch("/api/setups")
      .then((res) => res.json())
      .then((data) => {
        if (data.setups) {
          setSetups(data.setups);
        }
      })
      .catch(() => {
        // Engine not running — fall back to standard only
        setSetups([
          {
            key: "standard",
            name: "Standard Chess",
            description: "Classic chess",
            fen: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
          },
        ]);
      });
  }, []);

  const activeFen = fen.trim() || setups.find((s) => s.key === selectedSetup)?.fen || undefined;

  const createGame = async (mode: "human-vs-human" | "human-vs-ai" | "auto" | "fusion") => {
    setLoading(true);
    const playerToken = Math.random().toString(36).substring(2, 12);

    const res = await fetch("/api/games", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        mode,
        playerToken,
        aiDepth: 4,
        fen: activeFen,
      }),
    });
    const data = await res.json();

    localStorage.setItem(`game-${data.gameId}-token`, data.playerToken);
    localStorage.setItem(`game-${data.gameId}-color`, data.color);

    router.push(`/game/${data.gameId}`);
  };

  const joinGame = () => {
    if (joinId.trim()) {
      router.push(`/game/${joinId.trim()}`);
    }
  };

  const selected = setups.find((s) => s.key === selectedSetup);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "100vh",
        gap: "28px",
        padding: "20px",
      }}
    >
      <h1 style={{ fontSize: "48px", marginBottom: 0 }}>ChessLab</h1>

      {/* Variant picker */}
      {setups.length > 1 && (
        <div style={{ width: "340px" }}>
          <label
            style={{
              fontSize: "14px",
              color: "#666",
              marginBottom: "6px",
              display: "block",
            }}
          >
            Game Variant
          </label>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: "8px",
            }}
          >
            {setups.map((setup) => (
              <button
                key={setup.key}
                onClick={() => {
                  setSelectedSetup(setup.key);
                  setFen("");
                }}
                style={{
                  padding: "10px 8px",
                  fontSize: "14px",
                  cursor: "pointer",
                  border:
                    selectedSetup === setup.key
                      ? "2px solid #333"
                      : "1px solid #ccc",
                  borderRadius: "8px",
                  background:
                    selectedSetup === setup.key ? "#333" : "#fff",
                  color:
                    selectedSetup === setup.key ? "#fff" : "#333",
                  fontWeight:
                    selectedSetup === setup.key ? "bold" : "normal",
                  transition: "all 0.15s",
                }}
              >
                {setup.name}
              </button>
            ))}
          </div>
          {selected && selected.key !== "standard" && (
            <p
              style={{
                fontSize: "13px",
                color: "#888",
                marginTop: "8px",
                textAlign: "center",
                lineHeight: "1.4",
              }}
            >
              {selected.description}
            </p>
          )}
        </div>
      )}

      {/* Game mode buttons */}
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
          onClick={() => createGame("fusion")}
          disabled={loading}
          style={{
            padding: "16px",
            fontSize: "18px",
            cursor: "pointer",
            border: "2px solid #8b5cf6",
            borderRadius: "8px",
            background: "#8b5cf6",
            color: "#fff",
          }}
        >
          Fusion
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

      {/* Custom FEN override */}
      <div style={{ width: "300px" }}>
        <input
          type="text"
          placeholder="Custom FEN (overrides variant)"
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

      {/* Join existing game */}
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
