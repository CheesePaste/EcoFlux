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
| `PlantDefinition` | Record: 植物配置条目 |
| `PlantSpawnRules` | Record: 植物生成规则（权重、位置约束） |
| `FloatRange` / `IntRange` | Record: 范围工具类型（min/max） |

## JSON 文件位置

```
src/main/resources/data/ecoflux/succession_paths/
├── plains_to_forest.json
├── plains_to_meadow.json
├── plains_to_savanna.json
├── meadow_to_forest.json
├── meadow_to_cherry_grove.json
├── forest_to_birch_forest.json
├── forest_to_dark_forest.json
├── forest_to_flower_forest.json
├── forest_to_jungle.json
├── forest_to_taiga.json
├── birch_forest_to_old_growth_birch_forest.json
├── taiga_to_old_growth_pine_taiga.json
├── taiga_to_old_growth_spruce_taiga.json
├── savanna_to_savanna_plateau.json
├── savanna_to_windswept_hills.json
├── jungle_to_bamboo_jungle.json
├── swamp_to_mangrove_swamp.json
├── river_to_swamp.json
├── cherry_grove_to_forest.json
├── windswept_hills_to_meadow.json
└── (21 个文件)
```

## SuccessionPathDefinition JSON Schema (v1)

```json
{
  "schema_version": 1,
  "path_id": "plains_to_forest",
  "priority": 0,
  "source_biomes": ["minecraft:plains"],
  "target_biome": "minecraft:forest",
  "fallback_biome": "minecraft:plains",
  "climate": {
    "temperature": { "min": 0.3, "max": 1.0 },
    "downfall": { "min": 0.1, "max": 0.8 }
  },
  "chunk_rules": {
    "consuming": 15.0,
    "max_plant_count": 8,
    "queue_fill_factor": 2.0,
    "evaluation_interval_days": {
      "min": 3,
      "max": 7
    }
  },
  "plants": [
    {
      "plant_id": "minecraft:dandelion",
      "point_value": 1.0,
      "weight": 6,
      "max_age_days": 5,
      "category": "FLOWER",
      "spawn_rules": {
        "placement": "ON_GROUND",
        "allowed_base_blocks": [
          "minecraft:grass_block",
          "minecraft:dirt"
        ],
        "max_nearby_same_type": 3
      }
    }
  ]
}
```

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
| `consuming` | double | 维持当前群系所需的植物点数 |
| `max_plant_count` | int | 区块内最大植物数 |
| `queue_fill_factor` | double | 队列容量倍数。实际队列容量 = `max_plant_count × queue_fill_factor` |
| `evaluation_interval_days.min` | int | 评估间隔最小值（游戏日） |
| `evaluation_interval_days.max` | int | 评估间隔最大值（游戏日），**已解析但未使用** |

### plants[] 字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `plant_id` | string | 是 | 方块 ID，如 `"minecraft:dandelion"` |
| `point_value` | double | 是 | 植物提供的演替点数 |
| `weight` | int | 是 | 队列随机权重 |
| `max_age_days` | int | 是 | 植物最大寿命（游戏日） |
| `category` | string | 否 | 植物分类，已存储但**不影响游戏逻辑** |
| `spawn_rules` | object | 否 | 生成规则 |

### spawn_rules 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `placement` | string | 放置方式，已解析但**未使用**（当前由 `findSpawnPos()` 硬编码） |
| `allowed_base_blocks` | string[] | 允许种植的基底方块 |
| `max_nearby_same_type` | int | 附近同类型植物上限 |

## 加载流程

```
/reload 或首次加载
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
- `spawn_rules.placement` 已解析但 `findSpawnPos()` 忽略
- `plants[].category` 已存储但不影响游戏逻辑
- 配置文件依赖 `/reload` 或重启才能更新（运行时无法动态添加）

## 如何添加新的演替路径

1. 在 `src/main/resources/data/ecoflux/succession_paths/` 创建新 JSON 文件
2. 填写必需字段：`schema_version: 1`、`path_id`（唯一）、`source_biomes`、`target_biome`、`chunk_rules`、`plants`
3. 可选：添加 `climate` 限制、`fallback_biome` 退化目标、`priority` 优先级
4. 运行 `/reload` 或重启游戏
5. 用 `/ecoflux prototype describe` 验证路径被正确加载
