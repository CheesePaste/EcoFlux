# TODO List

以下待办基于当前代码实现状态整理。

## 近期完成 (2026-06-11)

- [x] P0-1 TreeGrowthSession NBT 持久化
- [x] P1-1 拆分 ModChunkEvents
- [x] P1-2 提取 ForestPlanter
- [x] P1-4 合并 Tree Profile 重复
- [x] P2-2 删除死代码
- [x] P3-2 MorphologyParams → MorphologyPresets
- [x] P3-3 CanopyConfig.fromMorphology()
- [x] **植物衰老死亡系统** — 三个 adapter 死亡检测 + VegetationTracker 方块移除 + 客户端 DEAD 视觉阶段

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

### F. GameTest / 可重复验证步骤

- [ ] 编写 GameTest 验证完整演替闭环
- [ ] 或编写命令脚本 + 文档化预期结果

---

## JSON 配置覆盖分析（2026-06-07）

### 主代码未接入的字段（2 个）

- [ ] `evaluation_interval_days.max` — `ChunkRules.resolvedEvaluationIntervalTicks()` 只读 `min`，`max` 从未使用
- [ ] `spawn_rules.placement` — 解析存储了但 `findSpawnPos()` 完全忽略

### 半接入（1 个）

- [ ] `plants[].category` — 存入 `ActiveVegetationRecord` + 日志输出，但不影响任何游戏逻辑

### 编辑器不可编辑的字段（2 个）

- [ ] `source_biomes` 不支持多选 — 编辑器图拓扑决定了只能单元素数组，Java 支持多源群系
- [ ] `fallback_biome` 只读 — 显示但不可编辑，自动设为 source biome

### 编辑器默认值与 Java 不一致（2 个）

- [ ] `processing_interval_ticks`：Java 默认 20，编辑器默认 100
- [ ] `positive_progress_step`：Java 默认 0.5，编辑器默认 0.25
