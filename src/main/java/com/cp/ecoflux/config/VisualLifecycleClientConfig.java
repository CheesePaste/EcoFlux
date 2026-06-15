package com.cp.ecoflux.config;

/**
 * Client-side configuration for visual lifecycle rendering.
 *
 * <p>Structure: defines a {@link net.neoforged.neoforge.common.ModConfigSpec} with
 * per-stage scale overrides (born, growing-start, mature, aging) and a toggle to
 * use uniform configured scales instead of per-plant-type defaults. Also supports
 * a debug uniform scale override for development.
 * <p>Role in Ecoflux: controls how plant lifecycle stages are visually rendered
 * on the client, allowing players to customize scale animations for growing plants.
 */

import com.cp.ecoflux.client.visual.VisualLifecycleProfile;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class VisualLifecycleClientConfig {
    public static final ModConfigSpec SPEC;
    private static volatile Float debugUniformScaleOverride;

    private static final ModConfigSpec.BooleanValue USE_CONFIGURED_STAGE_SCALES;
    private static final ModConfigSpec.DoubleValue BORN_SCALE;
    private static final ModConfigSpec.DoubleValue GROWING_START_SCALE;
    private static final ModConfigSpec.DoubleValue MATURE_SCALE;
    private static final ModConfigSpec.DoubleValue AGING_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("visual_lifecycle");
        USE_CONFIGURED_STAGE_SCALES = builder
                .comment("为 true 时使用下方统一缩放；默认 false 时使用每种植物自己的缩放，避免小草被异常放大。")
                .define("use_configured_stage_scales", false);
        BORN_SCALE = builder
                .comment("出生阶段缩放。")
                .defineInRange("born_scale", 0.2D, 0.05D, 100.0D);
        GROWING_START_SCALE = builder
                .comment("生长阶段起始缩放。")
                .defineInRange("growing_start_scale", 0.65D, 0.05D, 100.0D);
        MATURE_SCALE = builder
                .comment("成熟阶段缩放。")
                .defineInRange("mature_scale", 1.0D, 0.05D, 100.0D);
        AGING_SCALE = builder
                .comment("衰老阶段缩放。")
                .defineInRange("aging_scale", 0.9D, 0.05D, 100.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private VisualLifecycleClientConfig() {
    }

    public static float bornScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get() ? BORN_SCALE.get().floatValue() : profile.bornScale();
    }

    public static float growingStartScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get()
                ? GROWING_START_SCALE.get().floatValue()
                : profile.matureScale() * 0.65F;
    }

    public static float matureScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get() ? MATURE_SCALE.get().floatValue() : profile.matureScale();
    }

    public static float agingScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get() ? AGING_SCALE.get().floatValue() : profile.agingScale();
    }

    public static void setDebugUniformScaleOverride(float scale) {
        debugUniformScaleOverride = scale;
    }

    public static void clearDebugUniformScaleOverride() {
        debugUniformScaleOverride = null;
    }

    public static Float debugUniformScaleOverride() {
        return debugUniformScaleOverride;
    }
}
