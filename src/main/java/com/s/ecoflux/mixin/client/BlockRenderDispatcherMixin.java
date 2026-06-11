package com.s.ecoflux.mixin.client;

/**
 * Client-side mixin suppressing vanilla block rendering for Ecoflux-tracked
 * blocks with non-1.0 visual scale.
 *
 * <p>Structure: {@code @Inject} on both overloads of
 * {@code BlockRenderDispatcher.renderBatched()} at {@code HEAD}, cancellable.
 * If the block has a non-identity scale in {@code VisualLifecycleRenderState},
 * vanilla rendering is skipped so Ecoflux's custom renderer can draw the scaled
 * version instead.
 * <p>Role in Ecoflux: prevents vanilla and Ecoflux renders from overlapping,
 * enabling the plant growth/death scaling animations on the client.
 */

import com.mojang.blaze3d.vertex.PoseStack;
import com.s.ecoflux.client.visual.VisualLifecycleClientRuntime;
import com.s.ecoflux.client.visual.VisualLifecycleRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(
            method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ecoflux$skipBaseRenderForScaledTrackedBlocks(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            CallbackInfo ci) {
        if (VisualLifecycleClientRuntime.INSTANCE.isManualWorldRenderPass()) {
            return;
        }

        VisualLifecycleRenderState renderState = VisualLifecycleClientRuntime.INSTANCE.getRenderState(pos, state);
        if (renderState != null && Math.abs(renderState.scale() - 1.0F) >= 0.0001F) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ecoflux$skipBaseRenderForScaledTrackedBlocksWithModelData(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            ModelData modelData,
            RenderType renderType,
            CallbackInfo ci) {
        if (VisualLifecycleClientRuntime.INSTANCE.isManualWorldRenderPass()) {
            return;
        }

        VisualLifecycleRenderState renderState = VisualLifecycleClientRuntime.INSTANCE.getRenderState(pos, state);
        if (renderState != null && Math.abs(renderState.scale() - 1.0F) >= 0.0001F) {
            ci.cancel();
        }
    }
}
