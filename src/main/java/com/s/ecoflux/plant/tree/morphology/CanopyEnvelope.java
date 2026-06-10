package com.s.ecoflux.plant.tree.morphology;

import java.util.List;
import net.minecraft.core.BlockPos;

public final class CanopyEnvelope {

    private CanopyEnvelope() {}

    public enum CanopyType {
        ELLIPSOID,
        TALL_ELLIPSOID,
        CONE,
        CLUSTERED_ELLIPSOID,
        FLAT_CYLINDER,
        FLAT_DISC_CLUSTERED
    }

    @FunctionalInterface
    public interface DensityFunction {
        double density(int x, int y, int z);
    }

    public record CanopyConfig(
            CanopyType type,
            int trunkCenterX,
            int trunkCenterZ,
            int trunkBaseY,
            int currentTrunkY,
            int finalTrunkY,
            int resolvedHeight,
            double canopyRadiusXZ,
            double canopyRadiusY,
            double canopyCenterYBias,
            double edgeFeather,
            int subClusters,
            double subClusterRadius,
            List<BlockPos> branchNodes,
            List<Double> branchNodeRadii,
            double stageProgress
    ) {}

    public static DensityFunction createDensityFunction(CanopyConfig cfg) {
        return switch (cfg.type()) {
            case ELLIPSOID -> ellipsoidDensity(cfg);
            case TALL_ELLIPSOID -> tallEllipsoidDensity(cfg);
            case CONE -> coneDensity(cfg);
            case CLUSTERED_ELLIPSOID -> clusteredEllipsoidDensity(cfg);
            case FLAT_CYLINDER -> cylinderDensity(cfg);
            case FLAT_DISC_CLUSTERED -> discClusteredDensity(cfg);
        };
    }

    private static final double FLAT_TOP = 0.55;

    private static DensityFunction ellipsoidDensity(CanopyConfig cfg) {
        double stageFactor = cfg.stageProgress();
        double fullRx = cfg.resolvedHeight() * cfg.canopyRadiusXZ();
        double fullRy = cfg.resolvedHeight() * cfg.canopyRadiusY();
        double rx = 1.5 + (fullRx - 1.5) * stageFactor;
        double ry = 1.5 + (fullRy - 1.5) * stageFactor;

        double finalCenterY = cfg.finalTrunkY() + cfg.resolvedHeight() * cfg.canopyCenterYBias();
        double startCenterY = cfg.trunkBaseY() + 2;
        double centerY = startCenterY + (finalCenterY - startCenterY) * stageFactor;
        final int cx = cfg.trunkCenterX();
        final int cz = cfg.trunkCenterZ();

        return (x, y, z) -> {
            double dx = (x - cx) / rx;
            double dy = (y - centerY) / ry;
            double dz = (z - cz) / rx;
            double main = flatTopEllipsoidDensity(dx, dy, dz, cfg.edgeFeather());
            double branch = branchNodeDensity(x, y, z, cfg);
            return Math.max(main, branch);
        };
    }

    private static DensityFunction tallEllipsoidDensity(CanopyConfig cfg) {
        double stageFactor = cfg.stageProgress();
        double fullRx = cfg.canopyRadiusXZ();
        double fullRy = cfg.canopyRadiusY();
        double rx = 1.0 + (fullRx - 1.0) * stageFactor;
        double ry = 2.0 + (fullRy - 2.0) * stageFactor;

        double finalCenterY = cfg.finalTrunkY() - 2;
        double startCenterY = cfg.trunkBaseY() + 4;
        double centerY = startCenterY + (finalCenterY - startCenterY) * stageFactor;
        final int cx = cfg.trunkCenterX();
        final int cz = cfg.trunkCenterZ();

        return (x, y, z) -> {
            double dx = (x - cx) / rx;
            double dy = (y - centerY) / ry;
            double dz = (z - cz) / rx;
            double main = flatTopEllipsoidDensity(dx, dy, dz, cfg.edgeFeather());
            double branch = branchNodeDensity(x, y, z, cfg);
            return Math.max(main, branch);
        };
    }

    private static DensityFunction coneDensity(CanopyConfig cfg) {
        int clearTrunk = 2;
        int foliageStartY = cfg.trunkBaseY() + 1 + clearTrunk;
        int tipY = cfg.currentTrunkY() + 1;
        double fullBaseRadius = cfg.resolvedHeight() * cfg.canopyRadiusXZ();
        double stageFactor = cfg.stageProgress();
        double maxRadius = 1.5 + (fullBaseRadius - 1.5) * stageFactor;
        final int cx = cfg.trunkCenterX();
        final int cz = cfg.trunkCenterZ();

        return (x, y, z) -> {
            if (y < foliageStartY || y > tipY) return 0;
            double heightRatio = (double) (y - foliageStartY) / Math.max(1, tipY - foliageStartY);
            double sliceRadius = maxRadius * (1.0 - heightRatio);
            if (sliceRadius < 0.5) return 0;
            double dist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (dist > sliceRadius) return 0;
            double normDist = dist / sliceRadius;
            double main = flatTopRadialDensity(normDist, cfg.edgeFeather());
            double branch = branchNodeDensity(x, y, z, cfg);
            return Math.max(main, branch);
        };
    }

    private static DensityFunction clusteredEllipsoidDensity(CanopyConfig cfg) {
        double stageFactor = cfg.stageProgress();
        double fullRx = cfg.resolvedHeight() * cfg.canopyRadiusXZ();
        double fullRy = cfg.resolvedHeight() * cfg.canopyRadiusY();
        double rx = 1.5 + (fullRx - 1.5) * stageFactor;
        double ry = 1.5 + (fullRy - 1.5) * stageFactor;

        double finalCenterY = cfg.finalTrunkY() + cfg.resolvedHeight() * cfg.canopyCenterYBias();
        double startCenterY = cfg.trunkBaseY() + 3;
        double mainCenterY = startCenterY + (finalCenterY - startCenterY) * stageFactor;
        final int cx = cfg.trunkCenterX();
        final int cz = cfg.trunkCenterZ();

        return (x, y, z) -> {
            double dx = (x - cx) / rx;
            double dy = (y - mainCenterY) / ry;
            double dz = (z - cz) / rx;
            double main = flatTopEllipsoidDensity(dx, dy, dz, cfg.edgeFeather());
            double branch = branchNodeDensity(x, y, z, cfg);
            return Math.max(main, branch);
        };
    }

    private static DensityFunction cylinderDensity(CanopyConfig cfg) {
        double stageFactor = cfg.stageProgress();
        double fullRx = cfg.canopyRadiusXZ();
        double fullRy = cfg.canopyRadiusY();
        double rx = 1.5 + (fullRx - 1.5) * stageFactor;
        double ry = 1.5 + (fullRy - 1.5) * stageFactor;

        double finalCenterY = cfg.finalTrunkY() + cfg.resolvedHeight() * cfg.canopyCenterYBias();
        double startCenterY = cfg.trunkBaseY() + 2;
        double centerY = startCenterY + (finalCenterY - startCenterY) * stageFactor;
        final int cx = cfg.trunkCenterX();
        final int cz = cfg.trunkCenterZ();

        return (x, y, z) -> {
            double hDist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            double vDist = Math.abs(y - centerY);
            if (hDist > rx || vDist > ry) return 0;
            double hNorm = hDist / rx;
            double vNorm = vDist / ry;
            double main = Math.min(
                    flatTopRadialDensity(hNorm, cfg.edgeFeather()),
                    flatTopRadialDensity(vNorm, cfg.edgeFeather()));
            double branch = branchNodeDensity(x, y, z, cfg);
            return Math.max(main, branch);
        };
    }

    private static DensityFunction discClusteredDensity(CanopyConfig cfg) {
        double stageFactor = cfg.stageProgress();
        double fullRx = cfg.canopyRadiusXZ();
        double fullRy = cfg.canopyRadiusY();
        double rx = 1.5 + (fullRx - 1.5) * stageFactor;
        double ry = 0.8 + (fullRy - 0.8) * stageFactor;

        double discCenterY = cfg.finalTrunkY() - 1;
        final int cx = cfg.trunkCenterX();
        final int cz = cfg.trunkCenterZ();

        return (x, y, z) -> {
            double dx = (x - cx) / rx;
            double dy = (y - discCenterY) / ry;
            double dz = (z - cz) / rx;
            double discMain = flatTopEllipsoidDensity(dx, dy, dz, cfg.edgeFeather());
            double branch = branchNodeDensity(x, y, z, cfg);
            return Math.max(discMain, branch);
        };
    }

    private static double flatTopEllipsoidDensity(double dx, double dy, double dz, double feather) {
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq >= 1.0) return 0;
        double ftSq = FLAT_TOP * FLAT_TOP;
        if (distSq <= ftSq) return 1.0;
        double raw = 1.0 - (distSq - ftSq) / (1.0 - ftSq);
        if (feather > 0 && raw < feather) {
            double t = raw / feather;
            return t * t * (3.0 - 2.0 * t);
        }
        return raw;
    }

    private static double flatTopRadialDensity(double normDist, double feather) {
        if (normDist >= 1.0) return 0;
        if (normDist <= FLAT_TOP) return 1.0;
        double raw = 1.0 - (normDist - FLAT_TOP) / (1.0 - FLAT_TOP);
        if (feather > 0 && raw < feather) {
            double t = raw / feather;
            return t * t * (3.0 - 2.0 * t);
        }
        return raw;
    }

    private static double branchNodeDensity(int x, int y, int z, CanopyConfig cfg) {
        List<BlockPos> nodes = cfg.branchNodes();
        List<Double> radii = cfg.branchNodeRadii();
        if (nodes == null || nodes.isEmpty()) return 0;
        double defaultSr = cfg.subClusterRadius() > 0 ? cfg.subClusterRadius() : 3.5;
        double best = 0;
        double feather = cfg.edgeFeather() + 0.15;
        for (int i = 0; i < nodes.size(); i++) {
            double sr = (radii != null && i < radii.size()) ? radii.get(i) : defaultSr;
            if (sr < 0.3) continue;
            BlockPos node = nodes.get(i);
            if (Math.abs(x - node.getX()) > sr + 1) continue;
            if (Math.abs(y - node.getY()) > sr + 1) continue;
            if (Math.abs(z - node.getZ()) > sr + 1) continue;
            double sdx = (x - node.getX()) / sr;
            double sdy = (y - node.getY()) / sr;
            double sdz = (z - node.getZ()) / sr;
            double sub = flatTopEllipsoidDensity(sdx, sdy, sdz, feather);
            if (sub > 0 && y < node.getY()) {
                int below = node.getY() - y;
                sub *= Math.max(0.0, 1.0 - (below - 1) / 1.8);
            }
            if (sub > best) {
                best = sub;
                if (best >= 1.0) return 1.0;
            }
        }
        return best;
    }
}
