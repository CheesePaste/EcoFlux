package com.s.ecoflux.client.visual;

/**
 * Client singleton that receives and manages visual lifecycle state from the server.
 *
 * <p>Structure: stores {@link VisualLifecycleInstance} records keyed by dimension and
 * block position. {@code syncVegetationChunk()} processes
 * {@code VegetationVisualChunkSyncPayload} entries to add/update/remove tracked
 * instances from the {@code VEGETATION_SYSTEM} source. The tick method prunes
 * instances whose block type no longer matches. {@code getRenderState()} is the
 * primary per-frame lookup used by both the block color handler and the world
 * renderer; it resolves the adapter, caches the base biome color, and delegates
 * to {@link VisualLifecycleAdapter#resolveState}.
 * <p>Role in Ecoflux: this is the hub connecting server-synced vegetation state,
 * per-adapter visual computation, and the two rendering paths (tint via color
 * handler, scale via {@link VisualLifecycleWorldRenderer}).
 */

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.s.ecoflux.config.EcofluxBlockTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;

public final class VisualLifecycleClientRuntime {
    public static final VisualLifecycleClientRuntime INSTANCE = new VisualLifecycleClientRuntime();

    private final Map<String, VisualLifecycleInstance> trackedInstances = new ConcurrentHashMap<>();
    private final Map<Long, VisualLifecycleInstance> trackedInstancesByPos = new ConcurrentHashMap<>();
    /** Per-chunk index: chunkPosLong → Set<posLong> for VEGETATION_SYSTEM entries only. */
    private final Map<Long, Set<Long>> vegSystemByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Integer> baseColorCache = new ConcurrentHashMap<>();
    /** Per-position render state cache: posKey → (stage, state). Only recomputed on stage change. */
    private final Map<Long, CachedRenderState> renderStateCache = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> manualWorldRenderPass = ThreadLocal.withInitial(() -> false);
    private volatile List<VisualLifecycleInstance> cachedTrackedList = List.of();
    private volatile boolean trackedListDirty = true;
    private volatile boolean disabled = false;

    private VisualLifecycleClientRuntime() {
    }

    public String start(BlockPos pos) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "视觉生命周期启动失败：客户端世界尚未加载。";
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return "视觉生命周期跳过启动：" + pos + " 是空气。";
        }
        Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
        if (adapter.isEmpty()) {
            return "视觉生命周期跳过启动：" + pos + " 没有可用适配器。";
        }

        VisualLifecycleInstance instance = new VisualLifecycleInstance(
                adapter.get().typeId(),
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                pos.immutable(),
                level.getGameTime(),
                adapter.get().createProfile(state),
                null,
                null,
                VisualLifecycleTrackingSource.MANUAL);
        trackedInstances.put(key(level, pos), instance);
        trackedInstancesByPos.put(pos.asLong(), instance);
        invalidateTrackedList();
        markDirty(pos);
        return "已在 " + pos + " 为 " + instance.blockId() + " 启动视觉生命周期。";
    }

    public String stop(BlockPos pos) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "视觉生命周期停止失败：客户端世界尚未加载。";
        }

        VisualLifecycleInstance removed = trackedInstances.remove(key(level, pos));
        trackedInstancesByPos.remove(pos.asLong());
        baseColorCache.remove(pos.asLong());
        renderStateCache.remove(pos.asLong());
        invalidateTrackedList();
        markDirty(pos);
        return removed == null
                ? "视觉生命周期跳过停止：" + pos + " 没有追踪对象。"
                : "已停止 " + pos + " 的视觉生命周期。";
    }

    public String forceStage(BlockPos pos, VisualLifecycleStage stage) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "视觉生命周期强制阶段失败：客户端世界尚未加载。";
        }

        String key = key(level, pos);
        VisualLifecycleInstance instance = trackedInstances.get(key);
        if (instance == null) {
            return "视觉生命周期跳过强制阶段：" + pos + " 没有追踪对象。";
        }

        VisualLifecycleInstance updated = instance.withForcedStage(stage);
        trackedInstances.put(key, updated);
        trackedInstancesByPos.put(pos.asLong(), updated);
        invalidateTrackedList();
        markDirty(pos);
        return "已将 " + pos + " 的视觉生命周期强制为阶段 " + stage + "。";
    }

    public String inspect(BlockPos pos) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "视觉生命周期检查失败：客户端世界尚未加载。";
        }

        BlockState state = level.getBlockState(pos);
        Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
        VisualLifecycleInstance instance = trackedInstances.get(key(level, pos));
        String adapterText = adapter.map(found -> found.typeId().toString()).orElse("无");
        if (instance == null) {
            return "视觉生命周期检查 " + pos + "：方块="
                    + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " 适配器=" + adapterText
                    + " 已追踪=false";
        }

        int baseColor = defaultColor(level, pos, state);
        VisualLifecycleAdapter resolvedAdapter = adapter.orElse(GrassVisualLifecycleAdapter.INSTANCE);
        VisualLifecycleRenderState renderState = resolvedAdapter.resolveState(instance, level.getGameTime(), baseColor);
        return "视觉生命周期检查 " + pos
                + "：方块=" + instance.blockId()
                + " 适配器=" + instance.adapterId()
                + " 来源=" + instance.source()
                + " 阶段=" + renderState.stage()
                + " 进度=" + String.format("%.2f", renderState.stageProgress())
                + " 缩放=" + String.format("%.2f", renderState.scale())
                + " 色调=0x" + Integer.toHexString(renderState.tintedColor());
    }

    public String list() {
        ClientLevel level = currentLevel();
        if (level == null) {
            return "视觉生命周期列表失败：客户端世界尚未加载。";
        }
        if (trackedInstances.isEmpty()) {
            return "视觉生命周期列表：没有已追踪植物。";
        }

        String dimensionPrefix = level.dimension().location() + "|";
        String joined = trackedInstances.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(dimensionPrefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(instance -> instance.pos().asLong()))
                .limit(8)
                .map(instance -> instance.pos() + ":" + instance.blockId() + ":" + instance.source())
                .reduce((left, right) -> left + ", " + right)
                .orElse("无");
        return "视觉生命周期已追踪=" + trackedInstances.size() + " [" + joined + "]";
    }

    public String clear() {
        trackedInstances.clear();
        trackedInstancesByPos.clear();
        vegSystemByChunk.clear();
        baseColorCache.clear();
        renderStateCache.clear();
        invalidateTrackedList();
        refreshAll();
        return "视觉生命周期追踪已清空。";
    }

    public List<VisualLifecycleInstance> trackedInCurrentLevel() {
        if (disabled) {
            return List.of();
        }
        ClientLevel level = currentLevel();
        if (level == null || trackedInstances.isEmpty()) {
            return List.of();
        }

        if (!trackedListDirty) {
            return cachedTrackedList;
        }

        String dimensionPrefix = level.dimension().location() + "|";
        List<VisualLifecycleInstance> list = trackedInstances.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(dimensionPrefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(instance -> instance.pos().asLong()))
                .toList();
        cachedTrackedList = list;
        trackedListDirty = false;
        return list;
    }

    private void invalidateTrackedList() {
        trackedListDirty = true;
    }

    public void refreshAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    public void tick() {
        ClientLevel level = currentLevel();
        if (level == null) {
            trackedInstances.clear();
            trackedInstancesByPos.clear();
            baseColorCache.clear();
            renderStateCache.clear();
            invalidateTrackedList();
            return;
        }

        if (level.getGameTime() % 5L != 0L) {
            return;
        }

        boolean[] removed = {false};
        trackedInstances.entrySet().removeIf(entry -> {
            VisualLifecycleInstance instance = entry.getValue();
            BlockState state = level.getBlockState(instance.pos());
            Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
            boolean remove = adapter.isEmpty() || !adapter.get().typeId().equals(instance.adapterId());
            if (remove) {
                long posKey = instance.pos().asLong();
                trackedInstancesByPos.remove(posKey);
                baseColorCache.remove(posKey);
                renderStateCache.remove(posKey);
                if (instance.source() == VisualLifecycleTrackingSource.VEGETATION_SYSTEM) {
                    long ck = new ChunkPos(instance.pos()).toLong();
                    Set<Long> chunkPlants = vegSystemByChunk.get(ck);
                    if (chunkPlants != null) {
                        chunkPlants.remove(posKey);
                        if (chunkPlants.isEmpty()) vegSystemByChunk.remove(ck);
                    }
                }
                markDirty(instance.pos());
                removed[0] = true;
            }
            return remove;
        });
        if (removed[0]) {
            invalidateTrackedList();
        }
    }

    public VisualLifecycleRenderState getRenderState(BlockPos pos, BlockState state) {
        if (disabled) {
            return null;
        }

        ClientLevel level = currentLevel();
        if (level == null) {
            return null;
        }

        Long posKey = pos.asLong();
        VisualLifecycleInstance instance = trackedInstancesByPos.get(posKey);
        if (instance == null) {
            baseColorCache.remove(posKey);
            renderStateCache.remove(posKey);
            return null;
        }

        Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
        if (adapter.isEmpty() || !adapter.get().typeId().equals(instance.adapterId())) {
            return null;
        }

        long gameTime = level.getGameTime();

        // Quick stage check — only recompute render state on stage change
        VisualLifecycleStage currentStage = adapter.get().determineStage(instance, gameTime);
        CachedRenderState cached = renderStateCache.get(posKey);
        if (cached != null && cached.stage() == currentStage) {
            return cached.state();
        }

        // Stage changed or first lookup — compute full render state and cache
        int baseColor = baseColorCache.computeIfAbsent(posKey,
                k -> defaultColor(level, pos, state));
        VisualLifecycleRenderState renderState = adapter.get().resolveState(instance, gameTime, baseColor);
        renderStateCache.put(posKey, new CachedRenderState(currentStage, renderState));
        return renderState;
    }

    public int adjustTint(BlockState state, BlockPos pos, int baseColor) {
        if (disabled) {
            return baseColor;
        }
        VisualLifecycleRenderState renderState = getRenderState(pos, state);
        return renderState == null ? baseColor : renderState.tintedColor();
    }

    public boolean isTrackedForCurrentVisualPass(BlockPos pos, BlockState state) {
        if (disabled) {
            return false;
        }
        return getRenderState(pos, state) != null;
    }

    public void beginManualWorldRenderPass() {
        manualWorldRenderPass.set(true);
    }

    public void endManualWorldRenderPass() {
        manualWorldRenderPass.set(false);
    }

    public boolean isManualWorldRenderPass() {
        return manualWorldRenderPass.get();
    }

    public static final int DEAD_BUSH_COLOR = 0xA78F63;

    public static int defaultColor(BlockAndTintGetter getter, BlockPos pos, BlockState state) {
        if (state.is(EcofluxBlockTags.USES_GRASS_TINT)) {
            return BiomeColors.getAverageGrassColor(getter, pos);
        }
        if (state.is(EcofluxBlockTags.USES_FOLIAGE_TINT)) {
            return BiomeColors.getAverageFoliageColor(getter, pos);
        }
        if (state.is(Blocks.DEAD_BUSH)) {
            return DEAD_BUSH_COLOR;
        }
        return 0xFFFFFF;
    }

    private static void markDirty(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.setSectionDirty(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
        }
    }

    public void syncVegetationChunk(ResourceLocation dimensionId, ChunkPos chunkPos, List<com.s.ecoflux.network.VegetationVisualSyncEntry> entries) {
        ClientLevel level = currentLevel();
        if (level == null || !level.dimension().location().equals(dimensionId)) {
            return;
        }

        long chunkKey = chunkPos.toLong();
        Set<Long> incomingPositions = new HashSet<>(entries.size());
        for (com.s.ecoflux.network.VegetationVisualSyncEntry entry : entries) {
            incomingPositions.add(entry.pos().asLong());
        }

        boolean changed = false;

        // Per-chunk removal — O(chunk_plants) instead of O(total_plants)
        Set<Long> chunkPlants = vegSystemByChunk.get(chunkKey);
        if (chunkPlants != null) {
            List<Long> toRemove = new ArrayList<>();
            for (long posKey : chunkPlants) {
                if (!incomingPositions.contains(posKey)) {
                    toRemove.add(posKey);
                }
            }
            for (long posKey : toRemove) {
                String key = key(level, posKey);
                trackedInstances.remove(key);
                trackedInstancesByPos.remove(posKey);
                baseColorCache.remove(posKey);
                renderStateCache.remove(posKey);
                markDirty(BlockPos.of(posKey));
                chunkPlants.remove(posKey);
                changed = true;
            }
            if (chunkPlants.isEmpty()) {
                vegSystemByChunk.remove(chunkKey);
            }
        }

        for (com.s.ecoflux.network.VegetationVisualSyncEntry entry : entries) {
            BlockState state = level.getBlockState(entry.pos());
            if (state.isAir()) {
                continue;
            }

            Optional<VisualLifecycleAdapter> adapter = VisualLifecycleRegistry.INSTANCE.find(state);
            if (adapter.isEmpty()) {
                continue;
            }

            VisualLifecycleExternalState newExternalState = new VisualLifecycleExternalState(
                    mapVegetationStage(entry.stage()), entry.stageProgress(), level.getGameTime());

            Long posKey = entry.pos().asLong();
            VisualLifecycleInstance existing = trackedInstancesByPos.get(posKey);
            boolean isNew = existing == null;
            boolean stageChanged = !isNew && existing.externalState() != null
                    && existing.externalState().stage() != newExternalState.stage();

            VisualLifecycleInstance instance = new VisualLifecycleInstance(
                    adapter.get().typeId(),
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    entry.pos().immutable(),
                    entry.birthGameTime(),
                    adapter.get().createProfile(state),
                    null,
                    newExternalState,
                    VisualLifecycleTrackingSource.VEGETATION_SYSTEM);
            trackedInstances.put(key(level, entry.pos()), instance);
            trackedInstancesByPos.put(entry.pos().asLong(), instance);
            vegSystemByChunk.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(entry.pos().asLong());

            // Only markDirty for new or stage-changed entries.
            if (isNew || stageChanged) {
                markDirty(entry.pos());
                changed = true;
            }
        }
        if (changed) {
            invalidateTrackedList();
        }
    }

    private static ClientLevel currentLevel() {
        return Minecraft.getInstance().level;
    }

    private static VisualLifecycleStage mapVegetationStage(com.s.ecoflux.plant.VegetationLifecycleStage stage) {
        return switch (stage) {
            case BORN, JUVENILE -> VisualLifecycleStage.BORN;
            case GROWING -> VisualLifecycleStage.GROWING;
            case MATURE, TRANSFORMED -> VisualLifecycleStage.MATURE;
            case AGING -> VisualLifecycleStage.AGING;
            case DEAD -> VisualLifecycleStage.DEAD;
        };
    }

    private static boolean sameChunk(BlockPos pos, ChunkPos chunkPos) {
        return SectionPos.blockToSectionCoord(pos.getX()) == chunkPos.x
                && SectionPos.blockToSectionCoord(pos.getZ()) == chunkPos.z;
    }

    private static String key(ClientLevel level, BlockPos pos) {
        ResourceLocation dimensionId = level.dimension().location();
        return dimensionId + "|" + pos.asLong();
    }

    private static String key(ClientLevel level, long posKey) {
        ResourceLocation dimensionId = level.dimension().location();
        return dimensionId + "|" + posKey;
    }

    private record CachedRenderState(VisualLifecycleStage stage, VisualLifecycleRenderState state) {}
}
