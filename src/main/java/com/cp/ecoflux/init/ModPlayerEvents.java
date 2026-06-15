package com.cp.ecoflux.init;

/**
 * Player-triggered block event handlers for vegetation tracking.
 *
 * <p>Structure: listens to {@code BlockEvent.EntityPlaceEvent} and
 * {@code BlockEvent.BreakEvent} on the NeoForge event bus. On place it calls
 * {@code VegetationTracker.trackAt}; on break it calls
 * {@code VegetationTracker.untrack}.
 * <p>Role in Ecoflux: keeps vegetation records in sync when players manually
 * place or destroy plants.
 */

import com.cp.ecoflux.config.plant.PlantDefinition;
import com.cp.ecoflux.config.plant.PlantRegistry;
import com.cp.ecoflux.plant.VegetationTracker;
import com.cp.ecoflux.plant.tree.TreeGrowthHandler;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class ModPlayerEvents {
    private ModPlayerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ModPlayerEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ModPlayerEvents::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ModPlayerEvents::onClockRightClick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ModPlayerEvents::onBoneMealLog);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ModPlayerEvents::onPlayerLoggedIn);
    }

    private static final Component WELCOME_MESSAGE = Component.empty()
            .append(Component.literal("🌿 Ecoflux 生态演替模组已加载").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0x00AA00)).withBold(true)))
            .append(Component.literal("\n"))
            .append(Component.literal("  /ecoflux auto on  ").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0x55FF55))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ecoflux auto on"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击填入命令")))))
            .append(Component.literal("开启全局自动演替 (必须)").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0xAAAAAA))))
            .append(Component.literal("\n"))
            .append(Component.literal("  /ecoflux speed <倍率>  ").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0x55FFFF))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ecoflux speed "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击填入命令")))))
            .append(Component.literal("调整演替速度 (默认 1.0)").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0xAAAAAA))))
            .append(Component.literal("\n"))
            .append(Component.literal("  /ecoflux auto status  ").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0x55FF55))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ecoflux auto status"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击填入命令")))))
            .append(Component.literal("查看当前状态").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0xAAAAAA))))
            .append(Component.literal("\n"))
            .append(Component.literal("  /ecoflux lifecycle chunk  ").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0x55FFFF))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ecoflux lifecycle chunk"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击填入命令")))))
            .append(Component.literal("查看区块植物信息").setStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(0xAAAAAA))));

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        event.getEntity().displayClientMessage(WELCOME_MESSAGE, false);
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        LevelChunk chunk = getChunk(level, pos);
        if (chunk == null) {
            return;
        }

        net.minecraft.resources.ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        PlantDefinition plantDef = PlantRegistry.INSTANCE.getDefinition(blockId)
                .orElse(null);
        if (plantDef == null) return;
        VegetationTracker.INSTANCE.trackAt(level, chunk, pos, Optional.empty(), Optional.empty(), plantDef);
    }

    private static void onBlockBroken(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) levelAccessor;
        BlockPos pos = event.getPos();
        LevelChunk chunk = getChunk(level, pos);
        if (chunk == null) {
            return;
        }

        VegetationTracker.INSTANCE.untrack(chunk, pos);
    }

    private static void onClockRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!event.getItemStack().is(Items.CLOCK)) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        LevelChunk chunk = getChunk(level, pos);
        if (chunk == null) {
            return;
        }

        String result = VegetationTracker.INSTANCE.advanceStage(level, chunk, pos);
        event.getEntity().displayClientMessage(
                net.minecraft.network.chat.Component.literal(result), false);
        event.setCanceled(true);
    }

    private static void onBoneMealLog(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getItemStack().getItem() instanceof BoneMealItem)) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        LevelChunk chunk = getChunk(level, pos);
        if (chunk == null) return;

        var session = TreeGrowthHandler.INSTANCE.findSessionForSapling(level, pos);
        if (session == null) return;

        event.setCanceled(true);

        if (session.isComplete()) {
            return;
        }

        if (TreeGrowthHandler.INSTANCE.forceAdvanceStage(level, pos)) {
            Direction face = event.getFace();
            BlockPos particlePos = face != null ? pos.relative(face) : pos.above();
            level.levelEvent(1505, particlePos, 0);
            if (!event.getEntity().isCreative()) {
                event.getItemStack().shrink(1);
            }
        }
    }

    private static LevelChunk getChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
