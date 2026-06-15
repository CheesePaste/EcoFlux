# Ecoflux

*Chunk-scale ecological succession for Minecraft.*

---

**Ecoflux** 为 Minecraft 世界带来真实的生态演替系统。每一个 16×16 的区块都会经历独立的群系进化——植物生长、成熟、衰老、死亡，它们的集体生命力推动着草原变成森林，或使荒芜的土地退化为沙漠。

**Ecoflux** brings real ecological succession to Minecraft. Every 16×16 chunk evolves independently — plants grow, mature, age, and die, and their collective vitality pushes plains toward forests, or lets barren land regress into desert.

| | |
|---|---|
| **Minecraft** | 1.21.1 |
| **Loader** | NeoForge |
| **Version** | 0.2.0 (Alpha) |

---

## 特性 | Features

### 生态演替 | Ecological Succession
每个区块根据当前群系、温度和湿度，自动匹配演替路径。当区块内植物总点数超过维持阈值，进度前进；反之则倒退。进度满时，**群系发生真实替换**——草原变成森林，森林变成黑森林，当前测试版共 20 条默认演替路径。

Each chunk matches a succession path based on its biome, temperature, and downfall. When total plant points exceed the consuming threshold, progress advances; otherwise it regresses. When the progress bar fills, **the biome actually changes** — plains to forest, forest to dark forest, across 20 configured paths.

### 植物生命周期 | Plant Lifecycle
花草、树苗、树木、蘑菇——每种植物都有完整的生命周期：**出生 → 幼年 → 生长 → 成熟 → 衰老 → 死亡**。寿命由配置文件驱动，到达寿命终点后进入腐烂阶段，最终从世界消失。玩家可以亲眼看到植物从小到大、从绿到枯的完整过程。

Flowers, saplings, trees, mushrooms — every plant has a full lifecycle: **BORN → JUVENILE → GROWING → MATURE → AGING → DEAD**. Lifespan is config-driven. At end of life, plants decay and vanish from the world. Watch plants grow from tiny sprouts to mature forms, then wither away.

### 渐进式树木生长 | Progressive Tree Growth
树苗不再瞬间长成大树。被 Ecoflux 追踪的树苗会在 **数十分钟内分阶段逐渐生长**，使用空间定殖算法生成自然树形。支持 9 种树种（橡树、白桦、云杉、樱花、丛林、深色橡树、金合欢、红树 + 2×2 变体）和 2 种巨型蘑菇。

Saplings no longer pop into full trees instantly. Ecoflux-tracked saplings **grow progressively over dozens of minutes**, using a space colonization algorithm for natural tree shapes. 9 tree species (oak, birch, spruce, cherry, jungle, dark oak, acacia, mangrove + 2×2 variants) and 2 huge mushroom types.

### 数据驱动 | Data-Driven
所有演替路径、群系规则、植物定义均由 JSON 配置文件驱动。**64 个群系**拥有独立的植物生态规则（植物种类、权重、密度上限、维持阈值）。支持 `/reload` 热重载，无需重启游戏即可调整配置。

All succession paths, biome rules, and plant definitions are JSON-configurable. **64 biomes** have independent plant ecosystem rules (species, weights, max density, consuming threshold). Hot-reload with `/reload` — no restart needed.

### 世界生成集成 | World Generation Integration
在世界生成阶段，Ecoflux **自动替换原版树木**为自定义 SC 树，并在区块加载时扫描所有原版植被（花草、蘑菇等），将其纳入生命周期追踪。这意味着即使在新生成的世界中，生态演替也会从第一天开始自然运作。

During world generation, Ecoflux **replaces vanilla trees** with custom SC trees, then scans all vanilla vegetation (flowers, grass, mushrooms) on chunk load, bringing them into the lifecycle tracking system. Ecological succession begins naturally from day one in new worlds.

### 调试工具 | Debug Tools
内置丰富的调试命令：演替状态查询、性能分析器、植物分布采样器、瞬间生成树木测试、视觉生命周期渲染。

Rich built-in debug commands: succession state inspection, performance profiler, plant distribution sampler, instant tree generation for testing, visual lifecycle rendering.

---

## 命令参考 | Command Reference

| 命令 | 说明 |
|------|------|
| `/ecoflux prototype start/stop` | 开启/关闭 10 秒加速演替演示 |
| `/ecoflux prototype step` | 手动触发一步演替 |
| `/ecoflux prototype describe` | 查看当前区块演替状态 |
| `/ecoflux tree instant <preset>` | 瞬间生成完整树（测试用） |
| `/ecoflux tree grid <preset> <param> <min> <max> <count>` | 参数扫描测试 |
| `/ecoflux sample [radius] [apply]` | 采样原版植物分布 / 生成 BiomeRules |
| `/ecoflux sample batchall [radius]` | 批量采样所有群系 |
| `/ecoflux profile on/off/report` | 性能分析 |
| `/ecoflux visual start/stop/inspect/list` | 视觉生命周期调试 |

---

## 配置 | Configuration

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `gradual_tree_growth` | true | 树木渐进生长开关 |
| `gradual_plant_growth` | true | 植物渐进生长开关 |
| `evaluation_interval_ticks` | 24000 | 演替评估间隔（1 游戏日） |
| `spawn_interval_min_ticks` | 600 | 植物最短生成间隔 |
| `spawn_interval_max_ticks` | 1800 | 植物最长生成间隔 |
| `enable_visual_system` | false | 视觉渲染系统（实验性） |

---

## 自定义配置 | Customization

所有演替路径、群系规则、植物定义均通过 JSON 文件控制。玩家可以**通过数据包 (Data Pack) 覆盖和新增配置**，无需修改模组 JAR 文件。修改后执行 `/reload` 即可热重载，无需重启游戏。

All succession paths, biome rules, and plant definitions are JSON-driven. Players can **override or add configs via Data Packs** — no need to modify the mod JAR. Run `/reload` after changes, no restart required.

### 数据包结构 | Data Pack Structure

```
<你的数据包>/data/ecoflux/
├── succession_paths/           # 覆盖/新增演替路径
│   ├── plains_to_forest.json   # 修改已有路径
│   └── custom_path.json        # 添加自定义路径
├── biome_rules/minecraft/       # 覆盖/新增群系植物规则
│   ├── plains.json             # 调整平原植物种类和密度
│   └── forest.json             # 调整森林维持阈值
└── plant_definitions/          # 覆盖/新增植物定义
    └── plants.json             # 调整植物寿命和点数
```

### 用法 | Usage

1. 在存档目录下创建数据包：`.minecraft/saves/<世界名>/datapacks/<数据包名>/`
2. 按上述目录结构放置要修改的 JSON 文件
3. 游戏内执行 `/reload` 即可生效

数据包中的文件会**完全替换**模组内置的同路径文件（Minecraft 标准行为）。想要只修改个别数值时，需要从模组 JAR 中复制完整原文件再进行修改。

1. Create a data pack under: `.minecraft/saves/<world>/datapacks/<pack_name>/`
2. Place modified JSON files following the structure above
3. Run `/reload` in-game to apply changes

Files in your data pack **fully replace** the mod's built-in files at the same path (standard Minecraft behavior). To tweak a single value, copy the full original file from the mod JAR first, then edit.

### 可配置内容 | What You Can Change

| 配置文件 | 可修改内容 |
|----------|-----------|
| `succession_paths/*.json` | 演替路径：源/目标群系、气候条件、演替速度 |
| `biome_rules/**/*.json` | 群系规则：植物种类和权重、密度上限、维持阈值 |
| `plant_definitions/*.json` | 植物定义：点数、寿命、生成条件 |

---

## 兼容性 | Compatibility

- **NeoForge** 1.21.1
- 兼容 **Sodium**（钠模组）渲染
- 与大多数群系和世界生成模组兼容（通过 BiomeModifier 取消原版树）

**NeoForge** 1.21.1 required. Compatible with **Sodium**. Works alongside most biome and worldgen mods (vanilla trees are cancelled via BiomeModifier).

---

## 开发 | Development

Ecoflux 是一个开源项目。技术栈：Java 21 + NeoForge + Gradle。完整架构文档和开发指南见 [`docs/`](docs/) 目录。

Ecoflux is open source. Tech stack: Java 21 + NeoForge + Gradle. Full architecture docs and development guide in [`docs/`](docs/).

```bash
./gradlew build        # 构建
./gradlew runClient    # 运行客户端
./gradlew runServer    # 运行服务端
```
