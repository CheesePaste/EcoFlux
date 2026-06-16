package com.cp.ecoflux.test.sample;

import com.cp.ecoflux.plant.adapters.TreeStructureAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.config.biome.BiomeRules;
import com.cp.ecoflux.config.biome.BiomeRulesRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Samples connected same-biome chunks to collect vanilla plant distribution data.
 * Used to calibrate {@code BiomeRules} plant counts and weights to match
 * natural world generation.
 */
public final class BiomePlantSampler {

    private BiomePlantSampler() {}

    public static BiomePlantSample sample(ServerLevel level, LevelChunk centerChunk, int radius) {
        ResourceKey<Biome> targetBiome = com.cp.ecoflux.world.ChunkSamplingHelper
                .sampleChunkCenterBiome(centerChunk)
                .orElse(null);
        if (targetBiome == null) {
            return null;
        }

        Set<ChunkPos> allChunks = bfsSameBiomeChunks(level, centerChunk.getPos(), targetBiome, radius);
        Set<ChunkPos> coreChunks = filterCoreChunks(level, allChunks, targetBiome);
        Set<ChunkPos> dryChunks = filterWaterChunks(level, coreChunks);

        Map<ResourceLocation, Integer> plantCounts = new LinkedHashMap<>();
        List<Integer> perChunkCounts = new ArrayList<>();
        int totalTrees = 0;

        List<ChunkPos> sortedChunks = dryChunks.stream()
                .sorted(Comparator.comparingLong(ChunkPos::toLong))
                .toList();

        for (ChunkPos pos : sortedChunks) {
            LevelChunk chunk = level.getChunk(pos.x, pos.z);
            ChunkScanResult result = scanChunk(level, chunk);
            perChunkCounts.add(result.plantCount);
            totalTrees += result.treeCount;
            for (var entry : result.counts.entrySet()) {
                plantCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        int min = perChunkCounts.stream().min(Integer::compareTo).orElse(0);
        int max = perChunkCounts.stream().max(Integer::compareTo).orElse(0);
        double avg = perChunkCounts.stream().mapToInt(Integer::intValue).average().orElse(0);

        int removed = allChunks.size() - dryChunks.size();

        return new BiomePlantSample(targetBiome, dryChunks.size(), min, max, avg,
                plantCounts, totalTrees, perChunkCounts, removed);
    }

    /**
     * BFS from center chunk to find all same-biome connected chunks within radius.
     * Uses 4-directional adjacency (N/S/E/W) to ensure contiguous biome area.
     */
    private static Set<ChunkPos> bfsSameBiomeChunks(ServerLevel level, ChunkPos center,
                                                     ResourceKey<Biome> targetBiome, int radius) {
        Set<ChunkPos> visited = new HashSet<>();
        Queue<ChunkPos> queue = new ArrayDeque<>();
        visited.add(center);
        queue.add(center);

        while (!queue.isEmpty()) {
            ChunkPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                    ChunkPos neighbor = new ChunkPos(current.x + dx, current.z + dz);
                    if (visited.contains(neighbor)) continue;
                    if (Math.abs(neighbor.x - center.x) > radius
                            || Math.abs(neighbor.z - center.z) > radius) continue;

                    LevelChunk neighborChunk = level.getChunk(neighbor.x, neighbor.z);
                    ResourceKey<Biome> neighborBiome = com.cp.ecoflux.world.ChunkSamplingHelper
                            .sampleChunkCenterBiome(neighborChunk)
                            .orElse(null);
                    if (targetBiome.equals(neighborBiome)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
        return visited;
    }

    /**
     * Filters out edge chunks to keep only the deep interior of the biome.
     * A chunk is interior if ALL 4 neighbors (N/S/E/W) are also same-biome
     * and within the BFS-discovered area.
     */
    private static Set<ChunkPos> filterCoreChunks(ServerLevel level, Set<ChunkPos> allChunks,
                                                   ResourceKey<Biome> targetBiome) {
        Set<ChunkPos> core = new HashSet<>();
        for (ChunkPos pos : allChunks) {
            boolean allSameBiome = true;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                    ChunkPos neighbor = new ChunkPos(pos.x + dx, pos.z + dz);
                    if (!allChunks.contains(neighbor)) {
                        allSameBiome = false;
                        break;
                    }
                }
                if (!allSameBiome) break;
            }
            if (allSameBiome) {
                core.add(pos);
            }
        }
        return core.isEmpty() ? allChunks : core;
    }

    /**
     * Removes chunks where over 30% of surface blocks are water.
     * Single-biome worlds still generate rivers and oceans when the terrain
     * dips below sea level, which would skew plant counts to near-zero.
     */
    private static Set<ChunkPos> filterWaterChunks(ServerLevel level, Set<ChunkPos> chunks) {
        Set<ChunkPos> dry = new HashSet<>();
        int maxWaterColumns = 256 * 30 / 100; // 76 columns = 30% of 16×16

        for (ChunkPos pos : chunks) {
            int waterColumns = 0;
            int chunkMinX = pos.getMinBlockX();
            int chunkMinZ = pos.getMinBlockZ();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = chunkMinX + x;
                    int worldZ = chunkMinZ + z;
                    int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                    if (surfaceY > level.getMinBuildHeight()
                            && level.getBlockState(new BlockPos(worldX, surfaceY, worldZ)).is(Blocks.WATER)) {
                        waterColumns++;
                    }
                }
            }
            if (waterColumns <= maxWaterColumns) {
                dry.add(pos);
            }
        }
        return dry.isEmpty() ? chunks : dry;
    }

    /**
     * Scans a single chunk for vegetation. Small plants and saplings are counted
     * by block ID. Trees are detected as connected log+leaf components; each tree
     * contributes +1 to the inferred sapling type.
     */
    private static ChunkScanResult scanChunk(ServerLevel level, LevelChunk chunk) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        Set<BlockPos> treeProcessed = new HashSet<>();
        int treeCount = 0;
        int plantCount = 0;

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int chunkX = chunk.getPos().getMinBlockX();
        int chunkZ = chunk.getPos().getMinBlockZ();

        // Phase 1: Detect trees and infer sapling types from log blocks
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    if (treeProcessed.contains(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.LOGS)) continue;

                    Set<BlockPos> logs = collectConnectedLogs(level, pos, chunk);
                    if (logs.isEmpty()) continue;

                    Set<BlockPos> leaves = collectAdjacentLeaves(level, logs, chunk);

                    treeProcessed.addAll(logs);
                    treeProcessed.addAll(leaves);

                    if (!leaves.isEmpty()) {
                        treeCount++;
                        ResourceLocation logId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        ResourceLocation saplingId = inferSaplingFromLog(logId);
                        if (saplingId != null) {
                            counts.merge(saplingId, 1, Integer::sum);
                            plantCount++;
                        }
                    }
                }
            }
        }

        // Phase 1b: Detect huge mushrooms (stem + cap blocks)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    if (treeProcessed.contains(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.MUSHROOM_STEM)) continue;

                    Set<BlockPos> stems = collectConnectedStems(level, pos, chunk);
                    if (stems.isEmpty()) continue;

                    Set<BlockPos> caps = collectAdjacentMushroomCaps(level, stems, chunk);

                    treeProcessed.addAll(stems);
                    treeProcessed.addAll(caps);

                    if (!caps.isEmpty()) {
                        treeCount++;
                        ResourceLocation mushroomType = inferMushroomType(level, caps);
                        if (mushroomType != null) {
                            counts.merge(mushroomType, 1, Integer::sum);
                            plantCount++;
                        }
                    }
                }
            }
        }

        // Phase 2: Count small plants and ungrown saplings
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    if (treeProcessed.contains(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    var adapter = com.cp.ecoflux.plant.VegetationTracker.INSTANCE.findAdapter(state);
                    if (adapter.isEmpty()) continue;
                    if (adapter.get() instanceof TreeStructureAdapter) continue;

                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    counts.merge(blockId, 1, Integer::sum);
                    plantCount++;
                }
            }
        }

        return new ChunkScanResult(plantCount, treeCount, counts);
    }

    /**
     * Maps a log block ID to the sapling that would have grown into this tree.
     */
    private static ResourceLocation inferSaplingFromLog(ResourceLocation logId) {
        String path = logId.getPath();
        if (!path.endsWith("_log")) {
            return null;
        }
        String woodType = path.substring(0, path.length() - 4);
        if ("mangrove".equals(woodType)) {
            return ResourceLocation.fromNamespaceAndPath(logId.getNamespace(), "mangrove_propagule");
        }
        return ResourceLocation.fromNamespaceAndPath(logId.getNamespace(), woodType + "_sapling");
    }

    private static Set<BlockPos> collectConnectedStems(ServerLevel level, BlockPos start, LevelChunk chunk) {
        Set<BlockPos> stems = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        stems.add(start);
        queue.add(start);

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int minX = chunk.getPos().getMinBlockX();
        int maxX = minX + 15;
        int minZ = chunk.getPos().getMinBlockZ();
        int maxZ = minZ + 15;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = pos.offset(dx, dy, dz);
                        if (stems.contains(neighbor)) continue;
                        if (neighbor.getX() < minX || neighbor.getX() > maxX
                                || neighbor.getY() < minY || neighbor.getY() >= maxY
                                || neighbor.getZ() < minZ || neighbor.getZ() > maxZ) continue;
                        if (level.getBlockState(neighbor).is(Blocks.MUSHROOM_STEM)) {
                            stems.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return stems;
    }

    private static Set<BlockPos> collectAdjacentMushroomCaps(ServerLevel level, Set<BlockPos> stems, LevelChunk chunk) {
        Set<BlockPos> caps = new HashSet<>();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int minX = chunk.getPos().getMinBlockX();
        int maxX = minX + 15;
        int minZ = chunk.getPos().getMinBlockZ();
        int maxZ = minZ + 15;

        for (BlockPos stem : stems) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos neighbor = stem.offset(dx, dy, dz);
                        if (stems.contains(neighbor) || caps.contains(neighbor)) continue;
                        if (neighbor.getX() < minX || neighbor.getX() > maxX
                                || neighbor.getY() < minY || neighbor.getY() >= maxY
                                || neighbor.getZ() < minZ || neighbor.getZ() > maxZ) continue;
                        BlockState ns = level.getBlockState(neighbor);
                        if (ns.is(Blocks.BROWN_MUSHROOM_BLOCK) || ns.is(Blocks.RED_MUSHROOM_BLOCK)) {
                            caps.add(neighbor);
                        }
                    }
                }
            }
        }
        return caps;
    }

    private static ResourceLocation inferMushroomType(ServerLevel level, Set<BlockPos> caps) {
        int brown = 0;
        int red = 0;
        for (BlockPos pos : caps) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
                brown++;
            } else if (state.is(Blocks.RED_MUSHROOM_BLOCK)) {
                red++;
            }
        }
        if (brown >= red && brown > 0) {
            return ResourceLocation.withDefaultNamespace("brown_mushroom");
        }
        if (red > brown) {
            return ResourceLocation.withDefaultNamespace("red_mushroom");
        }
        return null;
    }

    private static Set<BlockPos> collectConnectedLogs(ServerLevel level, BlockPos start, LevelChunk chunk) {
        Set<BlockPos> logs = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        logs.add(start);
        queue.add(start);

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int minX = chunk.getPos().getMinBlockX();
        int maxX = minX + 15;
        int minZ = chunk.getPos().getMinBlockZ();
        int maxZ = minZ + 15;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = pos.offset(dx, dy, dz);
                        if (logs.contains(neighbor)) continue;
                        if (neighbor.getX() < minX || neighbor.getX() > maxX
                                || neighbor.getY() < minY || neighbor.getY() >= maxY
                                || neighbor.getZ() < minZ || neighbor.getZ() > maxZ) continue;
                        if (level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                            logs.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return logs;
    }

    private static Set<BlockPos> collectAdjacentLeaves(ServerLevel level, Set<BlockPos> logs, LevelChunk chunk) {
        Set<BlockPos> leaves = new HashSet<>();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int minX = chunk.getPos().getMinBlockX();
        int maxX = minX + 15;
        int minZ = chunk.getPos().getMinBlockZ();
        int maxZ = minZ + 15;

        for (BlockPos log : logs) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos neighbor = log.offset(dx, dy, dz);
                        if (logs.contains(neighbor) || leaves.contains(neighbor)) continue;
                        if (neighbor.getX() < minX || neighbor.getX() > maxX
                                || neighbor.getY() < minY || neighbor.getY() >= maxY
                                || neighbor.getZ() < minZ || neighbor.getZ() > maxZ) continue;
                        if (level.getBlockState(neighbor).is(BlockTags.LEAVES)) {
                            leaves.add(neighbor);
                        }
                    }
                }
            }
        }
        return leaves;
    }

    // ── Result types ──────────────────────────────────────────────────────

    private record ChunkScanResult(int plantCount, int treeCount,
                                   Map<ResourceLocation, Integer> counts) {}

    // ── Rules JSON generation ────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Runs the sampler and generates/updates a biome_rules JSON file from the
     * results. Existing consuming and queue_fill_factor values are preserved if
     * the biome already has rules; otherwise sensible defaults are used.
     */
    public static String sampleAndApply(ServerLevel level, LevelChunk centerChunk, int radius) {
        BiomePlantSample sample = sample(level, centerChunk, radius);
        if (sample == null) {
            return "采样失败：无法确定当前区块的群系。";
        }

        ResourceLocation biomeId = sample.biome.location();
        Optional<BiomeRules> existing = BiomeRulesRegistry.getRules(biomeId);

        int consuming = existing.map(BiomeRules::consuming).orElse(-1);
        double queueFillFactor = existing.map(BiomeRules::queueFillFactor).orElse(2.0);

        List<Integer> sorted = sample.perChunkCounts.stream().sorted().toList();
        int p5Idx = Math.max(0, sorted.size() * 5 / 100);
        int p95Idx = Math.min(sorted.size() - 1, sorted.size() * 95 / 100);
        int p5Val = sorted.get(p5Idx);
        int p95Val = sorted.get(p95Idx);
        int minPlants = p5Val;
        int maxPlants = Math.max(p5Val, p95Val);

        if (consuming < 0) {
            consuming = Math.max(minPlants, (int) Math.round(sample.avgPlantsPerChunk * 0.8));
        }

        Holder<Biome> biomeHolder = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(sample.biome)
                .orElse(null);

        boolean isWaterBiome = biomeHolder != null
                && (biomeHolder.is(BiomeTags.IS_OCEAN) || biomeHolder.is(BiomeTags.IS_RIVER));
        boolean isNonOverworld = biomeHolder != null
                && (biomeHolder.is(BiomeTags.IS_NETHER) || biomeHolder.is(BiomeTags.IS_END));
        boolean noPlantsDetected = sample.plantCounts.isEmpty();

        List<WeightedPlant> weightedPlants;
        if (isWaterBiome || isNonOverworld || noPlantsDetected) {
            minPlants = 0;
            maxPlants = 0;
            consuming = 0;
            queueFillFactor = 0.0;
            weightedPlants = List.of();
        } else {
            weightedPlants = computeWeights(sample.plantCounts);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("biome_id", biomeId.toString());
        root.addProperty("min_plant_count", minPlants);
        root.addProperty("max_plant_count", maxPlants);
        root.addProperty("consuming", consuming);
        root.addProperty("queue_fill_factor", queueFillFactor);

        JsonArray plantsArray = new JsonArray();
        for (WeightedPlant wp : weightedPlants) {
            JsonObject plantObj = new JsonObject();
            plantObj.addProperty("plant_id", wp.plantId.toString());
            plantObj.addProperty("weight", wp.weight);
            plantsArray.add(plantObj);
        }
        root.add("plants", plantsArray);

        String json = GSON.toJson(root);
        Path outFile = writeRulesJson(level, biomeId, json);

        String report = sample.formatReport();
        String summary = String.format(
                "\n自动生成 BiomeRules → %s\n  min=%d max=%d consuming=%d queue_fill_factor=%.1f\n  植物: %s\n",
                outFile.toAbsolutePath(), minPlants, maxPlants, consuming, queueFillFactor,
                weightedPlants.stream()
                        .map(wp -> wp.plantId.getPath() + ":" + wp.weight)
                        .collect(Collectors.joining(", ")));

        EcofluxConstants.LOGGER.info("[Ecoflux] 自动生成群系规则:\n{}", json);
        return report + summary;
    }

    /**
     * Computes integer weights from plant type percentages. The minimum share
     * gets weight 1, and all other weights are scaled proportionally and rounded.
     */
    private static List<WeightedPlant> computeWeights(Map<ResourceLocation, Integer> plantCounts) {
        int total = plantCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return List.of();
        }

        double minShare = plantCounts.values().stream()
                .mapToDouble(c -> (double) c / total)
                .filter(p -> p > 0)
                .min().orElse(0.01);

        return plantCounts.entrySet().stream()
                .map(e -> {
                    double share = (double) e.getValue() / total;
                    int weight = Math.max(1, (int) Math.round(share / minShare));
                    return new WeightedPlant(e.getKey(), weight);
                })
                .sorted(Comparator.comparingInt(WeightedPlant::weight).reversed())
                .toList();
    }

    public static Path writeRulesJson(ServerLevel level, ResourceLocation biomeId, String json) {
        Path dir = level.getServer().getServerDirectory()
                .resolve("ecoflux")
                .resolve("sampled_rules")
                .resolve(biomeId.getNamespace());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            EcofluxConstants.LOGGER.error("无法创建目录 {}", dir, e);
            return dir;
        }
        String fileName = biomeId.getPath().replace('/', '_') + ".json";
        Path file = dir.resolve(fileName);
        try {
            Files.writeString(file, json);
        } catch (IOException e) {
            EcofluxConstants.LOGGER.error("无法写入文件 {}", file, e);
        }
        return file;
    }

    private record WeightedPlant(ResourceLocation plantId, int weight) {}

    public record BiomePlantSample(
            ResourceKey<Biome> biome,
            int chunksSampled,
            int minPlantsPerChunk,
            int maxPlantsPerChunk,
            double avgPlantsPerChunk,
            Map<ResourceLocation, Integer> plantCounts,
            int treeCount,
            List<Integer> perChunkCounts,
            int edgeChunksRemoved) {

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("群系采样报告: ").append(biome.location()).append("\n");
            sb.append("  核心区块数: ").append(chunksSampled);
            if (edgeChunksRemoved > 0) {
                sb.append(" (已排除 ").append(edgeChunksRemoved).append(" 个边缘/水域区块)");
            }
            sb.append("\n");
            sb.append("  植物数/chunk: 最低=").append(minPlantsPerChunk)
                    .append(" 最高=").append(maxPlantsPerChunk)
                    .append(" 平均=").append(String.format("%.1f", avgPlantsPerChunk)).append("\n");
            sb.append("  树木总数: ").append(treeCount).append("\n");

            int totalPlants = plantCounts.values().stream().mapToInt(Integer::intValue).sum();
            sb.append("  植物总数: ").append(totalPlants).append("\n");

            if (!plantCounts.isEmpty()) {
                sb.append("  植物种类分布:\n");
                plantCounts.entrySet().stream()
                        .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed())
                        .forEach(entry -> {
                            double pct = totalPlants > 0
                                    ? 100.0 * entry.getValue() / totalPlants
                                    : 0;
                            sb.append(String.format("    %-40s %5d  (%5.1f%%)%n",
                                    entry.getKey(), entry.getValue(), pct));
                        });
            }

            if (!perChunkCounts.isEmpty()) {
                sb.append("  每区块植物数分布:\n");
                Map<Integer, Long> histogram = perChunkCounts.stream()
                        .collect(Collectors.groupingBy(c -> c, LinkedHashMap::new, Collectors.counting()));
                histogram.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            String bar = "█".repeat(entry.getValue().intValue());
                            sb.append(String.format("    %3d: %s (%d)%n",
                                    entry.getKey(), bar, entry.getValue()));
                        });
            }

            return sb.toString();
        }
    }
}
