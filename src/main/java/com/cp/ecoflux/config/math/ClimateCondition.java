package com.cp.ecoflux.config.math;

import com.cp.ecoflux.config.succession.SuccessionPathDefinition;

/**
 * Climate matching condition for succession path selection.
 *
 * <p>Structure: a record with {@link FloatRange} bounds for temperature and downfall.
 * The {@link #matches} method tests whether a chunk's climate values fall within both ranges.
 * <p>Role in Ecoflux: used by {@link SuccessionPathDefinition} to filter which paths
 * are eligible for a chunk based on its biome climate, ensuring succession only
 * progresses when environmental conditions are appropriate.
 */

public record ClimateCondition(FloatRange temperature, FloatRange downfall) {
    public ClimateCondition {
        if (temperature == null) {
            throw new IllegalArgumentException("temperature range cannot be null");
        }
        if (downfall == null) {
            throw new IllegalArgumentException("downfall range cannot be null");
        }
    }

    public boolean matches(double temperatureValue, double downfallValue) {
        return temperature.contains(temperatureValue) && downfall.contains(downfallValue);
    }
}
