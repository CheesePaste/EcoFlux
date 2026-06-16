# 架构重构计划

2026-06-16 架构审查，识别出以下影响代码可维护性和 AI 可读性的问题。按收益/风险比排序。

## 问题总览

| # | 问题 | 影响 | 风险 | 优先级 |
|---|------|------|------|--------|
| 1 | 集中式服务持有者（替代20个INSTANCE单例） | 依赖图不可见，AI每次需追踪隐式依赖 | 中 | P0 |
| 2 | 三个 Loader/Registry 对完全重复 | 6文件 ~420行，大量复制粘贴样板 | 低 | P0 |
| 3 | ModCommands.java God Class | 876行，每次理解命令系统消耗大量上下文 | 低 | P1 |
| 4 | 大型文件拆分 | 7个文件超400行，强制AI一次性消费 | 中 | P1 |
| 5 | 测试/开发工具混在 main 源码 | AI难以区分核心代码和辅助工具 | 低 | P2 |

---

## P0-1：集中式服务持有者 — 替代散落单例

### 当前状态

20 个 `public static final X INSTANCE = new X()` 散落在各文件，依赖关系完全不可见：

- `VegetationTracker.INSTANCE` 被 17 个文件引用
- `PlantRegistry.INSTANCE` 被 11 个文件引用
- `TreeGrowthHandler.INSTANCE` 被 7 个文件引用

### 目标

创建一个集中的 `EcofluxServices` 类，所有服务在一处初始化，依赖图单文件可见。

### 方案

```java
// src/main/java/com/cp/ecoflux/EcofluxServices.java
public final class EcofluxServices {
    // -- 第一层：无依赖的基础组件 --
    public static final PlantRegistry PLANT_REGISTRY = new PlantRegistry();
    
    // -- 第二层：适配器（依赖 PlantRegistry） --
    public static final SaplingAdapter SAPLING_ADAPTER = new SaplingAdapter(PLANT_REGISTRY);
    public static final SimplePlantAdapter SIMPLE_PLANT_ADAPTER = new SimplePlantAdapter(PLANT_REGISTRY);
    public static final TreeStructureAdapter TREE_STRUCTURE_ADAPTER = new TreeStructureAdapter();
    
    // -- 第三层：核心服务 --
    public static final VegetationTracker TRACKER = new VegetationTracker(
        List.of(SAPLING_ADAPTER, TREE_STRUCTURE_ADAPTER, SIMPLE_PLANT_ADAPTER));
    public static final PlantSpawner SPAWNER = new PlantSpawner(PLANT_REGISTRY, TRACKER);
    public static final TreeGrowthHandler TREE_GROWTH = new TreeGrowthHandler(TRACKER);
    
    // -- 第四层：编排服务 --
    public static final SuccessionService SUCCESSION = new SuccessionService(TRACKER, SPAWNER, ...);
    // ...
    
    private EcofluxServices() {}
}
```

### 实施步骤

1. 创建 `EcofluxServices`，将各 `INSTANCE` 的初始化逻辑集中
2. 原 `Xxx.INSTANCE` 改为委托 `EcofluxServices.XXX`，标记 `@Deprecated`
3. 逐步将新代码改为直接引用 `EcofluxServices`
4. 最终移除各文件中的 `INSTANCE` 字段

### 难点

- **Mixins** 无法构造注入，继续通过 `EcofluxServices` 访问
- **客户端类**（`client/visual/`）使用独立的 `EcofluxClientServices`
- **DT 兼容层**的 `DTTreeAdapter` 动态注入：保留 `addAdapter()` 机制

---

## P0-2：统一 Loader/Registry 基类

### 当前状态

6 个文件独立实现相同模式：

| 文件 | 行数 |
|------|------|
| `SuccessionConfigLoader.java` | 159 |
| `SuccessionConfigRegistry.java` | 85 |
| `BiomeRulesLoader.java` | 69 |
| `BiomeRulesRegistry.java` | 37 |
| `PlantRegistryLoader.java` | 75 |
| `PlantRegistry.java` | 38 |
| **合计** | **463** |

重复内容包括：GSON 字段定义、INSTANCE 单例、apply() 骨架、schema_version 检查、错误日志模板。

三个 Registry 的线程安全策略还互相不一致（synchronized+volatile vs ConcurrentHashMap）。

### 方案

抽取两个抽象基类：

```
AbstractJsonConfigLoader<T>
├── SuccessionConfigLoader    (解析为 SuccessionPathDefinition)
├── BiomeRulesLoader          (解析为 BiomeRules)
└── PlantRegistryLoader       (解析为 PlantDefinition)

AbstractConfigRegistry<K, T>
├── SuccessionConfigRegistry  (多索引：pathId + sourceBiome)
├── BiomeRulesRegistry        (单索引：biomeId)
└── PlantRegistry             (单索引：plantId)
```

`AbstractJsonConfigLoader<T>` 统一处理：
- GSON 字段
- `SimpleJsonResourceReloadListener` 构造
- `apply()` 骨架（stream → sort → forEach → try/catch → registry.replace）
- `schema_version` 校验
- 错误日志

子类只需实现 `parseEntry(JsonObject): T` 和 `getRegistry(): AbstractConfigRegistry<?, T>`。

`AbstractConfigRegistry<K, T>` 统一处理：
- 线程安全的 `replace(Map<K, T>)`
- `get(K): Optional<T>`
- `getAll(): Collection<T>`
- `size(): int`

预计消除 ~200 行重复代码，6 个文件缩减为更小的子类。

---

## P1-1：拆分 ModCommands.java

### 当前状态

876 行，包含所有 `/ecoflux` 子命令，直接耦合到几乎所有包。

### 方案

按子系统拆分命令注册，每个子系统在自己的包内注册命令：

```
init/ModCommands.java          → ~40行（命令注册入口，委托给各子系统）
plant/PlantCommands.java       → lifecycle 子命令
plant/tree/TreeCommands.java   → tree 子命令
succession/SuccessionCommands.java → auto, speed 子命令
test/sample/SampleCommands.java    → sample, batch 子命令
```

每个子系统命令文件负责自己的命令节点注册和逻辑。`ModCommands` 只做顶层 `/ecoflux` 注册和各子系统的调用。

预计：876 行 → 1 个 40 行入口 + 5 个 ~150 行子系统命令文件。

---

## P1-2：大型文件拆分

### 当前状态

| 行数 | 文件 | 拆分方向 |
|---:|---|---|
| 593 | `plant/VegetationTracker.java` | 拆分 TreeStructureManager（树重建+BFS+死亡处理）、VisualSyncBuilder（视觉同步条目构建） |
| 564 | `plant/tree/spacecolonization/SpaceColonizationGenerator.java` | 算法本身复杂但内聚，暂不拆分（来自 DT，MIT） |
| 434 | `plant/tree/TreeGrowthHandler.java` | 拆分 GrowthSessionFactory（session创建逻辑） |
| 432 | `client/visual/VisualLifecycleClientRuntime.java` | 拆分 VisualStateInterpolator（tick外推+HSB计算） |
| 431 | `worldgen/WorldGenVegetationScanner.java` | 按三个 Phase 拆分为独立类（DecorationTreeProcessor, MushroomScanner, SimplePlantScanner, DensityCapper） |
| 401 | `attachment/SuccessionChunkData.java` | NBT 序列化逻辑可抽取到 SuccessionChunkDataSerializer |

### 原则

- 每个类职责单一，不超过 200 行
- 拆分后原有公共 API 保持不变（委托）
- 优先拆分 VegetationTracker 和 WorldGenVegetationScanner（收益最大）

---

## P2-1：分离测试/开发工具到独立 source set

### 当前状态

`test/` 和 `util/sample/` 包在 `src/main/java` 下，实际是运行时调试工具：

- `util/sample/BiomePlantSampler.java` (642行)
- `util/sample/SamplingBiomeSource.java` (91行)

### 方案

将这些文件移到专用的 `src/devtools/java/` source set，通过 Gradle 的 `sourceSets` 配置仅在开发构建时包含。或至少重命名包为 `devtools/` 消除 `test` 的误导性。

---

## 实施顺序建议

```
Phase 1 (低风险高收益，先做)
├── P0-2: 统一 Loader/Registry 基类
├── P0-1: 创建 EcofluxServices，集中 INSTANCE 初始化
└── 更新 docs/ 对应文档

Phase 2 (中风险，随后)
├── P1-1: 拆分 ModCommands
├── P1-2: 拆分 VegetationTracker + WorldGenVegetationScanner
└── 更新 docs/ 对应文档

Phase 3 (低优先级，排后)
└── P2-1: 分离 devtools source set
```
