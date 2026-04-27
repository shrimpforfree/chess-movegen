"use client";

import React from "react";
import { defaultPieces } from "react-chessboard";

/**
 * Fairy chess piece renderers.
 *
 * Archbishop, Chancellor, Amazon, Zebra, and Mann use Cburnett-style SVGs
 * from Wikimedia Commons (CC-BY-SA 3.0, by User:Cburnett).
 *
 * Camel uses the standard knight with a badge (no Cburnett SVG exists for it).
 */

interface PieceProps {
  fill?: string;
  square?: string;
  svgStyle?: React.CSSProperties;
}

type PieceRenderer = (props?: PieceProps) => React.JSX.Element;

/** Render a fairy piece from a local SVG file in /public/pieces/wiki/ */
function svgPiece(src: string): PieceRenderer {
  return function SvgPiece(props?: PieceProps) {
    return (
      <img
        src={src}
        alt=""
        style={{ width: "100%", height: "100%", ...props?.svgStyle }}
        draggable={false}
      />
    );
  };
}

/** Wrap a standard piece with a small colored badge */
function withBadge(
  BasePiece: PieceRenderer,
  label: string,
  badgeColor: string,
): PieceRenderer {
  return function BadgedPiece(props?: PieceProps) {
    return (
      <div style={{ position: "relative", width: "100%", height: "100%" }}>
        <BasePiece {...props} />
        <div
          style={{
            position: "absolute",
            bottom: "2%",
            right: "2%",
            width: "30%",
            height: "30%",
            borderRadius: "50%",
            background: badgeColor,
            border: "1.5px solid rgba(0,0,0,0.3)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "70%",
            fontWeight: "bold",
            fontFamily: "sans-serif",
            color: "#fff",
            lineHeight: 1,
            boxShadow: "0 1px 2px rgba(0,0,0,0.3)",
          }}
        >
          {label}
        </div>
      </div>
    );
  };
}

export const fairyPieces: Record<string, PieceRenderer> = {
  // Wikimedia Cburnett-style SVGs (CC-BY-SA 3.0)
  wA: svgPiece("/pieces/wiki/wArchbishop.svg"),
  bA: svgPiece("/pieces/wiki/bArchbishop.svg"),
  wC: svgPiece("/pieces/wiki/wChancellor.svg"),
  bC: svgPiece("/pieces/wiki/bChancellor.svg"),
  wZ: svgPiece("/pieces/wiki/wAmazon.svg"),
  bZ: svgPiece("/pieces/wiki/bAmazon.svg"),
  wX: svgPiece("/pieces/wiki/wZebra.svg"),
  bX: svgPiece("/pieces/wiki/bZebra.svg"),
  wO: svgPiece("/pieces/wiki/wMann.svg"),
  bO: svgPiece("/pieces/wiki/bMann.svg"),

  // Camel — no Cburnett SVG exists, use knight with badge
  wM: withBadge(defaultPieces.wN, "C", "#8b5cf6"),
  bM: withBadge(defaultPieces.bN, "C", "#8b5cf6"),
};
