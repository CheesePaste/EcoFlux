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
- `SuccessionChunkData` — Core per-chunk state attached via NeoForge `DataAttachment<SuccessionChunkData>`. Contains: current/target/previous biome, progress (double), consuming value, max/current plant count, plant queue, active plant/vegetation maps, evaluation timer. Fully NBT-serializable
- `ActivePlantRecord` — Tracked plant: position, point value, birth/expiry time
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
- Adapters: `SimplePlantAdapter` (flowers, grass, ferns, mushrooms), `SaplingAdapter` (tree saplings → tree transformation), `TreeStructureAdapter` (mature trees)
- `VegetationTransformation` — Descriptor for sapling→tree conversion
- `PlantSpawner` — Plant spawning and pruning: `trySpawnPlant()`, `pruneInvalidPlants()`, `ensureQueue()`, `buildWeightedQueue()`, `fillPlants()`
- `tree/TreeGrowthHandler` — Singleton managing active tree growth sessions. Called by `SaplingBlockMixin` when growth is intercepted
- `tree/TreeGrowthSession` — Per-tree growth state (position, tree type, stage counter, timing), NBT-serializable

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

- **Done**: Mod bootstrap, chunk data attachments, JSON config loading with 3 example paths, vegetation lifecycle adapter system, client visual rendering, network sync, debug commands, prototype full-loop demo, service layer extraction from prototype → `succession/`/`world/`/`plant/` packages, vegetationRecords point-based progress evaluation, player place/break → VegetationTracker auto-tracking, multi-plant weighted queue with queue_fill_factor, **negative regression → fallback biome**, **activePlants retired — unified to vegetationRecords**, **tree lifecycle Phase 1: Mixin intercepts sapling instant growth** (SaplingBlockMixin + TreeGrowthHandler + TreeGrowthSession), **tree lifecycle Phase 2: gradual tree construction** (TreeGrowthProfile + OakGrowthProfile + tickAll with 20-tick throttle)
- **In progress**: Tree lifecycle Phase 3+ (multi-species profiles, death/decay, succession integration)
- **Not yet started**: Dynamic Trees compatibility, chunk boundary blending
- **Known gap**: Non-player block change events → vegetation cleanup, chunk boundary blending, more succession path JSONs, GameTest

## Documentation

All design docs are in `docs/`, written in Chinese:
- `architecture.md` — Target architecture: 4-layer design (config, data, runtime, compat), proposed package structure
- `development-context.md` — Constraints, gaps, risk areas
- `todolist.md` — Priority-ordered TODO (P0–P6)
- `latest_progress.md` — Most recent development log, current state of each subsystem
- `plant-lifecycle-system.md` — Vegetation adapter design
- `visual-lifecycle-layer.md` — Client visual rendering design
- `succession-path-format.md` — JSON path format spec
- `tree-lifecycle-implementation.md` — Tree lifecycle implementation plan (6 phases)

## Important Conventions

- All identifiers use the `ecoflux` namespace: mod_id, resource namespace, data directory
- Resource path helper: `EcofluxConstants.id("name")` → `ResourceLocation("ecoflux", "name")`
- Java records are preferred for data objects (see config records, attachment records)
- Logging goes through `EcofluxConstants.LOGGER` (SLF4J via NeoForge)
- The repo directory is still named `Succession` (historical); the mod itself is `Ecoflux`
- **CRITICAL**: After any significant code changes (new packages, extracted classes, new features, changed architecture), immediately update `CLAUDE.md` and the relevant files in `docs/` (`latest_progress.md`, `todolist.md`, etc.) to reflect the new state. Stale documentation is worse than no documentation
- **