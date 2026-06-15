package com.cp.ecoflux.plant.tree;

/**
 * Singleton managing all active tree growth sessions.
 *
 * <p>Structure: Provides session lifecycle methods (intercept, advance, complete, remove),
 * {@code tickAll} processing with {@link com.cp.ecoflux.config.SuccessionSpeedConfig} integration,
 * and profile resolution via the static {@code PROFILES} map. Sessions are stored in
 * {@link com.cp.ecoflux.attachment.SuccessionChunkData}.
 *
 * <p>Role in Ecoflux: Entry point for the progressive tree growth pipeline. Called by
 * {@code SaplingBlockMixin} to intercept vanilla instant growth, then dispatches staged
 * growth via {@link com.cp.ecoflux.plant.tree.morphology.TreeMorphology#growStage} (morphology
 * system) or legacy {@link TreeGrowthProfile#growStage}. Replaces Minecraft saplings with
 * slow-growing animated trees that take ~20-64 real minutes to mature.
 */

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.attachment.ActiveVegetationRecord;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.SuccessionSpeedConfig;
import com.cp.ecoflux.config.plant.PlantDefinition;
import com.cp.ecoflux.config.plant.PlantRegistry;
import com.cp.ecoflux.config.plant.PlantSpawnRules;
import com.cp.ecoflux.init.ModAttachments;
import com.cp.ecoflux.init.ModChunkEvents;
import com.cp.ecoflux.plant.TreeStructure;
import com.cp.ecoflux.plant.TreeStructureAdapter;
import com.cp.ecoflux.plant.tree.spacecolonization.SpaceColonizationParams;
import com.cp.ecoflux.plant.tree.spacecolonization.SpaceColonizationProfile;
import com.cp.ecoflux.plant.tree.profiles.MushroomGrowthProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

public final class TreeGrowthHandler {
    public static final TreeGrowthHandler INSTANCE = new TreeGrowthHandler();

    private static final Map<ResourceLocation, TreeGrowthProfile> PROFILES = new HashMap<>();

    static {
        reg(new SpaceColonizationProfile(id("oak"), 1200, Blocks.OAK_LOG, Blocks.OAK_LEAVES, false,
                SpaceColonizationParams.oak(), null));
        reg(new SpaceColonizationProfile(id("birch"), 800, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, false,
                SpaceColonizationParams.birch(), null));
        reg(new SpaceColonizationProfile(id("spruce"), 1600, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, false,
                SpaceColonizationParams.spruce(), null));
        reg(new SpaceColonizationProfile(id("cherry"), 1200, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, false,
                SpaceColonizationParams.cherry(), null));
        reg(new SpaceColonizationProfile(id("jungle_1x1"), 1400, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, false,
                SpaceColonizationParams.jungle(), null));
        reg(new SpaceColonizationProfile(id("acacia"), 1200, Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES, false,
                SpaceColonizationParams.acacia(), null));
        reg(new SpaceColonizationProfile(id("mangrove"), 1067, Blocks.MANGROVE_LOG, Blocks.MANGROVE_LEAVES, false,
                SpaceColonizationParams.mangrove(), SpaceColonizationProfile::placePropRoots));

        reg(new SpaceColonizationProfile(id("jungle"), 1600, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, true,
                SpaceColonizationParams.jungle(), null));
        reg(new SpaceColonizationProfile(id("dark_oak"), 1200, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, true,
                SpaceColonizationParams.darkOak(), null));

        reg(new MushroomGrowthProfile(id("brown_mushroom"), 4, 7, 800,
                Blocks.BROWN_MUSHROOM_BLOCK, MushroomGrowthProfile.MushroomCapStyle.FLAT));
        reg(new MushroomGrowthProfile(id("red_mushroom"), 3, 7, 800,
                Blocks.RED_MUSHROOM_BLOCK, MushroomGrowthProfile.MushroomCapStyle.DOMED));

        // Mangrove propagule is a distinct block from mangrove sapling
        PROFILES.put(id("mangrove_propagule"), PROFILES.get(id("mangrove")));
    }

    private static void reg(TreeGrowthProfile profile) {
        ResourceLocation type = profile.treeType();
        PROFILES.put(type, profile);
        String path = type.getPath();
        if (!path.endsWith("_sapling")) {
            PROFILES.put(id(path + "_sapling"), profile);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private final Set<Long> chunksWithSessions = new LinkedHashSet<>();

    private TreeGrowthHandler() {
    }

    public void interceptGrowth(ServerLevel level, BlockPos pos, ActiveVegetationRecord record) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation saplingId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        TreeGrowthProfile profile = resolveProfile(saplingId);
        if (profile == null) return;

        BlockPos sessionPos = pos.immutable();
        if (profile.is2x2()) {
            BlockPos nwCorner = TreeShapeUtils.find2x2NWCorner(level, pos);
            if (nwCorner != null) {
                sessionPos = nwCorner;
            } else {
                // Fall back to 1x1 variant for species that support both modes
                profile = resolveProfile(ResourceLocation.withDefaultNamespace("jungle_1x1"));
                if (profile == null) return;
            }
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getTreeGrowthSessions().containsKey(sessionPos)) return;

        int height = profile.resolveHeight(level.random);
        int stages = profile.totalStagesForHeight(height);
        int interval = profile.ticksPerStage();

        TreeGrowthSession session = new TreeGrowthSession(
                sessionPos, profile.treeType(), level.getGameTime(), stages, interval, height);

        if (profile instanceof SpaceColonizationProfile scProfile) {
            session.ensureConnectedPlan(level, scProfile.scParams(), scProfile.is2x2());
        }

        // Replace sapling with log immediately
        Block logBlock = profile.logBlock();
        if (profile.is2x2()) {
            for (BlockPos trunkPos : TreeShapeUtils.trunk2x2Positions(sessionPos, sessionPos.getY())) {
                chunkData.removeVegetation(trunkPos);
                level.removeBlock(trunkPos, false);
                level.setBlock(trunkPos, logBlock.defaultBlockState(), 3);
                session.placedLogs().add(trunkPos.immutable());
            }
        } else {
            chunkData.removeVegetation(sessionPos);
            level.removeBlock(sessionPos, false);
            level.setBlock(sessionPos, logBlock.defaultBlockState(), 3);
            session.placedLogs().add(sessionPos.immutable());
        }

        chunkData.addTreeGrowthSession(sessionPos, session);
        chunksWithSessions.add(chunk.getPos().toLong());

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Intercepted tree growth at {} (type={}), height={}, totalStages={}",
                sessionPos, saplingId, height, stages);
    }

    public void interceptMushroomGrowth(ServerLevel level, BlockPos pos, BlockState state) {
        ResourceLocation mushroomId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        TreeGrowthProfile profile = resolveProfile(mushroomId);
        if (profile == null) return;

        BlockPos sessionPos = pos.immutable();

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getTreeGrowthSessions().containsKey(sessionPos)) return;

        int height = profile.resolveHeight(level.random);
        int stages = profile.totalStagesForHeight(height);
        int interval = profile.ticksPerStage();

        TreeGrowthSession session = new TreeGrowthSession(
                sessionPos, mushroomId, level.getGameTime(), stages, interval, height);

        chunkData.addTreeGrowthSession(sessionPos, session);
        chunksWithSessions.add(chunk.getPos().toLong());

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Intercepted mushroom growth at {} (type={}), height={}, totalStages={}",
                sessionPos, mushroomId, height, stages);
    }


    @Nullable
    public TreeGrowthSession findSessionForSapling(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        TreeGrowthSession session = chunkData.getTreeGrowthSessions().get(pos);
        if (session != null) return session;

        // Search 2x2 area for 2x2 trees (session stored at NW corner)
        for (int dx = 0; dx >= -1; dx--) {
            for (int dz = 0; dz >= -1; dz--) {
                if (dx == 0 && dz == 0) continue;
                session = chunkData.getTreeGrowthSessions().get(
                        new BlockPos(pos.getX() + dx, pos.getY(), pos.getZ() + dz));
                if (session != null) return session;
            }
        }
        return null;
    }

    public void removeSession(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        chunkData.removeTreeGrowthSession(pos);
        if (!chunkData.hasTreeGrowthSessions()) {
            chunksWithSessions.remove(chunk.getPos().toLong());
        }
    }

    public boolean hasActiveSessions() {
        return !chunksWithSessions.isEmpty();
    }

    public void tickAll(ServerLevel level) {
        if (chunksWithSessions.isEmpty()) return;

        long gameTime = level.getGameTime();
        List<Long> emptyChunks = new ArrayList<>();

        for (long chunkPosLong : new ArrayList<>(chunksWithSessions)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(chunkPosLong),
                    net.minecraft.world.level.ChunkPos.getZ(chunkPosLong));
            if (chunk == null) continue;

            SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
            Map<BlockPos, TreeGrowthSession> sessions = chunkData.getTreeGrowthSessions();
            if (sessions.isEmpty()) {
                emptyChunks.add(chunkPosLong);
                continue;
            }

            List<BlockPos> completed = new ArrayList<>();

            for (Map.Entry<BlockPos, TreeGrowthSession> entry : sessions.entrySet()) {
                BlockPos pos = entry.getKey();
                TreeGrowthSession session = entry.getValue();

                if (session.isComplete()) {
                    completed.add(pos);
                    continue;
                }

                long effectiveTicksPerStage = (long) Math.max(1,
                        session.ticksPerStage() / SuccessionSpeedConfig.getSpeedMultiplier());
                if (gameTime - session.lastStageTime() < effectiveTicksPerStage) continue;

                TreeGrowthProfile profile = resolveProfile(session.treeType());
                if (profile == null) {
                    completed.add(pos);
                    EcofluxConstants.LOGGER.warn("[Ecoflux] No growth profile for tree type: {}", session.treeType());
                    continue;
                }

                if (!profile.canGrowStage(level, pos, session.currentStage(),
                        session.totalStages(), session.resolvedHeight())) {
                    continue;
                }

                RandomSource treeRandom = TreeShapeUtils.positionRandom(pos, level.getSeed());
                if (profile instanceof SpaceColonizationProfile scProfile) {
                    session.ensureConnectedPlan(level, scProfile.scParams(), scProfile.is2x2());
                    profile.growStage(level, pos, session.currentStage(),
                            session.totalStages(), session.resolvedHeight(), treeRandom);
                } else {
                    profile.growStage(level, pos, session.currentStage(),
                            session.totalStages(), session.resolvedHeight(), treeRandom);
                }
                session.advanceStage(gameTime);

                if (session.isComplete()) {
                    completed.add(pos);
                    onGrowthComplete(level, chunk, pos, session, profile);
                }
            }

            for (BlockPos pos : completed) {
                chunkData.removeTreeGrowthSession(pos);
            }
            if (!chunkData.hasTreeGrowthSessions()) {
                emptyChunks.add(chunkPosLong);
            }
        }

        chunksWithSessions.removeAll(emptyChunks);
    }

    public boolean forceAdvanceStage(ServerLevel level, BlockPos pos) {
        TreeGrowthSession session = findSessionForSapling(level, pos);
        if (session == null || session.isComplete()) return false;

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;

        TreeGrowthProfile profile = resolveProfile(session.treeType());
        if (profile == null) return false;

        if (!profile.canGrowStage(level, session.saplingPos(), session.currentStage(),
                session.totalStages(), session.resolvedHeight())) return false;

        RandomSource treeRandom = TreeShapeUtils.positionRandom(session.saplingPos(), level.getSeed());
        if (profile instanceof SpaceColonizationProfile scProfile) {
            session.ensureConnectedPlan(level, scProfile.scParams(), scProfile.is2x2());
            profile.growStage(level, session.saplingPos(), session.currentStage(),
                    session.totalStages(), session.resolvedHeight(), treeRandom);
        } else {
            profile.growStage(level, session.saplingPos(), session.currentStage(),
                    session.totalStages(), session.resolvedHeight(), treeRandom);
        }
        session.advanceStage(level.getGameTime());

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Tree bone-mealed stage {}/{} at {} (type={})",
                session.currentStage(), session.totalStages(), session.saplingPos(), session.treeType());

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (session.isComplete()) {
            onGrowthComplete(level, chunk, session.saplingPos(), session, profile);
            chunkData.removeTreeGrowthSession(session.saplingPos());
            if (!chunkData.hasTreeGrowthSessions()) {
                chunksWithSessions.remove(chunk.getPos().toLong());
            }
        }

        return true;
    }

    public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Map<BlockPos, TreeGrowthSession> sessions = chunkData.getTreeGrowthSessions();
        if (sessions.isEmpty()) return;

        boolean hasValidSessions = false;
        for (TreeGrowthSession session : sessions.values()) {
            TreeGrowthProfile profile = resolveProfile(session.treeType());
            if (profile == null) continue;
            if (profile instanceof SpaceColonizationProfile scProfile) {
                session.ensureConnectedPlan(level, scProfile.scParams(), scProfile.is2x2());
            }
            hasValidSessions = true;
        }

        if (hasValidSessions) {
            chunksWithSessions.add(chunk.getPos().toLong());
        }
    }

    private void onGrowthComplete(ServerLevel level, LevelChunk chunk, BlockPos basePos,
                                   TreeGrowthSession session, TreeGrowthProfile profile) {
        var chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Block logBlock = profile.logBlock();

        if (profile.is2x2()) {
            for (BlockPos trunkPos : TreeShapeUtils.trunk2x2Positions(basePos, basePos.getY())) {
                chunkData.removeVegetation(trunkPos);
                level.removeBlock(trunkPos, false);
                level.setBlock(trunkPos, logBlock.defaultBlockState(), 3);
                session.placedLogs().add(trunkPos.immutable());
            }
        } else {
            chunkData.removeVegetation(basePos);
            level.removeBlock(basePos, false);
            level.setBlock(basePos, logBlock.defaultBlockState(), 3);
            session.placedLogs().add(basePos.immutable());
        }

        TreeStructure treeStructure = new TreeStructure(
                session.placedLogs().stream().mapToLong(BlockPos::asLong).toArray(),
                session.placedLeaves().stream().mapToLong(BlockPos::asLong).toArray());

        net.minecraft.resources.ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(basePos).getBlock());
        PlantDefinition treeDef = PlantRegistry.INSTANCE.getDefinition(blockId)
                .orElseGet(() -> {
                    EcofluxConstants.LOGGER.warn("[Ecoflux] No PlantDefinition for mature tree block {}, using fallback", blockId);
                    return new PlantDefinition(blockId, 4, 288000L, PlantSpawnRules.EMPTY);
                });
        ActiveVegetationRecord treeRecord = TreeStructureAdapter.INSTANCE.captureBirth(
                level,
                basePos,
                level.getBlockState(basePos),
                level.getGameTime(),
                chunkData.getCurrentBiome().map(key -> key.location()),
                chunkData.getActivePathId(),
                treeDef);
        chunkData.trackVegetation(treeRecord.withTreeStructure(treeStructure));

        ModChunkEvents.markChunkHasTreeStructure(level, chunk.getPos().toLong());

        EcofluxConstants.LOGGER.info("[Ecoflux] Tree growth complete at {}, {} logs + {} leaves stored",
                basePos, treeStructure.logPositions().length, treeStructure.leafPositions().length);
    }

    @Nullable
    public static TreeGrowthProfile resolveProfile(ResourceLocation saplingId) {
        TreeGrowthProfile profile = PROFILES.get(saplingId);
        if (profile != null) return profile;

        String path = saplingId.getPath();
        if (path.endsWith("_sapling")) {
            String treeName = path.substring(0, path.length() - "_sapling".length());
            return PROFILES.get(ResourceLocation.fromNamespaceAndPath(saplingId.getNamespace(), treeName));
        }
        return null;
    }

    @Nullable
    public static TreeGrowthProfile resolveProfileFromLog(ResourceLocation logId) {
        String path = logId.getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        for (String suffix : new String[]{"_log", "_wood", "_stem"}) {
            if (path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        return PROFILES.get(ResourceLocation.fromNamespaceAndPath(logId.getNamespace(), path));
    }
}
