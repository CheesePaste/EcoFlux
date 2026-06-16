package com.cp.ecoflux.worldgen;

import com.cp.ecoflux.attachment.ActiveVegetationRecord;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.biome.BiomeRules;
import com.cp.ecoflux.config.biome.BiomeRulesRegistry;
import com.cp.ecoflux.config.plant.PathPlantEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import net.minecraft.resources.ResourceLocation;

/**
 * Caps vegetation count to maxPlantCount, prioritizing removal of over-represented plant types.
 *
 * <p>Extracted from {@link WorldGenVegetationScanner} to keep each class focused.
 */
public final class DensityCapper {

    private DensityCapper() {}

    /**
     * Builds a deviation map showing how much each plant type's actual share
     * exceeds or falls short of its target weight share. Positive = over-represented.
     */
    public static Map<ResourceLocation, Double> buildDeviationMap(SuccessionChunkData chunkData) {
        Map<ResourceLocation, Double> deviation = new HashMap<>();
        Optional<BiomeRules> rulesOpt = chunkData.getActiveBiomeRulesId()
                .flatMap(BiomeRulesRegistry::getRules);
        if (rulesOpt.isEmpty()) {
            return deviation;
        }
        BiomeRules rules = rulesOpt.get();

        int totalWeight = rules.plants().stream().mapToInt(PathPlantEntry::weight).sum();
        int totalPlants = chunkData.getCurrentPlantCount();
        if (totalWeight == 0 || totalPlants == 0) {
            return deviation;
        }

        Map<ResourceLocation, Integer> currentCounts = new HashMap<>();
        for (var record : chunkData.getVegetationRecords().values()) {
            currentCounts.merge(record.vegetationId(), 1, Integer::sum);
        }

        for (PathPlantEntry entry : rules.plants()) {
            double targetShare = (double) entry.weight() / totalWeight;
            double actualShare = (double) currentCounts.getOrDefault(entry.plantId(), 0) / totalPlants;
            deviation.put(entry.plantId(), actualShare - targetShare);
        }
        return deviation;
    }

    /**
     * Removes plants to bring the total under maxPlantCount.
     * Plants with the highest positive deviation (most over-represented) are removed first.
     *
     * @return the number of plants removed
     */
    public static int cap(SuccessionChunkData chunkData, int maxPlants, long chunkSeed) {
        int totalBeforeCap = chunkData.getCurrentPlantCount();
        if (maxPlants <= 0 || totalBeforeCap <= maxPlants) {
            return 0;
        }

        Map<ResourceLocation, Double> deviation = buildDeviationMap(chunkData);
        List<ActiveVegetationRecord> allRecords = new ArrayList<>(chunkData.getVegetationRecords().values());
        allRecords.sort(Comparator.<ActiveVegetationRecord>comparingDouble(
                r -> deviation.getOrDefault(r.vegetationId(), 0.0)).reversed());

        int toRemove = totalBeforeCap - maxPlants;
        Random random = new Random(chunkSeed ^ 0x5EED);
        int removed = 0;

        for (int i = 0; i < toRemove && !allRecords.isEmpty(); i++) {
            int poolSize = Math.min(allRecords.size(), Math.max(1, toRemove * 2));
            int idx = random.nextInt(poolSize);
            ActiveVegetationRecord record = allRecords.get(idx);
            chunkData.removeVegetation(record.position());
            allRecords.remove(idx);
            removed++;
        }

        return removed;
    }
}
