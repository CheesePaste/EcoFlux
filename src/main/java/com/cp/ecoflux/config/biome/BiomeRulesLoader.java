package com.cp.ecoflux.config.biome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.cp.ecoflux.EcofluxConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.cp.ecoflux.config.plant.PathPlantEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

public final class BiomeRulesLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "biome_rules";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final BiomeRulesLoader INSTANCE = new BiomeRulesLoader();

    private BiomeRulesLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonByFileId, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<BiomeRules> parsed = new ArrayList<>();

        jsonByFileId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    try {
                        JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                        int schemaVersion = GsonHelper.getAsInt(root, "schema_version");
                        if (schemaVersion != 1) {
                            throw new JsonParseException(entry.getKey() + " 中存在不支持的 schema_version " + schemaVersion);
                        }
                        parsed.add(parseRules(root));
                    } catch (RuntimeException exception) {
                        EcofluxConstants.LOGGER.error("解析群系规则文件 {} 失败", entry.getKey(), exception);
                    }
                });

        BiomeRulesRegistry.replace(parsed);
    }

    private BiomeRules parseRules(JsonObject root) {
        ResourceLocation biomeId = ResourceLocation.parse(GsonHelper.getAsString(root, "biome_id"));
        int maxPlantCount = GsonHelper.getAsInt(root, "max_plant_count");
        int minPlantCount = GsonHelper.getAsInt(root, "min_plant_count", maxPlantCount);
        int consuming = GsonHelper.getAsInt(root, "consuming");
        double queueFillFactor = GsonHelper.getAsDouble(root, "queue_fill_factor", 2.0D);
        JsonArray plantsArray = GsonHelper.getAsJsonArray(root, "plants");
        List<PathPlantEntry> plants = new ArrayList<>();
        for (JsonElement element : plantsArray) {
            JsonObject plantObject = GsonHelper.convertToJsonObject(element, "plant");
            plants.add(new PathPlantEntry(
                    ResourceLocation.parse(GsonHelper.getAsString(plantObject, "plant_id")),
                    GsonHelper.getAsInt(plantObject, "weight")));
        }
        return new BiomeRules(biomeId, minPlantCount, maxPlantCount, consuming, queueFillFactor, plants);
    }
}
