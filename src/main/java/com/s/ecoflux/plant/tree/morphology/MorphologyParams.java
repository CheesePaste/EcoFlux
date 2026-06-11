package com.s.ecoflux.plant.tree.morphology;

/**
 * Species morphology parameter record that drives the entire parametric tree-generation pipeline.
 *
 * <p>Structure: Immutable data record with fields for trunk dimensions, branch
 * geometry (angles, counts, length ratios), secondary branch settings, canopy shape
 * ({@link CanopyEnvelope.CanopyType}), leaf placement parameters, and staging
 * configuration. Provides {@link #resolveHeight} and {@link #totalStagesForHeight}
 * calculation methods.
 *
 * <p>Role in Ecoflux: The configuration object encoding species-specific tree
 * morphology. Created by static factory methods or directly by
 * {@link com.s.ecoflux.plant.tree.TreeGrowthProfile#morphologyParams()}. Consumed by
 * {@link SkeletonGenerator}, {@link CanopyEnvelope}, and {@link TreeMorphology} to
 * produce a complete parametric tree shape.
 */

import net.minecraft.util.RandomSource;

public record MorphologyParams(
        int minTrunkHeight,
        int maxTrunkHeight,
        int ticksPerStage,
        boolean is2x2,

        float maxLeanAngle,

        int minBranches,
        int maxBranches,
        float branchStartRatio,
        float branchEndRatio,
        float branchAngleMin,
        float branchAngleMax,
        float branchLengthRatio,
        boolean hasSecondary,
        float secondaryProb,
        int secondaryMaxLength,

        CanopyEnvelope.CanopyType canopyType,
        double canopyRadiusXZ,
        double canopyRadiusY,
        double canopyCenterYBias,
        double leafDensity,
        double edgeFeather,
        double branchClustering,

        int subClusters,
        double subClusterRadius,

        int canopyStages
) {
    public int totalStagesForHeight(int resolvedHeight) {
        return resolvedHeight + canopyStages;
    }

    public int resolveHeight(RandomSource random) {
        int range = maxTrunkHeight - minTrunkHeight;
        return minTrunkHeight + (range > 0 ? random.nextInt(range + 1) : 0);
    }
}
