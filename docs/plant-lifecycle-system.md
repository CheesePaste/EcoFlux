# 植物生命周期系统

> 最后更新: 2026-06-16

植物生命周期系统是 Ecoflux 的统一植被管理基础设施。它提供 robust 的抽象接口，使演替系统、树木生长系统和客户端视觉层可以独立于具体植物类型运作。

## 相关文档

修改植物系统前必读：[plant-death-system.md](plant-death-system.md) · [tree-growth-system.md](tree-growth-system.md) · [config-system.md](config-system.md)（PlantRegistry/PlantDefinition）

## 当前状态

- 后续演替逻辑不应直接操作某一类具体植物，而应建立在这个系统之上
- 系统已落地为可工作的实现：adapter 模式 + VegetationTracker + 视觉同步
- 可复用到：地被植物、花、蘑菇、树苗、树木、后续可能的 Dynamic Trees 兼容对象
- WorldGenVegetationScanner 在 chunk 加载时自动扫描和注册世界生成的植被

## 设计原则

### 1. 演替系统与植物生命系统分层

- **生命周期系统**负责：世界里一个植被对象怎样出生、成长、成熟、衰老、死亡、转换
- **演替系统**负责：当前区块应该鼓励哪些植物、这些植物在不同阶段如何贡献进度
- **视觉层**负责：当前阶段在客户端应该长什么样

### 2. 统一抽象，不硬编码

不能只针对 `dandelion`、`oak_sapling`、`short_grass` 写分散逻辑。必须建立统一抽象，让不同植被类型共用一套框架。

### 3. 数据驱动优先

植物配置通过 JSON 描述：植物定义通过 `PlantRegistry` 集中管理，群系规则通过 `BiomeRules` 配置植物列表和权重。

### 4. 输入源分层

生命周期系统的来源收敛为三层：
1. **世界生成扫描**（`WorldGenVegetationScanner`）→ chunk 加载时自动注册原版植被
2. **事件**（玩家放置/破坏）→ `ModPlayerEvents`
3. **低频校正扫描**（observeChunk）→ `VegetationTracker`
4. **少量关键 Mixin**（补洞）→ `SaplingBlockMixin`, `MushroomBlockMixin`

## 核心概念

### VegetationLifecycleStage

```
BORN → JUVENILE → GROWING → MATURE → AGING → DEAD
                                       ↓
                                  TRANSFORMED (树苗→树的转换中)
```

| 阶段 | 说明 |
|------|------|
| `BORN` | 刚生成，最小缩放 |
| `JUVENILE` | 早期幼年阶段 |
| `GROWING` | 生长中，缩放渐增 |
| `MATURE` | 成熟，标准大小，提供满额点数 |
| `AGING` | 老化，着色变化，触发演替评估 gate |
| `DEAD` | 死亡，方块即将移除，记录清理 |
| `TRANSFORMED` | 树苗到树的转换进行中 |

### 死亡系统

所有 adapter 的 `observe()` 方法在 `gameTime >= expireGameTime` 时返回 DEAD 阶段。
`gameTime >= expireGameTime + DECAY_TICKS` 时返回 `present=false`，触发 VegetationTracker 破坏方块并清理记录。

`expireGameTime` 由 `PlantDefinition.maxAgeTicks()` 决定，不再硬编码。各 adapter 的 `DECAY_TICKS` 常量如下：

| 植物类型 | DECAY_TICKS |
|---------|-------------|
| 花草/灌木 (SimplePlantAdapter) | 6000 ticks (5min) |
| 树木 (TreeStructureAdapter) | 24000 ticks (20min) |
| 树苗未生长 (SaplingAdapter) | 6000 ticks (5min) |

详见 [plant-death-system.md](plant-death-system.md)。

### ActiveVegetationRecord

单个植被追踪记录 (record)，关键字段：
- `vegetationId` — 植物方块 ID (`ResourceLocation`)
- `adapterType` — 适配器类型 ID (`ResourceLocation`)
- `position` — 方块位置 (`BlockPos`)
- `lifeStage` — 当前生命周期阶段
- `birthGameTime` / `lastObservedGameTime` / `expireGameTime`
- `basePointValue` / `currentPointValue` — 基准/当前演替点数
- `sourceBiomeId` / `sourcePathId` — 来源群系/路径（可空）
- `treeStructure` — 多块树结构数据（可空，仅树木有）

## 核心组件

### VegetationTypeAdapter

最核心的扩展点接口：

```java
public interface VegetationTypeAdapter {
    ResourceLocation typeId();
    boolean matches(BlockState state);
    ActiveVegetationRecord captureBirth(
        ServerLevel level, BlockPos pos, BlockState state, long gameTime,
        Optional<ResourceLocation> sourceBiomeId,
        Optional<ResourceLocation> sourcePathId,
        PlantDefinition plantDefinition);
    VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime);
    default VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) { ... }
    default Optional<VegetationTransformation> detectTransformation(...) { return Optional.empty(); }
}
```

注意：`category()` 方法已移除。点数、寿命等参数从 `PlantDefinition` 获取。

### VegetationTracker

统一追踪器（单例），不关心具体植物种类：

| 方法 | 说明 |
|------|------|
| `trackAt(level, pos, adapter, birthState, plantDefinition)` | 注册出生 |
| `observeTracked(level, pos, gameTime)` | 更新单条观察结果 |
| `observeChunk(chunk, gameTime)` | 批量观察 chunk 内所有追踪植物 |
| `untrack(level, pos)` | 移除追踪 |
| `syncChunkToTracking(level, chunkPos)` | 构建视觉快照同步给客户端 |

### VegetationObservation

观察结果对象，包含：
- 是否仍然存在 (`present`)
- 当前生命周期阶段
- 当前积分
- 是否成熟 / 是否衰老
- 是否发生转换 (`transformation`)

### VegetationTransformation

表达形态升级（如 `oak_sapling → oak_tree`），树苗变树不需要重建系统。

### VegetationVisualState

由 adapter 把逻辑阶段翻译成视觉阶段/进度，让客户端视觉层只负责表现。

## 当前 Adapter 实现

### SimplePlantAdapter
负责单格小型植物：花、草、蕨、小型蘑菇、枯萎的灌木、两格高植物（高草丛、向日葵等）。根据年龄给出 BORN → MATURING → MATURE → AGING 阶段。**两格高植物的上半部分会自动追踪，但贡献 0 积分避免重复计数。**

### SaplingAdapter
负责树苗和红树繁殖体。处理树苗出生、观察、消失。当树苗生长被 `SaplingBlockMixin` 拦截后，通过 `VegetationTransformation` 转换为 TreeStructure。树苗阶段 0→1 放行（视觉成熟），阶段 1→树 拦截。支持 9 种树种：橡树、白桦、云杉、丛林(2×2)、丛林(1×1)、深色橡树、金合欢、樱花、红树。

### TreeStructureAdapter
负责成熟树结构（包括由 `EcofluxTreeFeature` 在世界生成时放置的 SC 树和巨型蘑菇）。支持 MATURE → AGING → DEAD 阶段。是树苗转化后的承接点，通过 `TreeStructure` record 管理多块结构。

## 世界生成植被扫描 (WorldGenVegetationScanner)

Chunk 加载时（`ModChunkEvents.onChunkLoad`），`WorldGenVegetationScanner` 分三个阶段扫描和注册世界生成的植被：

1. **Phase 1**: 消费 `EcofluxTreeFeature.PENDING_TREES`（decoration→chunk-load 桥接），将世界生成阶段放置的 SC 树注册到 VegetationTracker
2. **Phase 1b**: BFS 检测巨型蘑菇（棕色/红色蘑菇方块连通组件）
3. **Phase 2**: 扫描小型植物（花草/蕨/小蘑菇/枯灌木/高草丛），通过 adapters 匹配并注册
4. **Phase 3**: 密度上限裁剪，移除过度代表的植物类型，使每区块植物数不超过 `BiomeRules.maxPlantCount`
5. 随机化植物出生时间（±20% 寿命变化）以分散死亡事件

## 与树木生长系统的关系

树苗被 `SaplingAdapter` 追踪后，原版瞬间生长被 `SaplingBlockMixin` 拦截，转由 `TreeGrowthHandler` 管理渐进生长过程。详见 [tree-growth-system.md](tree-growth-system.md)。

### 树木和大型蘑菇的双阶段生命周期

树木和大型蘑菇（棕色/红色蘑菇）与普通花草不同，它们有**两个独立的记录**跨越两个阶段：

```
阶段 1: 树苗 / 小蘑菇 (SaplingAdapter / SimplePlantAdapter)
  │
  │  PlantDefinition (树苗)                    PlantDefinition (小蘑菇)
  │  ┌──────────────────────┐                  ┌──────────────────────┐
  │  │ maxAgeTicks          │  控制: 树苗/蘑菇 │ maxAgeTicks          │
  │  │ (如 oak_sapling →    │  在生长前能活多久  │ (如 red_mushroom →   │
  │  │  168000 ticks)       │                  │  48000 ticks)        │
  │  └──────────────────────┘                  └──────────────────────┘
  │
  │  Mixin 拦截瞬间生长
  │  ┌──────────────────────────────────────────────┐
  │  │ TreeGrowthSession                            │
  │  │ ticksPerStage (SpaceColonizationProfile)     │
  │  │ 控制: 生长动画速度 (每阶段放多少方块)           │
  │  │ 如 oak: 1200 tick/stage × ~10 stages ≈ 10分   │
  │  └──────────────────────────────────────────────┘
  │
  ▼
阶段 2: 成熟原木 / 蘑菇方块 (TreeStructureAdapter)
  │
  │  PlantDefinition (原木)                     PlantDefinition (蘑菇方块)
  │  ┌──────────────────────┐                  ┌──────────────────────────┐
  │  │ maxAgeTicks          │  控制: 大树/蘑菇  │ maxAgeTicks              │
  │  │ (如 oak_log →        │  成熟后能活多久    │ (如 red_mushroom_block → │
  │  │  576000 ticks / 8h)  │                  │  288000 ticks / 4h)      │
  │  └──────────────────────┘                  └──────────────────────────┘
  │
  ▼
AGING → DEAD → 树叶先腐烂 → 原木后消失
```

**关键区别**：
- `ticksPerStage`（在 `SpaceColonizationProfile` / `MushroomGrowthProfile` 中）只控制**生长动画**的节奏
- `maxAgeTicks`（在 `PlantDefinition` 中）控制每个阶段的**寿命上限**，是所有植物共用的生命周期框架
- 树苗/小蘑菇有一个 `maxAgeTicks`（到期未生长则死亡），成熟后换了一个新的 `maxAgeTicks`（原木/蘑菇方块条目）
- 生长完成时 `onGrowthComplete()` 从 `PlantRegistry` 查找原木/蘑菇方块的 `PlantDefinition` 获取新寿命

## 当前相关文件

```
plant/
├── VegetationTypeAdapter.java        # 核心接口
├── VegetationTracker.java            # 统一追踪器（单例）
├── PlantSpawner.java                 # 植物生成与修剪
├── VegetationObservation.java        # 观察结果 record
├── VegetationTransformation.java     # 转换描述 record
├── VegetationVisualState.java        # 视觉快照 record
├── VegetationLifecycleStage.java     # 生命周期阶段枚举
├── TreeStructure.java                # 树的多块结构数据 record
├── SimplePlantAdapter.java           # 小型植物适配器
├── SaplingAdapter.java               # 树苗适配器
└── TreeStructureAdapter.java         # 成熟树适配器

worldgen/
└── WorldGenVegetationScanner.java    # 世界生成植被扫描器

init/
├── ModPlayerEvents.java              # 玩家放置/破坏 → VegetationTracker
└── ModChunkEvents.java               # Chunk tick → observeChunk + prune + WorldGenScanner

config/
├── plant/PlantRegistry.java          # 中心植物注册表
├── plant/PlantRegistryLoader.java    # 植物定义加载器
└── biome/BiomeRulesRegistry.java     # 群系规则注册表
```

## 当前决议

- 演替系统建立在植物生命系统之上
- 视觉层复用生命系统给出的阶段快照，不自己猜生命周期
- 采用"世界生成扫描 + 事件 + 少量关键 Mixin"的组合路线
- 所有植物追踪统一到 `vegetationRecords`，`activePlants` 已退役
- WorldGenVegetationScanner 在 chunk 加载时自动注册原版植被，无需手动初始化
