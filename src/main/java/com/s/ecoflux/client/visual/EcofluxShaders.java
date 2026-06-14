package com.s.ecoflux.client.visual;

/**
 * Client-side shader registration and management for Ecoflux custom shaders.
 *
 * <p>Structure: subscribes to {@code RegisterShadersEvent} on the mod event bus,
 * loading {@code rendertype_cutout_scaled} and storing the instance. Hooks
 * {@code GameRenderer.getRendertypeCutoutShader()} via mixin to swap in the
 * custom shader. Provides {@code updateScaleSampler()} to bind the
 * {@link EcofluxScaleTexture} after it becomes ready.
 *
 * <p>Role in Ecoflux: replaces the vanilla cutout vertex shader with one that
 * applies per-block scaling from a GPU texture lookup, moving the scale
 * animation from CPU re-rendering to zero-overhead GPU computation.
 */

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.s.ecoflux.EcofluxConstants;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class EcofluxShaders {
    @Nullable
    private static volatile ShaderInstance cutoutScaledShader;

    private EcofluxShaders() {
    }

    /** Register the shader on the mod event bus. Called from {@code EcofluxMod} (client-side only). */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EcofluxShaders::onRegisterShaders);
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        ResourceProvider provider = event.getResourceProvider();
        try {
            ShaderInstance shader = new ShaderInstance(
                    provider,
                    EcofluxConstants.id("rendertype_cutout_scaled"),
                    DefaultVertexFormat.BLOCK);
            event.registerShader(shader, instance -> {
                cutoutScaledShader = instance;
                EcofluxConstants.LOGGER.info("[Ecoflux] rendertype_cutout_scaled shader loaded successfully");
                if (EcofluxScaleTexture.INSTANCE.isReady()) {
                    instance.setSampler("EcofluxScaleTex",
                            EcofluxScaleTexture.INSTANCE.getGlId());
                }
            });
        } catch (IOException e) {
            EcofluxConstants.LOGGER.error("Failed to load Ecoflux cutout_scaled shader", e);
        }
    }

    /** Returns the custom cutout shader, or null if not loaded yet. */
    @Nullable
    public static ShaderInstance getCutoutScaledShader() {
        return cutoutScaledShader;
    }

    /**
     * Per-frame: rebind scale texture sampler.
     * Called from {@link VisualLifecycleClientRuntime#updateScaleTexture()} each frame.
     */
    public static void updateSampler() {
        ShaderInstance shader = cutoutScaledShader;
        if (shader != null && EcofluxScaleTexture.INSTANCE.isReady()) {
            shader.setSampler("EcofluxScaleTex",
                    EcofluxScaleTexture.INSTANCE.getGlId());
        }
    }
}
