"use client";

import { useState, useImperativeHandle, forwardRef } from "react";
import { FusionUpgrade } from "@/app/lib/types";

export interface FusionDraftRef {
  applyToSquare: (square: string) => void;
  isSelected: () => boolean;
}

export interface UpgradeResult {
  pieceName: string;
  description: string;
  value: number;
  square: string;
  movesFrom?: string[];
}

interface Props {
  gameId: string;
  playerToken: string;
  upgrade: FusionUpgrade;
  onDraftComplete: () => void;
  onUpgradeApplied: (result: UpgradeResult) => void;
}

const FusionDraftPanel = forwardRef<FusionDraftRef, Props>(function FusionDraftPanel(
  { gameId, playerToken, upgrade, onDraftComplete, onUpgradeApplied },
  ref
) {
  const [selected, setSelected] = useState(false);
  const [applied, setApplied] = useState(false);
  const [applying, setApplying] = useState(false);
  const [resultInfo, setResultInfo] = useState<{ pieceName: string; value: number; description: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const applyToSquare = async (square: string) => {
    if (applying || applied || !selected) return;
    setApplying(true);
    setError(null);
    try {
      const res = await fetch(`/api/games/${gameId}/fusion-draft`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ square, playerToken }),
      });
      const data = await res.json();
      if (data.error) {
        setError(data.error);
        setApplying(false);
      } else {
        setApplied(true);
        setApplying(false);
        setResultInfo({ pieceName: data.pieceName, value: data.value, description: data.description });
        onUpgradeApplied({
          pieceName: data.pieceName,
          description: data.description,
          value: data.value,
          square,
          movesFrom: data.movesFrom,
        });
      }
    } catch {
      setError("Failed to apply upgrade");
      setApplying(false);
    }
  };

  useImperativeHandle(ref, () => ({
    applyToSquare,
    isSelected: () => selected && !applied,
  }));

  return (
    <div style={{
      width: "220px",
      border: "1px solid #ddd",
      borderRadius: "8px",
      padding: "16px",
      background: "#fafafa",
      display: "flex",
      flexDirection: "column",
      gap: "12px",
    }}>
      <div style={{ fontWeight: "bold", fontSize: "14px", color: "#333" }}>
        Fusion Draft
      </div>

      {/* Upgrade card */}
      <button
        onClick={() => !applied && setSelected(!selected)}
        disabled={applied}
        style={{
          padding: "12px",
          border: selected ? "2px solid #8b5cf6" : "2px solid #ddd",
          borderRadius: "10px",
          background: selected ? "#f5f3ff" : applied ? "#f0fdf4" : "#fff",
          cursor: applied ? "default" : "pointer",
          textAlign: "left",
          transition: "all 0.15s",
        }}
      >
        <div style={{ fontSize: "15px", fontWeight: "bold", color: applied ? "#16a34a" : "#6d28d9" }}>
          {upgrade.name}
        </div>
        <div style={{ fontSize: "12px", color: "#666", marginTop: "4px" }}>
          {upgrade.description}
        </div>
        {!applied && (
          <div style={{ fontSize: "11px", color: selected ? "#8b5cf6" : "#aaa", marginTop: "6px" }}>
            {selected ? "Click a piece on the board" : "Click to select, then click a piece"}
          </div>
        )}
      </button>

      {resultInfo && (
        <div style={{ fontSize: "12px", color: "#333", padding: "8px", background: "#f0fdf4", borderRadius: "6px" }}>
          <div style={{ fontWeight: "bold" }}>{resultInfo.pieceName}</div>
          <div style={{ color: "#666" }}>{resultInfo.description}</div>
          <div style={{ color: "#999", marginTop: "2px" }}>{resultInfo.value}cp</div>
        </div>
      )}

      {error && <div style={{ color: "red", fontSize: "12px" }}>{error}</div>}

      {/* Skip — start without upgrading */}
      {!applied && (
        <button
          onClick={onDraftComplete}
          style={{
            padding: "8px",
            fontSize: "13px",
            cursor: "pointer",
            border: "1px solid #ccc",
            borderRadius: "6px",
            background: "#fff",
            color: "#999",
          }}
        >
          Skip upgrade
        </button>
      )}

      {applied && (
        <button
          onClick={onDraftComplete}
          style={{
            padding: "12px",
            fontSize: "16px",
            fontWeight: "bold",
            cursor: "pointer",
            border: "2px solid #333",
            borderRadius: "8px",
            background: "#333",
            color: "#fff",
          }}
        >
          Start Game
        </button>
      )}
    </div>
  );
});

export default FusionDraftPanel;
