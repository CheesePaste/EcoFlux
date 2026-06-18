package com.cp.ecoflux.api.data;

/**
 * Result record produced by {@link VegetationTypeAdapter#observe} after
 * evaluating a tracked plant's current state.
 *
 * <p>Structure: carries a {@code present} flag (false = plant is gone and
 * should be untracked), the current {@link VegetationLifecycleStage}, the
 * current point value, separate {@code mature} and {@code aging} boolean
 * flags for succession gating, an optional {@link VegetationTransformation}
 * for sapling-to-tree promotion, and a human-readable {@code detail} string
 * for debugging. The static factory {@link #absent} creates a default
 * not-present result.
 *
 * <p>Role in Ecoflux: returned by every adapter's {@code observe} call and
 * consumed by {@link VegetationTracker#observeTrackedInternal} to decide
 * whether to update, transform, or remove a vegetation record.
 */

import java.util.Optional;

public record VegetationObservation(
        boolean present,
        VegetationLifecycleStage stage,
        int currentPointValue,
        boolean mature,
        boolean aging,
        Optional<VegetationTransformation> transformation,
        String detail) {
    public static VegetationObservation absent(String detail) {
        return new VegetationObservation(
                false,
                VegetationLifecycleStage.DEAD,
                0,
                false,
                false,
                Optional.empty(),
                detail);
    }
}
