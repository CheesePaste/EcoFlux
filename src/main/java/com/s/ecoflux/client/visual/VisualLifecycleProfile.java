package com.s.ecoflux.client.visual;

/**
 * Timing and visual parameters for a plant category's lifecycle animation.
 *
 * <p>Structure: a record holding tick durations for each of the four lifecycle
 * stages ({@code bornTicks}, {@code growingTicks}, {@code matureTicks},
 * {@code agingTicks}), scale values at the born/mature/aging keyframes, and
 * three aging color-shift coefficients: hue shift (wraps around the color wheel),
 * saturation multiplier, and brightness multiplier.
 * <p>Role in Ecoflux: each {@link VisualLifecycleAdapter} creates a profile
 * tailored to its plant category. The profile is consumed by
 * {@link VisualLifecycleAdapter#resolveState} to drive scale interpolation and
 * color degradation over the plant's visual lifetime.
 */

public record VisualLifecycleProfile(
        int bornTicks,
        int growingTicks,
        int matureTicks,
        int agingTicks,
        float bornScale,
        float matureScale,
        float agingScale,
        float agingHueShift,
        float agingSaturationMultiplier,
        float agingBrightnessMultiplier) {
}
