import { useEditorStore } from "../../store/editorStore";

interface Props {
  nodeCount: number;
  edgeCount: number;
}

export function NoSelection({ nodeCount, edgeCount }: Props) {
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  const sourceBiomes = new Set(edges.map((e) => nodes.find((n) => n.id === e.source)?.data.biomeId).filter(Boolean));
  const targetBiomes = new Set(edges.map((e) => nodes.find((n) => n.id === e.target)?.data.biomeId).filter(Boolean));

  const totalPlants = edges.reduce((sum, e) => sum + (e.data?.plants.length ?? 0), 0);
  const uniquePlants = new Set(
    edges.flatMap((e) => (e.data?.plants ?? []).map((p) => p.plantId)),
  );

  return (
    <div style={{ padding: 16 }}>
      <div style={{ marginBottom: 20 }}>
        <h3 style={{ margin: "0 0 12px 0", color: "#ccc", fontSize: 15 }}>Graph Overview</h3>
        <div className="stat-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <StatBox label="Biome Nodes" value={nodeCount} />
          <StatBox label="Paths (Edges)" value={edgeCount} />
          <StatBox label="Source Biomes" value={sourceBiomes.size} />
          <StatBox label="Target Biomes" value={targetBiomes.size} />
          <StatBox label="Total Plants" value={totalPlants} />
          <StatBox label="Unique Plant Types" value={uniquePlants.size} />
        </div>
      </div>

      {nodes.length > 0 && (
        <div style={{ marginBottom: 20 }}>
          <h3 style={{ margin: "0 0 8px 0", color: "#ccc", fontSize: 15 }}>Biomes on Canvas</h3>
          <div style={{ maxHeight: 200, overflowY: "auto" }}>
            {nodes.map((node) => {
              const connEdges = edges.filter(
                (e) => e.source === node.id || e.target === node.id,
              );
              const hasOut = connEdges.some((e) => e.source === node.id);
              const hasIn = connEdges.some((e) => e.target === node.id);
              const meta = node.data.biomeMeta;
              return (
                <div
                  key={node.id}
                  style={{
                    padding: "4px 8px",
                    fontSize: 12,
                    color: "#aaa",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    borderBottom: "1px solid #2a2a3e",
                  }}
                >
                  <span>
                    {meta?.displayName ?? node.data.biomeId.replace("minecraft:", "")}
                    {meta && (
                      <span style={{ color: "#777", marginLeft: 6 }}>
                        🌡{meta.defaultTemp.toFixed(1)}
                      </span>
                    )}
                  </span>
                  <span style={{ fontSize: 10, color: "#666" }}>
                    {hasOut ? "→" : ""} {hasIn ? "←" : ""}
                    {!hasOut && !hasIn ? "isolated" : ""}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {edges.length > 0 && (
        <div>
          <h3 style={{ margin: "0 0 8px 0", color: "#ccc", fontSize: 15 }}>Paths</h3>
          <div style={{ maxHeight: 200, overflowY: "auto" }}>
            {edges.map((edge) => {
              const srcNode = nodes.find((n) => n.id === edge.source);
              const tgtNode = nodes.find((n) => n.id === edge.target);
              return (
                <div
                  key={edge.id}
                  style={{
                    padding: "4px 8px",
                    fontSize: 11,
                    color: "#aaa",
                    fontFamily: "monospace",
                    borderBottom: "1px solid #2a2a3e",
                  }}
                >
                  {srcNode?.data.biomeId.replace("minecraft:", "") ?? "?"} →{" "}
                  {tgtNode?.data.biomeId.replace("minecraft:", "") ?? "?"}
                  <span style={{ color: "#666", marginLeft: 6 }}>
                    ({edge.data?.plants.length ?? 0} plants)
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {nodes.length === 0 && (
        <div style={{ color: "#666", fontSize: 13, textAlign: "center", marginTop: 40 }}>
          Add biomes from the left palette and connect them to create succession paths.
        </div>
      )}
    </div>
  );
}

function StatBox({ label, value }: { label: string; value: number }) {
  return (
    <div
      style={{
        background: "#252540",
        borderRadius: 8,
        padding: "10px 12px",
        textAlign: "center",
      }}
    >
      <div style={{ fontSize: 22, fontWeight: "bold", color: "#fff" }}>{value}</div>
      <div style={{ fontSize: 10, color: "#888", marginTop: 2 }}>{label}</div>
    </div>
  );
}
