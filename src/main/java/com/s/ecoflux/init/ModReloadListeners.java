package com.s.ecoflux.init;

/**
 * Registers the succession config loader as a JSON resource reload listener.
 *
 * <p>Structure: listens to {@code AddReloadListenerEvent} and adds
 * {@code SuccessionConfigLoader.INSTANCE}, which is a
 * {@code SimpleJsonResourceReloadListener}.
 * <p>Role in Ecoflux: enables hot-reload of succession path JSON files via
 * {@code /reload} without restarting the server.
 */

import com.s.ecoflux.config.SuccessionConfigLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class ModReloadListeners {
    private ModReloadListeners() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModReloadListeners::onAddReloadListener);
    }

    private static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(SuccessionConfigLoader.INSTANCE);
    }
}
