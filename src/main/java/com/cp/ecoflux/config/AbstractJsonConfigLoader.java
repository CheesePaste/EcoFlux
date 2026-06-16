package com.cp.ecoflux.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.cp.ecoflux.EcofluxConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class AbstractJsonConfigLoader<T> extends SimpleJsonResourceReloadListener {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    protected AbstractJsonConfigLoader(String directory) {
        super(GSON, directory);
    }

    protected abstract void onLoadComplete(List<T> parsed);

    protected abstract List<T> parseFile(JsonObject root, ResourceLocation fileId);

    protected abstract String configType();

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonByFileId, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<T> parsed = new ArrayList<>();

        jsonByFileId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    try {
                        JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                        int schemaVersion = GsonHelper.getAsInt(root, "schema_version");
                        if (schemaVersion != 1) {
                            throw new JsonParseException(entry.getKey() + " 中存在不支持的 schema_version " + schemaVersion);
                        }
                        parsed.addAll(parseFile(root, entry.getKey()));
                    } catch (RuntimeException exception) {
                        EcofluxConstants.LOGGER.error("解析{}文件 {} 失败", configType(), entry.getKey(), exception);
                    }
                });

        onLoadComplete(parsed);
    }
}
