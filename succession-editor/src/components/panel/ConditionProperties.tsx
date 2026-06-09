import type { ConditionGraphNode, FloatRange } from "../../model/types";
import { useEditorStore } from "../../store/editorStore";
import { useT } from "../../i18n/I18nContext";

interface Props {
  node: ConditionGraphNode;
}

function FloatRangeEditor({ label, value, onChange }: { label: string; value: FloatRange; onChange: (r: FloatRange) => void }) {
  return (
    <div style={{ marginBottom: 8 }}>
      <div style={{ fontSize: 11, color: "#aaa", marginBottom: 4 }}>{label}</div>
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <input
          type="number"
          value={value.min}
          onChange={(e) => onChange({ ...value, min: parseFloat(e.target.value) || 0 })}
          className="prop-input"
          style={{ width: 60, fontSize: 11 }}
          step={0.05}
        />
        <span style={{ color: "#888", fontSize: 11 }}>—</span>
        <input
          type="number"
          value={value.max}
          onChange={(e) => onChange({ ...value, max: parseFloat(e.target.value) || 0 })}
          className="prop-input"
          style={{ width: 60, fontSize: 11 }}
          step={0.05}
        />
      </div>
      {value.min > value.max && (
        <div style={{ color: "#ef5350", fontSize: 10, marginTop: 2 }}>⚠ min &gt; max</div>
      )}
    </div>
  );
}

export function ConditionProperties({ node }: Props) {
  const { t } = useT();
  const removeNode = useEditorStore((s) => s.removeNode);
  const edges = useEditorStore((s) => s.edges);
  const setSelectedId = useEditorStore((s) => s.setSelectedId);

  const data = node.data;
  const incomingEdges = edges.filter((e) => e.target === node.id);
  const outgoingEdges = edges.filter((e) => e.source === node.id);
  const matchEdges = outgoingEdges.filter((e) => e.data?.conditionBranch === "match");
  const noMatchEdges = outgoingEdges.filter((e) => e.data?.conditionBranch === "no_match");

  const setConditionData = (patch: Partial<typeof data>) => {
    const updated = useEditorStore.getState().nodes.map((n) => {
      if (n.id !== node.id) return n;
      return { ...n, data: { ...n.data, ...patch } };
    });
    useEditorStore.setState({ nodes: updated as any });
  };

  return (
    <div style={{ padding: 10, fontSize: 12 }}>
      {/* Label */}
      <div className="prop-section">
        <div className="prop-section-title">{t("condition.label")}</div>
        <input
          type="text"
          value={data.label}
          onChange={(e) => setConditionData({ label: e.target.value })}
          className="prop-input"
          style={{ width: "100%", fontSize: 12 }}
          placeholder={t("condition.labelHint")}
        />
      </div>

      {/* Climate */}
      <div className="prop-section" style={{ marginTop: 10 }}>
        <div className="prop-section-title">{t("condition.climate")}</div>
        <FloatRangeEditor
          label={t("path.tempRange")}
          value={data.condition.temperature}
          onChange={(temperature) => setConditionData({ condition: { ...data.condition, temperature } })}
        />
        <FloatRangeEditor
          label={t("path.downfallRange")}
          value={data.condition.downfall}
          onChange={(downfall) => setConditionData({ condition: { ...data.condition, downfall } })}
        />
      </div>

      {/* Connection info */}
      <div className="prop-section" style={{ marginTop: 10 }}>
        <div className="prop-section-title">{t("condition.incomingFrom")}</div>
        {incomingEdges.length === 0 ? (
          <div style={{ color: "#888", fontSize: 11 }}>—</div>
        ) : (
          incomingEdges.map((e) => (
            <div key={e.id} style={{ color: "#a5d6a7", fontSize: 10, fontFamily: "monospace" }}>
              {(e.data?.pathId ?? "").replace("ecoflux:", "")}
            </div>
          ))
        )}
      </div>

      <div className="prop-section" style={{ marginTop: 10 }}>
        <div className="prop-section-title">{t("condition.outgoingTo")}</div>
        <div style={{ marginBottom: 4 }}>
          <span style={{ color: "#4caf50", fontSize: 11 }}>{t("condition.matchBranch")}</span>
          {matchEdges.length === 0 ? (
            <div style={{ color: "#888", fontSize: 11 }}>—</div>
          ) : (
            matchEdges.map((e) => (
              <div key={e.id} style={{ color: "#a5d6a7", fontSize: 10, fontFamily: "monospace", paddingLeft: 8 }}>
                → {(e.data?.pathId ?? "").replace("ecoflux:", "")}
              </div>
            ))
          )}
        </div>
        <div>
          <span style={{ color: "#ef5350", fontSize: 11 }}>{t("condition.noMatchBranch")}</span>
          {noMatchEdges.length === 0 ? (
            <div style={{ color: "#888", fontSize: 11 }}>—</div>
          ) : (
            noMatchEdges.map((e) => (
              <div key={e.id} style={{ color: "#a5d6a7", fontSize: 10, fontFamily: "monospace", paddingLeft: 8 }}>
                → {(e.data?.pathId ?? "").replace("ecoflux:", "")}
              </div>
            ))
          )}
        </div>
      </div>

      {/* Description */}
      <div style={{ marginTop: 10, fontSize: 10, color: "#888" }}>
        <div>{t("condition.matchDesc")}</div>
        <div style={{ marginTop: 2 }}>{t("condition.noMatchDesc")}</div>
      </div>

      {/* Delete */}
      <button
        onClick={() => {
          removeNode(node.id);
          setSelectedId(null);
        }}
        style={{
          marginTop: 16,
          width: "100%",
          padding: "8px",
          background: "#c62828",
          color: "#fff",
          border: "none",
          borderRadius: 6,
          cursor: "pointer",
          fontSize: 12,
        }}
      >
        {t("condition.delete")}
      </button>
    </div>
  );
}
