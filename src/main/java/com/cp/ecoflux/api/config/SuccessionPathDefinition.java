package com.cp.ecoflux.api.config;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SuccessionPathDefinition(
        ResourceLocation pathId,
        int priority,
        List<ResourceLocation> sourceBiomes,
        ResourceLocation targetBiome,
        @Nullable ResourceLocation fallbackBiome,
        ClimateCondition climate,
        double positiveProgressStep,
        double negativeProgressStep) {
    public SuccessionPathDefinition {
        if (pathId == null) {
            throw new IllegalArgumentException("pathId cannot be null");
        }
        if (sourceBiomes == null || sourceBiomes.isEmpty()) {
            throw new IllegalArgumentException("sourceBiomes cannot be empty");
        }
        if (targetBiome == null) {
            throw new IllegalArgumentException("targetBiome cannot be null");
        }
        if (climate == null) {
            throw new IllegalArgumentException("climate cannot be null");
        }
        if (positiveProgressStep <= 0.0D) {
            throw new IllegalArgumentException("positiveProgressStep must be positive");
        }
        if (negativeProgressStep <= 0.0D) {
            throw new IllegalArgumentException("negativeProgressStep must be positive");
        }
        sourceBiomes = List.copyOf(sourceBiomes);
    }

    public boolean matches(ResourceLocation biomeId, double temperature, double downfall) {
        return sourceBiomes.contains(biomeId) && climate.matches(temperature, downfall);
    }
}
