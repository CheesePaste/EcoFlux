# Ecoflux 综合配置编辑器 — 设计文档

## 目标

构建一个**综合性可视化配置编辑器**，覆盖 Ecoflux 模组所有可配置内容，取代手写 JSON。核心能力：

1. **演替路径图**：节点（群系）+ 连线（演替路径）的 DAG 可视化
2. **植物编辑器**：每条路径上的植物列表，含权重、积分、生成规则
3. **区块规则编辑器**：consuming、容量、评估间隔等参数
4. **气候条件编辑器**：温度/湿度范围匹配
5. **导入/导出**：与现有 `succession_paths/*.json` 格式兼容
6. **校验**：实时检查配置合法性，高亮错误

## 设计决策：外部 web 工具 vs 游戏内 GUI

| 维度 | 游戏内 GUI (Screen) | 外部 web 工具 |
|------|---------------------|---------------|
| 开发复杂度 | 手写所有 widget，不支持复杂图形 | React + ReactFlow，成熟生态 |
| 用户体验 | 受限窗口和分辨率 | 独立窗口，多屏对照，流畅拖拽缩放 |
| 与游戏数据集成 | 直接读取游戏内群系 | 导出 JSON 文件到 data 目录 |
| 后续扩展性 | 每加一种节点都要手写渲染 | 组件化，新类型易于扩展 |
| 适用场景 | 运行时调参 | 开发期设计配置 |

**决策：外部 web 工具。** 演替路径是设计时配置，开发者编辑 JSON → 丢进游戏验证，这是标准工作流。预留 HTTP 通信（Phase 4），但不依赖。

---

## 可编辑内容全景

以下是 Ecoflux 模组中需要可视化编辑的所有配置数据：

```
┌─────────────────────────────────────────────────────────┐
│                    Ecoflux 配置数据                        │
│                                                          │
│  ┌─────────────────┐   ┌─────────────────┐               │
│  │ SuccessionPath   │   │ PlantDefinition │               │
│  │─────────────────│   │─────────────────│               │
│  │ path_id          │   │ plant_id        │  方块 ID     │
│  │ priority         │   │ category        │  分类        │
│  │ source_biomes[]  │   │ weight          │  队列权重    │
│  │ target_biome     │   │ point_value     │  生态积分    │
│  │ fallback_biome   │   │ max_age_ticks   │  最大寿命    │
│  │ climate ─────────┼───│ spawn_rules ────┼── 生成规则   │
│  │ chunk_rules ─────┼── │                 │              │
│  │ plants[] ────────┼─> │                 │              │
│  └─────────────────┘   └─────────────────┘               │
│           │                       │                       │
│           ▼                       ▼                       │
│  ┌─────────────────┐   ┌─────────────────┐               │
│  │ ChunkRules       │   │ PlantSpawnRules │               │
│  │─────────────────│   │─────────────────│               │
│  │ consuming        │   │ placement       │  放置方式    │
│  │ max_plant_count  │   │ require_sky     │  需要天空    │
│  │ queue_fill_factor│   │ max_local_density│ 局部密度    │
│  │ eval_interval    │   │ allowed_blocks[]│  允许基底    │
│  │ progress_steps   │   └─────────────────┘               │
│  └─────────────────┘                                      │
│           │                                                │
│           ▼                                                │
│  ┌─────────────────┐                                      │
│  │ ClimateCondition │                                      │
│  │─────────────────│                                      │
│  │ temperature range│                                      │
│  │ downfall range   │                                      │
│  └─────────────────┘                                      │
│                                                          │
│  ┌─────────────────┐                                      │
│  │ Biome Metadata   │  (只读参考，不产出 JSON)             │
│  │─────────────────│                                      │
│  │ 默认温度/湿度    │                                      │
│  │ 群系分类        │                                      │
│  └─────────────────┘                                      │
└─────────────────────────────────────────────────────────┘
```

---

## 总体架构

```text
┌─────────────────────────────────────────────────────────┐
│              Ecoflux Config Editor (Web)                 │
│  React + TypeScript + @xyflow/react + Zustand            │
│                                                          │
│  ┌────────────┐  ┌────────────┐  ┌──────────────────┐   │
│  │  Graph     │  │  Property  │  │  Plant Manager   │   │
│  │  Canvas    │  │  Panel     │  │  (列表/表格编辑)  │   │
│  │  (DAG)     │  │  (表单)    │  │                  │   │
│  └────────────┘  └────────────┘  └──────────────────┘   │
│        │               │                  │              │
│        └───────────────┴──────────────────┘              │
│                        │                                 │
│              ┌─────────┴─────────┐                       │
│              │   Editor Store    │  Zustand              │
│              │   (unified state) │                       │
│              └─────────┬─────────┘                       │
│                        │                                 │
│              ┌─────────┴─────────┐                       │
│              │  JSON Serializer  │  import / export      │
│              └───────────────────┘                       │
└──────────────────────┬──────────────────────────────────┘
                       │  export JSON files → data/ecoflux/succession_paths/
                       ▼
┌─────────────────────────────────────────────────────────┐
│              Minecraft (Ecoflux Mod)                     │
│  SuccessionConfigLoader → 加载 JSON → 游戏内生效         │
└─────────────────────────────────────────────────────────┘
```

---

## 数据模型

所有类型与 Java record 一一对应，字段名保持一致（snake_case → camelCase 转换在序列化层处理）。

### 核心类型

```typescript
// ===== 演替路径 =====
interface SuccessionPath {
  schemaVersion: 1;
  pathId: string;              // "ecoflux:plains_to_forest"
  priority: number;            // 匹配优先级
  sourceBiomes: string[];      // ["minecraft:plains", "minecraft:sunflower_plains"]
  targetBiome: string;         // "minecraft:forest"
  fallbackBiome: string | null;
  climate: ClimateCondition;
  chunkRules: ChunkRules;
  plants: PlantDefinition[];
}

// ===== 气候条件 =====
interface ClimateCondition {
  temperature: FloatRange;     // { min: 0.6, max: 0.95 }
  downfall: FloatRange;        // { min: 0.45, max: 0.9 }
}

interface FloatRange {
  min: number;
  max: number;
}

// ===== 区块规则 =====
interface ChunkRules {
  consuming: number;                  // 维持消耗值
  maxPlantCount: number;              // 植物容量上限
  queueFillFactor: number;            // 队列倍率 (默认 2.0)
  evaluationIntervalDays: IntRange;   // 评估间隔 (游戏日)
  processingIntervalTicks: number;    // 处理间隔 (tick)
  evaluationIntervalTicks: number;    // 固定评估间隔 (0 = 使用 days)
  positiveProgressStep: number;       // 正向进度步长
  negativeProgressStep: number;       // 负向进度步长
}

interface IntRange {
  min: number;
  max: number;
}

// ===== 植物定义 =====
interface PlantDefinition {
  plantId: string;             // "minecraft:poppy"
  category: string;            // "flower" | "ground_cover" | "sapling" | "mushroom" | "tree"
  weight: number;              // 队列抽取权重
  pointValue: number;          // 生态积分
  maxAgeTicks: number;         // 最大寿命 (tick)
  spawnRules: PlantSpawnRules;
}

// ===== 植物生成规则 =====
interface PlantSpawnRules {
  placement: string;           // "surface" | "underground" | "water"
  requireSky: boolean;         // 是否需要天空光照
  maxLocalDensity: number;     // 局部最大密度
  allowedBaseBlocks: string[]; // 允许放置的基底方块 ["minecraft:grass_block", ...]
}
```

### 编辑器图模型（画布用）

```typescript
// 图上节点 = 群系
interface BiomeNodeData {
  type: "biome";
  biomeId: string;           // "minecraft:plains"
  biomeMeta?: {              // 只读，来自内置数据
    defaultTemp: number;
    defaultDownfall: number;
    category: string;
    displayName: string;
  };
}

// 图上连线 = 演替路径
interface PathEdgeData {
  pathId: string;
  priority: number;
  climate: ClimateCondition;
  chunkRules: ChunkRules;
  plants: PlantDefinition[];
}

// ReactFlow 节点
type GraphNode = Node<BiomeNodeData, "biome">;

// ReactFlow 连线
type GraphEdge = Edge<PathEdgeData, "succession">;
```

---

## UI 布局设计

```
┌──────────────────────────────────────────────────────────────────┐
│  Toolbar                                                         │
│  [Import] [Export] [Export All] │ [+ Biome] │ [Undo] [Redo]     │
│  [Auto Layout] [Fit View] │ [Validate]                           │
├───────────────┬──────────────────────────┬───────────────────────┤
│               │                          │                       │
│   Biome       │     Graph Canvas         │   Property Panel      │
│   Palette     │     (ReactFlow)          │                       │
│   (可折叠)    │                          │   根据选中内容切换：    │
│               │   ┌─────────┐            │                       │
│   ▸ Overworld │   │ Plains  │            │   ┌─────────────┐     │
│     Plains    │   └────┬────┘            │   │ 选中: 无    │     │
│     Forest    │        │ priority: 10    │   │ 点击节点/连线│     │
│     Taiga     │   ┌────▼────┐            │   │ 编辑属性    │     │
│     Desert    │   │ Forest  │            │   └─────────────┘     │
│     ...       │   └────┬────┘            │                       │
│               │        │ priority: 10    │   当选中连线时显示:    │
│   ▸ Nether    │   ┌────▼────┐            │   ─────────────────   │
│     ...       │   │Birch F. │            │   Path Properties     │
│               │   └─────────┘            │   path_id: [...]      │
│   ▸ End       │                          │   priority: [10]      │
│     ...       │   ┌─────────┐            │   ─────────────────   │
│               │   │ Taiga   │   (孤立)   │   Climate             │
│               │   └─────────┘            │   temp: [0.6 - 0.95]  │
│               │                          │   downfall: [0.4-0.9] │
│               │                          │   ─────────────────   │
│               │                          │   Chunk Rules         │
│               │                          │   consuming: [5]      │
│               │                          │   max_plants: [8]     │
│               │                          │   ...                 │
│               │                          │   ─────────────────   │
│               │                          │   Plants (6)          │
│               │                          │   ┌────────────────┐  │
│               │                          │   │ short_grass  w8 │  │
│               │                          │   │ fern         w4 │  │
│               │                          │   │ poppy        w2 │  │
│               │                          │   │ dandelion    w2 │  │
│               │                          │   │ oak_sapling  w1 │  │
│               │                          │   │ birch_sapling w1│  │
│               │                          │   └────────────────┘  │
│               │                          │   [+ Add Plant]       │
│               │                          │                       │
├───────────────┴──────────────────────────┴───────────────────────┤
│  Status: 4 nodes, 2 edges │ Last export: 10:30 │ 0 errors       │
└──────────────────────────────────────────────────────────────────┘
```

### 三个主要区域

**1. 左侧：群系面板 (Biome Palette)**
- 按维度分类（Overworld / Nether / End）
- 内置完整 Minecraft 1.21.1 群系列表
- 每个群系显示默认温度/湿度 tooltip
- 拖拽到画布创建节点，或点击添加
- 已存在于画布上的群系显示为灰色/已使用标记

**2. 中间：图画布 (Graph Canvas)**
- ReactFlow 驱动的 DAG 编辑器
- 节点 = 群系（颜色按默认温度渐变：冷=蓝，热=橙）
- 连线 = 演替路径（标签显示 path_id 缩写 + priority）
- 支持：拖拽节点、缩放、平移、框选
- 孤立节点标记（不在任何路径中的群系）
- 死端节点标记（只有入没有出的群系）

**3. 右侧：属性面板 (Property Panel)**
- 无选中时：显示全局统计（路径数、覆盖群系数、植物种类数）
- 选中节点：显示群系元数据（只读）
- 选中连线：全功能表单编辑器
  - **Path Identity**: path_id, priority, source 群系（自动从起点节点获取）, target 群系（自动从终点节点获取）, fallback_biome
  - **Climate**: temperature min/max, downfall min/max（带滑块 + 数值输入）
  - **Chunk Rules**: consuming, max_plant_count, queue_fill_factor, evaluation intervals, progress steps
  - **Plants 列表**: 可编辑表格，每行一个 PlantDefinition，支持：
    - 添加/删除/复制植物
    - 内联编辑所有字段（plant_id 下拉搜索、weight、point_value、category 下拉、max_age_ticks）
    - 展开行编辑 spawn_rules（placement、require_sky、max_local_density、allowed_base_blocks）
    - 按权重排序、按分类筛选

---

## 编辑器功能列表（按 Phase）

### Phase 1：基础编辑器（当前实现）

**目标**：完整覆盖现有 3 条路径的全部可编辑字段，导入/导出兼容现有 JSON。

- [x] 项目脚手架：Vite + React + TypeScript + @xyflow/react + Zustand
- [ ] 类型定义（`model/types.ts`）
- [ ] GraphModel（增删节点/连线）
- [ ] 群系面板（内置 1.21.1 群系列表，按维度分组）
- [ ] 图画布（BiomeNode 渲染、SuccessionEdge 渲染、拖拽连线）
- [ ] 属性面板 — 选中连线时：
  - Path 基本属性（path_id, priority）
  - Climate 编辑器（温度/湿度滑块）
  - ChunkRules 编辑器（全部 8 个字段）
  - **Plants 表格编辑器**：
    - 6 列表格：plant_id, category, weight, point_value, max_age_ticks, actions
    - 展开行显示 spawn_rules 子表单
    - 添加/删除/复制植物行
- [ ] JSON 导出（生成标准 schema v1 JSON → 浏览器下载）
- [ ] JSON 导入（解析现有文件 → 还原为图 + 填充属性）
- [ ] 校验（pathId 唯一、数值范围、必填字段）+ 错误高亮
- [ ] 撤销/重做（Zustand temporal middleware）
- [ ] 画布操作（缩放、平移、自动布局、适应视图）

### Phase 2：条件分支节点

- [ ] ClimateConditionNode（菱形节点，双输出端口 true/false）
- [ ] 条件表达式编辑器（temperature/downfall 的 gte/lte/between）
- [ ] 导出时展平为多条独立路径
- [ ] 导入时识别关联路径还原为条件节点

### Phase 3：植物池 + 高级编排

- [ ] 植物池（PlantLibrary）：独立面板，定义可复用的 PlantDefinition 集合
- [ ] 路径引用植物池而非内联植物列表
- [ ] PriorityRouterNode
- [ ] 全局 DAG 预览（所有路径汇总）
- [ ] 群系覆盖分析（哪些群系有路径、哪些没有）

### Phase 4：游戏内集成（可选）

- [ ] PathEditorHttpServer（Java 端）
- [ ] 一键推送 + `/reload` 热加载
- [ ] 获取游戏内群系/气候数据

---

## 技术栈

| 层 | 选型 | 原因 |
|----|------|------|
| 框架 | React 18+ | 生态最成熟 |
| 语言 | TypeScript 5.x | 类型安全 |
| 构建 | Vite 6.x | 快速 dev server |
| 图形库 | @xyflow/react (ReactFlow 12) | 节点图编辑器，拖拽/缩放/连线/自定义节点 |
| 状态 | Zustand + temporal middleware | 轻量，内置 undo/redo |
| 校验 | Zod | schema 校验 + TS 类型推导 |
| 样式 | CSS Modules 或 Tailwind | 无需重量级组件库 |

---

## 文件结构

```text
succession-editor/
  package.json
  vite.config.ts
  tsconfig.json
  index.html
  src/
    main.tsx                    # 入口
    App.tsx                     # 主布局
    App.css                     # 全局样式

    model/
      types.ts                  # 所有 TS 类型定义（与 Java record 对应）
      defaults.ts               # 默认值工厂函数
      biomeData.ts              # Minecraft 1.21.1 完整群系列表 + 元数据

    store/
      editorStore.ts            # Zustand store：图状态 + 选中 + 脏标记
      useEditorStore.ts         # 导出 hook

    serialization/
      exportJson.ts             # GraphModel → JSON 文件
      importJson.ts             # JSON 文件 → GraphModel
      schema.ts                 # Zod schema 校验

    components/
      layout/
        AppLayout.tsx           # 三栏布局容器

      canvas/
        GraphCanvas.tsx         # ReactFlow 包装器
        BiomeNode.tsx           # 自定义群系节点渲染
        SuccessionEdge.tsx      # 自定义连线渲染 + 标签

      palette/
        BiomePalette.tsx        # 左侧群系面板
        BiomePaletteItem.tsx    # 单个群系项

      panel/
        PropertyPanel.tsx       # 右侧属性面板容器
        NoSelection.tsx         # 无选中时的全局统计
        NodeProperties.tsx      # 群系节点属性（只读展示）
        PathProperties.tsx      # 连线属性（全部可编辑字段）

        editors/
          PathIdentityEditor.tsx    # path_id, priority, fallback
          ClimateEditor.tsx         # 温度/湿度范围
          ChunkRulesEditor.tsx      # 全部 8 个 chunk_rules 字段
          PlantTableEditor.tsx      # 植物列表表格
          PlantRowEditor.tsx        # 单行植物编辑
          SpawnRulesEditor.tsx      # 子表单：生成规则

      toolbar/
        Toolbar.tsx             # 导入/导出/添加/撤销/重做

      validation/
        ValidationPanel.tsx     # 校验结果面板

    utils/
      validation.ts             # 校验函数
      idGenerator.ts            # 生成唯一 ID
```

---

## Phase 1 实现步骤

按文件创建顺序，确保每一步都可通过 `npm run dev` 查看进展：

### Step 1: 项目基础
- 安装依赖：`@xyflow/react`, `zustand`, `zod`
- 清理 Vite 模板默认文件
- 配置 `vite.config.ts`（如有需要）

### Step 2: 类型 + 默认值
- `model/types.ts`：完整类型定义
- `model/defaults.ts`：各类型的默认值工厂
- `model/biomeData.ts`：内置群系列表

### Step 3: Store
- `store/editorStore.ts`：统一状态管理
  - `nodes: GraphNode[]`
  - `edges: GraphEdge[]`
  - `selectedId: string | null`
  - `addBiomeNode(biomeId)`, `removeNode(id)`
  - `addEdge(source, target)`, `removeEdge(id)`
  - `updateEdgeData(id, partialData)`
  - undo/redo via temporal middleware

### Step 4: 画布
- `BiomeNode.tsx`：自定义 ReactFlow 节点
- `GraphCanvas.tsx`：ReactFlow + 交互配置

### Step 5: 群系面板
- `BiomePalette.tsx`：按维度分组的可搜索列表

### Step 6: 属性面板
- `PropertyPanel.tsx`：选中分发
- `PathProperties.tsx`：连线编辑主容器
- `PathIdentityEditor.tsx`
- `ClimateEditor.tsx`
- `ChunkRulesEditor.tsx`
- `PlantTableEditor.tsx`（含展开行 SpawnRulesEditor）

### Step 7: 导入/导出
- `serialization/exportJson.ts`
- `serialization/importJson.ts`
- `serialization/schema.ts`

### Step 8: 工具栏 + 校验
- `Toolbar.tsx`
- `ValidationPanel.tsx`

### Step 9: 样式 + 完善
- 美化 UI
- 处理边界情况

---

## 与现有系统的对接

- 编辑器产出的 JSON 文件直接放入 `src/main/resources/data/ecoflux/succession_paths/`
- 现有的 `SuccessionConfigLoader` 无需任何修改
- Phase 1 导出格式与当前手动编写的 JSON **完全相同**（schema v1）
- 编辑器项目独立于 Minecraft mod，放在仓库根目录 `succession-editor/`

---

## 注意事项

- 编辑器仅处理**配置设计**，不涉及运行时状态
- 群系列表需要维护 MC 1.21.1 完整清单（约 60+ 群系）
- 数值输入需要范围校验（temperature 通常在 -0.5~2.0，downfall 0.0~1.0）
- plant_id 下拉应包含 Minecraft 原版植物 + 常见 mod 植物（可自定义输入）
- category 下拉：`ground_cover`, `flower`, `sapling`, `mushroom`, `tree`, `vine`, `crop`（可自定义输入）
- placement 下拉：`surface`, `underground`, `water`, `any`
- allowed_base_blocks 使用 tag 输入（支持多选 + 自定义输入）
