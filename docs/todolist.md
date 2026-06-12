# TODO List

以下待办基于当前代码实现状态整理。

## 近期完成 (2026-06-12)

- [x] P0-1 TreeGrowthSession NBT 持久化
- [x] P1-1 拆分 ModChunkEvents
- [x] P1-4 合并 Tree Profile 重复 (11 个独立类 → 2 个参数化 record)
- [x] P2-2 删除死代码 (VegetationCategory、ForestPlanter、旧 profile 类)
- [x] **植物衰老死亡系统** — 三个 adapter 死亡检测 + VegetationTracker 方块移除 + 客户端 DEAD 视觉阶段
- [x] **中心植物注册表 (PlantRegistry)** — PlantDefinition 从演替路径分离到 plant_definitions/，路径只存 plant_id + weight 引用

## P0：工程基础 ✅

- [x] 以 `Ecoflux` 为统一命名，同步修正 `mod_id`、包名和资源命名空间
- [x] 将语言资源迁移到 `assets/ecoflux/`
- [x] 新建 `EcofluxMod` 主入口类
- [x] 补齐基础语言文件（en_us / zh_cn）
- [x] 建立 `EcofluxConstants` 日志与常量类

## P1：区块演替数据骨架 ✅

- [x] 注册 `DataAttachment<SuccessionChunkData>`
- [x] 定义 `SuccessionChunkData`、`PlantQueueEntry`、`ActiveVegetationRecord`
- [x] 实现 NBT 序列化/反序列化
- [x] 区块初始化、加载和保存时机

## P2：数据驱动配置加载 ✅

- [x] 设计 `succession_paths` JSON 格式 (schema v1)
- [x] 实现 `SuccessionConfigLoader` + `SuccessionConfigRegistry`
- [x] 支持根据群系 + 温湿度条件匹配候选路径
- [x] 21 个演替路径 JSON 配置文件

## P3：植物管理闭环

- [x] 野生植物识别规则（VegetationTypeAdapter 系统）
- [x] 植物队列生成与权重抽取（PlantSpawner.ensureQueue / buildWeightedQueue）
- [x] 在 chunk tick 中尝试种植植物（trySpawnPlant）
- [x] 记录活跃植物（vegetationRecords，activePlants 已退役）
- [x] 监听植物死亡、玩家破坏和失效替换（ModPlayerEvents + pruneInvalidPlants）
- [x] 非玩家方块变更 → prune 快速轮询（10 tick 间隔）

## P4：积分与进度系统 ✅

- [x] 为不同植物定义 `pointValue`
- [x] 路径配置 `consumingValue`
- [x] 低频评估调度（evaluation_interval_days）
- [x] 进度增长、衰减和回退逻辑（SuccessionEvaluator）
- [x] 调试命令 `/ecoflux prototype step/describe/evaluate`

## P5：群系替换与边界处理

- [x] 封装群系替换服务（BiomeTransitionService）
- [x] 正向演替完成时切换到目标群系（applyTransition）
- [x] 负向回退时恢复 fallback 群系（applyRegression）
- [ ] 在区块边界加入轻量混合策略，缓解生硬切换
- [x] 演替完成后重置进度并刷新队列

## P6：同步、调试与兼容

- [x] 客户端视觉状态同步（VegetationVisualChunkSyncPayload）
- [x] 调试命令（/ecoflux prototype / auto / visual / lifecycle）
- [ ] Dynamic Trees 兼容
- [ ] GameTest 或可重复验证步骤

---

## 下一步计划

### A. 负向回退 → Fallback 群系切换 ✅

已完成。`SuccessionEvaluator.shouldRegress()` 在 progress ≤ -1.0 时触发，`BiomeTransitionService.applyRegression()` 切换到 fallbackBiome 并重置状态。

### B. activePlants 退役，统一到 vegetationRecords ✅

已完成。移除 `activePlants`、`ActivePlantRecord`、`getTotalPlantPoints()` 等，所有追踪统一到 `vegetationRecords`。

### C. 非玩家方块变更事件接入 VegetationTracker ✅

已完成。pruneInvalidPlants 逐位置检查 BlockState 是否匹配 adapter，prune 以独立 10-tick 间隔运行。NeoForge 1.21.1 没有覆盖非玩家方块移除的统一事件，快速 prune 轮询是最实用的方案。

### D. 区块边界混合

- [ ] 在 `BiomeTransitionService` 中实现 border-blend
- [ ] 过渡期间在区块边缘逐步混合目标群系

### E. 演替路径可视化编辑器

见 `docs/succession-editor.md`。Phase 1 完成，Phase 2 条件分支节点 + 植物快速选择完成（2026-06-09）。

### G. Tree Profile 重构 ✅

见 `docs/tree-profile-refactor.md`。2026-06-12 完成。

- [x] 创建 `MorphologyTreeProfile`（统一 9 个 morphology 模板）
- [x] 创建 `MushroomGrowthProfile`（统一 2 个蘑菇类）
- [x] 删除 12 个旧 profile 文件
- [x] 移动 `MorphologyPresets` 到 `morphology/` 包
- [x] 更新 `TreeGrowthHandler` 注册逻辑
- [x] 更新 CLAUDE.md 和 docs

### H. GameTest / 可重复验证步骤

- [ ] 编写 GameTest 验证完整演替闭环
- [ ] 或编写命令脚本 + 文档化预期结果

---

## JSON 配置覆盖分析（2026-06-12）

### 主代码未接入的字段（2 个）

- [ ] `evaluation_interval_days.max` — `ChunkRules.resolvedEvaluationIntervalTicks()` 只读 `min`，`max` 从未使用
- [ ] `spawn_rules.placement` — 解析存储了但 `findSpawnPos()` 未完全使用

### 编辑器不可编辑的字段（2 个）

- [ ] `source_biomes` 不支持多选 — 编辑器图拓扑决定了只能单元素数组，Java 支持多源群系
- [ ] `fallback_biome` 只读 — 显示但不可编辑，自动设为 source biome

### 编辑器默认值与 Java 不一致（2 个）

- [ ] `processing_interval_ticks`：Java 默认 20，编辑器默认 100
- [ ] `positive_progress_step`：Java 默认 0.5，编辑器默认 0.25

---

## Tree 包深层优化（延后，当前不改）

这些问题已在 `docs/tree-profile-refactor.md` 中记录，属于 profile 重构之后的更深层优化。

### 硬编码魔法数字

- [ ] **CanopyEnvelope.java** — `evaluate()` 中各 canopy 类型的密度衰减公式包含硬编码常数（如 `0.28`, `1.8`, `0.6`），应该参数化到 `MorphologyParams`
- [ ] **LeafFiller.java** — `ChebyshevDistance` 硬编码权重，`maxLeavesPerStage = 50` 硬编码，`decayFactor` 硬编码
- [ ] **SkeletonGenerator.java** — 分支角度、长度衰减率、噪声幅度等硬编码值未暴露为参数
- [ ] **CanopyEnvelope.CanopyType** — 每种类型的密度函数在代码中内联，应允许自定义密度函数

### 参数膨胀

- [ ] **MorphologyParams** — 记录有 23 个构造参数，难以阅读和维护。考虑拆分为子记录（TrunkParams, BranchParams, CanopyParams）或 builder 模式
- [ ] **TreeMorphology.growStage()** — 方法有 10 个参数（level, skel, morphologyParams, plan, stage, seed, random, logBlock, leavesBlock, placedLogs, placedLeaves）。考虑创建 GrowContext 记录封装
- [ ] **TreeMorphology.fillLeaves()** — 6 个参数，其中多个来自 MorphologyParams 的单独字段。可以通过直接传递 MorphologyParams 简化

### 蘑菇生长系统统一

- [ ] 合并后的 `MushroomGrowthProfile` 仍使用手动 stem+cap 放置，长远可考虑让蘑菇也走 morphology 管道（用细 trunk + 大 canopy radius 模拟蘑菇形状）

### TreeGrowthHandler 耦合

- [ ] `TreeGrowthHandler.tickAll()` 中有 morphology / legacy 两套分支代码，mushroom profile 统一后只会减少不会消除。理想状态是 profile 接口自足，handler 不需要 if-else 分发
- [ ] `TreeGrowthHandler` 的 chunksWithSessions 跟踪与 `SuccessionChunkData` 内部的 sessions map 存在重复状态，可简化
