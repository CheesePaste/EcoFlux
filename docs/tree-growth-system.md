# 树木生长系统

> 最后更新: 2026-06-16

树木生长系统拦截原版树苗/蘑菇的瞬间生长，改为多阶段渐进生长过程。使用 **空间定殖 (Space Colonization)** 算法生成所有树种的形状，灵感来自 Dynamic Trees (ferreusveritas, MIT)。

## 相关文档

修改树木系统前必读：[plant-lifecycle-system.md](plant-lifecycle-system.md) · [plant-death-system.md](plant-death-system.md)

## 阶段路线图

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 1 | ✅ 完成 | Mixin 拦截树苗生长 (`SaplingBlockMixin`) |
| Phase 2 | ✅ 完成 | 渐进生长会话 (`TreeGrowthSession` + `TreeGrowthHandler.tickAll()`) |
| Phase 3 | ✅ 完成 | 多树种 profiles (8 树 + 2 蘑菇) |
| Phase 4 | ✅ 完成 | 树木死亡/腐烂 |
| Phase 5 | 🔜 计划 | 演替系统整合 |
| Phase 6 | 🔜 计划 | 客户端死亡视觉效果 |
| Phase 7 | ✅ 完成 | 空间定殖算法 — 全部 9 个树种 (含 1x1/2x2)，世界生成集成 |

## 核心组件

| 类 | 职责 |
|----|------|
| `TreeGrowthHandler` | 全局单例，管理生长会话 (NBT 持久化)，从 `TreeGrowthProfileRegistry` 解析 profile，分发到 SC 或蘑菇管线，发射 `TreeGrowthEvent` |
| `TreeGrowthProfileRegistry` | 公开 API 注册表，管理所有 `TreeGrowthProfile`，内置 12 个原版 profile，外部 mod 可 `register()` 自定义树种 |
| `TreeGrowthSession` | 每棵树生长状态（位置、树种、阶段、高度），NBT 可序列化，含 transient SC 数据 (scParams/stageLogs/stageLeaves) |
| `TreeGrowthProfile` | 接口：树种标识、方块类型、is2x2、canGrowStage、growStage |
| `SpaceColonizationProfile` | record 实现 `TreeGrowthProfile`，is2x2 标志 + 可选 PostGrowHook（红树支柱根） |
| `SpaceColonizationParams` | 12 字段参数 record。包络体驱动树形 + DT 风格原木生长参数。含 9 个树种静态工厂方法 |
| `SpaceColonizationGenerator` | 核心算法：DT 风格离散方向 probMap 原木生长 + 末端叶片簇 + 包络体密度衰减。支持 1x1 和 2x2 |
| `MushroomGrowthProfile` | 参数化 record，统一棕色/红色蘑菇 (MushroomCapStyle: FLAT/DOMED) |
| `TreeShapeUtils` | 共享工具：位置确定性噪声、2x2 检测、原木放置 |

## 注册的所有 Profile

`TreeGrowthProfileRegistry.initBuiltin()` 注册 12 个内置 profile（由 `TreeGrowthHandler` 静态初始化块调用）：

| 树种 | Profile 类型 | ticksPerStage | is2x2 | 其他 |
|------|-------------|---------------|-------|------|
| oak | SpaceColonizationProfile | 1200 | no | — |
| birch | SpaceColonizationProfile | 800 | no | — |
| spruce_1x1 | SpaceColonizationProfile | 1400 | no | — |
| cherry | SpaceColonizationProfile | 1200 | no | — |
| jungle_1x1 | SpaceColonizationProfile | 1400 | no | — |
| acacia | SpaceColonizationProfile | 1200 | no | — |
| mangrove | SpaceColonizationProfile | 1067 | no | PostGrowHook: placePropRoots |
| jungle (2x2) | SpaceColonizationProfile | 1600 | yes | — |
| dark_oak (2x2) | SpaceColonizationProfile | 1200 | yes | — |
| spruce (2x2) | SpaceColonizationProfile | 2000 | yes | — |
| brown_mushroom | MushroomGrowthProfile | 800 | no | CapStyle: FLAT |
| red_mushroom | MushroomGrowthProfile | 800 | no | CapStyle: DOMED |

2x2→1x1 回退逻辑：`interceptGrowth()` 中若 `is2x2=true` 的 profile 检测 2x2 失败，自动查找 `<treeName>_1x1` 变体（如 `spruce` → `spruce_1x1`、`jungle` → `jungle_1x1`）。

## 生长管线

```
树苗/蘑菇自然生长 (randomTick)
  │
  ▼
SaplingBlock.advanceTree() / MushroomBlock.growMushroom() — 原版生长调用
  │
  ├─ 树苗/蘑菇未被追踪? → 原版瞬间生长 (不变)
  │
  └─ 被 VegetationTracker 追踪?
       │
       ▼
     SaplingBlockMixin / MushroomBlockMixin 拦截
       │  取消原版生长
       │
       ▼
     TreeGrowthHandler.interceptGrowth()
       ├─ 创建 TreeGrowthSession
       ├─ SC profile → ensureConnectedPlan() 懒生成
       ├─ 替换树苗为原木方块
       └─ 追踪为 TreeStructure (new PlantDefinition for log/mushroom_block)
       │
       ▼
┌──────────────────────────────────────────┐
│  TreeGrowthHandler.tickAll() (每 20 tick) │
│    遍历所有活跃 session                     │
│    ├─ 检查阶段计时器                        │
│    ├─ 到时 → growStage()                  │
│    └─ 全部阶段完成 → onGrowthComplete()     │
└──────────────────────────────────────────┘
       │
       ▼
     growStage() — 两路分发:
       ├─ profile instanceof SpaceColonizationProfile?
       │   ├─ YES → ensureConnectedPlan() (懒生成)
       │   └─ growStage() → 从 session 取对应阶段位置放置方块
       └─ 否 (蘑菇) → growStage() (自定义 stem+cap 逻辑)
```

## TreeGrowthSession

每棵生长中的树的状态：

| 字段 | 类型 | 持久化 | 说明 |
|------|------|--------|------|
| `saplingPos` | BlockPos | NBT | 树苗位置 (2x2 则为 NW 角) |
| `treeType` | ResourceLocation | NBT | 树种 ID |
| `currentStage` | int | NBT | 当前阶段 (0-based) |
| `totalStages` | int | NBT | 总阶段数 |
| `ticksPerStage` | int | NBT | 每阶段 tick 数 |
| `resolvedHeight` | int | NBT | 解析后的树高 |
| `growthStartTime` | long | NBT | 生长开始时间 |
| `lastStageTime` | long | NBT | 上一阶段完成时间 |
| `scParams` | SpaceColonizationParams | transient | SC 参数（从种子重建） |
| `scSeed` | long | NBT | SC 确定性种子 |
| `stageLogs` | List\<List\<BlockPos\>\> | transient | 每阶段原木位置 |
| `stageLeaves` | List\<List\<BlockPos\>\> | transient | 每阶段树叶位置 |
| `placedLogs` | Set\<BlockPos\> | transient | 已放置的所有原木 |
| `placedLeaves` | Set\<BlockPos\> | transient | 已放置的所有树叶 |

## 空间定殖算法 (Space Colonization)

混合算法：**Dynamic Trees 风格离散方向节点生长**（原木）+ **末端叶片簇 + 包络体密度衰减**（树叶）。

### 原木生成 (growLogs)

- **离散方向**: 6 个 `Direction` 值 (UP/DOWN/NORTH/SOUTH/WEST/EAST)，取代连续 pitch/yaw
- **probMap[6]**: DT 风格加权整数数组，方向选择 = 加权随机选择
  - UP 权重 = `upProbability` (birch=4, spruce=3, oak=2, cherry=1)
  - 当前方向动量 = +1 (防止 zigzag)
  - 水平方向 = 检查目标是否空闲
- **ConiferLogic** (spruce): 仅奇数步允许水平转弯，分枝能量 = trunk/3 (上限 16)，DOWN 永久禁止
- **分枝**: 首次转向 = 分枝点，创建带 fraction 能量的子 GrowSignal
- **二级分枝**: 现有分枝上 `secondaryChance` 概率产生子分枝
- **2x2 树干**: 4 条平行干信号从 2x2 网格 4 个位置发出

### 树叶生成 (generateLeaves)

三阶段策略：

1. **Phase 1 — 末端叶片簇**: 检测末端位置 (≤2 Chebyshev 邻居)，以 `max(2, leafRadius)` 半径放置 `leafDensity` 概率叶片
2. **Phase 2 — 包络体填充**: 对非末端原木，使用包络体密度衰减 (阈值 0.01)，填充剩余树冠
3. **Phase 3 — 2x2 树顶盖**: 2x2 树干（深色橡木、丛林、云杉、红树）的解析高度可能超出包络体顶部（丛林 +2.5 格、云杉 +4.5 格），导致树干顶部平台裸露。Phase 3 两遍传递：第一遍在最高原木层上方 dy=0..1 贴原木放置树叶；第二遍 dy=2..capR 使用**实时 leafSet**（非静态快照）作为锚点向上延伸，确保每层树叶可作为上一层的锚点。capR = leafRadius+2（最小 5），Phase 2 同步在树干顶部 3 层绕过包络体密度检测

### 包络体类型

| 类型 | 形状 | 适用树种 |
|------|------|---------|
| `ELLIPSOID` | 椭球体 | oak, cherry, jungle, dark_oak, acacia, mangrove |
| `TALL_ELLIPSOID` | 细长椭球 | birch |
| `CONE` | 圆锥形 | spruce |

### SpaceColonizationParams (12 字段)

| 参数 | 类型 | 说明 |
|------|------|------|
| `envelopeType` | EnvelopeType | 包络体形状 |
| `envelopeRadiusXZ` | double | 水平半径 |
| `envelopeHeight` | double | 垂直范围 |
| `envelopeCenterYOffset` | double | 包络体中心 Y 偏移 |
| `upProbability` | int | UP 方向权重 |
| `splitChance` | double | 每次步进的分枝概率 |
| `branchLengthRatio` | double | 分枝能量相对父干比例 |
| `secondaryChance` | double | 二级分枝概率 |
| `lowestBranchHeight` | int | 最小分枝高度 |
| `leafRadius` | int | 树叶距原木最大 Chebyshev 距离 |
| `leafDensity` | double | 树叶填充概率 |
| `canopyStages` | int | 树冠阶段数 |

### 正确性保证

- **无悬空方块**: 每片树叶必须 Chebyshev 邻接至少一个原木
- **原木连续性**: 离散方向保证每步 Chebyshev ≤ 1
- **底部清干**: `minLeafY = saplingPos + lowestBranchHeight`
- **确定性**: 相同种子总是生成相同树形 (`TreeShapeUtils.positionRandom`)
- **连通性**: `verifyConnectivity()` BFS 验证所有原木形成单一连通分量

## 世界生成集成

### EcofluxTreeFeature

`EcofluxTreeFeature` 在 `VEGETAL_DECORATION` 阶段放置 SC 树：

1. 从 biome → `BiomeRules` 读取该群系的植物配置
2. 从 `BiomeRules.plants()` 中筛选有对应 `TreeGrowthProfile` 的树种
3. 按权重随机选择树种
4. 调用 `SpaceColonizationGenerator.generateFull()` 生成完整树形
5. 用 `WorldGenLevel.setBlock()` 放置方块
6. 将 `TreeStructure` 存入 `static PENDING_TREES` map

### WorldGenVegetationScanner

Chunk 加载时消费 `PENDING_TREES`，将树注册到 `VegetationTracker`：
- Phase 1: 消费 PENDING_TREES（decoration→chunk-load 桥接）
- Phase 1b: BFS 检测巨型蘑菇
- Phase 2: 扫描小型植物（花草/蕨/小蘑菇）
- Phase 3: 密度上限裁剪

### BiomeModifier 管线

```
Phase.REMOVE: CancelVanillaTreesBiomeModifier
  └─ 遍历所有 VEGETAL_DECORATION 阶段
       └─ 递归展开 SimpleRandomFeatureConfiguration / RandomFeatureConfiguration
            └─ 移除 TreeConfiguration / RootSystemConfiguration 的 feature

Phase.ADD: AddEcofluxTreesBiomeModifier
  └─ 添加 ecoflux:ecoflux_trees placed feature
```

## 测试命令

```
/ecoflux tree instant <preset> [seed]     — 玩家位置瞬间生成完整树 (加 _2x2 后缀测试 2x2)
/ecoflux tree grid <preset> <param> <min> <max> <count> — 参数扫描
/ecoflux tree stats <preset> [seed]      — 统计信息
/ecoflux tree instant <mushroom_preset>  — 瞬间生成蘑菇
```

## Mixin 层

| Mixin | 目标 | 作用 |
|-------|------|------|
| `SaplingBlockMixin` | `SaplingBlock.advanceTree()` | 拦截被追踪树苗的瞬间生长 |
| `MushroomBlockMixin` | 蘑菇生长 | 拦截被追踪蘑菇的瞬间生长 |
| `BlockRenderDispatcherMixin` | 方块渲染 | 抑制被追踪方块的 vanilla 渲染 |
| `SodiumBlockRendererMixin` | 钠模组渲染 | 钠模组兼容性 |
| `ApplyBiomeDecorationMixin` | 世界装饰 | 装饰阶段集成 |

## API 事件

`TreeGrowthHandler` 在关键节点通过 NeoForge 事件总线发射 `TreeGrowthEvent`：

| 事件 | 发射位置 | 时机 |
|------|---------|------|
| `Start` | `TreeGrowthHandler.interceptGrowth()` | session 创建后 |
| `Stage` | `TreeGrowthHandler.tickAll()` | 每个生长阶段完成后 |
| `Complete` | `TreeGrowthHandler.onGrowthComplete()` | 树结构记录到 chunk 数据后 |

事件类位于 `com.cp.ecoflux.api.event.TreeGrowthEvent`，携带 `ServerLevel`、`BlockPos`（树苗位置）和 `TreeGrowthSession`。

## 如何添加新树种

### 内置树种（修改 EcoFlux 源码）

1. 在 `SpaceColonizationParams` 中添加 `xxx()` 静态工厂方法，配置 12 个参数
2. 在 `TreeGrowthProfileRegistry.initBuiltin()` 中用 `reg()` 注册：
   ```java
   reg(new SpaceColonizationProfile(id("xxx"), ticksPerStage, LOG, LEAVES, is2x2,
       SpaceColonizationParams.xxx(), null));  // 可选的 PostGrowHook
   ```
3. 如需自定义后处理（如红树支柱根），传入 `PostGrowHook` lambda
4. 用 `/ecoflux tree instant xxx` 反复调参测试
5. 确保在 `plant_definitions/plants.json` 中有对应的树苗和原木条目

### 外部 mod 注册（不修改 EcoFlux 源码）

外部 mod 通过 `TreeGrowthProfileRegistry` 注册自定义树种：

```java
// 在 mod 初始化阶段调用（ModInit，先于 world load）
TreeGrowthProfileRegistry.register(myCustomProfile);
```

`TreeGrowthProfileRegistry` 提供以下公开方法：

| 方法 | 说明 |
|------|------|
| `register(TreeGrowthProfile)` | 注册新树种 profile |
| `registerAlias(ResourceLocation alias, ResourceLocation target)` | 为已注册树种添加别名 |
| `find(ResourceLocation key)` | 按树种 ID 查找 profile（含 minecraft 命名空间回退） |
| `resolveFromSapling(ResourceLocation saplingId)` | 从树苗方块 ID 解析 profile（自动去除 `_sapling` 后缀） |
| `resolveFromLog(ResourceLocation logId)` | 从原木方块 ID 解析 profile（自动去除 `_log`/`_wood`/`_stem`/`stripped_` 前缀后缀） |
