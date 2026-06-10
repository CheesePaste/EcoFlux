# 树木生长系统

树木生长系统拦截原版树苗/蘑菇的瞬间生长，改为多阶段渐进生长过程。使用参数化递归骨架 + 3D 冠包络体 + 骨架感知叶填充生成真实树形，并通过 BlockDisplay 实体动画呈现生长过程。

## 阶段路线图

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 1 | ✅ 完成 | Mixin 拦截树苗生长 (`SaplingBlockMixin` + `MushroomBlockMixin`) |
| Phase 2 | ✅ 完成 | 渐进生长会话 (`TreeGrowthSession` + `TreeGrowthHandler.tickAll()`) |
| Phase 3 | ✅ 完成 | 多树种 profiles (6 树 + 2 蘑菇) |
| Phase 4 | 🔄 进行中 | 树木死亡/腐烂 |
| Phase 5 | 🔜 计划 | 演替系统整合 |
| Phase 6 | 🔜 计划 | 客户端死亡视觉效果 |

## 核心组件

| 类 | 职责 |
|----|------|
| `TreeGrowthHandler` | 全局单例，管理所有活跃生长会话，每 20 tick 驱动生长 |
| `TreeGrowthSession` | 每棵树生长状态（位置、树种、阶段、高度），NBT 可序列化 |
| `TreeGrowthProfile` | 接口：树种生长参数（高度范围、方块类型、阶段数、间距）+ `morphologyParams()` |
| `TreeShapeUtils` | 共享工具：位置确定性噪声、冠形半径函数、2x2 检测、枝干生成 |

## 生长管线

```
树苗自然生长 (randomTick)
  │
  ▼
SaplingBlock.advanceTree() — 原版生长调用
  │
  ├─ 树苗未被追踪? → 原版瞬间生长 (不变)
  │
  └─ 树苗被 VegetationTracker 追踪?
       │
       ▼
     SaplingBlockMixin 拦截
       │  取消原版 TreeGrower
       │
       ▼
     TreeGrowthHandler.interceptGrowth()
       ├─ 创建 TreeGrowthSession
       ├─ 树苗 → 原木 (替换方块)
       ├─ 追踪新原木为 TreeStructure
       └─ 同步客户端
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
     growStage()
       ├─ profile.morphologyParams() != null?
       │   ├─ YES → TreeMorphology.growStage()
       │   │         ├─ 放置当前阶段原木方块
       │   │         └─ LeafFiller.fillLeaves()
       │   └─ NO  → profile.growStage() (旧逻辑，向后兼容)
       └─ session.advanceStage()
```

## TreeGrowthSession

每棵生长中的树的状态：

| 字段 | 类型 | 持久化 | 说明 |
|------|------|--------|------|
| `pos` | BlockPos | NBT | 树苗位置 |
| `treeType` | String | NBT | 树种 ID（如 `"oak"`） |
| `stage` | int | NBT | 当前阶段 (0-based) |
| `totalStages` | int | NBT | 总阶段数 |
| `stageTicks` | int | NBT | 当前阶段已过 tick |
| `ticksPerStage` | int | NBT | 每阶段 tick 数 |
| `resolvedHeight` | int | NBT | 解析后的树高（从范围随机） |
| `gameTimeStart` | long | NBT | 生长开始时间 |
| `skeleton` | TreeSkeleton | transient | 骨架（不持久化，从种子重建） |
| `morphologyParams` | MorphologyParams | transient | 形态参数 |
| `stagePlan` | List | transient | 阶段分配计划 |

## TreeGrowthProfile 实现

| Profile | 树种 | 高度 | 阶段间隔 | 总耗时 | 形态特征 |
|---------|------|------|---------|--------|---------|
| `OakGrowthProfile` | 橡树 | 5-8 | 3600 tick | ~27 分 | 扁椭球冠，微倾斜，3-5 枝 |
| `BirchGrowthProfile` | 白桦 | 6-10 | 2400 tick | ~20 分 | 细高椭球冠，近乎垂直，0-2 短枝 |
| `SpruceGrowthProfile` | 云杉 | 8-15 | 4800 tick | ~48 分 | 锥形全高叶，垂直，8-15 水平枝 |
| `JungleGrowthProfile` | 丛林 | 10-15 | 4800 tick | ~64 分 | 2x2 干，大椭球+4 子冠，5-8 长枝 |
| `DarkOakGrowthProfile` | 深色橡树 | 6-10 | 3600 tick | ~30 分 | 2x2 干，扁圆柱密冠，4-6 枝 |
| `AcaciaGrowthProfile` | 金合欢 | 5-10 | 3600 tick | ~27 分 | 扁碟+散落簇，10-25° 倾斜，稀疏 |
| `BrownMushroomGrowthProfile` | 棕色蘑菇 | - | - | - | 蘑菇形态 |
| `RedMushroomGrowthProfile` | 红色蘑菇 | - | - | - | 蘑菇形态 |

## 形态学系统

形态学系统用参数化递归骨架 + 3D 冠包络体 + 骨架感知叶填充替代了简单的"直杆+圆盘"树形。

### 架构

```
TreeMorphology (集成入口)
  ├─ generateSkeleton(MorphologyParams, seed)
  │     └─→ SkeletonGenerator.generate()
  │           ├─ 主干 (trunk): 倾斜角 + 噪声扰动
  │           ├─ 一级枝 (primary): 径向角 + 上偏角 + 水平扰动
  │           ├─ 二级枝 (secondary): 从一级枝中段分出
  │           └─ 细枝 (twig): 末端细化
  │
  ├─ planStages(skeleton, totalStages)
  │     └─ 将骨架节点分配到各生长阶段
  │
  └─ growStage(skeleton, stage, stagePlan, level, animator)
        ├─ 放置当前阶段原木 (TRUNK/PRIMARY/SECONDARY 节点)
        └─→ LeafFiller.fillLeaves()
              ├─ 遍历包围盒候选位置
              ├─ CanopyEnvelope 密度检查
              ├─ 骨架距离计算 (Chebyshev 到最近骨架节点)
              ├─ 噪声概率过滤
              └─ 按距离排序，每阶段上限 50 树叶
```

### SkeletonGenerator

参数化递归分枝生成：

- **主干**: 从 sapling 位置向上，可配置倾斜角度（金合欢 10-25°）和噪声扰动（橡树微倾，云杉垂直）
- **一级枝**: 从主干特定高度范围分出，径向均匀分布 + 上偏角 + 水平随机扰动
- **二级枝**: 从一级枝中段分出（仅大冠树种如丛林），偏离主枝方向 30°-60°
- **2x2 树干**: 每层 4 个节点，父子索引正确对应上一层角落节点

### CanopyEnvelope — 5 种冠形

| 冠形 | 适用树种 | 特征 |
|------|---------|------|
| `ELLIPSOID` | 橡树 | 扁椭球 (宽 > 高) |
| `TALL_ELLIPSOID` | 白桦 | 细长椭球 (高 > 宽) |
| `CONE` | 云杉 | 圆锥形，底部宽顶部尖 |
| `CLUSTERED_ELLIPSOID` | 丛林 | 大椭球 + 4 个子冠 |
| `FLAT_CYLINDER` | 深色橡树 | 扁圆柱，高密度 |
| `FLAT_DISC_CLUSTERED` | 金合欢 | 扁碟 + 散落球簇 |

所有冠形支持边缘羽化（feathering），密度从中心向外递减。

### LeafFiller

骨架感知叶块放置策略：

1. 计算包围盒 (AABB)，遍历每个候选位置
2. 排除非空气/非树叶方块
3. 包络体密度 > 阶段阈值 → 继续
4. 计算到最近骨架节点 (TRUNK/PRIMARY/SECONDARY) 的 Chebyshev 距离
5. 概率 = branchProximity × density × edgeFactor × clustering + noise
6. 按距离排序（优先放置近骨架位置）
7. 每阶段上限 50 树叶

### MorphologyParams

每个树种的形态参数 record，工厂方法：

```java
MorphologyParams.oak()     // 橡树
MorphologyParams.birch()   // 白桦
MorphologyParams.spruce()  // 云杉
MorphologyParams.jungle()  // 丛林
MorphologyParams.darkOak() // 深色橡树
MorphologyParams.acacia()  // 金合欢
```

## BlockDisplay 生长动画

树生长阶段不瞬间放置方块，而是生成临时 `BlockDisplay` 实体播放平滑缩放动画。

### 动画风格

| 风格 | 方块类型 | 起始缩放 | 目标缩放 | 持续时间 |
|------|---------|---------|---------|---------|
| `TRUNK_EXTRUDE` | 原木 | (0.9, 0.05, 0.9) | (1.0, 1.0, 1.0) | 15 tick |
| `LEAF_INFLATE` | 树叶 | (0.05, 0.05, 0.05) | (1.08, 1.08, 1.08) | 20 tick |
| `LEAF_CLUSTER` | 顶部树叶 | (0.05, 0.05, 0.05) | (1.05, 1.05, 1.05) | 12 tick |

### 实现原理

- 两帧式触发：第 0 tick 生成实体（初始缩放 + 插值 0），第 1 tick 设置目标缩放 + 插值持续时间
- 利用 Minecraft 内置 Display 变换插值系统，服务端设置 `interpolation_duration` 后客户端自动平滑过渡
- 使用 Java 反射访问 `Display` 的私有 `EntityDataAccessor` 字段
- 实体意外消失时自动补放真实方块

### 组件

| 类 | 位置 | 职责 |
|----|------|------|
| `BlockDisplayAnimator` | `plant/tree/animation/` | 服务端：生成 BlockDisplay、设置缩放、完成替换 |
| `AnimationStyle` | `plant/tree/animation/` | 动画参数枚举 |
| `ClientGrowthAnimationManager` | `client/growth/` | 客户端：接收动画同步 |
| `BlockRenderDispatcherMixin` | `mixin/client/` | 抑制被追踪方块的 vanilla 渲染（缩放不为 1.0 时） |

## Mixin 层

| Mixin | 目标 | 作用 |
|-------|------|------|
| `SaplingBlockMixin` | `SaplingBlock.advanceTree()` | 拦截被追踪树苗的瞬间生长，改为创建 TreeGrowthSession |
| `MushroomBlockMixin` | 蘑菇生长 | 拦截被追踪蘑菇的瞬间生长 |
| `BlockRenderDispatcherMixin` | 方块渲染 | 抑制被追踪方块（缩放不为 1.0）的 vanilla 渲染 |

## 如何添加新树种

1. 在 `plant/tree/profiles/` 创建 `XxxGrowthProfile.java` 实现 `TreeGrowthProfile`
2. 实现必需方法：`heightRange()`, `logBlock()`, `leavesBlock()`, `stageCount()`, `ticksPerStage()`, `morphologyParams()`
3. 在 `MorphologyParams` 中添加 `xxx()` 工厂方法，配置骨架参数和冠形
4. 在 `TreeGrowthHandler` 中注册：`registerProfile("xxx", new XxxGrowthProfile())`
5. 确保对应的 `SaplingAdapter` 能匹配该树种的树苗方块
