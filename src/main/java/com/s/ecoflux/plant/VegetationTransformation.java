package com.s.ecoflux.plant;

/**
 * Descriptor for a vegetation type transition event, primarily
 * sapling-to-tree conversion.
 *
 * <p>Structure: immutable record carrying the target block's resource ID
 * ({@code targetVegetationId}), the adapter type to use after the transition
 * ({@code targetAdapterType}, typically
 * {@link TreeStructureAdapter#TYPE_ID}), the new {@link VegetationCategory}
 * and {@link VegetationLifecycleStage}, and the new base/current point values
 * for succession scoring.
 *
 * <p>Role in Ecoflux: produced by
 * {@link SaplingAdapter#detectTransformation} when a tracked sapling position
 * is found to contain a log or leaf block (meaning the morphology system
 * finished growing the tree). Consumed by
 * {@link VegetationTracker#observeTrackedInternal}, which creates a new
 * {@link ActiveVegetationRecord} via
 * {@link ActiveVegetationRecord#withTransformation} to replace the sapling
 * record with a tree record.
 */

import net.minecraft.resources.ResourceLocation;

public record VegetationTransformation(
        ResourceLocation targetVegetationId,
        ResourceLocation targetAdapterType,
        VegetationCategory targetCategory,
        VegetationLifecycleStage targetStage,
        int targetBasePointValue,
        int targetCurrentPointValue) {
}
