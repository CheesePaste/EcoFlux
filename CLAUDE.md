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
- `SuccessionPathDefinition` — Record: path_id, source/target/fallback biomes, climate conditions, plant list, `ChunkRules` (consuming, max_plants, intervals, progress steps)
- JSON path files live at `src/main/resources/data/ecoflux/succession_paths/*.json`

### Data layer (`attachment/`)
- `SuccessionChunkData` — Core per-chunk state attached via NeoForge `DataAttachment<SuccessionChunkData>`. Contains: current/target/previous biome, progress (double), consuming value, max/current plant count, plant queue, vegetationRecords map, evaluation timer. Fully NBT-serializable
- `ActiveVegetationRecord` — Vegetation lifecycle record: adapter type, category, stage, point value, birth/age/expiry times
- `PlantQueueEntry` — Pre-generated plant to spawn: plant_id, point_value, weight, max_age

### Succession service layer (`succession/`)
Extracted from `PrototypeChunkController`. Follows the architecture.md design:
- `SuccessionService` — Main orchestration entry point. `initializeChunk()`, `step()`, `processChunkTick()`, `pruneChunk()`, `spawnInChunk()`, `evaluateChunk()`, `forceTransition()`, `hasActivePath()`, `describeChunk()`
- `SuccessionTargetResolver` — Chunk initialization: samples biome/climate, matches config via `SuccessionConfigRegistry`, populates `SuccessionChunkData`
- `SuccessionEvaluator` — Progress evaluation with aging gate (`hasAgingVegetation`). Compares vegetation points vs consuming, accumulates progress
- `BiomeTransitionService` — Biome replacement via `ChunkAccess.fillBiomesFromNoise()`, broadcasts `ClientboundChunksBiomesPacket`, plants trees on completion, resets chunk state

### World utilities (`world/`)
- `ChunkSamplingHelper` — Static utilities: `sampleChunkCenterBiome()`, `sampleChunkClimate()`, `sampleSurfaceY()`, `findSpawnPos()`, `canPlantAt()`, `isAllowedBaseBlock()`, `countNearbyTrackedPlants()`, `toBiomeKey()`

### Plant system (`plant/`)
The most architecturally mature subsystem. Uses an **adapter pattern**:
- `VegetationTypeAdapter` — Core interface: `matches(BlockState)`, `captureBirth(BlockState)`, `observe(record, gameTime)`, `visualState(record, gameTime)`
- `VegetationTracker` — Singleton that tracks/observes/syncs all vegetation. Key methods: `trackAt()`, `observeTracked()`, `observeChunk()`
- Adapters: `SimplePlantAdapter` (flowers, grass, ferns, mushrooms, dead bushes, double plants), `SaplingAdapter` (tree saplings/propagules → tree transformation), `TreeStructureAdapter` (mature trees)
- `VegetationTracker.trackAt()` automatically tracks upper halves of double-height plants (tall grass, sunflowers) with 0 points to keep both halves visually synced
- `VegetationTransformation` — Descriptor for sapling→tree conversion
- `PlantSpawner` — Plant spawning and pruning: `trySpawnPlant()`, `pruneInvalidPlants()`, `ensureQueue()`, `buildWeightedQueue()`, `fillPlants()`
- `tree/TreeGrowthHandler` — Singleton managing active tree growth sessions. Called by `SaplingBlockMixin` when growth is intercepted. Maps sapling IDs → `TreeGrowthProfile`, supports 1x1 and 2x2 trees. If profile has `morphologyParams()`, calls `TreeMorphology.growStage()`; otherwise falls back to legacy `profile.growStage()`
- `tree/TreeGrowthSession` — Per-tree growth state (position, tree type, stage counter, resolved height, timing), NBT-serializable. Transient fields: skeleton, morphologyParams, stagePlan (rebuilt from seed on reload)
- `tree/TreeGrowthProfile` — Interface: species-specific growth parameters (height range, block types, stage count, spacing) + optional `morphologyParams()`. 9 implementations in `tree/profiles/` (oak, birch, spruce, jungle 2x2, jungle 1x1, dark oak, acacia, cherry, mangrove + 2 mushroom)
- `tree/TreeShapeUtils` — Shared utilities: position-deterministic noise, canopy radius functions, leaf disc placement, 2x2 detection, branch generation, log/leaf block placement helpers
- `tree/morphology/` — **New morphology system** (2026-06-10):
  - `NodeType` — Enum: TRUNK, PRIMARY_BRANCH, SECONDARY_BRANCH, TWIG
  - `SkeletonNode` — Record: pos, type, radius, parentIndex, depth
  - `TreeSkeleton` — Skeleton data structure: node list, trunk path, primary branch list, trunkLevels (for 2x2)
  - `SkeletonGenerator` — Parametric recursive branching: trunk with lean/noise, primary branches with radial+upward angle, secondary branches from branch midpoints
  - `CanopyEnvelope` — 5 canopy shape density functions (ELLIPSOID/TALL_ELLIPSOID/CONE/CLUSTERED_ELLIPSOID/FLAT_CYLINDER/FLAT_DISC_CLUSTERED) with edge feathering
  - `LeafFiller` — Skeleton-aware leaf placement: AABB traversal, canopy density check, skeleton distance, noise probability, sorted by proximity, max 50/stage, Chebyshev distance to nearest skeleton node
  - `MorphologyParams` — Species morphology parameter record with factory methods (oak/birch/spruce/jungle/darkOak/acacia)
  - `TreeMorphology` — Integration entry: generateSkeleton → planStages → growStage (place logs + fill leaves)
- `tree/profiles/OakGrowthProfile` — Oak: flat ellipsoid canopy, 8-13 trunk, 3600 ticks/stage (~27 min), slight lean, 5-9 branches, 3-block clear trunk
- `tree/profiles/BirchGrowthProfile` — Birch: tall slender ellipsoid, 10-16 trunk, 2400 ticks/stage (~20 min), nearly vertical, 0-3 short branches at top, 4-block clear trunk
- `tree/profiles/SpruceGrowthProfile` — Spruce: conical full-height foliage, 13-22 trunk, 4800 ticks/stage (~48 min), vertical, 12-20 horizontal branches, 3-block clear trunk
- `tree/profiles/JungleGrowthProfile` — Jungle: 2x2 trunk, 15-22 trunk, 4800 ticks/stage (~64 min), large ellipsoid + 5 sub-clusters, 7-12 long branches + secondary, 4-block clear trunk
- `tree/profiles/DarkOakGrowthProfile` — Dark oak: 2x2 trunk, flat cylinder dense canopy, 9-14 trunk, 3600 ticks/stage (~30 min), 6-10 branches, 3-block clear trunk
- `tree/profiles/AcaciaGrowthProfile` — Acacia: flat disc + scattered sphere clusters, 8-14 trunk, 3600 ticks/stage (~27 min), 15-18° lean, 4-7 branches, 4-block clear trunk
- `tree/profiles/Jungle1x1GrowthProfile` — Jungle 1×1: clustered ellipsoid, 12-18 trunk, 4200 ticks/stage, 5-9 branches, 4 sub-clusters, 3-block clear trunk
- `tree/profiles/CherryGrowthProfile` — Cherry: wide ellipsoid canopy, 8-14 trunk, 3600 ticks/stage, 4-8 spreading branches, pink wood/leaves
- `tree/profiles/MangroveGrowthProfile` — Mangrove: rounded ellipsoid, 6-10 trunk, 3200 ticks/stage, 3-6 branches + prop roots at base

### Mixins (`mixin/`)
- `client/BlockRenderDispatcherMixin` — Client-side: suppresses vanilla block render for visually-tracked blocks with non-1.0 scale
- `SaplingBlockMixin` — Server-side: intercepts `SaplingBlock.advanceTree()`, cancels vanilla instant tree growth for Ecoflux-tracked saplings (delegates to `TreeGrowthHandler`)

### Prototype controller (`prototype/`)
- `PrototypeChunkController` (~175 lines) — Slimmed down to only the **accelerated 10-second demo mode**. All standard succession operations have been extracted to the `succession/`, `world/`, and `plant/` service classes. Calls into `SuccessionService`, `PlantSpawner`, `BiomeTransitionService` for shared operations

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
- `ModReloadListeners` — JSON config reload hook

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

- **In progress**: Tree lifecycle Phase 4 (death/decay), succession integration
- **Not yet started**: Dynamic Trees compatibility, chunk boundary blending
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

## Important Conventions

- All identifiers use the `ecoflux` namespace: mod_id, resource namespace, data directory
- Resource path helper: `EcofluxConstants.id("name")` → `ResourceLocation("ecoflux", "name")`
- Java records are preferred for data objects (see config records, attachment records)
- Logging goes through `EcofluxConstants.LOGGER` (SLF4J via NeoForge)
- The repo directory is still named `Succession` (historical); the mod itself is `Ecoflux`
- **CRITICAL**: After any significant code changes (new packages, extracted classes, new features, changed architecture), immediately update `CLAUDE.md` and the relevant files in `docs/` (`latest_progress.md`, `todolist.md`, etc.) to reflect the new state. Stale documentation is worse than no documentation