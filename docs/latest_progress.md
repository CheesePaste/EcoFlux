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

## 2026-06-07：演替路径可视化编辑器设计 + Phase 1 实现

### 变更概要

- 新建 `docs/visual-succession-editor.md` 完整设计文档（综合性编辑器，覆盖全部可配置内容）
- 将 todolist 项 E 从"手动补充 JSON"升级为"构建可视化编辑器"
- **Phase 1 已实现**：`succession-editor/` 目录下完整的 web 编辑器

### Phase 1 实现内容

- ✅ Vite + React + TypeScript + @xyflow/react + Zustand 项目脚手架
- ✅ 完整类型定义（与 Java record 一一对应）
- ✅ Minecraft 1.21.1 完整群系列表（60+ 群系，含默认温湿度）
- ✅ Zustand store：节点/连线增删改、undo/redo、校验
- ✅ 三栏布局：左侧群系面板 + 中间图画布 + 右侧属性面板
- ✅ 自定义 BiomeNode 渲染（按温度着色、显示群系图标/数据）
- ✅ 自定义 SuccessionEdge 渲染（路径标签、选中高亮）
- ✅ 属性面板 — Path Identity 编辑器（path_id, priority）
- ✅ 属性面板 — Climate 编辑器（温度/湿度范围滑块+数值输入）
- ✅ 属性面板 — ChunkRules 编辑器（全部 8 个字段）
- ✅ 属性面板 — Plant Table 编辑器（6 列表格、展开行 SpawnRules 子表单）
- ✅ JSON 导出（兼容现有 schema v1）
- ✅ JSON 导入（解析现有文件 → 还原为图）
- ✅ 校验（pathId 唯一、数值范围、必填字段）+ 错误高亮
- ✅ 撤销/重做、键盘快捷键（Ctrl+Z/Y、Delete、Escape）
- ✅ 生产构建通过（`npm run build` 成功）

### 启动方式

```bash
cd succession-editor
npm run dev     # 开发服务器
npm run build   # 生产构建
```

## 建议的下一步

详见 `todolist.md` 底部「下一步计划」章节，剩余按优先级排列：

1. **C. 非玩家方块变更事件** — 水/岩浆/随机刻导致植被消失时同步清理追踪
2. **D. 区块边界混合** — 缓解群系切换的生硬边界
3. **E. 演替路径可视化编辑器** — 见 `docs/visual-succession-editor.md`
4. **F. GameTest / 可重复验证步骤**
