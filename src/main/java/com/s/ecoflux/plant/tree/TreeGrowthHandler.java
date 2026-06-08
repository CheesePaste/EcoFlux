package com.s.ecoflux.plant.tree;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.plant.TreeStructureAdapter;
import com.s.ecoflux.plant.VegetationTracker;
import com.s.ecoflux.plant.tree.profiles.OakGrowthProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

public final class TreeGrowthHandler {
    public static final TreeGrowthHandler INSTANCE = new TreeGrowthHandler();

    private static final Map<ResourceLocation, TreeGrowthProfile> PROFILES = new HashMap<>();
    static {
        PROFILES.put(ResourceLocation.withDefaultNamespace("oak"), OakGrowthProfile.INSTANCE);
        PROFILES.put(ResourceLocation.withDefaultNamespace("oak_sapling"), OakGrowthProfile.INSTANCE);
    }

    private final Map<BlockPos, TreeGrowthSession> activeGrowths = new HashMap<>();

    private TreeGrowthHandler() {
    }

    public void interceptGrowth(ServerLevel level, BlockPos pos, ActiveVegetationRecord record) {
        if (activeGrowths.containsKey(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        ResourceLocation saplingId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        TreeGrowthProfile profile = resolveProfile(saplingId);
        int stages = profile != null ? profile.totalStages() : 5;
        int interval = profile != null ? profile.ticksPerStage() : 40;

        TreeGrowthSession session = new TreeGrowthSession(
                pos.immutable(),
                saplingId,
                level.getGameTime(),
                stages,
                interval);
        activeGrowths.put(pos.immutable(), session);

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Intercepted tree growth at {} (type={}), totalStages={}, ticksPerStage={}",
                pos, saplingId, stages, interval);
    }

    @Nullable
    public TreeGrowthSession getSession(BlockPos pos) {
        return activeGrowths.get(pos);
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
        if (activeGrowths.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        List<BlockPos> completed = new ArrayList<>();

        for (Map.Entry<BlockPos, TreeGrowthSession> entry : activeGrowths.entrySet()) {
            BlockPos pos = entry.getKey();
            TreeGrowthSession session = entry.getValue();

            if (session.isComplete()) {
                completed.add(pos);
                continue;
            }

            long elapsed = gameTime - session.lastStageTime();
            if (elapsed < session.ticksPerStage()) {
                if (elapsed == 1 || elapsed % 20 == 0) {
                    EcofluxConstants.LOGGER.info(
                            "[Ecoflux] Tree growth waiting at {}: stage={}/{}, elapsed={}/{}",
                            pos, session.currentStage(), session.totalStages(),
                            elapsed, session.ticksPerStage());
                }
                continue;
            }

            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }

            TreeGrowthProfile profile = resolveProfile(session.treeType());
            if (profile == null) {
                completed.add(pos);
                EcofluxConstants.LOGGER.warn("[Ecoflux] No growth profile for tree type: {}", session.treeType());
                continue;
            }

            if (!profile.canGrowStage(level, pos, session.currentStage())) {
                continue;
            }

            List<GrowthPlacement> placed = profile.growStage(level, pos, session.currentStage());
            session.advanceStage(gameTime);

            if (!placed.isEmpty()) {
                Map<Byte, List<BlockPos>> byType = new HashMap<>();
                for (GrowthPlacement p : placed) {
                    byType.computeIfAbsent(p.animType(), k -> new ArrayList<>()).add(p.pos());
                }
                for (Map.Entry<Byte, List<BlockPos>> e : byType.entrySet()) {
                    ModNetworking.sendGrowthAnimation(level, chunk, e.getValue(), e.getKey());
                }
            }

            EcofluxConstants.LOGGER.info(
                    "[Ecoflux] Tree growth stage {}/{} at {} (type={}), placed {} blocks",
                    session.currentStage(), session.totalStages(), pos, session.treeType(), placed.size());

            if (session.isComplete()) {
                completed.add(pos);
                onGrowthComplete(level, chunk, pos);
            }
        }

        for (BlockPos pos : completed) {
            activeGrowths.remove(pos);
        }
    }

    private void onGrowthComplete(ServerLevel level, LevelChunk chunk, BlockPos saplingPos) {
        var chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        chunkData.removeVegetation(saplingPos);

        level.setBlock(saplingPos, Blocks.OAK_LOG.defaultBlockState(), 3);

        // Animate the sapling→log transition on the client
        ModNetworking.sendGrowthAnimation(level, chunk,
                List.of(saplingPos.immutable()), (byte) 0); // TRUNK_EXTRUDE

        ActiveVegetationRecord treeRecord = TreeStructureAdapter.INSTANCE.captureBirth(
                level,
                saplingPos,
                level.getBlockState(saplingPos),
                level.getGameTime(),
                chunkData.getCurrentBiome().map(key -> key.location()),
                chunkData.getActivePathId());
        chunkData.trackVegetation(treeRecord);

        EcofluxConstants.LOGGER.info("[Ecoflux] Tree growth complete at {}, sapling replaced with log", saplingPos);
    }

    @Nullable
    private static TreeGrowthProfile resolveProfile(ResourceLocation saplingId) {
        TreeGrowthProfile profile = PROFILES.get(saplingId);
        if (profile != null) {
            return profile;
        }

        String path = saplingId.getPath();
        if (path.endsWith("_sapling")) {
            String treeName = path.substring(0, path.length() - "_sapling".length());
            return PROFILES.get(ResourceLocation.fromNamespaceAndPath(saplingId.getNamespace(), treeName));
        }
        return null;
    }
}
