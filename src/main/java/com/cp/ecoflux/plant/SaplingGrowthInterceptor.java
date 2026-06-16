package com.cp.ecoflux.plant;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interception point for vanilla sapling → tree growth.
 *
 * <p>Two-phase design:
 * <ol>
 *   <li>{@link #canHandle(BlockState)} — pure query, no side effects. Used by
 *       the observation pipeline to know when to skip EcoFlux growth triggers.</li>
 *   <li>{@link #tryGrowSapling} — performs the actual growth. Called only from
 *       {@code SaplingBlockMixin} (the vanilla {@code advanceTree} path).</li>
 * </ol>
 *
 * <p>Registered by compatibility layers (DT, etc.). The mixin iterates all
 * interceptors; the first one returning {@code true} cancels vanilla growth.
 */
public interface SaplingGrowthInterceptor {

    /** Pure query: can this interceptor handle saplings of this block type? */
    boolean canHandle(BlockState state);

    /** Trigger growth. Called only when vanilla {@code advanceTree} fires. */
    boolean tryGrowSapling(ServerLevel level, BlockPos pos, BlockState state);
}
