# Succession Editor 架构文档

## 项目概述

独立 web 工具，用于可视化编辑 Ecoflux 模组的演替路径 JSON 配置。基于 ReactFlow 的 DAG 图编辑器。

- **路径**: `succession-editor/`
- **框架**: Vite 8 + React 19 + TypeScript 6
- **图形库**: @xyflow/react 12（ReactFlow v12）
- **状态**: Zustand 5（自定义 undo/redo stack）
- **无 UI 框架依赖**：纯 CSS 手写样式

## 目录结构

```text
succession-editor/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── docs/
│   └── editor-architecture.md    # 本文档
├── src/
│   ├── main.tsx                  # 入口
│   ├── App.tsx                   # 主布局：I18nProvider + ReactFlowProvider + KeyboardHandler
│   ├── App.css                   # 全局暗色主题样式
│   ├── index.css
│   │
│   ├── model/
│   │   ├── types.ts              # 所有 TS 类型（JSON 层 + Graph 层 + Palette + Validation）
│   │   ├── defaults.ts           # 工厂函数：defaultClimate/defaultChunkRules/defaultPlant/defaultEdgeData
│   │   └── biomeData.ts          # Minecraft 1.21.1 群系列表（65条目）+ 查找函数
│   │
│   ├── store/
│   │   └── editorStore.ts        # Zustand store：graph 状态 + undo/redo + 校验 + 所有 mutations
│   │
│   ├── serialization/
│   │   ├── exportJson.ts         # GraphModel → SuccessionPath[] → 浏览器下载 JSON
│   │   └── importJson.ts         # JSON 文件 → SuccessionPath[] → nodes + edges（含去重逻辑）
│   │
│   ├── i18n/
│   │   ├── translations.ts       # 中英文翻译表（~91 keys）
│   │   └── I18nContext.tsx        # React Context + useT() hook + localStorage 持久化语言偏好
│   │
│   └── components/
│       ├── ErrorBoundary.tsx      # 全局错误边界（类组件，捕获崩溃 + 重新加载按钮）
│       │
│       ├── toolbar/
│       │   └── Toolbar.tsx        # Import/Export/Validate/Undo/Redo/Clear/Lang 按钮
│       │
│       ├── palette/
│       │   └── BiomePalette.tsx   # 左侧面板：按维度分组、可折叠、搜索、拖拽/点击添加节点
│       │
│       ├── canvas/
│       │   ├── GraphCanvas.tsx    # ReactFlow 包装器：MiniMap + Controls + 网格吸附 + 错误高亮
│       │   ├── BiomeNode.tsx      # 自定义群系节点（温度颜色渐变、双 Handle）
│       │   └── SuccessionEdge.tsx # 自定义连线（pathId 标签、选中高亮）
│       │
│       └── panel/
│           ├── PropertyPanel.tsx   # 右侧属性面板容器（按选中类型分发）
│           ├── NoSelection.tsx     # 无选中时：全局统计概览表格
│           ├── NodeProperties.tsx  # 群系节点属性（只读展示 + 出入连接统计 + 删除按钮）
│           ├── PathProperties.tsx  # 连线属性容器（四个子编辑器 + 删除按钮）
│           └── editors/
│               ├── PathIdentityEditor.tsx  # path_id/priority + source/target/fallback 展示
│               ├── ClimateEditor.tsx       # 温度/湿度范围滑块 + 数值输入
│               ├── ChunkRulesEditor.tsx    # 8 个 chunk_rules 字段（可折叠）
│               ├── PlantTableEditor.tsx    # 植物列表表格 + 展开行编辑 + PlantDatalists
│               └── SpawnRulesEditor.tsx    # 子表单：placement/density/sky/base_blocks
```

## 数据模型

### 两层类型体系

**JSON 序列化层**（与 Java record 对应）:

```typescript
interface SuccessionPath {
  schemaVersion: 1;
  pathId: string;                 // "ecoflux:plains_to_forest"
  priority: number;
  sourceBiomes: string[];         // ["minecraft:plains", "minecraft:sunflower_plains"]
  targetBiome: string;
  fallbackBiome: string | null;
  climate: ClimateCondition;      // { temperature: FloatRange, downfall: FloatRange }
  chunkRules: ChunkRules;         // 8 fields
  plants: PlantDefinition[];      // PlantDefinition + PlantSpawnRules
}
```

**Graph 运行时层**（ReactFlow 用）:

```typescript
// 节点 = 群系
type BiomeGraphNode = Node<BiomeNodeData, "biome">;
interface BiomeNodeData extends Record<string, unknown> {
  type: "biome";
  biomeId: string;              // "minecraft:plains"
  biomeMeta?: BiomeMeta;        // 只读参考：defaultTemp/defaultDownfall/category/displayName
}

// 连线 = 演替路径（自包含全部配置）
type PathGraphEdge = Edge<PathEdgeData, "succession">;
interface PathEdgeData extends Record<string, unknown> {
  pathId: string;
  priority: number;
  climate: ClimateCondition;
  chunkRules: ChunkRules;
  plants: PlantDefinition[];
}
```

### 关键设计：Edge 自包含

每条 `PathGraphEdge` 携带完整的路径配置（climate、chunkRules、plants）。没有独立的 "PathConfig" 实体——路径仅存在于 edge data 中。这意味着：
- 两条不同 source→target 的 edge 可以有相同 pathId（导入时多 source 路径会拆成多条 edge）
- 修改一条 edge 不影响其他 edge
- 导出时一条 edge → 一个 SuccessionPath（sourceBiomes 单元素）

### 群系元数据

`biomeData.ts` 内置 65 个 Minecraft 1.21.1 群系，含默认温度(-0.7~2.0)、湿度(0.0~1.0)、分类、维度。用作：
- 左侧 Palette 面板内容
- 节点颜色计算（温度渐变）
- MiniMap 节点颜色
- 导入时的只读元数据填充

## Zustand Store 详解

### State

```typescript
interface EditorState {
  nodes: BiomeGraphNode[];          // 所有节点
  edges: PathGraphEdge[];           // 所有连线
  selectedId: string | null;        // 当前选中的 node/edge ID
  validationErrors: ValidationError[]; // 校验结果

  undoStack: HistoryEntry[];        // 最近 50 个快照 { nodes, edges }
  redoStack: HistoryEntry[];
}
```

### Actions 分类

**结构变更**（触发 pushHistory，可撤销）:
- `addBiomeNode(biomeId)` — 创建节点 + 推入 undo stack
- `removeNode(nodeId)` — 删除节点 + 级联删除关联 edge
- `addEdge(sourceId, targetId)` — 查重 → 创建 edge（data 来自 defaultEdgeData）
- `removeEdge(edgeId)` — 删除 edge
- `clearGraph()` — 清空全部

**数据变更**（不触发 pushHistory，不可撤销）:
- `updateEdgeData(edgeId, patch)` — 更新 edge.data 的任意字段（map 不可变更新）
- `addPlant(edgeId)` — 往 edge.data.plants 追加 defaultPlant()
- `removePlant(edgeId, plantIndex)` — 按索引删除
- `updatePlant(edgeId, plantIndex, patch)` — 按索引更新

**ReactFlow 集成**:
- `onNodesChange(changes)` — 标准受控模式，处理拖拽/删除/选择
- `onEdgesChange(changes)` — 处理 edge 变更

**导入/导出**:
- `loadGraph(nodes, edges)` — 原子替换整个 graph（重置 undo/redo/validation）
- `resetIdCounters()` — 重置 node/edge ID 计数器

**校验**:
- `validate()` — 全量校验，返回 boolean，错误存入 validationErrors
- `clearValidation()` — 清空校验结果

**选择器方法**:
- `getSelectedEdge()` — 根据 selectedId 查找 edge
- `getConnectedEdges(nodeId)` — 查找某节点的所有出入 edge

### Undo/Redo 实现

自定义 snapshot stack：
- `pushHistory()`: 深拷贝 nodes + edges → 推入 undoStack（保留最近 50），清空 redoStack
- `undo()`: undoStack.pop() → 当前状态推入 redoStack → 恢复快照
- `redo()`: redoStack.pop() → 当前状态推入 undoStack → 恢复快照

注意：edge data 的字段修改（updateEdgeData/addPlant 等）不会推入 history。只有 add/remove node/edge 和 clear 才可撤销。

## 组件树与数据流

```
App
├── ErrorBoundary
│   └── I18nProvider (Context)
│       └── ReactFlowProvider
│           ├── PlantDatalists       # 全局 <datalist> 元素（HTML5 原生）
│           ├── KeyboardHandler       # Ctrl+Z/Y/Escape（输入框内不触发）
│           ├── Toolbar               # 读取 store: nodes, edges, undoStack, redoStack
│           ├── main-content (flex row)
│           │   ├── BiomePalette      # 读取 BIOME_LIST + store.nodes（标记已使用）
│           │   ├── GraphCanvas       # 读取 store: nodes, edges, onNodesChange, onEdgesChange
│           │   └── PropertyPanel     # 读取 store: selectedId, nodes, edges
│           │       ├── NoSelection   # selectedId 未匹配时
│           │       ├── NodeProperties # selectedId 匹配 biome node 时
│           │       └── PathProperties # selectedId 匹配 succession edge 时
│           │           ├── PathIdentityEditor
│           │           ├── ClimateEditor
│           │           ├── ChunkRulesEditor
│           │           └── PlantTableEditor
│           │               └── SpawnRulesEditor (per expanded plant)
```

### 数据流原则

- Store 是唯一真相源。组件通过 `useEditorStore(selector)` 直接订阅。
- 只有派生数据（选中对象、连接统计）通过 props 传递。
- PropertyPanel 先尝试匹配 edge，再尝试匹配 node，都不匹配则 fallback 到 NoSelection。
- 每个编辑器组件接收 `onChange` 回调 → 调用 `updateEdgeData(edgeId, patch)`。

## ReactFlow 集成细节

### 受控模式

使用 `onNodesChange`/`onEdgesChange` + `applyNodeChanges`/`applyEdgeChanges`：
- 拖拽位置、删除、选中状态变更都通过 ReactFlow 标准 changes 处理
- `onNodesChange` 额外处理 `type === "remove"` 的 change → 级联删除关联 edge

### 自定义节点/连线

```typescript
const nodeTypes = { biome: BiomeNode };
const edgeTypes = { succession: SuccessionEdge };
```

- **BiomeNode**: 两个 Handle（target=Top, source=Bottom），`#555` 灰色。不支持自定义 handle ID。
- **SuccessionEdge**: 使用 `getBezierPath` 绘制，EdgeLabelRenderer 显示短 pathId + priority。

### 连接创建

```typescript
onConnect = (connection) => {
  if (connection.source && connection.target) {
    addEdge(connection.source, connection.target);
  }
}
```

当前不支持条件连接——所有 source+target 组合都允许，只要不重复。

### 画布配置

- `fitView`: 自动适应视图
- `snapToGrid` + `snapGrid=[16,16]`: 16px 网格吸附
- `deleteKeyCode=["Backspace","Delete"]`: 键盘删除
- `multiSelectionKeyCode="Shift"`: Shift 多选
- Background: Dots 样式，`#333` 颜色
- MiniMap: 节点颜色用 `miniMapNodeColor()` 按温度渐变

### 错误高亮

`GraphCanvas` 读取 `validationErrors`：
- 有错误的节点：加红色 border + boxShadow
- 有错误的 edge：红色 stroke

## 序列化管线

### Export: Graph → JSON

`exportPaths(nodes, edges)`:
1. 遍历每条 edge
2. 通过 edge.source/edge.target 查找对应节点
3. 构建 SuccessionPath：sourceBiomes = `[sourceNode.biomeId]`（单元素数组）
4. 深拷贝 climate/chunkRules/plants
5. `downloadJson()`: JSON.stringify → Blob → URL.createObjectURL → `<a>` 下载

### Import: JSON → Graph

`readJsonFiles(files)` → `importPaths(paths)`:
1. FileReader 读取每个文件 → JSON.parse
2. 支持单文件包含单个 path 或 path 数组
3. `importPaths()` 两遍扫描：
   - **Pass 1**: `nodeMap<biomeId, node>` 去重创建节点，同一群系多条路径共享一个节点
   - **Pass 2**: 每条 path 的每个 sourceBiome 创建一个 edge
4. `loadGraph()` 原子替换 store 状态

### 已知不匹配

- Export 的 `sourceBiomes` 永远是单元素。如果两条 edge 有相同 pathId，导出会产生重复 SuccessionPath。
- Import 支持多元素 `sourceBiomes`（一条 path → 多条 edge），但 export 不支持合并回去。

## i18n 系统

- `I18nContext.tsx`: `I18nProvider` + `useT()` hook
- `useT()` 返回 `{ t, lang, setLang, toggleLang }`
- `t(key)` 查翻译表，缺失时返 key 本身
- 语言偏好存 `localStorage("ecoflux-editor-lang")`
- 翻译表 ~91 keys，分组：toolbar/palette/panel/overview/node/path/plant/spawn/validation/error
- ErrorBoundary 因是类组件，直接 import translations + getSavedLang()

## 关键约定

- `EcofluxConstants.MOD_ID = "ecoflux"`，所有 mod ID 以此为 prefix
- pathId 格式: `ecoflux:xxx_to_yyy`
- biomeId 格式: `minecraft:xxx`
- plantId 格式: `minecraft:xxx`
- CSS 类名：暗色主题，`#1a1a2e` 底色，`#252540` 卡片色，`#ff5722` 选中/高亮色
- 所有编辑器输入统一用 `className="prop-input"` 样式
- PlantTableEditor 额外导出 `PlantDatalists` 组件（全局 `<datalist>` 元素）
- undo 最大 50 步
- 编辑器只在 `onBlur` 或 `onChange` 时更新 store（非 debounced）

## 条件分支节点 (Phase 2)

### 概述

2026-06 新增：ClimateConditionNode 菱形节点，支持可视化组织演替路径的分支逻辑。

### 类型定义

```typescript
// 新节点类型
interface ConditionNodeData extends Record<string, unknown> {
  type: "condition";
  label: string;                          // 展示标签
  condition: ClimateCondition;            // 温度 + 湿度范围
}
type ConditionGraphNode = Node<ConditionNodeData, "condition">;

// 统一节点类型（用于 store.nodes）
type GraphNode = BiomeGraphNode | ConditionGraphNode;

// PathEdgeData 新增字段
conditionBranch?: "match" | "no_match";   // 匹配/不匹配分支
parentConditionId?: string;               // 关联的条件节点
sourceBiome?: string;                     // 导出用
targetBiome?: string;                     // 导出用
```

### 新增组件

- **`ConditionNode.tsx`** — 菱形节点渲染：1 个 target Handle (Top)，2 个 source Handle（match=绿色左侧, no_match=红色右侧），显示 label + 气候摘要
- **`ConditionProperties.tsx`** — 右侧属性面板编辑器：label、温度/湿度范围、连接统计、删除按钮

### 连线逻辑

- biome → condition: 普通连线（不标记 branch）
- condition → biome (match handle): 标记 `conditionBranch="match"` + `parentConditionId`
- condition → biome (no_match handle): 标记 `conditionBranch="no_match"` + `parentConditionId`

### 导出行为

当前只导出 biome→biome 连线。条件节点是视觉组织工具，不直接产出 JSON。

### Palette 新增

"Branching" 分组，包含 "Climate Condition" 菱形节点入口，点击在画布中央创建。

## 植物快速选择

### 概述

在 PathProperties 的 PlantTableEditor 中新增 quick-add 下拉框，按 category 分组列出 48 种常见 Minecraft 植物。

### 实现

- `inferPlantCategory(plantId)` — 根据 plantId 关键词推断 category/weight/pointValue/maxAgeTicks 默认值
- `defaultPlant(plantId)` — 接受 plantId 参数，返回完整 PlantDefinition
- `addPlant(edgeId, plant?)` — store action 支持可选 PlantDefinition 参数
- 下拉框使用 `<select>` + `<optgroup>`，选中后立即添加正确默认值的植物
