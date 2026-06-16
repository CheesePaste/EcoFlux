# UIPanel 设计文档

> 创建: 2026-06-16 | 分支: `UIPanel`

自定义 Screen（不依赖 Cloth Config），分 Tab 显示演替数据和卫星图区块编辑器。

## 包结构

```
com.cp.ecoflux/
├── client/ui/                    # Screen + Tab 渲染
│   ├── EcofluxPanelScreen.java       # 主 Screen：Tab 栏 + 背景 + 容器
│   ├── SuccessionOverviewTab.java    # Tab 1：演替数据面板
│   └── ChunkMapTab.java             # Tab 2：卫星图 + 区块编辑
├── client/ui/map/                # 卫星图子系统
│   ├── SatelliteMapRenderer.java     # 顶点缓冲(VBO) + 正交投影渲染
│   ├── ChunkColorSampler.java        # 顶部方块颜色采样 + tint
│   └── ChunkClickMapper.java         # 鼠标坐标 → ChunkPos 转换 util
├── network/                      # 新增 Packet（见下方协议设计）
│   ├── c2s/
│   │   ├── RequestPanelDataPayload.java
│   │   └── ToggleChunkExclusionPayload.java
│   └── s2c/
│       ├── PanelFullSyncPayload.java
│       └── PanelProgressDeltaPayload.java
└── attachment/
    └── SuccessionChunkData.java      # + successionDisabled 字段
```

## Tab 概览

| Tab | 名称 | 内容 |
|-----|------|------|
| Tab 1 | 演替概览 | 全局平均进度条 + 当前区块详细数据（进度条/群系/路径/积分） |
| Tab 2 | 区块编辑 | 8×8 卫星图（上北下南固定）+ 点击切换排除状态 |

## Screen 架构

- **Tab 栏**：顶部自定义渲染，`drawString` + 手动点击检测（`mouseClicked` 判断 Y 范围），不需要 `AbstractButton`
- **背景**：半透明暗色背景叠加游戏画面（`fillGradient` + `enableScissor`）
- **热键**：自定义 keybind 打开（待定，默认 `K` 或 `M`？）
- **无 Cloth Config 依赖**：全部手写

## Tab 1 布局：演替概览

```
┌─ 演替面板 ────────┐
│ [概览] [地图]     │  ← Tab 栏
├───────────────────┤
│ 全局平均进度       │
│ ████████░░ 78%    │  ← fill() 渲染进度条
│                   │
│ 当前区块: (3, -5)  │
│ 群系: 草原 → 森林  │  ← source → target
│ 演替路径:          │
│   plains_to_forest │
│                   │
│ 演替进度           │
│ ██████░░░░ 62%    │  ← -1.0~+1.0 映射到 0~100%
│ 贡献积分: 340/500  │  ← contributing / consuming
│ 贡献植被: 12株     │
│ 总追踪植被: 18株   │
│ 状态: 正向演替中   │
└───────────────────┘
```

- 全局平均值：Screen 打开时服务端当场计算所有已加载 chunk 中 `activePathId != null` 的 progress 均值。后续不实时更新。
- 当前区块数据：通过 delta packet 实时更新（每次 evaluation 后推送）。

## Tab 2 布局：卫星图区块编辑器

```
┌─ 区块编辑 ────────┐
│ [概览] [地图]     │
├───────────────────┤
│                   │
│   8×8 卫星图      │
│   上北下南固定     │
│   64 个 quad      │
│                   │
│   [悬停tooltip]   │  ← ChunkPos + 群系名
│   点击切换排除     │
│   排除chunk: 红框  │
│                   │
└───────────────────┘
```

### 卫星图渲染参数

| 项目 | 决定 |
|------|------|
| 范围 | 玩家周围 8×8 chunk（64 个），以玩家所在 chunk 为中心 |
| 朝向 | 上北下南，固定（不随玩家旋转） |
| 粒度 | 1 quad/chunk，共 64 个 quad |
| 颜色来源 | 每 chunk 中心点采样顶部非空气方块，应用原版 tint（草/叶/水） |
| 水处理 | 采样落在水上时用 `Biome#getWaterColor` |
| 排除标示 | 红框叠加，不遮挡卫星图颜色 |
| 技术栈 | 顶点缓冲 (VBO) + 正交投影 (`Matrix4f.orthographic`) |

### 缓存刷新策略

| 触发条件 | 行为 |
|---------|------|
| 玩家跨 chunk（`ChunkPos` 改变） | 整体重定位：重新采样 64 chunk 颜色 + 重建 VBO |
| 20 tick 定时器 | 兜底颜色刷新：仅重建 VBO 不变地图中心 |
| 排除状态变化 | 立即重建 VBO（更新红框叠加状态） |

### 交互

| 操作 | 行为 |
|------|------|
| 鼠标悬停 | tooltip 显示 `ChunkPos(x, z)` + 当前群系名 |
| 单击 chunk | 切换该 chunk 的排除状态（`successionDisabled` toggle） |
| Shift+拖拽 | 暂不实现，后续迭代 |
| 排除视觉反馈 | 红框叠加 |

### 坐标映射

`ChunkClickMapper` 工具类：
- 输入：鼠标屏幕坐标 (mouseX, mouseY) + 地图在屏幕上的左上角 (mapLeft, mapTop) + 地图像素大小
- 输出：`Optional<ChunkPos>`，或 `null`（点击在地图外）
- 算法：`(mouseX - mapLeft) / quadWidth` → chunk 列偏移 → 转换为世界 ChunkPos

## 区块排除机制

### 存储

`SuccessionChunkData` 新增字段：

```java
private boolean successionDisabled;  // NBT 持久化
```

### 行为

- `successionDisabled == true` 的 chunk 在 `processChunkTick()` 开头直接返回，完全跳过 pipeline（prune + observe + evaluate + spawn）
- 切换立刻生效（`processChunkTick` 每 tick 都会检查）

### Packet

```
C2S: ToggleChunkExclusionPayload
  - chunkX: int
  - chunkZ: int
  - excluded: boolean

服务端处理:
  1. level.getChunk(x, z) → getData(SUCCESSION_CHUNK_DATA)
  2. chunkData.setSuccessionDisabled(excluded)
  3. 返回确认（可选，或复用 PanelFullSyncPayload）
```

## 网络协议设计

### Packet 清单

| 方向 | Packet | 用途 |
|------|--------|------|
| C2S | `RequestPanelDataPayload` | 打开 Screen 时请求全量数据 |
| C2S | `ToggleChunkExclusionPayload` | 点击小地图切换排除状态 |
| S2C | `PanelFullSyncPayload` | 全量响应（全局 avg + 当前 chunk 详情 + 64 chunk 排除状态） |
| S2C | `PanelProgressDeltaPayload` | 增量推送（evaluation 后仅当前 chunk 的 progress/points 变化） |

### C2S: RequestPanelDataPayload

无参数。服务端以发送者位置为准。

### S2C: PanelFullSyncPayload

```
- globalAvgProgress: double          # 所有已初始化chunk的progress均值
- currentChunk: ChunkDetail          # 玩家所在chunk的完整数据
  - chunkX, chunkZ: int
  - currentBiome: ResourceLocation?
  - targetBiome: ResourceLocation?
  - activePathId: ResourceLocation?
  - progress: double
  - contributingPoints: int
  - consumingValue: int
  - contributingCount: int
  - totalVegetationCount: int
- nearbyExclusions: List<ChunkExclusionEntry>
  - chunkX, chunkZ: int
  - excluded: boolean
  (共 64 条，玩家周围 8×8)
```

### S2C: PanelProgressDeltaPayload

```
- chunkX, chunkZ: int
- progress: double
- contributingPoints: int
- consumingValue: int
- contributingCount: int
- totalVegetationCount: int
```

发送时机：`SuccessionEvaluator.evaluate()` 完成后，如果 Screen 开启中。

### 客户端状态机

```
Screen关闭 → 无状态
Screen打开 → 发送 RequestPanelData → 收到 FullSync → 渲染
                                          ↓
                         每 tick 收到 Delta（如有eval）→ 局部更新Tab1
                                         ↓
                         玩家点击地图 → 发送 ToggleExclusion → 服务端处理
                                                                   ↓
                                                          发回 FullSync 确认
```

## 待定项

- [ ] 打开 Screen 的快捷键（建议 `M` 或 `K`）
- [ ] Tab 1 是否显示 vegetation stage 分布饼图（当前决定：先不做）
- [ ] 排除操作是否需要二次确认（当前决定：不需确认，单击即切换）
- [ ] 服务端如何在 Screen 开启时追踪"需要推送 delta 的玩家列表"（需维护一个 `Set<ServerPlayer>`）
