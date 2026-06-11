package com.s.ecoflux.init;

/**
 * Debug and administration commands under {@code /ecoflux}.
 *
 * <p>Structure: registers a {@code /ecoflux} command tree with subcommands for
 * prototype stepping ({@code init, status, spawn, evaluate, step, accelerate,
 * transition}), automatic processing ({@code auto on/off}), lifecycle inspection
 * ({@code lifecycle inspect/track/observe/untrack}), and speed control
 * ({@code speed}).
 * <p>Role in Ecoflux: interactive debugging and manual control of the succession
 * pipeline for development and testing.
 */

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.plant.PlantSpawner;
import com.s.ecoflux.plant.VegetationTracker;
import com.s.ecoflux.prototype.PrototypeChunkController;
import com.s.ecoflux.succession.SuccessionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ModCommands {
    private ModCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ecoflux")
                .requires(source -> source.hasPermission(2))
                .then(registerGlobalAutoCommands())
                .then(registerSpeedCommand())
                .then(Commands.literal("accelerate").executes(context -> runAccelerate(context.getSource())))
                .then(registerLifecycleCommands())
                .then(registerPrototypeCommands()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerGlobalAutoCommands() {
        return Commands.literal("auto")
                .then(Commands.literal("on").executes(context -> enableFullAuto(context.getSource())))
                .then(Commands.literal("off").executes(context -> setAuto(context.getSource(), false)))
                .then(Commands.literal("status").executes(context -> autoStatus(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerSpeedCommand() {
        return Commands.literal("speed")
                .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.1f, 1000.0f))
                        .executes(context -> setSpeed(
                                context.getSource(),
                                FloatArgumentType.getFloat(context, "multiplier"))))
                .then(Commands.literal("status").executes(context -> speedStatus(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerLifecycleCommands() {
        return Commands.literal("lifecycle")
                .then(Commands.literal("inspect").then(positionArguments(LifecycleAction.INSPECT)))
                .then(Commands.literal("track").then(positionArguments(LifecycleAction.TRACK)))
                .then(Commands.literal("observe").then(positionArguments(LifecycleAction.OBSERVE)))
                .then(Commands.literal("untrack").then(positionArguments(LifecycleAction.UNTRACK)))
                .then(Commands.literal("kill").then(positionArguments(LifecycleAction.KILL)))
                .then(Commands.literal("observe_chunk").executes(context -> runLifecycleChunkAction(context.getSource(), LifecycleChunkAction.OBSERVE)))
                .then(Commands.literal("sync_chunk").executes(context -> runLifecycleChunkAction(context.getSource(), LifecycleChunkAction.SYNC)))
                .then(Commands.literal("chunk").executes(context -> runLifecycleChunk(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerPrototypeCommands() {
        return Commands.literal("prototype")
                .then(Commands.literal("auto")
                        .then(Commands.literal("on").executes(context -> setAuto(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> setAuto(context.getSource(), false)))
                        .then(Commands.literal("status").executes(context -> autoStatus(context.getSource()))))
                .then(Commands.literal("init").executes(context -> runPrototype(context.getSource(), PrototypeAction.INIT)))
                .then(Commands.literal("status").executes(context -> runPrototype(context.getSource(), PrototypeAction.STATUS)))
                .then(Commands.literal("prune").executes(context -> runPrototype(context.getSource(), PrototypeAction.PRUNE)))
                .then(Commands.literal("spawn").executes(context -> runPrototype(context.getSource(), PrototypeAction.SPAWN)))
                .then(Commands.literal("evaluate").executes(context -> runPrototype(context.getSource(), PrototypeAction.EVALUATE)))
                .then(Commands.literal("step").executes(context -> runPrototype(context.getSource(), PrototypeAction.STEP)))
                .then(Commands.literal("accelerate").executes(context -> runPrototype(context.getSource(), PrototypeAction.ACCELERATE)))
                .then(Commands.literal("transition").executes(context -> runPrototype(context.getSource(), PrototypeAction.TRANSITION)))
                .then(Commands.literal("queue").executes(context -> runPrototype(context.getSource(), PrototypeAction.QUEUE)))
                .then(Commands.literal("plants").executes(context -> runPrototype(context.getSource(), PrototypeAction.PLANTS)))
                .then(Commands.literal("refill").executes(context -> runPrototype(context.getSource(), PrototypeAction.REFILL)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> positionArguments(LifecycleAction action) {
        return Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(context -> runLifecycle(
                                        context.getSource(),
                                        action,
                                        new BlockPos(
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z"))))));
    }

    private static int runPrototype(CommandSourceStack source, PrototypeAction action)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        String message = switch (action) {
            case INIT -> {
                SuccessionService.initializeChunk(chunk);
                ModChunkEvents.syncChunkTracking(level, chunk);
                yield "已重新初始化当前区块。 " + SuccessionService.describeChunk(chunk);
            }
            case STATUS -> SuccessionService.describeChunk(chunk);
            case PRUNE -> SuccessionService.pruneChunk(level, chunk) + " " + SuccessionService.describeChunk(chunk);
            case SPAWN -> SuccessionService.spawnInChunk(level, chunk) + " " + SuccessionService.describeChunk(chunk);
            case EVALUATE -> SuccessionService.evaluateChunk(level, chunk) + " " + SuccessionService.describeChunk(chunk);
            case STEP -> SuccessionService.step(level, chunk) + " " + SuccessionService.describeChunk(chunk);
            case ACCELERATE -> {
                String result = PrototypeChunkController.accelerate(level, chunk);
                ModChunkEvents.syncChunkTracking(level, chunk);
                yield result + " " + SuccessionService.describeChunk(chunk);
            }
            case TRANSITION -> {
                String result = SuccessionService.forceTransition(level, chunk);
                ModChunkEvents.syncChunkTracking(level, chunk);
                yield result + " " + SuccessionService.describeChunk(chunk);
            }
            case QUEUE -> PlantSpawner.getQueueSummary(chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA));
            case PLANTS -> {
                var pathOpt = SuccessionConfigRegistry.getPath(
                        chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA).getActivePathId().orElse(null));
                yield pathOpt.map(path -> {
                    int totalWeight = path.plants().stream().mapToInt(p -> p.weight()).sum();
                    String list = path.plants().stream()
                            .map(p -> p.plantId() + "(w=" + p.weight() + ",pts=" + p.pointValue() + ")")
                            .reduce((a, b) -> a + " " + b)
                            .orElse("无");
                    return "路径=" + path.pathId() + " 植物总数=" + path.plants().size()
                            + " 总权重=" + totalWeight + " [" + list + "]";
                }).orElse("当前区块没有激活的演替路径。");
            }
            case REFILL -> {
                var pathOpt = SuccessionConfigRegistry.getPath(
                        chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA).getActivePathId().orElse(null));
                yield pathOpt.map(path -> {
                    PlantSpawner.forceRefillQueue(chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA), path);
                    return "已强制重新填充队列。 " + PlantSpawner.getQueueSummary(chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA));
                }).orElse("当前区块没有激活的演替路径。");
            }
        };

        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int runAccelerate(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return runPrototype(source, PrototypeAction.ACCELERATE);
    }

    private static int runLifecycle(CommandSourceStack source, LifecycleAction action, BlockPos pos)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkAt(pos);
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);

        String message = switch (action) {
            case INSPECT -> VegetationTracker.INSTANCE.inspect(level, pos);
            case TRACK -> VegetationTracker.INSTANCE.trackAt(
                    level,
                    chunk,
                    pos,
                    chunkData.getCurrentBiome().map(key -> key.location()),
                    chunkData.getActivePathId());
            case OBSERVE -> VegetationTracker.INSTANCE.observeTracked(level, chunk, pos);
            case UNTRACK -> VegetationTracker.INSTANCE.untrack(chunk, pos);
            case KILL -> VegetationTracker.INSTANCE.forceKill(level, chunk, pos);
        };

        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int runLifecycleChunk(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LevelChunk chunk = player.serverLevel().getChunkAt(player.blockPosition());
        source.sendSuccess(() -> Component.literal(VegetationTracker.INSTANCE.describeChunk(chunk)), false);
        return 1;
    }

    private static int runLifecycleChunkAction(CommandSourceStack source, LifecycleChunkAction action)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        String message = switch (action) {
            case OBSERVE -> VegetationTracker.INSTANCE.observeChunk(level, chunk);
            case SYNC -> {
                ModNetworking.syncChunkToTracking(level, chunk);
                yield "已同步区块 " + chunk.getPos() + " 的生命周期视觉状态。";
            }
        };
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setAuto(CommandSourceStack source, boolean enabled) {
        ModChunkEvents.setAutomaticProcessingEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("Ecoflux 原型自动演替已" + (enabled ? "开启" : "关闭") + "。"),
                true);
        return 1;
    }

    private static int autoStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Ecoflux 原型自动演替当前"
                                + (ModChunkEvents.isAutomaticProcessingEnabled() ? "已开启" : "已关闭") + "。"),
                false);
        return 1;
    }

    private static int setSpeed(CommandSourceStack source, float multiplier) {
        SuccessionSpeedConfig.setSpeedMultiplier(multiplier);
        source.sendSuccess(
                () -> Component.literal("Ecoflux 演替速度倍率已设置为 " + String.format("%.1f", multiplier) + "x。"),
                true);
        return 1;
    }

    private static int speedStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Ecoflux 当前演替速度倍率为 " + String.format("%.1f", SuccessionSpeedConfig.getSpeedMultiplier()) + "x。"),
                false);
        return 1;
    }

    private static int enableFullAuto(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);

        if (chunkData.getCurrentBiome().isEmpty()) {
            SuccessionService.initializeChunk(chunk);
        }

        ModChunkEvents.syncChunkTracking(level, chunk);
        ModChunkEvents.setAutomaticProcessingEnabled(true);
        String bootstrap = String.join(
                " ",
                SuccessionService.pruneChunk(level, chunk),
                SuccessionService.spawnInChunk(level, chunk),
                VegetationTracker.INSTANCE.observeChunk(level, chunk),
                "已跳过首次评估，以保留可见生长过程。");
        source.sendSuccess(
                () -> Component.literal("Ecoflux 完整自动演替已开启。 " + bootstrap + " " + SuccessionService.describeChunk(chunk)),
                true);
        return 1;
    }

    private enum PrototypeAction {
        INIT,
        STATUS,
        PRUNE,
        SPAWN,
        EVALUATE,
        STEP,
        ACCELERATE,
        TRANSITION,
        QUEUE,
        PLANTS,
        REFILL
    }

    private enum LifecycleAction {
        INSPECT,
        TRACK,
        OBSERVE,
        UNTRACK,
        KILL
    }

    private enum LifecycleChunkAction {
        OBSERVE,
        SYNC
    }
}
