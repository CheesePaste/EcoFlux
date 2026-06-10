# 网络同步与数据层

数据层负责 chunk 状态的 NBT 持久化，网络层负责将服务端植被视觉状态同步到客户端。

## 数据附件 (attachment/)

### SuccessionChunkData

每个 chunk 附加一个 `SuccessionChunkData` 实例，通过 `DataAttachment` 机制持久化。

**字段:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `currentBiome` | ResourceLocation | 当前群系 |
| `targetBiome` | ResourceLocation | 目标群系（演替方向） |
| `previousBiome` | ResourceLocation | 上一个群系（用于退化回退） |
| `progress` | double | 演替进度 [-1.0, 1.0]，达到 ±1.0 触发转换 |
| `consumingValue` | double | 维持当前群系所需植物点数 |
| `maxPlantCount` | int | 最大植物容量 |
| `currentPlantCount` | int | 当前植物数 (= `vegetationRecords.size()`) |
| `plantQueue` | Queue\<PlantQueueEntry\> | 预生成植物队列 |
| `vegetationRecords` | Map\<BlockPos, ActiveVegetationRecord\> | 活跃植被追踪记录 |
| `lastEvaluationGameTime` | long | 上次评估的游戏时间 |
| `queueVersion` | int | 队列版本号（配置变更时递增） |

**NBT 序列化**: 所有字段均写入 NBT，chunk 卸载/重载后自动恢复。`vegetationRecords` 中每个 `ActiveVegetationRecord` 独立序列化。

**访问方式:**
```java
SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
```

### ActiveVegetationRecord

单个植被的追踪记录。

**字段:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `adapterType` | String | 适配器类型 ID（如 `"simple_plant"`, `"sapling"`, `"tree_structure"`） |
| `category` | VegetationCategory | 植被分类（FLOWER, GRASS, SAPLING, TREE, MUSHROOM） |
| `stage` | VegetationLifecycleStage | 当前生命周期阶段 |
| `pointValue` | double | 植物提供的演替点数 |
| `birthGameTime` | long | 出生游戏时间 |
| `ageGameTime` | long | 进入老化阶段的时间 |
| `expireGameTime` | long | 过期时间（死亡） |
| `birthState` | CompoundTag | 出生时的附加状态（adapter 特定） |
| `visualState` | VegetationVisualState | 当前视觉状态快照 |

### PlantQueueEntry

预生成植物队列条目。

**字段:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `plantId` | String | 方块 ID |
| `pointValue` | double | 演替点数 |
| `weight` | int | 随机权重 |
| `maxAge` | int | 最大寿命（游戏日） |

### 生命周期阶段 (VegetationLifecycleStage)

```
BORN → MATURING → MATURE → AGING → DEAD → DECAYING → GONE
```

| 阶段 | 说明 |
|------|------|
| `BORN` | 刚生成，小缩放 |
| `MATURING` | 生长中，缩放渐增 |
| `MATURE` | 成熟，标准大小，提供点数 |
| `AGING` | 老化，着色变化，触发评估 gate |
| `DEAD` | 死亡，视觉凋零 |
| `DECAYING` | 腐烂中，渐隐 |
| `GONE` | 已消失，等待清理 |

### 植被分类 (VegetationCategory)

`FLOWER`, `GRASS`, `FERN`, `SAPLING`, `TREE`, `MUSHROOM`, `CROP`, `VINE`, `CACTUS`, `SUGAR_CANE`, `BAMBOO`, `OTHER`

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
  ├─ VegetationTracker.trackAt(level, pos, adapter, birthState)
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
- 更新 `SuccessionConfigRegistry` 缓存
- 所有已加载 chunk 在下次 tick 时使用新配置
