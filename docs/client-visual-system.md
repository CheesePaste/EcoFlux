# 客户端视觉系统

客户端视觉系统负责将服务端的植被生命周期状态渲染为可视效果（缩放、着色），以及生长动画的客户端接收。

## 定位

- 这是一个 **client-side 的视觉抽象层**
- 只关心"怎么画"，不关心"为什么现在该画成这样"
- 上层生命系统负责：这个植物现在是不是 `mature / aging`
- 视觉层负责：`mature / aging` 在画面上到底长什么样

## 设计目标

- 支持统一的阶段化视觉表达（scale + tint）
- 支持不同植物类型复用同一套接口
- 支持命令强制调试
- 支持客户端配置
- 可以被上层生命系统直接驱动

## 明确边界

### 负责什么
- 维护客户端 tracked visual instances
- 为 BlockState 找到视觉适配器
- 根据外部同步状态计算当前 visual render state
- 将 render state 转为 scale、tintedColor、stage、stageProgress
- 在客户端渲染阶段绘制缩放后的 tracked block

### 不负责什么
- 不负责真实出生/死亡判定
- 不负责植物积分
- 不负责演替进度
- 不负责服务端生命周期计算

## 核心类型

### VisualLifecycleStage
当前视觉阶段：`BORN` → `GROWING` → `MATURE` → `AGING`（视觉阶段，不等于完整逻辑生命周期）

### VisualLifecycleProfile
视觉生命周期模板：各阶段时长、born/mature/aging 的 scale 锚点、aging 的色调/饱和度/亮度偏移参数。

### VisualLifecycleExternalState
外部驱动状态：`stage` + `stageProgress`，由服务端生命周期系统权威提供。

### VisualLifecycleTrackingSource
当前来源分为 `MANUAL`（命令调试）和 `VEGETATION_SYSTEM`（服务端同步）。

### VisualLifecycleInstance
被客户端 tracked 的视觉实例：adapterId、blockId、pos、profile、externalState、source。
- 当 `externalState == null` 时，退回到本地时间驱动
- 当 `externalState != null` 时，直接使用外部同步阶段/进度

### VisualLifecycleRenderState
渲染层真正消费的结果对象：`stage`、`stageProgress`、`scale`、`tintedColor`。

### VisualLifecycleAdapter
视觉层最核心的扩展接口：
- `matches(BlockState)` — 决定某个方块归哪个视觉适配器管理
- `createProfile(BlockState)` — 生成视觉 profile
- `resolveState(...)` — 根据实例、本地时间、外部状态和颜色计算最终 render state

### VisualLifecycleRegistry
管理所有视觉 adapter，提供 `find(BlockState)` 做适配器匹配。

### VisualLifecycleClientRuntime
客户端运行时核心，管理：
- 所有 tracked instances
- 当前维度内的实例查询
- 命令入口调用
- 生命周期系统同步入口 (`syncVegetationChunk`)
- 渲染重建触发

## 渲染实现

### Tint 路径
通过 `RegisterColorHandlersEvent.Block` 注册 block color handler：
1. 原版或 biome 提供基础颜色
2. `VisualLifecycleClientRuntime.adjustTint(...)` 检查是否被 tracked
3. 若被 tracked 且处于 aging 阶段，返回 aging tint

### Scale 路径
1. 对 `scale != 1` 的 tracked block，`BlockRenderDispatcherMixin` 跳过原版基础渲染
2. `VisualLifecycleWorldRenderer` 在 `AFTER_BLOCK_ENTITIES` 阶段按 tracked 列表重新绘制缩放后的 block model
3. 使用 `renderBatched(...)` 保留 world + pos 语义，确保 tint 跟世界渲染路径一致
4. `SodiumBlockRendererMixin` 为钠 (Sodium) 模组提供兼容渲染路径

## 当前适配器

| 适配器 | 匹配范围 |
|--------|---------|
| `GrassVisualLifecycleAdapter` | `short_grass`, `fern`, `dead_bush` |
| `FlowerVisualLifecycleAdapter` | `#minecraft:small_flowers` |
| `SaplingVisualLifecycleAdapter` | `#minecraft:saplings` |
| `GenericVisualLifecycleAdapter` | 任意非空气方块（fallback） |

## 调试命令

```
/ecoflux visual start <x> <y> <z>
/ecoflux visual stop <x> <y> <z>
/ecoflux visual inspect <x> <y> <z>
/ecoflux visual stage <x> <y> <z> <born|growing|mature|aging>
/ecoflux visual list
/ecoflux visual clear
/ecoflux visual scale_override <value>
/ecoflux visual scale_override clear
```

## 客户端配置

配置文件：`config/ecoflux-client.toml`（`VisualLifecycleClientConfig`）

- `born_scale`, `growing_start_scale`, `mature_scale`, `aging_scale`
- `debugUniformScaleOverride`（运行时调试覆盖）

视觉系统可在服务端通过 `EcofluxServerConfig.enableVisualSystem()` 全局关闭（默认 false），关闭后客户端使用原版渲染，无性能开销。

## 数据流

```
服务端 VegetationTracker.observeChunk()
  → adapter.visualState() 映射 VisualLifecycleStage + progress
  → VegetationVisualChunkSyncPayload 打包
  → 发送给追踪该 chunk 的客户端
  → VisualLifecycleClientRuntime.syncVegetationChunk()
  → 更新 VisualLifecycleInstance.externalState
  → VisualLifecycleWorldRenderer 消费 VisualLifecycleRenderState
```

## 当前相关文件

```
client/visual/
├── VisualLifecycleStage.java
├── VisualLifecycleProfile.java
├── VisualLifecycleExternalState.java
├── VisualLifecycleTrackingSource.java
├── VisualLifecycleInstance.java
├── VisualLifecycleRenderState.java
├── VisualLifecycleAdapter.java
├── VisualLifecycleRegistry.java
├── VisualLifecycleClientRuntime.java
├── VisualLifecycleWorldRenderer.java
├── ModClientVisualLifecycle.java
├── FlowerVisualLifecycleAdapter.java
├── GrassVisualLifecycleAdapter.java
├── SaplingVisualLifecycleAdapter.java
└── GenericVisualLifecycleAdapter.java

mixin/client/
├── BlockRenderDispatcherMixin.java
└── SodiumBlockRendererMixin.java

config/
└── VisualLifecycleClientConfig.java

network/
├── VegetationVisualChunkSyncPayload.java
└── VegetationVisualSyncEntry.java
```
