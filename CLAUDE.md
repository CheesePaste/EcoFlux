## Project Overview

Ecoflux is a **NeoForge 1.21.1** Minecraft mod implementing chunk-scale ecological succession. Each 16×16 chunk undergoes biome evolution driven by plant life: plants grow, age, die, and their collective "points" determine whether the chunk's biome progresses (e.g., plains → forest) or regresses. The system is data-driven via JSON configuration files.

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

### Plant lifecycle system (`plant/`)
The most architecturally mature subsystem. Uses an **adapter pattern**:
- `VegetationTypeAdapter` — Core interface: `matches(BlockState)`, `captureBirth(BlockState)`, `observe(record, gameTime)`, `visualState(record, gameTime)`
- `VegetationTracker` — Singleton that tracks/observes/syncs all vegetation. Key methods: `trackAt()`, `observeTracked()`, `observeChunk()`
- Adapters: `SimplePlantAdapter` (flowers, grass, ferns, mushrooms), `SaplingAdapter` (tree saplings → tree transformation), `TreeStructureAdapter` (mature trees)
- `VegetationTransformation` — Descriptor for sapling→tree conversion

### Prototype controller (`prototype/`)
- `PrototypeChunkController` (827 lines) — **Monolithic working prototype** that ties everything together. Handles: chunk init, plant spawning, lifecycle observation via `VegetationTracker`, progress evaluation, biome replacement via `ChunkAccess.fillBiomesFromNoise()`, accelerated 10-second demo mode. This is the reference implementation that a future clean `succession/` package should extract from

### Client visual layer (`client/visual/`)
- `VisualLifecycleClientRuntime` — Client singleton receiving visual state from server
- `VisualLifecycleWorldRenderer` — Renders visual overlays (scale, aging tint) for tracked plants
- `VisualLifecycleAdapter` interface + per-type adapters (Flower, Grass, Sapling, Generic)
- `BlockRenderDispatcherMixin` — Client-side mixin hooking block rendering to suppress vanilla render for visually-tracked blocks

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

4. **Prototype-first approach**: `PrototypeChunkController` is a monolithic working implementation proving the full loop. The `architecture.md` envisions extracting it into a cleaner `succession/` package (`SuccessionService`, `SuccessionEvaluator`, `BiomeTransitionService`, etc.) but this extraction has not yet been done.

5. **Two tracking systems coexist**: `activePlants` (original prototype plant tracking) and `vegetationRecords` (newer lifecycle-based tracking via `VegetationTracker`). Progress evaluation currently checks `vegetationRecords` with an aging gate (`hasAgingVegetation`).

## Current Development State

- **Done**: Mod bootstrap, chunk data attachments, JSON config loading with 3 example paths, vegetation lifecycle adapter system, client visual rendering, network sync, debug commands, prototype full-loop demo
- **In prototype, needs extraction**: Production-grade succession service, proper evaluation scheduling (currently piggybacks on the prototype), multi-plant queue integration
- **Not yet started**: Player planting/destruction events → `VegetationTracker`, Dynamic Trees compatibility, chunk boundary blending, formal progress using `vegetationRecords` point values (currently uses aging gate only)
- **Known gap**: `vegetationRecords` point integration into chunk progress settlement is not yet complete — progress evaluation advances when aging vegetation exists, not based on actual point totals vs consuming

## Documentation

All design docs are in `docs/`, written in Chinese:
- `architecture.md` — Target architecture: 4-layer design (config, data, runtime, compat), proposed package structure
- `development-context.md` — Constraints, gaps, risk areas
- `todolist.md` — Priority-ordered TODO (P0–P6)
- `latest_progress.md` — Most recent development log, current state of each subsystem
- `plant-lifecycle-system.md` — Vegetation adapter design
- `visual-lifecycle-layer.md` — Client visual rendering design
- `succession-path-format.md` — JSON path format spec

## Important Conventions

- All identifiers use the `ecoflux` namespace: mod_id, resource namespace, data directory
- Resource path helper: `EcofluxConstants.id("name")` → `ResourceLocation("ecoflux", "name")`
- Java records are preferred for data objects (see config records, attachment records)
- Logging goes through `EcofluxConstants.LOGGER` (SLF4J via NeoForge)
- The repo directory is still named `Succession` (historical); the mod itself is `Ecoflux`
