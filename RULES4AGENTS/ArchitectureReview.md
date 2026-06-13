# DreamHub Architecture Review & Optimization Plan

> 版本: 2.0
> 更新日期: 2026-06-13
> 本文档按功能模块组织，对 DreamHub 现有架构问题进行深度分析并提供重构方案。
> 每个模块列出该模块内所有问题及其统一解决方案。

---

## 1. BackendManager — 统一后端进程管理

> **涉及文件:** `service/BackendService.kt`, `service/UpscaleBackendManager.kt`, `MainActivity.kt`

### 1.1 现状

两个独立的管理器操作同一个端口 8081，MainActivity 手动协调切换：

```
BackendService (Diffusion)          UpscaleBackendManager (单例)
├── Foreground Service              ├── object 单例（非 Service）
├── 端口: 8081                      ├── 端口: 8081
├── 状态: Idle/Starting/Running/Err ├── 状态: Idle/Starting/Running/Error
└── 管理: Process lifecycle          └── 管理: Process lifecycle

MainActivity 手动协调:
  loadUpscaleModel() { stopService(BackendService) → UpscaleBackendManager.start() }
  loadModel()       { UpscaleBackendManager.stop()  → startForegroundService(BackendService) }
```

### 1.2 问题清单

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 1.2.1 | 进程所有权混乱 | P0 | 两个管理者可能同时持有 Process 引用，竞争端口 8081 |
| 2.2.2 | 状态机不统一 | P1 | `BackendService.BackendState` 和 `UpscaleBackendManager.State` 语义重叠但独立定义 |
| 2.2.3 | 切换逻辑泄漏到 UI | P1 | MainActivity 硬编码 `stopService + start`，服务层应自协调 |
| 2.2.4 | 代码重复 | P1 | `prepareRuntimeDir()` (QNN 库复制) 在 BackendService 和 UpscaleBackendManager 各 ~50 行重复 |
| 2.2.5 | 生命周期不一致 | P1 | BackendService 有前台通知保护，UpscaleBackendManager 无 → 后者易被系统 kill |
| 2.2.6 | 僵尸进程风险 | P0 | `process?.destroy()` 不调 `waitFor()`，进程不响应 SIGTERM 时端口永久被占 |

### 1.3 统一方案：BackendManager

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

    // 模式切换：内部自动 stop 旧进程 → start 新进程
    fun startDiffusion(modelId: String, width: Int, height: Int, useOpenCL: Boolean)
    fun startUpscaler(upscalerId: String)

    // 优雅关闭：SIGTERM → waitFor(5s) → 超时 destroyForcibly() → waitFor()
    fun stop()

    // 统一 health check + 统一 OkHttpClient（见第 4 章）
    suspend fun healthCheck(): Boolean
    val httpClient: OkHttpClient
}
```

**与旧架构对比：**

| 维度 | 旧 | 新 |
|------|-----|-----|
| 进程唯一性 | 手动协调，易出错 | 内部保证 only-one-process |
| 进程退出 | `destroy()` 无 `waitFor()` | 优雅关闭 3 步流程 |
| 状态观察 | 两个独立 StateFlow | 单一 `StateFlow<State>` |
| 环境准备 | 两份 `prepareRuntimeDir()` | 启动时统一准备一次 |
| 生命周期 | Service vs Singleton 不一致 | 统一 Foreground Service 保护 |
| 切换逻辑 | 泄漏到 MainActivity | 封装在 `startXxx()` 内部 |

---

## 2. QueueProcessingService — 队列处理管道

> **涉及文件:** `service/BackgroundGenerationService.kt`, `service/QueueRepository.kt`, `data/QueueModels.kt`, `MainActivity.kt`

### 2.1 现状

当前是 "per-task Foreground Service" 模式，处理循环在 MainActivity 的 LaunchedEffect 中：

```
任务1: startForegroundService(BgGenService) → 生成 → stopSelf → wait stop
任务2: startForegroundService(BgGenService) → 生成 → stopSelf → wait stop
...

MainActivity LaunchedEffect:
  ├── Health Check → 重启 BackendService → 启 BgGenService
  ├── 等 generationState.first { Complete|Error }
  ├── 保存 HistoryManager → 等 isServiceRunning → false
  └── 下一轮...
```

### 2.2 问题清单

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 2.2.1 | per-task Service 开销大 | P0 | 每个任务创建/通知/销毁 Foreground Service，10 张图 = 10 次生命周期切换 |
| 2.2.2 | 队列循环在 UI 线程 | P0 | LaunchedEffect 依赖 Activity 存活，后台被杀则队列丢失 |
| 2.2.3 | 静态 companion 共享状态 | P1 | `generationState` / `_stopRequested` / `_bitmapConsumed` 全局可变，隐式耦合 |
| 2.2.4 | busy-wait 在 UI 层 | P2 | `while (isServiceRunning) { delay(100) }` 在 MainActivity 协程中 |
| 2.2.5 | 队列无持久化 | P1 | app 被杀后 PENDING 任务全部丢失 |
| 2.2.6 | SSE 解析不可复用 | P2 | ~100 行 SSE 解析内联在 Service 中，与 HTTP 传输层耦合，无法单测 |
| 2.2.7 | 大 Bitmap 未主动回收 | P2 | 4096×4096 RGBA = 64MB/张，依赖 GC 回收，低端设备 OOM |

### 2.3 统一方案：QueueProcessingService + SseStreamParser

```kotlin
// 独立的 SSE 解析器（可单测）
class SseStreamParser(inputStream: InputStream) {
    fun events(): Flow<SseEvent>  // sealed: Progress / Complete / Error
}

// 持久 Foreground Service，顺序处理队列
class QueueProcessingService : Service() {
    private lateinit var backendManager: BackendManager
    private lateinit var queueRepository: QueueRepository
    private lateinit var historyManager: HistoryManager

    private fun processLoop() {
        while (queueRepository.hasNextPending()) {
            val task = queueRepository.nextPending()
            // 1. Health Check (复用 BackendManager.httpClient)
            if (!backendManager.healthCheck()) backendManager.restart()
            // 2. POST /generate, SSE streaming via SseStreamParser
            // 3. Progress → queueRepository.updateProgress()
            // 4. Complete → historyManager.save() + recycle Bitmap
            // 5. Error   → queueRepository.markError()
        }
    }
}
```

**与旧架构对比：**

| 维度 | 旧 | 新 |
|------|-----|-----|
| 任务间延迟 | Service 创建/销毁 ~500ms+ | 零切换，直接下一轮 |
| 状态管理 | 静态 companion 全局可变 | Service 实例私有 |
| 生命周期 | 依赖 Activity | 独立 Foreground Service |
| 队列持久化 | 纯内存，丢失 | Room 持久化 PENDING/PROCESSING |
| SSE 解析 | ~100 行内联在 Service | `SseStreamParser` 独立类，可单测 |
| Bitmap 回收 | 依赖 GC | `Complete → consume → recycle()` |

---

## 3. MainActivity — UI 层拆分

> **涉及文件:** `MainActivity.kt`, `ui/screens/*.kt`

### 3.1 现状

`AppContent()` 是一个 ~900 行的 Composable 函数：

```
AppContent()
├── UI 状态管理 (selectedTab, selectedModelId, 全部gen参数...)
├── 队列处理循环 (LaunchedEffect)
├── 模型加载/卸载/导入 (loadModel, unloadModel, convertCustomModel...)
├── 对话框/删除/重命名 (showRenameDialog, showDeleteConfirm)
├── 参数持久化 (LaunchedEffect 读 GenerationPreferences)
└── 后端重启逻辑
```

### 3.2 问题清单

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 3.2.1 | God Object | P0 | 30+ `remember{}` / `mutableStateOf()` 散落，任何 state 变化触发全量重组 |
| 3.2.2 | 无法单独测试 | P1 | 所有逻辑耦合在 Composable 生命周期中 |
| 3.2.3 | 错误处理不一致 | P2 | Health check 静默重试 / Tokenize 静默失败 / Upscale 浮动卡片 / SSE 静默继续 — 4 种风格 |
| 3.2.4 | 无 DI | P2 | `remember{}` 创建依赖，singleton object 不可测试 |
| 3.2.5 | UI 层直接 HTTP | P2 | `GenerateScreen.tokenizePromptForGenerate()` 自己建 OkHttpClient 调 `/tokenize` |

### 3.3 统一方案：ViewModel + 错误密封类 + Application DI

```kotlin
// 统一错误模型
sealed class AppError {
    data class Network(override val message: String) : AppError()
    data class Backend(override val message: String) : AppError()
    data class Parse(override val message: String) : AppError()
    data class Storage(override val message: String) : AppError()
}

// DI: Application 统一持有
class DreamAndroidApplication : Application() {
    lateinit var backendManager: BackendManager
    lateinit var queueRepository: QueueRepository
    lateinit var historyManager: HistoryManager
}

// ViewModel 管理业务状态
class MainViewModel(application: Application) : AndroidViewModel(application) {
    // selectedTab, selectedModelId, generation params...
    // 队列调度逻辑或委托给 QueueProcessingService
}

// UI 只渲染 + 转发事件
AppContent(viewModel: MainViewModel) {
    // Composable → ViewModel → Service → Backend
}
```

**建议的 ViewModel 拆分：**

| ViewModel | 管理内容 |
|-----------|---------|
| `MainViewModel` | 导航状态、全局标志 |
| `ModelsViewModel` | 模型列表、加载/卸载/导入/删除 |
| `QueueViewModel` | 队列状态观察（只读） |
| `GenerateViewModel` | 生成参数、tokenize 调用 |
| `UpscaleViewModel` | 超分图片选择、执行、结果 |
| `BrowseViewModel` | 历史记录浏览、筛选、多选 |

---

## 4. HttpClient — 统一网络层

> **涉及文件:** `service/BackgroundGenerationService.kt`, `ui/screens/GenerateScreen.kt`, `utils/ImageUtils.kt`, `MainActivity.kt`

### 4.1 现状

应用中存在 **4 个独立的 OkHttpClient 实例**，各自维护连接池：

| 位置 | 变量名 | 用途 |
|------|--------|------|
| `BackgroundGenerationService` | `sharedClient` (lazy) | POST /generate |
| `GenerateScreen` | `generateScreenTokenizeClient` (lazy) | POST /tokenize |
| `ImageUtils` | `upscaleClient` (lazy) | POST /upscale |
| `MainActivity` health check | 每次 `OkHttpClient.Builder().build()` | GET /health |

### 4.2 问题清单

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 4.2.1 | 连接池无法共享 | P1 | 同一 localhost:8081 的请求分散到 4 个 client，keep-alive 失效 |
| 4.2.2 | Health check 每次建新 client | P1 | 资源浪费 |
| 4.2.3 | 超时配置不一致 | P2 | 4 处可能有不同的超时参数 |
| 4.2.4 | UI 层直接处理 HTTP | P2 | GenerateScreen 绕过服务层调 `/tokenize`，网络错误直接泄漏到 UI |

### 4.3 统一方案：BackendManager.httpClient

```kotlin
class BackendManager(private val context: Context) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)   // 生成耗时
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        .build()

    // 所有端点通过此方法调用
    suspend fun healthCheck(): Boolean
    fun tokenize(prompt: String): TokenizeResult
    fun generate(params: GenerateParams): Flow<SseEvent>
    fun upscale(input: ByteArray, width: Int, height: Int, upscalerPath: String): ByteArray
}
```

---

## 5. Data Layer — 数据持久化

> **涉及文件:** `data/Model.kt`, `data/HistoryManager.kt`, `data/GenerationPreferences.kt`

### 5.1 问题清单

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 5.1.1 | 模型数据双源无 Single Source of Truth | P1 | 文件系统扫描模型列表 + Room 存历史，删除时二者无事务一致性 → 孤儿记录/垃圾文件 |
| 5.1.2 | SharedPreferences 碎片化 | P2 | 3 个 pref 文件 (`app_prefs`, `upscaler_prefs`, default) + UI 层直接读写 |
| 5.1.3 | 队列无持久化（见 2.2.5） | P1 | 已归入第 2 章处理 |

### 5.2 模型数据统一方案

```kotlin
// Room 作为唯一数据源
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val modelId: String,
    val name: String,
    val type: ModelType,  // Diffusion/Upscaler
    val backendType: BackendType?,
    val filePath: String,   // 文件系统仅作存储位置
    val sizeBytes: Long,
    val downloaded: Boolean
)

// Repository 保证一致性
class ModelRepository(db: AppDatabase) {
    suspend fun deleteModel(modelId: String) {
        db.withTransaction {
            modelDao.delete(modelId)
            historyDao.clearForModel(modelId)  // 同一事务
        }
        // 事务成功后清理文件系统（可容忍失败）
        File(modelsDir, modelId).deleteRecursively()
    }
}
```

### 5.3 SharedPreferences 统一方案

```kotlin
// 集中所有 key 定义
object PrefKeys {
    // App
    const val DYNAMIC_COLOR = "dynamic_color"
    const val DARK_MODE = "dark_mode"
    // Generate
    const val PROMPT = "gen_prompt"
    const val NEGATIVE_PROMPT = "gen_negative_prompt"
    // ... 所有 key 集中管理

// 单一 PreferencesManager (DataStore)
class PreferencesManager(context: Context) {
    private val dataStore = context.dataStore
    // typed accessors: Flow / suspend set
}

// UI 层只通过 ViewModel → PreferencesManager 读写
```

---

## 6. Coroutine & Lifecycle — 协程与生命周期

> **涉及文件:** `data/Model.kt`, `service/UpscaleBackendManager.kt`, `utils/LogCapture.kt`

### 6.1 问题清单

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 6.1.1 | `runBlocking` 阻塞主线程 | P0 | `Model.deleteModel()` 在主线程调 `runBlocking{ Room I/O }` → ANR |
| 6.1.2 | 协程 Scope 泄漏 (3 处) | P1 | `UpscaleBackendManager` / `ModelRepository.init` / `LogCapture` 无 Job 无取消 → 无法回收 |

### 6.2 修复方案

**6.1.1 `runBlocking` 修复：**

```kotlin
// Before: 主线程阻塞
fun deleteModel() { runBlocking { historyManager.clearHistoryForModel(id) } }

// After: suspend + 协程调用
suspend fun deleteModel() { historyManager.clearHistoryForModel(id) }

// UI 层:
scope.launch { model.deleteModel() }
```

**6.1.2 Scope 泄漏修复：**

| 位置 | 修复 |
|------|------|
| `UpscaleBackendManager` | `CoroutineScope(SupervisorJob() + Dispatchers.IO)`，`stop()` 中 `scope.cancel()` |
| `ModelRepository.init` | 从 init 中移出，改为 `fun startObserving(scope: CoroutineScope)` 由调用方传 scope |
| `LogCapture` | 改为 `GlobalScope` 或绑定 Application 生命周期 |

---

## 7. Modularization — 项目模块化

> **涉及文件:** `app/build.gradle.kts`, 整个 `app/` 目录

### 7.1 问题

所有 Kotlin 代码在单 `:app` 模块，无编译期隔离：UI 可 import Service companion，改一行全量 recompile。

### 7.2 推荐多模块拆分

```
:core:common      → Model/Error types, base interfaces, extensions
:core:backend     → BackendManager, health check, HttpClient, SSE parser
:core:data        → Room DAO/Entities, Repository, PreferencesManager
:core:queue       → QueueRepository, GenerationTask, task scheduling
:feature:models   → ModelListScreen, ModelRunScreen
:feature:generate → GenerateScreen
:feature:queue    → QueueScreen
:feature:upscale  → UpscaleScreen
:feature:browse   → BrowseScreen
:app              → MainActivity, Navigation, DI wiring
```

**渐进策略：** 先拆 `:core:common` + `:core:backend`，其余逐步推进。

---

## 8. 重构优先级（跨模块统一排序）

| 优先级 | 所属模块 | 问题 | 风险 |
|--------|---------|------|------|
| **P0** | BackendManager | 进程所有权混乱 (1.2.1) | 端口竞争 |
| **P0** | BackendManager | 僵尸进程 (1.2.6) | 后端永久不可用 |
| **P0** | Coroutine | `runBlocking` 主线程阻塞 (6.1.1) | ANR |
| **P0** | Queue | per-task Service 开销 (2.2.1) | 延迟累积 |
| **P0** | Queue | 队列循环依赖 UI 生命周期 (2.2.2) | 后台被杀丢队列 |
| **P0** | MainActivity | God Object (3.2.1) | 无法维护 |
| **P1** | BackendManager | 状态机不统一 (1.2.2) | 维护成本 |
| **P1** | BackendManager | 切换逻辑泄漏到 UI (1.2.3) | 耦合 |
| **P1** | BackendManager | prepareRuntimeDir 代码重复 (1.2.4) | Bug 不一致 |
| **P1** | BackendManager | Upscale 无前台通知保护 (1.2.5) | 易被 kill |
| **P1** | Queue | 静态 companion 共享状态 (2.2.3) | 隐式耦合 |
| **P1** | Queue | 队列无持久化 (2.2.5) | 丢任务 |
| **P1** | HttpClient | 4 个 OkHttpClient 无复用 (4.2.1) | 连接池浪费 |
| **P1** | HttpClient | Health check 每次新 client (4.2.2) | 资源浪费 |
| **P1** | Data | 模型数据双源 (5.1.1) | 孤儿记录/垃圾文件 |
| **P1** | Coroutine | 协程 Scope 泄漏 (6.1.2) | 内存泄漏 |
| **P2** | Queue | busy-wait 在 UI 层 (2.2.4) | 浪费 |
| **P2** | Queue | SSE 解析不可复用 (2.2.6) | 重构阻塞 |
| **P2** | Queue | 大 Bitmap 未回收 (2.2.7) | 低端 OOM |
| **P2** | MainActivity | 错误处理不一致 (3.2.3) | 调试困难 |
| **P2** | MainActivity | 无 DI (3.2.4) | 不可测试 |
| **P2** | MainActivity | UI 层直接 HTTP (3.2.5) | 违反分层 |
| **P2** | HttpClient | 超时配置不一致 (4.2.3) | 行为不可预期 |
| **P2** | Data | SharedPreferences 碎片化 (5.1.2) | 迁移困难 |
| **P3** | HttpClient | UI 层处理 HTTP 错误 (4.2.4) | 体验不一致 |
| **P3** | Modularization | 单模块无隔离 (7.1) | 编译慢 + 耦合扩散 |

---

## 变更记录

| 日期 | 版本 | 描述 |
|------|------|------|
| 2026-06-13 | 1.0 | 初始版本，从 PrdReqDoc.md 独立，按发现顺序组织 |
| 2026-06-13 | 1.1 | 新增第二轮评审 11 项补充问题 |
| 2026-06-13 | 2.0 | 按功能模块重组织为 7 个模块，统一优先级排序 |
