import type { BiomeEntry } from "./types";

// Minecraft 1.21.1 biome list with default climate values
// Temperature and downfall from vanilla biome data
export const BIOME_LIST: BiomeEntry[] = [
  // ===== Overworld =====
  { biomeId: "minecraft:plains",            displayName: "Plains",              defaultTemp: 0.8,  defaultDownfall: 0.4,  category: "plains",            dimension: "overworld" },
  { biomeId: "minecraft:sunflower_plains",   displayName: "Sunflower Plains",    defaultTemp: 0.8,  defaultDownfall: 0.4,  category: "plains",            dimension: "overworld" },
  { biomeId: "minecraft:forest",             displayName: "Forest",              defaultTemp: 0.7,  defaultDownfall: 0.8,  category: "forest",            dimension: "overworld" },
  { biomeId: "minecraft:flower_forest",      displayName: "Flower Forest",       defaultTemp: 0.7,  defaultDownfall: 0.8,  category: "forest",            dimension: "overworld" },
  { biomeId: "minecraft:birch_forest",       displayName: "Birch Forest",        defaultTemp: 0.6,  defaultDownfall: 0.6,  category: "forest",            dimension: "overworld" },
  { biomeId: "minecraft:old_growth_birch_forest", displayName: "Old Growth Birch Forest", defaultTemp: 0.6, defaultDownfall: 0.6, category: "forest",    dimension: "overworld" },
  { biomeId: "minecraft:dark_forest",        displayName: "Dark Forest",         defaultTemp: 0.7,  defaultDownfall: 0.8,  category: "forest",            dimension: "overworld" },
  { biomeId: "minecraft:grove",              displayName: "Grove",               defaultTemp: -0.2, defaultDownfall: 0.8,  category: "forest",            dimension: "overworld" },
  { biomeId: "minecraft:taiga",              displayName: "Taiga",               defaultTemp: 0.25, defaultDownfall: 0.8,  category: "taiga",             dimension: "overworld" },
  { biomeId: "minecraft:snowy_taiga",        displayName: "Snowy Taiga",         defaultTemp: -0.5, defaultDownfall: 0.4,  category: "taiga",             dimension: "overworld" },
  { biomeId: "minecraft:old_growth_pine_taiga", displayName: "Old Growth Pine Taiga", defaultTemp: 0.3, defaultDownfall: 0.8, category: "taiga",       dimension: "overworld" },
  { biomeId: "minecraft:old_growth_spruce_taiga", displayName: "Old Growth Spruce Taiga", defaultTemp: 0.25, defaultDownfall: 0.8, category: "taiga", dimension: "overworld" },
  { biomeId: "minecraft:swamp",              displayName: "Swamp",               defaultTemp: 0.8,  defaultDownfall: 0.9,  category: "swamp",             dimension: "overworld" },
  { biomeId: "minecraft:mangrove_swamp",     displayName: "Mangrove Swamp",      defaultTemp: 0.8,  defaultDownfall: 0.9,  category: "swamp",             dimension: "overworld" },
  { biomeId: "minecraft:jungle",             displayName: "Jungle",              defaultTemp: 0.95, defaultDownfall: 0.9,  category: "jungle",            dimension: "overworld" },
  { biomeId: "minecraft:sparse_jungle",      displayName: "Sparse Jungle",       defaultTemp: 0.95, defaultDownfall: 0.8,  category: "jungle",            dimension: "overworld" },
  { biomeId: "minecraft:bamboo_jungle",      displayName: "Bamboo Jungle",       defaultTemp: 0.95, defaultDownfall: 0.9,  category: "jungle",            dimension: "overworld" },
  { biomeId: "minecraft:desert",             displayName: "Desert",              defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "desert",            dimension: "overworld" },
  { biomeId: "minecraft:badlands",           displayName: "Badlands",            defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "mesa",              dimension: "overworld" },
  { biomeId: "minecraft:wooded_badlands",    displayName: "Wooded Badlands",     defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "mesa",              dimension: "overworld" },
  { biomeId: "minecraft:eroded_badlands",    displayName: "Eroded Badlands",     defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "mesa",              dimension: "overworld" },
  { biomeId: "minecraft:savanna",            displayName: "Savanna",             defaultTemp: 1.2,  defaultDownfall: 0.0,  category: "savanna",           dimension: "overworld" },
  { biomeId: "minecraft:savanna_plateau",    displayName: "Savanna Plateau",     defaultTemp: 1.0,  defaultDownfall: 0.0,  category: "savanna",           dimension: "overworld" },
  { biomeId: "minecraft:windswept_hills",    displayName: "Windswept Hills",     defaultTemp: 0.2,  defaultDownfall: 0.3,  category: "extreme_hills",     dimension: "overworld" },
  { biomeId: "minecraft:windswept_forest",   displayName: "Windswept Forest",    defaultTemp: 0.2,  defaultDownfall: 0.3,  category: "extreme_hills",     dimension: "overworld" },
  { biomeId: "minecraft:windswept_gravelly_hills", displayName: "Windswept Gravelly Hills", defaultTemp: 0.2, defaultDownfall: 0.3, category: "extreme_hills", dimension: "overworld" },
  { biomeId: "minecraft:windswept_savanna",  displayName: "Windswept Savanna",   defaultTemp: 0.5,  defaultDownfall: 0.9,  category: "savanna",           dimension: "overworld" },
  { biomeId: "minecraft:meadow",             displayName: "Meadow",              defaultTemp: 0.5,  defaultDownfall: 0.8,  category: "mountain",          dimension: "overworld" },
  { biomeId: "minecraft:cherry_grove",       displayName: "Cherry Grove",        defaultTemp: 0.5,  defaultDownfall: 0.8,  category: "mountain",          dimension: "overworld" },
  { biomeId: "minecraft:beach",              displayName: "Beach",               defaultTemp: 0.8,  defaultDownfall: 0.4,  category: "beach",             dimension: "overworld" },
  { biomeId: "minecraft:snowy_beach",        displayName: "Snowy Beach",         defaultTemp: 0.05, defaultDownfall: 0.3,  category: "beach",             dimension: "overworld" },
  { biomeId: "minecraft:stony_shore",        displayName: "Stony Shore",         defaultTemp: 0.2,  defaultDownfall: 0.3,  category: "beach",             dimension: "overworld" },
  { biomeId: "minecraft:mushroom_fields",    displayName: "Mushroom Fields",     defaultTemp: 0.9,  defaultDownfall: 1.0,  category: "mushroom",          dimension: "overworld" },
  { biomeId: "minecraft:river",              displayName: "River",               defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "river",             dimension: "overworld" },
  { biomeId: "minecraft:frozen_river",       displayName: "Frozen River",        defaultTemp: 0.0,  defaultDownfall: 0.5,  category: "river",             dimension: "overworld" },
  { biomeId: "minecraft:ocean",              displayName: "Ocean",               defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:deep_ocean",         displayName: "Deep Ocean",          defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:warm_ocean",         displayName: "Warm Ocean",          defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:lukewarm_ocean",     displayName: "Lukewarm Ocean",      defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:cold_ocean",         displayName: "Cold Ocean",          defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:frozen_ocean",       displayName: "Frozen Ocean",        defaultTemp: 0.0,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:deep_lukewarm_ocean", displayName: "Deep Lukewarm Ocean", defaultTemp: 0.5, defaultDownfall: 0.5, category: "ocean",          dimension: "overworld" },
  { biomeId: "minecraft:deep_cold_ocean",    displayName: "Deep Cold Ocean",     defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:deep_frozen_ocean",  displayName: "Deep Frozen Ocean",   defaultTemp: 0.0,  defaultDownfall: 0.5,  category: "ocean",             dimension: "overworld" },
  { biomeId: "minecraft:ice_spikes",         displayName: "Ice Spikes",          defaultTemp: -0.5, defaultDownfall: 0.4,  category: "icy",               dimension: "overworld" },
  { biomeId: "minecraft:snowy_plains",       displayName: "Snowy Plains",        defaultTemp: 0.0,  defaultDownfall: 0.5,  category: "icy",               dimension: "overworld" },
  { biomeId: "minecraft:frozen_peaks",       displayName: "Frozen Peaks",        defaultTemp: -0.7, defaultDownfall: 0.9,  category: "mountain",          dimension: "overworld" },
  { biomeId: "minecraft:jagged_peaks",       displayName: "Jagged Peaks",        defaultTemp: -0.7, defaultDownfall: 0.9,  category: "mountain",          dimension: "overworld" },
  { biomeId: "minecraft:snowy_slopes",       displayName: "Snowy Slopes",        defaultTemp: -0.3, defaultDownfall: 0.9,  category: "mountain",          dimension: "overworld" },
  { biomeId: "minecraft:stony_peaks",        displayName: "Stony Peaks",         defaultTemp: 1.0,  defaultDownfall: 0.3,  category: "mountain",          dimension: "overworld" },
  { biomeId: "minecraft:dripstone_caves",    displayName: "Dripstone Caves",     defaultTemp: 0.2,  defaultDownfall: 0.0,  category: "underground",       dimension: "overworld" },
  { biomeId: "minecraft:lush_caves",         displayName: "Lush Caves",          defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "underground",       dimension: "overworld" },
  { biomeId: "minecraft:deep_dark",          displayName: "Deep Dark",           defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "underground",       dimension: "overworld" },

  // ===== Nether =====
  { biomeId: "minecraft:nether_wastes",      displayName: "Nether Wastes",       defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "nether",            dimension: "nether" },
  { biomeId: "minecraft:crimson_forest",     displayName: "Crimson Forest",      defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "nether",            dimension: "nether" },
  { biomeId: "minecraft:warped_forest",      displayName: "Warped Forest",       defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "nether",            dimension: "nether" },
  { biomeId: "minecraft:soul_sand_valley",   displayName: "Soul Sand Valley",    defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "nether",            dimension: "nether" },
  { biomeId: "minecraft:basalt_deltas",      displayName: "Basalt Deltas",       defaultTemp: 2.0,  defaultDownfall: 0.0,  category: "nether",            dimension: "nether" },

  // ===== End =====
  { biomeId: "minecraft:the_end",            displayName: "The End",              defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "end",               dimension: "end" },
  { biomeId: "minecraft:end_highlands",      displayName: "End Highlands",        defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "end",               dimension: "end" },
  { biomeId: "minecraft:end_midlands",       displayName: "End Midlands",         defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "end",               dimension: "end" },
  { biomeId: "minecraft:end_barrens",        displayName: "End Barrens",          defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "end",               dimension: "end" },
  { biomeId: "minecraft:small_end_islands",  displayName: "Small End Islands",    defaultTemp: 0.5,  defaultDownfall: 0.5,  category: "end",               dimension: "end" },
];

export function getBiomeMeta(biomeId: string): BiomeEntry | undefined {
  return BIOME_LIST.find((b) => b.biomeId === biomeId);
}

export function getBiomesByDimension(dimension: BiomeEntry["dimension"]): BiomeEntry[] {
  return BIOME_LIST.filter((b) => b.dimension === dimension);
}

export const DIMENSION_LABELS: Record<BiomeEntry["dimension"], string> = {
  overworld: "Overworld",
  nether: "Nether",
  end: "The End",
};
