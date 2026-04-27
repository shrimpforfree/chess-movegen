"use client";

import { type EngineConfig } from "@/app/lib/types";

interface Props {
  config: EngineConfig;
  onChange: (config: EngineConfig) => void;
  disabled?: boolean;
  label?: string;
}

export default function EngineConfigPanel({ config, onChange, disabled, label }: Props) {
  const update = (partial: Partial<EngineConfig>) => {
    onChange({ ...config, ...partial });
  };

  const inputStyle = {
    padding: "6px",
    fontSize: "13px",
    borderRadius: "4px",
    border: "1px solid #ccc",
    opacity: disabled ? 0.5 : 1,
    width: "100%",
    boxSizing: "border-box" as const,
  };

  const labelStyle = {
    fontSize: "12px",
    display: "flex" as const,
    flexDirection: "column" as const,
    gap: "2px",
  };

  const checkboxRow = {
    fontSize: "12px",
    display: "flex" as const,
    alignItems: "center" as const,
    gap: "6px",
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
      {label && <div style={{ fontWeight: "bold", fontSize: "14px" }}>{label}</div>}

      <label style={labelStyle}>
        Depth
        <input
          type="number"
          min={1}
          max={20}
          value={config.depth ?? 6}
          onChange={(e) => update({ depth: Number(e.target.value) })}
          disabled={disabled}
          style={inputStyle}
        />
      </label>

      <label style={checkboxRow}>
        <input
          type="checkbox"
          checked={config.useBook ?? true}
          onChange={(e) => update({ useBook: e.target.checked })}
          disabled={disabled}
        />
        Opening book
      </label>

      <label style={checkboxRow}>
        <input
          type="checkbox"
          checked={config.useHash ?? true}
          onChange={(e) => update({ useHash: e.target.checked })}
          disabled={disabled}
        />
        Hash table
      </label>

      {config.useHash && (
        <label style={labelStyle}>
          Hash size (MB)
          <input
            type="number"
            min={1}
            max={1024}
            value={config.hashSizeMb ?? 16}
            onChange={(e) => update({ hashSizeMb: Number(e.target.value) })}
            disabled={disabled}
            style={inputStyle}
          />
        </label>
      )}

      <label style={labelStyle}>
        Contempt
        <input
          type="number"
          min={-100}
          max={100}
          value={config.contempt ?? 0}
          onChange={(e) => update({ contempt: Number(e.target.value) })}
          disabled={disabled}
          style={inputStyle}
        />
      </label>

      <label style={checkboxRow}>
        <input
          type="checkbox"
          checked={config.useNullMove ?? true}
          onChange={(e) => update({ useNullMove: e.target.checked })}
          disabled={disabled}
        />
        Null move pruning
      </label>

      {(config.useNullMove ?? true) && (
        <>
          <label style={labelStyle}>
            NM depth reduction
            <input
              type="number"
              min={1}
              max={6}
              value={config.nullMoveDepthReduction ?? 2}
              onChange={(e) => update({ nullMoveDepthReduction: Number(e.target.value) })}
              disabled={disabled}
              style={inputStyle}
            />
          </label>

          <label style={labelStyle}>
            NM threshold
            <input
              type="number"
              min={0}
              max={10000}
              step={50}
              value={config.nullMoveThreshold ?? 0}
              onChange={(e) => update({ nullMoveThreshold: Number(e.target.value) })}
              disabled={disabled}
              style={inputStyle}
            />
          </label>
        </>
      )}
    </div>
  );
}
