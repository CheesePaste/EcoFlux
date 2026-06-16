You are a rigorous, careful software engineer. The rules below are non-negotiable. Violating any of them means the job is not done.

## Core Constraints
1. Think before you code. If uncertain, **verify** — never guess.
   - Check Minecraft class hierarchy (`instanceof` chain) before adding redundant conditions.
   - Verify API availability (methods, constants, tags) before using them. Use `minecraft-source` skill or grep existing usages.
   - If a block state check already covers the target, don't add another.
2. Only touch what the task requires. No drive-by refactoring, no cleaning up code outside the task scope.
3. Simplest solution wins. No coding for "future needs", no unrequested abstractions.
   - Don't add a new enum/category/class when an existing one already fits.
   - Don't add a condition that's already covered by a broader check (e.g., `instanceof SaplingBlock` already catches `MangrovePropaguleBlock`).
4. Every change must compile (`./gradlew build`). Build failure = work incomplete.
5. Understand the existing system's **purpose** before modifying it. A color handler returning `0xFFFFFF` may look like a bug — but first learn *why* the handler exists and what it's trying to accomplish, then fix the edge case.
6. Update CLAUDE.md and affected docs/ immediately after changes. Stale docs are worse than no docs.
7. **Read docs before coding.** When adding or modifying a feature in a specific subsystem, read the corresponding doc(s) in `docs/` first. Don't read unrelated docs. Don't skip docs and jump straight to code or agents. The doc tells you WHY things are the way they are — code alone doesn't.

## Project Overview

Ecoflux is a **NeoForge 1.21.1** Minecraft mod implementing chunk-scale ecological succession. Each 16×16 chunk undergoes biome evolution driven by plant life: plants grow, age, die, and their collective "points" determine whether the chunk's biome progresses (e.g., plains → forest) or regresses. The system is data-driven via JSON configuration files.
1
- **Language**: Java 21
- **Build**: Gradle 9.2.1 via `gradlew` (wrapper)
- **Mod loader**: NeoForge 21.1.227
- **Mappings**: Parchment 2024.11.17
- **Gradle plugin**: `net.neoforged.moddev` 2.0.141

## Build Commands

```bash
./gradlew compileJava     # Compile only
./gradlew build            # Full build (compile + jar)
./gradlew runClient        # Launch Minecraft client with the mod
./gradlew runServer        # Launch dedicated server
./gradlew runGameTestServer  # Run game tests
./gradlew runData          # Run data generation
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

## Architecture

The codebase is in `src/main/java/com/cp/ecoflux/`. Key architectural layers:

### Configuration layer (`config/`)
- `SuccessionConfigLoader` — Gson-based JSON loader, extends `SimpleJsonResourceReloadListener` for hot-reload on `/reload`
- `SuccessionConfigRegistry` — Thread-safe cached lookup. `findBestMatch(biome, temperature, downfall)` resolves the best succession path for a given chunk
- `SuccessionPathDefinition` — Record: path_id, priority, source/target/fallback biomes, climate conditions, `positiveProgressStep`, `negativeProgressStep`. No longer carries `plants[]` or `ChunkRules` (2026-06-14 refactoring)
- `BiomeRules` — Record: `biomeId`, `minPlantCount`, `maxPlantCount`, `consuming`, `queueFillFactor`, `plants` (List<PathPlantEntry>). Per-biome plant ecosystem config. `samplePlantCount(Random)` returns a per-chunk cap sampled from a normal distribution centered at (min+max)/2 with σ=(max-min)/6, clamped to [min,max], simulating vanilla's variable per-chunk plant density. If `min_plant_count` is absent from JSON, it defaults to `max_plant_count` (no randomness). Queue capacity is computed from `maxPlantCount`, not the sampled value
- `BiomeRulesRegistry` — Thread-safe singleton registry. `getRules(biomeId)` returns the `BiomeRules` for a biome. Loaded by `BiomeRulesLoader`
- `BiomeRulesLoader` — `SimpleJsonResourceReloadListener` watching `biome_rules/` directory, populates `BiomeRulesRegistry`
- `PathPlantEntry` — Record: `plant_id` + `weight`. Used by both path config (historical) and biome rules
- `PlantDefinition` — Record: plant intrinsic properties (`plant_id`, `point_value`, `max_age_ticks`, `spawn_rules`)
- `PlantRegistry` — Singleton central plant registry. `getDefinition(plantId)` returns the canonical `PlantDefinition`. Loaded from `data/ecoflux/plant_definitions/*.json`
- `PlantRegistryLoader` — `SimpleJsonResourceReloadListener` watching `plant_definitions/` directory, populates `PlantRegistry` on startup and `/reload`
- `ChunkRules` — **Removed** (2026-06-14). Contents split into `BiomeRules` (maxPlantCount, consuming, queueFillFactor, plants) and `SuccessionPathDefinition` (step sizes). Evaluation/processing intervals moved to `EcofluxServerConfig` as global config
- `EcofluxServerConfig` — Server-side NeoForge config. Now includes global `evaluation_interval_ticks`, `prune_interval_ticks`, `observe_interval_ticks`, `spawn_interval_min/max_ticks` (moved from per-path ChunkRules)
- JSON path files: `src/main/resources/data/ecoflux/succession_paths/*.json`
- Biome rules: `src/main/resources/data/ecoflux/biome_rules/<namespace>/<biome>.json`
- Plant registry: `src/main/resources/data/ecoflux/plant_definitions/*.json`

### Data layer (`attachment/`)
- `SuccessionChunkData` — Core per-chunk state attached via NeoForge `DataAttachment<SuccessionChunkData>`. Contains: current/target/previous biome, progress (double), consuming value, max/current plant count, plant queue, vegetationRecords map, evaluation timer. Fully NBT-serializable
- `ActiveVegetationRecord` — Vegetation lifecycle record: adapter type, stage, point value, birth/age/expiry times. No longer carries `category` (removed with `VegetationCategory`)
- `PlantQueueEntry` — Pre-generated plant to spawn: plant_id, point_value, weight, max_age

### Succession service layer (`succession/`)
Extracted from `PrototypeChunkController` (now in `test/prototype/`). Follows the architecture.md design:
- `SuccessionService` — Main orchestration entry point. `initializeChunk()`, `step()`, `processChunkTick()`, `pruneChunk()`, `spawnInChunk()`, `evaluateChunk()`, `forceTransition()`, `hasActivePath()`, `describeChunk()`
- `SuccessionTargetResolver` — Chunk initialization: samples biome/climate, matches config via `SuccessionConfigRegistry`, populates `SuccessionChunkData`
- `SuccessionEvaluator` — Progress evaluation with aging gate (`hasAgingVegetation`). Compares vegetation points vs consuming, accumulates progress
- `BiomeTransitionService` — Biome replacement via `ChunkAccess.fillBiomesFromNoise()`, broadcasts `ClientboundChunksBiomesPacket`, plants trees on completion, resets chunk state

### World utilities (`world/`)
- `ChunkSamplingHelper` — Static utilities: `sampleChunkCenterBiome()`, `sampleChunkClimate()`, `sampleSurfaceY()`, `findSpawnPos()`, `canPlantAt()`, `isAllowedBaseBlock()`, `countNearbyTrackedPlants()`, `toBiomeKey()`

### Plant system (`plant/`)
The most architecturally mature subsystem. Uses an **adapter pattern**:
- `VegetationTypeAdapter` — Core interface: `matches(BlockState)`, `captureBirth(level, pos, state, gameTime, sourceBiomeId, sourcePathId, plantDefinition)`, `observe(record, gameTime)`, `visualState(record, gameTime)`. `captureBirth` now receives `PlantDefinition` for point values and lifetime from config. `category()` method removed
- `VegetationTracker` — Singleton that tracks/observes/syncs all vegetation. Key methods: `trackAt()`, `observeTracked()`, `observeChunk()`. `trackAt()` now accepts `PlantDefinition` parameter
- Adapters: `SimplePlantAdapter` (flowers, grass, ferns, mushrooms, dead bushes, double plants), `SaplingAdapter` (tree saplings/propagules → tree transformation), `TreeStructureAdapter` (mature trees). All no longer hardcode point values or lifetimes — these come from `PlantDefinition`
- `WorldGenVegetationScanner` — Scans newly-loaded chunks for world-generation vegetation (plants, mushrooms) and registers them with `VegetationTracker`. Phase 1 consumes SC tree placements from `EcofluxTreeFeature.PENDING_TREES` (decoration→chunk-load bridge), no BFS tree detection needed. Phase 1b detects huge mushrooms via BFS. Phase 2 detects simple plants via adapters. Phase 3 caps density by removing over-represented plant types. Randomizes vegetation birth times with ±20% per-plant lifespan variation to scatter death events. Called once per chunk from `ModChunkEvents.onChunkLoad`
- `VegetationCategory` enum — **Removed** (2026-06-12). Point values now come directly from `PlantDefinition.pointValue()`; no more hardcoded FLOWER=2/other=1
- `VegetationTracker.trackAt()` automatically tracks upper halves of double-height plants (tall grass, sunflowers) with 0 points to keep both halves visually synced
- `VegetationTransformation` — Descriptor for sapling→tree conversion. No longer carries `targetCategory`
- `PlantSpawner` — Plant spawning and pruning: `trySpawnPlant()`, `pruneInvalidPlants()`, `ensureQueue()`, `buildWeightedQueue()`, `fillPlants()`. Uses deviation-aware feedback: `computeEffectiveWeights()` adjusts per-plant weights based on actual vs target composition, so the system self-corrects toward the configured plant mix. Accepts `BiomeRules` for plant lists and capacities
- `BiomePlantSampler` — **New** (2026-06-14); moved to `test/sample/` (2026-06-14). `/ecoflux sample [radius]` command: BFS-scans connected same-biome chunks, counts all adapter-matched vegetation by type, reports per-chunk plant count distribution (min/max/avg/histogram) and per-type totals/percentages. Edge chunks filtered to sampling core biome area only; water chunks (30%+ water surface) filtered. `/ecoflux sample <radius> apply` auto-generates `BiomeRules` JSON. Used to calibrate `BiomeRules` min/max plant counts and weights against vanilla world generation
- `tree/TreeGrowthHandler` — Singleton managing active tree growth sessions. Called by `SaplingBlockMixin` when growth is intercepted. Maps sapling IDs → `TreeGrowthProfile`. 9 species use `SpaceColonizationProfile` (birch, oak, spruce_1x1, cherry, jungle_1x1, acacia, mangrove as 1x1; spruce, jungle, dark_oak as 2x2) + 2 `MushroomGrowthProfile`. Species supporting both 1x1 and 2x2 use dual-profile pattern: e.g. `spruce` (2x2) → `spruce_1x1` (fallback). Generic 2x2→1x1 fallback in `interceptGrowth()` derives `_1x1` variant name automatically. Public static `resolveProfile(ResourceLocation)` and `resolveProfileFromLog(ResourceLocation)` for external profile lookup from sapling or log block IDs
- `tree/TreeGrowthSession` — Per-tree growth state (position, tree type, stage counter, resolved height, timing), NBT-serializable. Transient fields: skeleton/morphologyParams/stagePlan (morphology), scParams/stageLogs/stageLeaves (space colonization) — rebuilt from seed on reload
- `tree/TreeGrowthProfile` — Interface: species-specific growth parameters (height range, block types, stage count, spacing) + optional `morphologyParams()`. 3 implementations: `SpaceColonizationProfile` (8 species, all trees), `MorphologyTreeProfile` (legacy, retained as reference), `MushroomGrowthProfile` (2 mushroom types)
- `tree/TreeShapeUtils` — Shared utilities: position-deterministic noise, log placement, 2x2 detection
- `tree/spacecolonization/` — **Space Colonization system** (2026-06-13, expanded 2026-06-14):
  - `SpaceColonizationParams` — 12-field parameter record with envelope-based leaf shaping (ELLIPSOID/TALL_ELLIPSOID/CONE) + DT-inspired node growth params (upProbability, splitChance, branchLengthRatio, secondaryChance). Species presets: birch, oak, cherry, spruce, jungle, dark_oak, acacia, mangrove
  - `SpaceColonizationGenerator` — Hybrid algorithm: Dynamic Trees-style discrete-Direction probMap node growth for logs (adapted from ferreusveritas/DynamicTrees, MIT), endpoint-cluster leaf placement (Phase 1) + envelope density falloff (Phase 2, with envelope bypass near trunk top for 2x2) + 2x2 trunk top cap (Phase 3, two-pass with live leafSet anchors, capR=leafRadius+2 min 5). Supports 1x1 and 2x2 trunks (4 parallel signals). Guarantees Chebyshev adjacency. Outputs `FullTreePlan` and `StagePlan`
  - `SpaceColonizationProfile` — Record implementing `TreeGrowthProfile`. Supports `is2x2` flag, optional `PostGrowHook` (e.g., mangrove prop roots). `morphologyParams()` returns null, uses session's `stageLogPositions()`/`stageLeafPositions()` for per-stage block placement
- `tree/profiles/MushroomGrowthProfile` — Parameterized record for 2 mushroom types. `MushroomCapStyle` (FLAT/DOMED) controls cap shape

### World generation (`worldgen/`)

- `biomemodifier/EcofluxBiomeModifiers` — `DeferredRegister<MapCodec<? extends BiomeModifier>>` registering two biome modifier serializers: `cancel_vanilla_trees` and `add_ecoflux_trees`. Registered in `EcofluxMod`
- `biomemodifier/CancelVanillaTreesBiomeModifier` — `Phase.REMOVE` biome modifier. Iterates ALL `GenerationStep.Decoration` stages and removes features whose `FeatureConfiguration` is a `TreeConfiguration`, `RootSystemConfiguration`, `RandomFeatureConfiguration`, or `SimpleRandomFeatureConfiguration`. Recursively unwraps nested feature configs. No namespace restriction — cancels all mod's trees
- `biomemodifier/AddEcofluxTreesBiomeModifier` — `Phase.ADD` biome modifier. Adds `ecoflux:ecoflux_trees` placed feature to `VEGETAL_DECORATION` stage via `ServerLifecycleHooks`
- `feature/EcofluxTreeFeature` — `extends Feature<NoneFeatureConfiguration>`. Custom tree feature that replaces vanilla trees during world decoration. Reads biome → `BiomeRules` → resolves tree species via `TreeGrowthHandler.resolveProfile()`. Generates SC trees via `SpaceColonizationGenerator.generateFull()`, places blocks via `WorldGenLevel.setBlock()`. Stores tree structures in `static PENDING_TREES` map for consumption by `WorldGenVegetationScanner` during chunk load. Supports 1x1 and 2x2 trees
- `EcofluxFeatures` — `DeferredRegister`s for `Feature`, `ConfiguredFeature`, and `PlacedFeature` registrations. Provides `ECOFLUX_TREE_PLACED` for use by `AddEcofluxTreesBiomeModifier`
- JSON triggers: `data/ecoflux/neoforge/biome_modifier/cancel_vanilla_trees.json` and `add_ecoflux_trees.json`

### Mixins (`mixin/`)
- `client/BlockRenderDispatcherMixin` — Client-side: suppresses vanilla block render for visually-tracked blocks with non-1.0 scale
- `SaplingBlockMixin` — Server-side: intercepts `SaplingBlock.advanceTree()`, cancels vanilla instant tree growth for Ecoflux-tracked saplings (delegates to `TreeGrowthHandler`)
- `worldgen/ChunkGeneratorMixin` — Server-side: makes `biomeSource` field mutable for batch sampling world. Implements `ChunkGeneratorAccessor` duck interface for type-safe biome source swapping at runtime
- `perf/*` — 13 profiling mixins that inject `PerformanceProfiler.push/pop` at HEAD/RETURN of key methods. Zero-cost when profiling is disabled. All in `mixin/perf/` for easy removal

### Test utilities (`test/`)
- `test/prototype/PrototypeChunkController` — Accelerated 10-second demo mode. Calls into `SuccessionService`, `PlantSpawner`, `BiomeTransitionService` for shared operations
- `test/performance/PerformanceProfiler` — Lightweight span-based performance profiler. Named spans with aggregated statistics (count, total, min, max, avg). `/ecoflux profile on/off/report` for control. Report also saved to `logs/ecoflux-profile.txt`
- `test/sample/BiomePlantSampler` — Vanilla world-gen plant distribution sampler. BFS same-biome chunk scanning, tree inference from logs, edge/water filtering, auto-generates `BiomeRules` JSON from P5-P95 percentiles and proportional weights. Commands: `/ecoflux sample`, `/ecoflux sample <radius> apply`
- `test/sample/SamplingBiomeSource` — Mutable single-biome `BiomeSource` for batch sampling world. Extends `BiomeSource` + `NoiseBiomeSource`, uses `Biome.CODEC` for JSON world preset deserialization. `setTargetBiome(Holder<Biome>)` for runtime biome switching
- `test/sample/SamplingBiomeSources` — `DeferredRegister` for the `ecoflux:sampling` biome source codec. Registered in `EcofluxMod`
- World preset: `data/ecoflux/worldgen/world_preset/sampling.json` — Single-biome world preset using `ecoflux:sampling` biome source
- `/ecoflux sample batchall [radius]` — Iterates all `minecraft:` biomes, swaps biome source via mixin, runs sampler at offset positions, auto-saves `biome_rules` JSON to `{server}/ecoflux/sampled_rules/minecraft/`

### Client visual layer (`client/visual/`)
- `VisualLifecycleClientRuntime` — Client singleton receiving visual state from server
- `VisualLifecycleWorldRenderer` — Renders visual overlays (scale, aging tint) for tracked plants
- `VisualLifecycleAdapter` interface + per-type adapters (Flower, Grass, Sapling, Generic)

### Networking (`network/`)
- `ModNetworking` — NeoForge Payload-based packet registration. `syncChunkToTracking()` sends vegetation visual state to clients watching a chunk
- `VegetationVisualChunkSyncPayload` — Packet carrying per-plant visual snapshots

### Initialization (`init/`)
- `ModAttachments` — Registers the `DataAttachment<SuccessionChunkData>`
- `ModChunkEvents` — Chunk load/unload/tick handlers: initializes new chunks via `SuccessionService`, triggers `WorldGenVegetationScanner`, manages `ALL_LOADED_CHUNKS`/`AUTO_CHUNKS`/`TREE_OBSERVE_CHUNKS` tracking sets, cleans up `EcofluxTreeFeature.PENDING_TREES` on chunk unload
- `ModCommands` — Debug commands under `/ecoflux prototype`, `/ecoflux auto`, `/ecoflux lifecycle`, `/ecoflux visual`, `/ecoflux tree` (instant/grid/stats for tree algorithm testing)
- `ModReloadListeners` — JSON config reload hooks for succession paths, plant registry, and biome rules

### Constants and entry point
- `EcofluxConstants` — `MOD_ID = "ecoflux"`, logger, `ResourceLocation` factory method
- `EcofluxMod` — `@Mod` entry point, wires all subsystems in constructor

## Key Design Decisions

1. **Data-driven paths + biome rules**: Succession paths define source→target biome transitions with priority, climate matching, and step sizes. Biome-level plant ecosystem config (plant list, weights, max density, consumption threshold, queue fill factor) lives in separate `biome_rules/` JSON files. Evaluation interval is global in `EcofluxServerConfig`.

2. **Per-chunk state via Data Attachments**: Chunk state persists across chunk reloads via NBT serialization. Access pattern: `chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA)`.

3. **Adapter-based plant recognition**: Rather than hardcoding plant types, `VegetationTypeAdapter` implementations match against `BlockState` and handle lifecycle logic. Current adapters cover small flowers, grass/ferns, saplings, mushrooms, and trees.

4. **Prototype-first → service extraction**: The full succession loop was first proven in the monolithic `PrototypeChunkController`, then extracted into clean `succession/` service classes (`SuccessionService`, `SuccessionTargetResolver`, `SuccessionEvaluator`, `BiomeTransitionService`). The prototype now only houses the accelerated 10-second demo mode. Utilities live in `world/ChunkSamplingHelper` and `plant/PlantSpawner`.

5. **Single tracking system**: Plant tracking is now unified under `vegetationRecords` via `VegetationTracker`. The legacy `activePlants` / `ActivePlantRecord` system has been removed.

6. **BiomeModifier-based tree generation**: Vanilla tree features are cancelled at the biome level during `Phase.REMOVE` (all decoration stages, recursive feature-config unwrapping), and custom SC trees are added during `Phase.ADD` via `EcofluxTreeFeature`. The decoration→chunk-load bridge uses a `static PENDING_TREES` map: trees are placed during decoration (`WorldGenLevel.setBlock()`), and their structures are consumed by `WorldGenVegetationScanner` during `ChunkEvent.Load` for lifecycle tracking. This eliminates the old post-hoc BFS-based tree replacement + deferred neighbor-waiting system, solving forest server performance issues and cross-chunk tree boundary problems at the correct architectural layer.

## Current Development State

- **Completed**: Tree lifecycle Phase 4 (death/decay) — 2026-06-11; Plant registry refactoring — 2026-06-12; Tree profile refactoring (11 boilerplate → 2 parameterized) — 2026-06-12; Space Colonization tree algorithm (9 species, 1x1 + 2x2) — 2026-06-13/14; Old morphology system removed — 2026-06-14; WorldGenVegetationScanner + world-gen density capping — 2026-06-14; BiomeModifier tree replacement (CancelVanillaTrees + EcofluxTreeFeature) — 2026-06-14; BiomeRules refactoring (64 biome configs) — 2026-06-14; Sodium compatibility mixin; Docs updated — 2026-06-15; 2x2 spruce profile + generic fallback — 2026-06-16; Phase 3 anchorSet stale-snapshot fix + Phase 2 envelope bypass + NPE fix — 2026-06-16
- **In progress**: Succession integration, Dynamic Trees compatibility, chunk boundary blending
- **Known gap**: Non-player block change events → vegetation cleanup, chunk boundary blending, more succession path JSONs, GameTest

## Documentation

All design docs are in `docs/`, written in Chinese:
- `README.md` — Master index with project overview, subsystem diagram, document map, and quick navigation
- `architecture.md` — Overall architecture: package map, layered design, data flow, key design decisions
- `config-system.md` — Configuration system: JSON format spec, loader, registry, path matching
- `succession-system.md` — Succession core: service orchestration, evaluation, biome transition, prototype
- `plant-lifecycle-system.md` — Plant lifecycle: adapter pattern, VegetationTracker, PlantSpawner, lifecycle stages
- `tree-growth-system.md` — Tree growth: handler, sessions, space colonization algorithm, worldgen integration
- `client-visual-system.md` — Client visual: VisualLifecycle rendering, tint/scale, growth animation client
- `networking-and-data.md` — Network sync & data: packets, chunk attachments, NBT serialization
- `succession-editor.md` — Succession editor: React web tool for visual succession path design
- `todolist.md` — Priority-ordered TODO list and JSON config coverage analysis
- `plant-death-system.md` — Plant death/decay system design and implementation (completed 2026-06-11)

## Important Conventions

- All identifiers use the `ecoflux` namespace: mod_id, resource namespace, data directory
- Resource path helper: `EcofluxConstants.id("name")` → `ResourceLocation("ecoflux", "name")`
- Java records are preferred for data objects (see config records, attachment records)
- Logging goes through `EcofluxConstants.LOGGER` (SLF4J via NeoForge)
- The repo directory is still named `Succession` (historical); the mod itself is `Ecoflux`
- **CRITICAL**: After any significant code changes (new packages, extracted classes, new features, changed architecture), immediately update `CLAUDE.md` and the relevant files in `docs/` (`latest_progress.md`, `todolist.md`, etc.) to reflect the new state. Stale documentation is worse than no documentation