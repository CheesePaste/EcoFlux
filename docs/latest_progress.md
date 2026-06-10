# Latest Progress

本文档记录当前分支里“单植物 + 单路径 + 单区块进度”最小原型的最新状态，方便后续继续开发时快速恢复上下文。

## 当前结论

- 最小闭环仍然成立：
  - 区块加载时可匹配 `ecoflux:plains_to_forest`
  - 原型植物可以放置
  - 进度满后会把整个区块 biome 切到 `minecraft:forest`
- 自动模式已经恢复，并继续复用命令调通后的底层逻辑。
- 客户端视觉生命周期层已经不再只是命令玩具，**现在已接入 plant lifecycle system 的最小自动通路**：
  - 服务端 `VegetationTracker` 维护 `ActiveVegetationRecord`
  - 自动 tick / 调试命令会推进 `observeChunk(...)`
  - 服务端会把区块内生命周期视觉快照同步给客户端
  - 客户端 `VisualLifecycleClientRuntime` 会直接消费外部阶段/进度做 scale 与 aging tint
- 当前视觉层仍保持独立抽象：
  - 手动命令调试仍可用
  - 生命周期系统自动驱动也可用
  - 两者通过 `source=MANUAL / VEGETATION_SYSTEM` 区分

## 当前原型范围

- 重点原型路径：`ecoflux:plains_to_forest`
- 当前原型植物：`minecraft:dandelion`
- 当前目标：验证单路径、单植物、单区块进度的完整运行链路，而不是正式平衡性

## 当前代码落点

- 主入口：`src/main/java/com/s/ecoflux/EcofluxMod.java`
- 区块附件：`src/main/java/com/s/ecoflux/attachment/SuccessionChunkData.java`
- 生命周期记录：`src/main/java/com/s/ecoflux/attachment/ActiveVegetationRecord.java`
- 原型核心逻辑：`src/main/java/com/s/ecoflux/prototype/PrototypeChunkController.java`
- 生命周期追踪器：`src/main/java/com/s/ecoflux/plant/VegetationTracker.java`
- 生命周期适配器接口：`src/main/java/com/s/ecoflux/plant/VegetationTypeAdapter.java`
- 当前植物适配器：`src/main/java/com/s/ecoflux/plant/SimplePlantAdapter.java`
- 当前树苗适配器：`src/main/java/com/s/ecoflux/plant/SaplingAdapter.java`
- 当前树结构适配器：`src/main/java/com/s/ecoflux/plant/TreeStructureAdapter.java`
- 生命周期 -> 视觉同步：`src/main/java/com/s/ecoflux/network/ModNetworking.java`
- 生命周期视觉载荷：`src/main/java/com/s/ecoflux/network/VegetationVisualChunkSyncPayload.java`
- 客户端视觉接口：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleAdapter.java`
- 客户端视觉运行时：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleClientRuntime.java`
- 世界重绘入口：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleWorldRenderer.java`
- 基础方块跳过渲染 Mixin：`src/main/java/com/s/ecoflux/mixin/client/BlockRenderDispatcherMixin.java`
- 调试命令：`src/main/java/com/s/ecoflux/init/ModCommands.java`
- 原型路径配置：`src/main/resources/data/ecoflux/succession_paths/plains_to_forest.json`

## 已实现能力

### 1. 区块初始化与原型队列

- 区块加载时会采样 biome / temperature / downfall 并匹配路径
- 匹配成功后初始化：
  - `currentBiome`
  - `targetBiome`
  - `previousBiome`
  - `activePathId`
  - `consumingValue`
  - `maxPlantCount`
  - `plantQueue`
- `plains_to_forest` 当前是单植物原型，队列为空时也会自动补回原型植物条目

### 2. 原型植物种植与生命周期记录

- 服务端会在区块内寻找合法放置点并放置 `minecraft:dandelion`
- 成功种下后：
  - 写入 `activePlants`
  - 同时调用 `VegetationTracker.trackAt(...)`
  - 生成 `ActiveVegetationRecord`
- `SimplePlantAdapter` 现在会在出生时就写入 born 阶段基础点数，而不是初始 0 点

### 3. 生命周期观察与视觉同步

- `VegetationTracker` 现在支持：
  - `trackAt(...)`
  - `observeTracked(...)`
  - `observeChunk(...)`
  - 构建区块级视觉同步快照
- 原型自动 tick 和 `/ecoflux prototype step` 现在都会执行：
  - `prune`
  - `spawn`
  - `observeChunk`
  - `evaluate`
- 服务端会在以下时机同步当前区块视觉快照：
  - 植被 track
  - 植被 observe / transform / remove
  - 玩家开始接收该 chunk
  - 玩家停止追踪该 chunk
- 客户端 visual runtime 现在支持两种来源：
  - `MANUAL`
  - `VEGETATION_SYSTEM`

### 4. 客户端视觉生命周期层

- 视觉层仍通过 world render fallback 路径保证 scale 一定可见
- 当前可验证：
  - `born -> mature` 的 scale 放大
  - `aging` 的颜色衰老
- 当前支持范围：
  - 地被：`minecraft:short_grass` / `minecraft:fern` / `minecraft:dead_bush`
  - 花：`#minecraft:small_flowers`
  - 树苗：`#minecraft:saplings`
  - fallback：任意被 track 的非空气方块
- 生命周期系统同步过来的阶段/进度会直接驱动 visual runtime 的 `externalState`

### 5. 进度评估与 biome 转化

- 当前区块进度仍来自 `activePlants`
- `totalPlantPoints >= consumingValue` 时按正步长推进
- 否则按负步长回退
- 达到 `>= 1.0` 时会真实把整个区块 biome 切到 `minecraft:forest`
- biome 转化完成后会清理 prototype 状态与生命周期视觉同步状态

## 当前命令

以下命令默认作用于“玩家脚下所在区块”：

### Prototype

- `/ecoflux prototype init`
  - 重新初始化当前区块并重建原型状态
- `/ecoflux prototype status`
  - 查看当前区块状态
- `/ecoflux prototype prune`
  - 清理失效追踪植物
- `/ecoflux prototype spawn`
  - 手动尝试种植一次
- `/ecoflux prototype evaluate`
  - 手动做一次进度评估
- `/ecoflux prototype step`
  - 一次执行 `prune + spawn + observeChunk + evaluate`
- `/ecoflux prototype transition`
  - 强制当前区块立刻完成 biome 转化

### Prototype Auto

- `/ecoflux prototype auto on`
- `/ecoflux prototype auto off`
- `/ecoflux prototype auto status`

### Global Auto

- `/ecoflux auto on`
  - 一键开启“全套自动”：
    - 初始化当前区块
    - 接回 tracked chunk
    - 开启自动处理
    - 立刻执行一次 `step`
- `/ecoflux auto off`
- `/ecoflux auto status`

### Lifecycle

- `/ecoflux lifecycle inspect <x> <y> <z>`
  - 查看某个方块当前会被哪个生命周期 adapter 识别
- `/ecoflux lifecycle track <x> <y> <z>`
  - 手动把一个植被对象注册进生命周期系统
- `/ecoflux lifecycle observe <x> <y> <z>`
  - 手动观察一个 tracked 位置
- `/ecoflux lifecycle untrack <x> <y> <z>`
  - 手动移除一个 tracked 位置
- `/ecoflux lifecycle chunk`
  - 查看当前区块 tracked vegetation 概览
- `/ecoflux lifecycle observe_chunk`
  - 立刻观察当前区块内所有 tracked vegetation，并刷新客户端视觉状态
- `/ecoflux lifecycle sync_chunk`
  - 立刻把当前区块的生命周期视觉快照重发给客户端

### Visual

以下是客户端视觉调试命令，作用于指定坐标方块：

- `/ecoflux visual start <x> <y> <z>`
- `/ecoflux visual stop <x> <y> <z>`
- `/ecoflux visual inspect <x> <y> <z>`
- `/ecoflux visual stage <x> <y> <z> <born|growing|mature|aging>`
- `/ecoflux visual list`
- `/ecoflux visual clear`
- `/ecoflux visual help`
- `/ecoflux visual scale_override <value>`
- `/ecoflux visual scale_override clear`

## 当前验证结论

- 本地已验证 `./gradlew.bat compileJava` 成功
- 本地已验证 `./gradlew.bat build` 成功
- 生命周期系统已经不再只是服务端数据骨架，当前最小通路已能直接驱动客户端视觉层
- 命令调试入口和自动入口现在都能走到同一套生命周期 -> 视觉同步链路

## 已知现状与限制

- 当前仍然是原型实现，不是正式玩法版本
- 当前只重点保证 `plains_to_forest` 的最小闭环
- 当前区块进度结算已经使用 `vegetationRecords` 的贡献积分（GROWING/MATURE/AGING 阶段）与 consuming 对比
- 玩家放置/破坏方块会触发 `VegetationTracker` 的自动追踪/取消追踪
- 当前视觉层负责表现，不负责真实出生/死亡来源判定
- 对任意 tracked 方块都能保证 scale 效果，但颜色衰老效果只会在可着色植物上更明显
- 目前的自动模式仍是原型级调度，还没有做更细的负载控制和更广泛的数据同步设计
- 当前 biome 转化仍然是“整块直接切换”，边界混合尚未开始做

## 2026-06-07：从原型中提取正式服务层

### 变更概要

- 从 `PrototypeChunkController`（原 827 行）中提取核心逻辑到 6 个新服务类
- `PrototypeChunkController` 瘦身为 ~175 行，仅保留 10 秒加速演示模式

### 新建文件

| 类 | 包 | 职责 |
|---|---|---|
| `ChunkSamplingHelper` | `world/` | 群系/气候采样、生成位置查找、放置规则校验 |
| `PlantSpawner` | `plant/` | 植物队列管理、加权抽取、种植、过期清理 |
| `SuccessionTargetResolver` | `succession/` | 区块初始化：采样 + 配置匹配 + 数据填充 |
| `SuccessionEvaluator` | `succession/` | 进度评估（衰老门控 + consuming 对比） |
| `BiomeTransitionService` | `succession/` | 群系替换 + 树木种植 + 状态重置 |
| `SuccessionService` | `succession/` | 编排入口：step、processChunkTick、单步调试方法 |

### 修改文件

- `PrototypeChunkController` — 仅保留 `accelerate()`、`processAcceleratedTick()` 及加速专用私有方法，其余委托给新服务
- `ModChunkEvents` — `isPrototypeChunk` 泛化为 `SuccessionService.hasActivePath`（现在任何有激活演替路径的区块都可参与自动 tick）
- `ModCommands` — 标准操作调用 `SuccessionService`，加速操作保留 `PrototypeChunkController`

### 关键泛化

`isPrototypeChunk`（硬编码 `plains_to_forest`）→ `hasActivePath(chunkData)`（检查是否有任何激活的演替路径 ID），使自动 tick 系统对所有配置路径生效。

## 2026-06-07：vegetationRecords 积分接入进度结算

### 变更概要

- `SuccessionEvaluator` 不再使用 `hasAgingVegetation` 衰老门控
- 改为统计 GROWING/MATURE/AGING 阶段的植被作为"贡献植被"
- 贡献植被的 `currentPointValue` 总和与 `consumingValue` 对比决定进度方向
- `SuccessionChunkData` 新增 `getContributingVegetationPoints()`、`hasContributingVegetation()`、`countContributingVegetation()`
- `describeChunk` 输出新增贡献积分和贡献植被数

### 评估逻辑

1. 无贡献植被（所有植被均处于 BORN 阶段）→ 进度不变，等待植被成长
2. 贡献积分 ≥ 消耗阈值 → 进度正向推进
3. 贡献积分 < 消耗阈值 → 进度回退

## 2026-06-07：玩家放置/破坏事件接入 VegetationTracker

### 变更概要

- 新建 `ModPlayerEvents` 监听 `BlockEvent.EntityPlaceEvent` 和 `BlockEvent.BreakEvent`
- 玩家放置匹配植被适配器的方块时，自动调用 `VegetationTracker.trackAt()` 逻辑并同步客户端
- 玩家破坏已被追踪的植被方块时，自动从 `vegetationRecords` 中移除并同步客户端
- 在 `EcofluxMod` 中注册新的事件处理器

### 新增文件

- `init/ModPlayerEvents.java` — 玩家方块放置/破坏事件 → VegetationTracker

## 2026-06-07：多植物队列与权重抽取完成

### 变更概要

- 修复 `ensureQueue` 忽略配置中 `queue_fill_factor` 的 bug，现在使用 `ChunkRules.queueCapacity()` (= maxPlantCount × queueFillFactor)
- `plains_to_forest` 队列容量从 8 → 16，显著提高多植物多样性
- 新增 `getQueueSummary()` — 按植物类型分组展示队列内容
- 新增 `forceRefillQueue()` — 强制重新填充队列
- `describeChunk` 输出现在包含队列摘要（类型分布）
- 新增调试命令：
  - `/ecoflux prototype queue` — 查看当前队列分布
  - `/ecoflux prototype plants` — 查看当前路径下所有配置植物及权重
  - `/ecoflux prototype refill` — 强制重新填充队列

### 修改/新增文件

- `plant/PlantSpawner.java` — 修复 queueCapacity，新增 getQueueSummary / forceRefillQueue
- `init/ModCommands.java` — 新增 queue / plants / refill 子命令
- `succession/SuccessionService.java` — describeChunk 输出包含队列摘要

## 2026-06-07：负向回退 + activePlants 退役

### A. 负向回退 → Fallback 群系切换

- `SuccessionEvaluator` 新增 `shouldRegress()` — 进度 ≤ -1.0 时返回 true
- `BiomeTransitionService` 新增 `applyRegression()` — 将群系切换到 `fallbackBiome` 或 `previousBiome`，重置状态（不种树）
- `SuccessionService.step()` / `evaluateChunk()` / `processChunkTick()` — 评估后检查 regression 条件并触发

### B. activePlants 退役

- `SuccessionChunkData` — 移除 `activePlants`、`ActivePlantRecord` 引用、`trackPlant()`、`removeTrackedPlant()`、`clearTrackedPlants()`、`getActivePlants()`、`getTotalPlantPoints()`、`currentPlantCount` 字段；`getCurrentPlantCount()` 现在返回 `vegetationRecords.size()`；NBT 序列化不再写入 ACTIVE_PLANTS
- `PlantSpawner.trySpawnPlant()` — 移除 `trackPlant()` 调用，仅依赖 `VegetationTracker.trackAt()`
- `PlantSpawner.pruneInvalidPlants()` — 改为基于 `vegetationRecords` 遍历，用 adapter 匹配检查方块是否仍然有效
- `ChunkSamplingHelper.countNearbyTrackedPlants()` — 改用 `vegetationRecords`
- `PrototypeChunkController.setAcceleratedVegetationStage()` — 移除 activePlants 迭代块
- `BiomeTransitionService.applyTransition()` — 移除 `clearTrackedPlants()` 调用
- 删除 `ActivePlantRecord.java`

## 2026-06-07：树生命周期 Phase 1 — Mixin 拦截原版树苗生长

### 变更概要

- 新增 `SaplingBlockMixin`，拦截 `SaplingBlock.advanceTree()` 阻止被追踪树苗的原版瞬间生长
- 新增 `TreeGrowthHandler`（单例，管理活跃生长会话）和 `TreeGrowthSession`（每棵树生长状态，支持 NBT）
- `SaplingAdapter.TYPE_ID` 改为 public 供 Mixin 引用
- 玩家放置树苗自动追踪（`ModPlayerEvents`），未被追踪的树苗完全保持原版行为
- STAGE 0→1 放行（树苗视觉成熟），STAGE 1→树生成 拦截（Mixin 取消 + 创建生长会话）

### 新增文件

- `mixin/SaplingBlockMixin.java` — 服务端 Mixin，拦截 SaplingBlock.advanceTree()
- `plant/tree/TreeGrowthHandler.java` — 生长管理器，维护活跃生长会话 Map
- `plant/tree/TreeGrowthSession.java` — 生长会话状态（位置、树种、阶段、时间）

### 修改文件

- `plant/SaplingAdapter.java` — TYPE_ID 改为 public
- `ecoflux.mixins.json` — 注册 SaplingBlockMixin 到 mixins 数组

### 验证结果

- 编译通过
- 游戏内测试：被追踪树苗使用骨粉不会瞬间长成树，日志输出拦截信息
- 未被追踪的树苗（untrack 后）正常生长为树

## 2026-06-07：树生命周期 Phase 2 — 渐进式树木生长系统

### 变更概要

- 新增 `TreeGrowthProfile` 接口 — 定义树种生长参数（阶段数、阶段间隔、空间检查、方块放置）
- 新增 `OakGrowthProfile` — 橡树 5 阶段渐进生长：每阶段延伸 1 格树干 + 扩展半径树叶 + 顶部冠层
- `TreeGrowthHandler` 新增 `tickAll()` / `onGrowthComplete()` / `resolveProfile()` — 树苗→原木替换 + 自动追踪为 TreeStructure
- `ModChunkEvents` 新增 `processTreeGrowth()` — 每 20 tick 驱动树生长，独立于 succession auto 模式
- 修复：`treeTickCounter` 多维度共享 bug，改为 `gameTime % 20` 每维度独立

### 新增文件

- `plant/tree/TreeGrowthProfile.java` — 树种生长参数接口
- `plant/tree/profiles/OakGrowthProfile.java` — 橡树 5 阶段渐进生长

### 修改文件

- `plant/tree/TreeGrowthHandler.java` — tickAll / onGrowthComplete / resolveProfile
- `init/ModChunkEvents.java` — processTreeGrowth 每 20 tick 驱动

### 验证结果

- 游戏内测试：树苗→5阶段渐进生长→完成替换为原木，全流程日志可见

### 已知限制

- 同一位置重新种树会产生相同形状（位置确定性噪声），设计如此以保证视觉一致性
- 2x2 树的 NW 角检测假设树苗排列为左上对齐，极端边界情况可能有偏差
- 树冠可能略微超出区块边界，canGrowStage 仅检查高度上限

## 2026-06-10：树生命周期 Phase 3 — 真实树木生长系统

### 变更概要

- 完全重写 `TreeGrowthProfile` 接口：新增高度范围、log/leaves 方块类型、2x2 支持、每实例随机高度解析
- 新建 `TreeShapeUtils` 共享工具类：位置确定性噪声（同树同形）、冠形函数（橡扁球/白桦椭圆/云杉锥形）、树叶圆形放置（非方形）、2x2 检测/角定位、枝干生成
- `TreeGrowthSession` 新增 `resolvedHeight` 字段 + NBT 持久化
- `TreeGrowthHandler` 注册全部 6 个原版树种，`onGrowthComplete()` 使用 `profile.logBlock()`，支持 2x2 替换
- 重写 `OakGrowthProfile`：扁球形宽冠 5-8 格，3600 tick/阶段（~27 分钟）
- 新建 5 个 profile：
  | 树种 | 高度 | 间隔 | 总耗时 | 形态 |
  |------|------|------|--------|------|
  | Birch | 6-10 | 2400 | ~20 分钟 | 细高椭圆顶冠 |
  | Spruce | 8-15 | 4800 | ~48 分钟 | 锥形全高覆叶 |
  | Jungle | 10-15 | 4800 | ~64 分钟 | 2x2 粗干+宽冠+侧枝 |
  | Dark Oak | 6-10 | 3600 | ~30 分钟 | 2x2 粗干+密冠 |
  | Acacia | 5-10 | 3600 | ~27 分钟 | 斜干+稀疏不规则冠 |
- 开关 `gradualTreeGrowth` 行为不变：false → 原版瞬间生长，树长相不变

### 修改/新增文件

- `plant/tree/TreeGrowthProfile.java` — 接口扩展
- `plant/tree/TreeShapeUtils.java` — 新建：噪声/冠形/放置/2x2/枝干 共享工具
- `plant/tree/TreeGrowthSession.java` — 新增 resolvedHeight
- `plant/tree/TreeGrowthHandler.java` — 6 树种注册 + 2x2 + profile.logBlock()
- `plant/tree/profiles/OakGrowthProfile.java` — 重写
- `plant/tree/profiles/BirchGrowthProfile.java` — 新建
- `plant/tree/profiles/SpruceGrowthProfile.java` — 新建
- `plant/tree/profiles/JungleGrowthProfile.java` — 新建
- `plant/tree/profiles/DarkOakGrowthProfile.java` — 新建
- `plant/tree/profiles/AcaciaGrowthProfile.java` — 新建

### 验证结果

- 编译通过

## 2026-06-10：新树木形态系统设计

### 设计文档

完成 `docs/tree-morphology-design.md` — 树木形态系统完整设计方案。

核心思路：用 **参数化递归骨架 + 3D 冠包络体 + 骨架感知树叶填充** 替代当前的 "直杆 + 圆盘" 树形。

关键设计：
- `TreeSkeleton` — 骨架数据结构（节点列表、主干路径、分枝列表）
- `SkeletonGenerator` — 递归参数化分枝生成（主千 + 一级枝 + 二级枝 + 细枝）
- `CanopyEnvelope` — 5 种 3D 冠形数学函数（橡扁椭球/白桦细椭球/云杉圆锥/丛林簇团/金合欢碟形）
- `LeafFiller` — 树叶放置策略（骨架距离 + 包络体密度 + 噪声 + 边缘羽化）
- `MorphologyParams` — 每个树种的形态参数 record
- 树叶 DISTANCE 改为到最近骨架节点距离（而非到树干）

实现分 5 个 Phase，Phase 1-3 为骨架+冠形+阶段化（P0），Phase 4 为 6 树种接入（P1）。

## 2026-06-10：树形态系统 Phase 1-5 实现

### 变更概要

基于 `docs/tree-morphology-design.md` 设计方案，完整实现了新的树木形态系统。

**Phase 1: 骨架系统**
- `NodeType` — 节点类型枚举（TRUNK/PRIMARY_BRANCH/SECONDARY_BRANCH/TWIG）
- `SkeletonNode` — 骨架节点 record（pos, type, radius, parentIndex, depth）
- `TreeSkeleton` — 骨架数据结构（节点列表、主千路径、一级枝列表、2x2 trunkLevels 支持）
- `SkeletonGenerator` — 参数化递归分枝生成：
  - 主千：可倾角 + 噪声扰动（金合欢大幅倾斜，橡树微倾，云杉垂直）
  - 一级枝：从主千特定高度范围分出，径向 + 上偏角 + 水平扰动
  - 二级枝：从一级枝中段分出（仅大冠树种），偏离主枝方向 30°-60°
  - 2x2 树干：每层 4 节点，父子索引正确对应

**Phase 2: 冠形与填充**
- `CanopyEnvelope` — 5 种 3D 冠形密度函数 + 边缘羽化：
  - ELLIPSOID（橡树扁椭球） / TALL_ELLIPSOID（白桦细长椭球）
  - CONE（云杉圆锥） / CLUSTERED_ELLIPSOID（丛林大椭球+子冠）
  - FLAT_CYLINDER（深色橡树扁圆柱密实） / FLAT_DISC_CLUSTERED（金合欢扁碟+散落簇）
- `LeafFiller` — 骨架感知树叶放置：
  - 候选位置遍历包围盒（AABB），排除非空气/非树叶方块
  - 包络体密度 > 阶段阈值 → 骨架距离 → 概率（branchProximity × density × edgeFactor × clustering + noise）
  - 按距离排序优先放置近骨架位置，每阶段上限 50 树叶
  - 树叶 DISTANCE 改为到最近骨架节点（TRUNK/PRIMARY/SECONDARY，不含 TWIG）的 Chebyshev 距离
  - 世界高度边界检查

**Phase 3: 阶段离散化**
- `MorphologyParams` — 形态参数 record，每个树种工厂方法（oak/birch/spruce/jungle/darkOak/acacia）
- `TreeMorphology` — 整合入口：
  - `generateSkeleton()` → `planStages()` → `growStage()`
  - 阶段分组：主千按深度分组 + 枝节点分配到对应主千阶段 + 剩余节点均分到冠阶段
  - growStage 逻辑：放置原木方块（TRUNK/PRIMARY/SECONDARY） + 调用 LeafFiller 填充树叶

**Phase 4: 6 树种接入**
- 所有 6 个 profile 新增 `morphologyParams()` 方法，返回对应的 MorphologyParams
- `TreeGrowthProfile` 接口新增 `morphologyParams()` 默认方法（返回 null 保持向后兼容）
- `TreeGrowthHandler` 检测 profile 是否有 morphology params：
  - 有 → `session.ensureSkeleton()` → `TreeMorphology.growStage()`
  - 无 → 回退到旧的 `profile.growStage()` 逻辑
- `TreeGrowthSession` 新增 transient 字段：skeleton、morphologyParams、stagePlan（不持久化，从种子重建）

**Phase 5: 优化与打磨**
- 2x2 树 trunkLevels 修复（区分 trunkPath 节点数和实际高度层数）
- 2x2 树父子索引正确映射（每层节点指向上一层对应角落节点）
- 世界高度边界检查（原木和树叶放置均检查 maxBuildHeight）
- 每阶段树叶上限 50 个

### 新增文件

| 文件 | 职责 |
|------|------|
| `plant/tree/morphology/NodeType.java` | 骨架节点类型枚举 |
| `plant/tree/morphology/SkeletonNode.java` | 骨架节点 record |
| `plant/tree/morphology/TreeSkeleton.java` | 骨架数据结构 |
| `plant/tree/morphology/SkeletonGenerator.java` | 递归参数化分枝生成 |
| `plant/tree/morphology/CanopyEnvelope.java` | 5 种 3D 冠形密度函数 |
| `plant/tree/morphology/LeafFiller.java` | 骨架感知树叶放置策略 |
| `plant/tree/morphology/MorphologyParams.java` | 树种形态参数 record |
| `plant/tree/morphology/TreeMorphology.java` | 形态系统总入口 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `plant/tree/TreeGrowthSession.java` | 新增 skeleton/morphologyParams/stagePlan transient 字段 + ensureSkeleton() |
| `plant/tree/TreeGrowthProfile.java` | 新增 morphologyParams() 默认方法 |
| `plant/tree/TreeGrowthHandler.java` | tickAll/forceAdvanceStage/interceptGrowth 均接入 morphology 管线 |
| `plant/tree/profiles/OakGrowthProfile.java` | 新增 morphologyParams() → MorphologyParams.oak() |
| `plant/tree/profiles/BirchGrowthProfile.java` | 新增 morphologyParams() → MorphologyParams.birch() |
| `plant/tree/profiles/SpruceGrowthProfile.java` | 新增 morphologyParams() → MorphologyParams.spruce() |
| `plant/tree/profiles/JungleGrowthProfile.java` | 新增 morphologyParams() → MorphologyParams.jungle() |
| `plant/tree/profiles/DarkOakGrowthProfile.java` | 新增 morphologyParams() → MorphologyParams.darkOak() |
| `plant/tree/profiles/AcaciaGrowthProfile.java` | 新增 morphologyParams() → MorphologyParams.acacia() |

### 架构说明

```
TreeGrowthHandler.tickAll()
  ├─ profile.morphologyParams() != null?
  │   ├─ YES → session.ensureSkeleton() → TreeMorphology.growStage()
  │   │         ├─ 放置当前阶段原木方块 (TRUNK/PRIMARY/SECONDARY 节点)
  │   │         └─ LeafFiller.fillLeaves() (包络体密度 + 骨架距离 + 噪声)
  │   └─ NO  → profile.growStage() (旧逻辑，向后兼容)
  └─ session.advanceStage()
```

骨架在 session 创建时生成（位置确定性种子），不持久化。区块卸载/重载后从 worldSeed + saplingPos 重建相同骨架。

### 验证结果

- 编译通过 (`./gradlew compileJava`)

## 2026-06-08：树生长 BlockDisplay 动画系统

### 变更概要

- 树生长阶段不再瞬间放置方块，改为生成 `BlockDisplay` 实体播放平滑缩放动画
- 动画完成后自动替换实体为真实方块
- 三种动画风格：树干挤出 (TRUNK_EXTRUDE)、树叶膨胀 (LEAF_INFLATE)、树冠簇 (LEAF_CLUSTER)

### 新增文件

- `plant/tree/animation/AnimationStyle.java` — 动画风格枚举（起止缩放 + 持续时间）
- `plant/tree/animation/BlockDisplayAnimator.java` — BlockDisplay 实体生命周期管理（生成、缩放动画、完成替换），使用反射访问 Display 私有 EntityDataAccessor

### 修改文件

- `plant/tree/TreeGrowthProfile.java` — `growStage()` 签名新增 `BlockDisplayAnimator` 参数
- `plant/tree/profiles/OakGrowthProfile.java` — `growStage()` 替换所有 `level.setBlock()` 为 `animator.animateBlock()`
- `plant/tree/TreeGrowthHandler.java` — 持有 `BlockDisplayAnimator` 实例，`tickAll()` 开头调用 `animator.tick()`，`onGrowthComplete()` 使用动画替换树苗

### 动画效果细节

| 风格 | 方块类型 | 起始缩放 | 目标缩放 | 持续时间 |
|------|---------|---------|---------|---------|
| TRUNK_EXTRUDE | 原木 | (0.9, 0.05, 0.9) | (1.0, 1.0, 1.0) | 15 tick |
| LEAF_INFLATE | 树叶 | (0.05, 0.05, 0.05) | (1.08, 1.08, 1.08) | 20 tick |
| LEAF_CLUSTER | 顶部树叶 | (0.05, 0.05, 0.05) | (1.05, 1.05, 1.05) | 12 tick |

树干动画产生"从地面挤出"的视觉效果（Y 轴从 0.05 拉伸到 1.0），树叶动画产生"气球膨胀"效果（全轴从极小放大到正常尺寸，LEAF_INFLATE 带轻微 overshoot 至 1.08）。

### 技术实现要点

- 使用 Java 反射绕过 Mojang 未公开的 `DATA_SCALE_ID` / `DATA_BLOCK_STATE_ID` 等 EntityDataAccessor
- 利用 Minecraft 内置的 Display 变换插值系统，服务端设置 `interpolation_duration` 后客户端自动平滑过渡
- 两帧式触发：第 0 tick 生成实体（初始缩放 + 插值 0 = 瞬时），第 1 tick 设置目标缩放 + 插值持续时间（触发客户度动画）
- 实体意外消失（如区块卸载）时自动补放真实方块

## 建议的下一步

详见 `todolist.md` 底部「下一步计划」章节，以及 `docs/tree-lifecycle-implementation.md` 树生命周期完整方案。

1. **树生命周期 Phase 4** — 树木死亡腐烂（AGING→DEAD→DECAYING 阶段）
2. **C. 非玩家方块变更事件** — 水/岩浆/随机刻导致植被消失时同步清理追踪
3. **D. 区块边界混合** — 缓解群系切换的生硬边界
