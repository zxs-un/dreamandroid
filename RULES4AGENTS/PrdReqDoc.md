# DreamHub Product Requirements Document (PRD)

> 版本: 1.0  
> 更新日期: 2026-06-13  
> 本文档描述 DreamHub Android 应用的完整产品架构与功能需求。

---

## 1. 产品概述

DreamHub 是一款本地 AI 图像生成 Android 应用。它通过原生 C++ 后端（cpp-httplib HTTP Server，端口 8081）在设备本地运行 Stable Diffusion 模型和 Real-ESRGAN 超分辨率模型，提供文生图、图生图、超分辨率放大等功能。前端使用 Jetpack Compose + Material 3 构建。

---

## 2. 系统架构

```
┌──────────────────────────────────────────────────┐
│                 Android App (Kotlin + Compose)    │
│  ┌─────────┐  ┌─────────┐  ┌────────┐  ┌──────┐  │
│  │ Models  │  │  Queue  │  │Generate│  │Upscl │  │
│  │  Tab    │  │  Tab    │  │  Tab   │  │ Tab  │  │
│  └────┬────┘  └────┬────┘  └───┬────┘  └──┬───┘  │
│       │            │          │          │       │
│  ┌────┴────────────┴──────────┴──────────┴────┐  │
│  │            MainActivity (Orchestrator)      │  │
│  │  - State Management                         │  │
│  │  - Queue Processing Loop                    │  │
│  │  - Batch Health Check & Service Mgmt        │  │
│  └──────────────────┬──────────────────────────┘  │
│                     │                              │
│  ┌──────────────────┴──────────────────────────┐  │
│  │              Services Layer                  │  │
│  │  - BackendService (C++ Process Manager)     │  │
│  │  - BackgroundGenerationService (HTTP gen)   │  │
│  │  - UpscaleBackendManager (Upscale Process)  │  │
│  │  - QueueRepository (Task Queue)             │  │
│  │  - ModelDownloadService (Download)          │  │
│  └──────────────────┬──────────────────────────┘  │
└─────────────────────┼─────────────────────────────┘
                      │ HTTP (OkHttp)
┌─────────────────────┴─────────────────────────────┐
│     C++ Backend (libstable_diffusion_core.so)      │
│     HTTP Server: http://localhost:8081              │
│     - Qualcomm QNN SDK (NPU)                       │
│     - alibaba/MNN (CPU)                             │
│     - cpp-httplib (HTTP Server)                     │
│     - xtensor-stack (Tensor ops)                    │
└───────────────────────────────────────────────────┘
```

### 2.1 数据流

```
Generate Tab → Add to Queue → Queue Tab → Queue Processing Loop
                                              ↓
                                   Health Check (GET /health)
                                              ↓
                                   BackgroundGenerationService
                                       (POST /generate)
                                              ↓
                                   Result → HistoryManager (save)
                                              ↓
                                   Browse Tab (view/manage)
```

---

## 3. 后端 HTTP 接口

后端为 C++ 原生进程，通过 `cpp-httplib` 在端口 8081 上提供 HTTP 服务。

### 3.1 健康检查

| 项目 | 描述 |
|------|------|
| **端点** | `GET http://localhost:8081/health` |
| **功能** | 验证后端服务是否在线可用 |
| **请求体** | 无 |
| **响应** | HTTP 200 (服务可用) 或连接拒绝/超时 (不可用) |
| **超时** | 连接/读取超时 3s |
| **调用方** | MainActivity Queue 处理循环、GenerateScreen (非直接) |
| **重试策略** | 可配置间隔 (默认20s) 和最大失败次数 (默认4次)，超限后自动重启后端 |

### 3.2 图片生成

| 项目 | 描述 |
|------|------|
| **端点** | `POST http://localhost:8081/generate` |
| **功能** | 提交图像生成请求，返回 SSE 流式响应 |
| **Content-Type** | `application/json` |
| **超时** | 连接/读/写/Call 超时 3600s |

**请求参数 (JSON Body):**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `prompt` | string | 是 | — | 正向提示词 |
| `negative_prompt` | string | 否 | `""` | 负面提示词 |
| `steps` | int | 是 | 28 | 采样步数 (1-50) |
| `cfg` | float | 是 | 7.0 | CFG 引导系数 (1.0-30.0) |
| `use_cfg` | bool | 是 | true | 是否使用 CFG |
| `width` | int | 是 | 512 | 生成宽度 (64-4096) |
| `height` | int | 是 | 512 | 生成高度 (64-4096) |
| `denoise_strength` | float | 是 | 0.6 | 去噪强度 (图生图) |
| `use_opencl` | bool | 是 | false | 是否使用GPU (CPU模型) |
| `scheduler` | string | 是 | `"dpm"` | 调度器选择 |
| `show_diffusion_process` | bool | 是 | false | 是否返回中间步骤预览 |
| `show_diffusion_stride` | int | 是 | 1 | 中间预览步长 |
| `aspect_ratio` | string | 是 | `"1:1"` | 宽高比 |
| `seed` | long | 否 | 随机 | 随机种子 |
| `image` | string (base64) | 否 | — | 输入图片 (图生图) |
| `mask` | string (base64) | 否 | — | 蒙版图片 (Inpainting) |

**调度器选项 (scheduler):**
- `dpm` — DPM++ 2M
- `dpm_sde` — DPM++ 2M SDE
- `euler_a` — Euler A
- `euler` — Euler
- `lcm` — LCM
- 以上均可附加 `_karras` 后缀 (LCM 除外)

**响应格式 (SSE Streaming):**
```
data: {"type":"progress","step":1,"total_steps":20,"image":"<base64>"}
data: {"type":"progress","step":2,"total_steps":20,"image":"<base64>"}
...
data: {"type":"complete","image":"<base64>","seed":12345,"width":512,"height":512}
data: [DONE]
```
或错误：
```
data: {"type":"error","message":"<error description>"}
```

**调用方:** BackgroundGenerationService (通过 MainActivity Queue 处理循环调度)

### 3.3 超分辨率放大

| 项目 | 描述 |
|------|------|
| **端点** | `POST http://localhost:8081/upscale` |
| **功能** | 对图片进行超分辨率放大 |
| **Content-Type** | `application/octet-stream` |
| **请求体** | RGB 原始字节 (width×height×3 bytes) |
| **超时** | 连接/读取超时 300s |

**请求头:**

| 头名称 | 说明 |
|--------|------|
| `X-Image-Width` | 输入图片宽度 (像素) |
| `X-Image-Height` | 输入图片高度 (像素) |
| `X-Upscaler-Path` | Upscaler 模型文件绝对路径 |

**响应:** RGB 字节流 (4× 放大后尺寸)

**调用方:** `performUpscale()` in ImageUtils.kt (by UpscaleScreen)

### 3.4 Token 计数

| 项目 | 描述 |
|------|------|
| **端点** | `POST http://localhost:8081/tokenize` |
| **功能** | 计算提示词的 CLIP Token 数量 |
| **Content-Type** | `application/json` |
| **超时** | 连接 2s，读取 5s |

**请求参数 (JSON):**

| 参数 | 类型 | 说明 |
|------|------|------|
| `prompt` | string | 需要计数的提示词文本 |

**响应 (JSON):**

| 字段 | 类型 | 说明 |
|------|------|------|
| `count` | int | Token 数量 |
| `max_length` | int | CLIP 最大 token 长度 (77) |
| `overflow_offset` | int | 溢出开始的字符偏移，-1 表示未溢出 |

**调用方:** GenerateScreen (tokenizePromptForGenerate 函数)

---

## 4. 前端功能需求

### 4.1 导航结构

底部导航栏 (Bottom Navigation Bar) 包含 5 个 Tab：

| 顺序 | Tab | 路由 | 图标 | 功能简述 |
|------|-----|------|------|---------|
| 1 | Models | `models` | Memory (内存芯片) | 模型管理 |
| 2 | Queue | `queue` | AutoAwesome (魔法星星) | 任务队列 |
| 3 | Generate | `generate` | AutoFixHigh (魔法棒/wizard) | 参数组合 |
| 4 | Upscale | `upscale` | ImageSearch (放大镜图片) | 超分辨率 |
| 5 | Browse | `browse` | PhotoLibrary (图库) | 图片画廊 |

每个 Tab 配有独立 TopAppBar，左侧菜单按钮打开设置抽屉。

---

### 4.2 Models Tab — 模型管理页面

**功能定位:** 管理后端服务的启停、模型选取与加载、模型导入/删除/重命名。

#### 4.2.1 模型列表

- 显示已下载/导入的生成模型（Diffusion Models）
- 显示已下载/导入的超分辨率模型（Upscale Models）
- 每张卡片显示: 模型名称、描述、类型标签
- 选中状态视觉反馈 (secondaryContainer 背景色)
- 已加载状态视觉反馈 (primaryContainer + primary 边框)

#### 4.2.2 模型类型

**生成模型 (Diffusion Models) — 三种子类型:**

| 类型 | backendType | 运行时 | 分辨率 | 说明 |
|------|-------------|--------|--------|------|
| SD 1.5 NPU | `sd15npu` | Qualcomm 芯片 NPU | 128-512 | 需要 QNN SDK |
| SD 1.5 CPU | `sd15cpu` | MNN CPU Runtime | 128-512 | 可切换 GPU(OpenCL) |
| SDXL NPU | `sdxl` | Qualcomm 8Gen3+ NPU | 1024 | SDXL 大分辨率 |

**预置模型列表:**

| 模型 ID | 名称 | 类型 | 大小 |
|---------|------|------|------|
| `sdxl_base` | SDXL Base 1.0 | SDXL NPU | 4.2GB |
| `illustrious_v16` | Illustrious v16 | SDXL NPU | 4.2GB |
| `anythingv5` | Anything V5.0 | SD1.5 NPU | 1.1GB |
| `anythingv5cpu` | Anything V5.0 | SD1.5 CPU | 1.2GB |
| `qteamix` | QteaMix | SD1.5 NPU | 1.1GB |
| `qteamixcpu` | QteaMix | SD1.5 CPU | 1.2GB |
| `absolutereality` | Absolute Reality | SD1.5 NPU | 1.1GB |
| `absoluterealitycpu` | Absolute Reality | SD1.5 CPU | 1.2GB |
| `cuteyukimix` | CuteYukiMix | SD1.5 NPU | 1.1GB |
| `cuteyukimixcpu` | CuteYukiMix | SD1.5 CPU | 1.2GB |
| `chilloutmix` | ChilloutMix | SD1.5 NPU | 1.1GB |
| `chilloutmixcpu` | ChilloutMix | SD1.5 CPU | 1.2GB |

**超分辨率模型 (Upscale Models):**

| 模型 ID | 名称 | 说明 |
|---------|------|------|
| `upscaler_anime` | Anime Upscaler | Real-ESRGAN 4x 动漫 |
| `upscaler_realistic` | Realistic Upscaler | UltraSharpV2 Lite 4x 写实 |

#### 4.2.3 模型操作

- **加载模型:** 停止当前后端 → 启动新 BackendService (传 modelId、width、height、use_opencl)
- **卸载模型:** 停止所有生成服务 → 发送 ACTION_STOP 广播
- **下载模型:** 从 HuggingFace (或镜像站) 下载，显示进度
- **导入自定义模型:**
  - CPU 模型: 选择文件 → convertCustomModel() 转换 → 标记 `finished`
  - NPU 模型: 选择 ZIP → extractNpuModel() → 标记 `npucustom`
  - SDXL 模型: 标记 `SDXL` + `npucustom`
- **导入 Upscale 模型:** 选择 .bin 文件 → 复制到 models/{id}/ 目录 → 标记 `upscaler_custom`
- **重命名模型:** 重命名模型目录 → 更新 selectedModelId
- **删除模型:** 删除模型目录 + 清除历史记录 + 清除偏好设置。如已加载则先卸载

#### 4.2.4 TopAppBar 操作

- Menu: 打开设置抽屉
- Load Model 按钮 (模型已选且未加载时显示)
- Unload Model 按钮 (模型已加载时显示)
- Loading 进度指示器 (模型加载中)
- 重命名按钮 (✏️)
- 删除按钮 (🗑️)
- 导入按钮 (+)，下拉菜单: 导入模型 / 导入NPU模型 / 导入Upscale模型

---

### 4.3 Queue Tab — 任务队列管理

**功能定位:** 作为 Generate 和 Backend 之间的中间层，管理请求处理的完整生命周期。

#### 4.3.1 核心职责

1. **接收任务:** 从 Generate Screen 接收批量生成请求
2. **健康检查:** 发送前自动检查 `GET /health`；失败时自动重启后端
3. **任务调度:** 按 FIFO 顺序处理 PENDING 任务
4. **调用生成服务:** 为每个任务启动 BackgroundGenerationService
5. **结果处理:** 接收 Complete/Error → 保存到 HistoryManager → 标记 consumed
6. **状态同步:** 进度实时更新、错误状态反馈

#### 4.3.2 任务数据模型 (GenerationTask)

```
字段: id, batchGroupId, batchIndex, modelId,
      prompt, negativePrompt, steps, cfg, seed,
      width, height, effectiveWidth, effectiveHeight,
      denoiseStrength, useOpenCL, scheduler, aspectRatio,
      status (PENDING|PROCESSING|COMPLETED|ERROR|CANCELLED),
      timestamp, resultBitmap, resultSeed, errorMessage, progress
```

#### 4.3.3 队列处理流程

```
1. 轮询 getNextPending() → 获取下一个 PENDING 任务
2. 标记任务为 PROCESSING
3. 执行 Health Check（可配置重试）
   - 失败: 累加连续失败计数
   - 超限: 自动重启 BackendService，重新健康检查
4. 健康通过 → 启动 BackgroundGenerationService
   传入所有生成参数
5. 等待 Complete 或 Error（带超时保护）
6. Complete: 保存图片到 HistoryManager → 标记 COMPLETED
7. Error: 标记 ERROR（附错误信息）
8. Timeout: 标记 ERROR → 发送停止广播 → 重置状态
9. 等待服务完全停止后进入下一轮
```

#### 4.3.4 UI 功能

- **批量折叠显示:** 同一批 (相同 batchGroupId) 的请求折叠为一组
  - 折叠状态: 显示提示词、数量徽章、完成/运行/失败统计
  - 展开状态: 显示每个独立条目的完整卡片
  - 单条目批: 不折叠，直接显示平铺卡片
- **状态指示:** 颜色圆点 (PENDING灰、PROCESSING蓝/紫、COMPLETED绿、ERROR红、CANCELLED浅灰)
- **进度条:** PROCESSING 状态任务显示 LinearProgressIndicator
- **详情展开 (ℹ️ 按钮):** 点击展开查看 Steps、CFG、Size、Scheduler、Seed、Negative Prompt
- **左滑删除:** 非 PROCESSING 状态的任务/批次可左滑露出红色删除图标
- **空队列提示:** "No tasks in queue" + "Tap Generate to add tasks"

#### 4.3.5 健康检查配置

| 配置项 | SharedPreferences Key | 默认值 |
|--------|----------------------|--------|
| 重试间隔 | `health_check_retry_interval_s` | 20 秒 |
| 最大失败次数 | `health_check_max_failures` | 4 次 |

---

### 4.4 Generate Tab — 参数组合页面

**功能定位:** 负责组合生成请求的所有参数，并提供一键添加到队列。

#### 4.4.1 参数列表

| 参数 | 类型 | 范围/选项 | 默认值 | 持久化 | 说明 |
|------|------|-----------|--------|--------|------|
| Batch Count | int | 1 - 60 | 1 | 全局 | 批量生成数量（有seed时固定为1） |
| Prompt | string | — | 模型默认 | 全局 | 正向提示词，CLIP 77 token 限制 |
| Negative Prompt | string | — | 模型默认 | 全局 | 负面提示词 |
| Width | int | 64 - 4096 | 512 | 全局 | 图片宽度 |
| Height | int | 64 - 4096 | 512 | 全局 | 图片高度 |
| Steps | int | 1 - 50 (Slider) | 20 | 按模型 | 采样步数 |
| CFG Scale | float | 1.0 - 30.0 (Slider) | 7.0 | 按模型 | 引导强度 |
| Scheduler | string | dpm/dpm_sde/euler_a/euler/lcm | dpm | 按模型 | 采样调度器 |
| Karras | bool | on/off | off | 按模型 | Karras 噪声调度（LCM不可用） |
| Seed | long | 任意数字 (留空=随机) | 空 | 按模型 | 随机种子 |
| OpenCL | bool | CPU/GPU | CPU | 按模型 | 仅 CPU 模型可见 |
| Denoise Strength | float | — | 0.6 | 按模型 | 去噪强度 (CPU模型) |

#### 4.4.2 Token 计数功能

- 输入提示词后 400ms 防抖发送到 `POST /tokenize` 
- 实时显示 `当前token数/最大token数(77)`
- 超出 CLIP 限制的字符以 38% 透明度灰显
- 超出限制时显示 ⚠️ 警告图标

#### 4.4.3 操作按钮

- **Add to Queue (▶️ PlayArrow):** 将所有参数组合为 QueueRepository.addBatch() 调用。如未加载模型则弹出提示对话框。反馈: Snackbar "Added N to queue"
- **Reset:** 重置所有参数为默认值

#### 4.4.4 参数持久化

- 全局参数 (Prompt, Negative Prompt, Batch Count, Width, Height): 通过 GenerationPreferences 全局持久化
- 模型参数 (Steps, CFG, Seed, Scheduler, Denoise, OpenCL): 按 modelId 持久化
- 切换模型时自动加载该模型的保存参数；全局参数保留（不覆盖）

---

### 4.5 Upscale Tab — 超分辨率放大页面

**功能定位:** 对已有图片进行 AI 超分辨率放大处理。

#### 4.5.1 功能列表

- **图片选择:** 点击卡片区域 → 系统图片选择器 (image/*)
- **输入验证:** 图库版 (filter flavor) 限制最大 2048×2048
- **图片预览:** 原图区域支持双指缩放/平移 (1×-5×)
- **图片信息:** 左下角显示分辨率 "W × H"
- **清除图片:** 右上角 ✕ 按钮
- **放大执行:** FAB 按钮 (AutoFixHigh 图标)，依赖 Upscale 模型已加载
  - 未加载时 FAB 灰显
  - 执行时显示 BlockingProgressOverlay (圆形进度 + 瓦片进度 "N/M")
- **结果预览:** 放大后结果区域同样支持缩放/平移
- **保存结果:** 右上角 Save 按钮 → saveImage() 保存到相册
- **错误处理:** 浮动错误卡片，点击关闭

#### 4.5.2 Upscale 后端

- **启动:** UpscaleBackendManager.start(context, upscalerId)
  - 同用端口 8081，会先停止 Diffusion 后端
  - 启动 `libstable_diffusion_core.so --upscaler_mode`
  - 自动准备 QNN 运行时库
  - 8 秒超时自动标记 Running
- **停止:** UpscaleBackendManager.stop()
- **状态:** Idle / Starting(upscalerId) / Running(upscalerId) / Error(message)
- **处理:** 前端将 Bitmap 转 RGB bytes → POST /upscale → 解析返回 → 重建 Bitmap

#### 4.5.3 Upscale 模型管理 (在 Models Tab)

- 显示区域: "Upscale Models" 分隔标题
- 模型卡片: 名称、描述、加载/卸载按钮
- 选择状态跟踪 (SharedPreferences)
- 支持自定义模型导入 (.bin文件)

---

### 4.6 Browse Tab — 图库/画廊页面

**功能定位:** 浏览、管理和操作所有生成的历史图片。

#### 4.6.1 现有功能

- **图片列表:** LazyColumn 垂直滚动，卡片式布局
- **模型筛选:** 横向滚动的 FilterChip 列表（"All" + 各 modelId）
- **详情查看:** 点击卡片 → AlertDialog 显示
  - 大图预览
  - 提示词 (最多3行)
  - 参数信息 (Steps/CFG/Seed)
  - 分辨率 × 生成时间
- **单张操作 (详情弹窗):**
  - Save: 保存到系统相册 (Pictures/DreamHub/)
  - Delete: 删除 (含确认对话框)
- **多选模式:** 长按卡片进入选择模式
  - Checkbox 出现
  - TopBar 显示已选数量和操作按钮
  - 批量删除 (确认对话框)
  - 批量保存到相册
  - 退出按钮 (✕)
- **空状态:** "No generated images" 图标 + 提示文字

#### 4.6.2 待实现功能

> 以下为用户提出的新需求，待开发实现。

##### 4.6.2.1 自定义列数

- 新增列数调节控件（如 SegmentedButton 或 Slider）
- 可选列数: 1 / 2 / 3 / 4 列
- 切换后图片网格动态重排
- 列数设置通过 SharedPreferences 持久化
- 实现: 将 LazyColumn 切换为 LazyVerticalGrid，使用 `GridCells.Fixed(columnCount)`

##### 4.6.2.2 增强批量管理

在现有的长按多选基础上增加:

- **全选 (Select All):** 在 SelectionMode TopBar 增加 "全选" 按钮 → 选中当前筛选的所有图片
- **全不选 (Deselect All):** 在 SelectionMode TopBar 增加 "全不选" 按钮 → 清除所有选中项
- **保存到相册:** 批量保存选中的图片到系统相册 (Pictures/DreamHub/)
  - 逐个保存，显示成功/失败计数 Toast
- **删除:** 批量删除 (已有功能，保持不变)

##### 4.6.2.3 SelectionMode TopBar 布局

```
[← Cancel] [N selected]  [Select All] [Deselect All] [Save] [Delete]
```

---

## 5. 服务层

### 5.1 BackendService

- 前台服务，管理 C++ 原生进程生命周期
- 通过 `libstable_diffusion_core.so` 启动 HTTP 服务
- 状态: Idle / Starting / Running / Error
- 支持 NPU 模式 (QNN)、CPU 模式 (MNN)、Upscaler 模式
- 环境变量管理: LD_LIBRARY_PATH、DSP_LIBRARY_PATH

### 5.2 BackgroundGenerationService

- 前台服务，处理单个 HTTP 生成请求
- 最多重试 3 次 (间隔 1.5s)
- SSE 流式解析: progress → Progress 状态 / complete → Complete 状态
- 进度预览解码 (Base64 → RGB → Bitmap)
- Bitmap 消费等待机制 (默认 30s 超时)
- 支持用户主动停止 (ACTION_STOP 广播)

### 5.3 QueueRepository

- 内存中的任务队列状态管理 (MutableStateFlow)
- 方法: addBatch, removeTask, removeBatch, getNextPending, cancelAllPending
- 状态管理: markTaskProcessing/Complete/Error, updateTaskProgress
- 批量分组: getBatchGroups() 返回 List<BatchGroupDisplay>

### 5.4 UpscaleBackendManager

- 单例对象，管理 Upscale 后端的完整生命周期
- 进程启动、状态监控、优雅停止
- 自动准备 QNN 运行时库

---

## 6. 数据持久化

### 6.1 HistoryManager (Room Database)

- 存储生成的图片记录
- 字段: modelId, imageFile, params (Steps/CFG/Seed/Prompt/NegativePrompt/Width/Height/GenerationTime), mode
- 支持按 modelId 筛选
- 支持观察 (Flow)

### 6.2 GenerationPreferences (SharedPreferences `app_prefs`)

- 全局参数持久化: prompt, negativePrompt, batchCounts, width, height
- 模型参数持久化 (按 modelId): steps, cfg, seed, scheduler, denoiseStrength, useOpenCL
- HuggingFace Base URL 配置

### 6.3 Upscaler Preferences (SharedPreferences `upscaler_prefs`)

- `upscaler_standalone_selected_upscaler`: 最后选择的 Upscaler 模型 ID

---

## 7. 用户配置 (Settings Drawer)

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| Dynamic Color | Material You 动态取色 | 开 |
| Dark Mode | System / Light / Dark | System |
| Health Check Retry Interval | 健康检查重试间隔 (秒) | 20 |
| Health Check Max Failures | 最大连续失败次数 | 4 |
| HuggingFace Base URL | 模型下载地址 | `https://huggingface.co/` |
| Listen on All Addresses | 后端监听所有地址 | 否 |
| Show Diffusion Process | 显示扩散中间步骤 | 否 |
| Show Diffusion Stride | 中间步骤步长 | 1 |

---

## 8. 权限要求

| 权限 | 用途 | 适用条件 |
|------|------|---------|
| POST_NOTIFICATIONS | 后台生成通知 | Android 13+ |
| WRITE_EXTERNAL_STORAGE | 保存图片到相册 | Android < 10 |
| INTERNET | HTTP 通信 (localhost) | 所有 |

---

## 9. 文件结构

```
app/src/main/java/io/github/dreamandroid/local/
├── MainActivity.kt              # 主 Activity，状态管理和编排
├── DreamAndroidApplication.kt   # Application 类
├── navigation/
│   └── Navigation.kt            # BottomTab 枚举和路由
├── data/
│   ├── Model.kt                 # Model/UpscalerModel 数据类 + Repository
│   ├── QueueModels.kt           # GenerationTask/TaskStatus/BatchGroupDisplay
│   ├── HistoryManager.kt        # 历史记录 Room DB 管理
│   ├── GenerationPreferences.kt # 生成参数持久化
│   └── db/                      # Room Database Entity/DAO
├── service/
│   ├── BackendService.kt        # C++ 后端进程管理
│   ├── BackgroundGenerationService.kt  # 单次 HTTP 生成服务
│   ├── QueueRepository.kt       # 任务队列状态管理
│   ├── UpscaleBackendManager.kt # Upscale 后端管理
│   └── ModelDownloadService.kt  # 模型下载服务
├── ui/
│   ├── screens/
│   │   ├── ModelListScreen.kt   # 模型列表/下载页面
│   │   ├── ModelRunScreen.kt    # 模型详情/操作页面
│   │   ├── GenerateScreen.kt    # 生成参数组合页面
│   │   ├── QueueScreen.kt       # 任务队列页面
│   │   ├── UpscaleScreen.kt     # 超分辨率页面
│   │   └── BrowseScreen.kt      # 图库/画廊页面
│   ├── components/              # 通用 Compose 组件
│   └── theme/                   # 主题配置
├── utils/
│   ├── ImageUtils.kt            # performUpscale/saveImage
│   └── LogCapture.kt            # 日志捕获
└── cpp/
    └── src/main.cpp             # C++ 后端 HTTP Server
```

---

## 10. 变更记录

| 日期 | 版本 | 描述 |
|------|------|------|
| 2026-06-13 | 1.0 | 初始版本，完整架构文档 |
| 2026-06-13 | 1.1 | 将架构评审内容独立为 ArchitectureReview.md |
