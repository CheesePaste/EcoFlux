Ecoflux adds chunk-scale ecological succession to Minecraft. Every 16×16 chunk evolves independently — plants grow, mature, age, and die, and their collective vitality determines whether the biome advances (e.g. plains → forest) or regresses.

---

## Features

**Ecological Succession**
Each chunk matches a succession path based on its biome, temperature, and downfall. When total plant points exceed the consuming threshold, progress advances; otherwise it regresses. When the progress bar fills, the biome actually changes — plains become forests, forests become dark forests. 20 succession paths included.

**Plant Lifecycle**
Flowers, saplings, trees, and mushrooms all follow a full lifecycle: BORN → JUVENILE → GROWING → MATURE → AGING → DEAD. Lifespan is configurable. Plants visibly grow from sprouts to mature forms, then wither and decay.

**Progressive Tree Growth**
Saplings no longer grow instantly. Tracked saplings grow in stages over dozens of minutes using a space colonization algorithm. 9 tree species (oak, birch, spruce, cherry, jungle, dark oak, acacia, mangrove + 2×2 variants) and 2 huge mushroom types.

**Data-Driven**
All succession paths, biome rules, and plant definitions are JSON-configurable. 64 biomes have independent plant ecosystem rules. Supports data pack overrides and `/reload` hot-reloading — no restart needed.

**World Generation Integration**
Vanilla trees are replaced with custom trees during world generation. All vanilla vegetation is scanned and registered on chunk load, so succession begins naturally from day one in new worlds.

---

## Getting Started

1. Install **NeoForge 21.1.227+**
2. Drop the JAR into `.minecraft/mods/`
3. Enter your world and run:  
   `/ecoflux auto on`
4. *(The system is off by default — you must enable it manually)*

Check status: `/ecoflux auto status`

---

## Customization

All configs can be overridden via data packs. Place your JSON files under `data/ecoflux/` in a data pack, then run `/reload`.

```
<your-datapack>/data/ecoflux/
├── succession_paths/       # Succession paths
├── biome_rules/minecraft/   # Biome plant rules
└── plant_definitions/      # Plant definitions
```

See the [README](https://github.com/CheesePaste/Succession) for full documentation.

---

## Commands

| Command | Description |
|---------|-------------|
| `/ecoflux auto on` | **Enable succession (required)** |
| `/ecoflux auto off` | Disable succession |
| `/ecoflux auto status` | Check current state |
| `/ecoflux prototype start/stop` | Toggle 10-second accelerated demo |
| `/ecoflux prototype describe` | View current chunk succession state |
| `/ecoflux tree instant <preset>` | Instantly generate a full tree (testing) |
| `/ecoflux sample [radius]` | Sample vanilla plant distribution |

---

## Notes

- This is an **Alpha** release — it has not been widely tested and may contain bugs.
- Compatible with **Sodium**.
- Source code and full docs: [GitHub](https://github.com/CheesePaste/Succession)

---

Ecoflux 为 Minecraft 带来**区块尺度的生态演替系统**。每一个 16×16 的区块都是一个独立的生态单元——植物会生长、成熟、衰老、死亡，它们的集体生命力推动群系向前演进（如草原→森林），也可能使土地退化。这个系统完全由 JSON 配置驱动，无需编写代码即可自定义。

## 核心机制

**生态演替**
每个区块根据当前群系、温度和湿度自动匹配演替路径。区块内所有植物都有自己的点数，当总点数超过"维持阈值"时，演替进度前进；反之则倒退。当进度条满，**群系会真实替换**——草原变成森林，森林变成黑森林（共 20 条默认路径）。

**完整的植物生命周期**
花、草、树苗、大树、蘑菇——每种植物都经历完整的生死循环：**出生 → 幼年 → 生长 → 成熟 → 衰老 → 死亡**。寿命由配置文件决定。植物到达寿命终点后会进入腐烂阶段，最终从世界消失。你可以亲眼看到植物从小苗长成成熟形态，然后逐渐枯萎。

**渐进式树木生长**
树苗不会再瞬间变成大树。被 Ecoflux 追踪的树苗会在**数十分钟内分阶段逐步生长**，使用空间定殖算法生成自然树形。支持 9 种树种（橡树、白桦、云杉、樱花、丛林、深色橡树、金合欢、红树 + 2×2 巨型变体）和 2 种巨型蘑菇（棕色/红色）。

**完全数据驱动**
所有演替路径、群系植物规则、植物属性均由 JSON 配置文件控制。**64 个群系**拥有独立的植物生态系统配置（植物种类、权重、密度上限、维持阈值）。支持通过数据包覆盖配置，执行 `/reload` 即可热重载，无需重启游戏。

**世界生成无缝集成**
在世界生成阶段，Ecoflux 会自动替换原版树木为自定义树，并在区块加载时扫描所有原版植被（花草、蘑菇等）纳入生命周期追踪。新生成的世界中，生态演替从第一天就开始自然运作。

## 快速开始

1. 安装 **NeoForge 21.1.227+**
2. 将 JAR 文件放入 `.minecraft/mods/` 目录
3. 进入世界后执行：  
   `/ecoflux auto on`
4. **（系统默认关闭，必须手动开启才能生效）**

检查状态：`/ecoflux auto status`

## 自定义配置

所有配置均可通过数据包覆盖。按以下结构将 JSON 文件放入数据包的 `data/ecoflux/` 目录，然后执行 `/reload` 即可：

```
<你的数据包>/data/ecoflux/
├── succession_paths/       # 演替路径（源群系→目标群系、气候条件、演替速度）
├── biome_rules/minecraft/   # 群系植物规则（植物种类和权重、密度上限、维持阈值）
└── plant_definitions/      # 植物定义（寿命、点数、生成条件）
```

## 注意事项

- 这是 **Alpha 测试版**，尚未进行大规模测试，可能存在 bug。欢迎反馈。
- 兼容 **Sodium**（钠模组）。
- 源代码和完整文档：[GitHub](https://github.com/CheesePaste/Succession)
