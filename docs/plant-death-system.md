# 植物衰老死亡系统

> **状态**: 已实现 (2026-06-11)

植物在到达 `AGING` 阶段后，应在 `expireGameTime` 时死亡，经过短暂腐烂期后从世界移除。本文档描述死亡系统的设计、实现和涉及文件。

## 当前状态

- `VegetationLifecycleStage.DEAD` 已实现，所有 adapter 在 `gameTime >= expireGameTime` 时返回此阶段
- `ActiveVegetationRecord.expireGameTime` 由 `PlantDefinition.maxAgeTicks()` 设置
- 植物在 `expireGameTime + DECAY_TICKS` 后从世界移除

## 设计目标

1. 植物到达 `expireGameTime` 后进入 DEAD 阶段 ✅
2. DEAD 阶段有视觉表现（客户端褐色调、缩小）✅
3. 短暂腐烂期后，方块从世界移除，追踪记录清理 ✅
4. 不引入新字段，复用已有 `expireGameTime` ✅

## 生命周期完整流程

```
BORN → JUVENILE/GROWING → MATURE → AGING → DEAD → (方块移除 + 取消追踪)
                                              ↑                ↑
                                         expireGameTime    expireGameTime
                                                           + DECAY_TICKS
```

- **expireGameTime**：死亡时间点，植物进入 DEAD 阶段
- **expireGameTime + DECAY_TICKS**：移除时间点，方块被破坏、记录清除

### 各植物类型的时间参数

`expireGameTime` 由 `PlantDefinition.maxAgeTicks()` 从 `plant_definitions/plants.json` 配置驱动，不再硬编码。以下为各 adapter 的 `DECAY_TICKS` 常量：

| Adapter | DECAY_TICKS（腐烂） |
|---------|-------------------|
| SimplePlantAdapter | 6000 ticks (5min) |
| TreeStructureAdapter | 24000 ticks (20min) |
| SaplingAdapter | 6000 ticks (5min) |

## 实现概要

### SimplePlantAdapter 死亡检测

`observe()` 方法在 `gameTime >= expireGameTime` 时返回 DEAD 阶段。
`gameTime >= expireGameTime + DECAY_TICKS` 时返回 `present=false`，触发方块移除和记录清理。

### TreeStructureAdapter 死亡检测

同上，`DECAY_TICKS = 24000L`（树木腐烂更慢）。

### SaplingAdapter 死亡检测

树苗如果到期未生长（未被转化为树木），进入死亡。
`DECAY_TICKS = 6000L`

### VegetationTracker 死亡处理

在 `observeTrackedInternal()` 中，当 `!observation.present()` 时：
- 检查方块是否仍然存在（避免重复破坏）
- 调用 `level.destroyBlock(pos, false)` 移除方块
- 从 `chunkData.vegetationRecords` 中移除记录
- DEAD 阶段的记录在移除前正常更新，让客户端有时间渲染死亡视觉效果

### 客户端死亡视觉

`SimplePlantAdapter.visualState()` 和 `TreeStructureAdapter.visualState()` 包含 DEAD 分支：
- DEAD 阶段 progress 在 expireGameTime → expireGameTime+DECAY_TICKS 之间插值
- 客户端 `VisualLifecycleAdapter` 对 DEAD 阶段应用褐色 tint + 缩小 scale

## 涉及文件

| 文件 | 改动类型 |
|------|---------|
| `plant/SimplePlantAdapter.java` | 死亡检测逻辑 |
| `plant/TreeStructureAdapter.java` | 死亡检测逻辑 |
| `plant/SaplingAdapter.java` | 死亡检测逻辑 |
| `plant/VegetationTracker.java` | 死亡时破坏方块 |
| `client/visual/VisualLifecycleAdapter.java` | DEAD 阶段视觉 |
| `client/visual/VisualLifecycleWorldRenderer.java` | DEAD 阶段渲染 |

## 树木特殊处理

树木是多块结构，与花草的死亡机制不同：

1. **位置收集**：树生长时，`SpaceColonizationGenerator.generateFull()` 计算所有原木和树叶位置，`TreeGrowthSession` 在每阶段通过 `stageLogs` / `stageLeaves` 放置方块，同时记录到 `placedLogs` / `placedLeaves`
2. **存储**：生长完成后，`TreeGrowthHandler.onGrowthComplete()` 从 `PlantRegistry` 查找原木方块的 `PlantDefinition`（如 `oak_log` → 576000 ticks / 8h），创建 `TreeStructure` record，通过 `ActiveVegetationRecord.withTreeStructure()` 附加到追踪记录
3. **阶段性死亡**：进入 DEAD 后，每次 observe 移除约 1/8 的方块，**树叶优先**，树叶全部消失后才开始移除原木
4. **隔离性**：只移除该树生长时记录的方块，不会影响相邻的树
5. **世界生成的树**：`EcofluxTreeFeature` 在世界生成时放置的树也通过 `PENDING_TREES` → `WorldGenVegetationScanner` 注册为 `TreeStructure` 记录，享受相同的死亡机制

- 死亡后掉落物（原版 `destroyBlock` 已处理）
- 不同死亡原因（病害、干旱等）—— 当前只有"寿终正寝"
- 死亡传播（相邻植物连锁死亡）
