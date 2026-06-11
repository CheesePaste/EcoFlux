package com.s.ecoflux.plant.tree;

/**
 * Singleton managing all active tree growth sessions.
 *
 * <p>Structure: Provides session lifecycle methods (intercept, advance, complete, remove),
 * {@code tickAll} processing with {@link com.s.ecoflux.config.SuccessionSpeedConfig} integration,
 * and profile resolution via the static {@code PROFILES} map. Sessions are stored in
 * {@link com.s.ecoflux.attachment.SuccessionChunkData}.
 *
 * <p>Role in Ecoflux: Entry point for the progressive tree growth pipeline. Called by
 * {@code SaplingBlockMixin} to intercept vanilla instant growth, then dispatches staged
 * growth via {@link com.s.ecoflux.plant.tree.morphology.TreeMorphology#growStage} (morphology
 * system) or legacy {@link TreeGrowthProfile#growStage}. Replaces Minecraft saplings with
 * slow-growing animated trees that take ~20-64 real minutes to mature.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.plant.TreeStructureAdapter;
import com.s.ecoflux.plant.tree.profiles.AcaciaGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.BirchGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.BrownMushroomGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.CherryGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.DarkOakGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.Jungle1x1GrowthProfile;
import com.s.ecoflux.plant.tree.profiles.JungleGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.MangroveGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.OakGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.RedMushroomGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.SpruceGrowthProfile;
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
        PROFILES.put(id("oak"), OakGrowthProfile.INSTANCE);
        PROFILES.put(id("oak_sapling"), OakGrowthProfile.INSTANCE);
        PROFILES.put(id("birch"), BirchGrowthProfile.INSTANCE);
        PROFILES.put(id("birch_sapling"), BirchGrowthProfile.INSTANCE);
        PROFILES.put(id("spruce"), SpruceGrowthProfile.INSTANCE);
        PROFILES.put(id("spruce_sapling"), SpruceGrowthProfile.INSTANCE);
        PROFILES.put(id("jungle"), JungleGrowthProfile.INSTANCE);
        PROFILES.put(id("jungle_sapling"), JungleGrowthProfile.INSTANCE);
        PROFILES.put(id("dark_oak"), DarkOakGrowthProfile.INSTANCE);
        PROFILES.put(id("dark_oak_sapling"), DarkOakGrowthProfile.INSTANCE);
        PROFILES.put(id("acacia"), AcaciaGrowthProfile.INSTANCE);
        PROFILES.put(id("acacia_sapling"), AcaciaGrowthProfile.INSTANCE);
        PROFILES.put(id("jungle_1x1"), Jungle1x1GrowthProfile.INSTANCE);
        PROFILES.put(id("cherry"), CherryGrowthProfile.INSTANCE);
        PROFILES.put(id("cherry_sapling"), CherryGrowthProfile.INSTANCE);
        PROFILES.put(id("mangrove"), MangroveGrowthProfile.INSTANCE);
        PROFILES.put(id("mangrove_propagule"), MangroveGrowthProfile.INSTANCE);
        PROFILES.put(id("brown_mushroom"), BrownMushroomGrowthProfile.INSTANCE);
        PROFILES.put(id("red_mushroom"), RedMushroomGrowthProfile.INSTANCE);
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
                sessionPos, saplingId, level.getGameTime(), stages, interval, height);

        var morphologyParams = profile.morphologyParams();
        if (morphologyParams != null) {
            session.ensureSkeleton(level, morphologyParams);
        }

        chunkData.addTreeGrowthSession(sessionPos, session);
        chunksWithSessions.add(chunk.getPos().toLong());

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Intercepted tree growth at {} (type={}), height={}, totalStages={}, morphology={}",
                sessionPos, saplingId, height, stages, morphologyParams != null);
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
    public TreeGrowthSession getSession(BlockPos pos) {
        return null; // Session lookup must go through chunk data now
    }

    @Nullable
    public TreeGrowthSession findSessionForSapling(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        TreeGrowthSession session = chunkData.getTreeGrowthSessions().get(pos);
        if (session != null) return session;
        for (int dx = 0; dx >= -1; dx--) {
            for (int dz = 0; dz >= -1; dz--) {
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
                var morphologyParams = profile.morphologyParams();
                if (morphologyParams != null) {
                    session.ensureSkeleton(level, morphologyParams);
                    var skel = session.skeleton();
                    var plan = session.stagePlan();
                    if (skel != null && plan != null) {
                        com.s.ecoflux.plant.tree.morphology.TreeMorphology.growStage(
                                level, skel, morphologyParams, plan,
                                session.currentStage(), level.getSeed(), treeRandom,
                                profile.logBlock(), profile.leavesBlock());
                    }
                    // Species-specific post-morphology behavior (e.g., mangrove prop roots)
                    profile.growStage(level, pos, session.currentStage(),
                            session.totalStages(), session.resolvedHeight(), treeRandom);
                } else {
                    profile.growStage(level, pos, session.currentStage(),
                            session.totalStages(), session.resolvedHeight(), treeRandom);
                }
                session.advanceStage(gameTime);

                EcofluxConstants.LOGGER.info(
                        "[Ecoflux] Tree growth stage {}/{} at {} (type={})",
                        session.currentStage(), session.totalStages(), pos, session.treeType());

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
        var morphologyParams = profile.morphologyParams();
        if (morphologyParams != null) {
            session.ensureSkeleton(level, morphologyParams);
            var skel = session.skeleton();
            var plan = session.stagePlan();
            if (skel != null && plan != null) {
                com.s.ecoflux.plant.tree.morphology.TreeMorphology.growStage(
                        level, skel, morphologyParams, plan,
                        session.currentStage(), level.getSeed(), treeRandom,
                        profile.logBlock(), profile.leavesBlock());
            }
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
            var morphologyParams = profile.morphologyParams();
            if (morphologyParams != null) {
                session.ensureSkeleton(level, morphologyParams);
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
            }
        } else {
            chunkData.removeVegetation(basePos);
            level.removeBlock(basePos, false);
            level.setBlock(basePos, logBlock.defaultBlockState(), 3);
        }

        ActiveVegetationRecord treeRecord = TreeStructureAdapter.INSTANCE.captureBirth(
                level,
                basePos,
                level.getBlockState(basePos),
                level.getGameTime(),
                chunkData.getCurrentBiome().map(key -> key.location()),
                chunkData.getActivePathId());
        chunkData.trackVegetation(treeRecord);

        EcofluxConstants.LOGGER.info("[Ecoflux] Tree growth complete at {}, replaced with {}", basePos, logBlock);
    }

    @Nullable
    private static TreeGrowthProfile resolveProfile(ResourceLocation saplingId) {
        TreeGrowthProfile profile = PROFILES.get(saplingId);
        if (profile != null) return profile;

        String path = saplingId.getPath();
        if (path.endsWith("_sapling")) {
            String treeName = path.substring(0, path.length() - "_sapling".length());
            return PROFILES.get(ResourceLocation.fromNamespaceAndPath(saplingId.getNamespace(), treeName));
        }
        return null;
    }
}
