# Ecoflux 优化计划

本文档列出通过全项目审查发现的全部设计问题，按严重程度排序，并提供分步执行方案。

---

## 问题清单

### P0 — 严重 Bug（运行时错误或性能问题）

#### P0-1: TreeGrowthSession.ensureSkeleton() 引用比较 bug

**位置:** `plant/tree/TreeGrowthSession.java:55`

```java
if (skeleton != null && morphologyParams == params) return;
```

`morphologyParams == params` 使用引用相等比较，但 `MorphologyParams.oak()` 等工厂每次返回**新 record 实例**，导致每次 `growStage()` 调用都重新运行 `SkeletonGenerator.generate()` 和 `planStages()`。数百个生长阶段累积的性能损耗显著。

**修复:** 改为 `.equals()` 比较，或直接从 profile 的 `treeType()` 推导 morphologyParams 而非存储比较。

#### P0-2: 6 个 TreeGrowthProfile 与 MorphologyParams 高度数据不一致

**位置:** 所有 6 个树种 profile + `MorphologyParams.java`

| 树种 | `profile.minTrunkHeight()` | `MorphologyParams.xxx().minTrunkHeight` |
|------|---------------------------|----------------------------------------|
| Oak | 5 | 6 |
| Birch | 6 | 7 |
| Spruce | 8 | 10 |
| Jungle | 10 | 12 |
| DarkOak | 6 | 7 |
| Acacia | 5 | 6 |

`TreeGrowthProfile.resolveHeight()` 使用 profile 接口的值，但 `totalStagesForHeight()` 委托给 `MorphologyParams` 的参数值。同一个树的两套数字不一致，阶段性计数和高度解析来自不同数据源。

**修复:** 删除 profile 接口中的 `minTrunkHeight/maxTrunkHeight` 方法，统一从 `morphologyParams()` 获取。

#### P0-3: 服务端/客户端植物匹配逻辑不一致

**位置:**
- 服务端: `plant/SimplePlantAdapter.java:33-42`
- 客户端: `client/visual/FlowerVisualLifecycleAdapter.java:25`, `GrassVisualLifecycleAdapter.java:24`

服务端 `SimplePlantAdapter` 匹配 `TALL_GRASS`, `LARGE_FERN`, `TALL_FLOWERS`, `BROWN_MUSHROOM`, `RED_MUSHROOM`。但客户端没有对应的专用 visual adapter，这些方块全部落到 `GenericVisualLifecycleAdapter`，获得通用 visual profile（与服务器端生长时间不一致）。

同时 `DEAD_BUSH` 被客户端 `GrassVisualLifecycleAdapter` 匹配，但服务端 `SimplePlantAdapter` 不匹配它。

**修复:** 统一客户端 visual adapter 的匹配范围，与服务器 adapter 对齐。删除不再需要的 `GenericVisualLifecycleAdapter` 自动 fallback。

#### P0-4: SaplingBlockMixin / MushroomBlockMixin 的 use-after-remove

**位置:** `mixin/SaplingBlockMixin.java:42-47`, `plant/tree/TreeGrowthHandler.java:260-296`

`forceAdvanceStage()` 在第 262 行移除活跃会话后，调用 `onGrowthComplete()`，其第 270 行又从 `activeGrowths.get(basePos)` 重新查找。单阶段 profile（如蘑菇）在第一阶段就完成 → 查找返回 `null` → 回退到 `Blocks.OAK_LOG`。

---

### P1 — 高危重复（修改一处必须同步改多处，遗漏即 Bug）

#### P1-1: Chunk 数据重置逻辑在 3 处逐字重复

**位置:**
- `succession/BiomeTransitionService.java:applyRegression()` 第 59-66 行
- `succession/BiomeTransitionService.java:applyTransition()` 第 103-110 行
- `succession/SuccessionTargetResolver.java:resolveTarget()` 第 55-63 行

三处完全相同的 9 行重置代码。新增一个需要重置的字段必须同时修改 3 处。`SuccessionChunkData.clearRuntimeState()` 已存在但不完整（未清除 targetBiome, consumingValue, maxPlantCount）。

**修复:** 完善 `clearRuntimeState()` 使其完整重置，三处调用方全部改为调用该方法。

#### P1-2: 生命周期阶段 tick 常量在 3 个文件中独立定义

**位置:**
- `prototype/PrototypeChunkController.java:26-30` (SIMPLE_PLANT_BORN_TICKS 等)
- `plant/SimplePlantAdapter.java:108-121` (硬编码 200, 1200, 48000, 72000)
- `plant/SaplingAdapter.java:55,69,110-111` (硬编码 1200, 24000, 144000)

同样的生命周期阶段时长（BORN 200 tick, GROWING 1200 tick 等）在三处独立维护。改一个值需要改三处。

**修复:** 在 `VegetationTypeAdapter` 接口中定义生命周期时长默认方法，或提取到共享常量类。

#### P1-3: ~370 行死代码 — 6 个 tree profile 的旧 growStage()

**位置:** 所有 6 个 tree profile 的 `growStage()` 方法 + `TreeShapeUtils` 中的旧冠形函数

所有 tree profile 的 `morphologyParams()` 返回非 null → `TreeGrowthHandler` 永远走 morphology 分支。旧 `growStage()` 实现（Oak ~60行, Birch ~46行, Spruce ~39行, Jungle ~45行, DarkOak ~41行, Acacia ~80行）是死代码。`TreeShapeUtils` 中 `oakCanopyRadius`, `birchCanopyRadius`, `spruceCanopyRadius`, `generateBranchPositions` 也是死代码。

**修复:** 删除所有旧 `growStage()` 实现和 `TreeShapeUtils` 中仅被旧代码调用的函数。蘑菇 profile 需要改为实现 morphology 路径（或用单独的 base class）。

#### P1-4: GrowthBlockCapture 整个机制从未被激活

**位置:** `plant/tree/GrowthBlockCapture.java`, `mixin/LevelMixin.java`

`GrowthBlockCapture.ACTIVE` ThreadLocal 的 `set(true)` 调用在整个代码库中零引用。`LevelMixin.setBlock()` 注入的拦截代码永远不会触发。`GrowthBlockCapture.BLOCKS` ThreadLocal 也从未被读取。整个类和 Mixin 注入都是死代码。

**修复:** 删除 `GrowthBlockCapture` 类和 `LevelMixin` 中对应的注入逻辑。

#### P1-5: ModPlayerEvents 重复 VegetationTracker 管线

**位置:**
- `init/ModPlayerEvents.java:40-54` (onBlockPlaced) vs `plant/VegetationTracker.java:64-77` (trackAt)
- `init/ModPlayerEvents.java:71-76` (onBlockBroken) vs `plant/VegetationTracker.java:187` (untrack)

`onBlockPlaced` 手动实现了与 `VegetationTracker.trackAt()` 完全相同的 adapter 匹配 → captureBirth → trackVegetation → sync 管线。`onBlockBroken` 手动实现了与 `untrack()` 相同逻辑。如果 `trackAt()` 增加副作用，`ModPlayerEvents` 会遗漏。

**修复:** `ModPlayerEvents` 的方法改为直接调用 `VegetationTracker.INSTANCE.trackAt()` / `untrack()`。

#### P1-6: SuccessionService 的 pipeline 在 step() 和 processChunkTick() 中 90% 重复

**位置:** `succession/SuccessionService.java:48-77` (step) vs `122-165` (processChunkTick)

核心管线 (prune → queue → spawn → observe → evaluate → transition) 在两个方法中重复。仅差异在 interval gating 和 ignoreInterval 参数。

**修复:** 提取为 `doPipeline(chunk, options)` 私有方法，两个公开方法变为薄包装。

#### P1-7: 两个独立生命周期阶段枚举 + 有损单向映射

**位置:**
- `plant/VegetationLifecycleStage.java` — 7 个值
- `client/visual/VisualLifecycleStage.java` — 4 个值
- 映射函数: `client/visual/VisualLifecycleClientRuntime.java:389-396`

JUVENILE → BORN, TRANSFORMED → MATURE, DEAD → AGING。语义信息丢失。如果服务端枚举增删值，映射函数必须手动更新。客户端 visual profile 的阶段时长与服务端 adapters 的阶段时长完全独立维护，没有编译期保证一致性。

**修复:** 合并为单一共享枚举。客户端如需更少视觉阶段，通过配置映射表而非硬编码 switch。

#### P1-8: 客户端 visual profile 时长与服务端独立维护

**位置:**
- 客户端: `FlowerVisualLifecycleAdapter.java:29-41` (born=200, growing=1000, mature=46800, aging=24000)
- 服务端: `SimplePlantAdapter.java:108-121` (born<200, growing<1200, mature<48000)

客户端 profile 的 tick 常量必须与服务端完全一致但没有共享来源。修改服务端时长后客户端会静默不一致。

**修复:** 将生命周期时长作为数据通过网络同步或从 profile 配置中共享。

---

### P2 — 中危（维护负担、设计不一致、死字段）

#### P2-1: 默认颜色逻辑在客户端两处重复

**位置:**
- `client/visual/VisualLifecycleClientRuntime.java:301-312` (defaultColor)
- `client/visual/ModClientVisualLifecycle.java:135-146` (defaultTintColor)

两段代码完全相同（grass/fern → grassColor, sapling → foliageColor, deadBush → 0xA78F63）。魔法颜色 `0xA78F63` 硬编码在两处。

**修复:** 提取到共享方法，颜色常量命名。

#### P2-2: 群系转换参数硬编码不可配置

**位置:** `succession/BiomeTransitionService.java:127,129,144-155`

- 树种数量: `5 + random.nextInt(4)` (5-8)
- 最大尝试次数: 72
- 仅 Oak 和 Birch 作为奖励树种
- 树高: `4 + random.nextInt(2)`

这些是配置层 (`ChunkRules`) 的天然候选字段，但现在硬编码在服务层。

**修复:** 在 `ChunkRules` 中添加 `transition_rewards` 配置段（tree_count, allowed_species, height_range）。

#### P2-3: Adapter 硬编码静态列表，无注册表模式

**位置:**
- `plant/VegetationTracker.java:17-20` — `List.of(SaplingAdapter, TreeStructureAdapter, SimplePlantAdapter)`
- `client/visual/VisualLifecycleRegistry.java:11-15` — `List.of(Grass, Flower, Sapling, Generic)`

无插件扩展机制。Adapter 的 `typeId()` 唯一性无验证。`findAdapter(ResourceLocation)` 是线性扫描。如果 adapter 列表变更，已持久化的 `ActiveVegetationRecord.adapterType` 可能指向不存在的 adapter。

**修复:** 使用注册表模式，支持 `IEventBus` 注册 adapter，验证 typeId 唯一性。

#### P2-4: progress() 方法在 3 个 adapter 中复制粘贴

**位置:**
- `plant/SimplePlantAdapter.java:162-167`
- `plant/SaplingAdapter.java:162-167`
- `plant/TreeStructureAdapter.java:88-93`

三处完全相同的 `private static float progress(long age, long start, long endExclusive)`。

**修复:** 提升为 `VegetationTypeAdapter` 接口的 default 方法或提取到工具类。

#### P2-5: PrototypeChunkController 重复 SuccessionService 管线

**位置:** `prototype/PrototypeChunkController.java:35-95`

`accelerate()` 和 `processAcceleratedTick()` 重新实现了 `SuccessionService.step()` / `processChunkTick()` 中的 prune → queue → spawn → evaluate 管线，而非调用共享的 service 方法。

**修复:** 让原型控制器调用 `SuccessionService` 的对应方法，仅设置加速参数。

#### P2-6: JSON 配置中有 3 个字段解析后从未使用

| 字段 | 位置 | 状态 |
|------|------|------|
| `evaluation_interval_days.max` | `config/ChunkRules.java:7` | 解析存储，`resolvedEvaluationIntervalTicks()` 只用 `min` |
| `spawn_rules.placement` | `config/PlantSpawnRules.java:7` | 解析校验，零引用 `.placement()` |
| `plants[].category` | `config/PlantDefinition.java:7` | String 类型解析存储，从不读取 |

**修复:** 删除未使用字段（或接入逻辑使它们生效）。对 `category`，要么校验为 `VegetationCategory` 枚举，要么删除。

#### P2-7: ActiveVegetationRecord.category 存储但不影响逻辑

**位置:** `attachment/ActiveVegetationRecord.java:13`

`category` 持久化到 NBT、在 NBT 读回、在调试输出中显示，但**不参与任何游戏逻辑决策**：点值计算、评估判断、生成规则均忽略它。与 `PlantDefinition.category` 是两套平行的 category 概念，互不验证。

**修复:** 要么让 `category` 影响点值/生长参数（不同分类不同规则），要么从 record 中删除。

#### P2-8: ModCommands 单体类 290 行 + 12-case switch

**位置:** `init/ModCommands.java:101-153`

每个 case 分支直接做格式化输出，逻辑与 I/O 混合。PLANTS case 内联实现了与 `PlantSpawner.getQueueSummary()` 等效的格式化。

**修复:** 拆分命令处理器为独立类，使用命令注册模式而非 switch 分发。

#### P2-9: 蘑菇 profile 大面积重复代码

**位置:** `plant/tree/profiles/BrownMushroomGrowthProfile.java` vs `RedMushroomGrowthProfile.java`

- `totalStagesForHeight()` 完全相同
- `placeStem()` 完全相同（都用 `Blocks.MUSHROOM_STEM`）
- `canGrowStage()` 几乎相同（仅方块名不同）
- `tryPlaceCapBlock()` 80% 相同

**修复:** 提取 `MushroomGrowthProfile` 抽象基类，颜色/方块类型作为构造参数。

#### P2-10: 5 个 tree profile 的 canGrowStage() 完全相同

**位置:** Oak, Birch, Spruce (单体) 的 `canGrowStage()` 相同；Jungle, DarkOak (2x2) 的相同。

**修复:** 在 `TreeGrowthProfile` 接口中提供 `default` 实现：`canGrowStageSingle()` 和 `canGrowStage2x2()`。

#### P2-11: 树类型 ID 硬编码字符串，无规范注册表

**位置:** `TreeGrowthHandler.java:38-51` — 12 个硬编码 `id("oak")`, `id("oak_sapling")` 等字符串。

添加新树种需要：创建 profile 类 + 在 handler 中注册 2 个条目（树种名 + sapling 名）+ `MorphologyParams` 工厂方法。`resolveProfile` 的 `_sapling` 后缀 fallback 是脆弱的字符串操作。

**修复:** 定义 TreeType 枚举或集中注册表，`Profile` 实现返回其 `treeType()`。

#### P2-12: 客户端 visual 数据流有 4 层中间对象

服务端 `VegetationVisualState` → `VegetationVisualSyncEntry` → 网络 → `VisualLifecycleExternalState` → `VisualLifecycleRenderState`

(stage, progress) 对经过 4 个不同的 record 类型传播。中间的 `VegetationVisualState` 只有 3 行代码。

**修复:** 简化数据流，合并中间的薄 wrapper。

#### P2-13: 动画类型使用 ordinal 相关 byte 常量

**位置:** `plant/tree/GrowthPlacement.java:13-15`, `client/growth/ClientGrowthAnimationManager.java:29-36`

服务端定义 `ANIM_TRUNK=0`, `ANIM_LEAF_INFLATE=1`, `ANIM_LEAF_CLUSTER=2`。客户端使用 `PARAMS[animType]` 数组索引。如果服务端添加新类型而客户端 `PARAMS` 数组不同步 → 静默回退到 `PARAMS[0]`。

**修复:** 两端共享 AnimationStyle 枚举，通过网络发送枚举常量名而非 byte。

---

### P3 — 低危（代码清洁度、风格问题）

#### P3-1: Inconsistent singleton pattern
`VegetationTracker` 和 `TreeGrowthHandler` 使用 `INSTANCE` 单例模式；`SuccessionService`, `PlantSpawner`, `SuccessionEvaluator`, `BiomeTransitionService` 全部使用 static 方法。应统一。

#### P3-2: SuccessionConfigRegistry 的 volatile + synchronized 冗余
字段已 `volatile`，方法已 `synchronized`。`synchronized` 本身提供 memory barrier，`volatile` 多余。

#### P3-3: SuccessionChunkData.ACTIVE_PLANTS 常量未使用
遗留自旧 `activePlants` 系统的命名常量，当前序列化代码不再引用。

#### P3-4: JungleGrowthProfile 重复 import
`import java.util.List;` 出现两次。

#### P3-5: SkeletonGenerator 未使用变量 `currentPos`
第 58 行赋值，第 101 行重新赋值，但从未读取。

#### P3-6: 文档描述不存在的类
`docs/tree-growth-system.md` 第 173-199 行描述 `BlockDisplayAnimator` 使用 Display 实体 + 反射。实际实现是 `ClientGrowthAnimationManager`（直接方块渲染），无 Display 实体，无反射。

#### P3-7: ModChunkEvents 全局可变 static speedMultiplier
`public static int speedMultiplier` 被 4+ 子系统读取。全局可变状态是并发隐患。

---

## 分步执行计划

### 第 1 步: 修复 P0 严重 Bug（不改变架构，最小改动）

**目标:** 消除运行时错误和性能退化，不涉及重构。

| # | 操作 | 文件 |
|---|------|------|
| 1 | 修复 `ensureSkeleton()` 的 `==` → `.equals()` | `TreeGrowthSession.java:55` |
| 2 | 统一高度数据：令 profile 的 `minTrunkHeight/maxTrunkHeight` 委托到 `morphologyParams()` 的值 | 6 个 profile + `MorphologyParams.java` |
| 3 | 修复 `onGrowthComplete()` 的 use-after-remove：从 session 获取 treeType 而非从 map 重新查找 profile | `TreeGrowthHandler.java:260-296` |
| 4 | 修复客户端 visual adapter 匹配范围与服务器对齐（TALL_GRASS, TALL_FLOWERS → Grass/Flower adapter, BROWN/RED_MUSHROOM → 新增 adapter 或扩展现有） | 客户端 visual adapters |

**验证:** `./gradlew compileJava` 通过，启动游戏测试橡树生长动画不再出现阶段性卡顿。

---

### 第 2 步: 删除死代码

**目标:** 消除未使用的代码路径，减少维护负担。

| # | 操作 | 文件 |
|---|------|------|
| 1 | 删除 6 个 tree profile 中的旧 `growStage()` 实现 | 6 个 profile 文件 |
| 2 | 删除 `TreeShapeUtils` 中仅被旧 `growStage()` 调用的函数 | `TreeShapeUtils.java` |
| 3 | 删除 `GrowthBlockCapture.java` 整个类 | `GrowthBlockCapture.java` |
| 4 | 删除 `LevelMixin` 中 `GrowthBlockCapture` 相关注入 | `LevelMixin.java` |
| 5 | 删除 `TreeGrowthHandler` 中 morphology vs legacy 分支判断，统一走 morphology 路径 | `TreeGrowthHandler.java` |
| 6 | 为蘑菇 profile 实现 `morphologyParams()` 或单独的 morphology-compatible growth path | 蘑菇 profile |
| 7 | 删除 `ChunkRules.evaluationIntervalDays.max`（或接入评估器使用它） | `ChunkRules.java`, `SuccessionEvaluator.java` |
| 8 | 删除 `PlantSpawnRules.placement` | `PlantSpawnRules.java`, `SuccessionConfigLoader.java` |
| 9 | 删除 `PlantDefinition.category`（或改为 `VegetationCategory` 枚举 + 校验） | `PlantDefinition.java`, `SuccessionConfigLoader.java` |
| 10 | 删除 `ActiveVegetationRecord.category`（或在点值/评估中使用它） | `ActiveVegetationRecord.java` |
| 11 | 删除 `SuccessionChunkData.ACTIVE_PLANTS` 常量 | `SuccessionChunkData.java` |
| 12 | 删除 `SkeletonGenerator.currentPos` 未使用变量 | `SkeletonGenerator.java` |
| 13 | 删除 `SuccessionConfigRegistry` 的 `volatile` 修饰符 | `SuccessionConfigRegistry.java` |

**验证:** `./gradlew build` 通过，确认所有 Minecraft 6 树种 + 2 蘑菇生长测试正常。

---

### 第 3 步: 消除高危重复

**目标:** 每个业务规则只有一处定义，修改不需要跨文件同步。

| # | 操作 | 文件 |
|---|------|------|
| 1 | 完善 `SuccessionChunkData.clearRuntimeState()` 使其清除所有运行时字段，3 处重复调用方改为调用该方法 | `SuccessionChunkData.java`, `BiomeTransitionService.java`, `SuccessionTargetResolver.java` |
| 2 | 提取生命周期时长常量到 `VegetationTypeAdapter` 接口的 default 方法或共享常量类，更新 3 处引用 | `VegetationTypeAdapter.java`, `SimplePlantAdapter.java`, `SaplingAdapter.java`, `PrototypeChunkController.java` |
| 3 | 提取 `progress()` 为 `VegetationTypeAdapter` 的 default/static 方法 | `VegetationTypeAdapter.java`, `SimplePlantAdapter.java`, `SaplingAdapter.java`, `TreeStructureAdapter.java` |
| 4 | `ModPlayerEvents.onBlockPlaced/onBlockBroken` 改为调用 `VegetationTracker.INSTANCE.trackAt()/untrack()` | `ModPlayerEvents.java` |
| 5 | 提取 `SuccessionService` 的 pipeline 核心逻辑到 `doPipeline(chunk, options)` 方法，`step()` 和 `processChunkTick()` 变为薄包装 | `SuccessionService.java` |
| 6 | 提取默认颜色逻辑到共享方法，命名为 `DEFAULT_DEAD_BUSH_COLOR` | `VisualLifecycleClientRuntime.java`, `ModClientVisualLifecycle.java` |
| 7 | `PrototypeChunkController` 改为调用 `SuccessionService` 而非重新实现管线 | `PrototypeChunkController.java` |
| 8 | 提取 `TreeGrowthProfile` 的 `canGrowStage()` 为 default 方法（`canGrowStageSingle` / `canGrowStage2x2`） | `TreeGrowthProfile.java`, 5 个受影响的 profile |

**验证:** `./gradlew build` 通过，运行游戏验证正常/加速模式演替循环行为不变。

---

### 第 4 步: 统一阶段枚举和客户端服务端映射

**目标:** 消除两个生命周期阶段枚举之间的脆弱映射。

| # | 操作 | 文件 |
|---|------|------|
| 1 | 将 `VisualLifecycleStage` 合并到 `VegetationLifecycleStage`（客户端使用服务端枚举），或保留客户端枚举但通过集中配置表映射 | 两个枚举 + 映射函数 |
| 2 | 客户端 visual profile 的阶段时长改为从服务端同步或共享常量源 | 客户端 adapters, 服务端 adapters |
| 3 | 统一客户端 adapter 匹配范围与服务器 adapter 完全一致 | 客户端 adapters, 服务端 adapters |

**验证:** 所有测试通过，视觉行为不变。

---

### 第 5 步: 提取公共基础设施

**目标:** 建立可扩展的注册表、减少蘑菇重复代码。

| # | 操作 | 文件 |
|---|------|------|
| 1 | 提取 `MushroomGrowthProfile` 抽象基类，消除 Brown/Red 蘑菇代码重复 | 蘑菇 profile |
| 2 | 定义 `TreeType` 集中注册表，消除 `TreeGrowthHandler` 中硬编码字符串 | `TreeGrowthHandler.java`, profiles |
| 3 | 拆分 `ModCommands` 为独立命令处理器类 | `ModCommands.java` |
| 4 | `ChunkRules` 添加 `transition_rewards` 配置段，替换 `BiomeTransitionService` 中硬编码值 | `ChunkRules.java`, `BiomeTransitionService.java`, JSON 文件 |
| 5 | `ModChunkEvents.speedMultiplier` 移至非静态配置对象 | `ModChunkEvents.java`, 所有读取方 |
| 6 | 更新 `docs/tree-growth-system.md` 修正过时文档描述 | `docs/tree-growth-system.md` |

**验证:** `./gradlew build` 通过。

---

### 第 6 步: 最终验证

| # | 操作 |
|---|------|
| 1 | `./gradlew build` 完整构建 |
| 2 | `./gradlew runClient` 启动游戏，测试：橡树/白桦/云杉/丛林/深色橡树/金合欢渐进生长 |
| 3 | 测试演替完整循环：初始化 → 植物生成 → 进度增长 → 群系转换 |
| 4 | 测试退化流程 |
| 5 | 测试 `/ecoflux prototype` 调试命令 |
| 6 | 更新 `CLAUDE.md` 和 `docs/` 中所有相关文档 |

---

## 变更影响范围对照表

| 变更内容 | 需要更新的文件数（当前） | 优化后 |
|----------|------------------------|--------|
| 新增 chunk 数据重置字段 | 3 | 1 |
| 修改植物生命周期时长 | 3 | 1 |
| 修改演替 pipeline 步骤顺序 | 5 | 1 |
| 新增树种的 growth profile | 3 处注册 + profile 类 | 2（profile 类 + 注册表） |
| 修改群系转换参数 | 1（但不可配置） | 1（JSON 配置） |
| 客户端同步视觉状态 | 4 层中间对象 | 2-3 层 |
| 修改默认颜色 | 2 | 1 |
| 修改动画类型 | 2（服务端+客户端独立定义） | 1（共享枚举） |

---

## 预期收益

1. **Bug 修复**: 修复骨架重复生成性能问题，修复高度数据不一致，修复 use-after-remove
2. **删除 ~450 行死代码**: 旧 growStage + GrowthBlockCapture + 未使用字段/常量
3. **消除 ~30 处重复**: 重置管线、生命周期时长、颜色逻辑、progress()、canGrowStage、蘑菇 profile 等
4. **可维护性**: 每个业务规则单一定义点，修改不遗漏
5. **可扩展性**: 注册表模式替代硬编码列表，JSON 配置覆盖更多参数
