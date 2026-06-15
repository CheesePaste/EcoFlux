package com.cp.ecoflux.client.visual;

/**
 * The four visual lifecycle stages that drive scale and color animation.
 *
 * <p>Structure: a simple enum with values {@code BORN} (initial emergence),
 * {@code GROWING} (scale increasing toward mature), {@code MATURE} (full size,
 * no color shift), and {@code AGING} (scale decreasing with hue/saturation/
 * brightness degradation toward a wilted appearance).
 * <p>Role in Ecoflux: maps from the server-side {@code VegetationLifecycleStage}
 * enum and drives the scale-interpolation and color-shift logic in
 * {@link VisualLifecycleAdapter#resolveState}.
 */

public enum VisualLifecycleStage {
    BORN,
    GROWING,
    MATURE,
    AGING,
    DEAD
}
