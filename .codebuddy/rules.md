# DreamHub AI Agent 规则

## 版本号管理

DreamHub 的版本号由仓库根目录的两个文件管理：

- `VERSION_NAME` — app versionName
- `VERSION_CODE` — app versionCode

`build.gradle.kts` 读取这两个文件构建 APK。

### VERSION_NAME

- **格式**：`YYYY.MM.DD.HH.mm`，使用 **UTC 时间**
- **补齐规则**：每段 2 位零补齐（补前导 0），如 `01`、`05`、`09`
- **正则校验**：`^\d{4}\.\d{2}\.\d{2}\.\d{2}\.\d{2}$`
- **示例**：`2026.06.13.05.22`（UTC 2026年6月13日 05:22）
- **CI 行为**：`build.yml` 在构建开始时严格校验此格式，不匹配则 fail

### VERSION_CODE

- **规则**：纯整数，每次发版 **+1**（Google-style version code）
- **示例**：`244` → `245`

### Release Tag

- **格式**：`v{YYYY.MM.DD.HH.MM}`，必须以 `v` 开头
- **正则**：`v*`
- **示例**：`v2026.06.13.05.22`
- **CI 触发**：
  - `startsWith(github.ref, 'refs/tags/v')` 触发 native C++ 编译（stage 1）
  - 触发 APK artifact 上传
  - 触发 GitHub Release 发布
  - Release APK 重命名为 `DreamHub-{VERSION}-arm64-v8a-release.apk`（去掉 v 前缀）

### 发版操作流程

1. 获取当前 UTC 时间，格式化为 `YYYY.MM.DD.HH.mm`（每段 2 位零补齐）
2. 更新 `VERSION_NAME` 为当前 UTC 分钟值
3. 将 `VERSION_CODE` 加 1
4. `git add VERSION_NAME VERSION_CODE`
5. `git commit -m "chore: bump version to {VERSION_NAME} (code {VERSION_CODE})"`
6. `git tag -a "v{VERSION_NAME}" -m "Release v{VERSION_NAME}"`
7. `git push && git push origin "v{VERSION_NAME}"`

### CI 工作流

- 触发条件：push to `master`/`main`，tag `v*`，或 `workflow_dispatch`
- Stage 1 (`native`)：仅 tag push 或手动触发时编译 C++ 原生库
- Stage 2 (`apk`)：编译 Kotlin + 打包 APK（依赖 native，允许跳过）
- Stage 3 (`github-release`)：仅 tag push 或手动 release=true 时创建 GitHub Release
