package com.s.ecoflux.plant;

/**
 * Record holding visual state data for a tracked plant, synced to clients for
 * rendering.
 *
 * <p>Structure: compact canonical constructor clamps {@code stageProgress} to
 * [0.0, 1.0] via {@link net.minecraft.util.Mth#clamp}. Fields are a
 * {@link VegetationLifecycleStage} and a normalized {@code float}
 * representing progress through that stage (0 = just entered, 1 = about to
 * transition).
 *
 * <p>Role in Ecoflux: produced by each adapter's
 * {@link VegetationTypeAdapter#visualState} method, bundled into
 * {@link com.s.ecoflux.network.VegetationVisualSyncEntry} by
 * {@link VegetationTracker#buildVisualSyncEntries}, and sent to clients via
 * {@link com.s.ecoflux.network.ModNetworking#syncChunkToTracking}. The client
 * visual layer uses {@code stage} and {@code stageProgress} to interpolate
 * scale, tint, and alpha for each rendered plant.
 */

import net.minecraft.util.Mth;

public record VegetationVisualState(VegetationLifecycleStage stage, float stageProgress) {
    public VegetationVisualState {
        stageProgress = Mth.clamp(stageProgress, 0.0F, 1.0F);
    }
}
