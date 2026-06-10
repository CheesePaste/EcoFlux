package com.s.ecoflux.plant.tree;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.plant.TreeStructureAdapter;
import com.s.ecoflux.plant.tree.profiles.AcaciaGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.BirchGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.BrownMushroomGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.DarkOakGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.JungleGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.OakGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.RedMushroomGrowthProfile;
import com.s.ecoflux.plant.tree.profiles.SpruceGrowthProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        PROFILES.put(id("brown_mushroom"), BrownMushroomGrowthProfile.INSTANCE);
        PROFILES.put(id("red_mushroom"), RedMushroomGrowthProfile.INSTANCE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private final Map<BlockPos, TreeGrowthSession> activeGrowths = new HashMap<>();

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
            if (nwCorner == null) return;
            sessionPos = nwCorner;
        }

        if (activeGrowths.containsKey(sessionPos)) return;

        int height = profile.resolveHeight(level.random);
        int stages = profile.totalStagesForHeight(height);
        int interval = profile.ticksPerStage();

        TreeGrowthSession session = new TreeGrowthSession(
                sessionPos, saplingId, level.getGameTime(), stages, interval, height);

        var morphologyParams = profile.morphologyParams();
        if (morphologyParams != null) {
            session.ensureSkeleton(level, morphologyParams);
        }

        activeGrowths.put(sessionPos, session);

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Intercepted tree growth at {} (type={}), height={}, totalStages={}, morphology={}",
                sessionPos, saplingId, height, stages, morphologyParams != null);
    }

    public void interceptMushroomGrowth(ServerLevel level, BlockPos pos, BlockState state) {
        ResourceLocation mushroomId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        TreeGrowthProfile profile = resolveProfile(mushroomId);
        if (profile == null) return;

        BlockPos sessionPos = pos.immutable();
        if (activeGrowths.containsKey(sessionPos)) return;

        int height = profile.resolveHeight(level.random);
        int stages = profile.totalStagesForHeight(height);
        int interval = profile.ticksPerStage();

        TreeGrowthSession session = new TreeGrowthSession(
                sessionPos, mushroomId, level.getGameTime(), stages, interval, height);

        activeGrowths.put(sessionPos, session);

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Intercepted mushroom growth at {} (type={}), height={}, totalStages={}",
                sessionPos, mushroomId, height, stages);
    }

    @Nullable
    public TreeGrowthSession getSession(BlockPos pos) {
        return activeGrowths.get(pos);
    }

    @Nullable
    public TreeGrowthSession findSessionForSapling(BlockPos pos) {
        TreeGrowthSession session = activeGrowths.get(pos);
        if (session != null) return session;
        for (int dx = 0; dx >= -1; dx--) {
            for (int dz = 0; dz >= -1; dz--) {
                session = activeGrowths.get(new BlockPos(pos.getX() + dx, pos.getY(), pos.getZ() + dz));
                if (session != null) return session;
            }
        }
        return null;
    }

    public void removeSession(BlockPos pos) {
        activeGrowths.remove(pos);
    }

    public Map<BlockPos, TreeGrowthSession> activeGrowths() {
        return activeGrowths;
    }

    public boolean hasActiveSessions() {
        return !activeGrowths.isEmpty();
    }

    public void tickAll(ServerLevel level) {
        if (activeGrowths.isEmpty()) return;

        long gameTime = level.getGameTime();
        List<BlockPos> completed = new ArrayList<>();

        for (Map.Entry<BlockPos, TreeGrowthSession> entry : activeGrowths.entrySet()) {
            BlockPos pos = entry.getKey();
            TreeGrowthSession session = entry.getValue();

            if (session.isComplete()) {
                completed.add(pos);
                continue;
            }

            if (gameTime - session.lastStageTime() < session.ticksPerStage()) continue;

            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;

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
            List<GrowthPlacement> placed = null;
            var morphologyParams = profile.morphologyParams();
            if (morphologyParams != null) {
                session.ensureSkeleton(level, morphologyParams);
                var skel = session.skeleton();
                var plan = session.stagePlan();
                if (skel != null && plan != null) {
                    placed = com.s.ecoflux.plant.tree.morphology.TreeMorphology.growStage(
                            level, skel, morphologyParams, plan,
                            session.currentStage(), level.getSeed(), treeRandom,
                            profile.logBlock(), profile.leavesBlock());
                }
            } else {
                placed = profile.growStage(level, pos, session.currentStage(),
                        session.totalStages(), session.resolvedHeight(), treeRandom);
            }
            session.advanceStage(gameTime);

            if (placed != null && !placed.isEmpty()) {
                ModNetworking.sendGrowthAnimation(level, chunk, placed);
            }

            EcofluxConstants.LOGGER.info(
                    "[Ecoflux] Tree growth stage {}/{} at {} (type={})",
                    session.currentStage(), session.totalStages(), pos, session.treeType());

            if (session.isComplete()) {
                completed.add(pos);
                onGrowthComplete(level, chunk, pos);
            }
        }

        for (BlockPos pos : completed) {
            activeGrowths.remove(pos);
        }
    }

    public boolean forceAdvanceStage(ServerLevel level, BlockPos pos) {
        TreeGrowthSession session = findSessionForSapling(pos);
        if (session == null || session.isComplete()) return false;

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;

        TreeGrowthProfile profile = resolveProfile(session.treeType());
        if (profile == null) return false;

        if (!profile.canGrowStage(level, session.saplingPos(), session.currentStage(),
                session.totalStages(), session.resolvedHeight())) return false;

        List<GrowthPlacement> placed = null;
        RandomSource treeRandom = TreeShapeUtils.positionRandom(session.saplingPos(), level.getSeed());
        var morphologyParams = profile.morphologyParams();
        if (morphologyParams != null) {
            session.ensureSkeleton(level, morphologyParams);
            var skel = session.skeleton();
            var plan = session.stagePlan();
            if (skel != null && plan != null) {
                placed = com.s.ecoflux.plant.tree.morphology.TreeMorphology.growStage(
                        level, skel, morphologyParams, plan,
                        session.currentStage(), level.getSeed(), treeRandom,
                        profile.logBlock(), profile.leavesBlock());
            }
        } else {
            placed = profile.growStage(level, session.saplingPos(), session.currentStage(),
                    session.totalStages(), session.resolvedHeight(), treeRandom);
        }
        session.advanceStage(level.getGameTime());

        if (placed != null && !placed.isEmpty()) {
            ModNetworking.sendGrowthAnimation(level, chunk, placed);
        }

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Tree bone-mealed stage {}/{} at {} (type={})",
                session.currentStage(), session.totalStages(), session.saplingPos(), session.treeType());

        if (session.isComplete()) {
            onGrowthComplete(level, chunk, session.saplingPos());
            activeGrowths.remove(session.saplingPos());
        }

        return true;
    }

    private void onGrowthComplete(ServerLevel level, LevelChunk chunk, BlockPos basePos) {
        var chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        TreeGrowthSession session = activeGrowths.get(basePos);
        TreeGrowthProfile profile = session != null ? resolveProfile(session.treeType()) : null;
        Block logBlock = profile != null ? profile.logBlock() : Blocks.OAK_LOG;

        if (profile != null && profile.is2x2()) {
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
