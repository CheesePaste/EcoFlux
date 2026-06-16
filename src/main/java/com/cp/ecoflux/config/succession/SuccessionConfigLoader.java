package com.cp.ecoflux.config.succession;

import com.cp.ecoflux.api.config.SuccessionPathDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cp.ecoflux.config.AbstractJsonConfigLoader;
import com.cp.ecoflux.api.config.ClimateCondition;
import com.cp.ecoflux.api.config.FloatRange;
import com.cp.ecoflux.EcofluxConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

public final class SuccessionConfigLoader extends AbstractJsonConfigLoader<SuccessionPathDefinition> {
    public static final String DIRECTORY = "succession_paths";
    public static final SuccessionConfigLoader INSTANCE = new SuccessionConfigLoader();

    private SuccessionConfigLoader() {
        super(DIRECTORY);
    }

    @Override
    protected String configType() {
        return "Ecoflux 演替路径";
    }

    @Override
    protected List<SuccessionPathDefinition> parseFile(JsonObject root, ResourceLocation fileId) {
        return List.of(parseDefinition(fileId, root));
    }

    @Override
    protected void onLoadComplete(List<SuccessionPathDefinition> parsed) {
        SuccessionConfigRegistry.replace(parsed);
        EcofluxConstants.LOGGER.info("已加载 {} 条 Ecoflux 演替路径定义", parsed.size());
        logSourceBiomeSummary(parsed);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonByFileId, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<SuccessionPathDefinition> parsedPaths = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> fileIdByPathId = new LinkedHashMap<>();

        jsonByFileId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    try {
                        JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                        int schemaVersion = GsonHelper.getAsInt(root, "schema_version");
                        if (schemaVersion != 1) {
                            throw new JsonParseException(entry.getKey() + " 中存在不支持的 schema_version " + schemaVersion);
                        }
                        SuccessionPathDefinition definition = parseDefinition(entry.getKey(), root);

                        ResourceLocation existingFile = fileIdByPathId.putIfAbsent(definition.pathId(), entry.getKey());
                        if (existingFile != null) {
                            throw new JsonParseException("重复的 path_id " + definition.pathId() + "，也声明于 " + existingFile);
                        }

                        parsedPaths.add(definition);
                    } catch (RuntimeException exception) {
                        EcofluxConstants.LOGGER.error("解析{}文件 {} 失败", configType(), entry.getKey(), exception);
                    }
                });

        onLoadComplete(parsedPaths);
    }

    private SuccessionPathDefinition parseDefinition(ResourceLocation fileId, JsonObject root) {
        JsonObject chunkRulesObject = GsonHelper.getAsJsonObject(root, "chunk_rules");

        return new SuccessionPathDefinition(
                parseId(root, "path_id"),
                GsonHelper.getAsInt(root, "priority", 0),
                parseIdList(GsonHelper.getAsJsonArray(root, "source_biomes"), "source_biomes"),
                parseId(root, "target_biome"),
                parseOptionalId(root, "fallback_biome"),
                parseClimate(GsonHelper.getAsJsonObject(root, "climate")),
                GsonHelper.getAsDouble(chunkRulesObject, "positive_progress_step", 0.5D),
                GsonHelper.getAsDouble(chunkRulesObject, "negative_progress_step", 0.25D));
    }

    private ClimateCondition parseClimate(JsonObject climateObject) {
        return new ClimateCondition(
                parseFloatRange(GsonHelper.getAsJsonObject(climateObject, "temperature"), "temperature"),
                parseFloatRange(GsonHelper.getAsJsonObject(climateObject, "downfall"), "downfall"));
    }

    private FloatRange parseFloatRange(JsonObject rangeObject, String fieldName) {
        return new FloatRange(
                GsonHelper.getAsDouble(rangeObject, "min"),
                GsonHelper.getAsDouble(rangeObject, "max"));
    }

    private List<ResourceLocation> parseIdList(JsonArray jsonArray, String fieldName) {
        List<ResourceLocation> values = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            values.add(parseId(element, fieldName));
        }
        if (values.isEmpty()) {
            throw new JsonParseException(fieldName + " 不能为空");
        }
        return values;
    }

    private ResourceLocation parseId(JsonObject json, String memberName) {
        return parseId(GsonHelper.getAsString(json, memberName), memberName);
    }

    private ResourceLocation parseId(JsonElement element, String fieldName) {
        return parseId(GsonHelper.convertToString(element, fieldName), fieldName);
    }

    private ResourceLocation parseId(String value, String fieldName) {
        try {
            return ResourceLocation.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException(fieldName + " 的资源位置无效：" + value, exception);
        }
    }

    private @Nullable ResourceLocation parseOptionalId(JsonObject json, String memberName) {
        String value = GsonHelper.getAsString(json, memberName, "");
        return value.isBlank() ? null : parseId(value, memberName);
    }

    private void logSourceBiomeSummary(List<SuccessionPathDefinition> parsedPaths) {
        String summary = parsedPaths.stream()
                .flatMap(path -> path.sourceBiomes().stream())
                .collect(Collectors.groupingBy(
                        ResourceLocation::toString,
                        LinkedHashMap::new,
                        Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));

        EcofluxConstants.LOGGER.info(
                "Ecoflux 演替来源群系统计：{}",
                summary.isBlank() ? "未加载来源群系" : summary);
    }
}
