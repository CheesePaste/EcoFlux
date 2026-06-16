package com.cp.ecoflux.config.plant;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.api.config.PlantDefinition;
import com.cp.ecoflux.config.AbstractConfigRegistry;
import java.util.Collection;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class PlantRegistry extends AbstractConfigRegistry<ResourceLocation, PlantDefinition> {
    public static final PlantRegistry INSTANCE = new PlantRegistry();

    private PlantRegistry() {}

    public void reload(Collection<PlantDefinition> loaded) {
        replaceAll(loaded, PlantDefinition::plantId);
        EcofluxConstants.LOGGER.info("植物注册表已加载 {} 个定义。", size());
    }

    public Optional<PlantDefinition> getDefinition(ResourceLocation plantId) {
        return get(plantId);
    }

    public Collection<PlantDefinition> getAllDefinitions() {
        return getAll();
    }
}
