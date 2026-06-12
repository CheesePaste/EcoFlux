# Ecoflux 文档目录

Ecoflux 是一个 **NeoForge 1.21.1** Minecraft 模组，实现 chunk 尺度的生态演替：植物生长、老化、死亡，其集体"点数"决定该 chunk 的群系演进方向（如草原→森林）或退化方向。

## 子系统总览

```
┌─────────────────────────────────────────────────────────┐
│                      Ecoflux Mod                        │
├─────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  配置系统     │  │  演替核心     │  │  植物生命周期  │  │
│  │  config/     │  │  succession/ │  │  plant/       │  │
│  │  JSON 加载   │  │  评估·转换   │  │  适配器·追踪  │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                 │           │
│         ▼                 ▼                 ▼           │
│  SuccessionPath    SuccessionService   VegetationTracker│
│  Definition        Evaluator           TypeAdapter      │
│  ConfigRegistry    TargetResolver      PlantSpawner     │
│                    BiomeTransition                      │
│                                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  树木生长     │  │  客户端视觉   │  │  网络与数据   │  │
│  │  plant/tree/ │  │  client/     │  │  network/    │  │
│  │  Morphology  │  │  Visual      │  │  attachment/ │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Mixin 层: SaplingBlock / BlockRenderDispatcher   │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## 文档索引

| 文档 | 内容 | 适用场景 |
|------|------|---------|
| [architecture.md](architecture.md) | 总体架构、包结构、分层设计、数据流、关键设计决策 | 首次了解项目、全局视角 |
| [config-system.md](config-system.md) | JSON 配置格式、加载器、注册表、路径匹配算法 | 添加新演替路径、理解配置 schema |
| [succession-system.md](succession-system.md) | 演替循环、进度评估、群系转换、原型演示 | 理解群系如何演变、调试演替逻辑 |
| [plant-lifecycle-system.md](plant-lifecycle-system.md) | 植物适配器模式、生命周期追踪、繁殖器 | 理解植物如何被识别和追踪 |
| [tree-growth-system.md](tree-growth-system.md) | 树木渐进生长、形态学系统、BlockDisplay 动画 | 理解树如何生长、添加新树种 |
| [client-visual-system.md](client-visual-system.md) | 客户端视觉渲染、生命周期可视化、生长动画 | 理解客户端渲染层 |
| [networking-and-data.md](networking-and-data.md) | 网络协议、数据附件、NBT 序列化 | 理解数据如何持久化和同步 |
| [succession-editor.md](succession-editor.md) | Web 可视化编辑器（React 子项目） | 使用或扩展演替编辑器 |
| [todolist.md](todolist.md) | 优先级排序的开发任务清单 | 了解当前进度和待办事项 |

## 快速导航

| 我想... | 去看... |
|---------|--------|
| 了解项目整体结构 | `architecture.md` |
| 添加新的群系演替路径 | `config-system.md` → 末尾"如何添加" |
| 理解 chunk 如何从草原变成森林 | `succession-system.md` → 演替循环 |
| 添加新的植物类型支持 | `plant-lifecycle-system.md` → VegetationTypeAdapter |
| 添加新的树种生长形态 | `tree-growth-system.md` → 如何添加新树种 |
| 理解树为什么不是瞬间长大 | `tree-growth-system.md` → 生长管线 |
| 查看当前开发进度 | `todolist.md` |
| 使用可视化编辑器 | `succession-editor.md` |

## 当前开发状态 (2026-06-12)

**已完成:**
- Mod 引导、chunk 数据附件、JSON 配置加载（21 个演替路径）
- 植被生命周期适配器系统（SimplePlant / Sapling / TreeStructure）
- 客户端视觉渲染、网络同步、调试命令
- 原型全流程演示 → 服务层提取（succession/world/plant 包）
- vegetationRecords 统一追踪（activePlants 已退役）
- 玩家放置/破坏 → VegetationTracker 自动追踪
- 多植物加权队列 + queue_fill_factor
- 负向退化 → fallback 群系
- 树生命周期 Phase 1-4：Mixin 拦截 + 渐进生长 + 形态学系统 + 死亡/腐烂
- 树形态学系统：骨架生成 + 5 种冠形 + 骨架感知叶填充 + BlockDisplay 动画
- 植物衰老死亡系统（三个 adapter 死亡检测 + DEAD 视觉阶段）
- 中心植物注册表 (PlantRegistry)：plant_definitions 与 succession_paths 分离
- Tree Profile 重构：11 个独立 profile 类 → 2 个参数化 record
- 演替路径可视化编辑器 (React web 工具)

**进行中:**
- 演替系统整合

**未开始:**
- Dynamic Trees 兼容
- 区块边界混合
- GameTest
