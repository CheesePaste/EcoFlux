package com.s.ecoflux.plant.tree;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * ThreadLocal capture state used by {@link com.s.ecoflux.mixin.LevelMixin}
 * and {@link com.s.ecoflux.mixin.MushroomBlockMixin}.
 * <p>
 * Lives in a standalone class because Mixin classes cannot contain non-private static fields.
 */
public final class GrowthBlockCapture {
    public static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<List<BlockPos>> BLOCKS = ThreadLocal.withInitial(ArrayList::new);

    private GrowthBlockCapture() {}
}
