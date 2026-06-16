package com.cp.ecoflux.config.biome;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.cp.ecoflux.config.AbstractJsonConfigLoader;
import com.cp.ecoflux.config.plant.PathPlantEntry;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public final class BiomeRulesLoader extends AbstractJsonConfigLoader<BiomeRules> {
    public static final String DIRECTORY = "biome_rules";
    public static final BiomeRulesLoader INSTANCE = new BiomeRulesLoader();

    private BiomeRulesLoader() {
        super(DIRECTORY);
    }

    @Override
    protected String configType() {
        return "群系规则";
    }

    @Override
    protected List<BiomeRules> parseFile(JsonObject root, ResourceLocation fileId) {
        return List.of(parseRules(root));
    }

    @Override
    protected void onLoadComplete(List<BiomeRules> parsed) {
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
