# 总体架构

## 包结构

```
com.s.ecoflux/
├── EcofluxMod.java              # @Mod 入口，组装所有子系统
├── EcofluxConstants.java        # MOD_ID, LOGGER, ResourceLocation 工厂
│
├── config/                      # 配置系统
│   ├── SuccessionConfigLoader       # Gson JSON 加载器 (SimpleJsonResourceReloadListener)
│   ├── SuccessionConfigRegistry     # 线程安全缓存查找 (findBestMatch)
│   ├── SuccessionPathDefinition     # Record: 演替路径完整定义
│   ├── PathPlantEntry               # Record: 路径中植物引用 (plant_id + weight)
│   ├── ChunkRules                   # Record: max_plants, consuming, intervals
│   ├── ClimateCondition             # Record: temperature/rainfall 范围
│   ├── PlantDefinition              # Record: 中心植物注册表条目 (plant_id, pointValue, maxAgeTicks, spawnRules)
│   ├── PlantRegistry                # 单例：中心植物注册表 (ConcurrentHashMap 缓存)
│   ├── PlantRegistryLoader          # SimpleJsonResourceReloadListener，监听 plant_definitions/
│   ├── PlantSpawnRules              # Record: 生成规则 (requireSky, maxLocalDensity, allowedBaseBlocks)
│   ├── FloatRange / IntRange        # Record: 范围工具类型
│   ├── EcofluxServerConfig          # 服务端 TOML 配置
│   ├── VisualLifecycleClientConfig  # 客户端 TOML 配置
│   └── SuccessionSpeedConfig        # 全局速度倍率
│
├── attachment/                  # 数据附件 (NBT 持久化)
│   ├── SuccessionChunkData          # 核心 per-chunk 状态
│   ├── ActiveVegetationRecord       # 活跃植被追踪记录
│   └── PlantQueueEntry              # 预生成植物条目
│
├── succession/                  # 演替核心系统
│   ├── SuccessionService            # 主编排入口 (init/step/tick/prune/spawn/evaluate)
│   ├── SuccessionTargetResolver     # 初始化：采样 biome/climate → 匹配 config
│   ├── SuccessionEvaluator          # 进度评估：aging gate + 点数 vs consuming
│   └── BiomeTransitionService       # 群系替换：fillBiomesFromNoise + 发包，植树下放 ForestPlanter
│
├── plant/                       # 植物生命周期系统
│   ├── VegetationTypeAdapter        # 核心接口：matches/captureBirth/observe/visualState
│   ├── VegetationTracker            # 单例：追踪/观察/同步所有植被
│   ├── PlantSpawner                 # 生成/修剪：trySpawnPlant/pruneInvalid/ensureQueue
│   ├── VegetationObservation        # Record: 观察结果
│   ├── VegetationTransformation     # Record: 转换描述
│   ├── VegetationVisualState        # Record: 视觉快照
│   ├── VegetationLifecycleStage     # Enum: 生命周期阶段
│   ├── TreeStructure                # Record: 树的多块结构数据
│   ├── SimplePlantAdapter           # 小型植物适配器 (花/草/蕨/蘑菇/枯灌木)
│   ├── SaplingAdapter               # 树苗适配器 (→ TreeGrowthHandler)
│   └── TreeStructureAdapter         # 成熟树适配器
│   │
│   └── tree/                    # 树木生长子系统
│       ├── TreeGrowthHandler        # 单例：通过 chunk data 管理生长会话 (NBT 持久化)
│       ├── TreeGrowthSession        # 每棵树状态 (NBT 可序列化，含 transient skeleton)
│       ├── TreeGrowthProfile        # 接口：树种生长参数 + morphologyParams()
│       ├── TreeShapeUtils           # 共享工具 (噪声/2x2/枝干/原木放置)
│       │
│       ├── morphology/          # 形态学系统
│       │   ├── TreeMorphology        # 集成入口 (generateSkeleton→planStages→growStage)
│       │   ├── SkeletonGenerator     # 参数化递归分枝
│       │   ├── CanopyEnvelope        # 6 种 3D 冠形密度函数 + CanopyConfig.fromMorphology()
│       │   ├── LeafFiller            # 骨架感知叶块放置
│       │   ├── MorphologyParams      # 纯数据 record (工厂方法迁至 MorphologyPresets)
│       │   ├── TreeSkeleton          # 骨架数据结构
│       │   ├── SkeletonNode          # Record: pos, type, radius, parentIndex, depth
│       │   └── NodeType              # Enum: TRUNK/PRIMARY_BRANCH/SECONDARY_BRANCH/TWIG
│       │
│       ├── profiles/            # 树种生长配置 (2 个参数化 record)
│       │   ├── MorphologyTreeProfile      # 统一 9 种形态学树种 (CanGrowStageStrategy + GrowStageHook)
│       │   └── MushroomGrowthProfile      # 统一 2 种蘑菇 (MushroomCapStyle: FLAT/DOMED)
│       │
│
├── client/                      # 客户端
│   ├── visual/                  # 视觉生命周期渲染
│   │   ├── VisualLifecycleClientRuntime  # 客户端单例：接收服务端视觉状态
│   │   ├── VisualLifecycleWorldRenderer  # 渲染视觉叠加 (缩放/老化着色)
│   │   ├── VisualLifecycleAdapter        # 接口
│   │   ├── VisualLifecycleProfile        # 视觉配置
│   │   ├── VisualLifecycleInstance       # 活跃实例
│   │   ├── VisualLifecycleRenderState    # 渲染状态
│   │   ├── VisualLifecycleStage          # 视觉阶段
│   │   ├── VisualLifecycleExternalState  # 外部状态输出
│   │   ├── VisualLifecycleTrackingSource # 追踪来源
│   │   ├── VisualLifecycleRegistry       # 注册表
│   │   ├── ModClientVisualLifecycle      # 客户端初始化
│   │   └── adapters: Flower/Grass/Sapling/Generic
│   │
│
├── network/                     # 网络同步
│   ├── ModNetworking                    # Payload 注册
│   ├── VegetationVisualChunkSyncPayload # 植被视觉状态 → 客户端
│   ├── VegetationVisualSyncEntry        # 单植物同步条目
│
├── world/                       # 世界工具
│   └── ChunkSamplingHelper      # 采样 biome/climate/surface/findSpawnPos
│
├── init/                        # 初始化与事件
│   ├── ModAttachments           # DataAttachment<SuccessionChunkData> 注册
│   ├── ModChunkEvents           # Chunk load/unload/tick 事件
│   ├── ModPlayerEvents          # 玩家放置/破坏 → VegetationTracker
│   ├── ModCommands              # /ecoflux debug 命令
│   └── ModReloadListeners       # JSON 热重载 (/reload)
│
├── mixin/                       # Mixin 层
│   ├── SaplingBlockMixin        # 拦截树苗瞬间生长 → TreeGrowthHandler
│   ├── MushroomBlockMixin       # 拦截蘑菇瞬间生长
│   └── client/
│       └── BlockRenderDispatcherMixin  # 抑制被追踪方块的 vanilla 渲染
│
└── test/                        # 测试 / 原型工具
    ├── prototype/
    │   └── PrototypeChunkController  # 10 秒加速演替演示模式
    └── performance/
        └── PerformanceProfiler       # 轻量级性能分析器
```

## 分层架构

```
┌────────────────────────────────────────────┐
│              配置层 (config/)               │
│   JSON 定义 → SuccessionConfigRegistry     │
│   决定：什么群系能演替到什么群系              │
└────────────────────┬───────────────────────┘
                     │ 提供 SuccessionPathDefinition
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
│   Handler → Session → Morphology → Animation│
└────────────────────────────────────────────┘

横向支撑层：
┌────────────┐  ┌────────────┐  ┌────────────┐
│ attachment │  │  network   │  │   mixin    │
│ 数据持久化  │  │  网络同步   │  │  字节码钩子 │
└────────────┘  └────────────┘  └────────────┘
```

## 核心数据流

```
Chunk Load (ModChunkEvents)
  │
  ├─→ SuccessionService.initializeChunk()
  │     ├─→ ChunkSamplingHelper.sampleChunkCenterBiome()
  │     ├─→ ChunkSamplingHelper.sampleChunkClimate()
  │     ├─→ SuccessionConfigRegistry.findBestMatch()
  │     ├─→ SuccessionTargetResolver.populateChunkData()
  │     └─→ PlantSpawner.ensureQueue() + trySpawnPlant()
  │
  ▼
Chunk Tick (每 20 tick / 1秒)
  │
  ├─→ SuccessionService.processChunkTick()
  │     ├─→ VegetationTracker.observeChunk()
  │     ├─→ SuccessionEvaluator.evaluateProgress()
  │     │     ├─ hasAgingVegetation()? → 比较 vegetationPoints vs consuming
  │     │     └─ shouldRegress()? → progress <= -1.0
  │     ├─→ progress >= 1.0 → BiomeTransitionService.applyTransition()
  │     ├─→ progress <= -1.0 → BiomeTransitionService.applyRegression()
  │     ├─→ PlantSpawner.pruneInvalidPlants()
  │     └─→ PlantSpawner.trySpawnPlant()
  │
  ├─→ TreeGrowthHandler.tickAll() (每 20 tick)
  │     └─→ profile.morphologyParams()? → TreeMorphology.growStage()
  │
  └─→ VegetationTracker.syncChunkToTracking() → 网络发包

## 关键设计决策

### 1. Data-driven 配置
演替路径在 `data/ecoflux/succession_paths/*.json` 中定义为 JSON 文件，通过 `SimpleJsonResourceReloadListener` 加载，支持 `/reload` 热重载。无需修改代码即可添加新路径。

### 2. DataAttachment 持久化
每个 chunk 的状态通过 `DataAttachment<SuccessionChunkData>` 附加，完整 NBT 序列化。Chunk 卸载后重新加载时状态自动恢复。

### 3. Adapter 模式植物识别
植物类型通过 `VegetationTypeAdapter` 接口识别，而非硬编码。每个 adapter 实现 `matches(BlockState)` 判断是否匹配，`captureBirth()` 记录出生状态，`observe()` 评估生命周期阶段。

### 4. 统一 vegetationRecords
所有植物追踪统一到 `vegetationRecords` (`Map<BlockPos, ActiveVegetationRecord>`)，不再有单独的 `activePlants` 系统。`SuccessionChunkData.getCurrentPlantCount()` 返回 `vegetationRecords.size()`。

### 5. 中心植物注册表 (PlantRegistry)
植物定义 (`PlantDefinition`) 与演替路径分离：路径只通过 `PathPlantEntry(plantId, weight)` 引用植物，完整定义集中在 `data/ecoflux/plant_definitions/plants.json`，通过 `PlantRegistry` 单例按 ID 查找。不再在路径 JSON 中内联 `point_value`、`max_age_days`、`spawn_rules` 等字段。

### 6. 形态学驱动树木生长
树生长使用参数化递归骨架 + 3D 冠包络体 + 骨架感知叶填充，而非简单的"直杆+圆盘"。每个树种有独立的 `MorphologyParams`，骨架通过位置确定性种子生成以保证视觉一致性。

### 7. BlockDisplay 动画
树生长阶段不瞬间放置方块，而是生成临时 `BlockDisplay` 实体播放缩放动画（树干挤出/树叶膨胀），完成后替换为真实方块。利用 Minecraft 内置的 Display 插值系统实现客户端平滑过渡。

## 启动流程

```
EcofluxMod 构造器
  ├─ ModAttachments.register()      — 注册 SUCCESSION_CHUNK_DATA
  ├─ ModNetworking.register()       — 注册网络 Payload
  ├─ ModChunkEvents.register()      — 注册 chunk load/unload/tick 处理器
  ├─ ModPlayerEvents.register()     — 注册玩家放置/破坏处理器
  ├─ ModCommands.register()         — 注册 /ecoflux 命令
  ├─ ModReloadListeners.register()  — 注册 JSON 热重载监听器
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
    ├── succession_paths/                  # 21 个演替路径 JSON
    │   ├── plains_to_forest.json
    │   ├── plains_to_meadow.json
    │   └── ...
    ├── plant_definitions/
    │   └── plants.json                    # 中心植物注册表
    └── tags/blocks/
        ├── simple_vegetation.json
        ├── grass_cover.json
        ├── uses_foliage_tint.json
        └── uses_grass_tint.json
```
