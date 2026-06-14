package com.s.ecoflux.mixin.worldgen;

import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Patches {@code ChunkGenerator.applyBiomeDecoration} to tolerate negative
 * feature indices produced when a runtime-swapped biome source causes a mismatch
 * between a chunk-section biome and the global features-per-step index.
 *
 * <p>Without this patch, an {@code IndexOutOfBoundsException: Index -1} is
 * thrown when {@code indexMapping().applyAsInt()} returns -1 for a feature
 * not present in the step's feature list (e.g. stale neighbouring chunks from
 * a previous batch-sampling iteration).</p>
 */
@Mixin(ChunkGenerator.class)
public abstract class ApplyBiomeDecorationMixin {

    @Redirect(
        method = "applyBiomeDecoration",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/ints/IntSet;toIntArray()[I"
        )
    )
    private int[] filterNegativeIndices(IntSet intset) {
        int[] arr = intset.toIntArray();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] < 0) {
                arr[i] = 0;
            }
        }
        return arr;
    }
}
