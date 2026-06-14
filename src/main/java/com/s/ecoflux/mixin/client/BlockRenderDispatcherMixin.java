package com.s.ecoflux.mixin.client;

/**
 * Client-side mixin on {@code BlockRenderDispatcher.renderBatched()}.
 *
 * <p>Currently a no-op. Scale animation is now handled GPU-side by the custom
 * {@code rendertype_cutout_scaled} vertex shader, which applies per-block scale
 * from a lookup texture within the vanilla chunk draw call.
 *
 * <p>Kept as a placeholder for future per-block render modifications that may
 * require cancelling the vanilla render.
 */

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    // Mixin kept as placeholder — scale is now handled by rendertype_cutout_scaled.vsh
}
