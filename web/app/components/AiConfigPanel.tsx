"use client";

import { useState, useCallback } from "react";
import EngineConfigPanel from "./EngineConfigPanel";
import { type EngineConfig } from "@/app/lib/types";

interface Props {
  gameId: string;
}

export default function AiConfigPanel({ gameId }: Props) {
  const [config, setConfig] = useState<EngineConfig>({
    depth: 6,
    useBook: true,
    useHash: true,
    hashSizeMb: 16,
    contempt: 0,
    useNullMove: true,
    nullMoveDepthReduction: 2,
    nullMoveThreshold: 0,
  });

  const saveConfig = useCallback(async (newConfig: EngineConfig) => {
    setConfig(newConfig);
    await fetch(`/api/games/${gameId}/config`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ target: "ai", config: newConfig }),
    });
  }, [gameId]);

  const [open, setOpen] = useState(false);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        border: "1px solid #ccc",
        borderRadius: "8px",
        width: "180px",
        alignSelf: "flex-start",
        overflow: "hidden",
      }}
    >
      <button
        onClick={() => setOpen(!open)}
        style={{
          padding: "10px 16px",
          fontSize: "13px",
          fontWeight: "bold",
          cursor: "pointer",
          border: "none",
          background: "#fafafa",
          color: "#333",
          textAlign: "left",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        AI Settings
        <span style={{ fontSize: "10px", color: "#999" }}>{open ? "▲" : "▼"}</span>
      </button>
      {open && (
        <div style={{ padding: "12px 16px", maxHeight: "60vh", overflowY: "auto" }}>
          <EngineConfigPanel
            label=""
            config={config}
            onChange={saveConfig}
          />
        </div>
      )}
    </div>
  );
}
