import { Handle, Position, type NodeProps } from "@xyflow/react";
import type { BiomeNodeData } from "../../model/types";

function tempColor(temp: number): string {
  if (temp < 0) return "#b3e5fc";
  if (temp < 0.3) return "#81d4fa";
  if (temp < 0.6) return "#a5d6a7";
  if (temp < 1.0) return "#fff176";
  if (temp < 1.5) return "#ffcc80";
  return "#ef9a9a";
}

export function BiomeNode(props: NodeProps) {
  const data = props.data as unknown as BiomeNodeData;
  const selected = props.selected;
  const meta = data.biomeMeta;
  const temp = meta?.defaultTemp ?? 0.5;
  const bgColor = tempColor(temp);
  const shortName = data.biomeId.replace("minecraft:", "");

  return (
    <div
      className={`biome-node ${selected ? "selected" : ""}`}
      style={{
        background: bgColor,
        border: selected ? "3px solid #ff5722" : "2px solid #666",
        borderRadius: 12,
        padding: "10px 16px",
        minWidth: 120,
        textAlign: "center",
        fontFamily: "monospace",
        fontSize: 13,
        cursor: "pointer",
        boxShadow: selected ? "0 0 12px rgba(255,87,34,0.5)" : "0 2px 6px rgba(0,0,0,0.3)",
        color: "#222",
      }}
    >
      <Handle type="target" position={Position.Top} style={{ background: "#555" }} />
      <div style={{ fontWeight: "bold", fontSize: 14 }}>{meta?.displayName ?? shortName}</div>
      <div style={{ fontSize: 10, opacity: 0.6 }}>{data.biomeId}</div>
      {meta && (
        <div style={{ fontSize: 10, marginTop: 4 }}>
          🌡{meta.defaultTemp.toFixed(1)} 💧{meta.defaultDownfall.toFixed(1)}
        </div>
      )}
      <Handle type="source" position={Position.Bottom} style={{ background: "#555" }} />
    </div>
  );
}
