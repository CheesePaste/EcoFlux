package com.s.ecoflux.config;

/**
 * Immutable inclusive range of float (double) values.
 *
 * <p>Structure: a record with {@code min} and {@code max} bounds. The {@link #contains}
 * method checks inclusive membership. Construction validates that max >= min.
 * <p>Role in Ecoflux: used by {@link ClimateCondition} to define temperature and
 * downfall range constraints for succession path matching.
 */

public record FloatRange(double min, double max) {
    public FloatRange {
        if (max < min) {
            throw new IllegalArgumentException("Range max must be greater than or equal to min");
        }
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }
}
