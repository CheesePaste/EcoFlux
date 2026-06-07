import { useState } from "react";
import type { PathGraphEdge, PathEdgeData } from "../../../model/types";

interface Props {
  edge: PathGraphEdge;
  onChange: (patch: Partial<PathEdgeData>) => void;
}

export function ChunkRulesEditor({ edge, onChange }: Props) {
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
        {collapsed ? "▶" : "▼"} Chunk Rules
      </div>

      {!collapsed && (
        <>
          <div className="prop-row">
            <label>consuming</label>
            <input
              type="number"
              value={cr.consuming}
              onChange={(e) => setField("consuming", parseInt(e.target.value) || 0)}
              className="prop-input"
              style={{ width: 80 }}
              min={0}
            />
            <span style={{ fontSize: 10, color: "#666" }}>维持消耗</span>
          </div>

          <div className="prop-row">
            <label>max_plant_count</label>
            <input
              type="number"
              value={cr.maxPlantCount}
              onChange={(e) => setField("maxPlantCount", parseInt(e.target.value) || 1)}
              className="prop-input"
              style={{ width: 80 }}
              min={1}
            />
            <span style={{ fontSize: 10, color: "#666" }}>植物容量</span>
          </div>

          <div className="prop-row">
            <label>queue_fill_factor</label>
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
              queue: {Math.ceil(cr.maxPlantCount * cr.queueFillFactor)}
            </span>
          </div>

          <div style={{ marginTop: 4, fontSize: 11, color: "#aaa" }}>Evaluation Interval (days)</div>
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
            <span style={{ color: "#666" }}>to</span>
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
            <span style={{ fontSize: 10, color: "#666" }}>days</span>
          </div>

          <div className="prop-row">
            <label>processing_interval_ticks</label>
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
            <label>evaluation_interval_ticks</label>
            <input
              type="number"
              value={cr.evaluationIntervalTicks}
              onChange={(e) => setField("evaluationIntervalTicks", parseInt(e.target.value) || 0)}
              className="prop-input"
              style={{ width: 80 }}
              min={0}
            />
            <span style={{ fontSize: 9, color: "#666" }}>0=use days</span>
          </div>

          <div className="prop-row">
            <label>positive_progress_step</label>
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
            <label>negative_progress_step</label>
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
