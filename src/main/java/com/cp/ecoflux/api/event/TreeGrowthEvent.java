package com.cp.ecoflux.api.event;

import com.cp.ecoflux.plant.TreeStructure;
import com.cp.ecoflux.plant.tree.TreeGrowthSession;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public abstract class TreeGrowthEvent extends Event {
    private final ServerLevel level;
    private final BlockPos saplingPos;
    private final TreeGrowthSession session;

    TreeGrowthEvent(ServerLevel level, BlockPos saplingPos, TreeGrowthSession session) {
        this.level = level;
        this.saplingPos = saplingPos;
        this.session = session;
    }

    public ServerLevel level() { return level; }
    public BlockPos saplingPos() { return saplingPos; }
    public TreeGrowthSession session() { return session; }

    /** A tree growth session has started. */
    public static final class Start extends TreeGrowthEvent {
        private final ResourceLocation treeType;

        public Start(ServerLevel level, BlockPos saplingPos, TreeGrowthSession session, ResourceLocation treeType) {
            super(level, saplingPos, session);
            this.treeType = treeType;
        }

        public ResourceLocation treeType() { return treeType; }
    }

    /** A single growth stage completed. */
    public static final class Stage extends TreeGrowthEvent {
        private final int currentStage;
        private final int totalStages;

        public Stage(ServerLevel level, BlockPos saplingPos, TreeGrowthSession session,
                     int currentStage, int totalStages) {
            super(level, saplingPos, session);
            this.currentStage = currentStage;
            this.totalStages = totalStages;
        }

        public int currentStage() { return currentStage; }
        public int totalStages() { return totalStages; }
    }

    /** Tree growth completed, tree structure now tracked. */
    public static final class Complete extends TreeGrowthEvent {
        private final TreeStructure structure;

        public Complete(ServerLevel level, BlockPos saplingPos, TreeGrowthSession session, TreeStructure structure) {
            super(level, saplingPos, session);
            this.structure = structure;
        }

        public TreeStructure structure() { return structure; }
    }
}
