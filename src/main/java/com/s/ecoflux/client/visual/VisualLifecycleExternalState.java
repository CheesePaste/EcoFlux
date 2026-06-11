package com.s.ecoflux.client.visual;

/**
 * Server-synced lifecycle state snapshot for a tracked plant.
 *
 * <p>Structure: a record holding the server-side {@link VisualLifecycleStage},
 * normalized stage progress (clamped to 0.0-1.0 via {@code Mth.clamp}), and
 * the game time at which the sync occurred.
 * <p>Role in Ecoflux: carried inside {@link VisualLifecycleInstance} for
 * {@code VEGETATION_SYSTEM} entries. The adapter's {@code resolveState()} method
 * uses this snapshot as a base and extrapolates forward by elapsed ticks to
 * compute the current visual state, keeping the client roughly in sync with the
 * server without per-tick network updates.
 */

import net.minecraft.util.Mth;

public record VisualLifecycleExternalState(VisualLifecycleStage stage, float stageProgress, long syncGameTime) {
    public VisualLifecycleExternalState {
        stageProgress = Mth.clamp(stageProgress, 0.0F, 1.0F);
    }
}
