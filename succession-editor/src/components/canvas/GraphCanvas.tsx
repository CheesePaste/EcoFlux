import { useCallback } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Connection,
  type Edge,
  BackgroundVariant,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEditorStore } from "../../store/editorStore";
import { BiomeNode } from "./BiomeNode";
import { SuccessionEdge } from "./SuccessionEdge";

const nodeTypes = { biome: BiomeNode };
const edgeTypes = { succession: SuccessionEdge };

function miniMapNodeColor(node: any): string {
  const temp = node.data?.biomeMeta?.defaultTemp ?? 0.5;
  if (temp < 0.2) return "#81d4fa";
  if (temp < 0.6) return "#a5d6a7";
  if (temp < 1.0) return "#fff176";
  if (temp < 1.5) return "#ffcc80";
  return "#ef9a9a";
}

export function GraphCanvas() {
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const setSelectedId = useEditorStore((s) => s.setSelectedId);
  const addEdge = useEditorStore((s) => s.addEdge);
  const validationErrors = useEditorStore((s) => s.validationErrors);

  const onConnect = useCallback(
    (connection: Connection) => {
      if (connection.source && connection.target) {
        addEdge(connection.source, connection.target);
      }
    },
    [addEdge],
  );

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: any) => {
      setSelectedId(node.id);
    },
    [setSelectedId],
  );

  const onEdgeClick = useCallback(
    (_: React.MouseEvent, edge: any) => {
      setSelectedId(edge.id);
    },
    [setSelectedId],
  );

  const onPaneClick = useCallback(() => {
    setSelectedId(null);
  }, [setSelectedId]);

  // Highlight nodes/edges with errors
  const errorIds = new Set(validationErrors.map((e) => e.targetId));
  const nodesWithErrors = nodes.map((n) => ({
    ...n,
    style: errorIds.has(n.id)
      ? { ...n.style, border: "2px solid #f44336", boxShadow: "0 0 10px rgba(244,67,54,0.6)" }
      : n.style,
  }));
  const edgesWithErrors: Edge[] = edges.map((e) => ({
    ...e,
    style: errorIds.has(e.id)
      ? { ...e.style, stroke: "#f44336", strokeWidth: 3 }
      : e.style,
  }));

  return (
    <div style={{ flex: 1, height: "100%" }}>
      <ReactFlow
        nodes={nodesWithErrors}
        edges={edgesWithErrors}
        onConnect={onConnect}
        onNodeClick={onNodeClick}
        onEdgeClick={onEdgeClick}
        onPaneClick={onPaneClick}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        deleteKeyCode={["Backspace", "Delete"]}
        multiSelectionKeyCode="Shift"
        snapToGrid
        snapGrid={[16, 16]}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#333" />
        <Controls />
        <MiniMap nodeColor={miniMapNodeColor} maskColor="rgba(0,0,0,0.4)" />
      </ReactFlow>
    </div>
  );
}
