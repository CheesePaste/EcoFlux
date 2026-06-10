package com.s.ecoflux.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record PlantSpawnRules(
        boolean requireSky,
        int maxLocalDensity,
        List<ResourceLocation> allowedBaseBlocks) {
    public PlantSpawnRules {
        if (maxLocalDensity <= 0) {
            throw new IllegalArgumentException("maxLocalDensity must be positive");
        }
        allowedBaseBlocks = List.copyOf(allowedBaseBlocks);
    }
}
