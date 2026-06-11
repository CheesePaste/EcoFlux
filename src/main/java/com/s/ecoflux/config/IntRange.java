package com.s.ecoflux.config;

/**
 * Immutable inclusive range of integer values.
 *
 * <p>Structure: a record with {@code min} and {@code max} bounds. The {@link #contains}
 * method checks inclusive membership. Construction validates that max >= min.
 * <p>Role in Ecoflux: used by {@link ChunkRules} for evaluation interval day ranges
 * and other integer range constraints in configuration.
 */

public record IntRange(int min, int max) {
    public IntRange {
        if (max < min) {
            throw new IllegalArgumentException("Range max must be greater than or equal to min");
        }
    }

    public boolean contains(int value) {
        return value >= min && value <= max;
    }
}
