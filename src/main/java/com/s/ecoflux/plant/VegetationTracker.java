package com.s.ecoflux.plant;

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.plant.PlantDefinition;
import com.s.ecoflux.config.plant.PlantRegistry;
import com.s.ecoflux.config.plant.PlantSpawnRules;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.network.VegetationVisualSyncEntry;
import com.s.ecoflux.util.TickProfiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

public final class VegetationTracker {
    public static final VegetationTracker INSTANCE = new VegetationTracker(List.of(
            SaplingAdapter.INSTANCE,
            TreeStructureAdapter.INSTANCE,
            SimplePlantAdapter.INSTANCE));

    private final List<VegetationTypeAdapter> adapters;

    public VegetationTracker(List<VegetationTypeAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public List<VegetationTypeAdapter> adapters() {
        return adapters;
    }

    public Optional<VegetationTypeAdapter> findAdapter(BlockState state) {
        return adapters.stream().filter(adapter -> adapter.matches(state)).findFirst();
    }

    public Optional<VegetationTypeAdapter> findAdapter(ResourceLocation adapterTypeId) {
        return adapters.stream().filter(adapter -> adapter.typeId().equals(adapterTypeId)).findFirst();
    }

    public String inspect(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Optional<VegetationTypeAdapter> adapter = findAdapter(state);
        if (adapter.isEmpty()) {
            return "位置 " + pos + " 没有匹配的植被适配器（" + state.getBlock().getDescriptionId() + "）。";
        }

        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        PlantDefinition plantDef = PlantRegistry.INSTANCE.getDefinition(blockId)
                .orElse(new PlantDefinition(blockId, 1, 72000L, PlantSpawnRules.EMPTY));

        ActiveVegetationRecord preview = adapter.get().captureBirth(level, pos, state, level.getGameTime(), Optional.empty(), Optional.empty(), plantDef);
        VegetationObservation observation = adapter.get().observe(level, preview, state, level.getGameTime());
        return "适配器=" + adapter.get().typeId()
                + " 阶段=" + observation.stage()
                + " 存在=" + observation.present()
                + " 积分=" + observation.currentPointValue()
                + " 详情=" + observation.detail();
    }

    public String trackAt(
            ServerLevel level,
            LevelChunk chunk,
            BlockPos pos,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId,
            PlantDefinition plantDefinition) {
        BlockState state = level.getBlockState(pos);
        Optional<VegetationTypeAdapter> adapter = findAdapter(state);
        if (adapter.isEmpty()) {
            return "位置 " + pos + " 跳过追踪：没有匹配 " + state.getBlock().getDescriptionId() + " 的植被适配器。";
        }

        ActiveVegetationRecord record = adapter.get().captureBirth(
                level,
                pos,
                state,
                level.getGameTime(),
                sourceBiomeId,
                sourcePathId,
                plantDefinition);
        chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA).trackVegetation(record);

        if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upperPos = pos.above();
            BlockState upperState = level.getBlockState(upperPos);
            Optional<VegetationTypeAdapter> upperAdapter = findAdapter(upperState);
            if (upperAdapter.isPresent()) {
                ActiveVegetationRecord upperRecord = upperAdapter.get().captureBirth(
                        level, upperPos, upperState, level.getGameTime(),
                        sourceBiomeId, sourcePathId,
                        plantDefinition);
                ActiveVegetationRecord zeroPointUpper = new ActiveVegetationRecord(
                        upperRecord.vegetationId(),
                        upperRecord.adapterType(),
                        upperRecord.position(),
                        upperRecord.lifeStage(),
                        upperRecord.birthGameTime(),
                        upperRecord.lastObservedGameTime(),
                        upperRecord.expireGameTime(),
                        0,
                        0,
                        upperRecord.sourceBiomeId(),
                        upperRecord.sourcePathId(),
                        null);
                chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA).trackVegetation(zeroPointUpper);
            }
        }

        ModNetworking.syncChunkToTracking(level, chunk);
        return "已追踪位置 " + pos + " 的植被，适配器=" + adapter.get().typeId() + "。";
    }

    public String observeTracked(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        ObserveResult result = observeTrackedInternal(level, chunk, pos);
        ModNetworking.syncChunkToTracking(level, chunk);
        return result.message();
    }

    public String observeChunk(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getVegetationRecords().isEmpty()) {
            return "区块 " + chunk.getPos() + " 跳过观察：没有已追踪植被记录。";
        }

        TickProfiler.Span span = TickProfiler.INSTANCE.start("observeChunk");
        long gameTime = level.getGameTime();
        int observeInterval = EcofluxServerConfig.observeIntervalTicks();
        List<BlockPos> snapshot = new ArrayList<>(chunkData.getVegetationRecords().keySet());
        int removed = 0;
        int transformed = 0;
        int updated = 0;
        int skipped = 0;
        for (BlockPos pos : snapshot) {
            ActiveVegetationRecord record = chunkData.getVegetationRecords().get(pos);
            if (record != null) {
                boolean needsImmediateObserve = record.lifeStage() == VegetationLifecycleStage.DEAD
                        || record.lifeStage() == VegetationLifecycleStage.TRANSFORMED;
                if (!needsImmediateObserve
                        && gameTime - record.lastObservedGameTime() < observeInterval) {
                    skipped++;
                    continue;
                }
            }

            ObserveResult result = observeTrackedInternal(level, chunk, pos);
            if (result.removed()) {
                removed++;
            } else if (result.transformed()) {
                transformed++;
            } else if (result.updated()) {
                updated++;
            }
        }

        boolean changed = updated > 0 || removed > 0 || transformed > 0;
        if (changed) {
            ModNetworking.syncChunkToTracking(level, chunk);
        }
        TickProfiler.INSTANCE.end(span);
        return "区块 " + chunk.getPos()
                + "：更新=" + updated + "，转化=" + transformed
                + "，移除=" + removed + "，跳过=" + skipped
                + "，剩余=" + chunkData.getVegetationRecords().size() + "。";
    }

    public List<VegetationVisualSyncEntry> buildVisualSyncEntries(LevelChunk chunk, long gameTime) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        List<VegetationVisualSyncEntry> entries = new ArrayList<>(chunkData.getVegetationRecords().size());
        for (ActiveVegetationRecord record : chunkData.getVegetationRecords().values()) {
            Optional<VegetationTypeAdapter> adapter = findAdapter(record.adapterType());
            if (adapter.isEmpty()) {
                continue;
            }

            VegetationVisualState visualState = adapter.get().visualState(record, gameTime);
            entries.add(new VegetationVisualSyncEntry(record.position(), visualState.stage(), visualState.stageProgress(), record.birthGameTime()));
        }
        return List.copyOf(entries);
    }

    private ObserveResult observeTrackedInternal(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = chunkData.getVegetationRecords().get(pos);
        if (record == null) {
            return ObserveResult.noop("位置 " + pos + " 跳过观察：没有已追踪植被记录。");
        }

        BlockState state = level.getBlockState(pos);
        Optional<VegetationTypeAdapter> adapter = findAdapter(record.adapterType());
        if (adapter.isEmpty()) {
            return ObserveResult.noop("位置 " + pos + " 观察失败：适配器 " + record.adapterType() + " 未注册。");
        }

        if (record.adapterType().equals(SaplingAdapter.TYPE_ID)) {
            long saplingAge = (long) ((level.getGameTime() - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
            if (saplingAge >= 1200L) {
                var handler = com.s.ecoflux.plant.tree.TreeGrowthHandler.INSTANCE;
                if (handler.findSessionForSapling(level, pos) == null) {
                    handler.interceptGrowth(level, pos, record);
                    handler.forceAdvanceStage(level, pos);
                    chunkData.removeVegetation(pos);
                    return new ObserveResult(
                            "树苗成熟，触发树木生长。位置=" + pos,
                            false, true, true);
                }
            }
        }

        VegetationObservation observation = adapter.get().observe(level, record, state, level.getGameTime());
        if (!observation.present()) {
            if (record.treeStructure() != null && !record.treeStructure().isEmpty()) {
                removeAllTreeBlocks(level, chunkData, pos, record.treeStructure());
                return new ObserveResult(
                        "已观察 " + pos + "：树木结构完全腐烂，方块已移除。",
                        true, false, false);
            }
            BlockState currentState = level.getBlockState(pos);
            if (!currentState.isAir()) {
                level.destroyBlock(pos, false);
            }
            chunkData.removeVegetation(pos);
            return new ObserveResult(
                    "已观察 " + pos + "：植被死亡或消失。详情=" + observation.detail(),
                    true, false, false);
        }

        if (observation.stage() == VegetationLifecycleStage.DEAD
                && record.treeStructure() != null
                && !record.treeStructure().isEmpty()) {
            TreeStructure reduced = processTreeDeath(level, record.treeStructure());
            if (reduced.isEmpty()) {
                chunkData.removeVegetation(pos);
                return new ObserveResult(
                        "已观察 " + pos + "：树木结构死亡，所有方块已移除。",
                        true, false, false);
            }
            chunkData.trackVegetation(record.withObservation(
                    observation.stage(), observation.currentPointValue(), level.getGameTime())
                    .withTreeStructure(reduced));
            return new ObserveResult(
                    "已观察 " + pos + "：阶段=DEAD，树木腐烂中（剩余 "
                            + reduced.totalBlocks() + " 方块）。",
                    false, false, true);
        }

        if (observation.transformation().isPresent()) {
            VegetationTransformation transformation = observation.transformation().get();
            ActiveVegetationRecord transformedRecord = record.withTransformation(
                    transformation.targetVegetationId(),
                    transformation.targetAdapterType(),
                    transformation.targetStage(),
                    transformation.targetBasePointValue(),
                    transformation.targetCurrentPointValue(),
                    level.getGameTime(),
                    transformation.targetExpireGameTime());
            chunkData.trackVegetation(transformedRecord);
            return new ObserveResult(
                    "已观察 " + pos + "：已转化为 " + transformation.targetVegetationId() + "。",
                    false,
                    true,
                    true);
        }

        boolean stageChanged = record.lifeStage() != observation.stage();
        boolean pointsChanged = record.currentPointValue() != observation.currentPointValue();
        if (stageChanged || pointsChanged) {
            chunkData.trackVegetation(record.withObservation(
                    observation.stage(),
                    observation.currentPointValue(),
                    level.getGameTime()));
        } else {
            // Only update lastObservedGameTime without triggering visual sync
            chunkData.touchVegetation(record, level.getGameTime());
        }
        return new ObserveResult(
                "已观察 " + pos + "：阶段=" + observation.stage()
                        + "，积分=" + observation.currentPointValue()
                        + "，详情=" + observation.detail(),
                false,
                false,
                true);
    }

    public String untrack(LevelChunk chunk, BlockPos pos) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord removed = chunkData.removeVegetation(pos);
        BlockPos upperPos = pos.above();
        ActiveVegetationRecord upperRemoved = chunkData.removeVegetation(upperPos);
        if ((removed != null || upperRemoved != null) && chunk.getLevel() instanceof ServerLevel serverLevel) {
            ModNetworking.syncChunkToTracking(serverLevel, chunk);
        }
        return removed == null
                ? "位置 " + pos + " 跳过取消追踪：没有已追踪植被记录。"
                : "已取消追踪位置 " + pos + " 的植被。";
    }

    private TreeStructure processTreeDeath(ServerLevel level, TreeStructure ts) {
        long[] leaves = ts.leafPositions();
        long[] logs = ts.logPositions();
        int maxRemove = Math.max(1, (leaves.length + logs.length) / 48);

        if (leaves.length > 0) {
            int removeLeaves = Math.min(maxRemove, leaves.length);
            for (int i = 0; i < removeLeaves; i++) {
                BlockPos leafPos = BlockPos.of(leaves[i]);
                BlockState leafState = level.getBlockState(leafPos);
                if (leafState.is(BlockTags.LEAVES)) {
                    level.destroyBlock(leafPos, false);
                }
            }
            long[] remainingLeaves = new long[leaves.length - removeLeaves];
            System.arraycopy(leaves, removeLeaves, remainingLeaves, 0, remainingLeaves.length);
            return new TreeStructure(logs, remainingLeaves);
        }

        if (logs.length > 0) {
            int removeLogs = Math.min(maxRemove, logs.length);
            for (int i = 0; i < removeLogs; i++) {
                BlockPos logPos = BlockPos.of(logs[i]);
                BlockState logState = level.getBlockState(logPos);
                if (logState.is(BlockTags.LOGS)) {
                    level.destroyBlock(logPos, false);
                }
            }
            long[] remainingLogs = new long[logs.length - removeLogs];
            System.arraycopy(logs, removeLogs, remainingLogs, 0, remainingLogs.length);
            return new TreeStructure(remainingLogs, new long[0]);
        }

        return ts;
    }

    private void removeAllTreeBlocks(ServerLevel level, SuccessionChunkData chunkData,
                                      BlockPos recordPos, TreeStructure ts) {
        for (long packed : ts.leafPositions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.getBlockState(pos).isAir()) {
                level.destroyBlock(pos, false);
            }
            chunkData.removeVegetation(pos);
        }
        for (long packed : ts.logPositions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.getBlockState(pos).isAir()) {
                level.destroyBlock(pos, false);
            }
            chunkData.removeVegetation(pos);
        }
        chunkData.removeVegetation(recordPos);
    }

    public String describeChunk(LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getVegetationRecords().isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有已追踪植被记录。";
        }

        long treeCount = chunkData.getVegetationRecords().values().stream()
                .filter(r -> r.treeStructure() != null && !r.treeStructure().isEmpty())
                .count();
        String joined = chunkData.getVegetationRecords().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ActiveVegetationRecord::vegetationId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                list -> list.get(0))))
                .values().stream()
                .map(record -> {
                    String extra = record.treeStructure() != null && !record.treeStructure().isEmpty()
                            ? "[树:" + record.treeStructure().totalBlocks() + "]"
                            : "";
                    long count = chunkData.getVegetationRecords().values().stream()
                            .filter(r -> r.vegetationId().equals(record.vegetationId())).count();
                    return record.vegetationId() + "×" + count + extra;
                })
                .reduce((left, right) -> left + ", " + right)
                .orElse("无");
        return "区块 " + chunk.getPos()
                + " 已追踪植被=" + chunkData.getVegetationRecords().size()
                + " 总积分=" + chunkData.getTotalVegetationPoints()
                + " 树木=" + treeCount
                + " [" + joined + "]";
    }

    public String advanceStage(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = chunkData.getVegetationRecords().get(pos);
        if (record == null) {
            return "位置 " + pos + " 没有已追踪植被。";
        }

        BlockState state = level.getBlockState(pos);
        Optional<VegetationTypeAdapter> adapter = findAdapter(record.adapterType());
        if (adapter.isEmpty()) {
            return "位置 " + pos + " 的适配器未注册。";
        }

        VegetationObservation obs = adapter.get().observe(level, record, state, level.getGameTime());
        if (!obs.present()) {
            return "位置 " + pos + " 的植被已不存在。";
        }

        long now = level.getGameTime();
        float speed = SuccessionSpeedConfig.getSpeedMultiplier();
        long newBirth = record.birthGameTime();
        long newExpire = record.expireGameTime();

        String nextStageName = switch (obs.stage()) {
            case BORN -> {
                newBirth = now - (long) (200L / speed);
                yield "GROWING";
            }
            case JUVENILE -> {
                newBirth = now - (long) (1200L / speed);
                yield "GROWING";
            }
            case GROWING -> {
                if (record.adapterType().equals(SaplingAdapter.TYPE_ID)) {
                    newExpire = now - 1;
                    yield "DEAD";
                }
                newBirth = now - (long) (1200L / speed);
                yield "MATURE";
            }
            case MATURE -> {
                long threshold = record.adapterType().equals(TreeStructureAdapter.TYPE_ID) ? 96000L : 48000L;
                newBirth = now - (long) (threshold / speed);
                yield "AGING";
            }
            case AGING -> {
                newExpire = now - 1;
                yield "DEAD";
            }
            case DEAD -> {
                newExpire = -100000L;
                yield "REMOVED";
            }
            case TRANSFORMED -> {
                yield "TRANSFORMED";
            }
        };

        ActiveVegetationRecord updated = new ActiveVegetationRecord(
                record.vegetationId(),
                record.adapterType(),
                record.position(),
                record.lifeStage(),
                newBirth,
                now,
                newExpire,
                record.basePointValue(),
                record.currentPointValue(),
                record.sourceBiomeId(),
                record.sourcePathId(),
                record.treeStructure());
        chunkData.trackVegetation(updated);
        ModNetworking.syncChunkToTracking(level, chunk);

        String result = observeTracked(level, chunk, pos);
        return "点击推进 " + pos.toShortString() + " → " + nextStageName + " | " + result;
    }

    public String forceKill(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = chunkData.getVegetationRecords().get(pos);
        if (record == null) {
            return "位置 " + pos + " 跳过强制死亡：没有已追踪植被记录。";
        }

        long now = level.getGameTime();
        ActiveVegetationRecord killed = new ActiveVegetationRecord(
                record.vegetationId(),
                record.adapterType(),
                record.position(),
                VegetationLifecycleStage.AGING,
                record.birthGameTime(),
                now,
                now - 1,
                record.basePointValue(),
                Math.max(1, record.currentPointValue()),
                record.sourceBiomeId(),
                record.sourcePathId(),
                record.treeStructure());
        chunkData.trackVegetation(killed);
        ModNetworking.syncChunkToTracking(level, chunk);

        return observeTracked(level, chunk, pos);
    }

    private record ObserveResult(String message, boolean removed, boolean transformed, boolean updated) {
        private static ObserveResult noop(String message) {
            return new ObserveResult(message, false, false, false);
        }
    }
}
