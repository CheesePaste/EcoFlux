package com.cp.ecoflux.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class SuccessionEvent extends Event {
    private final ServerLevel level;
    private final LevelChunk chunk;

    SuccessionEvent(ServerLevel level, LevelChunk chunk) {
        this.level = level;
        this.chunk = chunk;
    }

    public ServerLevel level() { return level; }
    public LevelChunk chunk() { return chunk; }

    /** Fired after succession evaluation completes. Cancel to veto the progress change. */
    public static final class Evaluate extends SuccessionEvent {
        private final int totalPoints;
        private final int consuming;
        private final double oldProgress;
        private final double newProgress;

        public Evaluate(ServerLevel level, LevelChunk chunk,
                        int totalPoints, int consuming, double oldProgress, double newProgress) {
            super(level, chunk);
            this.totalPoints = totalPoints;
            this.consuming = consuming;
            this.oldProgress = oldProgress;
            this.newProgress = newProgress;
        }

        public int totalPoints() { return totalPoints; }
        public int consuming() { return consuming; }
        public double oldProgress() { return oldProgress; }
        public double newProgress() { return newProgress; }
    }

    /** Fired before a biome transition. Cancel to block the transition. */
    public static final class PreTransition extends SuccessionEvent implements ICancellableEvent {
        private final ResourceLocation fromBiome;
        private final ResourceLocation toBiome;

        public PreTransition(ServerLevel level, LevelChunk chunk,
                             ResourceLocation fromBiome, ResourceLocation toBiome) {
            super(level, chunk);
            this.fromBiome = fromBiome;
            this.toBiome = toBiome;
        }

        public ResourceLocation fromBiome() { return fromBiome; }
        public ResourceLocation toBiome() { return toBiome; }
    }

    /** Fired before a biome regression. Cancel to block the regression. */
    public static final class PreRegression extends SuccessionEvent implements ICancellableEvent {
        private final ResourceLocation fromBiome;
        private final ResourceLocation toBiome;

        public PreRegression(ServerLevel level, LevelChunk chunk,
                             ResourceLocation fromBiome, ResourceLocation toBiome) {
            super(level, chunk);
            this.fromBiome = fromBiome;
            this.toBiome = toBiome;
        }

        public ResourceLocation fromBiome() { return fromBiome; }
        public ResourceLocation toBiome() { return toBiome; }
    }

    /** Fired after a biome transition completes. */
    public static final class PostTransition extends SuccessionEvent {
        private final ResourceLocation fromBiome;
        private final ResourceLocation toBiome;

        public PostTransition(ServerLevel level, LevelChunk chunk,
                              ResourceLocation fromBiome, ResourceLocation toBiome) {
            super(level, chunk);
            this.fromBiome = fromBiome;
            this.toBiome = toBiome;
        }

        public ResourceLocation fromBiome() { return fromBiome; }
        public ResourceLocation toBiome() { return toBiome; }
    }
}
