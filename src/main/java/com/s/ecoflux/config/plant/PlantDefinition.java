package com.s.ecoflux.config.plant;

import net.minecraft.resources.ResourceLocation;

public record PlantDefinition(
        ResourceLocation plantId,
        int pointValue,
        long maxAgeTicks,
        PlantSpawnRules spawnRules) {
    public PlantDefinition {
        if (plantId == null) {
            throw new IllegalArgumentException("plantId cannot be null");
        }
        if (pointValue < 0) {
            throw new IllegalArgumentException("pointValue must be non-negative");
        }
        if (maxAgeTicks <= 0L) {
            throw new IllegalArgumentException("maxAgeTicks must be positive");
        }
        if (spawnRules == null) {
            throw new IllegalArgumentException("spawnRules cannot be null");
        }
    }
}
