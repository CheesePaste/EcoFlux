package com.s.ecoflux.config;

/**
 * Global succession speed multiplier for accelerated or slowed transitions.
 *
 * <p>Structure: a simple holder with a volatile {@code speedMultiplier} float,
 * clamped between 0.1x and 1000x. Accessible via static getter/setter.
 * <p>Role in Ecoflux: allows runtime adjustment of succession pacing for debugging
 * and prototyping without modifying individual path configurations.
 */

public final class SuccessionSpeedConfig {
    private static volatile float speedMultiplier = 1.0f;

    private SuccessionSpeedConfig() {}

    public static float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public static void setSpeedMultiplier(float multiplier) {
        speedMultiplier = Math.max(0.1f, Math.min(1000.0f, multiplier));
    }
}
