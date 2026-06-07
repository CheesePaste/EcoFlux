import { create } from "zustand";
import type { BiomeGraphNode, PathGraphEdge, PathEdgeData, ValidationError, PlantDefinition } from "../model/types";
import { getBiomeMeta } from "../model/biomeData";
import { defaultEdgeData, defaultPlant } from "../model/defaults";

let nextNodeId = 0;
let nextEdgeId = 0;

function genNodeId(): string {
  return `node_${++nextNodeId}`;
}

function genEdgeId(): string {
  return `edge_${++nextEdgeId}`;
}

export function resetIdCounters(): void {
  nextNodeId = 0;
  nextEdgeId = 0;
}

interface HistoryEntry {
  nodes: BiomeGraphNode[];
  edges: PathGraphEdge[];
}

interface EditorState {
  nodes: BiomeGraphNode[];
  edges: PathGraphEdge[];
  selectedId: string | null;
  validationErrors: ValidationError[];

  undoStack: HistoryEntry[];
  redoStack: HistoryEntry[];
  undo: () => void;
  redo: () => void;
  pushHistory: () => void;

  addBiomeNode: (biomeId: string) => string;
  removeNode: (nodeId: string) => void;

  addEdge: (sourceId: string, targetId: string) => string | null;
  removeEdge: (edgeId: string) => void;
  updateEdgeData: (edgeId: string, patch: Partial<PathEdgeData>) => void;

  setSelectedId: (id: string | null) => void;

  addPlant: (edgeId: string) => void;
  removePlant: (edgeId: string, plantIndex: number) => void;
  updatePlant: (edgeId: string, plantIndex: number, patch: Partial<PlantDefinition>) => void;

  loadGraph: (nodes: BiomeGraphNode[], edges: PathGraphEdge[]) => void;
  clearGraph: () => void;

  validate: () => boolean;
  clearValidation: () => void;

  getSelectedEdge: () => PathGraphEdge | undefined;
  getConnectedEdges: (nodeId: string) => PathGraphEdge[];
}

export const useEditorStore = create<EditorState>()((set, get) => ({
  nodes: [],
  edges: [],
  selectedId: null,
  validationErrors: [],
  undoStack: [],
  redoStack: [],

  pushHistory() {
    const { nodes, edges, undoStack } = get();
    set({
      undoStack: [...undoStack.slice(-49), { nodes, edges }],
      redoStack: [],
    });
  },

  undo() {
    const { undoStack, nodes, edges } = get();
    if (undoStack.length === 0) return;
    const prev = undoStack[undoStack.length - 1];
    set({
      undoStack: undoStack.slice(0, -1),
      redoStack: [...get().redoStack, { nodes, edges }],
      nodes: prev.nodes,
      edges: prev.edges,
      selectedId: null,
    });
  },

  redo() {
    const { redoStack, nodes, edges } = get();
    if (redoStack.length === 0) return;
    const next = redoStack[redoStack.length - 1];
    set({
      redoStack: redoStack.slice(0, -1),
      undoStack: [...get().undoStack, { nodes, edges }],
      nodes: next.nodes,
      edges: next.edges,
      selectedId: null,
    });
  },

  addBiomeNode(biomeId) {
    get().pushHistory();
    const id = genNodeId();
    const meta = getBiomeMeta(biomeId);
    const node: BiomeGraphNode = {
      id,
      type: "biome",
      position: { x: 100 + Math.random() * 300, y: 100 + Math.random() * 300 },
      data: {
        type: "biome",
        biomeId,
        biomeMeta: meta
          ? {
              defaultTemp: meta.defaultTemp,
              defaultDownfall: meta.defaultDownfall,
              category: meta.category,
              displayName: meta.displayName,
            }
          : undefined,
      },
    };
    set({ nodes: [...get().nodes, node] });
    return id;
  },

  removeNode(nodeId) {
    get().pushHistory();
    set({
      nodes: get().nodes.filter((n) => n.id !== nodeId),
      edges: get().edges.filter((e) => e.source !== nodeId && e.target !== nodeId),
      selectedId: get().selectedId === nodeId ? null : get().selectedId,
    });
  },

  addEdge(sourceId, targetId) {
    const existing = get().edges.find(
      (e) => e.source === sourceId && e.target === targetId,
    );
    if (existing) return null;

    const sourceNode = get().nodes.find((n) => n.id === sourceId);
    const targetNode = get().nodes.find((n) => n.id === targetId);
    if (!sourceNode || !targetNode) return null;

    get().pushHistory();
    const id = genEdgeId();
    const data = defaultEdgeData(sourceNode.data.biomeId, targetNode.data.biomeId);
    const edge: PathGraphEdge = {
      id,
      type: "succession",
      source: sourceId,
      target: targetId,
      data,
    };
    set({ edges: [...get().edges, edge] });
    return id;
  },

  removeEdge(edgeId) {
    get().pushHistory();
    set({
      edges: get().edges.filter((e) => e.id !== edgeId),
      selectedId: get().selectedId === edgeId ? null : get().selectedId,
    });
  },

  updateEdgeData(edgeId, patch) {
    const updated = get().edges.map((e) => {
      if (e.id !== edgeId) return e;
      return { ...e, data: { ...e.data, ...patch } } as PathGraphEdge;
    });
    set({ edges: updated });
  },

  setSelectedId(id) {
    set({ selectedId: id });
  },

  addPlant(edgeId) {
    const updated = get().edges.map((e) => {
      if (e.id !== edgeId) return e;
      return {
        ...e,
        data: { ...e.data!, plants: [...e.data!.plants, defaultPlant()] },
      } as PathGraphEdge;
    });
    set({ edges: updated });
  },

  removePlant(edgeId, plantIndex) {
    const updated = get().edges.map((e) => {
      if (e.id !== edgeId) return e;
      return {
        ...e,
        data: {
          ...e.data!,
          plants: e.data!.plants.filter((_, i) => i !== plantIndex),
        },
      } as PathGraphEdge;
    });
    set({ edges: updated });
  },

  updatePlant(edgeId, plantIndex, patch) {
    const updated = get().edges.map((e) => {
      if (e.id !== edgeId) return e;
      return {
        ...e,
        data: {
          ...e.data!,
          plants: e.data!.plants.map((p, i) =>
            i === plantIndex ? { ...p, ...patch } : p,
          ),
        },
      } as PathGraphEdge;
    });
    set({ edges: updated });
  },

  loadGraph(nodes, edges) {
    nodes.forEach((n) => {
      const num = parseInt(n.id.replace("node_", ""), 10);
      if (!isNaN(num) && num >= nextNodeId) nextNodeId = num + 1;
    });
    edges.forEach((e) => {
      const num = parseInt(e.id.replace("edge_", ""), 10);
      if (!isNaN(num) && num >= nextEdgeId) nextEdgeId = num + 1;
    });
    set({ nodes, edges, selectedId: null, validationErrors: [], undoStack: [], redoStack: [] });
  },

  clearGraph() {
    get().pushHistory();
    set({ nodes: [], edges: [], selectedId: null, validationErrors: [] });
  },

  validate() {
    const errors: ValidationError[] = [];
    const { nodes, edges } = get();
    const pathIds = new Set<string>();

    for (const edge of edges) {
      const d = edge.data;
      if (!d) continue;

      if (pathIds.has(d.pathId)) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "pathId", message: `Duplicate path_id: ${d.pathId}`,
        });
      }
      pathIds.add(d.pathId);

      if (!d.pathId || d.pathId.trim() === "") {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "pathId", message: "path_id is required",
        });
      }
      if (d.priority < 0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "priority", message: "priority must be >= 0",
        });
      }
      if (d.climate.temperature.min > d.climate.temperature.max) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "climate.temperature", message: "temperature min > max",
        });
      }
      if (d.climate.downfall.min > d.climate.downfall.max) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "climate.downfall", message: "downfall min > max",
        });
      }
      if (d.chunkRules.consuming < 0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "chunkRules.consuming", message: "consuming must be >= 0",
        });
      }
      if (d.chunkRules.maxPlantCount <= 0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "chunkRules.maxPlantCount", message: "maxPlantCount must be > 0",
        });
      }
      if (d.chunkRules.queueFillFactor < 1.0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "chunkRules.queueFillFactor", message: "queueFillFactor must be >= 1.0",
        });
      }
      if (d.plants.length === 0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "plants", message: "At least one plant is required",
        });
      }
      d.plants.forEach((plant, idx) => {
        if (!plant.plantId || plant.plantId.trim() === "") {
          errors.push({
            type: "error", targetId: edge.id, targetType: "edge",
            field: `plants[${idx}].plantId`,
            message: `Plant #${idx + 1}: plant_id is required`,
          });
        }
        if (plant.weight <= 0) {
          errors.push({
            type: "error", targetId: edge.id, targetType: "edge",
            field: `plants[${idx}].weight`,
            message: `Plant #${idx + 1}: weight must be > 0`,
          });
        }
      });
    }

    for (const node of nodes) {
      const hasEdge = edges.some((e) => e.source === node.id || e.target === node.id);
      if (!hasEdge) {
        errors.push({
          type: "warning", targetId: node.id, targetType: "node",
          message: `Isolated biome: ${node.data.biomeId}`,
        });
      }
    }

    set({ validationErrors: errors });
    return errors.filter((e) => e.type === "error").length === 0;
  },

  clearValidation() {
    set({ validationErrors: [] });
  },

  getSelectedEdge() {
    const { selectedId, edges } = get();
    if (!selectedId) return undefined;
    return edges.find((e) => e.id === selectedId);
  },

  getConnectedEdges(nodeId) {
    return get().edges.filter((e) => e.source === nodeId || e.target === nodeId);
  },
}));
