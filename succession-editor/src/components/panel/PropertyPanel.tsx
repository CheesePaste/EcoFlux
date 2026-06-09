import { useEditorStore } from "../../store/editorStore";
import { NodeProperties } from "./NodeProperties";
import { ConditionProperties } from "./ConditionProperties";
import { PathProperties } from "./PathProperties";
import { NoSelection } from "./NoSelection";
import { useT } from "../../i18n/I18nContext";
import type { ConditionGraphNode } from "../../model/types";

export function PropertyPanel() {
  const { t } = useT();
  const selectedId = useEditorStore((s) => s.selectedId);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  const selectedNode = selectedId ? nodes.find((n) => n.id === selectedId) : undefined;
  const selectedEdge = selectedId ? edges.find((e) => e.id === selectedId) : undefined;
  const isConditionNode = selectedNode?.data?.type === "condition";
  const edgeCount = edges.length;
  const nodeCount = nodes.length;

  const headerText = selectedEdge
    ? t("panel.titleEdge").replace("{pathId}", selectedEdge.data!.pathId.replace("ecoflux:", ""))
    : isConditionNode
      ? t("condition.title").replace("{label}", (selectedNode!.data as any).label)
      : selectedNode
        ? t("panel.titleNode").replace("{name}", (selectedNode.data as any)?.biomeMeta?.displayName ?? (selectedNode.data as any)?.biomeId)
        : t("panel.titleNone");

  const panelBody = selectedEdge ? (
    <PathProperties edge={selectedEdge} />
  ) : isConditionNode ? (
    <ConditionProperties node={selectedNode as ConditionGraphNode} />
  ) : selectedNode ? (
    <NodeProperties node={selectedNode as any} />
  ) : (
    <NoSelection nodeCount={nodeCount} edgeCount={edgeCount} />
  );

  return (
    <div
      className="property-panel"
      style={{
        width: 340,
        borderLeft: "1px solid #444",
        background: "#1a1a2e",
        height: "100%",
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <div
        style={{
          padding: "8px 12px",
          borderBottom: "1px solid #444",
          fontWeight: "bold",
          fontSize: 14,
          color: "#ccc",
          background: "#252540",
        }}
      >
        {headerText}
      </div>

      <div style={{ flex: 1, overflowY: "auto" }}>
        {panelBody}
      </div>
    </div>
  );
}
