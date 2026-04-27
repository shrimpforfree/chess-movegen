"use client";

import { useState, useEffect, useCallback } from "react";
import { Chessboard } from "react-chessboard";

interface Props {
  gameId: string;
  playerToken: string;
  playerColor: "white" | "black";
  readonly?: boolean;
  onNewGame?: () => void;
}

export default function ChessBoardComponent({
  gameId,
  playerToken,
  playerColor,
  readonly: readonlyMode,
  onNewGame,
}: Props) {
  const [fen, setFen] = useState(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  );
  const [status, setStatus] = useState("waiting");
  const [moves, setMoves] = useState<string[]>([]);
  const [winner, setWinner] = useState<string | undefined>();
  const [evalScore, setEvalScore] = useState<number | undefined>();
  const [error, setError] = useState<string | null>(null);

  // Subscribe to SSE for real-time updates
  useEffect(() => {
    const eventSource = new EventSource(`/api/sse/${gameId}`);

    eventSource.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === "update") {
        if (data.fen) setFen(data.fen);
        if (data.status) setStatus(data.status);
        if (data.winner) setWinner(data.winner);
        if (data.eval !== undefined) setEvalScore(data.eval);
      }
    };

    return () => eventSource.close();
  }, [gameId]);

  // Fetch initial state
  useEffect(() => {
    fetch(`/api/games/${gameId}`)
      .then((res) => res.json())
      .then((data) => {
        setFen(data.fen);
        setStatus(data.status);
        setMoves(data.moves || []);
        if (data.winner) setWinner(data.winner);
        if (data.eval !== undefined) setEvalScore(data.eval);
      });
  }, [gameId]);

  const isMyTurn = useCallback(() => {
    const sideToMove = fen.split(" ")[1];
    return (
      (playerColor === "white" && sideToMove === "w") ||
      (playerColor === "black" && sideToMove === "b")
    );
  }, [fen, playerColor]);

  const gameOver = status === "checkmate" || status === "stalemate" || status === "draw";

  const onDrop = useCallback(
    ({
      piece,
      sourceSquare,
      targetSquare,
    }: {
      piece: { pieceType: string; position: string; isSparePiece: boolean };
      sourceSquare: string;
      targetSquare: string | null;
    }) => {
      if (!isMyTurn() || gameOver || !targetSquare) return false;

      setError(null);

      // Build UCI move string
      let moveUci = sourceSquare + targetSquare;
      // Check for promotion (pawn reaching last rank)
      const isPawn = piece.pieceType.toLowerCase().includes("p");
      const isPromotion =
        isPawn &&
        ((playerColor === "white" && targetSquare[1] === "8") ||
          (playerColor === "black" && targetSquare[1] === "1"));
      if (isPromotion) {
        moveUci += "q"; // auto-promote to queen for now
      }

      // Send move to server
      fetch(`/api/games/${gameId}/move`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerToken, move: moveUci }),
      })
        .then((res) => res.json())
        .then((data) => {
          if (data.error) {
            setError(data.error);
          } else {
            setFen(data.fen);
            setStatus(data.status);
            setMoves(data.moves || []);
            if (data.winner) setWinner(data.winner);
            if (data.eval !== undefined) setEvalScore(data.eval);
          }
        })
        .catch(() => setError("Failed to make move"));

      return true;
    },
    [gameId, playerToken, playerColor, isMyTurn, gameOver]
  );

  const statusText = () => {
    if (status === "waiting") return "Waiting for opponent to join...";
    if (status === "checkmate") return `Checkmate! ${winner} wins!`;
    if (status === "stalemate") return "Stalemate — draw!";
    if (status === "draw") return "Draw — threefold repetition!";
    if (status === "check")
      return isMyTurn() ? "You are in check!" : "Opponent is in check";
    if (isMyTurn()) return "Your turn";
    return "Opponent's turn";
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: "16px",
      }}
    >
      <div style={{ fontSize: "18px", fontWeight: "bold" }}>
        {statusText()}
      </div>

      <div style={{ display: "flex", gap: "8px", width: "590px", maxWidth: "100%" }}>
        {/* Eval bar */}
        {evalScore !== undefined && (() => {
          // Convert centipawns to a percentage (sigmoid-ish clamping)
          // ±500cp maps to 0–100%, clamped at the extremes
          const clamped = Math.max(-500, Math.min(500, evalScore));
          const whitePct = 50 + (clamped / 500) * 50;
          const displayVal = `${evalScore > 0 ? "+" : ""}${(evalScore / 100).toFixed(1)}`;
          const isWhiteUp = evalScore >= 0;
          // When board is flipped, flip the bar too
          const topColor = playerColor === "white" ? "#333" : "#fff";
          const bottomColor = playerColor === "white" ? "#fff" : "#333";
          const topPct = playerColor === "white" ? (100 - whitePct) : whitePct;

          return (
            <div style={{
              width: "22px",
              flexShrink: 0,
              borderRadius: "4px",
              overflow: "hidden",
              border: "1px solid #ccc",
              display: "flex",
              flexDirection: "column",
              position: "relative",
            }}>
              {/* Top portion */}
              <div style={{
                background: topColor,
                transition: "flex 0.3s ease",
                flex: `${topPct} 0 0%`,
              }} />
              {/* Bottom portion */}
              <div style={{
                background: bottomColor,
                transition: "flex 0.3s ease",
                flex: `${100 - topPct} 0 0%`,
              }} />
              {/* Score label */}
              <div style={{
                position: "absolute",
                left: "50%",
                transform: "translateX(-50%)",
                fontSize: "10px",
                fontWeight: "bold",
                fontFamily: "monospace",
                ...(isWhiteUp
                  ? { bottom: "4px", color: "#333" }
                  : { top: "4px", color: "#ccc" }),
              }}>
                {displayVal}
              </div>
            </div>
          );
        })()}

        <div style={{ width: "560px", maxWidth: "100%", flexShrink: 1, position: "relative" }}>
          <Chessboard
            options={{
              position: fen,
              boardOrientation: playerColor,
              allowDragging:
                !readonlyMode && isMyTurn() && !gameOver && status !== "waiting",
              onPieceDrop: onDrop,
            }}
          />
          {gameOver && (
            <div style={{
              position: "absolute",
              inset: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              background: "rgba(0, 0, 0, 0.6)",
              borderRadius: "4px",
            }}>
              <div style={{
                background: "#fff",
                padding: "24px 40px",
                borderRadius: "12px",
                textAlign: "center",
              }}>
                <div style={{ fontSize: "24px", fontWeight: "bold", marginBottom: "8px" }}>
                  {status === "checkmate" ? "Checkmate" : status === "draw" ? "Draw" : "Stalemate"}
                </div>
                <div style={{ fontSize: "16px", color: "#666", marginBottom: onNewGame ? "16px" : 0 }}>
                  {status === "checkmate"
                    ? `${winner && winner[0].toUpperCase() + winner.slice(1)} wins`
                    : status === "draw"
                    ? "Threefold repetition"
                    : "Draw"}
                </div>
                {onNewGame && (
                  <button
                    onClick={onNewGame}
                    style={{
                      padding: "10px 24px",
                      fontSize: "14px",
                      cursor: "pointer",
                      border: "2px solid #333",
                      borderRadius: "8px",
                      background: "#333",
                      color: "#fff",
                    }}
                  >
                    New Game
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {error && (
        <div style={{ color: "red", fontSize: "14px" }}>{error}</div>
      )}

      {moves.length > 0 && (
        <div style={{ maxWidth: "560px", width: "100%" }}>
          <strong>Moves:</strong>{" "}
          <span style={{ fontFamily: "monospace", fontSize: "14px" }}>
            {moves.map((m, i) => (
              <span key={i}>
                {i % 2 === 0 ? `${Math.floor(i / 2) + 1}. ` : ""}
                {m}{" "}
              </span>
            ))}
          </span>
        </div>
      )}
    </div>
  );
}
