package com.cp.ecoflux.compat.dynamictrees;

import com.cp.ecoflux.config.plant.PlantDefinition;
import com.cp.ecoflux.config.plant.PlantRegistry;
import com.cp.ecoflux.config.plant.PlantSpawnRules;
import com.dtteam.dynamictrees.tree.species.Species;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Maps DT {@link Species} to EcoFlux {@link PlantDefinition} for lifecycle tracking.
 * Resolves by: (1) primitive log block ID, (2) species registry name heuristics, (3) fallback.
 */
public final class DTSpeciesMapping {

    private DTSpeciesMapping() {}

    public static PlantDefinition resolve(Species species) {
        ResourceLocation plantId = null;

        // 1. Try primitive log block ID from family
        var logOpt = species.getFamily().getPrimitiveLog();
        if (logOpt.isPresent()) {
            Block logBlock = logOpt.get();
            plantId = BuiltInRegistries.BLOCK.getKey(logBlock);
            var def = PlantRegistry.INSTANCE.getDefinition(plantId);
            if (def.isPresent()) return def.get();
        }

        // 2. Try mappings based on species registry name
        ResourceLocation speciesId = species.getRegistryName();
        if (speciesId != null) {
            String path = speciesId.getPath();
            plantId = ResourceLocation.withDefaultNamespace(path + "_log");
            var def = PlantRegistry.INSTANCE.getDefinition(plantId);
            if (def.isPresent()) return def.get();
        }

        // 3. Try common species
        Species commonSpecies = species.getCommonSpecies();
        if (commonSpecies != null && commonSpecies != species) {
            return resolve(commonSpecies);
        }

        // 4. Fallback
        ResourceLocation fallbackId = plantId != null ? plantId
                : ResourceLocation.withDefaultNamespace("oak_log");
        return new PlantDefinition(fallbackId, 4, 288000L, PlantSpawnRules.EMPTY);
    }
}
