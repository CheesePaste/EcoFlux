# TODO List

以下待办基于 `README.md` 和当前分支状态整理，默认不参考其他 Git 分支实现。

## P0：先把工程从模板推进到可开发状态

- [x] 以 `Ecoflux` 为统一命名，同步修正 `mod_id`、`mod_name`、包名和资源命名空间。
- [x] 将语言资源迁移到 `assets/ecoflux/`。
- [x] 新建 `EcofluxMod` 主入口类，替换模板 `neoforge.mods.toml` 描述。
- [x] 补齐基础语言文件，至少提供模组名称和调试文本。
- [x] 建立基础日志分类与常量类，避免后续散落硬编码字符串。
- [ ] 新建 `data/ecoflux/` 下的数据驱动资源骨架。

验收标准：

- 能正常 `runClient` 启动模组。
- 模组列表里显示的名称、版本、命名空间一致。
- 工程里不再只有模板文件。

## P1：区块演替数据骨架

- [x] 注册 `Data Attachments`，为区块附件预留 `SuccessionChunkData`。
- [x] 定义 `SuccessionChunkData`、`PlantQueueEntry`、`ActivePlantRecord`。
- [x] 设计并实现基础序列化/反序列化。
- [x] 约定区块初始化、加载和保存时机。
- [x] 增加最小调试输出，能在日志中看到区块数据创建与读取。

验收标准：

- 区块重新加载后附件数据仍能保留。
- 附件内容至少包含进度、目标群系、植物队列和活跃植物映射的基础字段。

## P2：数据驱动配置加载

- [x] 设计 `succession_paths` JSON 格式。
- [x] 实现配置加载、校验和缓存。
- [x] 支持根据群系 + 温湿度条件匹配候选路径。
- [x] 为路径配置生成至少 1 份示例数据。
- [x] 处理缺失配置、非法配置和重复路径的日志提示。

验收标准：

- 游戏启动或数据重载后可以正确加载 JSON。
- 给定群系与气候参数时能稳定解析目标路径。

## P3：植物管理闭环

- [ ] 实现野生植物识别规则，明确排除农作物。
- [ ] 实现植物队列生成与权重抽取。
- [ ] 在随机刻或调度逻辑中尝试种植植物。
- [ ] 记录活跃植物并维护 `currentPlantCount`。
- [ ] 监听植物死亡、玩家破坏和失效替换，及时移除记录。

验收标准：

- 区块里可以自动生成受控植物。
- 植物消失后对应记录会同步清理。
- `activePlants` 与世界实际状态基本一致。

## P4：积分与进度系统

- [x] 为不同植物定义 `pointValue`。
- [x] 根据路径配置提供 `consumingValue`。
- [x] 实现低频评估调度（3~7 游戏日）。
- [x] 实现进度增长、衰减和回退逻辑。
- [x] 设计调试命令或日志，便于观察区块进度变化。

验收标准：

- `totalPlantPoints > consumingValue` 时进度能增加。
- 反之进度能下降。
- 达到阈值前后行为可通过日志或命令验证。

## P5：群系替换与边界处理

- [ ] 封装群系替换服务，验证 1.21.1 的可行 API。
- [ ] 正向演替完成时切换到目标群系。
- [ ] 负向回退时恢复上一群系或回退目标。
- [ ] 在区块边界加入轻量混合策略，缓解生硬切换。
- [ ] 演替完成后重置进度并刷新队列。

验收标准：

- 达到阈值时区块群系会真实改变。
- 演替完成后数据状态重置合理，不会卡死在旧阶段。

## P6：同步、调试与兼容

- [ ] 只同步客户端真正需要展示的数据。
- [ ] 评估是否需要调试命令、HUD 或开发者日志开关。
- [ ] 实现 `Dynamic Trees` 探测与兼容入口。
- [ ] 在未安装 `Dynamic Trees` 时平稳降级。
- [ ] 补充 GameTest 或至少一组可重复的人工验证步骤。

验收标准：

- 无兼容模组时主流程正常运行。
- 有兼容模组时不会破坏基础流程。

## 当前最先建议执行的 5 项

1. [x] 设计第一版 `succession_paths` JSON。
2. [x] 把 `SuccessionChunkData` 接入真实区块初始化流程。
3. [x] 增加最小调试输出或命令，便于观察附件状态。
4. [x] 做一个“单植物 + 单路径 + 单区块进度”的最小可运行原型。
5. [x] 再补 `data/ecoflux/` 示例数据与加载器。

## 2026-06-07 更新：服务层提取已完成

以下工作已在 `another-try` 分支完成：

- [x] 从 `PrototypeChunkController` 提取正式服务层：`succession/SuccessionService`、`SuccessionTargetResolver`、`SuccessionEvaluator`、`BiomeTransitionService`
- [x] 提取通用工具：`world/ChunkSamplingHelper`、`plant/PlantSpawner`
- [x] `isPrototypeChunk` 泛化为 `hasActivePath`，自动 tick 对所有配置路径生效
- [x] `PrototypeChunkController` 瘦身为 ~175 行（仅保留加速演示模式）

当前最优先的下一步：
- [x] 把 `vegetationRecords` 积分接入区块进度结算（目前仅用衰老门控）
- [x] 把玩家放置/破坏事件接入 `VegetationTracker`
- [x] 多植物队列与权重抽取（原型仅测了单植物 dandelion）

---

## 下一步计划（2026-06-07 更新，排除 Dynamic Trees 兼容）

### A. 负向回退 → Fallback 群系切换 ✅

- [x] `BiomeTransitionService` 支持反向切换：读取 `previousBiome` 或 `fallbackBiome`，执行群系回退
- [x] `SuccessionEvaluator` 在进度 ≤ -1.0 时触发反向过渡
- [x] 回退完成后重置进度和队列
- [x] 用 `/ecoflux prototype evaluate` 循环可验证：贡献积分不足时应能观测到进度持续下降，最终触发群系回退

### B. activePlants 退役，统一到 vegetationRecords ✅

- [x] `trySpawnPlant` 不再写入 `activePlants`，仅依赖 `VegetationTracker.trackAt()`
- [x] `pruneInvalidPlants` 改为基于 `vegetationRecords` 的过期/消失检查
- [x] 移除 `SuccessionChunkData` 中的 `activePlants`、`ActivePlantRecord`、`getTotalPlantPoints()` 等遗留字段和方法
- [x] 确认所有引用 `activePlants` 的地方已迁移（`PrototypeChunkController`、`ChunkSamplingHelper`、`BiomeTransitionService`、NBT 序列化等）
- [x] 删除 `ActivePlantRecord.java`

### C. 非玩家方块变更事件接入 VegetationTracker

当前 `ModPlayerEvents` 只处理玩家放置/破坏。以下自然事件会导致植被记录与实际世界不一致：
- 水流/岩浆冲走方块
- 原版随机刻导致的植物生长/死亡
- 其他 mod 或原版机制替换方块（如村民踩踏耕地）

- [ ] 监听 `NeighborBlockEvent` 或等效事件：当追踪位置的方块被替换为非匹配方块时，自动移除追踪
- [ ] 或者：在 `prune` 时逐位置检查 `BlockState` 是否仍然匹配 adapter（当前只在 `ActivePlantRecord` 层面检查，`vegetationRecords` 的 prune 不完整）
- [ ] `VegetationTracker.observeChunk()` 中已经检查 `observation.present()` 并移除消失的植被——确认这是否覆盖了所有情况

### D. 区块边界混合

当前群系替换是整块直接切换（`fillBiomesFromNoise` 写入统一 biome），相邻不同群系的区块之间边界非常生硬。

- [ ] 在 `BiomeTransitionService` 中实现 border-blend：使用邻近区块的 biome 作为插值源
- [ ] 过渡期间（progress 0.8~1.0）在区块边缘逐步混合目标群系
- [ ] 评估 1.21.1 中 `fillBiomesFromNoise` 的参数是否支持逐列 biome 设置

### E. 演替路径可视化编辑器

见 `docs/visual-succession-editor.md` 完整设计文档。

原计划是手动补充更多演替路径 JSON（目前只有 3 条路径，覆盖源群系很少）。重新评估后，改为构建一个**可视化节点-连线编辑器**，从根本上解决路径配置的效率问题。

**Phase 1（当前）**：Web 编辑器（React + ReactFlow），支持群系节点 + 演替连线绘制，导出标准 JSON
- [ ] 项目脚手架（Vite + React + TS + ReactFlow）
- [ ] BiomeNode 创建与渲染
- [ ] SuccessionEdge 连线交互
- [ ] 属性编辑面板（chunkRules / plants / climate）
- [ ] JSON 导入/导出（兼容现有 schema v1）
- [ ] 撤销/重做、校验

**Phase 2**：条件分支节点（温度/湿度判定 → 不同目标群系）
**Phase 3**：优先级路由、植物池复用、全局 DAG 预览
**Phase 4**：游戏内 HTTP 集成（可选，推送配置热加载）

此外，短期手动补充的路径仍可直接编辑 JSON 文件：
- [ ] 补充反向路径：`forest_to_plains`（森林退化回平原）
- [ ] 补充更多源群系路径：`taiga_to_forest`、`swamp_to_forest` 等

### F. GameTest / 可重复验证步骤

当前只能手动进游戏敲命令测试。需要一套可重复的验证流程。

- [ ] 编写 GameTest：验证 `plains_to_forest` 的完整闭环（init → spawn → observe → evaluate → transition）
- [ ] 或编写一套命令脚本（`/ecoflux prototype ...` 的固定序列），文档化预期结果
- [ ] 验证点至少覆盖：队列多样性、积分正确累加、进度正/负向变化、群系切换前后状态
