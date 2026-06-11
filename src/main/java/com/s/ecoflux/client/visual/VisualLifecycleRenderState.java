package com.s.ecoflux.client.visual;

/**
 * Pre-computed per-frame render state for a tracked plant.
 *
 * <p>Structure: a record holding the current {@link VisualLifecycleStage}, normalized
 * stage progress (0.0 to 1.0), scale factor, and tinted color (RGB packed as int).
 * <p>Role in Ecoflux: produced by {@link VisualLifecycleAdapter#resolveState} each
 * frame, consumed by the block color handler (for tint via
 * {@link ModClientVisualLifecycle#onRegisterBlockColors}) and by
 * {@link VisualLifecycleWorldRenderer} (for scale-animated block rendering).
 * The render state is the single output that drives both visual effects.
 */

public record VisualLifecycleRenderState(
        VisualLifecycleStage stage,
        float stageProgress,
        float scale,
        int tintedColor) {
}
