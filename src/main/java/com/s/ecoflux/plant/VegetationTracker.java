package com.s.ecoflux.plant;

/**
 * Central singleton that tracks, observes, and synchronizes all vegetation in
 * the world.
 *
 * <p>Structure: holds a registry of {@link VegetationTypeAdapter}
 * implementations and provides the main entry points — {@link #trackAt}
 * records a plant at a position (with automatic tracking of upper halves for
 * double-height plants at 0 points), {@link #observeTracked} advances a
 * single plant's lifecycle, {@link #observeChunk} advances all plants in a
 * chunk, and {@link #buildVisualSyncEntries} packages visual state for
 * network transmission. Lookup methods {@link #findAdapter(BlockState)} and
 * {@link #findAdapter(ResourceLocation)} resolve the correct adapter by block
 * or type ID.
 *
 * <p>Role in Ecoflux: {@code VegetationTracker} is the hub of the plant
 * lifecycle subsystem. It is constructed once at startup with the three
 * concrete adapters (sapling, tree structure, simple plant), then invoked
 * from {@link com.s.ecoflux.plant.PlantSpawner},
 * {@link com.s.ecoflux.succession.SuccessionService}, and chunk event
 * handlers. All vegetation observations flow through this class, keeping
 * lifecycle logic centralized and adapter-agnostic.
 */

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.network.VegetationVisualSyncEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

        ActiveVegetationRecord preview = adapter.get().captureBirth(level, pos, state, level.getGameTime(), Optional.empty(), Optional.empty());
        VegetationObservation observation = adapter.get().observe(level, preview, state, level.getGameTime());
        return "适配器=" + adapter.get().typeId()
                + " 分类=" + adapter.get().category()
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
            Optional<ResourceLocation> sourcePathId) {
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
                sourcePathId);
        chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA).trackVegetation(record);

        // Also track upper half of double-height plants so both halves scale together
        if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upperPos = pos.above();
            BlockState upperState = level.getBlockState(upperPos);
            Optional<VegetationTypeAdapter> upperAdapter = findAdapter(upperState);
            if (upperAdapter.isPresent()) {
                ActiveVegetationRecord upperRecord = upperAdapter.get().captureBirth(
                        level, upperPos, upperState, level.getGameTime(),
                        sourceBiomeId, sourcePathId);
                ActiveVegetationRecord zeroPointUpper = new ActiveVegetationRecord(
                        upperRecord.vegetationId(),
                        upperRecord.adapterType(),
                        upperRecord.category(),
                        upperRecord.position(),
                        upperRecord.lifeStage(),
                        upperRecord.birthGameTime(),
                        upperRecord.lastObservedGameTime(),
                        upperRecord.expireGameTime(),
                        0,
                        0,
                        upperRecord.sourceBiomeId(),
                        upperRecord.sourcePathId());
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

        List<BlockPos> snapshot = new ArrayList<>(chunkData.getVegetationRecords().keySet());
        int removed = 0;
        int transformed = 0;
        int updated = 0;
        for (BlockPos pos : snapshot) {
            ObserveResult result = observeTrackedInternal(level, chunk, pos);
            if (result.removed()) {
                removed++;
            } else if (result.transformed()) {
                transformed++;
            } else if (result.updated()) {
                updated++;
            }
        }

        ModNetworking.syncChunkToTracking(level, chunk);
        return "已观察区块 " + chunk.getPos()
                + "：更新=" + updated
                + "，转化=" + transformed
                + "，移除=" + removed
                + "，剩余追踪=" + chunkData.getVegetationRecords().size() + "。";
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

        VegetationObservation observation = adapter.get().observe(level, record, state, level.getGameTime());
        if (!observation.present()) {
            chunkData.removeVegetation(pos);
            return new ObserveResult(
                    "已观察 " + pos + "：植被死亡或消失。详情=" + observation.detail(),
                    true,
                    false,
                    false);
        }

        if (observation.transformation().isPresent()) {
            VegetationTransformation transformation = observation.transformation().get();
            ActiveVegetationRecord transformedRecord = record.withTransformation(
                    transformation.targetVegetationId(),
                    transformation.targetAdapterType(),
                    transformation.targetCategory(),
                    transformation.targetStage(),
                    transformation.targetBasePointValue(),
                    transformation.targetCurrentPointValue(),
                    level.getGameTime());
            chunkData.trackVegetation(transformedRecord);
            return new ObserveResult(
                    "已观察 " + pos + "：已转化为 " + transformation.targetVegetationId() + "。",
                    false,
                    true,
                    true);
        }

        chunkData.trackVegetation(record.withObservation(
                observation.stage(),
                observation.currentPointValue(),
                level.getGameTime()));
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
        // Also untrack upper half of double plants
        BlockPos upperPos = pos.above();
        ActiveVegetationRecord upperRemoved = chunkData.removeVegetation(upperPos);
        if ((removed != null || upperRemoved != null) && chunk.getLevel() instanceof ServerLevel serverLevel) {
            ModNetworking.syncChunkToTracking(serverLevel, chunk);
        }
        return removed == null
                ? "位置 " + pos + " 跳过取消追踪：没有已追踪植被记录。"
                : "已取消追踪位置 " + pos + " 的植被。";
    }

    public String describeChunk(LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getVegetationRecords().isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有已追踪植被记录。";
        }

        String joined = chunkData.getVegetationRecords().values().stream()
                .limit(8)
                .map(record -> record.position() + ":" + record.vegetationId() + ":" + record.lifeStage() + ":" + record.currentPointValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("无");
        return "区块 " + chunk.getPos()
                + " 已追踪植被=" + chunkData.getVegetationRecords().size()
                + " 总积分=" + chunkData.getTotalVegetationPoints()
                + " 样本=[" + joined + "]";
    }

    private record ObserveResult(String message, boolean removed, boolean transformed, boolean updated) {
        private static ObserveResult noop(String message) {
            return new ObserveResult(message, false, false, false);
        }
    }
}
