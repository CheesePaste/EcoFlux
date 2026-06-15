package com.s.ecoflux.worldgen.feature;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.config.biome.BiomeRules;
import com.s.ecoflux.config.biome.BiomeRulesRegistry;
import com.s.ecoflux.config.plant.PathPlantEntry;
import com.s.ecoflux.plant.TreeStructure;
import com.s.ecoflux.plant.tree.TreeGrowthHandler;
import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.spacecolonization.SpaceColonizationGenerator;
import com.s.ecoflux.plant.tree.spacecolonization.SpaceColonizationProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EcofluxTreeFeature extends Feature<NoneFeatureConfiguration> {

    /** Decoration → ChunkEvent.Load bridge: stores tree placements for tracking. */
    public static final Map<Long, List<PendingTreePlacement>> PENDING_TREES = new HashMap<>();

    public record PendingTreePlacement(BlockPos root, ResourceLocation speciesId, TreeStructure structure) {}

    public EcofluxTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        EcofluxConstants.LOGGER.debug("[EcofluxTree] place() called at origin {}", origin);

        Holder<Biome> biomeHolder = level.getBiome(origin);
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        if (biomeKey == null) {
            EcofluxConstants.LOGGER.debug("[EcofluxTree] no biome key at {}", origin);
            return false;
        }

        Optional<BiomeRules> rulesOpt = BiomeRulesRegistry.getRules(biomeKey.location());
        if (rulesOpt.isEmpty()) {
            EcofluxConstants.LOGGER.debug("[EcofluxTree] no BiomeRules for {}", biomeKey.location());
            return false;
        }
        BiomeRules rules = rulesOpt.get();

        List<TreeSpeciesEntry> treeSpecies = collectTreeSpecies(rules);
        if (treeSpecies.isEmpty()) {
            EcofluxConstants.LOGGER.debug("[EcofluxTree] no tree species in BiomeRules for {} (plants={})",
                    biomeKey.location(), rules.plants().size());
            return false;
        }

        int treeCount = sampleTreeCount(rules, treeSpecies, random);
        if (treeCount <= 0) return false;

        int chunkMinX = origin.getX() & ~15;
        int chunkMinZ = origin.getZ() & ~15;
        int placed = 0;

        for (int attempt = 0; attempt < treeCount * 6 && placed < treeCount; attempt++) {
            int x = chunkMinX + random.nextInt(16);
            int z = chunkMinZ + random.nextInt(16);
            int heightVal = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            if (heightVal <= level.getMinBuildHeight()) continue;
            int surfaceY = heightVal - 1;
            BlockPos groundPos = new BlockPos(x, surfaceY, z);

            BlockState groundState = level.getBlockState(groundPos);
            if (!groundState.is(BlockTags.DIRT)) continue;

            TreeSpeciesEntry species = pickWeighted(treeSpecies, random);
            SpaceColonizationProfile scProfile = species.scProfile();
            boolean is2x2 = scProfile.is2x2();

            BlockPos genPos = groundPos;
            if (is2x2) {
                if (!check2x2Ground(level, groundPos)) continue;
            }

            int height = scProfile.resolveHeight(random);
            SpaceColonizationGenerator.FullTreePlan plan;
            try {
                plan = SpaceColonizationGenerator.generateFull(
                        genPos, scProfile.scParams(), height, is2x2, random);
            } catch (Exception e) {
                EcofluxConstants.LOGGER.warn("SC tree generation failed at {}", genPos, e);
                continue;
            }

            // Place blocks via WorldGenLevel (correct API during decoration)
            Block logBlock = scProfile.logBlock();
            Block leavesBlock = scProfile.leavesBlock();
            BlockState leafState = leavesBlock.defaultBlockState()
                    .setValue(LeavesBlock.DISTANCE, 1)
                    .setValue(LeavesBlock.PERSISTENT, true);

            for (BlockPos logPos : plan.logPositions()) {
                if (level.isAreaLoaded(logPos, 0)) {
                    level.setBlock(logPos, logBlock.defaultBlockState(), 3);
                }
            }
            for (BlockPos leafPos : plan.leafPositions()) {
                if (!level.isAreaLoaded(leafPos, 0)) continue;
                BlockState existing = level.getBlockState(leafPos);
                if (existing.isAir() || existing.is(BlockTags.LEAVES) || existing.canBeReplaced()) {
                    level.setBlock(leafPos, leafState, 3);
                }
            }

            // Store for later tracking via PENDING_TREES bridge
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(
                    genPos.getX() >> 4, genPos.getZ() >> 4);
            TreeStructure structure = new TreeStructure(
                    plan.logPositions().stream().mapToLong(BlockPos::asLong).toArray(),
                    plan.leafPositions().stream().mapToLong(BlockPos::asLong).toArray());
            PENDING_TREES.computeIfAbsent(chunkKey, k -> new ArrayList<>())
                    .add(new PendingTreePlacement(genPos.immutable(), species.saplingId(), structure));

            placed++;
        }

        EcofluxConstants.LOGGER.debug("[EcofluxTree] placed {}/{} trees in chunk [{}, {}]",
                placed, treeCount, chunkMinX >> 4, chunkMinZ >> 4);
        return placed > 0;
    }

    // ── Species resolution ──────────────────────────────────────────────

    private record TreeSpeciesEntry(
            ResourceLocation saplingId,
            int weight,
            SpaceColonizationProfile scProfile) {}

    private static List<TreeSpeciesEntry> collectTreeSpecies(BiomeRules rules) {
        List<TreeSpeciesEntry> result = new ArrayList<>();
        for (PathPlantEntry entry : rules.plants()) {
            TreeGrowthProfile profile = TreeGrowthHandler.resolveProfile(entry.plantId());
            // Biome rules use minecraft: namespace; profiles are registered under ecoflux: namespace
            if (profile == null) {
                profile = TreeGrowthHandler.resolveProfile(
                        EcofluxConstants.id(entry.plantId().getPath()));
            }
            if (profile instanceof SpaceColonizationProfile scProfile) {
                result.add(new TreeSpeciesEntry(entry.plantId(), entry.weight(), scProfile));
            }
        }
        return result;
    }

    private static int sampleTreeCount(BiomeRules rules, List<TreeSpeciesEntry> treeSpecies, RandomSource random) {
        int totalWeight = rules.plants().stream().mapToInt(PathPlantEntry::weight).sum();
        int treeWeight = treeSpecies.stream().mapToInt(e -> e.weight).sum();
        if (totalWeight == 0 || treeWeight == 0) return 0;

        double treeRatio = (double) treeWeight / totalWeight;
        int base = (int) Math.round(treeRatio * rules.maxPlantCount());
        int variation = Math.max(1, base / 3);
        int raw = base - variation + random.nextInt(variation * 2 + 1);
        return Math.max(0, Math.min(raw, 20));
    }

    private static TreeSpeciesEntry pickWeighted(List<TreeSpeciesEntry> entries, RandomSource random) {
        int total = entries.stream().mapToInt(e -> e.weight).sum();
        if (total <= 0) return entries.get(0);
        int r = random.nextInt(total);
        for (TreeSpeciesEntry e : entries) {
            r -= e.weight;
            if (r < 0) return e;
        }
        return entries.get(entries.size() - 1);
    }

    // ── 2x2 ground check ────────────────────────────────────────────────

    private static boolean check2x2Ground(WorldGenLevel level, BlockPos nwGround) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos g = nwGround.offset(dx, 0, dz);
                if (!level.getBlockState(g).is(BlockTags.DIRT)) return false;
                BlockPos a = g.above();
                BlockState as = level.getBlockState(a);
                if (!as.isAir() && !as.canBeReplaced()) return false;
            }
        }
        return true;
    }
}
