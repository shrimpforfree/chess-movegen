"use client";

import { useState, useEffect } from "react";

interface PieceInfo {
  id: string;
  fenChar: string;
  value: number;
  description: string;
  movesFrom: string[];
}

interface Props {
  fen: string; // current board FEN — used to determine which pieces are on the board
}

/** Extract unique piece chars (lowercase) from a FEN's piece placement section. */
function piecesInFen(fen: string): Set<string> {
  const placement = fen.split(" ")[0];
  const chars = new Set<string>();
  for (const ch of placement) {
    if (ch !== "/" && !/\d/.test(ch)) {
      chars.add(ch.toLowerCase());
    }
  }
  return chars;
}

/** Render a mini 8x8 grid showing reachable squares from D4. */
function MovementDiagram({
  squares,
  fenChar,
}: {
  squares: string[];
  fenChar: string;
}) {
  const reachable = new Set(squares);
  const cells: React.ReactNode[] = [];

  for (let rank = 7; rank >= 0; rank--) {
    for (let file = 0; file < 8; file++) {
      const sq =
        String.fromCharCode(97 + file) + String(rank + 1);
      const isOrigin = sq === "d4";
      const isReachable = reachable.has(sq);
      const isDark = (rank + file) % 2 === 1;

      cells.push(
        <div
          key={sq}
          style={{
            width: "20px",
            height: "20px",
            background: isOrigin
              ? "#4a90d9"
              : isReachable
              ? "#7bc67e"
              : isDark
              ? "#b58863"
              : "#f0d9b5",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "12px",
            fontWeight: "bold",
            color: isOrigin ? "#fff" : undefined,
          }}
        >
          {isOrigin ? fenChar.toUpperCase() : ""}
        </div>
      );
    }
  }

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(8, 20px)",
        border: "1px solid #ccc",
        borderRadius: "2px",
        overflow: "hidden",
        marginTop: "6px",
      }}
    >
      {cells}
    </div>
  );
}

export default function RulesPanel({ fen }: Props) {
  const [allPieces, setAllPieces] = useState<PieceInfo[]>([]);

  useEffect(() => {
    fetch("/api/pieces")
      .then((res) => res.json())
      .then((data) => {
        if (data.pieces) setAllPieces(data.pieces);
      })
      .catch(() => {});
  }, []);

  const standardChars = new Set(["p", "n", "b", "r", "q", "k"]);
  const onBoard = piecesInFen(fen);

  // Only show custom pieces that are on the board
  const visiblePieces = allPieces.filter(
    (p) => onBoard.has(p.fenChar) && !standardChars.has(p.fenChar)
  );

  // Hide panel entirely if no custom pieces on the board
  if (visiblePieces.length === 0) return null;

  return (
    <div
      style={{
        width: "220px",
        maxHeight: "560px",
        overflowY: "auto",
        border: "1px solid #ddd",
        borderRadius: "8px",
        padding: "12px",
        fontSize: "13px",
        lineHeight: "1.4",
        background: "#fafafa",
      }}
    >
      <div
        style={{
          fontWeight: "bold",
          fontSize: "14px",
          marginBottom: "10px",
          color: "#333",
        }}
      >
        Piece Rules
      </div>

      {visiblePieces.map((piece) => (
        <div
          key={piece.id}
          style={{
            marginBottom: "14px",
            paddingBottom: "14px",
            borderBottom: "1px solid #eee",
          }}
        >
          <div style={{ fontWeight: "bold", color: "#333" }}>
            {piece.fenChar.toUpperCase()} &mdash;{" "}
            {piece.id.charAt(0).toUpperCase() + piece.id.slice(1)}
            {piece.value > 0 && (
              <span style={{ fontWeight: "normal", color: "#999", marginLeft: "6px" }}>
                {(piece.value / 100).toFixed(1)}
              </span>
            )}
          </div>
          <div style={{ color: "#666", marginTop: "4px" }}>
            {piece.description}
          </div>
          <MovementDiagram squares={piece.movesFrom} fenChar={piece.fenChar} />
        </div>
      ))}
    </div>
  );
}
