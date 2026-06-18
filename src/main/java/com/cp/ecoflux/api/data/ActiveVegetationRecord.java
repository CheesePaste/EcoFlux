package com.cp.ecoflux.api.data;

import com.cp.ecoflux.plant.TreeStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ActiveVegetationRecord(
        ResourceLocation vegetationId,
        ResourceLocation adapterType,
        BlockPos position,
        VegetationLifecycleStage lifeStage,
        long birthGameTime,
        long lastObservedGameTime,
        long expireGameTime,
        int basePointValue,
        int currentPointValue,
        @Nullable ResourceLocation sourceBiomeId,
        @Nullable ResourceLocation sourcePathId,
        @Nullable TreeStructure treeStructure) {
    private static final String VEGETATION_ID = "vegetation_id";
    private static final String ADAPTER_TYPE = "adapter_type";
    private static final String POSITION = "position";
    private static final String LIFE_STAGE = "life_stage";
    private static final String BIRTH_GAME_TIME = "birth_game_time";
    private static final String LAST_OBSERVED_GAME_TIME = "last_observed_game_time";
    private static final String EXPIRE_GAME_TIME = "expire_game_time";
    private static final String BASE_POINT_VALUE = "base_point_value";
    private static final String CURRENT_POINT_VALUE = "current_point_value";
    private static final String SOURCE_BIOME_ID = "source_biome_id";
    private static final String SOURCE_PATH_ID = "source_path_id";
    private static final String TREE_STRUCTURE = "tree_structure";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(VEGETATION_ID, vegetationId.toString());
        tag.putString(ADAPTER_TYPE, adapterType.toString());
        tag.putLong(POSITION, position.asLong());
        tag.putString(LIFE_STAGE, lifeStage.name());
        tag.putLong(BIRTH_GAME_TIME, birthGameTime);
        tag.putLong(LAST_OBSERVED_GAME_TIME, lastObservedGameTime);
        tag.putLong(EXPIRE_GAME_TIME, expireGameTime);
        tag.putInt(BASE_POINT_VALUE, basePointValue);
        tag.putInt(CURRENT_POINT_VALUE, currentPointValue);
        if (sourceBiomeId != null) {
            tag.putString(SOURCE_BIOME_ID, sourceBiomeId.toString());
        }
        if (sourcePathId != null) {
            tag.putString(SOURCE_PATH_ID, sourcePathId.toString());
        }
        // TreeStructure is NOT persisted to NBT. It is rebuilt on-demand via BFS
        // from the root position when needed for death processing. This avoids
        // massive NBT bloat (hundreds of KB per chunk in dense forests).
        return tag;
    }

    public ActiveVegetationRecord withObservation(VegetationLifecycleStage nextStage, int nextPointValue, long observedGameTime) {
        return new ActiveVegetationRecord(
                vegetationId, adapterType, position,
                nextStage, birthGameTime, observedGameTime, expireGameTime,
                basePointValue, nextPointValue, sourceBiomeId, sourcePathId,
                treeStructure);
    }

    public ActiveVegetationRecord withTransformation(
            ResourceLocation nextVegetationId,
            ResourceLocation nextAdapterType,
            VegetationLifecycleStage nextStage,
            int nextBasePointValue,
            int nextCurrentPointValue,
            long observedGameTime,
            long expireGameTime) {
        return new ActiveVegetationRecord(
                nextVegetationId, nextAdapterType, position,
                nextStage, observedGameTime, observedGameTime, expireGameTime,
                nextBasePointValue, nextCurrentPointValue, sourceBiomeId, sourcePathId,
                treeStructure);
    }

    public ActiveVegetationRecord withTreeStructure(TreeStructure ts) {
        return new ActiveVegetationRecord(
                vegetationId, adapterType, position,
                lifeStage, birthGameTime, lastObservedGameTime, expireGameTime,
                basePointValue, currentPointValue, sourceBiomeId, sourcePathId, ts);
    }

    public static ActiveVegetationRecord fromTag(CompoundTag tag) {
        String sourceBiome = tag.getString(SOURCE_BIOME_ID);
        String sourcePath = tag.getString(SOURCE_PATH_ID);
        // TreeStructure is no longer persisted — rebuilt on demand via BFS from root
        return new ActiveVegetationRecord(
                ResourceLocation.parse(tag.getString(VEGETATION_ID)),
                ResourceLocation.parse(tag.getString(ADAPTER_TYPE)),
                BlockPos.of(tag.getLong(POSITION)),
                VegetationLifecycleStage.valueOf(tag.getString(LIFE_STAGE)),
                tag.getLong(BIRTH_GAME_TIME),
                tag.getLong(LAST_OBSERVED_GAME_TIME),
                tag.getLong(EXPIRE_GAME_TIME),
                tag.getInt(BASE_POINT_VALUE),
                tag.getInt(CURRENT_POINT_VALUE),
                sourceBiome.isEmpty() ? null : ResourceLocation.parse(sourceBiome),
                sourcePath.isEmpty() ? null : ResourceLocation.parse(sourcePath),
                null);
    }
}
