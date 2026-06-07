import type {
  ClimateCondition,
  ChunkRules,
  PlantSpawnRules,
  PlantDefinition,
  PathEdgeData,
} from "./types";

export function defaultFloatRange(min = 0, max = 1): { min: number; max: number } {
  return { min, max };
}

export function defaultIntRange(min = 1, max = 5): { min: number; max: number } {
  return { min, max };
}

export function defaultClimate(): ClimateCondition {
  return {
    temperature: { min: 0.6, max: 0.95 },
    downfall: { min: 0.4, max: 0.9 },
  };
}

export function defaultChunkRules(): ChunkRules {
  return {
    consuming: 5,
    maxPlantCount: 8,
    queueFillFactor: 2.0,
    evaluationIntervalDays: { min: 1, max: 1 },
    processingIntervalTicks: 100,
    evaluationIntervalTicks: 0,
    positiveProgressStep: 0.25,
    negativeProgressStep: 0.25,
  };
}

export function defaultSpawnRules(): PlantSpawnRules {
  return {
    placement: "surface",
    requireSky: true,
    maxLocalDensity: 4,
    allowedBaseBlocks: ["minecraft:grass_block", "minecraft:dirt"],
  };
}

export function defaultPlant(plantId = "minecraft:short_grass"): PlantDefinition {
  return {
    plantId,
    category: "ground_cover",
    weight: 8,
    pointValue: 1,
    maxAgeTicks: 72000,
    spawnRules: defaultSpawnRules(),
  };
}

export function defaultEdgeData(
  sourceBiome: string,
  targetBiome: string,
): PathEdgeData {
  const srcName = sourceBiome.replace("minecraft:", "");
  const tgtName = targetBiome.replace("minecraft:", "");
  return {
    pathId: `ecoflux:${srcName}_to_${tgtName}`,
    priority: 10,
    climate: defaultClimate(),
    chunkRules: defaultChunkRules(),
    plants: [defaultPlant()],
  };
}
