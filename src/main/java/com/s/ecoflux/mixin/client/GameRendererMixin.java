package com.s.ecoflux.mixin.client;

/**
 * Client-side mixin swapping the vanilla {@code rendertype_cutout} shader
 * for Ecoflux's {@code rendertype_cutout_scaled} shader.
 *
 * <p>Structure: {@code @ModifyReturnValue} on
 * {@code GameRenderer.getRendertypeCutoutShader()}, replacing the return
 * value with the custom shader when it is available. Falls back to the
 * vanilla shader during startup before the custom shader is loaded.
 *
 * <p>Role in Ecoflux: enables per-block scale animation in the vertex
 * shader without creating a new {@link net.minecraft.client.renderer.RenderType}.
 * All cutout-rendered blocks pass through the custom vertex shader, but
 * only tracked plants have non-1.0 scale values in the lookup texture.
 */

import com.s.ecoflux.client.visual.EcofluxShaders;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @ModifyReturnValue(method = "getRendertypeCutoutShader", at = @At("RETURN"))
    private static ShaderInstance ecoflux$swapCutoutShader(ShaderInstance original) {
        ShaderInstance custom = EcofluxShaders.getCutoutScaledShader();
        return custom != null ? custom : original;
    }
}
