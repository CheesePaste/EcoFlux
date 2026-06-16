# EcoFlux — NeoForge 1.21.1 生态演替模组

每个 16×16 chunk 中，植物生长/老化/死亡 → 集体"点数"决定群系演进（草原→森林）或退化。数据驱动（JSON 配置），空间定殖树生成，适配器模式植物识别。

## Core Constraints

1. **不确定就验证，不猜测。**
   - 检查 Minecraft 类层次（`instanceof` 链）再添加条件。
   - 用 `minecraft-source` skill 或 grep 现有用法验证 API 可用性。
   - 如果已有检查覆盖了目标，不要加另一个。
2. **只碰任务要求的代码。** 不做顺手重构，不清理无关代码。
3. **最简方案优先。** 不编码"未来需求"，不添加不必要的抽象。
   - 已有 enum/category/class 能用的就不要新建。
   - 已有更宽泛检查覆盖的情况（如 `instanceof SaplingBlock` 已捕获 `MangrovePropaguleBlock`），不要加冗余条件。
4. **必须编译通过。** `./gradlew build` 失败 = 工作未完成。
5. **理解目的再修改。** 返回 `0xFFFFFF` 的颜色处理器可能看起来是 bug——先搞清楚处理器为什么存在、要达成什么，再修边界情况。
6. **读文档再编码。** 修改某子系统前，先读 `docs/` 中对应文档。文档告诉你 WHY，代码只告诉你 WHAT。
7. **变更后立即更新 docs/。** 过时文档比没有更糟。CLAUDE.md严格控制行数，非关键内容不加入其中。

## Build Commands

```bash
./gradlew compileJava     # 仅编译
./gradlew build            # 完整构建（编译 + jar）
./gradlew runClient        # 启动客户端
./gradlew runServer        # 启动服务端
./gradlew runGameTestServer  # 运行 GameTest
./gradlew runData          # 运行数据生成
```

Windows 用 `.\gradlew.bat`。

## Architecture Overview

```
config/         JSON 配置加载 + 注册表（路径/群系规则/植物定义）
attachment/     SuccessionChunkData（每 chunk 的 NBT 持久化状态）
succession/     演替编排（Service → Evaluator → BiomeTransition）
plant/          植物生命周期（Adapter 模式 → Tracker 单例 → Spawner）
plant/tree/     空间定殖树生长（Handler → Session → SC Generator）
worldgen/       世界生成（BiomeModifier 取消原版树 + EcofluxTreeFeature 放置 SC 树）
worldgen/biomemodifier/  CancelVanillaTreesBiomeModifier + AddEcofluxTreesBiomeModifier
mixin/          SaplingBlockMixin（拦截树苗生长）
client/visual/  客户端渲染（缩放/着色/动画）
compat/dynamictrees/  DT 兼容（Interceptor + EventHandler + adapter）
network/        网络同步（VegetationVisualChunkSyncPayload）
init/           入口：ModCommands, ModChunkEvents, ModAttachments, ModReloadListeners
```

## Service Dependencies
```
PlantRegistry (无依赖)
  → SaplingAdapter, SimplePlantAdapter, TreeStructureAdapter
    → VegetationTracker(adapters)
      → TreeGrowthHandler

DTTreeAdapter 通过 tracker.addAdapter() 动态注入，不参与静态初始化。
```

## Doc Reading Rules

**修改代码前必须读对应文档。** 这些规则优先级高于架构总览。

| 场景 | 必读文档 |
|------|---------|
| 修改演替逻辑（评估/转换/初始化） | `@docs/succession-system.md` |
| 修改 JSON 配置 schema / Loader / Registry | `@docs/config-system.md` |
| 修改植物识别/追踪/生成/适配器 | `@docs/plant-lifecycle-system.md` |
| 修改树生长（算法/树种/session） | `@docs/tree-growth-system.md` |
| 修改世界生成（BiomeModifier/Feature/Scanner） | `@docs/architecture.md`（worldgen 章节） |
| 修改客户端渲染/动画 | `@docs/client-visual-system.md` |
| 修改 NBT 序列化/网络包 | `@docs/networking-and-data.md` |
| 修改植物死亡/腐烂 | `@docs/plant-death-system.md` |
| 修改命令 | `@docs/architecture.md`（init 章节） |

## Key Design Decisions

1. **数据驱动**：演替路径（`succession_paths/`）和群系植物规则（`biome_rules/`）分离。植物定义在 `plant_definitions/`。全局参数在 `EcofluxServerConfig`。
2. **Adapter 模式**：`VegetationTypeAdapter` 接口识别植物，不硬编码类型。4 个实现：SimplePlant / Sapling / TreeStructure / DTTree。
3. **统一追踪**：`vegetationRecords` (`Map<BlockPos, ActiveVegetationRecord>`) 是唯一追踪数据源。`activePlants` 已退役。
4. **Decoraction→ChunkLoad 桥接**：`EcofluxTreeFeature` 在装饰阶段放置 SC 树 → `PENDING_TREES` → `WorldGenVegetationScanner` 在 chunk 加载时消费。消除旧 BFS 树检测。
5. **渐进生长**：`SaplingBlockMixin` 拦截原版瞬间生长 → `TreeGrowthHandler` 管理多阶段生长。空间定殖算法（Dynamic Trees 改编，MIT）。
6. **BiomeModifier 替换**：Phase.REMOVE 取消所有 mod 的树 feature（递归展开嵌套 config） → Phase.ADD 添加 Ecoflux 树。

## Conventions

- 命名空间：`ecoflux`；`EcofluxConstants.id("name")` → `ResourceLocation("ecoflux", "name")`
- Java record 优先用于数据对象
- 日志通过 `EcofluxConstants.LOGGER`（SLF4J）
- 无 `src/test/` 目录；运行时调试工具在 `src/main/java` 的 `util/sample/` 包下

## Memory

项目记忆存储在 `memory/`（通过 Claude Code memory 系统），包含架构问题记录等技术债务信息。

## Highlights

- **读源码，读文档，不猜测。**