package com.s.ecoflux.plant;

/**
 * Core interface for the adapter-based plant recognition system.
 *
 * <p>Structure: defines the contract all vegetation adapters must fulfill —
 * {@link #matches(BlockState)} identifies supported plants, {@link #captureBirth}
 * records initial state into an {@link ActiveVegetationRecord},
 * {@link #observe} advances the lifecycle each tick, and {@link #visualState}
 * computes render parameters for client sync. Default methods provide optional
 * {@link #detectTransformation} for sapling-to-tree conversion and a static
 * {@link #progress} helper for normalized stage interpolation.
 *
 * <p>Role in Ecoflux: every plant type (flowers, grass, saplings, trees,
 * mushrooms) is handled by a dedicated implementation of this interface.
 * {@link VegetationTracker} holds the registry of adapters and delegates all
 * per-plant logic through these methods, keeping the tracker agnostic of
 * specific block types.
 */

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public interface VegetationTypeAdapter {
    ResourceLocation typeId();

    VegetationCategory category();

    boolean matches(BlockState state);

    ActiveVegetationRecord captureBirth(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            long gameTime,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId);

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
