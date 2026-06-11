# 植物衰老死亡系统

> **状态**: 已实现 (2026-06-11)

植物在到达 `AGING` 阶段后，应在 `expireGameTime` 时死亡，经过短暂腐烂期后从世界移除。本文档描述死亡系统的设计、实现步骤和涉及文件。

## 当前状态

- `VegetationLifecycleStage.DEAD` 枚举已定义，但没有任何 adapter 返回此阶段
- `ActiveVegetationRecord.expireGameTime` 已被设置但从未用于触发死亡
- 植物到达 AGING 后永远停留，不死亡，不清理

## 设计目标

1. 植物到达 `expireGameTime` 后进入 DEAD 阶段
2. DEAD 阶段有视觉表现（客户端褐色调、缩小）
3. 短暂腐烂期后，方块从世界移除，追踪记录清理
4. 不引入新字段，复用已有 `expireGameTime`

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

| 植物类型 | expireGameTime（死亡） | DECAY_TICKS（腐烂） |
|---------|----------------------|-------------------|
| 花草/灌木 | 72000 ticks (1h) | 6000 ticks (5min) |
| 树木 | 288000 ticks (4h) | 24000 ticks (20min) |
| 树苗（未生长） | 144000 ticks (2h) | 6000 ticks (5min) |

## 实现步骤

### Step 1: SimplePlantAdapter 死亡检测

修改 `observe()` 方法，在 `gameTime >= expireGameTime` 时返回 DEAD 阶段。

```java
// 在 AGING 分支之后添加：
if (gameTime >= record.expireGameTime() + DECAY_TICKS) {
    return VegetationObservation.absent("简单植物已死亡并腐烂。");
}
if (gameTime >= record.expireGameTime()) {
    return new VegetationObservation(true, DEAD, 0, false, false, Optional.empty(), "简单植物已死亡。");
}
```

常量：`DECAY_TICKS = 6000L`

### Step 2: TreeStructureAdapter 死亡检测

同上，`DECAY_TICKS = 24000L`（树木腐烂更慢）。

### Step 3: SaplingAdapter 死亡检测

树苗如果到期未生长（未被转化为树木），进入死亡。
`DECAY_TICKS = 6000L`

### Step 4: VegetationTracker 死亡处理

在 `observeTrackedInternal()` 中，当 `!observation.present()` 时，检查是否需要破坏方块：

```java
if (!observation.present()) {
    BlockState currentState = level.getBlockState(pos);
    // 只有方块还存在时才破坏（玩家已破坏则跳过）
    if (!currentState.isAir()) {
        level.destroyBlock(pos, false);
    }
    chunkData.removeVegetation(pos);
    return ...;
}
```

同时，DEAD 阶段的记录正常更新（不立即移除），让客户端有时间渲染死亡视觉效果。

### Step 5: 客户端死亡视觉

`SimplePlantAdapter.visualState()` 和 `TreeStructureAdapter.visualState()` 添加 DEAD 分支：
- DEAD 阶段 progress 在 expireGameTime → expireGameTime+DECAY_TICKS 之间插值
- 客户端 `VisualLifecycleAdapter` 对 DEAD 阶段应用褐色 tint + 缩小 scale

### Step 6: 编译验证

`./gradlew build` 确保所有更改编译通过。

## 涉及文件

| 文件 | 改动类型 |
|------|---------|
| `plant/SimplePlantAdapter.java` | 添加死亡检测逻辑 |
| `plant/TreeStructureAdapter.java` | 添加死亡检测逻辑 |
| `plant/SaplingAdapter.java` | 添加死亡检测逻辑 |
| `plant/VegetationTracker.java` | 死亡时破坏方块 |
| `client/visual/VisualLifecycleAdapter.java` | DEAD 阶段视觉 |
| `client/visual/VisualLifecycleWorldRenderer.java` | DEAD 阶段渲染 |
| `docs/plant-death-system.md` | 本文档 |
| `docs/plant-lifecycle-system.md` | 更新生命周期阶段说明 |
| `CLAUDE.md` | 更新开发状态 |

## 树木特殊处理

树木是多块结构，与花草的死亡机制不同：

1. **位置收集**：树生长时，`TreeMorphology.growStage()` 和 `LeafFiller.fillLeaves()` 直接将放置的每个方块位置写入 `TreeGrowthSession.placedLogs/placedLeaves`
2. **存储**：生长完成后，`TreeGrowthHandler.onGrowthComplete()` 创建 `TreeStructure` 记录，通过 `ActiveVegetationRecord.withTreeStructure()` 附加到追踪记录
3. **阶段性死亡**：进入 DEAD 后，每次 observe 移除约 1/8 的方块，**树叶优先**，树叶全部消失后才开始移除原木
4. **隔离性**：只移除该树生长时记录的方块，不会影响相邻的树

- 死亡后掉落物（原版 `destroyBlock` 已处理）
- 不同死亡原因（病害、干旱等）—— 当前只有"寿终正寝"
- 死亡传播（相邻植物连锁死亡）
