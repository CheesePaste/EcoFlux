package com.cp.ecoflux.plant.tree.spacecolonization;

import net.minecraft.util.RandomSource;

public record SpaceColonizationParams(
        EnvelopeType envelopeType,
        double envelopeRadiusXZ,
        double envelopeHeight,
        double envelopeCenterYOffset,
        int upProbability,
        double splitChance,
        double branchLengthRatio,
        double secondaryChance,
        int lowestBranchHeight,
        int leafRadius,
        double leafDensity,
        int canopyStages
) {
    public enum EnvelopeType {
        ELLIPSOID,
        TALL_ELLIPSOID,
        CONE
    }

    public int resolveTrunkHeight(RandomSource random) {
        int base = (int) (envelopeCenterYOffset + envelopeHeight * 0.40);
        int variation = (int) (envelopeHeight * 0.25);
        return base + (variation > 0 ? random.nextInt(variation + 1) : 0);
    }

    public int totalStagesForHeight(int resolvedHeight) {
        return resolvedHeight + canopyStages;
    }

    // --- Presets ---
    // Parameter values derived from DynamicTrees species JSONs (MIT License).
    // DynamicTrees: https://github.com/ferreusveritas/DynamicTrees, Copyright (c) 2025 DynamicTreesTeam

    public static SpaceColonizationParams birch() {
        return new SpaceColonizationParams(
                EnvelopeType.TALL_ELLIPSOID,
                6.0, 18.0, 17.0,
                4, 0.05, 0.30, 0.05,
                4,
                3, 0.66, 6
        );
    }

    public static SpaceColonizationParams oak() {
        return new SpaceColonizationParams(
                EnvelopeType.ELLIPSOID,
                12.0, 15.0, 14.0,
                2, 0.12, 0.65, 0.35,
                3,
                4, 0.74, 7
        );
    }

    public static SpaceColonizationParams cherry() {
        return new SpaceColonizationParams(
                EnvelopeType.ELLIPSOID,
                16.0, 17.0, 15.0,
                1, 0.15, 0.60, 0.45,
                4,
                4, 0.72, 8
        );
    }

    public static SpaceColonizationParams spruce() {
        return new SpaceColonizationParams(
                EnvelopeType.CONE,
                8.0, 32.0, 19.0,
                3, 0.25, 0.20, 0.05,
                3,
                3, 0.78, 8
        );
    }

    public static SpaceColonizationParams jungle() {
        return new SpaceColonizationParams(
                EnvelopeType.ELLIPSOID,
                10.0, 20.0, 21.0,
                2, 0.15, 0.55, 0.40,
                5,
                4, 0.70, 8
        );
    }

    public static SpaceColonizationParams darkOak() {
        return new SpaceColonizationParams(
                EnvelopeType.ELLIPSOID,
                12.0, 10.0, 13.0,
                3, 0.15, 0.55, 0.40,
                3,
                4, 0.75, 7
        );
    }

    public static SpaceColonizationParams acacia() {
        return new SpaceColonizationParams(
                EnvelopeType.ELLIPSOID,
                14.0, 6.0, 12.0,
                2, 0.20, 0.45, 0.30,
                5,
                4, 0.65, 6
        );
    }

    public static SpaceColonizationParams mangrove() {
        return new SpaceColonizationParams(
                EnvelopeType.ELLIPSOID,
                8.0, 10.0, 8.0,
                2, 0.12, 0.50, 0.30,
                3,
                4, 0.65, 6
        );
    }
}
