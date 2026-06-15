# 网络同步与数据层

数据层负责 chunk 状态的 NBT 持久化，网络层负责将服务端植被视觉状态同步到客户端。

## 数据附件 (attachment/)

### SuccessionChunkData

每个 chunk 附加一个 `SuccessionChunkData` 实例，通过 `DataAttachment` 机制持久化。

**字段:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `currentBiome` | ResourceKey\<Biome\> | 当前群系 |
| `targetBiome` | ResourceKey\<Biome\> | 目标群系（演替方向） |
| `previousBiome` | ResourceKey\<Biome\> | 上一个群系（用于退化回退） |
| `activePathId` | ResourceLocation? | 当前活跃演替路径 ID（可空） |
| `progress` | double | 演替进度 [-1.0, 1.0]，达到 ±1.0 触发转换 |
| `consumingValue` | double | 维持当前群系所需植物点数（来自 BiomeRules） |
| `maxPlantCount` | int | 最大植物容量（正态分布采样值，来自 BiomeRules） |
| `currentPlantCount` | int | 当前活跃植物数 |
| `plantQueue` | Deque\<PlantQueueEntry\> | 预生成植物队列 |
| `vegetationRecords` | Map\<BlockPos, ActiveVegetationRecord\> | 活跃植被追踪记录 |
| `treeGrowthSessions` | Map\<BlockPos, TreeGrowthSession\> | 活跃树木生长会话 |
| `lastEvaluationGameTime` | long | 上次评估的游戏时间 |
| `lastSpawnGameTime` | long | 上次生成植物的游戏时间 |

**NBT 序列化**: 所有字段均写入 NBT，chunk 卸载/重载后自动恢复。`vegetationRecords` 中每个 `ActiveVegetationRecord` 独立序列化。`treeGrowthSessions` 也完整序列化，transient 字段在加载后从种子重建。

**访问方式:**
```java
SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
```

### ActiveVegetationRecord

单个植被的追踪记录 (record)。

**字段:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `vegetationId` | ResourceLocation | 植物方块 ID |
| `adapterType` | ResourceLocation | 适配器类型 ID（如 `"ecoflux:simple_plant"`, `"ecoflux:sapling"`, `"ecoflux:tree_structure"`） |
| `position` | BlockPos | 方块位置 |
| `lifeStage` | VegetationLifecycleStage | 当前生命周期阶段 |
| `birthGameTime` | long | 出生游戏时间 |
| `lastObservedGameTime` | long | 上次观察游戏时间 |
| `expireGameTime` | long | 过期时间（死亡），由 `PlantDefinition.maxAgeTicks()` 决定 |
| `basePointValue` | int | 基准演替点数（来自 PlantDefinition） |
| `currentPointValue` | int | 当前演替点数（可能随阶段变化） |
| `sourceBiomeId` | ResourceLocation? | 来源群系（可空） |
| `sourcePathId` | ResourceLocation? | 来源演替路径（可空） |
| `treeStructure` | TreeStructure? | 多块树结构数据（可空，仅树木有） |

### PlantQueueEntry

预生成植物队列条目 (record)。

**字段:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `plantId` | ResourceLocation | 植物方块 ID |
| `pointValue` | int | 演替点数（来自 PlantDefinition） |
| `weight` | int | 随机权重（来自 BiomeRules） |
| `maxAgeTicks` | long | 最大寿命（游戏 tick，来自 PlantDefinition） |

### 生命周期阶段 (VegetationLifecycleStage)

```
BORN → JUVENILE → GROWING → MATURE → AGING → DEAD
                                       ↓
                                  TRANSFORMED
```

| 阶段 | 说明 |
|------|------|
| `BORN` | 刚生成，最小缩放 |
| `JUVENILE` | 早期幼年阶段 |
| `GROWING` | 生长中，缩放渐增 |
| `MATURE` | 成熟，标准大小，提供满额点数 |
| `AGING` | 老化，着色变化，触发演替评估 gate |
| `DEAD` | 死亡，方块即将移除，记录清理 |
| `TRANSFORMED` | 树苗到树的转换中 |

> `VegetationCategory` 枚举已于 2026-06-12 移除。植物分类不再需要，点数通过 `PlantDefinition.pointValue()` 获取。

## 网络同步 (network/)

### ModNetworking

在 `EcofluxMod` 构造器中注册所有 Payload：

- `VegetationVisualChunkSyncPayload` — 植被视觉状态同步

### VegetationVisualChunkSyncPayload

将 chunk 内所有活跃植被的视觉状态发送给追踪该 chunk 的客户端。

**数据结构:**
- `chunkPos: ChunkPos` — 区块坐标
- `entries: List<VegetationVisualSyncEntry>` — 植被视觉条目列表

触发时机：
- 玩家进入 chunk 追踪范围
- 植被状态变更（生长阶段变化、老化等）
- 植物被添加/移除

### VegetationVisualSyncEntry

单个植物的同步数据：
- `pos: BlockPos` — 植物位置
- `adapterType: String` — 适配器类型
- `stage: VegetationLifecycleStage` — 生命周期阶段
- `visualSnapshot: CompoundTag` — 视觉快照（缩放、着色等）

## 同步流程

```
服务端 (Server Level)
  │
  ├─ VegetationTracker.trackAt(level, pos, adapter, birthState, plantDef)
  │     └─ chunkData.vegetationRecords.put(pos, record)
  │
  ├─ VegetationTracker.observeChunk(chunk)
  │     └─ 遍历 vegetationRecords → adapter.observe() → 更新 stage/visualState
  │
  ├─ VegetationTracker.syncChunkToTracking(level, chunkPos)
  │     └─ 构建 VegetationVisualChunkSyncPayload
  │     └─ 发送给所有追踪该 chunk 的客户端
  │
  ▼
客户端 (Client Level)
  │
  └─ ClientPayloadHandler 接收
        └─ VisualLifecycleClientRuntime.applySync()
              └─ 更新本地 visualLifecycleInstances 缓存
                    └─ VisualLifecycleWorldRenderer 在下一帧渲染
```

## 客户端接收流程

```
VegetationVisualChunkSyncPayload 到达客户端
  │
  └─→ VisualLifecycleClientRuntime.updateChunk(chunkPos, entries)
        ├─ 清除旧缓存
        ├─ 创建 VisualLifecycleInstance（每个 entry 一个）
        │     ├─ 匹配 VisualLifecycleAdapter
        │     ├─ 构建 VisualLifecycleProfile
        │     └─ 设置 VisualLifecycleRenderState
        └─ 标记 chunk 需要重新渲染
```

## ModAttachments

```java
public static final DataAttachment<SuccessionChunkData> SUCCESSION_CHUNK_DATA =
    DataAttachment.builder(/* default */)
        .serialize(SuccessionChunkData.CODEC)  // NBT 序列化
        .build();
```

在 `ModAttachments.register()` 中通过 `modEventBus.registerAttachment()` 注册。

## ModReloadListeners

监听 `/reload` 命令和数据包重载事件：
- 触发 `SuccessionConfigLoader` 重新扫描 JSON
- 触发 `BiomeRulesLoader` 重新扫描群系规则
- 触发 `PlantRegistryLoader` 重新扫描植物定义
- 更新所有注册表缓存
- 所有已加载 chunk 在下次 tick 时使用新配置

## 树木生长会话持久化

`TreeGrowthSession` 持久化关键字段（NBT），transient 字段在加载时重建：
- 持久化：`saplingPos`, `treeType`, `currentStage`, `totalStages`, `ticksPerStage`, `resolvedHeight`, `growthStartTime`, `lastStageTime`, `scSeed`
- 重建：`scParams`（从 profile 查找）、`stageLogs`/`stageLeaves`（从种子重新运行 SC 算法）、`placedLogs`/`placedLeaves`（从已完成的阶段累积）

## 世界生成数据流

```
WorldGen Decoration 阶段
  │
  ├─ CancelVanillaTreesBiomeModifier (Phase.REMOVE)
  │     └─ 移除所有 mod 的原版树 feature
  │
  └─ AddEcofluxTreesBiomeModifier (Phase.ADD)
        └─ EcofluxTreeFeature.place()
              ├─ 读取 biome → BiomeRules → 树种
              ├─ SpaceColonizationGenerator.generateFull()
              ├─ WorldGenLevel.setBlock() 放置 SC 树
              └─ 存入 static PENDING_TREES map

Chunk Load (ModChunkEvents)
  │
  └─ WorldGenVegetationScanner.scanChunk()
        ├─ Phase 1: 消费 PENDING_TREES → VegetationTracker
        ├─ Phase 1b: BFS 检测巨型蘑菇
        ├─ Phase 2: 扫描简单植物 → VegetationTracker
        └─ Phase 3: 密度上限裁剪
```
