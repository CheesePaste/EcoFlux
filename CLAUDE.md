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

The codebase is in `src/main/java/com/s/ecoflux/`. Key architectural layers:

### Configuration layer (`config/`)
- `SuccessionConfigLoader` — Gson-based JSON loader, extends `SimpleJsonResourceReloadListener` for hot-reload on `/reload`
- `SuccessionConfigRegistry` — Thread-safe cached lookup. `findBestMatch(biome, temperature, downfall)` resolves the best succession path for a given chunk
- `SuccessionPathDefinition` — Record: path_id, source/target/fallback biomes, climate conditions, `PathPlantEntry` list, `ChunkRules` (consuming, max_plants, intervals, progress steps)
- `PathPlantEntry` — Record: `plant_id` + `weight`. Plants in succession paths reference the central registry by ID, with path-specific spawn weight
- `PlantDefinition` — Record: plant intrinsic properties (`plant_id`, `point_value`, `max_age_ticks`, `spawn_rules`). No longer carries `weight` (path-specific) or `category` (removed)
- `PlantRegistry` — Singleton central plant registry. `getDefinition(plantId)` returns the canonical `PlantDefinition`. Loaded from `data/ecoflux/plant_definitions/*.json`
- `PlantRegistryLoader` — `SimpleJsonResourceReloadListener` watching `plant_definitions/` directory, populates `PlantRegistry` on startup and `/reload`
- JSON path files live at `src/main/resources/data/ecoflux/succession_paths/*.json`
- Plant registry JSON lives at `src/main/resources/data/ecoflux/plant_definitions/*.json`

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
- `VegetationCategory` enum — **Removed** (2026-06-12). Point values now come directly from `PlantDefinition.pointValue()`; no more hardcoded FLOWER=2/other=1
- `VegetationTracker.trackAt()` automatically tracks upper halves of double-height plants (tall grass, sunflowers) with 0 points to keep both halves visually synced
- `VegetationTransformation` — Descriptor for sapling→tree conversion. No longer carries `targetCategory`
- `PlantSpawner` — Plant spawning and pruning: `trySpawnPlant()`, `pruneInvalidPlants()`, `ensureQueue()`, `buildWeightedQueue()`, `fillPlants()`. Uses `PlantRegistry` to resolve plant definitions, `PathPlantEntry` for weighted selection
- `tree/TreeGrowthHandler` — Singleton managing active tree growth sessions. Called by `SaplingBlockMixin` when growth is intercepted. Maps sapling IDs → `TreeGrowthProfile`, supports 1x1 and 2x2 trees. If profile has `morphologyParams()`, calls `TreeMorphology.growStage()`; otherwise falls back to legacy `profile.growStage()`
- `tree/TreeGrowthSession` — Per-tree growth state (position, tree type, stage counter, resolved height, timing), NBT-serializable. Transient fields: skeleton, morphologyParams, stagePlan (rebuilt from seed on reload)
- `tree/TreeGrowthProfile` — Interface: species-specific growth parameters (height range, block types, stage count, spacing) + optional `morphologyParams()`. 2 parameterized implementations: `MorphologyTreeProfile` (9 tree species) + `MushroomGrowthProfile` (2 mushroom types)
- `tree/TreeShapeUtils` — Shared utilities: position-deterministic noise, canopy radius functions, leaf disc placement, 2x2 detection, branch generation, log/leaf block placement helpers
- `tree/morphology/` — **Morphology system**:
  - `NodeType` — Enum: TRUNK, PRIMARY_BRANCH, SECONDARY_BRANCH, TWIG
  - `SkeletonNode` — Record: pos, type, radius, parentIndex, depth
  - `TreeSkeleton` — Skeleton data structure: node list, trunk path, primary branch list, trunkLevels (for 2x2)
  - `SkeletonGenerator` — Parametric recursive branching: trunk with lean/noise, primary branches with radial+upward angle, secondary branches from branch midpoints
  - `CanopyEnvelope` — 6 canopy shape density functions (ELLIPSOID/TALL_ELLIPSOID/CONE/CLUSTERED_ELLIPSOID/FLAT_CYLINDER/FLAT_DISC_CLUSTERED) with edge feathering
  - `LeafFiller` — Skeleton-aware leaf placement: AABB traversal, canopy density check, skeleton distance, noise probability, sorted by proximity, max 50/stage
  - `MorphologyParams` — Species morphology parameter record
  - `MorphologyPresets` — 9 static factory methods (oak/birch/spruce/jungle/darkOak/jungle1x1/cherry/mangrove/acacia) returning pre-configured `MorphologyParams`
  - `TreeMorphology` — Integration entry: generateSkeleton → planStages → growStage (place logs + fill leaves)
- `tree/profiles/MorphologyTreeProfile` — Parameterized record for 9 tree species. Configurable `CanGrowStageStrategy` (SINGLE_TRUNK/TRUNK_2X2/ACACIA_3X3) and optional `GrowStageHook` for species-specific post-morphology behavior (e.g., mangrove prop roots)
- `tree/profiles/MushroomGrowthProfile` — Parameterized record for 2 mushroom types. `MushroomCapStyle` (FLAT/DOMED) controls cap shape

### Mixins (`mixin/`)
- `client/BlockRenderDispatcherMixin` — Client-side: suppresses vanilla block render for visually-tracked blocks with non-1.0 scale
- `SaplingBlockMixin` — Server-side: intercepts `SaplingBlock.advanceTree()`, cancels vanilla instant tree growth for Ecoflux-tracked saplings (delegates to `TreeGrowthHandler`)
- `perf/*` — 13 profiling mixins that inject `PerformanceProfiler.push/pop` at HEAD/RETURN of key methods. Zero-cost when profiling is disabled. All in `mixin/perf/` for easy removal

### Test utilities (`test/`)
- `test/prototype/PrototypeChunkController` — Accelerated 10-second demo mode. Calls into `SuccessionService`, `PlantSpawner`, `BiomeTransitionService` for shared operations
- `test/performance/PerformanceProfiler` — Lightweight span-based performance profiler. Named spans with aggregated statistics (count, total, min, max, avg). `/ecoflux profile on/off/report` for control

### Client visual layer (`client/visual/`)
- `VisualLifecycleClientRuntime` — Client singleton receiving visual state from server
- `VisualLifecycleWorldRenderer` — Renders visual overlays (scale, aging tint) for tracked plants
- `VisualLifecycleAdapter` interface + per-type adapters (Flower, Grass, Sapling, Generic)

### Networking (`network/`)
- `ModNetworking` — NeoForge Payload-based packet registration. `syncChunkToTracking()` sends vegetation visual state to clients watching a chunk
- `VegetationVisualChunkSyncPayload` — Packet carrying per-plant visual snapshots

### Initialization (`init/`)
- `ModAttachments` — Registers the `DataAttachment<SuccessionChunkData>`
- `ModChunkEvents` — Chunk load/unload/tick handlers, accelerated transition mode
- `ModCommands` — Debug commands under `/ecoflux prototype`, `/ecoflux auto`, `/ecoflux lifecycle`, `/ecoflux visual`
- `ModReloadListeners` — JSON config reload hooks for succession paths and plant registry

### Constants and entry point
- `EcofluxConstants` — `MOD_ID = "ecoflux"`, logger, `ResourceLocation` factory method
- `EcofluxMod` — `@Mod` entry point, wires all subsystems in constructor

## Key Design Decisions

1. **Data-driven paths**: Succession paths are JSON files in the data pack, loaded via `SimpleJsonResourceReloadListener`. Each path defines source→target biome, climate matching, plant weights, and chunk rules.

2. **Per-chunk state via Data Attachments**: Chunk state persists across chunk reloads via NBT serialization. Access pattern: `chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA)`.

3. **Adapter-based plant recognition**: Rather than hardcoding plant types, `VegetationTypeAdapter` implementations match against `BlockState` and handle lifecycle logic. Current adapters cover small flowers, grass/ferns, saplings, mushrooms, and trees.

4. **Prototype-first → service extraction**: The full succession loop was first proven in the monolithic `PrototypeChunkController`, then extracted into clean `succession/` service classes (`SuccessionService`, `SuccessionTargetResolver`, `SuccessionEvaluator`, `BiomeTransitionService`). The prototype now only houses the accelerated 10-second demo mode. Utilities live in `world/ChunkSamplingHelper` and `plant/PlantSpawner`.

5. **Single tracking system**: Plant tracking is now unified under `vegetationRecords` via `VegetationTracker`. The legacy `activePlants` / `ActivePlantRecord` system has been removed.

## Current Development State

- **Completed**: Tree lifecycle Phase 4 (death/decay) — 2026-06-11; Plant registry refactoring (central `plants.json`, `VegetationCategory` removal, adapter config-driven values) — 2026-06-12; Tree profile refactoring (11 boilerplate profiles → 2 parameterized classes) — 2026-06-12
- **In progress**: Succession integration, Dynamic Trees compatibility, chunk boundary blending
- **Known gap**: Non-player block change events → vegetation cleanup, chunk boundary blending, more succession path JSONs, GameTest

## Documentation

All design docs are in `docs/`, written in Chinese:
- `README.md` — Master index with project overview, subsystem diagram, document map, and quick navigation
- `architecture.md` — Overall architecture: package map, layered design, data flow, key design decisions
- `config-system.md` — Configuration system: JSON format spec, loader, registry, path matching
- `succession-system.md` — Succession core: service orchestration, evaluation, biome transition, prototype
- `plant-lifecycle-system.md` — Plant lifecycle: adapter pattern, VegetationTracker, PlantSpawner, lifecycle stages
- `tree-growth-system.md` — Tree growth: handler, sessions, profiles, morphology system, BlockDisplay animations
- `client-visual-system.md` — Client visual: VisualLifecycle rendering, tint/scale, growth animation client
- `networking-and-data.md` — Network sync & data: packets, chunk attachments, NBT serialization
- `succession-editor.md` — Succession editor: React web tool for visual succession path design
- `todolist.md` — Priority-ordered TODO list and JSON config coverage analysis
- `plant-death-system.md` — Plant death/decay system design and implementation (completed 2026-06-11)
- `plant-registry-refactor.md` — Central plant registry refactoring design doc (completed 2026-06-12)
- `tree-profile-refactor.md` — Tree profile refactoring design: replacing 11 boilerplate profile classes with 2 parameterized classes

## Important Conventions

- All identifiers use the `ecoflux` namespace: mod_id, resource namespace, data directory
- Resource path helper: `EcofluxConstants.id("name")` → `ResourceLocation("ecoflux", "name")`
- Java records are preferred for data objects (see config records, attachment records)
- Logging goes through `EcofluxConstants.LOGGER` (SLF4J via NeoForge)
- The repo directory is still named `Succession` (historical); the mod itself is `Ecoflux`
- **CRITICAL**: After any significant code changes (new packages, extracted classes, new features, changed architecture), immediately update `CLAUDE.md` and the relevant files in `docs/` (`latest_progress.md`, `todolist.md`, etc.) to reflect the new state. Stale documentation is worse than no documentation