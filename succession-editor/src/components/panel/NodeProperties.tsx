import { useEditorStore } from "../../store/editorStore";
import type { BiomeGraphNode } from "../../model/types";

interface Props {
  node: BiomeGraphNode;
}

export function NodeProperties({ node }: Props) {
  const removeNode = useEditorStore((s) => s.removeNode);
  const connectedEdges = useEditorStore((s) => s.getConnectedEdges(node.id));
  const meta = node.data.biomeMeta;

  const outEdges = connectedEdges.filter((e) => e.source === node.id);
  const inEdges = connectedEdges.filter((e) => e.target === node.id);

  return (
    <div style={{ padding: 12 }}>
      {/* Biome info */}
      <div className="prop-section">
        <div className="prop-section-title">Biome Info</div>
        <div className="prop-row">
          <label>Biome ID</label>
          <code style={{ color: "#a5d6a7" }}>{node.data.biomeId}</code>
        </div>
        {meta && (
          <>
            <div className="prop-row">
              <label>Category</label>
              <span>{meta.category}</span>
            </div>
            <div className="prop-row">
              <label>Default Temp</label>
              <span>🌡 {meta.defaultTemp.toFixed(2)}</span>
            </div>
            <div className="prop-row">
              <label>Default Downfall</label>
              <span>💧 {meta.defaultDownfall.toFixed(2)}</span>
            </div>
          </>
        )}
      </div>

      {/* Connection info */}
      <div className="prop-section">
        <div className="prop-section-title">Connections</div>
        <div className="prop-row">
          <label>Total Edges</label>
          <span>{connectedEdges.length}</span>
        </div>
        <div className="prop-row">
          <label>Outgoing Paths</label>
          <span style={{ color: "#81c784" }}>{outEdges.length} →</span>
        </div>
        {outEdges.map((e) => (
          <div key={e.id} className="prop-row" style={{ fontSize: 11, paddingLeft: 12 }}>
            → {e.data!.pathId.replace("ecoflux:", "")}
          </div>
        ))}
        <div className="prop-row">
          <label>Incoming Paths</label>
          <span style={{ color: "#64b5f6" }}>← {inEdges.length}</span>
        </div>
        {inEdges.map((e) => (
          <div key={e.id} className="prop-row" style={{ fontSize: 11, paddingLeft: 12 }}>
            ← {e.data!.pathId.replace("ecoflux:", "")}
          </div>
        ))}
      </div>

      {/* Actions */}
      <div className="prop-section">
        <button
          onClick={() => removeNode(node.id)}
          style={{
            width: "100%",
            padding: "8px",
            background: "#c62828",
            color: "#fff",
            border: "none",
            borderRadius: 6,
            cursor: "pointer",
            fontSize: 13,
          }}
        >
          🗑 Remove Node
        </button>
      </div>
    </div>
  );
}
