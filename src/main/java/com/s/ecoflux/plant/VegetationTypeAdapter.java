package com.s.ecoflux.plant;

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.config.PlantDefinition;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public interface VegetationTypeAdapter {
    ResourceLocation typeId();

    boolean matches(BlockState state);

    ActiveVegetationRecord captureBirth(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            long gameTime,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId,
            PlantDefinition plantDefinition);

    VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime);

    default VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        return new VegetationVisualState(record.lifeStage(), 1.0F);
    }

    default Optional<VegetationTransformation> detectTransformation(
            ServerLevel level,
            ActiveVegetationRecord record,
            BlockState state,
            long gameTime) {
        return Optional.empty();
    }

    static float progress(long age, long start, long endExclusive) {
        if (endExclusive <= start) {
            return 1.0F;
        }
        return (float) (Math.max(0L, age - start)) / (float) (endExclusive - start);
    }
}
