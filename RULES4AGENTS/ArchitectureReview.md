# DreamHub Architecture Review & Optimization Plan

> 版本: 1.0
> 更新日期: 2026-06-13
> 本文档对 DreamHub 现有实现的 3 个核心架构问题进行深度分析，并提供重构建议。

---

## 1. 问题一：MainActivity 是 God Object

**现状：** `AppContent()` 是一个 ~900 行的 Composable 函数，承载了以下全部职责：

```
AppContent()
├── UI 状态管理 (selectedTab, selectedModelId, 全部gen参数...)
├── 队列处理循环 (LaunchedEffect: 轮询 → health check → start service → 等待结果 → 保存历史)
├── 模型加载/卸载 (loadModel, unloadModel, loadUpscaleModel, unloadUpscaleModel)
├── 模型导入逻辑 (convertCustomModel, extractNpuModel, 导入 upscale 模型)
├── 对话/重命名/删除 (showRenameDialog, showDeleteConfirm, showNoModelWarning)
├── 参数持久化 (LaunchedEffect 读取 GenerationPreferences)
└── 后端重启逻辑 (队列循环内嵌健康检查失败后的 restart 代码)
```

**问题：**
- **无法单独测试**：所有逻辑耦合在 Composable 生命周期中
- **状态管理脆弱**：30+ 个 `remember{}` / `mutableStateOf()` 散落在函数体中
- **UI 重组性能**：任何一个 state 变化触发整个 AppContent 重组
- **扩展困难**：每加一个功能，AppContent 增加更多状态和逻辑
- **生命周期依赖**：队列循环在 `LaunchedEffect(Unit)` 中，依赖 Activity 存活；app 进入后台被系统 kill 后队列丢失

**优化方向：**
- 拆分为 ViewModel（如 `MainViewModel`）管理业务状态和队列调度逻辑
- 队列处理逻辑下沉到独立 Service（见 第2章）
- UI 层只负责渲染和事件转发

---

## 2. 问题二：BackgroundGenerationService 是否应由 Queue 接管？

**现状：** 当前生成流程是 "per-task Foreground Service" 模式：

```
任务1: startForegroundService(BgGenService) → 生成 → stopSelf → wait stop
任务2: startForegroundService(BgGenService) → 生成 → stopSelf → wait stop
任务3: ...
```

整个处理循环写在 `MainActivity.AppContent()` 的 `LaunchedEffect` 中：

```
MainActivity LaunchedEffect (队列循环)
  ├── 1. Health Check (GET /health) ── 内联 OkHttp 调用
  ├── 2. 如失败 → 重启 BackendService ── 内联代码
  ├── 3. 启动 BackgroundGenerationService (per-task foreground service)
  ├── 4. 等待 generationState.first { Complete|Error }
  ├── 5. 保存到 HistoryManager ── 内联代码
  ├── 6. 等待 isServiceRunning → false
  └── 7. 循环回到 1
```

**问题：**
- **per-task Service 开销大**：每个任务都要经历 Foreground Service 的创建/通知/销毁生命周期。一个 10 张图的 batch 意味着 10 次 Service start/stop，累积延迟不可忽略
- **队列循环在 UI 线程的协程中**：`LaunchedEffect` 的生命周期绑定到 Composable 组合。Activity 旋转/重建会导致循环被取消和重建（`key=Unit` 确保只启动一次，但仍依赖 Activity 存活）
- **静态 companion 对象共享状态**：`BackgroundGenerationService.generationState`、`_stopRequested`、`_bitmapConsumed` 是全局可变状态。MainActivity 的队列循环直接读取 `first { }` 等待这些 flow。这是隐式的跨组件耦合——任何代码都可以修改这些状态
- **checkBackendHealth() 在 companion 中但每次 new OkHttpClient**：health check 方法在 `BackgroundGenerationService.companion` 中定义，语义上不属于这里（它不涉及 generation），且每次调用创建新的 OkHttpClient 实例（浪费资源）
- **服务等待逻辑在 UI 层**：`while (isServiceRunning.value) { delay(100) }` 这种 busy-wait 在 MainActivity 协程中执行

**结论：YES，Queue 应接管 Generate 调度。**

**推荐架构：QueueProcessingService（一个新的 Foreground Service）**

```
QueueProcessingService (Foreground Service)
├── 内部持有 BackendManager 引用
├── 内部持有 QueueRepository 引用
├── 内部持有 HistoryManager 引用
├── 循环处理任务：
│   while (有 PENDING 任务) {
│     ├── Health Check（复用 BackendManager 的统一 health client）
│     ├── 如失败 → BackendManager.restart()
│     ├── POST /generate (SSE streaming) — 直接在此 Service 中发起 HTTP
│     ├── 进度 → 更新 QueueRepository.taskProgress
│     ├── Complete → HistoryManager.save() → QueueRepository.markComplete()
│     └── Error → QueueRepository.markError()
│   }
└── 对外暴露: tasks StateFlow, processingActive StateFlow
```

**对比旧架构的优势：**

| 维度 | 旧 (per-task service) | 新 (QueueProcessingService) |
|------|----------------------|---------------------------|
| HTTP 连接复用 | 每次新 OkHttpClient (虽然有 lazy sharedClient) | 单 Service 内复用整个生命周期 |
| 任务间延迟 | Service stop→start 开销 (~500ms+) | 零切换开销，直接下一轮循环 |
| 状态管理 | 静态 companion 全局可变 | Service 实例私有状态 |
| 生命周期 | 依赖 Activity 存活 | 独立 Foreground Service，后台稳定运行 |
| Health check | 耦合在 BackgroundGenerationService companion | 统一到 BackendManager |
| 通知 | 每任务一条进度通知 | 单条持续更新的队列进度通知 |
| 可测试性 | 依赖 Android Service context | Service 内逻辑可单独提取为 Manager 类测试 |

---

## 3. 问题三：后端 Model Server Runner 是否应独立？

**现状：** 两个独立的管理器操作同一个端口 8081：

```
BackendService (Diffusion)          UpscaleBackendManager (单例)
├── Foreground Service              ├── object 单例（非 Service）
├── 启动: libstable_diffusion_core.so ├── 启动: libstable_diffusion_core.so --upscaler_mode
├── 端口: 8081                      ├── 端口: 8081
├── 状态: Idle/Starting/Running/Err ├── 状态: Idle/Starting/Running/Error
└── 管理: Process lifecycle          └── 管理: Process lifecycle

MainActivity 手动协调两者的切换:
  loadUpscaleModel() {
    context.stopService(Intent(context, BackendService::class.java))  // 手动停
    UpscaleBackendManager.start(context, upscalerId)                  // 手动启
  }
  
  loadModel() {
    UpscaleBackendManager.stop()                                      // 手动停
    context.startForegroundService(Intent(context, BackendService::class.java))  // 手动启
  }
```

**问题：**
- **进程所有权混乱**：两个独立管理者可能同时持有一个僵尸 Process 引用。如果 UpscaleBackendManager 启动前忘记调 BackendService.stopService()，会出现两个进程竞争端口 8081
- **状态机不统一**：`BackendService.BackendState` 和 `UpscaleBackendManager.State` 是两个独立的 sealed class，但语义高度重叠 (Idle/Starting/Running/Error)
- **MainActivity 承担协调职责**：`loadModel()` 和 `loadUpscaleModel()` 中硬编码了相互停止的逻辑，这是服务层应处理的
- **代码重复**：环境变量准备（LD_LIBRARY_PATH、QNN 库复制）在两个类中重复实现
- **生命周期不一致**：BackendService 是 Android Service (有前台通知)，UpscaleBackendManager 是普通 singleton (无通知)。Upscale 进程被系统 kill 时没有前台服务保护

**结论：YES，应统一为 BackendManager。**

**推荐架构：统一的 BackendManager**

```kotlin
class BackendManager(private val context: Context) {
    
    enum class Mode { Diffusion, Upscaler }
    
    sealed class State {
        object Idle : State()
        data class Starting(val mode: Mode, val modelId: String) : State()
        data class Running(val mode: Mode, val modelId: String) : State()
        data class Error(val message: String) : State()
    }
    
    val state: StateFlow<State>
    
    fun startDiffusion(modelId: String, width: Int, height: Int, useOpenCL: Boolean)
    fun startUpscaler(upscalerId: String)
    fun stop()
    
    // 统一的健康检查（复用 OkHttpClient）
    suspend fun healthCheck(): Boolean
    
    // 统一的 HTTP 客户端（所有端点共享连接池）
    val httpClient: OkHttpClient
}
```

**统一后的调用方式：**

```kotlin
// Models Tab: 加载 Diffusion 模型
backendManager.startDiffusion(modelId, width, height, useOpenCL)

// Models Tab: 加载 Upscale 模型
backendManager.startUpscaler(upscalerId)

// 不再需要手动 stop + start 坐标！
// BackendManager 内部自动处理: 停止当前进程 → 启动新模式进程

// Queue 处理流程中使用:
if (!backendManager.healthCheck()) {
    backendManager.restart()  // 统一的重启逻辑
}
val response = backendManager.httpClient.newCall(generateRequest).execute()
```

**对比旧架构的优势：**

| 维度 | 旧 (两个管理器) | 新 (BackendManager) |
|------|----------------|-------------------|
| 进程唯一性 | 手动协调，易出错 | 内部保证 only-one-process |
| 状态观察 | 两个独立 StateFlow | 单一 StateFlow<State> |
| 环境准备 | 代码重复 (QNN libs) | 启动时统一准备一次 |
| 生命周期 | Service vs Singleton 不一致 | 统一 Foreground Service 保护 |
| 切换逻辑 | 泄漏到 MainActivity | 封装在 BackendManager 内部 |
| HTTP 客户端 | 分散在 3 处 (BgGenSvc / GenerateScreen / health check) | 统一 OkHttpClient，共享连接池 |
| 健康检查 | 耦合在 BackgroundGenerationService companion | BackendManager.healthCheck() |
| 错误恢复 | Queue 循环内手动 restart | BackendManager 内部监控 + 自动/手动重启 |

---

## 4. 其它优化建议

### 4.1 GenerateScreen 的独立 HTTP 调用

**问题:** `GenerateScreen` 中 `tokenizePromptForGenerate()` 自己创建 `generateScreenTokenizeClient` 做 `POST /tokenize` 调用，绕过了服务层。

**建议:** Tokenize 调用应通过 BackendManager 的统一 HTTP 客户端，或者至少提取到 TokenizeService 中，避免 UI 层直接处理网络错误。

### 4.2 队列持久化

**问题:** `QueueRepository` 是纯内存状态（`MutableStateFlow<List<GenerationTask>>`），app 被杀后所有 PENDING 任务丢失。

**建议:** 将 PENDING/PROCESSING 的任务通过 Room 持久化，app 重启后恢复队列。

### 4.3 依赖注入

**问题:** 无 DI 框架；`remember{}` 在 Composable 中创建依赖；singleton object 模式不可测试。

**建议（渐进式）：** 不强制引入 Hilt/Koin，但至少将核心服务（BackendManager、QueueRepository、HistoryManager）集中由 `DreamAndroidApplication` 创建和持有，Composable 通过 `LocalContext.current.applicationContext` 获取。

---

## 5. 重构优先级

| 优先级 | 建议 | 影响范围 | 收益 |
|--------|------|---------|------|
| **P0** | 统一 BackendManager（合并 BackendService + UpscaleBackendManager） | BackendService, UpscaleBackendManager, MainActivity | 消除进程管理 bug，简化切换 |
| **P1** | QueueProcessingService 接管队列处理循环 | MainActivity, BackgroundGenerationService, QueueRepository | 减少 per-task overhead，后台稳定运行 |
| **P2** | 提取 ViewModel，减少 MainActivity 职责 | MainActivity, 各 Screen | 可测试，可维护 |
| **P3** | 队列持久化 (Room) | QueueRepository, QueueModels | 防丢任务 |
| **P4** | 统一 HTTP 客户端 (BackendManager.httpClient) | GenerateScreen tokenize, health check, generate, upscale | 连接池复用，一致超时配置 |
| **P5** | 引入 DI (Hilt/Koin 或 Application-level DI) | 全局 | 可测试性 |

---

---

## 6. 第二轮评审：新增架构问题

> 以下问题为深入审查后发现的补充问题，未在第 1-5 章中覆盖。

---

### 6.1 `runBlocking` 阻塞主线程 → 直接 ANR 风险

**涉及文件:** `data/Model.kt` — `Model.deleteModel()` 第 141-147 行

**问题:**
```kotlin
fun deleteModel() {
    // 在主线程被调用 (from dialog button onClick)
    runBlocking {  // ⚠ 阻塞主线程直到协程完成
        historyManager.clearHistoryForModel(modelId)
        generationPreferences.clearPreferencesForModel(modelId)
    }
}
```
`deleteModel()` 被 MainActivity 的删除确认对话框按钮在**主线程**直接调用。`runBlocking` 会阻塞主线程直到 Room I/O 完成——触发 ANR (Application Not Responding)。

**建议:** 将 `deleteModel()` 改为 `suspend` 函数，在 UI 层用 `coroutineScope.launch { }` 调用，移除所有 `runBlocking`。

---

### 6.2 协程 Scope 泄漏 — 不可取消的火灭协程

**涉及文件:**
- `service/UpscaleBackendManager.kt` (line 49): `CoroutineScope(Dispatchers.IO)` — 无 `Job()`，无取消机制
- `data/Model.kt` (lines 281, 390): `CoroutineScope(Dispatchers.Main).launch` in init blocks
- `utils/LogCapture.kt` (line 38): `CoroutineScope(Dispatchers.IO)` — 无 scope 管理

**问题:**
- `UpscaleBackendManager` 中启动的协程无法被取消——即使 `stop()` 被调用，协程仍在后台运行
- `ModelRepository` / `UpscalerRepository` init 中使用 `CoroutineScope(Dispatchers.Main)` 启动协程，scope 无生命周期绑定
- 这些 scope 永远无法被回收，造成协程泄漏

**建议:** 所有自定义 scope 必须有 `Job()` 和明确的取消时机（Service.onDestroy / ViewModel.onCleared / Application-scoped 需显式 cancel）。

---

### 6.3 OkHttpClient 重复创建 — 无连接池复用

**涉及文件:**
- `ui/screens/GenerateScreen.kt` — `generateScreenTokenizeClient` lazy 单 client
- `service/BackgroundGenerationService.kt` — `sharedClient` lazy 单 client
- `MainActivity.kt` — health check 中直接 `OkHttpClient.Builder().build()`（无复用）
- `utils/ImageUtils.kt` — `upscaleClient` lazy 单 client

**问题:** 应用中至少存在 **4 个独立的 OkHttpClient 实例**，每个都维护自己的连接池、DNS 缓存、SSL 上下文。对同一 `localhost:8081` 的请求被分散到不同 client，无法共享 keep-alive 连接。Health check 每次创建新 client 更是浪费。

**建议:** 由 `BackendManager` 或 `DreamAndroidApplication` 提供单一的 `OkHttpClient` 实例，所有 HTTP 调用通过它完成。

---

### 6.4 模型数据双源问题 — 文件系统 vs Room 无 Single Source of Truth

**涉及文件:**
- `data/Model.kt` — `ModelRepository` 从文件系统扫描模型目录
- `data/HistoryManager.kt` — Room 数据库存储生成历史

**问题:** 模型列表来自文件系统扫描（`getModelsDir()`），生成历史来自 Room。当模型被删除时：
1. 删除文件系统目录（`Model.deleteModel()`）
2. 清除 Room 中的关联记录（`historyManager.clearHistoryForModel()`）

如果第 1 步成功但第 2 步失败，Room 中存在"孤儿"记录指向不存在的模型。反之，如果清除了 Room 但目录未删除，文件系统存在垃圾文件。两个数据源之间没有事务一致性保证。

**建议:** Room 作为唯一数据源（包括模型元数据），文件系统目录仅作为存储位置。删除操作先更新 Room，再清理文件系统，Room 变更通过 Flow 驱动 UI。

---

### 6.5 SharedPreferences 管理碎片化

**涉及文件:**
- `data/GenerationPreferences.kt` — 使用 `"app_prefs"` 
- `data/HistoryManager.kt` — 内联 `PreferenceManager.getDefaultSharedPreferences()`
- `ui/screens/ModelRunScreen.kt` — 直接读写 model-specific prefs
- `ui/screens/UpscaleScreen.kt` — 使用 `"upscaler_prefs"`

**问题:** 存在至少 3 个不同的 SharedPreferences 文件，跨多个 UI 层直接读写。没有统一的 key 命名规范、类型安全访问、或迁移策略。UI 层直接写 SharedPreferences 违反了分层原则。

**建议:** 统一使用 DataStore (Preferences DataStore) 或至少集中所有 key 定义到一个 `PreferencesKeys` 对象。UI 层只通过 Repository 读写偏好。

---

### 6.6 原生进程生命周期管理不完整 — 僵尸进程风险

**涉及文件:**
- `service/BackendService.kt` — `stopBackend()` 
- `service/UpscaleBackendManager.kt` — `stop()`

**问题:**
```kotlin
process?.destroy()  // 仅发 SIGTERM，不等待退出
process = null      // 立即丢弃引用
```
`Process.destroy()` 发送 `SIGTERM` 后不调用 `waitFor()`。如果进程不响应 SIGTERM，它成为**僵尸进程**继续占用端口 8081。下一次启动后端时会因端口被占失败，而开发者无法通过 `process` 引用找到这个进程。

**建议:** 实现优雅关闭流程：`SIGTERM → waitFor(5s, TimeUnit.SECONDS) → 超时则 destroyForcibly() → waitFor()`。进程退出后才清空引用。

---

### 6.7 大 Bitmap 未主动回收 — 内存压力

**涉及文件:**
- `service/BackgroundGenerationService.kt` — `decodePreviewImage()` 产生中间 Bitmap
- `ui/screens/UpscaleScreen.kt` — 原图和结果图同时持有大 Bitmap
- `utils/ImageUtils.kt` — `performUpscale()` 创建中间 Bitmap

**问题:** 生成/超分流程会产生多个大 Bitmap（如 4096×4096 RGBA = 64MB 每个），旧 Bitmap 依赖 GC finalizer 回收。Android 的 `Bitmap.recycle()` 在 API 33+ 已被 deprecated 但 heavy Bitmap 在低端设备上仍需主动调用以缓解内存压力。没有全局的 Bitmap 池或释放策略。

**建议:** 建立 Bitmap 生命周期约定：用完后立即 `recycle()` + 置 null。考虑引入 LRU Bitmap 缓存上限。

---

### 6.8 SSE 流解析逻辑在 Service 中无法复用

**涉及文件:**
- `service/BackgroundGenerationService.kt` — 完整的 SSE 解析 inline 实现

**问题:** SSE 流解析（`{ "type": "progress/completed/error" }` 的行读逻辑）内联在 `BackgroundGenerationService` 约 100 行代码中。如果未来 `QueueProcessingService` 直接做 HTTP 调用（如第 2 章推荐），这段解析逻辑会需要重复。更关键的是，SSE 解析与 HTTP 传输层耦合，无法单测。

**建议:** 提取 `SseStreamParser` 独立类：输入 `InputStream`，输出 `Flow<SseEvent>`（sealed class: Progress / Complete / Error）。单测友好，Service 层只负责连接它。

---

### 6.9 错误处理不一致 — 吞错误 vs 用户可见

**涉及文件:** 多处

**问题:**
- Health check 失败：静默重试，无用户反馈直到达到 max failures
- Tokenize 失败：`GenerateScreen` 中 catch 后静默失败，token 计数显示 0/0
- Upscale 失败：`ImageUtils` 中 throw IOException 后 `UpscaleScreen` 显示浮动错误卡片
- SSE parse 错误：`BackgroundGenerationService` catch(JSONException) 后静默继续

没有统一的错误处理策略：user-facing errors、recoverable errors、fatal errors 处理方式分散且不一致。

**建议:** 定义错误密封类层次（`AppError` → `NetworkError` / `BackendError` / `ParseError` / `StorageError`），建立错误传播链：Service → ViewModel → UI 统一渲染。

---

### 6.10 模块化缺失 — 全部代码在单模块 `:app`

**涉及文件:** `app/build.gradle.kts`、整个 `app/` 目录

**问题:** 所有 Kotlin 代码（Service、UI、Data、Utils）都在 `:app` 模块中。没有模块边界意味着：
- 任何代码可以 import 任何其他代码（编译期无隔离）
- UI 层可以直接 import `BackgroundGenerationService.companion.checkBackendHealth()`
- 修改一个文件可能导致全量 recompile
- 无并行编译可能

**建议:** Gradle 多模块拆分（渐进式）：
```
:core:common      → Model/Error types, base interfaces
:core:backend     → BackendManager, health check, HTTP client
:core:data        → Room DAO, Repository, Preferences
:core:queue       → QueueRepository, SSE parser, task logic
:feature:models   → ModelListScreen, ModelRunScreen
:feature:generate → GenerateScreen
:feature:queue    → QueueScreen
:feature:upscale  → UpscaleScreen
:feature:browse   → BrowseScreen
:app              → MainActivity, Navigation, DI wiring
```

---

### 6.11 补充重构优先级

| 优先级 | 问题 | 风险等级 |
|--------|------|---------|
| **P0** | `runBlocking` 主线程阻塞 | ANR → Play Store 拒绝 |
| **P0** | 僵尸进程 (无 waitFor) | 后端不可用，需强杀 app |
| **P1** | 协程 Scope 泄漏 | 内存泄漏 + 后台无意义 CPU 消耗 |
| **P1** | 4 个 OkHttpClient 无连接池复用 | 延迟增加 + 资源浪费 |
| **P1** | 模型数据双源无一致性 | 孤儿记录 / 垃圾文件 |
| **P2** | SharedPreferences 碎片化 | 维护成本 + 迁移困难 |
| **P2** | SSE 解析不可复用不可测 | 重构阻塞 |
| **P2** | 大 Bitmap 无主动回收 | 低端设备 OOM |
| **P3** | 错误处理不一致 | 用户困惑 + 调试困难 |
| **P4** | 单模块无隔离 | 编译慢 + 耦合扩散 |

---

## 变更记录

| 日期 | 版本 | 描述 |
|------|------|------|
| 2026-06-13 | 1.0 | 初始版本，从 PrdReqDoc.md 第 10 章独立为架构评审文档 |
| 2026-06-13 | 1.1 | 新增第 6 章：第二轮评审补充问题 (11 项) |
