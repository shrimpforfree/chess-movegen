"use client";

import { useState, useEffect, useCallback } from "react";
import { Chessboard, defaultPieces } from "react-chessboard";
import { fairyPieces } from "./FairyPieces";

interface BoardJson {
  pieces: Record<string, { kind: string; color: string }>;
  sideToMove: string;
  [key: string]: unknown;
}

interface Props {
  gameId: string;
  playerToken: string;
  playerColor: "white" | "black";
  readonly?: boolean;
  onNewGame?: () => void;
  onFenChange?: (fen: string) => void;
  onSquareClicked?: (square: string) => void;
}

/**
 * Map standard piece kinds to react-chessboard piece type codes.
 * Custom/upgraded pieces get mapped to fairy piece codes using first char of the base piece.
 */
const kindToCode: Record<string, string> = {
  pawn: "P", knight: "N", bishop: "B", rook: "R", queen: "Q", king: "K",
  archbishop: "A", chancellor: "C", amazon: "Z", camel: "M", zebra: "X", mann: "O",
};

function boardJsonToPosition(bj: BoardJson): Record<string, { pieceType: string }> {
  const pos: Record<string, { pieceType: string }> = {};
  for (const [sq, piece] of Object.entries(bj.pieces)) {
    const colorPrefix = piece.color === "white" ? "w" : "b";
    let code = kindToCode[piece.kind];
    if (!code) {
      // Upgraded piece like "bishop+j21" — extract base piece
      const base = piece.kind.split("+")[0];
      code = kindToCode[base] || "P";
    }
    pos[sq] = { pieceType: colorPrefix + code };
  }
  return pos;
}

export default function ChessBoardComponent({
  gameId,
  playerToken,
  playerColor,
  readonly: readonlyMode,
  onNewGame,
  onFenChange,
  onSquareClicked,
}: Props) {
  const [fen, setFen] = useState(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  );
  const [status, setStatus] = useState("waiting");
  const [moves, setMoves] = useState<string[]>([]);
  const [winner, setWinner] = useState<string | undefined>();
  const [evalScore, setEvalScore] = useState<number | undefined>();
  const [error, setError] = useState<string | null>(null);
  const [boardJson, setBoardJson] = useState<BoardJson | null>(null);

  // Click-to-move state
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null);
  const [legalMoves, setLegalMoves] = useState<string[]>([]);
  const [legalTargets, setLegalTargets] = useState<Set<string>>(new Set());

  // Subscribe to SSE for real-time updates
  useEffect(() => {
    const eventSource = new EventSource(`/api/sse/${gameId}`);

    eventSource.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === "update") {
        if (data.fen) { setFen(data.fen); onFenChange?.(data.fen); }
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
        onFenChange?.(data.fen);
        setStatus(data.status);
        setMoves(data.moves || []);
        if (data.winner) setWinner(data.winner);
        if (data.eval !== undefined) setEvalScore(data.eval);
        if (data.boardJson) setBoardJson(data.boardJson);
      });
  }, [gameId]);

  // Fetch legal moves whenever FEN changes (for click-to-move highlights)
  useEffect(() => {
    if (readonlyMode) return;
    fetch(`/api/games/${gameId}`)
      .then((res) => res.json())
      .then((data) => {
        if (data.legalMoves) {
          setLegalMoves(data.legalMoves);
        }
      })
      .catch(() => {});
  }, [fen, gameId, readonlyMode]);

  const isMyTurn = useCallback(() => {
    const sideToMove = fen.split(" ")[1];
    return (
      (playerColor === "white" && sideToMove === "w") ||
      (playerColor === "black" && sideToMove === "b")
    );
  }, [fen, playerColor]);

  const gameOver = status === "checkmate" || status === "stalemate" || status === "draw";
  const canInteract = !readonlyMode && isMyTurn() && !gameOver && status !== "waiting";

  // Send a move to the server
  const sendMove = useCallback(
    (moveUci: string) => {
      setError(null);
      setSelectedSquare(null);
      setLegalTargets(new Set());

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
            onFenChange?.(data.fen);
            setStatus(data.status);
            setMoves(data.moves || []);
            if (data.winner) setWinner(data.winner);
            if (data.eval !== undefined) setEvalScore(data.eval);
            if (data.boardJson) setBoardJson(data.boardJson);
          }
        })
        .catch(() => setError("Failed to make move"));
    },
    [gameId, playerToken, onFenChange]
  );

  // Handle square click — select piece or make move
  const onSquareClick = useCallback(
    ({ square }: { piece?: unknown; square: string }) => {
      // Fusion draft mode — forward click to parent
      if (onSquareClicked) { onSquareClicked(square); return; }
      if (!canInteract) return;

      // If clicking a highlighted target square, make the move
      if (selectedSquare && legalTargets.has(square)) {
        let moveUci = selectedSquare + square;
        // Check for pawn promotion (legal move has 5 chars like "a7a8q")
        const hasPromo = legalMoves.some(
          (m) => m.startsWith(selectedSquare + square) && m.length === 5
        );
        if (hasPromo) {
          moveUci += "q"; // auto-promote to queen
        }
        sendMove(moveUci);
        return;
      }

      // Check if this square has a friendly piece with legal moves
      const movesFromSquare = legalMoves.filter((m) => m.startsWith(square));
      if (movesFromSquare.length > 0) {
        setSelectedSquare(square);
        setLegalTargets(new Set(movesFromSquare.map((m) => m.substring(2, 4))));
      } else {
        // Clicked empty/enemy square with nothing selected — deselect
        setSelectedSquare(null);
        setLegalTargets(new Set());
      }
    },
    [canInteract, selectedSquare, legalTargets, legalMoves, sendMove]
  );

  // Handle drag-and-drop (still works alongside click)
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
      if (!canInteract || !targetSquare) return false;

      let moveUci = sourceSquare + targetSquare;
      const isPawn = piece.pieceType.toLowerCase().includes("p");
      const isPromotion =
        isPawn &&
        ((playerColor === "white" && targetSquare[1] === "8") ||
          (playerColor === "black" && targetSquare[1] === "1"));
      if (isPromotion) {
        moveUci += "q";
      }

      sendMove(moveUci);
      return true;
    },
    [canInteract, playerColor, sendMove]
  );

  // Clear selection when turn changes or game updates
  useEffect(() => {
    setSelectedSquare(null);
    setLegalTargets(new Set());
  }, [fen]);

  // Build square styles for highlights
  const squareStyles: Record<string, React.CSSProperties> = {};
  if (selectedSquare) {
    squareStyles[selectedSquare] = { background: "rgba(255, 255, 0, 0.4)" };
  }
  for (const sq of legalTargets) {
    squareStyles[sq] = {
      background: "radial-gradient(circle, rgba(0, 0, 0, 0.2) 25%, transparent 25%)",
      cursor: "pointer",
    };
  }

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
        {/* Eval bar — always visible, defaults to 0.0 before first eval */}
        {(() => {
          const score = evalScore ?? 0;
          const clamped = Math.max(-500, Math.min(500, score));
          const whitePct = 50 + (clamped / 500) * 50;
          const displayVal = `${score > 0 ? "+" : ""}${(score / 100).toFixed(1)}`;
          const isWhiteUp = score >= 0;
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
              <div style={{
                background: topColor,
                transition: "flex 0.3s ease",
                flex: `${topPct} 0 0%`,
              }} />
              <div style={{
                background: bottomColor,
                transition: "flex 0.3s ease",
                flex: `${100 - topPct} 0 0%`,
              }} />
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
              position: boardJson ? boardJsonToPosition(boardJson) : fen,
              boardOrientation: playerColor,
              allowDragging: canInteract,
              onPieceDrop: onDrop,
              onSquareClick: onSquareClick,
              squareStyles: squareStyles,
              pieces: { ...defaultPieces, ...fairyPieces },
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
