package com.s.ecoflux.attachment;

import com.s.ecoflux.plant.VegetationLifecycleStage;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

public final class SuccessionChunkData implements INBTSerializable<CompoundTag> {
    private static final String CURRENT_BIOME = "current_biome";
    private static final String TARGET_BIOME = "target_biome";
    private static final String PREVIOUS_BIOME = "previous_biome";
    private static final String ACTIVE_PATH_ID = "active_path_id";
    private static final String PROGRESS = "progress";
    private static final String CONSUMING_VALUE = "consuming_value";
    private static final String MAX_PLANT_COUNT = "max_plant_count";
    private static final String CURRENT_PLANT_COUNT = "current_plant_count";
    private static final String LAST_EVALUATION_GAME_TIME = "last_evaluation_game_time";
    private static final String PLANT_QUEUE = "plant_queue";
    private static final String VEGETATION_RECORDS = "vegetation_records";
    private static final String ACTIVE_PLANTS = "active_plants";

    private static final Set<VegetationLifecycleStage> CONTRIBUTING_STAGES = Set.of(
            VegetationLifecycleStage.GROWING,
            VegetationLifecycleStage.MATURE,
            VegetationLifecycleStage.AGING);

    private final ChunkAccess owner;
    private @Nullable ResourceKey<Biome> currentBiome;
    private @Nullable ResourceKey<Biome> targetBiome;
    private @Nullable ResourceKey<Biome> previousBiome;
    private @Nullable ResourceLocation activePathId;
    private double progress;
    private int consumingValue;
    private int maxPlantCount;
    private long lastEvaluationGameTime;
    private final Deque<PlantQueueEntry> plantQueue = new ArrayDeque<>();
    private final Map<BlockPos, ActiveVegetationRecord> vegetationRecords = new LinkedHashMap<>();

    public SuccessionChunkData(ChunkAccess owner) {
        this.owner = owner;
    }

    public Optional<ResourceKey<Biome>> getCurrentBiome() {
        return Optional.ofNullable(currentBiome);
    }

    public void setCurrentBiome(@Nullable ResourceKey<Biome> currentBiome) {
        this.currentBiome = currentBiome;
        markDirty();
    }

    public Optional<ResourceKey<Biome>> getTargetBiome() {
        return Optional.ofNullable(targetBiome);
    }

    public void setTargetBiome(@Nullable ResourceKey<Biome> targetBiome) {
        this.targetBiome = targetBiome;
        markDirty();
    }

    public Optional<ResourceKey<Biome>> getPreviousBiome() {
        return Optional.ofNullable(previousBiome);
    }

    public void setPreviousBiome(@Nullable ResourceKey<Biome> previousBiome) {
        this.previousBiome = previousBiome;
        markDirty();
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
        markDirty();
    }

    public Optional<ResourceLocation> getActivePathId() {
        return Optional.ofNullable(activePathId);
    }

    public void setActivePathId(@Nullable ResourceLocation activePathId) {
        this.activePathId = activePathId;
        markDirty();
    }

    public int getConsumingValue() {
        return consumingValue;
    }

    public void setConsumingValue(int consumingValue) {
        this.consumingValue = consumingValue;
        markDirty();
    }

    public int getMaxPlantCount() {
        return maxPlantCount;
    }

    public void setMaxPlantCount(int maxPlantCount) {
        this.maxPlantCount = maxPlantCount;
        markDirty();
    }

    public int getCurrentPlantCount() {
        return vegetationRecords.size();
    }

    public long getLastEvaluationGameTime() {
        return lastEvaluationGameTime;
    }

    public void setLastEvaluationGameTime(long lastEvaluationGameTime) {
        this.lastEvaluationGameTime = lastEvaluationGameTime;
        markDirty();
    }

    public Collection<PlantQueueEntry> getPlantQueue() {
        return plantQueue;
    }

    public void replacePlantQueue(Collection<PlantQueueEntry> entries) {
        plantQueue.clear();
        plantQueue.addAll(entries);
        markDirty();
    }

    public void enqueuePlant(PlantQueueEntry entry) {
        plantQueue.addLast(entry);
        markDirty();
    }

    public Optional<PlantQueueEntry> pollPlant() {
        PlantQueueEntry entry = plantQueue.pollFirst();
        if (entry != null) {
            markDirty();
        }
        return Optional.ofNullable(entry);
    }

    public Map<BlockPos, ActiveVegetationRecord> getVegetationRecords() {
        return vegetationRecords;
    }

    public int getTotalVegetationPoints() {
        return vegetationRecords.values().stream().mapToInt(ActiveVegetationRecord::currentPointValue).sum();
    }

    public int getContributingVegetationPoints() {
        return vegetationRecords.values().stream()
                .filter(record -> CONTRIBUTING_STAGES.contains(record.lifeStage()))
                .mapToInt(ActiveVegetationRecord::currentPointValue)
                .sum();
    }

    public boolean hasContributingVegetation() {
        return vegetationRecords.values().stream()
                .anyMatch(record -> CONTRIBUTING_STAGES.contains(record.lifeStage()));
    }

    public long countContributingVegetation() {
        return vegetationRecords.values().stream()
                .filter(record -> CONTRIBUTING_STAGES.contains(record.lifeStage()))
                .count();
    }

    public void trackVegetation(ActiveVegetationRecord record) {
        vegetationRecords.put(record.position(), record);
        markDirty();
    }

    public @Nullable ActiveVegetationRecord removeVegetation(BlockPos pos) {
        ActiveVegetationRecord removed = vegetationRecords.remove(pos);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public void clearVegetationRecords() {
        vegetationRecords.clear();
        markDirty();
    }

    public void clearRuntimeState() {
        activePathId = null;
        progress = 0.0D;
        lastEvaluationGameTime = 0L;
        plantQueue.clear();
        vegetationRecords.clear();
        markDirty();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        writeBiomeKey(tag, CURRENT_BIOME, currentBiome);
        writeBiomeKey(tag, TARGET_BIOME, targetBiome);
        writeBiomeKey(tag, PREVIOUS_BIOME, previousBiome);
        if (activePathId != null) {
            tag.putString(ACTIVE_PATH_ID, activePathId.toString());
        }
        tag.putDouble(PROGRESS, progress);
        tag.putInt(CONSUMING_VALUE, consumingValue);
        tag.putInt(MAX_PLANT_COUNT, maxPlantCount);
        tag.putInt(CURRENT_PLANT_COUNT, vegetationRecords.size());
        tag.putLong(LAST_EVALUATION_GAME_TIME, lastEvaluationGameTime);

        ListTag queueTag = new ListTag();
        for (PlantQueueEntry entry : plantQueue) {
            queueTag.add(entry.toTag());
        }
        tag.put(PLANT_QUEUE, queueTag);

        ListTag vegetationTag = new ListTag();
        for (ActiveVegetationRecord record : vegetationRecords.values()) {
            vegetationTag.add(record.toTag());
        }
        tag.put(VEGETATION_RECORDS, vegetationTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        currentBiome = readBiomeKey(tag, CURRENT_BIOME);
        targetBiome = readBiomeKey(tag, TARGET_BIOME);
        previousBiome = readBiomeKey(tag, PREVIOUS_BIOME);
        String storedPathId = tag.getString(ACTIVE_PATH_ID);
        activePathId = storedPathId.isEmpty() ? null : ResourceLocation.parse(storedPathId);
        progress = tag.getDouble(PROGRESS);
        consumingValue = tag.getInt(CONSUMING_VALUE);
        maxPlantCount = tag.getInt(MAX_PLANT_COUNT);
        lastEvaluationGameTime = tag.getLong(LAST_EVALUATION_GAME_TIME);

        plantQueue.clear();
        ListTag queueTag = tag.getList(PLANT_QUEUE, Tag.TAG_COMPOUND);
        for (Tag entryTag : queueTag) {
            plantQueue.addLast(PlantQueueEntry.fromTag((CompoundTag) entryTag));
        }

        vegetationRecords.clear();
        ListTag vegetationTag = tag.getList(VEGETATION_RECORDS, Tag.TAG_COMPOUND);
        for (Tag recordTag : vegetationTag) {
            ActiveVegetationRecord record = ActiveVegetationRecord.fromTag((CompoundTag) recordTag);
            vegetationRecords.put(record.position(), record);
        }

        markDirty();
    }

    private void markDirty() {
        owner.setUnsaved(true);
    }

    private static void writeBiomeKey(CompoundTag tag, String key, @Nullable ResourceKey<Biome> biomeKey) {
        if (biomeKey != null) {
            tag.putString(key, biomeKey.location().toString());
        }
    }

    private static @Nullable ResourceKey<Biome> readBiomeKey(CompoundTag tag, String key) {
        String biomeId = tag.getString(key);
        if (biomeId.isEmpty()) {
            return null;
        }
        return ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
    }
}
