package com.cp.ecoflux.api.event;

import com.cp.ecoflux.api.data.ActiveVegetationRecord;
import com.cp.ecoflux.api.data.VegetationLifecycleStage;
import com.cp.ecoflux.api.data.VegetationObservation;
import com.cp.ecoflux.api.data.VegetationTransformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

public abstract class VegetationLifecycleEvent extends Event {
    private final ServerLevel level;
    private final ActiveVegetationRecord record;

    VegetationLifecycleEvent(ServerLevel level, ActiveVegetationRecord record) {
        this.level = level;
        this.record = record;
    }

    public ServerLevel level() { return level; }
    public ActiveVegetationRecord record() { return record; }

    /** A plant is newly tracked (born). */
    public static final class Born extends VegetationLifecycleEvent {
        private final BlockPos pos;
        private final BlockState state;

        public Born(ServerLevel level, ActiveVegetationRecord record, BlockPos pos, BlockState state) {
            super(level, record);
            this.pos = pos;
            this.state = state;
        }

        public BlockPos pos() { return pos; }
        public BlockState state() { return state; }
    }

    /** A plant's lifecycle stage changed. */
    public static final class StageChange extends VegetationLifecycleEvent {
        private final VegetationLifecycleStage oldStage;
        private final VegetationLifecycleStage newStage;

        public StageChange(ServerLevel level, ActiveVegetationRecord record,
                           VegetationLifecycleStage oldStage, VegetationLifecycleStage newStage) {
            super(level, record);
            this.oldStage = oldStage;
            this.newStage = newStage;
        }

        public VegetationLifecycleStage oldStage() { return oldStage; }
        public VegetationLifecycleStage newStage() { return newStage; }
    }

    /** A plant died/disappeared and will be untracked. */
    public static final class Death extends VegetationLifecycleEvent {
        private final VegetationObservation lastObservation;

        public Death(ServerLevel level, ActiveVegetationRecord record, VegetationObservation lastObservation) {
            super(level, record);
            this.lastObservation = lastObservation;
        }

        public VegetationObservation lastObservation() { return lastObservation; }
    }

    /** A plant transformed (e.g. sapling → tree structure). */
    public static final class Transformed extends VegetationLifecycleEvent {
        private final VegetationTransformation transformation;

        public Transformed(ServerLevel level, ActiveVegetationRecord record, VegetationTransformation transformation) {
            super(level, record);
            this.transformation = transformation;
        }

        public VegetationTransformation transformation() { return transformation; }
    }
}
