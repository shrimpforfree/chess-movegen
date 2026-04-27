"use client";

interface Props {
  evalScore: number;
  playerColor: "white" | "black";
}

export default function EvalBar({ evalScore, playerColor }: Props) {
  const clamped = Math.max(-500, Math.min(500, evalScore));
  const whitePct = 50 + (clamped / 500) * 50;
  const displayVal = `${evalScore > 0 ? "+" : ""}${(evalScore / 100).toFixed(1)}`;
  const isWhiteUp = evalScore >= 0;
  const topColor = playerColor === "white" ? "#333" : "#fff";
  const bottomColor = playerColor === "white" ? "#fff" : "#333";
  const topPct = playerColor === "white" ? (100 - whitePct) : whitePct;

  return (
    <div style={{
      width: "22px", flexShrink: 0, borderRadius: "4px", overflow: "hidden",
      border: "1px solid #ccc", display: "flex", flexDirection: "column", position: "relative",
      height: "560px",
    }}>
      <div style={{ background: topColor, transition: "flex 0.3s ease", flex: `${topPct} 0 0%` }} />
      <div style={{ background: bottomColor, transition: "flex 0.3s ease", flex: `${100 - topPct} 0 0%` }} />
      <div style={{
        position: "absolute", left: "50%", transform: "translateX(-50%)",
        fontSize: "10px", fontWeight: "bold", fontFamily: "monospace",
        ...(isWhiteUp ? { bottom: "4px", color: "#333" } : { top: "4px", color: "#ccc" }),
      }}>
        {displayVal}
      </div>
    </div>
  );
}
