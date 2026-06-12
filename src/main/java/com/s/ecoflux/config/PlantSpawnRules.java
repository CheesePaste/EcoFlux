package com.s.ecoflux.config;

/**
 * Spawning constraints for a plant species in a succession path.
 *
 * <p>Structure: a record specifying sky access requirement ({@code requireSky}),
 * local density cap ({@code maxLocalDensity}), and a list of allowed base blocks
 * ({@code allowedBaseBlocks}) on which the plant can be placed.
 * <p>Role in Ecoflux: attached to {@link PlantDefinition} to validate spawn placement
 * during succession plant generation, ensuring plants only appear on valid surfaces.
 */

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record PlantSpawnRules(
        boolean requireSky,
        int maxLocalDensity,
        List<ResourceLocation> allowedBaseBlocks) {

    public static final PlantSpawnRules EMPTY = new PlantSpawnRules(false, 1, List.of());

    public PlantSpawnRules {
        if (maxLocalDensity <= 0) {
            throw new IllegalArgumentException("maxLocalDensity must be positive");
        }
        allowedBaseBlocks = List.copyOf(allowedBaseBlocks);
    }
}
