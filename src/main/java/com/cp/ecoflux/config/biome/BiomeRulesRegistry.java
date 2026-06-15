package com.cp.ecoflux.config.biome;

import com.cp.ecoflux.EcofluxConstants;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;

public final class BiomeRulesRegistry {
    private static volatile Map<ResourceLocation, BiomeRules> rulesByBiome = Map.of();

    private BiomeRulesRegistry() {}

    public static synchronized void replace(Collection<BiomeRules> loaded) {
        Map<ResourceLocation, BiomeRules> map = new ConcurrentHashMap<>();
        for (BiomeRules rules : loaded) {
            map.put(rules.biomeId(), rules);
        }
        rulesByBiome = map;
        EcofluxConstants.LOGGER.info("群系规则注册表已加载 {} 个群系。", map.size());
    }

    public static Optional<BiomeRules> getRules(ResourceLocation biomeId) {
        return Optional.ofNullable(rulesByBiome.get(biomeId));
    }

    public static Collection<BiomeRules> getAllRules() {
        return Collections.unmodifiableCollection(rulesByBiome.values());
    }

    public static int size() {
        return rulesByBiome.size();
    }
}
