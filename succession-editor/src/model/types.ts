// ===== Ecoflux Config Types =====
// Mirrors the Java records in com.s.ecoflux.config

export interface FloatRange {
  min: number;
  max: number;
}

export interface IntRange {
  min: number;
  max: number;
}

export interface ClimateCondition {
  temperature: FloatRange;
  downfall: FloatRange;
}

export interface ChunkRules {
  consuming: number;
  maxPlantCount: number;
  queueFillFactor: number;
  evaluationIntervalDays: IntRange;
  processingIntervalTicks: number;
  evaluationIntervalTicks: number;
  positiveProgressStep: number;
  negativeProgressStep: number;
}

export interface PlantSpawnRules {
  placement: string;
  requireSky: boolean;
  maxLocalDensity: number;
  allowedBaseBlocks: string[];
}

export interface PlantDefinition {
  plantId: string;
  category: string;
  weight: number;
  pointValue: number;
  maxAgeTicks: number;
  spawnRules: PlantSpawnRules;
}

export interface SuccessionPath {
  schemaVersion: 1;
  pathId: string;
  priority: number;
  sourceBiomes: string[];
  targetBiome: string;
  fallbackBiome: string | null;
  climate: ClimateCondition;
  chunkRules: ChunkRules;
  plants: PlantDefinition[];
}

// ===== Graph Model (ReactFlow) =====

export interface BiomeMeta {
  defaultTemp: number;
  defaultDownfall: number;
  category: string;
  displayName: string;
}

// Use Record<string, unknown> as base so ReactFlow types are satisfied
export interface BiomeNodeData extends Record<string, unknown> {
  type: "biome";
  biomeId: string;
  biomeMeta?: BiomeMeta;
}

export interface PathEdgeData extends Record<string, unknown> {
  pathId: string;
  priority: number;
  climate: ClimateCondition;
  chunkRules: ChunkRules;
  plants: PlantDefinition[];
  conditionBranch?: "match" | "no_match";
  parentConditionId?: string;
  sourceBiome?: string;
  targetBiome?: string;
}

// ===== Condition Node =====

export interface ConditionNodeData extends Record<string, unknown> {
  type: "condition";
  label: string;
  condition: ClimateCondition;
}

export type ConditionGraphNode = Node<ConditionNodeData, "condition">;

// ReactFlow node/edge types
import type { Node, Edge } from "@xyflow/react";
export type BiomeGraphNode = Node<BiomeNodeData, "biome">;
export type PathGraphEdge = Edge<PathEdgeData, "succession">;
export type GraphNode = BiomeGraphNode | ConditionGraphNode;

// ===== Biome Palette =====
export interface BiomeEntry {
  biomeId: string;
  displayName: string;
  defaultTemp: number;
  defaultDownfall: number;
  category: string;
  dimension: "overworld" | "nether" | "end";
}

// ===== Validation =====
export interface ValidationError {
  type: "error" | "warning";
  targetId: string;
  targetType: "node" | "edge";
  field?: string;
  message: string;
}
