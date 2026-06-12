package com.s.ecoflux.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.s.ecoflux.EcofluxConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

public final class PlantRegistryLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "plant_definitions";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final PlantRegistryLoader INSTANCE = new PlantRegistryLoader();

    private PlantRegistryLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonByFileId, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<PlantDefinition> allPlants = new ArrayList<>();

        jsonByFileId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    try {
                        JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                        int schemaVersion = GsonHelper.getAsInt(root, "schema_version");
                        if (schemaVersion != 1) {
                            throw new JsonParseException(entry.getKey() + " 中存在不支持的 schema_version " + schemaVersion);
                        }
                        JsonArray plantsArray = GsonHelper.getAsJsonArray(root, "plants");
                        for (JsonElement element : plantsArray) {
                            JsonObject plantObject = GsonHelper.convertToJsonObject(element, "plant");
                            allPlants.add(parsePlantDefinition(plantObject));
                        }
                    } catch (RuntimeException exception) {
                        EcofluxConstants.LOGGER.error("解析植物定义文件 {} 失败", entry.getKey(), exception);
                    }
                });

        PlantRegistry.INSTANCE.reload(allPlants);
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
        if (values.isEmpty()) {
            throw new JsonParseException(fieldName + " 不能为空");
        }
        return values;
    }
}
