package com.s.ecoflux.mixin.client;

import com.s.ecoflux.client.visual.VisualLifecycleClientRuntime;
import com.s.ecoflux.client.visual.VisualLifecycleRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Sodium from including Ecoflux-tracked blocks with non-1.0 scale in
 * its chunk meshes. Without this, Sodium renders the full-size model alongside
 * Ecoflux's scaled custom render, causing double-draw.
 *
 * <p>{@code @Pseudo} ensures no crash when Sodium is absent.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void ecoflux$skipEcofluxTrackedBlocksInSodiumMesh(
            BakedModel model,
            BlockState state,
            BlockPos pos,
            BlockPos origin,
            CallbackInfo ci) {
        VisualLifecycleRenderState renderState = VisualLifecycleClientRuntime.INSTANCE.getRenderState(pos, state);
        if (renderState != null && Math.abs(renderState.scale() - 1.0F) >= 0.0001F) {
            ci.cancel();
        }
    }
}
