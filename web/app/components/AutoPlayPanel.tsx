"use client";

import { useState, useRef, useCallback, useImperativeHandle, forwardRef } from "react";
import EngineConfigPanel from "./EngineConfigPanel";
import { type EngineConfig } from "@/app/lib/types";

export interface AutoPlayRef {
  start: () => void;
  pause: () => void;
  running: boolean;
}

interface Props {
  gameId: string;
  onRunningChange?: (running: boolean) => void;
}

const defaultConfig: EngineConfig = {
  depth: 6,
  useBook: true,
  useHash: true,
  hashSizeMb: 16,
  contempt: 0,
  useNullMove: true,
  nullMoveDepthReduction: 2,
  nullMoveThreshold: 0,
};

const AutoPlayPanel = forwardRef<AutoPlayRef, Props>(({ gameId, onRunningChange }, ref) => {
  const [whiteConfig, setWhiteConfig] = useState<EngineConfig>({ ...defaultConfig });
  const [blackConfig, setBlackConfig] = useState<EngineConfig>({ ...defaultConfig });
  const [delay, setDelay] = useState(1);
  const [running, setRunning] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pendingRef = useRef(false);

  const saveConfig = useCallback(async (target: "white" | "black", config: EngineConfig) => {
    await fetch(`/api/games/${gameId}/config`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ target, config }),
    });
  }, [gameId]);

  const handleWhiteChange = (config: EngineConfig) => {
    setWhiteConfig(config);
    saveConfig("white", config);
  };

  const handleBlackChange = (config: EngineConfig) => {
    setBlackConfig(config);
    saveConfig("black", config);
  };

  const triggerMove = useCallback(async () => {
    if (pendingRef.current) return;
    pendingRef.current = true;

    try {
      const res = await fetch(`/api/games/${gameId}/auto-move`, {
        method: "POST",
      });
      const data = await res.json();

      if (data.gameOver || data.error) {
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
          intervalRef.current = null;
        }
        setRunning(false);
        onRunningChange?.(false);
      }
    } finally {
      pendingRef.current = false;
    }
  }, [gameId, onRunningChange]);

  const pause = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setRunning(false);
    onRunningChange?.(false);
  }, [onRunningChange]);

  const start = useCallback(() => {
    setRunning(true);
    onRunningChange?.(true);
    triggerMove();
    intervalRef.current = setInterval(triggerMove, delay * 1000);
  }, [triggerMove, delay, onRunningChange]);

  useImperativeHandle(ref, () => ({ start, pause, running }), [start, pause, running]);

  const inputStyle = {
    padding: "8px",
    fontSize: "14px",
    borderRadius: "6px",
    border: "1px solid #ccc",
    opacity: running ? 0.5 : 1,
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        padding: "16px",
        border: "1px solid #ccc",
        borderRadius: "8px",
        width: "280px",
        alignSelf: "flex-start",
        maxHeight: "80vh",
        overflowY: "auto",
      }}
    >
      {/* General */}
      <div style={{ fontWeight: "bold", fontSize: "14px" }}>General</div>

      <label style={{ fontSize: "13px", display: "flex", flexDirection: "column", gap: "4px" }}>
        Delay (sec)
        <input
          type="number"
          min={0}
          max={30}
          step={0.1}
          value={delay}
          onChange={(e) => setDelay(Number(e.target.value))}
          disabled={running}
          style={inputStyle}
        />
      </label>

      <hr style={{ border: "none", borderTop: "1px solid #e0e0e0", margin: "4px 0" }} />

      {/* White & Black side by side */}
      <div style={{ display: "flex", gap: "16px" }}>
        <div style={{ flex: 1 }}>
          <EngineConfigPanel
            label="White"
            config={whiteConfig}
            onChange={handleWhiteChange}
            disabled={running}
          />
        </div>
        <div style={{ flex: 1 }}>
          <EngineConfigPanel
            label="Black"
            config={blackConfig}
            onChange={handleBlackChange}
            disabled={running}
          />
        </div>
      </div>
    </div>
  );
});

AutoPlayPanel.displayName = "AutoPlayPanel";
export default AutoPlayPanel;
