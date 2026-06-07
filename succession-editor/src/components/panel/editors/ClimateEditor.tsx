import { useState } from "react";
import type { PathGraphEdge, PathEdgeData } from "../../../model/types";
import { useT } from "../../../i18n/I18nContext";

interface Props {
  edge: PathGraphEdge;
  onChange: (patch: Partial<PathEdgeData>) => void;
}

export function ClimateEditor({ edge, onChange }: Props) {
  const { t } = useT();
  const { climate } = edge.data!;
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="prop-section">
      <div
        className="prop-section-title"
        onClick={() => setCollapsed(!collapsed)}
        style={{ cursor: "pointer" }}
      >
        {collapsed ? "▶" : "▼"} {t("path.climate")}
      </div>

      {!collapsed && (
        <>
          <div style={{ marginBottom: 10 }}>
            <div style={{ fontSize: 11, color: "#aaa", marginBottom: 4 }}>{t("path.tempRange")}</div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <input
                type="number"
                value={climate.temperature.min}
                onChange={(e) =>
                  onChange({
                    climate: {
                      ...climate,
                      temperature: { ...climate.temperature, min: parseFloat(e.target.value) || 0 },
                    },
                  })
                }
                className="prop-input"
                style={{ width: 70 }}
                step={0.05}
                min={-1}
                max={2}
              />
              <span style={{ color: "#666" }}>{t("path.to")}</span>
              <input
                type="number"
                value={climate.temperature.max}
                onChange={(e) =>
                  onChange({
                    climate: {
                      ...climate,
                      temperature: { ...climate.temperature, max: parseFloat(e.target.value) || 0 },
                    },
                  })
                }
                className="prop-input"
                style={{ width: 70 }}
                step={0.05}
                min={-1}
                max={2}
              />
            </div>
            <input
              type="range"
              min={-1.0}
              max={2.0}
              step={0.05}
              value={climate.temperature.min}
              onChange={(e) =>
                onChange({
                  climate: {
                    ...climate,
                    temperature: { ...climate.temperature, min: parseFloat(e.target.value) },
                  },
                })
              }
              style={{ width: "100%", marginTop: 4 }}
            />
          </div>

          <div style={{ marginBottom: 8 }}>
            <div style={{ fontSize: 11, color: "#aaa", marginBottom: 4 }}>{t("path.downfallRange")}</div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <input
                type="number"
                value={climate.downfall.min}
                onChange={(e) =>
                  onChange({
                    climate: {
                      ...climate,
                      downfall: { ...climate.downfall, min: parseFloat(e.target.value) || 0 },
                    },
                  })
                }
                className="prop-input"
                style={{ width: 70 }}
                step={0.05}
                min={0}
                max={1}
              />
              <span style={{ color: "#666" }}>{t("path.to")}</span>
              <input
                type="number"
                value={climate.downfall.max}
                onChange={(e) =>
                  onChange({
                    climate: {
                      ...climate,
                      downfall: { ...climate.downfall, max: parseFloat(e.target.value) || 0 },
                    },
                  })
                }
                className="prop-input"
                style={{ width: 70 }}
                step={0.05}
                min={0}
                max={1}
              />
            </div>
            <input
              type="range"
              min={0}
              max={1.0}
              step={0.05}
              value={climate.downfall.min}
              onChange={(e) =>
                onChange({
                  climate: {
                    ...climate,
                    downfall: { ...climate.downfall, min: parseFloat(e.target.value) },
                  },
                })
              }
              style={{ width: "100%", marginTop: 4 }}
            />
          </div>

          {climate.temperature.min > climate.temperature.max && (
            <div style={{ color: "#ef5350", fontSize: 11 }}>{t("path.climateErrorTemp")}</div>
          )}
          {climate.downfall.min > climate.downfall.max && (
            <div style={{ color: "#ef5350", fontSize: 11 }}>{t("path.climateErrorDownfall")}</div>
          )}
        </>
      )}
    </div>
  );
}
