package com.cp.ecoflux.compat.dynamictrees;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.plant.SaplingGrowthInterceptor;
import com.dtteam.dynamictrees.block.sapling.DynamicSaplingBlock;
import com.dtteam.dynamictrees.config.DTConfigs;
import com.dtteam.dynamictrees.tree.species.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@link SaplingGrowthInterceptor} for Dynamic Trees.
 *
 * <p>Two-phase design:
 * <ul>
 *   <li>{@link #canHandle} — pure query, checks config + sapling replacer map.</li>
 *   <li>{@link #tryGrowSapling} — triggers DT growth. Called only from
 *       {@code SaplingBlockMixin} (vanilla {@code advanceTree} path).</li>
 * </ul>
 *
 * <p>EcoFlux record management (sapling → tree) is handled by
 * {@link DTEventHandler#onTransitionSaplingToTree}, which listens to DT's
 * {@code TransitionSaplingToTreeEvent}. This class does NOT touch EcoFlux data.
 */
final class DTSaplingHandler implements SaplingGrowthInterceptor {

    static final DTSaplingHandler INSTANCE = new DTSaplingHandler();

    private DTSaplingHandler() {}

    @Override
    public boolean canHandle(BlockState state) {
        if (!DTConfigs.COMMON.replaceVanillaSaplings.get()) {
            return false;
        }
        if (!DynamicSaplingBlock.shouldReplaceSaplingWhenGrown(state)) {
            return false;
        }
        return DynamicSaplingBlock.SAPLING_REPLACERS.containsKey(state.getBlock());
    }

    @Override
    public boolean tryGrowSapling(ServerLevel level, BlockPos pos, BlockState state) {
        if (!canHandle(state)) {
            return false;
        }

        Species species = DynamicSaplingBlock.SAPLING_REPLACERS.get(state.getBlock());
        if (species == null || !species.isValid()) {
            return false;
        }

        species = species.selfOrLocationOverride(level, pos);
        if (!species.isValid()) {
            return false;
        }

        level.removeBlock(pos, false);

        if (DynamicSaplingBlock.canSaplingStay(level, species, pos)) {
            species.transitionToTree(level, pos);
            EcofluxConstants.LOGGER.debug("[Ecoflux-DT] DT handled sapling growth: {} at {}",
                    species.getRegistryName(), pos);
            return true;
        }

        return false;
    }
}
