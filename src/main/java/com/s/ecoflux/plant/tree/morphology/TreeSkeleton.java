package com.s.ecoflux.plant.tree.morphology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class TreeSkeleton {
    private final List<SkeletonNode> nodes = new ArrayList<>();
    private int rootIndex = -1;
    private final List<Integer> trunkPath = new ArrayList<>();
    private final List<Integer> primaryBranches = new ArrayList<>();
    private BlockPos saplingPos;
    private int trunkLevels;

    public TreeSkeleton() {}

    public int addNode(SkeletonNode node) {
        int index = nodes.size();
        nodes.add(node);
        return index;
    }

    public SkeletonNode getNode(int index) {
        return nodes.get(index);
    }

    public List<SkeletonNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public int rootIndex() {
        return rootIndex;
    }

    public void setRootIndex(int index) {
        this.rootIndex = index;
    }

    public List<Integer> trunkPath() {
        return Collections.unmodifiableList(trunkPath);
    }

    public void addTrunkNode(int index) {
        trunkPath.add(index);
    }

    public List<Integer> primaryBranches() {
        return Collections.unmodifiableList(primaryBranches);
    }

    public void addPrimaryBranch(int index) {
        primaryBranches.add(index);
    }

    public BlockPos saplingPos() {
        return saplingPos;
    }

    public void setSaplingPos(BlockPos pos) {
        this.saplingPos = pos;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int trunkHeight() {
        return trunkLevels > 0 ? trunkLevels : trunkPath.size();
    }

    public void setTrunkLevels(int levels) {
        this.trunkLevels = levels;
    }
}
