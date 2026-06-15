package com.cp.ecoflux.client.visual;

/**
 * Interface for visual lifecycle adapters, mirroring the server-side adapter pattern.
 *
 * <p>Structure: each implementation declares a {@code typeId()}, a {@code matches(BlockState)}
 * predicate, a factory for a default {@link VisualLifecycleProfile}, and optional
 * {@code demoBlocks()}/{@code supportSummary()} for command feedback. The default
 * {@code resolveState()} method is the core algorithm: it determines the current
 * {@link VisualLifecycleStage} from instance age (or server-synced external state
 * with tick extrapolation), interpolates scale along the stage curve, and applies
 * aging hue/saturation/brightness shifts via HSB color manipulation.
 * <p>Role in Ecoflux: adapters decouple block-type recognition from visual
 * computation, enabling each plant category (flowers, grass, saplings, generic
 * fallback) to have its own scale curve, timing, and color-degradation parameters.
 */

import com.cp.ecoflux.config.VisualLifecycleClientConfig;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public interface VisualLifecycleAdapter {
    ResourceLocation typeId();

    boolean matches(BlockState state);

    VisualLifecycleProfile createProfile(BlockState state);

    default List<Block> demoBlocks() {
        return List.of();
    }

    default String supportSummary() {
        return typeId().toString();
    }

    default VisualLifecycleRenderState resolveState(VisualLifecycleInstance instance, long gameTime, int baseColor) {
        VisualLifecycleStage stage = instance.forcedStage();
        float progress = 1.0F;
        VisualLifecycleProfile profile = instance.profile();
        if (stage == null) {
            VisualLifecycleExternalState externalState = instance.externalState();
            if (externalState != null) {
                stage = externalState.stage();
                float accumulated = externalState.stageProgress();
                long remaining = Math.max(0L, gameTime - externalState.syncGameTime());
                while (remaining > 0 && stage != null) {
                    int duration = switch (stage) {
                        case BORN -> profile.bornTicks();
                        case GROWING -> profile.growingTicks();
                        case MATURE -> profile.matureTicks();
                        case AGING -> profile.agingTicks();
                        case DEAD -> 0;
                    };
                    if (duration <= 0) {
                        break;
                    }
                    float remainingInStage = 1.0F - accumulated;
                    long ticksToComplete = (long) (remainingInStage * duration);
                    if (remaining >= ticksToComplete) {
                        remaining -= ticksToComplete;
                        accumulated = 0.0F;
                        stage = switch (stage) {
                            case BORN -> VisualLifecycleStage.GROWING;
                            case GROWING -> VisualLifecycleStage.MATURE;
                            case MATURE -> VisualLifecycleStage.AGING;
                            case AGING -> VisualLifecycleStage.AGING;
                            case DEAD -> VisualLifecycleStage.DEAD;
                        };
                    } else {
                        accumulated += (float) remaining / duration;
                        remaining = 0;
                    }
                }
                if (stage == null) {
                    stage = VisualLifecycleStage.AGING;
                }
                progress = accumulated;
            } else {
                long age = Math.max(0L, gameTime - instance.startGameTime());
                if (age < profile.bornTicks()) {
                    stage = VisualLifecycleStage.BORN;
                    progress = profile.bornTicks() <= 0 ? 1.0F : (float) age / profile.bornTicks();
                } else if (age < profile.bornTicks() + profile.growingTicks()) {
                    stage = VisualLifecycleStage.GROWING;
                    int localAge = (int) (age - profile.bornTicks());
                    progress = profile.growingTicks() <= 0 ? 1.0F : (float) localAge / profile.growingTicks();
                } else if (age < profile.bornTicks() + profile.growingTicks() + profile.matureTicks()) {
                    stage = VisualLifecycleStage.MATURE;
                    int localAge = (int) (age - profile.bornTicks() - profile.growingTicks());
                    progress = profile.matureTicks() <= 0 ? 1.0F : (float) localAge / profile.matureTicks();
                } else {
                    stage = VisualLifecycleStage.AGING;
                    long agingAge = age - profile.bornTicks() - profile.growingTicks() - profile.matureTicks();
                    progress = profile.agingTicks() <= 0 ? 1.0F : (float) agingAge / profile.agingTicks();
                }
            }
        }

        float bornScale = VisualLifecycleClientConfig.bornScale(profile);
        float growingStartScale = VisualLifecycleClientConfig.growingStartScale(profile);
        float matureScale = VisualLifecycleClientConfig.matureScale(profile);
        float agingScale = VisualLifecycleClientConfig.agingScale(profile);
        float scale = switch (stage) {
            case BORN -> lerp(bornScale, growingStartScale, progress);
            case GROWING -> lerp(growingStartScale, matureScale, progress);
            case MATURE -> matureScale;
            case AGING -> lerp(matureScale, agingScale, progress);
            case DEAD -> lerp(agingScale, 0.0F, progress);
        };
        int tintedColor = stage == VisualLifecycleStage.AGING || stage == VisualLifecycleStage.DEAD
                ? shiftColor(baseColor, profile, progress)
                : baseColor;
        return new VisualLifecycleRenderState(stage, clamp01(progress), scale, tintedColor);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp01(progress);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int shiftColor(int baseColor, VisualLifecycleProfile profile, float progress) {
        float clampedProgress = clamp01(progress);
        float red = ((baseColor >> 16) & 0xFF) / 255.0F;
        float green = ((baseColor >> 8) & 0xFF) / 255.0F;
        float blue = (baseColor & 0xFF) / 255.0F;
        float[] hsb = java.awt.Color.RGBtoHSB(
                Math.round(red * 255.0F),
                Math.round(green * 255.0F),
                Math.round(blue * 255.0F),
                null);
        float shiftedHue = hsb[0] + profile.agingHueShift() * clampedProgress;
        while (shiftedHue < 0.0F) {
            shiftedHue += 1.0F;
        }
        while (shiftedHue > 1.0F) {
            shiftedHue -= 1.0F;
        }
        float saturationMultiplier = lerp(1.0F, profile.agingSaturationMultiplier(), clampedProgress);
        float brightnessMultiplier = lerp(1.0F, profile.agingBrightnessMultiplier(), clampedProgress);
        float saturation = clamp01(hsb[1] * saturationMultiplier);
        float brightness = clamp01(hsb[2] * brightnessMultiplier);
        return java.awt.Color.HSBtoRGB(shiftedHue, saturation, brightness) & 0xFFFFFF;
    }
}
