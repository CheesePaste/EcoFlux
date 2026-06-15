package com.cp.ecoflux.config.biome;

import java.util.List;
import java.util.Random;

import com.cp.ecoflux.config.plant.PathPlantEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public record BiomeRules(
        ResourceLocation biomeId,
        int minPlantCount,
        int maxPlantCount,
        int consuming,
        double queueFillFactor,
        List<PathPlantEntry> plants) {
    public BiomeRules {
        if (biomeId == null) {
            throw new IllegalArgumentException("biomeId cannot be null");
        }
        if (minPlantCount < 0) {
            throw new IllegalArgumentException("minPlantCount must be non-negative");
        }
        if (maxPlantCount < 0) {
            throw new IllegalArgumentException("maxPlantCount must be non-negative");
        }
        if (minPlantCount > maxPlantCount) {
            throw new IllegalArgumentException("minPlantCount must be <= maxPlantCount");
        }
        if (consuming < 0) {
            throw new IllegalArgumentException("consuming must be non-negative");
        }
        // Empty biome (maxPlantCount == 0): biome does not participate in succession.
        // Skip plants/queue validation — these fields are irrelevant for empty paths.
        if (maxPlantCount > 0) {
            if (queueFillFactor < 1.0D) {
                throw new IllegalArgumentException("queueFillFactor must be at least 1.0");
            }
            if (plants == null || plants.isEmpty()) {
                throw new IllegalArgumentException("plants cannot be empty when maxPlantCount > 0");
            }
        }
        plants = List.copyOf(plants);
    }

    /** @deprecated Use {@link #samplePlantCount(Random)} for per-chunk caps. */
    @Deprecated
    public int plantCountUpperBound() {
        return maxPlantCount;
    }

    public int queueCapacity() {
        return (int) Math.ceil(maxPlantCount * queueFillFactor);
    }

    /**
     * Samples a per-chunk plant count cap from a normal distribution centered at
     * (min + max) / 2, with σ = (max - min) / 6 so [min, max] covers ±3σ (99.7%).
     * The result is clamped to [min, max].
     */
    public int samplePlantCount(Random random) {
        if (minPlantCount >= maxPlantCount) {
            return maxPlantCount;
        }
        double mean = (minPlantCount + maxPlantCount) / 2.0;
        double stddev = (maxPlantCount - minPlantCount) / 6.0;
        double gaussian = random.nextGaussian() * stddev + mean;
        return Mth.clamp((int) Math.round(gaussian), minPlantCount, maxPlantCount);
    }
}
