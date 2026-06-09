import type { GraphNode, PathGraphEdge, SuccessionPath } from "../model/types";

export function exportPaths(
  nodes: GraphNode[],
  edges: PathGraphEdge[],
): SuccessionPath[] {
  const paths: SuccessionPath[] = [];

  edges.forEach((edge) => {
    const sourceNode = nodes.find((n) => n.id === edge.source);
    const targetNode = nodes.find((n) => n.id === edge.target);
    if (!sourceNode || !targetNode) return;
    if (sourceNode.data.type !== "biome" || targetNode.data.type !== "biome") return;

    const srcData = sourceNode.data;
    const tgtData = targetNode.data;
    const d = edge.data!;

    const path: SuccessionPath = {
      schemaVersion: 1,
      pathId: d.pathId,
      priority: d.priority,
      sourceBiomes: [srcData.biomeId],
      targetBiome: tgtData.biomeId,
      fallbackBiome: srcData.biomeId,
      climate: {
        temperature: { ...d.climate.temperature },
        downfall: { ...d.climate.downfall },
      },
      chunkRules: {
        consuming: d.chunkRules.consuming,
        maxPlantCount: d.chunkRules.maxPlantCount,
        queueFillFactor: d.chunkRules.queueFillFactor,
        evaluationIntervalDays: {
          min: d.chunkRules.evaluationIntervalDays.min,
          max: d.chunkRules.evaluationIntervalDays.max,
        },
        processingIntervalTicks: d.chunkRules.processingIntervalTicks,
        evaluationIntervalTicks: d.chunkRules.evaluationIntervalTicks,
        positiveProgressStep: d.chunkRules.positiveProgressStep,
        negativeProgressStep: d.chunkRules.negativeProgressStep,
      },
      plants: d.plants.map((p) => ({
        plantId: p.plantId,
        category: p.category,
        weight: p.weight,
        pointValue: p.pointValue,
        maxAgeTicks: p.maxAgeTicks,
        spawnRules: {
          placement: p.spawnRules.placement,
          requireSky: p.spawnRules.requireSky,
          maxLocalDensity: p.spawnRules.maxLocalDensity,
          allowedBaseBlocks: [...p.spawnRules.allowedBaseBlocks],
        },
      })),
    };

    paths.push(path);
  });

  return paths;
}

export function downloadJson(paths: SuccessionPath[]): void {
  const json = JSON.stringify(paths, null, 2);
  const blob = new Blob([json], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "succession_paths_export.json";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
