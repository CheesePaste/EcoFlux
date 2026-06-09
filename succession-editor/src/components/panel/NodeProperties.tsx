import { useMemo } from "react";
import { useEditorStore } from "../../store/editorStore";
import type { BiomeGraphNode } from "../../model/types";
import { useT } from "../../i18n/I18nContext";

interface Props {
  node: BiomeGraphNode;
}

export function NodeProperties({ node }: Props) {
  const { t } = useT();
  const removeNode = useEditorStore((s) => s.removeNode);
  const edges = useEditorStore((s) => s.edges);
  const connectedEdges = useMemo(
    () => edges.filter((e) => e.source === node.id || e.target === node.id),
    [edges, node.id],
  );
  const meta = node.data.biomeMeta;

  const outEdges = connectedEdges.filter((e) => e.source === node.id);
  const inEdges = connectedEdges.filter((e) => e.target === node.id);

  return (
    <div style={{ padding: 12 }}>
      {/* Biome info */}
      <div className="prop-section">
        <div className="prop-section-title">{t("node.biomeInfo")}</div>
        <div className="prop-row">
          <label>{t("node.biomeId")}</label>
          <code style={{ color: "#a5d6a7" }}>{node.data.biomeId}</code>
        </div>
        {meta && (
          <>
            <div className="prop-row">
              <label>{t("node.category")}</label>
              <span>{meta.category}</span>
            </div>
            <div className="prop-row">
              <label>{t("node.defaultTemp")}</label>
              <span>🌡 {meta.defaultTemp.toFixed(2)}</span>
            </div>
            <div className="prop-row">
              <label>{t("node.defaultDownfall")}</label>
              <span>💧 {meta.defaultDownfall.toFixed(2)}</span>
            </div>
          </>
        )}
      </div>

      {/* Connection info */}
      <div className="prop-section">
        <div className="prop-section-title">{t("node.connections")}</div>
        <div className="prop-row">
          <label>{t("node.totalEdges")}</label>
          <span>{connectedEdges.length}</span>
        </div>
        <div className="prop-row">
          <label>{t("node.outgoing")}</label>
          <span style={{ color: "#81c784" }}>{outEdges.length} →</span>
        </div>
        {outEdges.map((e) => (
          <div key={e.id} className="prop-row" style={{ fontSize: 11, paddingLeft: 12 }}>
            → {e.data!.pathId.replace("ecoflux:", "")}
          </div>
        ))}
        <div className="prop-row">
          <label>{t("node.incoming")}</label>
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
          {t("node.removeNode")}
        </button>
      </div>
    </div>
  );
}
