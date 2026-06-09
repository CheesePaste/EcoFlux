import { Handle, Position, type NodeProps } from "@xyflow/react";
import type { ConditionNodeData } from "../../model/types";

export function ConditionNode(props: NodeProps) {
  const data = props.data as unknown as ConditionNodeData;
  const selected = props.selected;
  const cond = data.condition;
  const tempStr = `T:${cond.temperature.min.toFixed(1)}-${cond.temperature.max.toFixed(1)}`;
  const downStr = `D:${cond.downfall.min.toFixed(1)}-${cond.downfall.max.toFixed(1)}`;

  return (
    <div style={{ position: "relative", width: 110, height: 110 }}>
      {/* Diamond shape via rotation */}
      <div
        style={{
          position: "absolute",
          inset: 15,
          background: "#ffb74d",
          border: selected ? "3px solid #ff5722" : "2px solid #e65100",
          transform: "rotate(45deg)",
          borderRadius: 8,
          boxShadow: selected ? "0 0 14px rgba(255,87,34,0.5)" : "0 2px 6px rgba(0,0,0,0.3)",
        }}
      />
      {/* Content counter-rotated to stay readable */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          fontFamily: "monospace",
          fontSize: 10,
          color: "#222",
          pointerEvents: "none",
          padding: 8,
        }}
      >
        <div style={{ fontWeight: "bold", fontSize: 11, textAlign: "center" }}>
          {data.label}
        </div>
        <div style={{ marginTop: 2, opacity: 0.7 }}>{tempStr}</div>
        <div style={{ opacity: 0.7 }}>{downStr}</div>
      </div>

      {/* Target handle (top) */}
      <Handle
        type="target"
        position={Position.Top}
        id="target"
        style={{ background: "#ffb74d", border: "2px solid #e65100", width: 10, height: 10 }}
      />

      {/* Match handle (left, green) */}
      <Handle
        type="source"
        position={Position.Left}
        id="match"
        style={{ background: "#4caf50", border: "2px solid #2e7d32", width: 12, height: 12 }}
      />

      {/* No-match handle (right, red) */}
      <Handle
        type="source"
        position={Position.Right}
        id="no_match"
        style={{ background: "#ef5350", border: "2px solid #c62828", width: 12, height: 12 }}
      />
    </div>
  );
}
