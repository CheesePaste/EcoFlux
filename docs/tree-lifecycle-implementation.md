# 树生命周期系统实现方案

本文档是 Ecoflux 树生命周期（树苗→生长→成熟→衰老→死亡）的完整实现方案。
目标：**废除 MC 原版树苗瞬间长成树的机制，改为 Ecoflux 自己控制的分阶段渐进生长。**

## 参考：Dynamic Trees 的核心设计

Dynamic Trees 的做法（不直接照搬，但借鉴核心思路）：

1. **取消原版瞬间生长** — 替换 SaplingBlock，树苗不再通过原版 random tick 瞬间生成整棵树
2. **渐进式生长** — 每次 random tick 生长一步（加一层原木/一片树叶），直到成熟
3. **物种差异化算法** — 橡树、白桦、云杉各有自己的生长逻辑（形态、速度不同）
4. **根系土壤 (Rooty Soil)** — 树苗种下后下方方块变成 "根系土壤"，作为生长基础
5. **种子循环** — 成熟树随机掉落种子，种子自种 → 新树生长 → 森林蔓延
6. **光照竞争** — 多棵树靠近时争夺阳光，长得更高更瘦

## Ecoflux 的实现路线

我们不走 Dynamic Trees 那么重的路线（替换全部方块、自定义模型），而是：
- **保留原版方块**（原木、树叶、树苗都是原版）
- **接管生长控制权** — Mixin 拦截 `SaplingBlock.advanceTree()`，由 Ecoflux 决定何时、如何生长
- **分阶段放置方块** — 按树种类型，每 N tick 放一层树干+树叶，模拟渐进生长
- **融合进现有演替系统** — 树生命周期为 succession 提供积分、触发 biome 演替

---

## Phase 1: Mixin 拦截原版树苗生长

### 目标
当树苗被 Ecoflux 追踪后，阻止原版的瞬间生长。未被追踪的树苗保持原版行为。

### 需要新增的文件

```
src/main/java/com/s/ecoflux/mixin/
  └── SaplingBlockMixin.java

src/main/resources/ecoflux.mixins.json  (修改，添加 mixin 条目)
```

### 1.1 Mixin: `SaplingBlockMixin`

Mixin 目标：`net.minecraft.world.level.block.SaplingBlock.advanceTree(ServerLevel, BlockPos, BlockState, RandomSource)`

逻辑：
1. 获取树苗位置的 `LevelChunk`
2. 检查 `SuccessionChunkData` 中该位置是否已被追踪
3. 如果已追踪 → **取消原版生长**（`ci.cancel()`），改为调用 Ecoflux 的 `TreeGrowthHandler.tryStartGrowth()`
4. 如果未追踪 → 走原版逻辑（不做任何事，让原版继续）

```java
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin {

    @Inject(
        method = "advanceTree(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ecoflux$interceptSaplingGrowth(
            ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
            CallbackInfo ci) {
        // 只拦截被 Ecoflux 追踪的树苗
        LevelChunk chunk = level.getChunkAt(pos);
        SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = data.getVegetationRecords().get(pos);
        if (record != null && record.adapterType().equals(SaplingAdapter.TYPE_ID)) {
            ci.cancel();
            TreeGrowthHandler.INSTANCE.tryStartGrowth(level, pos, record);
        }
    }
}
```

### 1.2 需要确认的技术点

- NeoForge 1.21.1 中 `SaplingBlock.advanceTree()` 的准确方法签名
- `SuccessionChunkData.getVegetationRecords()` 返回的 Map 是否包含 `get(pos)` 方法
- 如果是通过 `BlockGrowFeatureEvent` 这种 NeoForge 事件能拦截，优先用事件而不是 Mixin

---

## Phase 2: 渐进式树木生长系统

### 目标
当 Ecoflux 决定让树苗开始生长后，不一次性生成整棵树，而是分阶段逐步放置方块。

### 需要新增/修改的文件

```
src/main/java/com/s/ecoflux/plant/tree/
  ├── TreeGrowthHandler.java        (单例，生长入口 + tick 驱动)
  ├── TreeGrowthSession.java        (每棵正在生长的树的运行时状态)
  ├── TreeGrowthProfile.java        (按树种定义的生长参数)
  └── profiles/
       ├── OakGrowthProfile.java
       ├── BirchGrowthProfile.java
       ├── SpruceGrowthProfile.java
       └── JungleGrowthProfile.java

src/main/java/com/s/ecoflux/plant/
  └── TreeStructureAdapter.java     (修改，增加 DEAD 阶段)
```

### 2.1 TreeGrowthSession

每棵正在生长的树一个实例，存储在 `SuccessionChunkData` 中或独立管理。

```java
public final class TreeGrowthSession {
    private final BlockPos saplingPos;       // 原始树苗位置
    private final ResourceLocation treeType; // 树种 (oak/birch/spruce/...)
    private final long growthStartTime;
    private final int totalStages;           // 总生长阶段数
    private int currentStage;                // 当前阶段 (0 = 未开始)
    private long lastStageTime;              // 上次阶段推进的 gameTime
    private final Set<BlockPos> placedBlocks; // 已放置的方块位置集合
    private final TreeGrowthProfile profile;
    
    // ... constructor, toTag(), fromTag() for NBT persistence
}
```

### 2.2 TreeGrowthHandler

单例，负责：
- 维护 `Map<BlockPos, TreeGrowthSession>` activeGrowths
- 每个 chunk tick 调用 `tickGrowth(ServerLevel, BlockPos)`
- 每到达阶段间隔就调用 `TreeGrowthProfile.growStage()` 放置下一层方块
- 生长完成后清理 session，通知 `VegetationTracker` 树木已成熟

```java
public final class TreeGrowthHandler {
    public static final TreeGrowthHandler INSTANCE = new TreeGrowthHandler();
    
    private final Map<BlockPos, TreeGrowthSession> activeGrowths = new HashMap<>();
    private final Map<ResourceLocation, TreeGrowthProfile> profiles = new HashMap<>();
    
    // 由 Mixin 调用
    public void tryStartGrowth(ServerLevel level, BlockPos pos, ActiveVegetationRecord record);
    
    // 由 SuccessionChunkData 的 tick 驱动
    public void tickGrowth(ServerLevel level, BlockPos pos);
    
    // 获取某位置正在进行中的生长 session
    public Optional<TreeGrowthSession> getSession(BlockPos pos);
}
```

### 2.3 TreeGrowthProfile（树种生长参数）

每种树定义不同的阶段参数：

```java
public interface TreeGrowthProfile {
    ResourceLocation treeType();
    
    /** 总阶段数（决定了树的高度等于阶段数层） */
    int totalStages();
    
    /** 每阶段之间的 tick 间隔 */
    int ticksPerStage();
    
    /** 每阶段需要满足的条件（光照、空间等） */
    boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int stage);
    
    /** 执行一个阶段的生长：放置这一层的原木和树叶 */
    void growStage(ServerLevel level, BlockPos saplingPos, int stage, Set<BlockPos> placedBlocks);
    
    /** 生长完成后的最大生命周期 (tick) */
    long maxLifetime();
    
    /** 成熟后提供的积分 */
    int maturePointValue();
}
```

示例 `OakGrowthProfile`：
```java
public class OakGrowthProfile implements TreeGrowthProfile {
    @Override public ResourceLocation treeType() { return ResourceLocation.withDefaultNamespace("oak"); }
    @Override public int totalStages() { return 5; }
    @Override public int ticksPerStage() { return 2400; } // 2 分钟一阶段，共 10 分钟长成
    @Override public long maxLifetime() { return 576000L; } // 约 8 个 MC 日
    
    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int stage) {
        // 检查上方是否有足够空间
        BlockPos topPos = saplingPos.above(1 + stage * 2);
        return level.getBlockState(topPos).isAir() || level.getBlockState(topPos).is(BlockTags.LEAVES);
    }
    
    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int stage, Set<BlockPos> placedBlocks) {
        int trunkHeight = 1 + stage; // 阶段 0=1 格高树干，阶段 4=5 格高
        // 放置/延伸树干
        BlockPos trunkPos = saplingPos.above();
        for (int y = 0; y < trunkHeight; y++) {
            BlockPos logPos = saplingPos.above(1 + y);
            if (level.getBlockState(logPos).isAir() || level.getBlockState(logPos).is(BlockTags.LEAVES)) {
                level.setBlock(logPos, Blocks.OAK_LOG.defaultBlockState(), 3);
                placedBlocks.add(logPos.immutable());
            }
        }
        // 放置/扩展树冠（树叶）
        placeCanopyLayer(level, saplingPos.above(trunkHeight), stage, placedBlocks);
    }
    
    private void placeCanopyLayer(ServerLevel level, BlockPos centerPos, int stage, Set<BlockPos> placedBlocks) {
        // 按阶段逐步扩大树冠范围
        int radius = Math.min(stage + 1, 3);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) == radius && Math.abs(dz) == radius && level.random.nextFloat() > 0.5) continue;
                BlockPos leafPos = centerPos.offset(dx, 0, dz);
                if (level.getBlockState(leafPos).isAir()) {
                    level.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(LeavesBlock.DISTANCE, Math.min(Math.abs(dx) + Math.abs(dz), 7))
                        .setValue(LeavesBlock.PERSISTENT, false), 3);
                    placedBlocks.add(leafPos.immutable());
                }
            }
        }
    }
}
```

---

## Phase 3: 树木死亡与腐烂

### 目标
树木成熟后经过足够长时间，进入衰老→死亡→腐烂阶段。
死亡不是瞬间消失，而是逐步腐烂（树叶先凋零、原木逐渐消失）。

### 3.1 修改 TreeStructureAdapter

增加 `DEAD` 和 `DECAYING` 阶段：

```java
// VegetationLifecycleStage 需要新增:
// DEAD     - 已死亡（积分归零或为负，即将移除）
// DECAYING - 腐烂中（方块逐步消失）
```

### 3.2 树死亡流程

```
MATURE (288000 tick)
  ↓ 96000 tick 后
AGING (点值从 5 降到 3)
  ↓ 到达 expireGameTime
DEAD (点值为 0)
  ↓ VegetationTracker.observeChunk() 检测到 dead
通知 TreeDecayHandler 开始腐烂
  ↓
DECAYING (树叶先消失，然后原木)
  ↓ 腐烂完成
移除追踪，区块更新
```

### 3.3 TreeDecayHandler

```java
public final class TreeDecayHandler {
    public static final TreeDecayHandler INSTANCE = new TreeDecayHandler();
    
    // 开始一棵树的腐烂过程
    public void startDecay(ServerLevel level, BlockPos treeBasePos, ActiveVegetationRecord record);
    
    // 每次 tick 腐烂一步：随机移除一个树叶/原木
    public void tickDecay(ServerLevel level, BlockPos treeBasePos);
}
```

---

## Phase 4: 与演替系统融合

### 4.1 积分贡献调整

| 阶段 | 当前积分 | 建议调整 |
|------|---------|---------|
| 树苗 JUVENILE | 1 | 1 |
| 树苗 GROWING | 2 | 2 |
| 树木生长中 (TreeGrowthSession) | — | 3 (渐进增长) |
| 树木 MATURE | 5 | 5 |
| 树木 AGING | 3 | 3 |
| 树木 DEAD | — | 0 |

### 4.2 演替触发

- 一棵树苗长成树后，该 chunk 的 vegetation points 会大幅增加 → 更容易触发正向演替
- 多棵树同时成熟 → 加速森林化
- 树死亡腐烂后积分下降 → 可能触发负向演替（如果没有其他植物补充）

### 4.3 配置文件扩展

在 succession path JSON 中增加树的配置：

```json
{
  "path_id": "plains_to_forest",
  "plants": [
    {
      "plant_id": "minecraft:oak_sapling",
      "weight": 10,
      "point_value": 2,
      "max_age": 144000,
      "growth_profile": "ecoflux:oak"
    }
  ],
  "chunk_rules": {
    "tree_growth_multiplier": 1.0,
    "trees_required_for_forest": 3
  }
}
```

---

## Phase 5: 实现顺序

按优先级排列，每个子任务完成后可独立验证：

### Step 1: Mixin 拦截 ✅ 已完成
- [x] 确认 NeoForge 1.21.1 中 SaplingBlock 是否有可用的 Forge 事件 → 无，使用 Mixin
- [x] 编写 `SaplingBlockMixin`
- [x] 注册到 `ecoflux.mixins.json`
- [x] 测试：验证被追踪的树苗不会瞬间生长
- [x] 验证：未被追踪的树苗保持原版行为

### Step 2: 生长会话系统 ✅ 已完成
- [x] 实现 `TreeGrowthSession` + NBT 序列化
- [x] 实现 `TreeGrowthProfile` 接口
- [x] 实现 `OakGrowthProfile`（5 阶段渐进生长）
- [x] 实现 `TreeGrowthHandler.tickAll()` / `onGrowthComplete()` / `resolveProfile()`
- [x] `ModChunkEvents.processTreeGrowth()` 每 20 tick 驱动
- [x] 测试：橡树树苗分 5 阶段渐进长成，树苗→原木替换
- [x] 修复：多维度共享 counter bug → `gameTime % 20`

### Step 3: 多树种支持 ✅ 已完成
- [x] `BirchGrowthProfile`（白桦：细高 6-10 格，椭圆顶冠，2400 tick/阶段，~20 分钟长成）
- [x] `SpruceGrowthProfile`（云杉：锥形 8-15 格，全高覆叶，4800 tick/阶段，~48 分钟长成）
- [x] `JungleGrowthProfile`（丛林：2x2 粗干 10-15 格，宽冠+侧枝，4800 tick/阶段，~64 分钟长成）
- [x] `DarkOakGrowthProfile`（深色橡树：2x2 粗干 6-10 格，密冠，3600 tick/阶段，~30 分钟长成）
- [x] `AcaciaGrowthProfile`（金合欢：斜干 5-10 格，稀疏不规则冠，3600 tick/阶段，~27 分钟长成）
- [x] `OakGrowthProfile` 重写（扁球形宽冠 5-8 格，3600 tick/阶段，~27 分钟长成）
- [x] 树苗方块 → Profile 的自动匹配（6 树种全部注册）
- [x] `TreeShapeUtils` 共享工具类（位置确定性噪声、冠形函数、树叶/原木放置、2x2 检测、枝干生成）
- [x] `TreeGrowthProfile` 接口扩展（高度范围、log/leaves 方块类型、2x2 支持、实例级随机高度）
- [x] `TreeGrowthSession` 新增 `resolvedHeight` 字段 + NBT 持久化
- [x] `TreeGrowthHandler.onGrowthComplete()` 使用 `profile.logBlock()` 替代硬编码 OAK_LOG
- [x] 2x2 检测逻辑（`isValid2x2` + NW 角定位 + 4 个树苗同时替换）
- [x] 开关 `gradualTreeGrowth` 行为不变：false → 原版瞬间生长，true → 渐进生长

### Step 4: 死亡与腐烂 ✅ 预计 3-4h
- [ ] 扩展 `VegetationLifecycleStage` 增加 DEAD/DECAYING
- [ ] 修改 `TreeStructureAdapter.observe()` 增加死亡判定
- [ ] 实现 `TreeDecayHandler`
- [ ] 测试：树从成熟→衰老→死亡→腐烂的完整流程

### Step 5: 演替系统接入 ✅ 预计 2-3h
- [ ] 树生长进度影响 chunk vegetation points
- [ ] 多树成熟触发 forest biome 演替加速
- [ ] JSON 配置文件支持树参数
- [ ] 端到端测试：树苗 → 森林 biome 演替

### Step 6: 客户端视觉效果 ✅ 预计 3-4h
- [ ] 生长中的树苗有视觉反馈（scale 逐渐增大）
- [ ] 衰老的树有枯黄效果
- [ ] 腐烂动画

---

## 关键技术风险

| 风险 | 缓解措施 |
|------|---------|
| NeoForge 没有 SaplingGrowTreeEvent | 使用 Mixin 兜底 |
| 渐进生成方块可能破坏地形 | 只在已有树叶/空气位置放置，不破坏非树方块 |
| 不同树种形态差异大 | 先只做橡树、白桦两个 profile，验证框架可行性后再扩展 |
| 方块放置可能触发原版更新 | 使用 `Block.UPDATE_ALL` flag 的替代，避免不必要的更新链 |
| 区块卸载时生长 session 丢失 | TreeGrowthSession 实现 NBT 序列化，随 ChunkData 持久化 |
| 玩家手动挖掉树苗/树木 | VegetationTracker 的 observe 已有 absent 检测，自然清理 |

---

## 与原版兼容策略

- **未被追踪的树苗**：100% 保持原版行为（Mixin 只在追踪时介入）
- **被追踪但不在 succession path 中的树苗**：仍走原版逻辑
- **玩家用骨粉催熟**：后续可以接入 NeoForge 的 `BonemealEvent`，对追踪树苗取消催熟或改为加速生长阶段
- **与其他模组的树苗**：只要扩展了 `SaplingBlock`，Mixin 就能拦截；适配器通过 `BlockTags.SAPLINGS` 匹配

---

## 后续扩展方向（本次不实现）

- [ ] 树冠竞争（多树靠近时长更高更瘦）
- [ ] 种子自动散播（成熟树掉落种子 → 自动种植）
- [ ] 根系土壤肥力机制
- [ ] Dynamic Trees 完整兼容层
- [ ] 树种间杂交/变异

---

## 已实现阶段的改进想法

### 树形自然化（噪声 + 不规则枝干）

当前 OakGrowthProfile 每阶段放置的树叶是规则的环形（方形半径），树木看起来很人工。改进方向：

1. **噪声去叶** — 在 `growStage()` 放置树叶时，用 `level.random` 或基于位置的 simplex noise 决定每个候选叶子位置是否真正放置。例如 30% 概率跳过叶子放置，形成自然缺口
2. **枝干分叉** — 不是在每阶段只放一根垂直原木，而是在一定阶段（如 stage 3+）有一定概率在侧面生成 1-2 格长的斜向枝干（原木方块），然后在枝干末端放置小簇树叶
3. **树冠形状差异化** — 不同树种用不同噪声参数：橡树偏圆形冠、白桦偏锥形、云杉偏柱形
4. **生长阶段随机波动** — 每阶段的间隔加入 ±20% 的随机浮动，避免多棵树同步生长
5. **受光照/空间影响** — `canGrowStage()` 检查上方树叶密度，光照不足时跳过扩展或改变生长方向

所有这些都通过 `TreeGrowthProfile` 接口的 `growStage()` 方法内部实现，不改变外围框架。
