# 演替核心系统

演替核心系统编排 chunk 尺度生态演替的完整循环：初始化 → 植物生成 → 生命周期观察 → 进度评估 → 群系转换/退化。

## 核心组件

| 类 | 职责 |
|----|------|
| `SuccessionService` | 主编排入口，提供 `initializeChunk()`, `processChunkTick()`, `step()`, `evaluateChunk()` 等 |
| `SuccessionTargetResolver` | 初始化时采样 biome/climate，匹配配置，填充 `SuccessionChunkData` |
| `SuccessionEvaluator` | 进度评估：aging gate 检查 + 植被点数 vs consuming 比较 + 累积/减少 progress |
| `BiomeTransitionService` | 群系替换执行：`fillBiomesFromNoise()` + 发包 + 植树（或退化重置） |
| `PrototypeChunkController` | 10 秒加速演替演示模式（调用上述服务类） |
| `ChunkSamplingHelper` | 静态工具：`sampleChunkCenterBiome()`, `sampleChunkClimate()`, `sampleSurfaceY()`, `findSpawnPos()`, `canPlantAt()` 等 |

## 完整演替循环

```
┌─────────────────────────────────────────────────────────┐
│                    Chunk Load                            │
│  ModChunkEvents 检测 chunk 数据为空 → 首次初始化          │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  SuccessionService.initializeChunk()                     │
│    ├─ ChunkSamplingHelper.sampleChunkCenterBiome()       │
│    ├─ ChunkSamplingHelper.sampleChunkClimate()           │
│    ├─ SuccessionConfigRegistry.findBestMatch()            │
│    ├─ SuccessionTargetResolver.populateChunkData()        │
│    └─ PlantSpawner.ensureQueue() + trySpawnPlant() × N   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              Chunk Tick (每 20 tick)                     │
│  SuccessionService.processChunkTick()                    │
└────────────────────┬────────────────────────────────────┘
                     │
         ┌───────────┼───────────┐
         ▼           ▼           ▼
    ┌────────┐ ┌──────────┐ ┌──────────┐
    │ 观察    │ │ 修剪      │ │ 生成      │
    │Tracker │ │pruneInvalid│ │trySpawn  │
    └────────┘ └──────────┘ └──────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  评估 (按 evaluation_interval_days 间隔)                  │
│  SuccessionEvaluator.evaluateProgress()                  │
│    ├─ hasAgingVegetation()? — 有老化植被才触发评估        │
│    ├─ totalPoints = Σ activeRecords.pointValue            │
│    ├─ diff = totalPoints - consuming                      │
│    ├─ diff > 0 → progress += diff / consuming             │
│    └─ diff <= 0 → progress -= 0.1 * Δt                   │
└────────────────────┬────────────────────────────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
   progress >= 1.0        progress <= -1.0
   (正向演替)               (负向退化)
          │                     │
          ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│ applyTransition  │  │ applyRegression  │
│ → targetBiome    │  │ → fallbackBiome  │
│   种树奖励        │  │   重置状态        │
│   重置进度        │  │   不种树          │
└──────────────────┘  └──────────────────┘
```

## SuccessionService

主编排器，提供以下核心方法：

| 方法 | 说明 |
|------|------|
| `initializeChunk(chunk)` | 首次加载时初始化 chunk 数据：采样 biome/climate、匹配路径、填充队列、首次生成 |
| `processChunkTick(chunk)` | 每 20 tick 调用：观察植被、修剪无效植物、尝试生成、定期评估 |
| `step(chunk)` | 单步演替（手动触发，用于调试） |
| `evaluateChunk(chunk)` | 强制执行一次评估 |
| `pruneChunk(chunk)` | 清理无效植物 |
| `spawnInChunk(chunk)` | 在 chunk 内生成一棵植物 |
| `forceTransition(chunk)` | 强制执行群系转换（调试用） |
| `hasActivePath(chunk)` | 检查 chunk 是否有活跃演替路径 |
| `describeChunk(chunk)` | 返回 chunk 状态描述字符串（含队列摘要） |

## SuccessionEvaluator

进度评估逻辑：

1. **Aging gate**: 检查 `vegetationRecords` 中是否有任何记录处于 AGING 或更晚阶段。没有老化植被时不触发评估，防止空 chunk 自动退化。
2. **计算点数**: 遍历所有 `ActiveVegetationRecord`，累加 `pointValue`。
3. **比较 consuming**: `diff = totalPoints - consuming`。
4. **累积进度**: `progress += diff / consuming`（正值前进，负值后退）。
5. **退化检测**: `shouldRegress()` 在 `progress <= -1.0` 时返回 true。

## BiomeTransitionService

### 正向演替 (applyTransition)
1. 使用 `ChunkAccess.fillBiomesFromNoise()` 将 chunk 内群系替换为目标群系
2. 发送 `ClientboundChunksBiomesPacket` 通知客户端
3. 软重置（保留现有植被记录和树木生长会话），重新解析新群系的演替目标
4. 现有植物继续其生命周期，新植物随新群系路径逐渐生成

### 负向退化 (applyRegression)
1. 群系切换到 `fallbackBiome`（或 `previousBiome` 作为兜底）
2. 发送同步包
3. 软重置，重新解析新群系的演替目标

## ChunkSamplingHelper

静态工具方法：

| 方法 | 说明 |
|------|------|
| `sampleChunkCenterBiome(chunk)` | 采样 chunk 中心 (8, ~, 8) 的群系 |
| `sampleChunkClimate(chunk)` | 采样 chunk 中心的温度和湿度 |
| `sampleSurfaceY(chunk, x, z)` | 获取 (x, z) 处的地表 Y 坐标 |
| `findSpawnPos(chunk)` | 在 chunk 内寻找合适的植物生成位置 |
| `canPlantAt(level, pos)` | 检查某位置是否可以种植 |
| `isAllowedBaseBlock(state, allowedBlocks)` | 检查方块是否在允许的基底列表中 |
| `countNearbyTrackedPlants(chunk, pos, plantId, radius)` | 统计附近同类型追踪植物数量 |
| `toBiomeKey(biome)` | ResourceLocation → Biome 的辅助转换 |

## 事件驱动

### ModChunkEvents
- **Chunk Load**: 检测 `SuccessionChunkData` 是否为空 → 调用 `initializeChunk()`
- **Chunk Unload**: 调用 `TreeGrowthHandler` 持久化生长会话
- **Chunk Tick**: 根据模式（加速/正常）调用 `processChunkTick()` 或 `PrototypeChunkController`

### ModPlayerEvents
- **方块放置**: 检测是否匹配 `VegetationTypeAdapter` → 自动 `VegetationTracker.trackAt()`
- **方块破坏**: 检测是否在 `vegetationRecords` 中 → 自动移除 + 客户端同步

## PrototypeChunkController（加速演示）

10 秒加速演替模式，用于快速验证演替循环：

- 每 200 tick (~10 秒) 强制执行一次完整演替循环
- 植物生命周期阶段被加速（数秒内完成 BORN→MATURE→AGING→DEAD）
- 调用 `SuccessionService` 和 `BiomeTransitionService` 完成实际演替操作
- 通过 `/ecoflux prototype start/stop` 开关

## 调试命令

| 命令 | 说明 |
|------|------|
| `/ecoflux prototype start` | 开启加速演替模式 |
| `/ecoflux prototype stop` | 关闭加速演替模式 |
| `/ecoflux prototype step` | 手动触发一步演替 |
| `/ecoflux prototype describe` | 查看当前 chunk 演替状态 |
| `/ecoflux prototype queue` | 查看植物队列分布 |
| `/ecoflux prototype plants` | 列出当前路径所有配置植物及权重 |
| `/ecoflux prototype refill` | 强制重新填充队列 |
| `/ecoflux auto` | 查看/切换自动模式状态 |
