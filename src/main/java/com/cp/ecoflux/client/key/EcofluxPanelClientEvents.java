package com.cp.ecoflux.client.key;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.client.ui.EcofluxPanelScreen;
import com.cp.ecoflux.network.PanelDataPayload;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side glue: keybinding toggle + stores latest {@link PanelDataPayload}.
 */

@EventBusSubscriber(modid = EcofluxConstants.MOD_ID, value = Dist.CLIENT)
public final class EcofluxPanelClientEvents {

    private static volatile PanelDataPayload latestPanelData;
    private static final Set<ChunkPos> knownExcludedChunks =
            Collections.synchronizedSet(new HashSet<>());

    private EcofluxPanelClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (EcofluxPanelKeyBindings.OPEN_PANEL.consumeClick()) {
            if (mc.screen instanceof EcofluxPanelScreen) {
                mc.screen.onClose();
            } else if (mc.screen == null) {
                mc.setScreen(new EcofluxPanelScreen());
            }
        }
    }

    public static void handlePanelData(PanelDataPayload payload) {
        latestPanelData = payload;
        // Replace excluded chunk set entirely from server data (8×8 viewport)
        knownExcludedChunks.clear();
        knownExcludedChunks.addAll(payload.excludedChunks());
    }

    public static PanelDataPayload getLatestPanelData() {
        return latestPanelData;
    }

    public static Set<ChunkPos> getKnownExcludedChunks() {
        return knownExcludedChunks;
    }
}
