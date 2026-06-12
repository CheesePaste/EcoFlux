# 配置系统

配置系统负责加载、缓存和匹配演替路径定义。所有演替规则由 `data/ecoflux/succession_paths/*.json` 中的数据驱动，不需要硬编码。

## 核心组件

| 类 | 职责 |
|----|------|
| `SuccessionConfigLoader` | Gson JSON 加载器，继承 `SimpleJsonResourceReloadListener`，支持 `/reload` 热重载 |
| `SuccessionConfigRegistry` | 线程安全缓存，提供 `findBestMatch(biome, temperature, downfall)` 查找 |
| `SuccessionPathDefinition` | Record: 单条演替路径的完整定义 |
| `ChunkRules` | Record: consuming、max_plants、queue_fill_factor、evaluation_interval_days |
| `ClimateCondition` | Record: temperature/rainfall 范围匹配 |
| `PlantDefinition` | Record: 中心植物注册表条目 (plant_id, pointValue, maxAgeTicks, spawnRules) |
| `PlantRegistry` | 单例：中心植物注册表，`getDefinition(plantId)` 按 ID 查找 |
| `PlantRegistryLoader` | `SimpleJsonResourceReloadListener`，加载 `plant_definitions/` 目录 |
| `PathPlantEntry` | Record: 路径中的植物引用 (plant_id + weight)，不内联完整定义 |
| `PlantSpawnRules` | Record: 生成规则 (requireSky, maxLocalDensity, allowedBaseBlocks) |
| `FloatRange` / `IntRange` | Record: 范围工具类型（min/max） |

## JSON 文件位置

```
src/main/resources/data/ecoflux/
├── succession_paths/              # 演替路径定义 (21 个文件)
│   ├── plains_to_forest.json
│   ├── plains_to_meadow.json
│   └── ...
├── plant_definitions/
│   └── plants.json                # 中心植物注册表 (所有植物的完整定义)
└── tags/blocks/
    ├── simple_vegetation.json
    ├── grass_cover.json
    ├── uses_foliage_tint.json
    └── uses_grass_tint.json
```

## SuccessionPathDefinition JSON Schema (v1)

```json
{
  "schema_version": 1,
  "path_id": "ecoflux:plains_to_forest",
  "priority": 10,
  "source_biomes": ["minecraft:plains", "minecraft:sunflower_plains"],
  "target_biome": "minecraft:forest",
  "fallback_biome": "minecraft:plains",
  "climate": {
    "temperature": { "min": 0.6, "max": 0.95 },
    "downfall": { "min": 0.4, "max": 0.9 }
  },
  "chunk_rules": {
    "consuming": 5,
    "max_plant_count": 8,
    "queue_fill_factor": 2.0,
    "processing_interval_ticks": 100,
    "evaluation_interval_ticks": 0,
    "positive_progress_step": 0.25,
    "negative_progress_step": 0.25,
    "evaluation_interval_days": {
      "min": 1,
      "max": 1
    }
  },
  "plants": [
    { "plant_id": "minecraft:short_grass", "weight": 8 },
    { "plant_id": "minecraft:dandelion", "weight": 6 }
  ]
}
```

**重要变更 (2026-06-12):** 植物定义不再内联在路径 JSON 中。`plants[]` 只包含 `plant_id` + `weight` 引用，完整定义（`point_value`、`max_age_ticks`、`spawn_rules`）集中存放在 `plant_definitions/plants.json`，通过 `PlantRegistry` 按 ID 查找。

### 顶层字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `schema_version` | int | 是 | 当前为 `1` |
| `path_id` | string | 是 | 唯一标识符，跨文件不能重复 |
| `priority` | int | 否(默认0) | 匹配优先级，值越大越优先 |
| `source_biomes` | string[] | 是 | 源群系列表，如 `["minecraft:plains"]` |
| `target_biome` | string | 是 | 目标群系，如 `"minecraft:forest"` |
| `fallback_biome` | string | 否 | 退化目标群系，默认回到源群系 |
| `climate` | object | 否 | 气候条件，无则匹配所有气候 |
| `chunk_rules` | object | 是 | 区块规则 |
| `plants` | array | 是 | 植物列表 |

### climate 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `temperature` | `{min, max}` | 温度范围 [0.0, 2.0] |
| `downfall` | `{min, max}` | 湿度范围 [0.0, 1.0] |

### chunk_rules 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `consuming` | int | 维持当前群系所需的植物点数 |
| `max_plant_count` | int | 区块内最大植物数 |
| `queue_fill_factor` | double | 队列容量倍数。实际队列容量 = `max_plant_count × queue_fill_factor` |
| `processing_interval_ticks` | int | 处理间隔（tick），控制生成/修剪频率 |
| `evaluation_interval_ticks` | int | 评估间隔（tick），为 0 时回退到 `evaluation_interval_days` |
| `evaluation_interval_days.min` | int | 评估间隔最小值（游戏日），回退时使用 |
| `evaluation_interval_days.max` | int | 评估间隔最大值（游戏日），**已解析但未使用** |
| `positive_progress_step` | double | 正向演替每步进度增量 |
| `negative_progress_step` | double | 负向退化每步进度减量 |

### plants[] 字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `plant_id` | string | 是 | 植物 ID，对应 `PlantRegistry` 中的定义 |
| `weight` | int | 是 | 队列随机权重 |

### PlantDefinition JSON 格式 (plant_definitions/plants.json)

植物完整定义集中在中心注册表中：

```json
{
  "schema_version": 1,
  "plants": [
    {
      "plant_id": "minecraft:dandelion",
      "point_value": 2,
      "max_age_ticks": 72000,
      "spawn_rules": {
        "placement": "surface",
        "require_sky": true,
        "max_local_density": 6,
        "allowed_base_blocks": [
          "minecraft:grass_block",
          "minecraft:dirt"
        ]
      }
    }
  ]
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `plant_id` | string | 是 | 方块 ID |
| `point_value` | int | 是 | 植物提供的演替点数 |
| `max_age_ticks` | long | 是 | 植物最大寿命（游戏 tick） |
| `spawn_rules` | object | 是 | 生成规则 |

### spawn_rules 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `placement` | string | 放置策略标识，已解析但 `findSpawnPos()` 未完全使用 |
| `require_sky` | bool | 是否需要天空光照 |
| `max_local_density` | int | 附近同类型植物密度上限 |
| `allowed_base_blocks` | string[] | 允许种植的基底方块 |

## 加载流程

```
/reload 或首次加载
  │
  ├─→ PlantRegistryLoader (SimpleJsonResourceReloadListener)
  │     ├─ 扫描 data/ecoflux/plant_definitions/*.json
  │     ├─ Gson 反序列化 → List<PlantDefinition>
  │     └─→ PlantRegistry.reload(definitions)
  │           └─ 构建 ConcurrentHashMap 缓存
  │
  └─→ SuccessionConfigLoader (SimpleJsonResourceReloadListener)
        ├─ 扫描 data/ecoflux/succession_paths/*.json
        ├─ Gson 反序列化 → List<SuccessionPathDefinition>
        ├─ 校验 path_id 唯一性
        └─→ SuccessionConfigRegistry.reload(paths)
              └─ 构建缓存（按 source_biome 索引）
```

## 路径匹配算法

`SuccessionConfigRegistry.findBestMatch(biome, temperature, downfall)`：

1. 筛选 `source_biomes` 包含当前群系的所有路径
2. 在候选中过滤气候条件匹配的（temperature/downfall 在范围内）
3. 按 `priority` 降序排列
4. 返回第一个匹配的路径

如果没有找到匹配路径，chunk 不参与演替。

## 辅助配置

### EcofluxServerConfig
服务端 TOML 配置（`ecoflux-server.toml`），包含加速演示模式开关等。

### VisualLifecycleClientConfig
客户端 TOML 配置（`ecoflux-client.toml`），控制视觉生命周期渲染开关和参数。

## 已知限制

- `evaluation_interval_days.max` 已解析但评估器未使用（仅用 `min`）
- `spawn_rules.placement` 已解析但 `findSpawnPos()` 未完全使用
- 配置文件依赖 `/reload` 或重启才能更新（运行时无法动态添加）

## 如何添加新的演替路径

1. 在 `src/main/resources/data/ecoflux/succession_paths/` 创建新 JSON 文件
2. 填写必需字段：`schema_version: 1`、`path_id`（唯一）、`source_biomes`、`target_biome`、`chunk_rules`、`plants`（仅需 `plant_id` + `weight` 引用）
3. 确保 `plants[]` 中引用的 `plant_id` 在 `plant_definitions/plants.json` 中有对应定义
4. 可选：添加 `climate` 限制、`fallback_biome` 退化目标、`priority` 优先级
5. 运行 `/reload` 或重启游戏
6. 用 `/ecoflux prototype describe` 验证路径被正确加载

## 如何添加新植物

1. 在 `plant_definitions/plants.json` 的 `plants` 数组中添加条目，填写 `plant_id`、`point_value`、`max_age_ticks`、`spawn_rules`
2. 在需要该植物的演替路径 JSON 的 `plants[]` 中添加 `{"plant_id": "minecraft:xxx", "weight": N}` 引用
3. 确保有对应的 `VegetationTypeAdapter` 能匹配该植物方块
4. 运行 `/reload` 或重启游戏
