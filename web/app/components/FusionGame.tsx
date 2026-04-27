"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import ChessBoard, { boardJsonToFen } from "./ChessBoard";
import AiConfigPanel from "./AiConfigPanel";
import RulesPanel from "./RulesPanel";
import EvalBar from "./EvalBar";
import FusionDraftPanel from "./FusionDraftPanel";
import type { FusionDraftRef, UpgradeResult } from "./FusionDraftPanel";
import { FusionUpgrade, BoardJson } from "@/app/lib/types";

interface Props {
  gameId: string;
  playerToken: string;
  upgrade: FusionUpgrade;
  onNewGame: () => void;
}

// Standard starting board JSON
const START_BOARD: BoardJson = {
  pieces: {
    a1:{kind:"rook",color:"white"},b1:{kind:"knight",color:"white"},c1:{kind:"bishop",color:"white"},
    d1:{kind:"queen",color:"white"},e1:{kind:"king",color:"white"},f1:{kind:"bishop",color:"white"},
    g1:{kind:"knight",color:"white"},h1:{kind:"rook",color:"white"},
    a2:{kind:"pawn",color:"white"},b2:{kind:"pawn",color:"white"},c2:{kind:"pawn",color:"white"},
    d2:{kind:"pawn",color:"white"},e2:{kind:"pawn",color:"white"},f2:{kind:"pawn",color:"white"},
    g2:{kind:"pawn",color:"white"},h2:{kind:"pawn",color:"white"},
    a8:{kind:"rook",color:"black"},b8:{kind:"knight",color:"black"},c8:{kind:"bishop",color:"black"},
    d8:{kind:"queen",color:"black"},e8:{kind:"king",color:"black"},f8:{kind:"bishop",color:"black"},
    g8:{kind:"knight",color:"black"},h8:{kind:"rook",color:"black"},
    a7:{kind:"pawn",color:"black"},b7:{kind:"pawn",color:"black"},c7:{kind:"pawn",color:"black"},
    d7:{kind:"pawn",color:"black"},e7:{kind:"pawn",color:"black"},f7:{kind:"pawn",color:"black"},
    g7:{kind:"pawn",color:"black"},h7:{kind:"pawn",color:"black"},
  },
  sideToMove: "white",
  castling: { whiteKingside: true, whiteQueenside: true, blackKingside: true, blackQueenside: true },
  epSquare: null, halfmoveClock: 0, fullmoveNumber: 1,
};

interface UpgradeInfo {
  pieceName: string;
  description: string;
  value: number;
  appliedSquare: string;
  movesFrom?: string[];
}

export default function FusionGame({ gameId, playerToken, upgrade, onNewGame }: Props) {
  const [board, setBoard] = useState<BoardJson>(START_BOARD);
  const [draftDone, setDraftDone] = useState(false);
  const [status, setStatus] = useState("in_progress");
  const [moves, setMoves] = useState<string[]>([]);
  const [winner, setWinner] = useState<string>();
  const [evalScore, setEvalScore] = useState(0);
  const [legalMoves, setLegalMoves] = useState<string[]>([]);
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null);
  const [legalTargets, setLegalTargets] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [thinking, setThinking] = useState(false);
  const [upgradeInfo, setUpgradeInfo] = useState<UpgradeInfo | null>(null);
  const [upgradeSelected, setUpgradeSelected] = useState(false);
  const fusionRef = useRef<FusionDraftRef>(null);

  // Fetch legal moves from board JSON
  const fetchLegalMoves = useCallback(() => {
    fetch(`/api/games/${gameId}`)
      .then(r => r.json())
      .then(data => {
        if (data.legalMoves) setLegalMoves(data.legalMoves);
        if (data.boardJson) setBoard(data.boardJson);
        if (data.status) setStatus(data.status);
        if (data.eval !== undefined) setEvalScore(data.eval);
      })
      .catch(() => {});
  }, [gameId]);

  // Initial load
  useEffect(() => {
    fetch(`/api/games/${gameId}`)
      .then(r => r.json())
      .then(data => {
        if (data.boardJson) setBoard(data.boardJson);
        if (data.fusionDraftDone) setDraftDone(true);
        if (data.legalMoves) setLegalMoves(data.legalMoves);
        if (data.status) setStatus(data.status);
        if (data.moves) setMoves(data.moves);
        if (data.eval !== undefined) setEvalScore(data.eval);
      });
  }, [gameId]);

  const isMyTurn = board.sideToMove === "white"; // player is always white
  const gameOver = status === "checkmate" || status === "stalemate" || status === "draw";
  const canPlay = draftDone && isMyTurn && !gameOver;

  // Validate move locally against the legal moves list
  const buildMoveUci = useCallback((from: string, to: string): string | null => {
    const base = from + to;
    const match = legalMoves.find(m => m.startsWith(base));
    if (!match) return null;
    return match.length === 5 ? base + "q" : base;
  }, [legalMoves]);

  // Apply move to local board state immediately (optimistic update).
  // We already know the move is legal — no reason to wait for the server.
  const applyMoveLocally = useCallback((moveUci: string) => {
    const from = moveUci.substring(0, 2);
    const to = moveUci.substring(2, 4);
    const promo = moveUci.length === 5 ? moveUci[4] : null;

    setBoard(prev => {
      const newPieces = { ...prev.pieces };
      const piece = newPieces[from];
      if (!piece) return prev;

      delete newPieces[from];

      // Promotion: swap kind
      if (promo) {
        const promoMap: Record<string, string> = { q: "queen", r: "rook", b: "bishop", n: "knight" };
        newPieces[to] = { kind: promoMap[promo] || "queen", color: piece.color };
      } else {
        newPieces[to] = piece;
      }

      // En passant: remove the captured pawn
      if (piece.kind === "pawn" && to === prev.epSquare) {
        const capturedSq = to[0] + from[1]; // same file as target, same rank as source
        delete newPieces[capturedSq];
      }

      // Castling: move the rook too
      if (piece.kind === "king" && Math.abs(to.charCodeAt(0) - from.charCodeAt(0)) === 2) {
        const rank = from[1];
        if (to[0] === "g") { // kingside
          newPieces["f" + rank] = newPieces["h" + rank];
          delete newPieces["h" + rank];
        } else if (to[0] === "c") { // queenside
          newPieces["d" + rank] = newPieces["a" + rank];
          delete newPieces["a" + rank];
        }
      }

      return { ...prev, pieces: newPieces, sideToMove: prev.sideToMove === "white" ? "black" : "white" as string };
    });
    setLegalMoves([]); // clear until server sends new ones
  }, []);

  const sendMove = useCallback((moveUci: string) => {
    setError(null);
    setSelectedSquare(null);
    setLegalTargets(new Set());
    setThinking(true);

    // Instant feedback — move the piece now
    applyMoveLocally(moveUci);

    fetch(`/api/games/${gameId}/move`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerToken, move: moveUci }),
    })
      .then(r => r.json())
      .then(data => {
        setThinking(false);
        if (data.error) { setError(data.error); fetchLegalMoves(); return; }
        if (data.aiError) setError(data.aiError);

        const finalBoard = data.boardJson;

        // Server response includes AI's move too — show it after a short delay
        // so the player can see where the AI moved from/to
        if (finalBoard) {
          setTimeout(() => {
            setBoard(finalBoard);
            if (data.status) setStatus(data.status);
            if (data.moves) setMoves(data.moves);
            if (data.winner) setWinner(data.winner);
            if (data.eval !== undefined) setEvalScore(data.eval);
            fetchLegalMoves();
          }, 300);
        } else {
          if (data.status) setStatus(data.status);
          if (data.moves) setMoves(data.moves);
          if (data.winner) setWinner(data.winner);
          if (data.eval !== undefined) setEvalScore(data.eval);
          fetchLegalMoves();
        }
      })
      .catch(() => { setThinking(false); setError("Failed to make move"); fetchLegalMoves(); });
  }, [gameId, playerToken, fetchLegalMoves, applyMoveLocally]);

  const onPieceDrop = useCallback((from: string, to: string) => {
    if (!canPlay) return false;
    const moveUci = buildMoveUci(from, to);
    if (!moveUci) return false;
    sendMove(moveUci);
    return true;
  }, [canPlay, buildMoveUci, sendMove]);

  const onSquareClick = useCallback((square: string) => {
    // During draft: forward to fusion panel
    if (!draftDone) {
      fusionRef.current?.applyToSquare(square);
      return;
    }
    if (!canPlay) return;

    if (selectedSquare && legalTargets.has(square)) {
      let moveUci = selectedSquare + square;
      if (legalMoves.some(m => m.startsWith(moveUci) && m.length === 5)) moveUci += "q";
      sendMove(moveUci);
      return;
    }
    const movesFrom = legalMoves.filter(m => m.startsWith(square));
    if (movesFrom.length > 0) {
      setSelectedSquare(square);
      setLegalTargets(new Set(movesFrom.map(m => m.substring(2, 4))));
    } else {
      setSelectedSquare(null);
      setLegalTargets(new Set());
    }
  }, [draftDone, canPlay, selectedSquare, legalTargets, legalMoves, sendMove]);

  // Clear selection on board change
  useEffect(() => { setSelectedSquare(null); setLegalTargets(new Set()); }, [board.sideToMove]);

  // Track where the upgraded piece currently is (it can move during the game)
  const upgradedPieceSquare = upgradeInfo
    ? Object.entries(board.pieces).find(
        ([, p]) => p.kind === upgradeInfo.pieceName && p.color === "white"
      )?.[0]
    : null;

  // Square highlights
  const squareStyles: Record<string, React.CSSProperties> = {};

  // During draft: highlight valid/invalid upgrade targets (only after selecting the upgrade card)
  if (!draftDone && upgrade.results && upgradeSelected) {
    for (const [sq, piece] of Object.entries(board.pieces)) {
      if (piece.color !== "white") continue;
      if (piece.kind === "king") continue;
      const canUpgrade = piece.kind in upgrade.results;
      if (canUpgrade) {
        squareStyles[sq] = { background: "rgba(139, 92, 246, 0.2)", cursor: "pointer" };
      } else {
        squareStyles[sq] = { background: "rgba(239, 68, 68, 0.15)" };
      }
    }
  }

  if (upgradedPieceSquare && !selectedSquare && draftDone) {
    squareStyles[upgradedPieceSquare] = { background: "rgba(139, 92, 246, 0.25)" };
  }
  if (selectedSquare) squareStyles[selectedSquare] = { background: "rgba(255, 255, 0, 0.4)" };
  for (const sq of legalTargets) squareStyles[sq] = { background: "radial-gradient(circle, rgba(0,0,0,0.2) 25%, transparent 25%)", cursor: "pointer" };

  const statusText = !draftDone
    ? "Select upgrade, click a piece on the board"
    : gameOver
    ? (status === "checkmate" ? `Checkmate! ${winner === "white" ? "White" : "Black"} wins!` : status === "stalemate" ? "Stalemate!" : "Draw!")
    : thinking
    ? "AI thinking..."
    : status === "check"
    ? (isMyTurn ? "Check! Your turn" : "AI thinking...")
    : isMyTurn ? "Your turn" : "AI thinking...";

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "16px" }}>
      <div style={{ fontSize: "18px", fontWeight: "bold" }}>{statusText}</div>

      <div style={{ display: "flex", gap: "16px", alignItems: "flex-start" }}>
        <div style={{ display: "flex", gap: "8px" }}>
          <EvalBar evalScore={evalScore} playerColor="white" />
          <div style={{ position: "relative" }}>
            <ChessBoard
              position={boardJsonToFen(board)}
              orientation="white"
              interactive={canPlay || !draftDone}
              squareStyles={squareStyles}
              onPieceDrop={canPlay ? onPieceDrop : undefined}
              onSquareClick={onSquareClick}
            />
            {gameOver && (
              <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", background: "rgba(0,0,0,0.6)", borderRadius: "4px" }}>
                <div style={{ background: "#fff", padding: "24px 40px", borderRadius: "12px", textAlign: "center" }}>
                  <div style={{ fontSize: "24px", fontWeight: "bold", marginBottom: "8px" }}>
                    {status === "checkmate" ? "Checkmate" : status === "stalemate" ? "Stalemate" : "Draw"}
                  </div>
                  <div style={{ fontSize: "16px", color: "#666", marginBottom: "16px" }}>
                    {status === "checkmate" ? `${winner === "white" ? "White" : "Black"} wins` : status === "stalemate" ? "No legal moves" : "Draw"}
                  </div>
                  <button onClick={onNewGame} style={{ padding: "10px 24px", fontSize: "14px", cursor: "pointer", border: "2px solid #333", borderRadius: "8px", background: "#333", color: "#fff" }}>
                    New Game
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          {!draftDone && (
            <FusionDraftPanel
              ref={fusionRef}
              gameId={gameId}
              playerToken={playerToken}
              upgrade={upgrade}
              onSelectionChange={setUpgradeSelected}
              onDraftComplete={async () => {
                if (!upgradeInfo) {
                  // Skipping — tell server to set up standard board
                  await fetch(`/api/games/${gameId}/fusion-draft`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ skip: true, playerToken }),
                  });
                }
                setDraftDone(true);
                fetchLegalMoves();
              }}
              onUpgradeUndone={() => {
                setBoard(START_BOARD);
                setUpgradeInfo(null);
              }}
              onUpgradeApplied={(result: UpgradeResult) => {
                setUpgradeInfo({
                  pieceName: result.pieceName,
                  description: result.description,
                  value: result.value,
                  appliedSquare: result.square,
                  movesFrom: result.movesFrom,
                });
                fetch(`/api/games/${gameId}`)
                  .then(r => r.json())
                  .then(data => { if (data.boardJson) setBoard(data.boardJson); });
              }}
            />
          )}
          {draftDone && upgradeInfo && (
            <div style={{
              width: "220px", border: "1px solid #c4b5fd", borderRadius: "8px",
              padding: "12px", background: "#f5f3ff",
            }}>
              <div style={{ fontWeight: "bold", fontSize: "13px", color: "#6d28d9", marginBottom: "4px" }}>
                Fusion Upgrade
              </div>
              <div style={{ fontSize: "14px", fontWeight: "bold", color: "#333" }}>
                {upgradeInfo.pieceName}
              </div>
              <div style={{ fontSize: "12px", color: "#666", marginTop: "2px" }}>
                {upgradeInfo.description}
              </div>
              <div style={{ fontSize: "11px", color: "#999", marginTop: "4px" }}>
                {upgradeInfo.value}cp &middot; {upgradedPieceSquare ? `on ${upgradedPieceSquare}` : "captured"}
              </div>
            </div>
          )}
          {draftDone && <AiConfigPanel gameId={gameId} />}
          <RulesPanel
            pieceKinds={Object.values(board.pieces).map(p => p.kind)}
            customPiece={upgradeInfo ? {
              name: upgradeInfo.pieceName,
              description: upgradeInfo.description,
              value: upgradeInfo.value,
              movesFrom: upgradeInfo.movesFrom || [],
            } : undefined}
          />
        </div>
      </div>

      {error && <div style={{ color: "red", fontSize: "14px" }}>{error}</div>}

      {moves.length > 0 && (
        <div style={{ maxWidth: "560px", width: "100%" }}>
          <strong>Moves:</strong>{" "}
          <span style={{ fontFamily: "monospace", fontSize: "14px" }}>
            {moves.map((m, i) => <span key={i}>{i % 2 === 0 ? `${Math.floor(i / 2) + 1}. ` : ""}{m} </span>)}
          </span>
        </div>
      )}
    </div>
  );
}
