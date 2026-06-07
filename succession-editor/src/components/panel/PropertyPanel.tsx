import { useEditorStore } from "../../store/editorStore";
import { NodeProperties } from "./NodeProperties";
import { PathProperties } from "./PathProperties";
import { NoSelection } from "./NoSelection";
import { useT } from "../../i18n/I18nContext";

export function PropertyPanel() {
  const { t } = useT();
  const selectedId = useEditorStore((s) => s.selectedId);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  const selectedNode = selectedId ? nodes.find((n) => n.id === selectedId) : undefined;
  const selectedEdge = selectedId ? edges.find((e) => e.id === selectedId) : undefined;
  const edgeCount = edges.length;
  const nodeCount = nodes.length;

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
        {selectedEdge
          ? t("panel.titleEdge").replace("{pathId}", selectedEdge.data!.pathId.replace("ecoflux:", ""))
          : selectedNode
            ? t("panel.titleNode").replace("{name}", selectedNode.data.biomeMeta?.displayName ?? selectedNode.data.biomeId)
            : t("panel.titleNone")}
      </div>

      <div style={{ flex: 1, overflowY: "auto" }}>
        {selectedEdge ? (
          <PathProperties edge={selectedEdge} />
        ) : selectedNode ? (
          <NodeProperties node={selectedNode} />
        ) : (
          <NoSelection nodeCount={nodeCount} edgeCount={edgeCount} />
        )}
      </div>
    </div>
  );
}
