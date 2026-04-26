"use client";

import { useState, useEffect, useCallback } from "react";
import { Chessboard } from "react-chessboard";

interface Props {
  gameId: string;
  playerToken: string;
  playerColor: "white" | "black";
}

export default function ChessBoardComponent({
  gameId,
  playerToken,
  playerColor,
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

  const gameOver = status === "checkmate" || status === "stalemate";

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

      {evalScore !== undefined && (
        <div style={{ fontSize: "14px", color: "#666", fontFamily: "monospace" }}>
          Eval: {evalScore > 0 ? "+" : ""}{(evalScore / 100).toFixed(2)}
        </div>
      )}

      <div style={{ width: "560px", maxWidth: "100%" }}>
        <Chessboard
          options={{
            position: fen,
            boardOrientation: playerColor,
            allowDragging:
              isMyTurn() && !gameOver && status !== "waiting",
            onPieceDrop: onDrop,
          }}
        />
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
