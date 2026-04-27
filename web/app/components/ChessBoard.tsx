"use client";

import { Chessboard, defaultPieces } from "react-chessboard";
import { fairyPieces } from "./FairyPieces";

/**
 * Pure rendering component — no game logic.
 * Takes a position (FEN or position object) and callbacks.
 */


interface Props {
  position: string | Record<string, { pieceType: string }>;
  orientation: "white" | "black";
  interactive?: boolean;
  squareStyles?: Record<string, React.CSSProperties>;
  onPieceDrop?: (from: string, to: string, piece: string) => boolean;
  onSquareClick?: (square: string) => void;
}

const kindToCode: Record<string, string> = {
  pawn: "P", knight: "N", bishop: "B", rook: "R", queen: "Q", king: "K",
  archbishop: "A", chancellor: "C", amazon: "Z", camel: "M", zebra: "X", mann: "O",
};

const kindToFenChar: Record<string, string> = {
  pawn: "p", knight: "n", bishop: "b", rook: "r", queen: "q", king: "k",
  archbishop: "a", chancellor: "c", amazon: "z", camel: "m", zebra: "x", mann: "o",
};

/** Convert board JSON to a FEN string for react-chessboard rendering (enables animation). */
export function boardJsonToFen(bj: { pieces: Record<string, { kind: string; color: string }>; sideToMove: string }): string {
  const rows: string[] = [];
  for (let rank = 7; rank >= 0; rank--) {
    let row = "";
    let empty = 0;
    for (let file = 0; file < 8; file++) {
      const sq = String.fromCharCode(97 + file) + String(rank + 1);
      const piece = bj.pieces[sq];
      if (piece) {
        if (empty > 0) { row += empty; empty = 0; }
        let ch = kindToFenChar[piece.kind];
        if (!ch) {
          // Upgraded piece like "bishop+j21" — use base piece char
          const base = piece.kind.split("+")[0];
          ch = kindToFenChar[base] || "p";
        }
        row += piece.color === "white" ? ch.toUpperCase() : ch;
      } else {
        empty++;
      }
    }
    if (empty > 0) row += empty;
    rows.push(row);
  }
  return rows.join("/") + " " + (bj.sideToMove === "white" ? "w" : "b") + " - - 0 1";
}

/** Convert board JSON to react-chessboard position object. */
export function boardJsonToPosition(bj: { pieces: Record<string, { kind: string; color: string }> }): Record<string, { pieceType: string }> {
  const pos: Record<string, { pieceType: string }> = {};
  for (const [sq, piece] of Object.entries(bj.pieces)) {
    const prefix = piece.color === "white" ? "w" : "b";
    let code = kindToCode[piece.kind];
    if (!code) {
      const base = piece.kind.split("+")[0];
      code = kindToCode[base] || "P";
    }
    pos[sq] = { pieceType: prefix + code };
  }
  return pos;
}

export default function ChessBoardComponent({
  position,
  orientation,
  interactive = true,
  squareStyles,
  onPieceDrop,
  onSquareClick,
}: Props) {
  return (
    <div style={{ width: "560px", maxWidth: "100%", position: "relative" }}>
      <Chessboard
        options={{
          position,
          boardOrientation: orientation,
          allowDragging: interactive,
          onPieceDrop: onPieceDrop
            ? ({ piece, sourceSquare, targetSquare }: { piece: { pieceType: string }; sourceSquare: string; targetSquare: string | null }) => {
                if (!targetSquare) return false;
                return onPieceDrop(sourceSquare, targetSquare, piece.pieceType);
              }
            : undefined,
          onSquareClick: onSquareClick
            ? ({ square }: { square: string }) => onSquareClick(square)
            : undefined,
          squareStyles,
          pieces: { ...defaultPieces, ...fairyPieces },
        }}
      />
    </div>
  );
}
