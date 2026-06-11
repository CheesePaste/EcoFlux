package com.s.ecoflux;

/**
 * Central constants for the Ecoflux mod.
 *
 * <p>Structure: holds {@code MOD_ID}, {@code MOD_NAME}, {@code LOGGER}, and a
 * {@code ResourceLocation} factory helper {@link #id(String)}.
 * <p>Role in Ecoflux: single source of truth for the mod namespace and logging,
 * used by every subsystem.
 */

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EcofluxConstants {
    public static final String MOD_ID = "ecoflux";
    public static final String MOD_NAME = "Ecoflux";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private EcofluxConstants() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
