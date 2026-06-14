# 树木生长系统

树木生长系统拦截原版树苗/蘑菇的瞬间生长，改为多阶段渐进生长过程。使用 **空间定殖 (Space Colonization)** 算法生成所有树种的形状，灵感来自 Dynamic Trees (ferreusveritas, MIT)。

## 阶段路线图

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 1 | ✅ 完成 | Mixin 拦截树苗生长 (`SaplingBlockMixin`) |
| Phase 2 | ✅ 完成 | 渐进生长会话 (`TreeGrowthSession` + `TreeGrowthHandler.tickAll()`) |
| Phase 3 | ✅ 完成 | 多树种 profiles (8 树 + 2 蘑菇) |
| Phase 4 | ✅ 完成 | 树木死亡/腐烂 (2026-06-11) |
| Phase 5 | 🔜 计划 | 演替系统整合 |
| Phase 6 | 🔜 计划 | 客户端死亡视觉效果 |
| Phase 7 | ✅ 完成 | 空间定殖算法 (2026-06-13/14) — 全部 8 个树种 |

## 核心组件

| 类 | 职责 |
|----|------|
| `TreeGrowthHandler` | 全局单例，管理生长会话 (NBT 持久化)，注册 10 个 profile (8 树 + 2 蘑菇)，分发到 SC 或蘑菇管线 |
| `TreeGrowthSession` | 每棵树生长状态（位置、树种、阶段、高度），NBT 可序列化，含 transient SC 数据 (scParams/stageLogs/stageLeaves) |
| `TreeGrowthProfile` | 接口：树种标识、方块类型、is2x2、高度/阶段计算、canGrowStage、growStage |
| `SpaceColonizationProfile` | record 实现 `TreeGrowthProfile`，is2x2 标志 + 可选 PostGrowHook（红树支柱根） |
| `SpaceColonizationParams` | 12 字段参数 record。包络体驱动树形 (ELLIPSOID/TALL_ELLIPSOID/CONE) + DT 风格原木生长参数 |
| `SpaceColonizationGenerator` | 核心算法：DT 风格离散方向 probMap 原木生长 + 末端叶片簇 (Phase 1) + 包络体密度衰减 (Phase 2)。支持 1x1 和 2x2 |
| `MushroomGrowthProfile` | 参数化 record，统一棕色/红色蘑菇 |
| `TreeShapeUtils` | 共享工具：位置确定性噪声、2x2 检测、原木放置 |

## 生长管线

```
树苗/蘑菇自然生长 (randomTick)
  │
  ▼
SaplingBlock.advanceTree() — 原版生长调用
  │
  ├─ 树苗未被追踪? → 原版瞬间生长 (不变)
  │
  └─ 树苗被 VegetationTracker 追踪?
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
       └─ 追踪为 TreeStructure
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
       │   └─ growStage() → 从 session 取阶段位置放置方块
       └─ 否 (蘑菇) → growStage() (自定义逻辑)
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
| `scParams` | SpaceColonizationParams | transient | SC 参数 |
| `stageLogs` | List\<List\<BlockPos\>\> | transient | 每阶段原木位置 |
| `stageLeaves` | List\<List\<BlockPos\>\> | transient | 每阶段树叶位置 |
| `placedLogs` | Set\<BlockPos\> | transient | 已放置的所有原木 |
| `placedLeaves` | Set\<BlockPos\> | transient | 已放置的所有树叶 |

## TreeGrowthProfile 实现

所有 8 个树种使用 `SpaceColonizationProfile`，2 个蘑菇使用 `MushroomGrowthProfile`。

### 空间定殖树种

| 树种 | 类型 | 高度 | 阶段间隔 | 总耗时 | 特征 |
|------|------|------|---------|--------|------|
| 橡树 (oak) | 1x1 | ~10-16 | 3600 tick | ~27 分 | ELLIPSOID, rXZ=12 h=15, 中分叉 |
| 白桦 (birch) | 1x1 | ~19-25 | 2400 tick | ~20 分 | TALL_ELLIPSOID, rXZ=6 h=18, 少分叉 |
| 樱花 (cherry) | 1x1 | ~16-22 | 3600 tick | ~27 分 | ELLIPSOID, rXZ=16 h=17, 高分叉 |
| 云杉 (spruce) | 1x1 | ~25-33 | 4800 tick | ~48 分 | CONE, rXZ=8 h=32, 水平短枝 |
| 丛林 (jungle) | 2x2 | ~24-33 | 4800 tick | ~64 分 | ELLIPSOID, rXZ=10 h=20, 4 平行干 |
| 深色橡树 (dark_oak) | 2x2 | ~14-20 | 3600 tick | ~30 分 | ELLIPSOID(宽), rXZ=12 h=10, 4 平行干 |
| 金合欢 (acacia) | 1x1 | ~12-16 | 3600 tick | ~27 分 | ELLIPSOID(扁), rXZ=14 h=6, 高分枝点 |
| 红树 (mangrove) | 1x1 | ~9-14 | 3200 tick | ~20 分 | ELLIPSOID, rXZ=8 h=10, PostGrowHook 支柱根 |

### 蘑菇

| 类型 | 高度 | 阶段间隔 | 特征 |
|------|------|---------|------|
| 棕色蘑菇 (brown_mushroom) | 4-7 | 2400 tick | 平顶蘑菇冠 (FLAT) |
| 红色蘑菇 (red_mushroom) | 3-7 | 2400 tick | 穹顶蘑菇冠 (DOMED) |

## 空间定殖算法 (Space Colonization)

混合算法：**Dynamic Trees 风格离散方向节点生长**（原木）+ **末端叶片簇 + 包络体密度衰减**（树叶）。MIT 标注。

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

两阶段策略：

1. **Phase 1 — 末端叶片簇** (DT 风格): 检测末端位置 (≤2 Chebyshev 邻居)，以 `max(2, leafRadius)` 半径放置 `leafDensity` 概率叶片，跳过包络体检查
2. **Phase 2 — 包络体填充**: 对非末端原木，使用包络体密度衰减 (阈值 0.01)，填充剩余树冠

### 包络体类型

| 类型 | 形状 | 适用树种 |
|------|------|---------|
| `ELLIPSOID` | 椭球体 | oak, cherry, jungle, dark_oak, acacia, mangrove |
| `TALL_ELLIPSOID` | 细长椭球 | birch |
| `CONE` | 圆锥形 | spruce |

### 参数 (12 字段)

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

- **无悬空方块**: 每片树叶必须 Chebyshev 邻接至少一个原木 (`isAdjacentTo`)
- **原木连续性**: 离散方向保证每步 Chebyshev ≤ 1
- **底部清干**: `minLeafY = saplingPos + lowestBranchHeight`
- **确定性**: 相同种子总是生成相同树形 (`TreeShapeUtils.positionRandom`)
- **连通性**: `verifyConnectivity()` BFS 验证所有原木形成单一连通分量

### 测试命令

```
/ecoflux tree instant <preset> [seed]     — 玩家位置瞬间生成 (加 _2x2 后缀测试 2x2)
/ecoflux tree grid <preset> <param> <min> <max> <count> — 参数扫描
/ecoflux tree stats <preset> [seed]      — 统计信息
```

## Mixin 层

| Mixin | 目标 | 作用 |
|-------|------|------|
| `SaplingBlockMixin` | `SaplingBlock.advanceTree()` | 拦截被追踪树苗的瞬间生长 |
| `BlockRenderDispatcherMixin` | 方块渲染 | 抑制被追踪方块的 vanilla 渲染 |

## 如何添加新树种

1. 在 `SpaceColonizationParams` 中添加 `xxx()` 工厂方法，配置 12 个参数
2. 在 `TreeGrowthHandler` 中用 `reg()` 注册：
   ```java
   reg(new SpaceColonizationProfile(id("xxx"), ticksPerStage, LOG, LEAVES, is2x2,
       SpaceColonizationParams.xxx(), null));  // 可选的 PostGrowHook
   ```
3. 用 `/ecoflux tree instant xxx` 反复调参测试
