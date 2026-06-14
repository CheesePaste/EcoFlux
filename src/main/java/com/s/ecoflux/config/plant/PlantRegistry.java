package com.s.ecoflux.config.plant;

import com.s.ecoflux.EcofluxConstants;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;

public final class PlantRegistry {
    public static final PlantRegistry INSTANCE = new PlantRegistry();

    private final Map<ResourceLocation, PlantDefinition> definitions = new ConcurrentHashMap<>();

    private PlantRegistry() {}

    public void reload(Collection<PlantDefinition> loaded) {
        definitions.clear();
        for (PlantDefinition def : loaded) {
            definitions.put(def.plantId(), def);
        }
        EcofluxConstants.LOGGER.info("植物注册表已加载 {} 个定义。", definitions.size());
    }

    public Optional<PlantDefinition> getDefinition(ResourceLocation plantId) {
        return Optional.ofNullable(definitions.get(plantId));
    }

    public Collection<PlantDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int size() {
        return definitions.size();
    }
}
