import { useState } from "react";
import type { PathGraphEdge, PathEdgeData } from "../../../model/types";
import { useT } from "../../../i18n/I18nContext";

interface Props {
  edge: PathGraphEdge;
  onChange: (patch: Partial<PathEdgeData>) => void;
}

export function ChunkRulesEditor({ edge, onChange }: Props) {
  const { t } = useT();
  const { chunkRules: cr } = edge.data!;
  const [collapsed, setCollapsed] = useState(false);

  const setField = (field: string, value: number) => {
    onChange({ chunkRules: { ...cr, [field]: value } });
  };

  return (
    <div className="prop-section">
      <div
        className="prop-section-title"
        onClick={() => setCollapsed(!collapsed)}
        style={{ cursor: "pointer" }}
      >
        {collapsed ? "▶" : "▼"} {t("path.chunkRules")}
      </div>

      {!collapsed && (
        <>
          <div className="prop-row">
            <label>{t("path.consuming")}</label>
            <input
              type="number"
              value={cr.consuming}
              onChange={(e) => setField("consuming", parseInt(e.target.value) || 0)}
              className="prop-input"
              style={{ width: 80 }}
              min={0}
            />
            <span style={{ fontSize: 10, color: "#666" }}>{t("path.consumingHint")}</span>
          </div>

          <div className="prop-row">
            <label>{t("path.maxPlantCount")}</label>
            <input
              type="number"
              value={cr.maxPlantCount}
              onChange={(e) => setField("maxPlantCount", parseInt(e.target.value) || 1)}
              className="prop-input"
              style={{ width: 80 }}
              min={1}
            />
            <span style={{ fontSize: 10, color: "#666" }}>{t("path.maxPlantCountHint")}</span>
          </div>

          <div className="prop-row">
            <label>{t("path.queueFillFactor")}</label>
            <input
              type="number"
              value={cr.queueFillFactor}
              onChange={(e) => setField("queueFillFactor", parseFloat(e.target.value) || 1.0)}
              className="prop-input"
              style={{ width: 80 }}
              min={1.0}
              step={0.5}
            />
            <span style={{ fontSize: 10, color: "#666" }}>
              {t("path.queueHint").replace("{size}", String(Math.ceil(cr.maxPlantCount * cr.queueFillFactor)))}
            </span>
          </div>

          <div style={{ marginTop: 4, fontSize: 11, color: "#aaa" }}>{t("path.evalInterval")}</div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 4 }}>
            <input
              type="number"
              value={cr.evaluationIntervalDays.min}
              onChange={(e) =>
                onChange({
                  chunkRules: {
                    ...cr,
                    evaluationIntervalDays: {
                      ...cr.evaluationIntervalDays,
                      min: parseInt(e.target.value) || 0,
                    },
                  },
                })
              }
              className="prop-input"
              style={{ width: 70 }}
              min={0}
            />
            <span style={{ color: "#666" }}>{t("path.to")}</span>
            <input
              type="number"
              value={cr.evaluationIntervalDays.max}
              onChange={(e) =>
                onChange({
                  chunkRules: {
                    ...cr,
                    evaluationIntervalDays: {
                      ...cr.evaluationIntervalDays,
                      max: parseInt(e.target.value) || 0,
                    },
                  },
                })
              }
              className="prop-input"
              style={{ width: 70 }}
              min={0}
            />
            <span style={{ fontSize: 10, color: "#666" }}>{t("path.days")}</span>
          </div>

          <div className="prop-row">
            <label>{t("path.processingInterval")}</label>
            <input
              type="number"
              value={cr.processingIntervalTicks}
              onChange={(e) => setField("processingIntervalTicks", parseInt(e.target.value) || 20)}
              className="prop-input"
              style={{ width: 80 }}
              min={1}
            />
          </div>

          <div className="prop-row">
            <label>{t("path.evalIntervalTicks")}</label>
            <input
              type="number"
              value={cr.evaluationIntervalTicks}
              onChange={(e) => setField("evaluationIntervalTicks", parseInt(e.target.value) || 0)}
              className="prop-input"
              style={{ width: 80 }}
              min={0}
            />
            <span style={{ fontSize: 9, color: "#666" }}>{t("path.evalTicksHint")}</span>
          </div>

          <div className="prop-row">
            <label>{t("path.positiveStep")}</label>
            <input
              type="number"
              value={cr.positiveProgressStep}
              onChange={(e) => setField("positiveProgressStep", parseFloat(e.target.value) || 0.1)}
              className="prop-input"
              style={{ width: 80 }}
              min={0.05}
              step={0.05}
            />
          </div>

          <div className="prop-row">
            <label>{t("path.negativeStep")}</label>
            <input
              type="number"
              value={cr.negativeProgressStep}
              onChange={(e) => setField("negativeProgressStep", parseFloat(e.target.value) || 0.1)}
              className="prop-input"
              style={{ width: 80 }}
              min={0.05}
              step={0.05}
            />
          </div>
        </>
      )}
    </div>
  );
}
