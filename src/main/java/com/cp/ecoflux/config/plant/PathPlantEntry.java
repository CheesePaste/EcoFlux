package com.cp.ecoflux.config.plant;

import net.minecraft.resources.ResourceLocation;

public record PathPlantEntry(ResourceLocation plantId, int weight) {
    public PathPlantEntry {
        if (plantId == null) {
            throw new IllegalArgumentException("plantId cannot be null");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
    }
}
