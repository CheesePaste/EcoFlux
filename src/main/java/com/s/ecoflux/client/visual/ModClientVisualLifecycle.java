package com.s.ecoflux.client.visual;

/**
 * Client-side event subscriber for the visual lifecycle system.
 *
 * <p>Structure: registers a block color handler for per-plant tinting via
 * {@code RegisterColorHandlersEvent.Block}, debug commands under
 * {@code /ecoflux visual} for manual start/stop/inspect/stage control, and a
 * client tick handler that drives the {@link VisualLifecycleClientRuntime} each frame.
 * <p>Role in Ecoflux: this is the client-side mod entry point for the visual
 * lifecycle layer. The color handler delegates to
 * {@link VisualLifecycleClientRuntime#getRenderState} to compute tint colors,
 * which integrates with the {@code BlockRenderDispatcherMixin} to suppress
 * vanilla rendering for scaled plants and with
 * {@link VisualLifecycleWorldRenderer} to draw the scaled replacement.
 */

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.config.EcofluxBlockTags;
import com.s.ecoflux.config.VisualLifecycleClientConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = EcofluxConstants.MOD_ID, value = Dist.CLIENT)
public final class ModClientVisualLifecycle {
    private ModClientVisualLifecycle() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ecoflux")
                .then(Commands.literal("visual")
                        .then(Commands.literal("start").then(positionArguments((source, pos) -> send(source, VisualLifecycleClientRuntime.INSTANCE.start(pos)))))
                        .then(Commands.literal("stop").then(positionArguments((source, pos) -> send(source, VisualLifecycleClientRuntime.INSTANCE.stop(pos)))))
                        .then(Commands.literal("inspect").then(positionArguments((source, pos) -> send(source, VisualLifecycleClientRuntime.INSTANCE.inspect(pos)))))
                        .then(Commands.literal("stage").then(stageArguments()))
                        .then(Commands.literal("scale_override")
                                .then(scaleOverrideArguments())
                                .then(Commands.literal("clear").executes(context -> {
                                    VisualLifecycleClientConfig.clearDebugUniformScaleOverride();
                                    VisualLifecycleClientRuntime.INSTANCE.refreshAll();
                                    return send(context.getSource(), "视觉生命周期统一缩放覆盖已清除。");
                                })))
                        .then(Commands.literal("list").executes(context -> send(context.getSource(), VisualLifecycleClientRuntime.INSTANCE.list())))
                        .then(Commands.literal("clear").executes(context -> send(context.getSource(), VisualLifecycleClientRuntime.INSTANCE.clear())))
                        .then(Commands.literal("cycle").executes(context -> send(context.getSource(), VisualLifecycleClientRuntime.INSTANCE.toggleDemoCycle())))
                        .then(Commands.literal("help").executes(context -> send(
                                context.getSource(),
                                "视觉生命周期可追踪任意非空气方块。已调校预设："
                                        + VisualLifecycleRegistry.INSTANCE.supportSummary()
                                        + "。可用 /ecoflux visual scale_override 100 做巨型测试；阶段缩放位于 config/ecoflux-client.toml。")))));
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, getter, pos, tintIndex) -> {
                    if (getter == null || pos == null) {
                        return defaultItemTintColor(state);
                    }
                    VisualLifecycleRenderState renderState = VisualLifecycleClientRuntime.INSTANCE.getRenderState(pos, state);
                    if (renderState != null) {
                        return renderState.tintedColor();
                    }
                    return defaultTintColor(state, getter, pos);
                },
                VisualLifecycleRegistry.INSTANCE.colorHandledBlocks());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        VisualLifecycleClientRuntime.INSTANCE.tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            VisualLifecycleClientRuntime.INSTANCE.updateScaleTexture();
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> positionArguments(VisualCommand visualCommand) {
        return Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(context -> visualCommand.run(
                                        context.getSource(),
                                        new BlockPos(
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z"))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> stageArguments() {
        return Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .executes(context -> {
                                            VisualLifecycleStage stage = switch (StringArgumentType.getString(context, "stage").toLowerCase()) {
                                                case "born" -> VisualLifecycleStage.BORN;
                                                case "growing" -> VisualLifecycleStage.GROWING;
                                                case "mature" -> VisualLifecycleStage.MATURE;
                                                case "aging" -> VisualLifecycleStage.AGING;
                                                default -> throw new IllegalArgumentException("阶段必须是 born/growing/mature/aging");
                                            };
                                            return send(
                                                    context.getSource(),
                                                    VisualLifecycleClientRuntime.INSTANCE.forceStage(
                                                            new BlockPos(
                                                                    IntegerArgumentType.getInteger(context, "x"),
                                                                    IntegerArgumentType.getInteger(context, "y"),
                                                                    IntegerArgumentType.getInteger(context, "z")),
                                                            stage));
                                        }))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Float> scaleOverrideArguments() {
        return Commands.argument("value", FloatArgumentType.floatArg(0.05F, 100.0F))
                .executes(context -> {
                    float scale = FloatArgumentType.getFloat(context, "value");
                    VisualLifecycleClientConfig.setDebugUniformScaleOverride(scale);
                    VisualLifecycleClientRuntime.INSTANCE.refreshAll();
                    return send(context.getSource(), "视觉生命周期统一缩放覆盖已设为 " + scale + "。");
                });
    }

    private static int send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int defaultItemTintColor(BlockState state) {
        // Default grass green (same as GrassColor.getDefaultColor())
        final int GRASS_DEFAULT = 0x7CBD2D;
        // Default foliage green (same as FoliageColor.getDefaultColor())
        final int FOLIAGE_DEFAULT = 0x48B518;

        if (state.is(EcofluxBlockTags.USES_GRASS_TINT)) {
            return GRASS_DEFAULT;
        }
        if (state.is(EcofluxBlockTags.USES_FOLIAGE_TINT)) {
            return FOLIAGE_DEFAULT;
        }
        if (state.is(Blocks.DEAD_BUSH)) {
            return VisualLifecycleClientRuntime.DEAD_BUSH_COLOR;
        }
        return 0xFFFFFF;
    }

    private static int defaultTintColor(BlockState state, BlockAndTintGetter getter, BlockPos pos) {
        return VisualLifecycleClientRuntime.defaultColor(getter, pos, state);
    }

    @FunctionalInterface
    private interface VisualCommand {
        int run(CommandSourceStack source, BlockPos pos);
    }
}
