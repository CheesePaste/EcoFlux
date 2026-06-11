package com.s.ecoflux.client.visual;

/**
 * One tracked plant instance in the client visual lifecycle system.
 *
 * <p>Structure: a record holding the adapter type ID, block resource location,
 * immutable block position, game time when tracking started, the visual profile,
 * an optional debug-forced stage, optional server-synced external state, and
 * the tracking source. Copy methods {@code withForcedStage()} and
 * {@code withExternalState()} enable immutable updates.
 * <p>Role in Ecoflux: this is the central data object flowing through the
 * visual pipeline. Created by {@link VisualLifecycleClientRuntime} from manual
 * commands or server sync packets, stored in concurrent maps keyed by dimension
 * and position, and consumed by adapters to produce per-frame
 * {@link VisualLifecycleRenderState}.
 */

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record VisualLifecycleInstance(
        ResourceLocation adapterId,
        ResourceLocation blockId,
        BlockPos pos,
        long startGameTime,
        VisualLifecycleProfile profile,
        VisualLifecycleStage forcedStage,
        VisualLifecycleExternalState externalState,
        VisualLifecycleTrackingSource source) {
    public VisualLifecycleInstance withForcedStage(VisualLifecycleStage nextStage) {
        return new VisualLifecycleInstance(adapterId, blockId, pos, startGameTime, profile, nextStage, externalState, source);
    }

    public VisualLifecycleInstance withExternalState(VisualLifecycleExternalState nextState) {
        return new VisualLifecycleInstance(adapterId, blockId, pos, startGameTime, profile, forcedStage, nextState, source);
    }
}
