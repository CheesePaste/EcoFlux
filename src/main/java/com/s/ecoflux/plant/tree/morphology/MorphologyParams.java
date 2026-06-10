package com.s.ecoflux.plant.tree.morphology;

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

    public static MorphologyParams oak() {
        return new MorphologyParams(
                6, 10, 3600,
                false,
                5f,
                5, 9, 0.25f, 0.85f,
                30f, 55f, 0.70f,
                true, 0.6f, 4,
                CanopyEnvelope.CanopyType.ELLIPSOID,
                0.40, 0.55, 0.05,
                1.24, 0.25, 0.5,
                0, 3.5, 4
        );
    }

    public static MorphologyParams birch() {
        return new MorphologyParams(
                7, 12, 2400,
                false,
                3f,
                0, 3, 0.65f, 0.90f,
                50f, 70f, 0.35f,
                false, 0, 0,
                CanopyEnvelope.CanopyType.TALL_ELLIPSOID,
                2.5, 6.0, -0.25,
                0.91, 0.30, 0.7,
                0, 2.0, 4
        );
    }

    public static MorphologyParams spruce() {
        return new MorphologyParams(
                10, 18, 4800,
                false,
                0f,
                12, 20, 0.10f, 1.0f,
                5f, 20f, 0.55f,
                false, 0, 0,
                CanopyEnvelope.CanopyType.CONE,
                0.40, 0, 0,
                1.24, 0.10, 0.15,
                0, 2.5, 4
        );
    }

    public static MorphologyParams jungle() {
        return new MorphologyParams(
                12, 18, 4800,
                true,
                3f,
                7, 12, 0.40f, 0.90f,
                20f, 45f, 0.75f,
                true, 0.6f, 5,
                CanopyEnvelope.CanopyType.CLUSTERED_ELLIPSOID,
                0.40, 0.45, 0.0,
                1.11, 0.20, 0.6,
                5, 3.5, 5
        );
    }

    public static MorphologyParams darkOak() {
        return new MorphologyParams(
                7, 11, 3600,
                true,
                2f,
                6, 10, 0.22f, 0.75f,
                25f, 45f, 0.55f,
                true, 0.5f, 4,
                CanopyEnvelope.CanopyType.FLAT_CYLINDER,
                2.5, 3.5, 0.15,
                1.30, 0.10, 0.25,
                0, 3.0, 4
        );
    }

    public static MorphologyParams acacia() {
        return new MorphologyParams(
                6, 11, 3600,
                false,
                18f,
                4, 7, 0.40f, 0.85f,
                10f, 40f, 0.60f,
                true, 0.45f, 3,
                CanopyEnvelope.CanopyType.FLAT_DISC_CLUSTERED,
                3.5, 1.8, 0,
                0.85, 0.35, 0.8,
                6, 3.5, 3
        );
    }
}
