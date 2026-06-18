package com.cp.ecoflux.config.plant;

import com.cp.ecoflux.api.config.PlantDefinition;
import com.cp.ecoflux.api.config.PlantSpawnRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.cp.ecoflux.config.AbstractJsonConfigLoader;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public final class PlantRegistryLoader extends AbstractJsonConfigLoader<PlantDefinition> {
    public static final String DIRECTORY = "plant_definitions";
    public static final PlantRegistryLoader INSTANCE = new PlantRegistryLoader();

    private PlantRegistryLoader() {
        super(DIRECTORY);
    }

    @Override
    protected String configType() {
        return "植物定义";
    }

    @Override
    protected List<PlantDefinition> parseFile(JsonObject root, ResourceLocation fileId) {
        JsonArray plantsArray = GsonHelper.getAsJsonArray(root, "plants");
        List<PlantDefinition> plants = new ArrayList<>();
        for (JsonElement element : plantsArray) {
            JsonObject plantObject = GsonHelper.convertToJsonObject(element, "plant");
            plants.add(parsePlantDefinition(plantObject));
        }
        return plants;
    }

    @Override
    protected void onLoadComplete(List<PlantDefinition> parsed) {
        PlantRegistry.INSTANCE.reload(parsed);
    }

    private PlantDefinition parsePlantDefinition(JsonObject plantObject) {
        ResourceLocation plantId = ResourceLocation.parse(GsonHelper.getAsString(plantObject, "plant_id"));
        int pointValue = GsonHelper.getAsInt(plantObject, "point_value");
        long maxAgeTicks = GsonHelper.getAsLong(plantObject, "max_age_ticks");
        JsonObject spawnRulesObject = GsonHelper.getAsJsonObject(plantObject, "spawn_rules");
        PlantSpawnRules spawnRules = new PlantSpawnRules(
                GsonHelper.getAsBoolean(spawnRulesObject, "require_sky", true),
                GsonHelper.getAsInt(spawnRulesObject, "max_local_density"),
                parseIdList(GsonHelper.getAsJsonArray(spawnRulesObject, "allowed_base_blocks"), "allowed_base_blocks"));
        return new PlantDefinition(plantId, pointValue, maxAgeTicks, spawnRules);
    }

    private List<ResourceLocation> parseIdList(JsonArray jsonArray, String fieldName) {
        List<ResourceLocation> values = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            values.add(ResourceLocation.parse(GsonHelper.convertToString(element, fieldName)));
        }
        return values;
    }
}
