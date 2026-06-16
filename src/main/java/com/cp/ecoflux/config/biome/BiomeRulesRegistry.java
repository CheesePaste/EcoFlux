package com.cp.ecoflux.config.biome;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.config.AbstractConfigRegistry;
import java.util.Collection;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class BiomeRulesRegistry extends AbstractConfigRegistry<ResourceLocation, BiomeRules> {
    private static final BiomeRulesRegistry INSTANCE = new BiomeRulesRegistry();

    private BiomeRulesRegistry() {}

    public static void replace(Collection<BiomeRules> loaded) {
        INSTANCE.replaceAll(loaded, BiomeRules::biomeId);
        EcofluxConstants.LOGGER.info("群系规则注册表已加载 {} 个群系。", INSTANCE.size());
    }

    public static Optional<BiomeRules> getRules(ResourceLocation biomeId) {
        return INSTANCE.get(biomeId);
    }

    public static Collection<BiomeRules> getAllRules() {
        return INSTANCE.getAll();
    }
}
