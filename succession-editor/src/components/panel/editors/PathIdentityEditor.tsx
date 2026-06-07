import type { PathGraphEdge, PathEdgeData } from "../../../model/types";
import { useEditorStore } from "../../../store/editorStore";

interface Props {
  edge: PathGraphEdge;
  onChange: (patch: Partial<PathEdgeData>) => void;
}

export function PathIdentityEditor({ edge, onChange }: Props) {
  const nodes = useEditorStore((s) => s.nodes);
  const sourceNode = nodes.find((n) => n.id === edge.source);
  const targetNode = nodes.find((n) => n.id === edge.target);

  return (
    <div className="prop-section">
      <div className="prop-section-title">Path Identity</div>

      <div className="prop-row">
        <label>path_id</label>
        <input
          type="text"
          value={edge.data!.pathId}
          onChange={(e) => onChange({ pathId: e.target.value })}
          className="prop-input mono"
          style={{ width: "100%" }}
        />
      </div>

      <div className="prop-row">
        <label>priority</label>
        <input
          type="number"
          value={edge.data!.priority}
          onChange={(e) => onChange({ priority: parseInt(e.target.value) || 0 })}
          className="prop-input"
          style={{ width: 80 }}
          min={0}
        />
      </div>

      <div className="prop-row">
        <label>Source Biome</label>
        <code style={{ color: "#81c784", fontSize: 11 }}>
          {sourceNode?.data.biomeMeta?.displayName ?? sourceNode?.data.biomeId ?? "?"}
        </code>
      </div>

      <div className="prop-row">
        <label>Target Biome</label>
        <code style={{ color: "#64b5f6", fontSize: 11 }}>
          {targetNode?.data.biomeMeta?.displayName ?? targetNode?.data.biomeId ?? "?"}
        </code>
      </div>

      <div className="prop-row">
        <label>fallback_biome</label>
        <input
          type="text"
          value={sourceNode?.data.biomeId ?? "minecraft:plains"}
          className="prop-input mono"
          style={{ width: "100%", opacity: 0.5 }}
          disabled
          title="Fallback is auto-derived from source biome"
        />
        <span style={{ fontSize: 9, color: "#666" }}>
          Auto: source biome
        </span>
      </div>
    </div>
  );
}
