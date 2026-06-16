# 总体架构

> 最后更新: 2026-06-16

## 相关文档

修改架构前必读：[config-system.md](config-system.md) · [succession-system.md](succession-system.md) · [plant-lifecycle-system.md](plant-lifecycle-system.md) · [tree-growth-system.md](tree-growth-system.md) · [client-visual-system.md](client-visual-system.md) · [networking-and-data.md](networking-and-data.md) · [plant-death-system.md](plant-death-system.md) · [refactoring-plan.md](refactoring-plan.md)

## 包结构

```
com.cp.ecoflux/
├── EcofluxMod.java              # @Mod 入口，组装所有子系统
├── EcofluxConstants.java        # MOD_ID, LOGGER, ResourceLocation 工厂
│
├── config/                      # 配置系统
│   ├── EcofluxServerConfig          # 服务端 TOML 配置
│   ├── EcofluxBlockTags             # 方块标签常量
│   ├── SuccessionSpeedConfig        # 全局速度倍率
│   ├── VisualLifecycleClientConfig  # 客户端 TOML 配置
│   │
│   ├── biome/                   # 群系植物规则
│   │   ├── BiomeRules               # Record: biomeId, maxPlantCount, consuming, plants
│   │   ├── BiomeRulesRegistry       # 单例：按 biomeId 缓存查找
│   │   └── BiomeRulesLoader         # SimpleJsonResourceReloadListener，监听 biome_rules/
│   │
│   ├── plant/                   # 植物注册表
│   │   ├── PlantDefinition          # Record: plant_id, pointValue, maxAgeTicks, spawnRules
│   │   ├── PlantRegistry            # 单例：中心植物注册表 (ConcurrentHashMap)
│   │   ├── PlantRegistryLoader      # SimpleJsonResourceReloadListener，监听 plant_definitions/
│   │   ├── PathPlantEntry           # Record: plant_id + weight 引用
│   │   └── PlantSpawnRules          # Record: requireSky, maxLocalDensity, allowedBaseBlocks
│   │
│   ├── succession/              # 演替路径配置
│   │   ├── SuccessionConfigLoader   # SimpleJsonResourceReloadListener，加载 succession_paths/
│   │   ├── SuccessionConfigRegistry # 线程安全缓存，findBestMatch(biome, temp, downfall)
│   │   └── SuccessionPathDefinition # Record: pathId, priority, source/target/fallback, step sizes
│   │
│   └── math/                    # 共享数学类型
│       ├── ClimateCondition         # Record: temperature/downfall 范围
│       ├── FloatRange               # Record: min/max float
│       └── IntRange                 # Record: min/max int
│
├── attachment/                  # 数据附件 (NBT 持久化)
│   ├── SuccessionChunkData          # 核心 per-chunk 状态
│   ├── ActiveVegetationRecord       # 活跃植被追踪记录
│   └── PlantQueueEntry              # 预生成植物条目
│
├── succession/                  # 演替核心系统
│   ├── SuccessionService            # 主编排入口 (init/step/tick/prune/spawn/evaluate)
│   ├── SuccessionTargetResolver     # 初始化：采样 biome/climate → 匹配 config → 填充数据
│   ├── SuccessionEvaluator          # 进度评估：aging gate + 点数 vs consuming
│   └── BiomeTransitionService       # 群系替换：fillBiomesFromNoise + 发包 + 软重置
│
├── plant/                       # 植物生命周期系统
│   ├── VegetationTypeAdapter        # 核心接口：matches/captureBirth/observe/visualState
│   ├── VegetationTracker            # 单例：追踪/观察/同步所有植被
│   ├── PlantSpawner                 # 生成/修剪：trySpawnPlant/pruneInvalid/ensureQueue
│   ├── VegetationObservation        # Record: 观察结果
│   ├── VegetationTransformation     # Record: 转换描述 (树苗→树)
│   ├── VegetationVisualState        # Record: 视觉快照
│   ├── VegetationLifecycleStage     # Enum: BORN→JUVENILE→GROWING→MATURE→AGING→DEAD→TRANSFORMED
│   ├── TreeStructure                # Record: 树的多块结构数据
│   ├── SimplePlantAdapter           # 小型植物适配器 (花/草/蕨/蘑菇/枯灌木/高草丛)
│   ├── SaplingAdapter               # 树苗适配器 (→ TreeGrowthHandler)
│   └── TreeStructureAdapter         # 成熟树适配器
│   │
│   └── tree/                    # 树木生长子系统
│       ├── TreeGrowthHandler        # 单例：管理生长会话 (NBT 持久化)，10 个 profile
│       ├── TreeGrowthSession        # 每棵树状态 (NBT + transient SC 数据)
│       ├── TreeGrowthProfile        # 接口：树种标识、方块类型、is2x2、stage 方法
│       ├── TreeShapeUtils           # 共享工具 (位置噪声/2x2/原木放置)
│       │
│       ├── profiles/            # 树种配置
│       │   └── MushroomGrowthProfile    # 参数化 record：2 种蘑菇 (FLAT/DOMED)
│       │
│       └── spacecolonization/   # 空间定殖系统
│           ├── SpaceColonizationParams    # 12 字段参数 record + 9 个树种预设
│           ├── SpaceColonizationGenerator # 混合算法：DT 风格原木生长 + 叶片簇 + 包络体
│           └── SpaceColonizationProfile   # Record 实现 TreeGrowthProfile
│
├── worldgen/                    # 世界生成集成
│   ├── WorldGenVegetationScanner    # Chunk 加载时扫描原版植被 → VegetationTracker
│   ├── EcofluxFeatures             # DeferredRegister: Feature/ConfiguredFeature/PlacedFeature
│   ├── biomemodifier/
│   │   ├── EcofluxBiomeModifiers    # DeferredRegister<BiomeModifier> 注册
│   │   ├── CancelVanillaTreesBiomeModifier  # Phase.REMOVE：取消所有原版树 feature
│   │   └── AddEcofluxTreesBiomeModifier     # Phase.ADD：添加 Ecoflux SC 树 feature
│   └── feature/
│       └── EcofluxTreeFeature       # 自定义树 feature，世界生成时放置 SC 树
│
├── world/                       # 世界工具
│   └── ChunkSamplingHelper      # 静态工具：采样 biome/climate/surface/findSpawnPos
│
├── client/                      # 客户端
│   └── visual/                  # 视觉生命周期渲染
│       ├── VisualLifecycleClientRuntime  # 客户端单例：接收服务端视觉状态
│       ├── VisualLifecycleWorldRenderer  # 渲染视觉叠加 (缩放/老化着色)
│       ├── VisualLifecycleAdapter        # 接口
│       ├── VisualLifecycleRegistry       # 注册表：find(BlockState) → adapter
│       ├── ModClientVisualLifecycle      # 客户端初始化
│       ├── VisualLifecycleStage          # 视觉阶段枚举
│       ├── VisualLifecycleProfile        # 视觉配置
│       ├── VisualLifecycleInstance       # 活跃实例
│       ├── VisualLifecycleRenderState    # 渲染状态
│       ├── VisualLifecycleExternalState  # 外部状态输出
│       ├── VisualLifecycleTrackingSource # 追踪来源
│       └── adapters: Flower/Grass/Sapling/Generic
│
├── network/                     # 网络同步
│   ├── ModNetworking                    # Payload 注册
│   ├── VegetationVisualChunkSyncPayload # 植被视觉状态 → 客户端
│   └── VegetationVisualSyncEntry        # 单植物同步条目
│
├── init/                        # 初始化与事件
│   ├── ModAttachments           # DataAttachment<SuccessionChunkData> 注册
│   ├── ModChunkEvents           # Chunk load/unload/tick 事件 + WorldGenVegetationScanner
│   ├── ModPlayerEvents          # 玩家放置/破坏 → VegetationTracker
│   ├── ModCommands              # /ecoflux debug 命令
│   └── ModReloadListeners       # JSON 热重载 (/reload)
│
├── mixin/                       # Mixin 层
│   ├── SaplingBlockMixin        # 拦截树苗瞬间生长 → TreeGrowthHandler
│   ├── MushroomBlockMixin       # 拦截蘑菇瞬间生长
│   ├── client/
│   │   ├── BlockRenderDispatcherMixin  # 抑制被追踪非满 scale 方块的 vanilla 渲染
│   │   └── SodiumBlockRendererMixin    # 钠模组渲染兼容
│   ├── worldgen/
│   │   ├── ChunkGeneratorMixin         # 使 biomeSource 可变 (采样世界)
│   │   └── ApplyBiomeDecorationMixin   # 装饰阶段集成
├── test/                        # 测试 / 工具
│   └── sample/
│       ├── BiomePlantSampler         # 原版世界生成植物分布采样 + BiomeRules JSON 生成
│       ├── SamplingBiomeSource       # 可切换单群系的 BiomeSource (采样世界)
│       ├── SamplingBiomeSources      # DeferredRegister<BiomeSource>
│       └── ChunkGeneratorAccessor    # Duck 接口：类型安全 biomeSource 交换
│
└── util/
    └── TickProfiler              # Tick 级性能分析工具
```

## 分层架构

```
┌────────────────────────────────────────────┐
│              配置层 (config/)               │
│   JSON 定义 → Registry 缓存查找             │
│   决定：什么群系能演替到什么群系              │
│   什么植物能在什么群系生成                    │
└────────────────────┬───────────────────────┘
                     │ 提供 PathDefinition + BiomeRules
                     ▼
┌────────────────────────────────────────────┐
│            演替核心层 (succession/)          │
│   Service → Evaluator → BiomeTransition    │
│   编排：初始化→生成→评估→转换                │
└──────┬──────────────────┬──────────────────┘
       │                  │
       ▼                  ▼
┌──────────────┐  ┌──────────────────┐
│  植物生命周期  │  │   世界工具        │
│  (plant/)    │  │   (world/)       │
│  追踪·观察   │  │   采样·定位       │
│  生成·修剪   │  │                  │
└──────┬───────┘  └──────────────────┘
       │
       ▼
┌────────────────────────────────────────────┐
│          树木生长 (plant/tree/)              │
│   Handler → Session → SpaceColonization    │
└────────────────────────────────────────────┘

世界生成横向层：
┌────────────────────────────────────────────┐
│        世界生成集成 (worldgen/)              │
│   BiomeModifier 取消原版树 + 添加 SC 树     │
│   Chunk 加载时扫描原版植被 → Tracker        │
└────────────────────────────────────────────┘

横向支撑层：
┌────────────┐  ┌────────────┐  ┌────────────┐
│ attachment │  │  network   │  │   mixin    │
│ 数据持久化  │  │  网络同步   │  │  字节码钩子 │
└────────────┘  └────────────┘  └────────────┘
```

## 核心数据流

```
世界生成阶段 (WorldGen)
  │
  ├─→ CancelVanillaTreesBiomeModifier (REMOVE phase)
  │     └─ 移除所有 TreeConfiguration/RandomFeatureConfiguration
  │
  └─→ AddEcofluxTreesBiomeModifier (ADD phase)
        └─→ EcofluxTreeFeature.place()
              ├─ 读取 biome → BiomeRules → 树种
              ├─ SpaceColonizationGenerator.generateFull() → FullTreePlan
              ├─ WorldGenLevel.setBlock() 放置方块
              └─ 存储到 PENDING_TREES (等待 chunk load)

Chunk Load (ModChunkEvents)
  │
  ├─→ SuccessionService.initializeChunk()
  │     ├─→ ChunkSamplingHelper.sampleChunkCenterBiome()
  │     ├─→ ChunkSamplingHelper.sampleChunkClimate()
  │     ├─→ SuccessionConfigRegistry.findBestMatch() → PathDefinition
  │     ├─→ BiomeRulesRegistry.getRules(biomeId) → BiomeRules
  │     ├─→ SuccessionTargetResolver.populateChunkData()
  │     └─→ PlantSpawner.ensureQueue() + trySpawnPlant()
  │
  ├─→ WorldGenVegetationScanner.scanChunk()
  │     ├─ Phase 1: 处理 PENDING_TREES (EcofluxTreeFeature 放置的 SC 树)
  │     ├─ Phase 1b: BFS 检测巨型蘑菇
  │     ├─ Phase 2: 扫描简单植物 (花草/蕨/蘑菇等)
  │     ├─ Phase 3: 密度上限裁剪 (去重过度代表的植物类型)
  │     └─ 随机化植物出生时间 (±20% 寿命变化分散死亡事件)
  │
  ▼
Chunk Tick (按 prune_interval_ticks 间隔，默认 120 tick)
  │
  ├─→ SuccessionService.processChunkTick()
  │     ├─→ VegetationTracker.observeChunk() (按 observe_interval_ticks 间隔)
  │     ├─→ SuccessionEvaluator.evaluateProgress() (按 evaluation_interval_ticks 间隔)
  │     │     ├─ hasAgingVegetation()? → 比较 vegetationPoints vs consuming
  │     │     └─ shouldRegress()? → progress <= -1.0
  │     ├─→ progress >= 1.0 → BiomeTransitionService.applyTransition()
  │     │     └─ fillBiomesFromNoise + ClientboundChunksBiomesPacket + 软重置
  │     ├─→ progress <= -1.0 → BiomeTransitionService.applyRegression()
  │     ├─→ PlantSpawner.pruneInvalidPlants()
  │     └─→ PlantSpawner.trySpawnPlant() (按 spawn_interval 间隔)
  │
  ├─→ TreeGrowthHandler.tickAll() (每 20 tick)
  │     └─→ profile.growStage() → 从 session 取阶段位置放置方块
  │
  └─→ VegetationTracker.syncChunkToTracking() → 网络发包

## 关键设计决策

### 1. Data-driven 配置
演替路径、群系规则、植物定义均为 JSON 文件，通过 `SimpleJsonResourceReloadListener` 加载，支持 `/reload` 热重载。无需修改代码即可添加新路径或群系。

### 2. DataAttachment 持久化
每个 chunk 的状态通过 `DataAttachment<SuccessionChunkData>` 附加，完整 NBT 序列化。Chunk 卸载后重新加载时状态自动恢复。

### 3. Adapter 模式植物识别
植物类型通过 `VegetationTypeAdapter` 接口识别，而非硬编码。每个 adapter 实现 `matches(BlockState)` 判断是否匹配，`captureBirth()` 记录出生状态，`observe()` 评估生命周期阶段。

### 4. 统一 vegetationRecords
所有植物追踪统一到 `vegetationRecords` (`Map<BlockPos, ActiveVegetationRecord>`)，不再有单独的 `activePlants` 系统。

### 5. 中心植物注册表 (PlantRegistry)
植物定义 (`PlantDefinition`) 与演替路径分离：完整定义集中在 `data/ecoflux/plant_definitions/*.json`。植物引用仅通过群系规则 (`BiomeRules`) 中的 `PathPlantEntry(plantId, weight)` 进行；演替路径 (`SuccessionPathDefinition`) 不再携带植物列表（2026-06-14 重构后移除）。

### 6. 空间定殖驱动树木生长
树生长使用混合算法：Dynamic Trees 风格离散方向 probMap 原木生长 + 末端叶片簇 + 包络体密度衰减。支持 1x1 和 2x2 树干，9 种树种 + 2 种蘑菇。每个树种有独立的 `SpaceColonizationParams`，通过位置确定性种子保证视觉一致性。

### 7. BiomeModifier 树替换
原版树 feature 在 `Phase.REMOVE` 阶段通过 `CancelVanillaTreesBiomeModifier` 取消（递归展开所有嵌套 feature config），自定义 SC 树 feature 在 `Phase.ADD` 阶段通过 `AddEcofluxTreesBiomeModifier` 添加。`EcofluxTreeFeature` 在装饰阶段放置 SC 树，将结构存入 `PENDING_TREES`，由 `WorldGenVegetationScanner` 在 chunk 加载时消费。

### 8. 渐进生长替代瞬间生长
`SaplingBlockMixin` 和 `MushroomBlockMixin` 拦截原版瞬间生长，转由 `TreeGrowthHandler` 管理多阶段渐进生长。每个生长阶段（`ticksPerStage`）放置一部分方块，整个生长过程持续数十分钟。

## 启动流程

```
EcofluxMod 构造器
  ├─ ModAttachments.register()      — 注册 SUCCESSION_CHUNK_DATA
  ├─ ModNetworking.register()       — 注册网络 Payload
  ├─ ModChunkEvents.register()      — 注册 chunk load/unload/tick 处理器
  ├─ ModPlayerEvents.register()     — 注册玩家放置/破坏处理器
  ├─ ModCommands.register()         — 注册 /ecoflux 命令
  ├─ ModReloadListeners.register()  — 注册 JSON 热重载监听器
  ├─ EcofluxBiomeModifiers.register() — 注册 BiomeModifier 序列化器
  ├─ EcofluxFeatures.register()     — 注册 Feature/ConfiguredFeature/PlacedFeature
  └─ registerConfig()               — 服务端 + 客户端 TOML 配置
```

## 资源结构

```
src/main/resources/
├── ecoflux.mixins.json                    # Mixin 配置
├── assets/ecoflux/lang/
│   ├── en_us.json                         # 英文翻译
│   └── zh_cn.json                         # 中文翻译
└── data/ecoflux/
    ├── succession_paths/                  # 20 个演替路径 JSON
    │   ├── plains_to_forest.json
    │   ├── plains_to_meadow.json
    │   └── ...
    ├── biome_rules/minecraft/             # 64 个群系规则 JSON
    │   ├── plains.json
    │   ├── forest.json
    │   └── ...
    ├── plant_definitions/
    │   └── plants.json                    # 中心植物注册表
    ├── neoforge/biome_modifier/
    │   ├── cancel_vanilla_trees.json      # BiomeModifier 触发文件
    │   └── add_ecoflux_trees.json
    ├── worldgen/world_preset/
    │   └── sampling.json                  # 采样世界预设
    └── tags/blocks/
        ├── simple_vegetation.json
        ├── grass_cover.json
        ├── uses_foliage_tint.json
        └── uses_grass_tint.json
```
