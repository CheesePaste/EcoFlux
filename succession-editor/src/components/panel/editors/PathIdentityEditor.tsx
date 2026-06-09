import type { PathGraphEdge, PathEdgeData } from "../../../model/types";
import { useEditorStore } from "../../../store/editorStore";
import { useT } from "../../../i18n/I18nContext";

interface Props {
  edge: PathGraphEdge;
  onChange: (patch: Partial<PathEdgeData>) => void;
}

export function PathIdentityEditor({ edge, onChange }: Props) {
  const { t } = useT();
  const nodes = useEditorStore((s) => s.nodes);
  const sourceNode = nodes.find((n) => n.id === edge.source);
  const targetNode = nodes.find((n) => n.id === edge.target);

  return (
    <div className="prop-section">
      <div className="prop-section-title">{t("path.identity")}</div>

      <div className="prop-row">
        <label>{t("path.pathId")}</label>
        <input
          type="text"
          value={edge.data!.pathId}
          onChange={(e) => onChange({ pathId: e.target.value })}
          className="prop-input mono"
          style={{ width: "100%" }}
        />
      </div>

      <div className="prop-row">
        <label>{t("path.priority")}</label>
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
        <label>{t("path.sourceBiome")}</label>
        <code style={{ color: "#81c784", fontSize: 11 }}>
          {(sourceNode?.data as any)?.biomeMeta?.displayName ?? (sourceNode?.data as any)?.biomeId ?? "?"}
        </code>
      </div>

      <div className="prop-row">
        <label>{t("path.targetBiome")}</label>
        <code style={{ color: "#64b5f6", fontSize: 11 }}>
          {(targetNode?.data as any)?.biomeMeta?.displayName ?? (targetNode?.data as any)?.biomeId ?? "?"}
        </code>
      </div>

      <div className="prop-row">
        <label>{t("path.fallbackBiome")}</label>
        <input
          type="text"
          value={(sourceNode?.data as any)?.biomeId ?? "minecraft:plains"}
          className="prop-input mono"
          style={{ width: "100%", opacity: 0.5 }}
          disabled
        />
        <span style={{ fontSize: 9, color: "#666" }}>
          {t("path.fallbackAuto")}
        </span>
      </div>
    </div>
  );
}
