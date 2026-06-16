package com.cp.ecoflux.client.key;

/**
 * Client-side keybinding registration for the Ecoflux panel screen.
 *
 * <p>Structure: registers {@code OPEN_PANEL} keybinding (default M) on the
 * mod event bus via {@link RegisterKeyMappingsEvent}. The public static
 * {@code KeyMapping} instance is consumed by {@link EcofluxPanelClientEvents}
 * each client tick to toggle the panel screen.
 *
 * <p>Role in Ecoflux: one of two client-side event subscribers that together
 * provide the keybinding → screen toggle pipeline.
 */

import com.cp.ecoflux.EcofluxConstants;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EcofluxConstants.MOD_ID, value = Dist.CLIENT)
public final class EcofluxPanelKeyBindings {

    public static final KeyMapping OPEN_PANEL = new KeyMapping(
            "key.ecoflux.open_panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.ecoflux");

    private EcofluxPanelKeyBindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PANEL);
    }
}
