# 树木形态系统设计

本文档是 Ecoflux 新树木形态系统的完整设计。目标是取代当前单调的"直杆+几何圆盘"树形，实现符合现实世界生长规律、形态多样化、自然美观的渐进式树木生长。

---

## 1. 现状与问题

### 当前树形生成方式

每个 `TreeGrowthProfile.growStage()` 按阶段执行：

```
树干阶段 (stage 0 ~ height-1):
  → 放置 1 格垂直原木
  → 围绕该原木放置水平树叶圆盘（方形半径）
  
树冠阶段 (stage height ~ total-1):
  → 在树干顶部堆叠树叶圆盘
  → 每层半径由数学函数决定
```

### 核心问题

| 问题 | 表现 |
|------|------|
| **树干僵直** | 永远垂直一根杆，无弯曲、无倾斜、无粗细变化 |
| **枝干固定** | 偶有水平 1-2 格原木延伸，方向随机、角度生硬、无次级分枝 |
| **树叶稀疏** | 每层只有薄薄一圈水平圆盘，覆盖率低，树冠像"叠盘子" |
| **几何感强** | 树叶放置基于方形半径（Chebyshev/Manhattan），外观方正不自然 |
| **无过渡** | 生长过程中每个阶段形态不连贯，早期阶段就是一格原木突兀竖立 |
| **树种雷同** | 除高度和半径参数不同外，所有树的基本结构一模一样 |

---

## 2. 设计目标

1. **自然分支结构** — 主千→一级分枝→二级分枝→末端细枝，符合真实树木拓扑
2. **有机树冠形态** — 树叶填充在 3D 包络体内，而非堆叠 2D 圆盘。边缘羽化，内部有疏密变化
3. **树种强辨识度** — 6 个树种有截然不同的形态特征（轮廓、分枝角度、冠形、疏密）
4. **实例级变异** — 同树种每棵树形态不同（位置确定性噪声驱动）
5. **生长过程自然** — 任何阶段看起来都像一棵"正在长大的树"，而非残缺几何体
6. **性能可控** — 单树阶段放置方块数限制在合理范围（~50 blocks/stage max）
7. **兼容现有架构** — 不改变 `TreeGrowthProfile` 接口签名，算法内聚在 profile 实现中

---

## 3. 总体方案：三阶段骨骼+包络体填充

### 整体架构

```
┌─────────────────────────────────────────────────────┐
│                  TreeMorphology                      │
│  新类: plant/tree/morphology/TreeMorphology.java     │
│  职责: 骨架生成 + 树叶填充 + 阶段离散化               │
└─────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
   TreeSkeleton     CanopyEnvelope    LeafFiller
   (骨架数据结构)    (冠形数学函数)    (树叶放置策略)
```

### 3.1 TreeSkeleton（骨架数据结构）

一棵树的完整骨架由节点和段组成：

```java
public class TreeSkeleton {
    // 所有骨架节点（位置 + 类型 + 半径）
    List<SkeletonNode> nodes;
    // 根节点索引（树苗位置）
    int rootIndex;
    // 树干路径（从根到顶的一系列节点索引）
    List<Integer> trunkPath;
    // 一级分枝根节点索引
    List<Integer> primaryBranches;
}
```

**SkeletonNode**：
```java
public record SkeletonNode(
    BlockPos pos,           // 世界坐标
    NodeType type,          // TRUNK / PRIMARY_BRANCH / SECONDARY_BRANCH / TWIG
    float radius,           // 该节点处原木/树枝的视觉半径（用于树叶距离计算）
    int parentIndex,        // 父节点索引 (-1 = 根)
    int depth               // 从根到该节点的段数
) {}
```

**NodeType 说明**：
- `TRUNK` — 主千节点，用原木方块表示
- `PRIMARY_BRANCH` — 一级分枝（从主干分出），用原木方块
- `SECONDARY_BRANCH` — 二级分枝（从一级分出），用原木方块
- `TWIG` — 末端细枝，不放原木方块，仅作为树叶附着点

### 3.2 骨架生成算法

采用 **参数化递归分枝 + 噪声扰动** 替代空间殖民算法（后者计算量大且难以分阶段）。

**主千生成**：
```
输入: 树苗位置, 解析高度 H, 树种参数, RandomSource
输出: trunkPath (List<BlockPos>)

for y in 1..H:
    basePos = 上一节点位置
    leanAngle = 树种最大倾角 × noise(seed, y)
    leanDir = 随机水平方向（金合欢大幅倾斜，橡树微倾，云杉垂直）
    nextPos = basePos.above(1) + leanOffset(leanAngle, leanDir)
    trunkPath.add(nextPos)
```

**一级分枝生成**：
```
输入: trunkPath, 树种参数, RandomSource
输出: primaryBranches (List<List<BlockPos>>)

分枝起始高度范围: [H*0.3, H*0.8]（树干下 30% 通常无分枝）
分枝数量: 树种参数决定 (橡树 3-5, 云杉 8-15, 白桦 0-2, 金合欢 2-4)
分枝角度: 从树干向上偏转 30°-60°（水平=90°，垂直=0°）
分枝长度: H*0.3 ~ H*0.6，随高度递减（顶部分枝短，底部长）

for each branch:
    startNode = 从 trunkPath 随机选取（偏向中上段）
    direction = 径向方向 + 上偏角度 + 水平扰动
    length = 基础长度 × noise 系数
    for step in 1..length:
        nextPos = 上一节点 + direction + 小噪声扰动
        branchPath.add(nextPos)
    primaryBranches.add(branchPath)
```

**二级分枝生成**（仅大冠树种：橡树、丛林、深色橡树）：
```
for each primaryBranch mid-to-tip segment:
    if noise < 二级分枝概率 (0.3-0.5):
        从该节点分出 1-2 个二级分枝
        方向: 偏离主枝方向 30°-60°
        长度: 主枝剩余长度的 0.3-0.5
```

### 3.3 CanopyEnvelope（树冠包络体）

每种树定义一个 3D 包络体函数 `canopyDensity(x, y, z) → [0, 1]`。

内部 1.0 = 必定有树叶，边缘 0.0 = 无树叶，中间过渡 = 概率递减（边缘羽化）。

**橡树 — 扁椭球体**：
```
center = trunkTop + (0, canopyHeight*0.3, 0)  // 中心略低于顶部
rx, rz = H * 0.45  // 水平半径（宽冠）
ry = H * 0.35      // 垂直半径（扁冠）
density = 1.0 - (dx²/rx² + dy²/ry² + dz²/rz²)
边缘羽化: 0.8 < density < 1.0 → 线性衰减概率
```

**白桦 — 细长椭球体**：
```
center = trunkTop + (0, -2, 0)  // 中心在顶部下方 2 格
rx, rz = 1.8   // 窄冠
ry = 4.0       // 高冠
density = 1.0 - (dx²/rx² + dy²/ry² + dz²/rz²)
树叶集中在顶部 1/3 高度，下部为裸露树干
```

**云杉 — 圆锥体**：
```
foliageStart = trunkBase + (0, clearTrunk, 0)  // 冠起始位置 (clearTrunk = 2-3)
tipY = trunkTop.y + 1
baseRadius = H * 0.35
for y in foliageStart.y .. tipY:
    ratio = (y - foliageStart.y) / (tipY - foliageStart.y)
    radius = baseRadius * (1.0 - ratio)
    全密度 → 无羽化（云杉冠边界清晰）
```

**丛林 — 大椭球 + 多个子冠**：
```
主冠: 同橡树椭球但更大 (rx=H*0.5, ry=H*0.4)
子冠: 3-5 个小型椭球附着在一级分枝末端
每个子冠半径 1.5-2.5
```

**深色橡树 — 扁圆柱 + 密实填充**：
```
rx, rz = 2.5（围绕 2x2 主干）
ry = 4.0（冠厚）
density = 核心区 1.0，边缘陡降
几乎无羽化 → 深色橡树冠密实
```

**金合欢 — 扁平碟形 + 散落簇群**：
```
主冠: rx=3.5, ry=1.5（扁碟，位于顶部）
子簇: 3-6 个独立的球形小簇（r=1.5-2.5）
      散布在一级分枝末端 2 格范围内
簇间有空隙 → 稀疏外观
```

### 3.4 LeafFiller（树叶放置策略）

基于骨架和包络体，分层次放置树叶：

**核心规则**：
1. 只在冠包络体密度 > 0 的位置放置树叶
2. 优先在骨架节点周围聚集（枝端浓密，枝间稀疏）
3. 树叶 DISTANCE 基于到最近骨架节点的距离（而非到树干）
4. 每阶段放置上限 50-80 个树叶方块

**放置算法**：
```
for each 候选位置 (x,y,z) in 当前阶段扩展区域:
    // 1. 包络体检查
    density = canopyDensity(x, y, z)
    if density <= 0: continue
    
    // 2. 距离骨架检查
    distToSkeleton = min distance to any skeleton node
    if distToSkeleton > maxLeafDistance: continue
    
    // 3. 概率计算
    branchProximity = 1.0 / (1.0 + distToSkeleton)  // 离骨架越近概率越高
    noise = positionNoise(seed, x, y, z)           // 确定性噪声 [0,1)
    edgeFactor = density < 0.3 ? (density / 0.3) : 1.0  // 边缘衰减
    
    prob = branchProximity * density * edgeFactor * 0.9 + noise * 0.1
    
    // 4. 放置判定
    if noise < prob:
        placeLeaf(x, y, z, distance=ceil(distToSkeleton))
```

**树叶 DISTANCE 改进**：
当前 `computeLeafDistance` 基于到树干原木的 Chebyshev 距离。改进为计算到最近骨架节点（TRUNK/PRIMARY/SECONDARY 类型节点，不含 TWIG）的距离：

```
leafDistance = min(Chebyshev_distance(leafPos, node.pos) for node in skeletonNodes where node.type != TWIG)
```

这样即使树叶离主干很远，只要靠近分枝就会得到小的 DISTANCE 值，不会腐烂。

### 3.5 生长阶段离散化

骨架生成是一次性的（在 session 创建时），阶段离散化控制哪些骨架节点和树叶在哪个阶段出现。

**阶段分配策略**：

```
树干阶段 (stage 0 ~ H-1):
  stage 0: 放置 trunkPath[0]（树苗上方第 1 格原木）
  stage 1: 放置 trunkPath[1] + 该高度附近的 1 级枝起始段
  stage 2: 放置 trunkPath[2] + 该高度附近枝的延伸段
  ...
  stage H-1: 放置 trunkPath[H-1] + 树冠起始层树叶

树干+枝阶段 (stage H ~ totalStages-2):
  继续延伸 1 级分枝
  开始出现 2 级分枝
  树叶覆盖率逐渐增加

树冠完善阶段 (最后 2 阶段):
  完成所有枝的末端段
  树叶填充到完整密度
  末端细枝 (TWIG) 全部出现
```

每个阶段的方块放置顺序：
1. 原木方块（主千 + 各级分枝段）
2. 树叶方块（按 LeafFiller 策略）

---

## 4. 各树种形态规格

### 4.1 橡树 (Oak)

| 参数 | 值 |
|------|-----|
| 高度范围 | 6-10 |
| 主千倾角 | ≤5°（微倾） |
| 一级分枝数 | 3-5 |
| 分枝起始高度 | H×0.35 ~ H×0.75 |
| 分枝上偏角度 | 40°-65° |
| 一级分枝长度 | H×0.35 ~ H×0.6 |
| 二级分枝 | 有（概率 0.4，长度 2-3） |
| 末端细枝 | 有 |
| 冠形 | 扁椭球，中心略偏下 |
| 冠水平半径 | H×0.45 |
| 冠垂直半径 | H×0.35 |
| 树叶密度 | 中等（羽化带宽 20%） |
| 树叶聚集度 | 中等（枝端密，枝间疏） |
| 生长时间 | 3600 tick/阶段 (~27 min) |

**轮廓特征**：宽大于高，扁球形。主干粗壮，分枝向四周均匀展开。树冠底部平坦，顶部圆润。

### 4.2 白桦 (Birch)

| 参数 | 值 |
|------|-----|
| 高度范围 | 7-12 |
| 主千倾角 | ≤3°（几乎垂直） |
| 一级分枝数 | 0-2（很少） |
| 分枝起始高度 | H×0.7 ~ H×0.85（仅在顶部） |
| 分枝上偏角度 | 60°-80°（细枝上扬） |
| 一级分枝长度 | H×0.15 ~ H×0.25（短枝） |
| 二级分枝 | 无 |
| 末端细枝 | 有（多而细，下垂倾向） |
| 冠形 | 细长椭球，集中在顶部 1/3 |
| 冠水平半径 | 1.8 |
| 冠垂直半径 | 4.0-5.0 |
| 树叶密度 | 稀疏（羽化带宽 30%） |
| 树叶聚集度 | 强（紧密聚集在少数枝周围） |
| 生长时间 | 2400 tick/阶段 (~20 min) |

**轮廓特征**：细高，树干银白裸露，仅顶部 1/3 处有一簇细枝和树叶。枝细而上扬，叶小而疏。远看像一根白色杆子顶一团淡绿云。

### 4.3 云杉 (Spruce)

| 参数 | 值 |
|------|-----|
| 高度范围 | 10-18 |
| 主千倾角 | 0°（完全垂直） |
| 一级分枝数 | H-3（几乎每层都有枝，底部 2-3 层无枝） |
| 分枝起始高度 | 第 3 层开始 |
| 分枝上偏角度 | 15°-30°（略上偏，近乎水平） |
| 一级分枝长度 | 底部 H×0.4 递减到顶部 0 |
| 二级分枝 | 无 |
| 末端细枝 | 无（枝末端即是叶附着点） |
| 冠形 | 圆锥体 |
| 冠底部半径 | H×0.35 |
| 树叶密度 | 浓密（无羽化，边界清晰） |
| 树叶聚集度 | 均匀（沿枝全长均匀分布） |
| 生长时间 | 4800 tick/阶段 (~48 min) |

**轮廓特征**：标准圣诞树形。主干笔直，每层有向四周水平展开的枝，枝上密布树叶。底部最宽，向顶部逐渐收窄成尖。底部 2-3 格为裸露树干。

### 4.4 丛林树 (Jungle)

| 参数 | 值 |
|------|-----|
| 高度范围 | 12-18 |
| 主千 | 2×2 |
| 主千倾角 | ≤3° |
| 一级分枝数 | 5-8 |
| 分枝起始高度 | H×0.5 ~ H×0.85（上半部） |
| 分枝上偏角度 | 30°-55° |
| 一级分枝长度 | H×0.4 ~ H×0.7（长枝） |
| 二级分枝 | 有（概率 0.5，长度 2-4） |
| 末端细枝 | 有 |
| 冠形 | 大椭球主冠 + 3-5 个子冠（附着在分枝末端） |
| 冠水平半径 | H×0.5（主冠） |
| 冠垂直半径 | H×0.4（主冠） |
| 子冠半径 | 1.5-2.5 |
| 树叶密度 | 中等偏密 |
| 树叶聚集度 | 簇状（主冠 + 子冠聚集，冠间有间隙） |
| 生长时间 | 4800 tick/阶段 (~64 min) |

**轮廓特征**：高大威猛。2×2 粗干支撑广阔树冠。主冠在上半部，几个子冠像"云朵"附着在长枝末端，整体轮廓不规则。

### 4.5 深色橡树 (Dark Oak)

| 参数 | 值 |
|------|-----|
| 高度范围 | 7-11 |
| 主千 | 2×2 |
| 主千倾角 | ≤2° |
| 一级分枝数 | 4-6 |
| 分枝起始高度 | H×0.3 ~ H×0.7 |
| 分枝上偏角度 | 35°-55° |
| 一级分枝长度 | H×0.3 ~ H×0.5 |
| 二级分枝 | 有（概率 0.3，长度 2-3） |
| 末端细枝 | 有 |
| 冠形 | 扁圆柱（密实） |
| 冠水平半径 | 2.5（围绕 2×2 主干） |
| 冠垂直半径 | 4.0 |
| 树叶密度 | 极密（几乎无羽化） |
| 树叶聚集度 | 均匀密实 |
| 生长时间 | 3600 tick/阶段 (~30 min) |

**轮廓特征**：矮壮密实。2×2 粗干，树冠宽大扁平，树叶极其浓密几乎不透光。分枝粗壮，隐藏在浓密叶丛中。

### 4.6 金合欢 (Acacia)

| 参数 | 值 |
|------|-----|
| 高度范围 | 6-11 |
| 主千倾角 | 10°-25°（明显倾斜） |
| 一级分枝数 | 2-4 |
| 分枝起始高度 | H×0.5 ~ H×0.8 |
| 分枝上偏角度 | 20°-50°（变化大） |
| 一级分枝长度 | H×0.3 ~ H×0.5 |
| 二级分枝 | 有（概率 0.3，长度 2） |
| 末端细枝 | 有 |
| 冠形 | 扁平碟形主冠 + 3-6 个球形散落子簇 |
| 冠水平半径 | 3.5（碟形） |
| 冠垂直半径 | 1.5（很扁） |
| 子簇半径 | 1.5-2.5 |
| 树叶密度 | 稀疏（羽化带宽 40%） |
| 树叶聚集度 | 极簇状（簇间大面积空隙） |
| 生长时间 | 3600 tick/阶段 (~27 min) |

**轮廓特征**：草原标志。主干明显倾斜（有时呈 S 形），顶部突然展开成扁平碟形冠。另有几团散落的球形叶簇挂在分枝末端，整体轮廓通透多孔。

---

## 5. 与现有系统集成

### 5.1 新类结构

```
plant/tree/
├── TreeGrowthProfile.java        (不变)
├── TreeGrowthHandler.java        (微调）
├── TreeGrowthSession.java        (新增: 存储 TreeSkeleton 引用)
├── TreeShapeUtils.java           (保留: 通用噪声和方块放置函数)
├── morphology/
│   ├── TreeMorphology.java       (新建: 骨架+冠+填充 总入口)
│   ├── TreeSkeleton.java         (新建: 骨架数据结构)
│   ├── SkeletonNode.java         (新建: 骨架节点 record)
│   ├── SkeletonGenerator.java    (新建: 参数化递归分枝生成)
│   ├── CanopyEnvelope.java       (新建: 各树种 3D 冠形函数集)
│   ├── LeafFiller.java           (新建: 树叶放置策略)
│   └── MorphologyParams.java     (新建: 形态参数 record，供各 profile 定义)
└── profiles/
    ├── OakGrowthProfile.java     (重写: 使用 TreeMorphology)
    ├── BirchGrowthProfile.java   (重写)
    ├── SpruceGrowthProfile.java  (重写)
    ├── JungleGrowthProfile.java  (重写)
    ├── DarkOakGrowthProfile.java (重写)
    └── AcaciaGrowthProfile.java  (重写)
```

### 5.2 工作流

```
Session 创建时:
  1. resolveHeight() → 确定高度
  2. SkeletonGenerator.generate(params, height, random) → 生成完整骨架
  3. 骨架存入 TreeGrowthSession.skeleton
  4. 阶段总数 = 骨架节点按阶段分组的组数

每个 growStage() 调用:
  1. 从 session.skeleton 中取出当前阶段对应的节点组
  2. 放置该组的原木方块（TRUNK/PRIMARY/SECONDARY 节点）
  3. 调用 CanopyEnvelope.density() 确定当前阶段树叶候选区域
  4. 调用 LeafFiller.fill() 放置该阶段树叶
```

### 5.3 TreeGrowthProfile 接口

现有接口保持不变，每个 profile 内部持有 `MorphologyParams` 实例，`growStage()` 委托给 `TreeMorphology.growStage()`。

```java
// MorphologyParams — 每个树种定义自己的形态参数
public record MorphologyParams(
    int minTrunkHeight,
    int maxTrunkHeight,
    int ticksPerStage,
    Block logBlock,
    Block leavesBlock,
    boolean is2x2,
    
    // 主千参数
    float maxLeanAngle,        // 最大倾角（度）
    
    // 分枝参数
    int minBranches,           // 最少一级分枝数
    int maxBranches,           // 最多一级分枝数
    float branchStartRatio,    // 分枝起始高度比例 (0-1)
    float branchEndRatio,      // 分枝结束高度比例 (0-1)
    float branchAngleMin,      // 分枝最小上偏角（度）
    float branchAngleMax,      // 分枝最大上偏角（度）
    float branchLengthRatio,   // 分枝长度相对于树高的比例
    boolean hasSecondary,      // 是否有二级分枝
    float secondaryProb,       // 二级分枝概率
    int secondaryLength,       // 二级分枝长度
    
    // 冠形参数
    CanopyType canopyType,     // ELLIPSOID / CONE / FLAT_DISC / CLUSTERED
    float canopyRadiusXZ,      // 冠水平半径系数
    float canopyRadiusY,       // 冠垂直半径系数
    float canopyCenterYBias,   // 冠中心垂直偏移（正=偏上）
    float leafDensity,         // 树叶密度 (0-1)
    float edgeFeather,         // 边缘羽化带宽 (0-1)
    float branchClustering,    // 树叶沿枝聚集度 (0-1)
    
    // 子簇参数（仅 CLUSTERED 类型）
    int subClusters,           // 子簇数量
    float subClusterRadius     // 子簇半径
) {}
```

---

## 6. 实现路线

### Phase 1: 骨架系统 (P0)

- [ ] `SkeletonNode.java` — 节点 record
- [ ] `TreeSkeleton.java` — 骨架数据结构
- [ ] `SkeletonGenerator.java` — 实现：主千生成 + 一级分枝 + 二级分枝
- [ ] 单元测试：不同种子生成不同骨架，同种子可复现
- [ ] 裸骨架可视化（仅原木放置，无树叶）

### Phase 2: 冠形与填充 (P0)

- [ ] `CanopyEnvelope.java` — 5 种冠形数学函数
- [ ] `LeafFiller.java` — 骨架距离 + 包络体密度 + 噪声 综合判定
- [ ] 树叶 DISTANCE 改为到最近骨架节点距离
- [ ] 测试：橡树冠自然填充

### Phase 3: 阶段离散化 (P0)

- [ ] `MorphologyParams.java` — 参数 record
- [ ] `TreeMorphology.java` — 整合入口：骨架 → 阶段分组 → 逐个 growStage
- [ ] 修改 `TreeGrowthSession`：新增 `skeleton` 字段（transient，不序列化）
- [ ] 阶段分组算法：按高度和深度将骨架节点分配到各阶段

### Phase 4: 各树种接入 (P1)

- [ ] `OakGrowthProfile` → 使用 `MorphologyParams.oak()`
- [ ] `BirchGrowthProfile` → 使用 `MorphologyParams.birch()`
- [ ] `SpruceGrowthProfile` → 使用 `MorphologyParams.spruce()`
- [ ] `JungleGrowthProfile` → 使用 `MorphologyParams.jungle()`
- [ ] `DarkOakGrowthProfile` → 使用 `MorphologyParams.darkOak()`
- [ ] `AcaciaGrowthProfile` → 使用 `MorphologyParams.acacia()`

### Phase 5: 优化与打磨 (P2)

- [ ] 树叶方块放置数量限制（per stage cap）
- [ ] 性能基准测试（50 棵树同时生长）
- [ ] 边界检查（树冠不超出世界高度）
- [ ] 2×2 树的骨架适配（主干路径为 2×2 而非 1×1）
- [ ] 区块卸载时骨架不持久化（下次加载从 seed 重建）

---

## 7. 性能考虑

| 指标 | 目标 |
|------|------|
| 骨架生成时间 | < 1ms（session 创建时执行一次） |
| 单阶段方块放置上限 | 50 个（原木+树叶） |
| 树叶放置候选遍历范围 | 当前阶段扩展的 AABB 包围盒 |
| 树叶概率计算 | O(N) 其中 N ≤ 200（候选位置数） |
| 骨架距离计算 | 预计算距离场或使用 kd-tree 加速 |

**优化策略**：
- 骨架生成仅在 session 创建时执行一次，结果缓存在 `TreeGrowthSession` 中
- 树叶放置时计算 AABB 包围盒，只遍历盒内位置
- 骨架节点数 ≤ 200，计算最近距离用暴力遍历即可（无需 kd-tree）
- 位置确定性噪声确保同位置重建时得到相同树形

---

## 8. 与原版/其他模组兼容

- **`gradualTreeGrowth = false`**：骨架系统完全不激活，Mixin 不拦截，原版瞬间生成
- **与其他模组树苗**：只有 Ecoflux 追踪的树苗才会触发 Mixin。未追踪树苗保持原版行为
- **区块卸载/重载**：骨架不持久化。下次 `advanceTree()` 触发时，同一位置+同一种子重建出相同骨架

---

## 9. 参考

- Runions et al. "Modeling Trees with a Space Colonization Algorithm" (2007)
- Dynamic Trees mod — 分枝结构和生长逻辑参考
- Minecraft 原版 `FoliagePlacer` / `TreeFeature` — 树叶放置和 DISTANCE 计算参考
- `docs/tree-lifecycle-implementation.md` — 当前树生命周期实现
