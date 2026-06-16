package com.cp.ecoflux.config.plant;

import com.cp.ecoflux.EcofluxConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Custom block tags for Ecoflux vegetation matching and visual behavior.
 * Modpack authors can extend these via datapack to add/remove blocks.
 */
public final class EcofluxBlockTags {
    /** Blocks that SimplePlantAdapter recognizes as simple vegetation. */
    public static final TagKey<Block> SIMPLE_VEGETATION = create("simple_vegetation");

    /** Blocks that should use the grass visual lifecycle (scale + growth animation). */
    public static final TagKey<Block> GRASS_COVER = create("grass_cover");

    /** Blocks that should be tinted with biome grass color. */
    public static final TagKey<Block> USES_GRASS_TINT = create("uses_grass_tint");

    /** Blocks that should be tinted with biome foliage color. */
    public static final TagKey<Block> USES_FOLIAGE_TINT = create("uses_foliage_tint");

    private EcofluxBlockTags() {}

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, EcofluxConstants.id(name));
    }
}
