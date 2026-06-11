# 植物生命周期系统

植物生命周期系统是 Ecoflux 的统一植被管理基础设施。它提供 robust 的抽象接口，使演替系统、树木生长系统和客户端视觉层可以独立于具体植物类型运作。

## 当前状态

- 后续演替逻辑不应直接操作某一类具体植物，而应建立在这个系统之上
- 系统已落地为可工作的实现：adapter 模式 + VegetationTracker + 视觉同步
- 可复用到：地被植物、花、蘑菇、树苗、树木、后续可能的 Dynamic Trees 兼容对象

## 设计原则

### 1. 演替系统与植物生命系统分层

- **生命周期系统**负责：世界里一个植被对象怎样出生、成长、成熟、衰老、死亡、转换
- **演替系统**负责：当前区块应该鼓励哪些植物、这些植物在不同阶段如何贡献进度
- **视觉层**负责：当前阶段在客户端应该长什么样

### 2. 统一抽象，不硬编码

不能只针对 `dandelion`、`oak_sapling`、`short_grass` 写分散逻辑。必须建立统一抽象，让不同植被类型共用一套框架。

### 3. 数据驱动优先

植物配置通过 JSON 描述：哪些植物属于哪个阶段池、成熟后贡献多少分、衰老/死亡后积分如何变化。

### 4. 输入源分层

生命周期系统的来源收敛为三层：
1. **事件**（玩家放置/破坏）→ `ModPlayerEvents`
2. **低频校正扫描**（observeChunk）→ `VegetationTracker`
3. **少量关键 Mixin**（补洞）→ `SaplingBlockMixin`, `MushroomBlockMixin`

## 核心概念

### VegetationCategory

```java
FLOWER, GRASS, FERN, SAPLING, TREE, MUSHROOM, CROP, VINE, CACTUS, SUGAR_CANE, BAMBOO, OTHER
```

### VegetationLifecycleStage

```
BORN → MATURING → MATURE → AGING → DEAD → DECAYING → GONE
```

| 阶段 | 说明 |
|------|------|
| `BORN` | 刚生成 |
| `MATURING` | 生长中 |
| `MATURE` | 成熟，标准大小，提供点数 |
| `AGING` | 老化，着色变化，触发评估 gate |
| `DEAD` | 死亡，视觉凋零 |
| `DECAYING` | 腐烂中 |
| `GONE` | 已消失，等待清理 |

### ActiveVegetationRecord

单个植被追踪记录，关键字段：
- `adapterType` — 适配器类型 ID
- `category` — 植被分类
- `stage` — 当前生命周期阶段
- `pointValue` — 演替点数
- `birthGameTime` / `ageGameTime` / `expireGameTime`
- `birthState` (CompoundTag) — 出生时附加状态
- `visualState` — 当前视觉快照

## 核心组件

### VegetationTypeAdapter

最核心的扩展点接口：

```java
public interface VegetationTypeAdapter {
    ResourceLocation typeId();
    VegetationCategory category();
    boolean matches(BlockState state);
    ActiveVegetationRecord captureBirth(LevelAccessor level, BlockPos pos, BlockState state, long gameTime);
    VegetationObservation observe(ActiveVegetationRecord record, LevelAccessor level, long gameTime);
    VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime);
    default Optional<VegetationTransformation> detectTransformation(...) { return Optional.empty(); }
}
```

### VegetationTracker

统一追踪器（单例），不关心具体植物种类：

| 方法 | 说明 |
|------|------|
| `trackAt(level, pos, adapter, birthState)` | 注册出生 |
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
负责成熟树结构。支持 MATURE → AGING → DEAD 阶段。是树苗转化后的承接点。

## 与树木生长系统的关系

树苗被 `SaplingAdapter` 追踪后，原版瞬间生长被 `SaplingBlockMixin` 拦截，转由 `TreeGrowthHandler` 管理渐进生长过程。详见 [tree-growth-system.md](tree-growth-system.md)。

## 当前相关文件

```
plant/
├── VegetationTypeAdapter.java        # 核心接口
├── VegetationTracker.java            # 统一追踪器（单例）
├── PlantSpawner.java                 # 植物生成与修剪
├── VegetationObservation.java        # 观察结果 record
├── VegetationTransformation.java     # 转换描述 record
├── VegetationVisualState.java        # 视觉快照 record
├── VegetationCategory.java           # 分类枚举
├── VegetationLifecycleStage.java     # 生命周期阶段枚举
├── SimplePlantAdapter.java           # 小型植物适配器
├── SaplingAdapter.java               # 树苗适配器
└── TreeStructureAdapter.java         # 成熟树适配器

init/
├── ModPlayerEvents.java              # 玩家放置/破坏 → VegetationTracker
└── ModChunkEvents.java               # Chunk tick → observeChunk + prune
```

## 当前决议

- 演替系统建立在植物生命系统之上
- 视觉层复用生命系统给出的阶段快照，不自己猜生命周期
- 采用"事件 + 扫描 + 少量关键 Mixin"的组合路线
- 所有植物追踪统一到 `vegetationRecords`，`activePlants` 已退役
