import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type EdgeProps,
} from "@xyflow/react";
import type { PathEdgeData } from "../../model/types";

export function SuccessionEdge(props: EdgeProps) {
  const {
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    selected,
  } = props;
  const data = props.data as unknown as PathEdgeData | undefined;

  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });

  const shortPathId = (data?.pathId ?? id).replace("ecoflux:", "");
  const branchLabel = data?.conditionBranch === "match" ? "[✓] " : data?.conditionBranch === "no_match" ? "[✗] " : "";

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke: selected ? "#ff5722" : "#888",
          strokeWidth: selected ? 3 : 2,
          strokeDasharray: data?.priority === 0 ? "5,5" : undefined,
        }}
      />
      <EdgeLabelRenderer>
        <div
          style={{
            position: "absolute",
            transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            background: selected ? "#ff5722" : "#444",
            color: "#fff",
            padding: "2px 8px",
            borderRadius: 6,
            fontSize: 10,
            fontFamily: "monospace",
            pointerEvents: "all",
            cursor: "pointer",
            whiteSpace: "nowrap",
            border: selected ? "2px solid #fff" : "1px solid #666",
          }}
        >
          {branchLabel}{shortPathId} (p:{data?.priority ?? "?"})
        </div>
      </EdgeLabelRenderer>
    </>
  );
}
