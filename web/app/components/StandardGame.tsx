"use client";

import { useState, useEffect, useCallback } from "react";
import ChessBoard from "./ChessBoard";
import AiConfigPanel from "./AiConfigPanel";
import RulesPanel from "./RulesPanel";
import EvalBar from "./EvalBar";

interface Props {
  gameId: string;
  playerToken: string;
  playerColor: "white" | "black";
  plusMode?: boolean;
  onNewGame: () => void;
}

const ADS = [
  { headline: "TIRED OF LOSING PIECES?", body: "Try PawnShield\u2122 \u2014 Now available at your local chess store! Side effects may include overconfidence and premature pawn storms.", color: "#e11d48" },
  { headline: "ONE WEIRD TRICK TO BEAT GRANDMASTERS", body: "Bishops HATE him! Local man discovers secret pawn technique that engines can't refute. Click to learn more!", color: "#ea580c" },
  { headline: "IS YOUR KING FEELING INSECURE?", body: "Try CastleGuard\u00ae \u2014 Premium king safety solutions. Because you're worth defending. Not responsible for back-rank mates.", color: "#2563eb" },
  { headline: "DOWNLOAD MORE PAWNS", body: "Why play with 8 pawns when you can have 9? Scientists confirm: more pawns = more wins. That's just math.", color: "#16a34a" },
  { headline: "HOT ROOKS IN YOUR AREA", body: "These rooks want to connect on YOUR open files! Don't miss out on this once-in-a-game opportunity. Must be 800+ rated.", color: "#9333ea" },
  { headline: "PAWN COIN - TO THE MOON", body: "Invest in PAWN COIN today! Each pawn backed by real centipawn value. Up 200% since the last blunder. Not financial advice.", color: "#ca8a04" },
];

export default function StandardGame({ gameId, playerToken, playerColor, plusMode, onNewGame }: Props) {
  const [fen, setFen] = useState("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
  const [status, setStatus] = useState("in_progress");
  const [moves, setMoves] = useState<string[]>([]);
  const [winner, setWinner] = useState<string>();
  const [evalScore, setEvalScore] = useState(0);
  const [legalMoves, setLegalMoves] = useState<string[]>([]);
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null);
  const [legalTargets, setLegalTargets] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [showAd, setShowAd] = useState(false);
  const [adCountdown, setAdCountdown] = useState(0);
  const [adIndex, setAdIndex] = useState(0);
  const [spawnedSquare, setSpawnedSquare] = useState<string | null>(null);

  // Fetch game state
  const fetchState = useCallback(() => {
    fetch(`/api/games/${gameId}`)
      .then(r => r.json())
      .then(data => {
        if (data.fen) setFen(data.fen);
        if (data.status) setStatus(data.status);
        if (data.moves) setMoves(data.moves);
        if (data.winner) setWinner(data.winner);
        if (data.eval !== undefined) setEvalScore(data.eval);
        if (data.legalMoves) setLegalMoves(data.legalMoves);
      })
      .catch(() => {});
  }, [gameId]);

  useEffect(() => { fetchState(); }, [fetchState]);



  const isMyTurn = fen.split(" ")[1] === (playerColor === "white" ? "w" : "b");
  const gameOver = status === "checkmate" || status === "stalemate" || status === "draw";
  const canInteract = isMyTurn && !gameOver;

  // Validate locally against legal moves list
  const buildMoveUci = useCallback((from: string, to: string): string | null => {
    const base = from + to;
    const match = legalMoves.find(m => m.startsWith(base));
    if (!match) return null;
    return match.length === 5 ? base + "q" : base;
  }, [legalMoves]);

  // Fetch only legal moves without touching the position
  const fetchLegalMoves = useCallback(() => {
    fetch(`/api/games/${gameId}`)
      .then(r => r.json())
      .then(data => {
        if (data.legalMoves) setLegalMoves(data.legalMoves);
      })
      .catch(() => {});
  }, [gameId]);

  const sendMove = useCallback((moveUci: string) => {
    setError(null);
    setSelectedSquare(null);
    setLegalTargets(new Set());
    setLegalMoves([]); // Clear legal moves while waiting

    fetch(`/api/games/${gameId}/move`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerToken, move: moveUci }),
    })
      .then(r => r.json())
      .then(data => {
        if (data.error) { setError(data.error); fetchState(); return; }
        const playerFen = data.playerFen || data.fen;
        const finalFen = data.fen;

        const applyFinal = () => {
          setFen(finalFen);
          if (data.status) setStatus(data.status);
          if (data.moves) setMoves(data.moves);
          if (data.winner) setWinner(data.winner);
          if (data.eval !== undefined) setEvalScore(data.eval);
          fetchLegalMoves();
        };

        if (playerFen !== finalFen) {
          // AI moved too — show player position briefly, then animate AI move
          setFen(playerFen);
          setTimeout(applyFinal, 400);
        } else {
          applyFinal();
        }
      })
      .catch(() => setError("Failed to make move"));
  }, [gameId, playerToken, fetchState, fetchLegalMoves]);

  const onPieceDrop = useCallback((from: string, to: string) => {
    if (!canInteract) return false;
    const moveUci = buildMoveUci(from, to);
    if (!moveUci) return false; // illegal — piece snaps back
    sendMove(moveUci);
    return true; // legal — react-chessboard animates immediately
  }, [canInteract, buildMoveUci, sendMove]);

  const onSquareClick = useCallback((square: string) => {
    if (!canInteract) return;
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
  }, [canInteract, selectedSquare, legalTargets, legalMoves, sendMove]);

  // Clear selection on position change
  useEffect(() => { setSelectedSquare(null); setLegalTargets(new Set()); }, [fen]);

  // Square highlights
  const squareStyles: Record<string, React.CSSProperties> = {};
  if (selectedSquare) squareStyles[selectedSquare] = { background: "rgba(255, 255, 0, 0.4)" };
  for (const sq of legalTargets) squareStyles[sq] = { background: "radial-gradient(circle, rgba(0,0,0,0.2) 25%, transparent 25%)", cursor: "pointer" };

  // Plus mode: ad logic
  const showAdButton = plusMode && !gameOver && isMyTurn && evalScore <= -100;

  const startAd = () => {
    setAdIndex(Math.floor(Math.random() * ADS.length));
    setShowAd(true);
    setAdCountdown(5);
    const interval = setInterval(() => {
      setAdCountdown(prev => {
        if (prev <= 1) { clearInterval(interval); return 0; }
        return prev - 1;
      });
    }, 1000);
  };

  const claimPawn = async () => {
    setShowAd(false);
    try {
      const res = await fetch(`/api/games/${gameId}/watch-ad`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerToken }),
      });
      const data = await res.json();
      if (data.fen) {
        setFen(data.fen);
        setSpawnedSquare(data.square);
        setTimeout(() => setSpawnedSquare(null), 2000);
        fetchState();
      }
      if (data.error) setError(data.error);
    } catch {
      setError("Failed to spawn pawn");
    }
  };

  const currentAd = ADS[adIndex % ADS.length];

  // Spawned pawn highlight
  if (spawnedSquare) {
    squareStyles[spawnedSquare] = { background: "rgba(34, 197, 94, 0.4)" };
  }

  const statusText = gameOver
    ? (status === "checkmate" ? `Checkmate! ${winner} wins!` : status === "stalemate" ? "Stalemate!" : "Draw!")
    : status === "check" ? (isMyTurn ? "You are in check!" : "Opponent in check")
    : isMyTurn ? "Your turn" : "AI thinking...";

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "16px" }}>
      <div style={{ fontSize: "18px", fontWeight: "bold" }}>{statusText}</div>

      <div style={{ display: "flex", gap: "16px", alignItems: "flex-start" }}>
        <div style={{ display: "flex", gap: "8px" }}>
          <EvalBar evalScore={evalScore} playerColor={playerColor} />
          <div style={{ position: "relative" }}>
            <ChessBoard
              position={fen}
              orientation={playerColor}
              interactive={canInteract}
              squareStyles={squareStyles}
              onPieceDrop={onPieceDrop}
              onSquareClick={onSquareClick}
            />
            {gameOver && <GameOverOverlay status={status} winner={winner} onNewGame={onNewGame} />}
            {/* Ad popup overlay */}
            {showAd && (
              <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", background: "rgba(0,0,0,0.75)", borderRadius: "4px", zIndex: 10 }}>
                <div style={{
                  background: "#fff", padding: "0", borderRadius: "12px", textAlign: "center",
                  width: "380px", overflow: "hidden", boxShadow: "0 20px 60px rgba(0,0,0,0.3)",
                }}>
                  <div style={{ background: currentAd.color, padding: "16px 24px", color: "#fff" }}>
                    <div style={{ fontSize: "11px", opacity: 0.8, marginBottom: "4px" }}>SPONSORED</div>
                    <div style={{ fontSize: "20px", fontWeight: "bold", letterSpacing: "0.5px" }}>
                      {currentAd.headline}
                    </div>
                  </div>
                  <div style={{ padding: "20px 24px" }}>
                    <div style={{ fontSize: "14px", color: "#555", lineHeight: "1.5", marginBottom: "20px" }}>
                      {currentAd.body}
                    </div>
                    {adCountdown > 0 ? (
                      <div style={{ fontSize: "14px", color: "#999", padding: "10px" }}>
                        Skip in {adCountdown}s...
                      </div>
                    ) : (
                      <button
                        onClick={claimPawn}
                        style={{
                          padding: "12px 32px", fontSize: "16px", fontWeight: "bold", cursor: "pointer",
                          border: "none", borderRadius: "8px", background: currentAd.color, color: "#fff",
                        }}
                      >
                        Claim Free Pawn
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          <AiConfigPanel gameId={gameId} />
          <RulesPanel fen={fen} />
        </div>
      </div>

      {/* Watch Ad button — only in plus mode when losing */}
      {showAdButton && (
        <div style={{ alignSelf: "flex-start" }}>
          <button
            onClick={startAd}
            disabled={showAd}
            style={{
              padding: "6px 14px", fontSize: "12px", cursor: "pointer",
              border: "1px solid #d4d4d4", borderRadius: "6px",
              background: "linear-gradient(135deg, #fef3c7, #fde68a)",
              color: "#92400e", fontWeight: 600,
            }}
          >
            Watch Ad for Free Pawn
          </button>
        </div>
      )}

      {error && <div style={{ color: "red", fontSize: "14px" }}>{error}</div>}
      <MoveHistory moves={moves} />
    </div>
  );
}

function GameOverOverlay({ status, winner, onNewGame }: { status: string; winner?: string; onNewGame: () => void }) {
  return (
    <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", background: "rgba(0,0,0,0.6)", borderRadius: "4px" }}>
      <div style={{ background: "#fff", padding: "24px 40px", borderRadius: "12px", textAlign: "center" }}>
        <div style={{ fontSize: "24px", fontWeight: "bold", marginBottom: "8px" }}>
          {status === "checkmate" ? "Checkmate" : status === "draw" ? "Draw" : "Stalemate"}
        </div>
        <div style={{ fontSize: "16px", color: "#666", marginBottom: "16px" }}>
          {status === "checkmate" ? `${winner && winner[0].toUpperCase() + winner.slice(1)} wins` : "Draw"}
        </div>
        <button onClick={onNewGame} style={{ padding: "10px 24px", fontSize: "14px", cursor: "pointer", border: "2px solid #333", borderRadius: "8px", background: "#333", color: "#fff" }}>
          New Game
        </button>
      </div>
    </div>
  );
}

function MoveHistory({ moves }: { moves: string[] }) {
  if (moves.length === 0) return null;
  return (
    <div style={{ maxWidth: "560px", width: "100%" }}>
      <strong>Moves:</strong>{" "}
      <span style={{ fontFamily: "monospace", fontSize: "14px" }}>
        {moves.map((m, i) => <span key={i}>{i % 2 === 0 ? `${Math.floor(i / 2) + 1}. ` : ""}{m} </span>)}
      </span>
    </div>
  );
}
