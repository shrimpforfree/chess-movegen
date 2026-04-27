"use client";

import { useState, useEffect } from "react";

interface PieceInfo {
  id: string;
  fenChar: string;
  value: number;
  description: string;
  movesFrom: string[];
}

interface CustomPieceInfo {
  name: string;
  description: string;
  value: number;
  movesFrom: string[];
}

interface Props {
  /** FEN string (standard mode) or list of piece kind names on the board (fusion mode). */
  fen?: string;
  pieceKinds?: string[];
  /** Dynamically created piece (fusion upgrade) that isn't in the /api/pieces list. */
  customPiece?: CustomPieceInfo;
}

const standardPieceIds = new Set(["pawn", "knight", "bishop", "rook", "queen", "king"]);

/** Extract unique piece chars (lowercase) from a FEN's piece placement section. */
function piecesInFen(fen: string): Set<string> {
  const placement = fen.split(" ")[0];
  const chars = new Set<string>();
  for (const ch of placement) {
    if (ch !== "/" && !/\d/.test(ch)) chars.add(ch.toLowerCase());
  }
  return chars;
}

/** Render a mini 8x8 grid showing reachable squares from D4. */
function MovementDiagram({ squares, fenChar }: { squares: string[]; fenChar: string }) {
  const reachable = new Set(squares);
  const cells: React.ReactNode[] = [];
  for (let rank = 7; rank >= 0; rank--) {
    for (let file = 0; file < 8; file++) {
      const sq = String.fromCharCode(97 + file) + String(rank + 1);
      const isOrigin = sq === "d4";
      const isReachable = reachable.has(sq);
      const isDark = (rank + file) % 2 === 1;
      cells.push(
        <div key={sq} style={{
          width: "20px", height: "20px",
          background: isOrigin ? "#4a90d9" : isReachable ? "#7bc67e" : isDark ? "#b58863" : "#f0d9b5",
          display: "flex", alignItems: "center", justifyContent: "center",
          fontSize: "12px", fontWeight: "bold", color: isOrigin ? "#fff" : undefined,
        }}>
          {isOrigin ? fenChar.toUpperCase() : ""}
        </div>
      );
    }
  }
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(8, 20px)", border: "1px solid #ccc", borderRadius: "2px", overflow: "hidden", marginTop: "6px" }}>
      {cells}
    </div>
  );
}

export default function RulesPanel({ fen, pieceKinds, customPiece }: Props) {
  const [allPieces, setAllPieces] = useState<PieceInfo[]>([]);
  const [seenIds, setSeenIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    fetch("/api/pieces")
      .then((res) => res.json())
      .then((data) => { if (data.pieces) setAllPieces(data.pieces); })
      .catch(() => {});
  }, []);

  // Track non-standard pieces — once seen, always shown
  useEffect(() => {
    const currentIds = new Set<string>();
    if (pieceKinds) {
      pieceKinds.forEach(k => { if (!standardPieceIds.has(k)) currentIds.add(k); });
    } else if (fen) {
      const standardChars = new Set(["p", "n", "b", "r", "q", "k"]);
      const onBoard = piecesInFen(fen);
      allPieces.forEach(p => { if (onBoard.has(p.fenChar) && !standardChars.has(p.fenChar)) currentIds.add(p.id); });
    }
    if (currentIds.size > 0) {
      setSeenIds(prev => {
        const merged = new Set(prev);
        currentIds.forEach(id => merged.add(id));
        return merged.size > prev.size ? merged : prev;
      });
    }
  }, [fen, pieceKinds, allPieces]);

  const visiblePieces = allPieces.filter(p => seenIds.has(p.id));

  // Also check if custom piece is already covered by the fetched pieces
  const hasCustom = customPiece && !visiblePieces.some(p => p.id === customPiece.name);

  if (visiblePieces.length === 0 && !hasCustom) return null;

  return (
    <div style={{
      width: "220px", maxHeight: "560px", overflowY: "auto",
      border: "1px solid #ddd", borderRadius: "8px", padding: "12px",
      fontSize: "13px", lineHeight: "1.4", background: "#fafafa",
    }}>
      <div style={{ fontWeight: "bold", fontSize: "14px", marginBottom: "10px", color: "#333" }}>
        Piece Rules
      </div>
      {hasCustom && customPiece && (
        <div style={{ marginBottom: "14px", paddingBottom: "14px", borderBottom: "1px solid #eee" }}>
          <div style={{ fontWeight: "bold", color: "#6d28d9" }}>
            {customPiece.name}
            {customPiece.value > 0 && (
              <span style={{ fontWeight: "normal", color: "#999", marginLeft: "6px" }}>
                {(customPiece.value / 100).toFixed(1)}
              </span>
            )}
          </div>
          <div style={{ color: "#666", marginTop: "4px" }}>{customPiece.description}</div>
          {customPiece.movesFrom.length > 0 && (
            <MovementDiagram squares={customPiece.movesFrom} fenChar="★" />
          )}
        </div>
      )}
      {visiblePieces.map(piece => (
        <div key={piece.id} style={{ marginBottom: "14px", paddingBottom: "14px", borderBottom: "1px solid #eee" }}>
          <div style={{ fontWeight: "bold", color: "#333" }}>
            {piece.fenChar.toUpperCase()} &mdash; {piece.id.charAt(0).toUpperCase() + piece.id.slice(1)}
            {piece.value > 0 && (
              <span style={{ fontWeight: "normal", color: "#999", marginLeft: "6px" }}>
                {(piece.value / 100).toFixed(1)}
              </span>
            )}
          </div>
          <div style={{ color: "#666", marginTop: "4px" }}>{piece.description}</div>
          <MovementDiagram squares={piece.movesFrom} fenChar={piece.fenChar} />
        </div>
      ))}
    </div>
  );
}
