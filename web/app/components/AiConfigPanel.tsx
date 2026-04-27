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

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        padding: "16px",
        border: "1px solid #ccc",
        borderRadius: "8px",
        width: "180px",
        alignSelf: "flex-start",
        maxHeight: "80vh",
        overflowY: "auto",
      }}
    >
      <EngineConfigPanel
        label="AI Settings"
        config={config}
        onChange={saveConfig}
      />
    </div>
  );
}
