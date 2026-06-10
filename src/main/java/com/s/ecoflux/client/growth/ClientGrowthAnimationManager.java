package com.s.ecoflux.client.growth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.s.ecoflux.EcofluxConstants;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * Client-side only. Receives tree growth block positions from the server
 * and plays short-lived scale animations on those blocks.
 * <p>
 * No server-side Display entities, no network sync of entity data — just a
 * lightweight packet with positions, and the client renders scaled blocks
 * locally until the animation expires.
 */
public final class ClientGrowthAnimationManager {
    public static final ClientGrowthAnimationManager INSTANCE = new ClientGrowthAnimationManager();

    /** Animation type constants matching server-side AnimationStyle ordinal */
    public static final byte TRUNK = 0;
    public static final byte LEAF_INFLATE = 1;
    public static final byte LEAF_CLUSTER = 2;

    private static final AnimParams[] PARAMS = {
        new AnimParams(new Vector3f(0.9f, 0.05f, 0.9f), new Vector3f(1.0f, 1.0f, 1.0f), 15, 5),
        new AnimParams(new Vector3f(0.05f, 0.05f, 0.05f), new Vector3f(1.08f, 1.08f, 1.08f), 20, 5),
        new AnimParams(new Vector3f(0.05f, 0.05f, 0.05f), new Vector3f(1.05f, 1.05f, 1.05f), 12, 3),
    };

    private final Map<BlockPos, GrowthAnimInstance> active = new ConcurrentHashMap<>();
    private long clientTick = 0;
    private boolean renderPassActive;

    private ClientGrowthAnimationManager() {}

    /** True while growth animation is rendering — Mixin uses this to skip its cancel logic. */
    public boolean isRenderPassActive() {
        return renderPassActive;
    }

    public void addSingle(BlockPos pos, byte animType) {
        AnimParams params = animType >= 0 && animType < PARAMS.length ? PARAMS[animType] : PARAMS[0];
        active.put(pos.immutable(), new GrowthAnimInstance(pos.immutable(), clientTick, params));
        EcofluxConstants.LOGGER.info("[Ecoflux] CLIENT anim mgr addSingle: pos={}, type={}, clientTick={}, total={}",
                pos, animType, clientTick, active.size());
    }

    public void addEntries(Iterable<BlockPos> positions, byte animType) {
        AnimParams params = animType >= 0 && animType < PARAMS.length ? PARAMS[animType] : PARAMS[0];
        for (BlockPos pos : positions) {
            active.put(pos.immutable(), new GrowthAnimInstance(pos.immutable(), clientTick, params));
        }
    }

    public boolean isAnimating(BlockPos pos) {
        return active.containsKey(pos);
    }

    public int activeCount() {
        return active.size();
    }

    public void onClientTick() {
        clientTick++;
        Iterator<Map.Entry<BlockPos, GrowthAnimInstance>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            GrowthAnimInstance inst = it.next().getValue();
            if (clientTick - inst.startTick > inst.params.durationTicks + inst.params.holdTicks) {
                it.remove();
            }
        }
    }

    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (active.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (clientTick % 20 == 0) {
            EcofluxConstants.LOGGER.info("[Ecoflux] CLIENT render: {} active anims, renderPassActive={}",
                    active.size(), renderPassActive);
        }

        var camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        renderPassActive = true;
        try {
            for (GrowthAnimInstance inst : active.values()) {
                BlockPos pos = inst.pos;
                BlockState state = mc.level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }

                long elapsed = clientTick - inst.startTick;
                float progress = (elapsed + tickDelta) / (float) inst.params.durationTicks;
                if (progress > 1.0f) progress = 1.0f;
                float eased = easeOutBack(progress);

                float sx = lerp(inst.params.startScale.x, inst.params.targetScale.x, eased);
                float sy = lerp(inst.params.startScale.y, inst.params.targetScale.y, eased);
                float sz = lerp(inst.params.startScale.z, inst.params.targetScale.z, eased);

                poseStack.pushPose();
                poseStack.translate(
                        pos.getX() - camera.getPosition().x,
                        pos.getY() - camera.getPosition().y,
                        pos.getZ() - camera.getPosition().z);
                poseStack.translate(0.5, 0.0, 0.5);
                poseStack.scale(sx, sy, sz);
                poseStack.translate(-0.5, 0.0, -0.5);

                var bufferSource = mc.renderBuffers().bufferSource();
                blockRenderer.renderBatched(state, pos, mc.level, poseStack,
                        bufferSource.getBuffer(ItemBlockRenderTypes.getChunkRenderType(state)),
                        false, RandomSource.create(state.getSeed(pos)));

                poseStack.popPose();
            }
        } finally {
            renderPassActive = false;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Ease-out with slight overshoot for bouncy feel. */
    private static float easeOutBack(float t) {
        float c1 = 1.2f;
        float c3 = c1 + 1f;
        return (float) (1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
    }

    private record AnimParams(
            Vector3f startScale,
            Vector3f targetScale,
            int durationTicks,
            int holdTicks) {}

    private record GrowthAnimInstance(
            BlockPos pos,
            long startTick,
            AnimParams params) {}
}
