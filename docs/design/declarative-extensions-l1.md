# L1：声明式扩展系统设计（Squircle CE）

> 状态：设计草案（未实现）
> 范围：数据型扩展（主题 / 字体 / 语法 / 语言关联），**不执行任何第三方代码**
> 后续层：L2 = 命令注册表 + Termux 脚本；L3 = 独立 APK 插件（明确不做）

---

## 0. 核心取舍

| 决策 | 选择 | 理由 |
|---|---|---|
| 是否加载可执行代码 | **否** | 避免 API 兼容契约、进程隔离、安全审核三座大山 |
| 分发方式 | **内置索引仓库 + 本地导入复用同一管线** | 索引只是"发现"渠道，不改变安装管线 |
| 扩展粒度 | zip 包（含 `extension.json`） | 可内嵌 tmLanguage/主题/字体，比单文件灵活 |
| 主题/语法冲突 | 内置资源优先（内置赢） | 用户升级 App 后行为可预测 |
| 卸载后内存残留 | 标记删除，**下次冷启动生效** | sora-editor 的 `GrammarRegistry`/`ThemeRegistry` 是全局单例，不 fork 也能做 |
| 索引网络策略 | 显式操作才联网 + 内置离线索引兜底 | F-Droid 合规、离线可用 |

---

## 1. 目标 / 非目标

**目标（L1 完成时用户能做的事）**

1. 从内置索引浏览并安装扩展
2. 从本地 zip 导入扩展（同一套manifest 与安装管线）
3. 安装后：新增语法高亮、新增文件扩展名关联、新增 UI 主题、新增等宽字体
4. 启用 / 禁用 / 卸载扩展、查看扩展详情与错误原因
5. 扩展导致的解析失败**不能**让 App 崩溃（fail-closed + 可见错误）

**非目标（明确不在 L1）**

- snippets 的消费端（schema 保留字段，L1.5 再做）
- keybindings 的消费端（L2 与 CommandRegistry 一起做）
- 脚本 / 命令 / 网络能力 / 文件系统插件
- 语义能力（补全 / 诊断 / 跳转 / LSP）——见 §17，属 L1.5 / L2 / L3，不在 L1
- 扩展自动更新（只做"有新版本"提示 + 手动更新）
- 插件间依赖解析

---

## 2. 架构总览

```
                    ┌─────────────────────────────┐
   index.json ─────►│  feature-extensions (UI)     │
   (HTTPS/内置)     │  Browse / Installed / 详情    │
                    └──────────────┬──────────────┘
                                   │ ExtensionInteractor
                    ┌──────────────▼──────────────┐
                    │  core-extension             │
                    │  ┌────────────────────────┐ │
   local zip ──────►│  │ ExtensionInstaller     │ │  zip4j + sha256 + 原子替换
                    │  ├────────────────────────┤ │
                    │  │ ExtensionRegistry      │ │  Room + 内存快照 + Flow
                    │  ├────────────────────────┤ │
                    │  │ ExtensionFileResolver  │ │  给 sora-editor 的 FileProvider
                    │  └────────────────────────┘ │
                    └───┬──────────┬──────────┬───┘
                        │          │          │
              ┌─────────▼──┐  ┌────▼─────┐  ┌─▼──────────────┐
              │feature-    │  │feature-  │  │feature-editor  │
              │themes      │  │fonts     │  │语法 + 关联      │
              └────────────┘  └──────────┘  └────────────────┘
```

依赖方向：`core-extension` 只依赖 `core-common` + `core-ui`（不依赖任何 `feature-*`）。
三个消费者各自依赖 `core-extension` 的契约，互不依赖。

---

## 3. 扩展包格式

### 3.1 zip 布局

```
acme.toml-support-1.2.0.zip
├── extension.json            # 必需，清单
├── themes/
│   └── dark.json             # TextMate 主题（与现有 ExternalTheme 格式一致）
├── syntaxes/
│   └── toml.tmLanguage.json  # TextMate 语法
├── language-configuration/
│   └── toml.json             # 可选
├── fonts/
│   └── acme-mono.ttf         # 可选，仅 .ttf/.otf
└── icons/
    └── file-toml.svg         # 可选（L1.5）
```

**白名单目录**：`themes/`、`syntaxes/`、`language-configuration/`、`fonts/`、`icons/`。
根目录只允许 `extension.json`。其他任何路径/文件 = 安装失败。

### 3.2 `extension.json`

```json
{
  "schemaVersion": 1,
  "id": "acme.toml-support",
  "publisher": "acme",
  "displayName": "TOML Support",
  "description": "TOML 语法高亮与文件关联",
  "version": "1.2.0",
  "minVersionCode": 10028,
  "contributes": {
    "languages": [
      {
        "scopeName": "source.toml",
        "name": "toml",
        "displayName": "TOML",
        "extensions": [".toml"],
        "filenames": ["Cargo.lock"],
        "mimeTypes": ["application/toml"],
        "grammar": "extension://acme.toml-support/syntaxes/toml.tmLanguage.json",
        "languageConfiguration": "extension://acme.toml-support/language-configuration/toml.json",
        "embeddedLanguages": { "meta.embedded.block.markdown": "source.toml" }
      }
    ],
    "themes": [
      {
        "id": "toml-dark",
        "path": "themes/dark.json",
        "uiTheme": "dark",
        "displayName": "TOML Dark"
      }
    ],
    "fonts": [
      {
        "id": "acme-mono",
        "path": "fonts/acme-mono.ttf",
        "displayName": "Acme Mono"
      }
    ],
    "snippets": [{ "language": "source.toml", "path": "snippets/toml.json" }],
    "keybindings": [{ "command": "editor.save", "keys": ["ctrl+s"] }],
    "completions": [{ "language": "source.toml", "path": "completions/toml.json" }],
    "lsp": [
      {
        "language": "source.toml",
        "transport": { "type": "tcp", "host": "127.0.0.1", "port": 38123 },
        "launch": { "type": "none" },
        "installHint": "Termux: pkg install taplo && taplo lsp stdio"
      }
    ]
  }
}
```

**规则**

- `schemaVersion` 当前只接受 `1`；未知值 → 拒绝安装（明确报错，不猜测）
- `id` 必须匹配 `^[a-z0-9][a-z0-9._-]{2,63}$`，且 `publisher` 是 `id` 的第一段
- `grammar` / `languageConfiguration` **必须**用 `extension://<id>/…` 前缀，解析时按扩展 id 定位
- `themes[].id`、`fonts[].id` 是**扩展内局部 id**，全局 id 由宿主拼接：
  - 主题：`ext:<extensionId>/<themeId>`（对应 `SettingsManager.KEY_EDITOR_THEME` 的存量裸 id）
  - 语法：直接用 `scopeName`，与 assets 冲突时**内置优先**，并在扩展详情页显示"被内置语法覆盖"
- `snippets` / `keybindings` / `completions` / `lsp` 在 L1 只做**解析与存储**，不消费
  （`completions` 消费者在 L1.5，`keybindings` 在 L2，`lsp` 在 L3 —— 见 §17）

### 3.3 与现有模型的对齐

| 现有代码 | 复用什么 |
|---|---|
| `feature-themes/.../data/model/ExternalTheme.kt` | 扩展主题文件格式**完全一致**，`ThemeMapper` 不用改 |
| `feature-editor/.../data/model/GrammarData.kt` | 字段一一对应（`name/displayName/scopeName/grammar/languageConfiguration/embeddedLanguages`） |
| `feature-editor/.../data/model/LanguageScope.kt` | 内置常量表保留；扩展的 scopeName 走运行时注册表 |
| 序列化 | `CoreModule.provideJson()` 已设 `ignoreUnknownKeys = true`，前向兼容免费拿到 |

---

## 4. 索引仓库

### 4.1 `index.json`

托管在独立的 GitHub 仓库（raw 直链），由维护者手工/CI 生成：

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-01-10T00:00:00Z",
  "extensions": [
    {
      "id": "acme.toml-support",
      "displayName": "TOML Support",
      "publisher": "acme",
      "summary": "TOML 语法高亮与文件关联",
      "iconUrl": "https://raw.githubusercontent.com/.../toml.png",
      "versions": [
        {
          "version": "1.2.0",
          "minVersionCode": 10028,
          "downloadUrl": "https://github.com/.../releases/download/v1.2.0/acme.toml-support-1.2.0.zip",
          "sha256": "9f2c…",
          "sizeBytes": 48213
        }
      ]
    }
  ]
}
```

- `versions` 取**最高可行版本**（`minVersionCode <= 当前 versionCode`）
- 当前 versionCode 通过 `PackageInfoCompat.getLongVersionCode` 读取，复用 `feature-settings/data/Extensions.kt` 里已有的 `Context.versionCode` 扩展模式（比引入 BuildConfig 更省事）

### 4.2 索引来源优先级

1. 用户在设置里填的自定义索引 URL（可选，L1.5）
2. 内置默认索引 URL（`https://raw.githubusercontent.com/<org>/squircle-extensions/main/index.json`）
3. **APK 内 `assets/extensions-index.json`**（离线兜底；安装仍然需要联网，但"浏览"不空屏）

### 4.3 缓存与刷新

- `cache/extensions/index.json` + `cache/extensions/index.etag`
- 请求带 `If-None-Match`，304 则复用缓存
- **只在两种时机刷新**：用户主动下拉/点刷新；进入 Browse 页且缓存超过 24h（且**仅一次**，失败不重试不弹窗）

---

## 5. 安装管线

```
下载/选中 zip
   → ① 大小检查（≤ 20 MB）
   → ② sha256 校验（索引来源才有）
   → ③ 解压到 files/extensions/.staging/<id>-<随机>（zip4j）
   → ④ zip 条目安全校验（见 §11）
   → ⑤ 解析 + 校验 extension.json（§3.2 规则 + minVersionCode）
   → ⑥ 语义校验（语法文件可解析、主题文件可解析、字体可加载）
   → ⑦ 与已安装版本比较（同 id 且 version 相同 → 询问覆盖）
   → ⑧ 原子替换：rm -rf target && staging.renameTo(target)
   → ⑨ Room upsert（enabled = true）+ Registry.refresh()
```

**任何一步失败**：删除 staging 目录、保持旧版本原样、把错误分类（`DownloadFailed` / `ChecksumMismatch` / `InvalidPackage` / `UnsupportedVersion` / `ParseFailed`）返回给 UI 显示。

**关键约束**

- `files/extensions/<id>/` 就是最终目录，扩展内部路径按 `<id>` 定位，不需要记录绝对路径（App 私有目录路径在备份/迁移后会变）
- 安装必须**幂等**：同一个 zip 装两次结果一致
- 更新 = 安装到 `.staging` 成功后再替换，避免"更新一半打不开"

---

## 6. 数据模型与迁移

### 6.1 新表

```kotlin
// core-common/.../database/entity/extension/ExtensionEntity.kt
@Entity(tableName = Tables.EXTENSIONS)
data class ExtensionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "install_type") val installType: String,   // "index" | "local"
    @ColumnInfo(name = "source_url") val sourceUrl: String?,       // 索引来源才有
    @ColumnInfo(name = "installed_at") val installedAt: Long,
    @ColumnInfo(name = "manifest") val manifest: String,           // 原始 JSON，避免表结构随贡献点膨胀
)
```

> **决策**：清单原文存 JSON 列，而不是给每种贡献点建子表。新增贡献类型（L2 的 commands）只需改解析器，不需要 Room migration。

### 6.2 改动清单

| 文件 | 改动 |
|---|---|
| `core-common/.../database/utils/Tables.kt` | `const val EXTENSIONS = "tbl_extensions"` |
| `core-common/.../database/dao/extension/ExtensionDao.kt` | 新增（`Flow<List<ExtensionEntity>>`、upsert、delete、setEnabled） |
| `core-common/.../database/AppDatabase.kt` | `fun extensionDao(): ExtensionDao` |
| `core-common/.../database/AppDatabaseImpl.kt` | `version = 6`、`entities += ExtensionEntity::class`、`abstract override fun extensionDao()` |
| `core-common/.../database/utils/Migrations.kt` | `MIGRATION_5_6`（`CREATE TABLE tbl_extensions …`） |
| `core-common/.../internal/CoreModule.kt` | `.addMigrations(..., Migrations.MIGRATION_5_6)` |
| `core-common/src/androidTest/.../MigrationTest.kt` | 补 5→6 用例 |
| `core-common/schemas/` | Room 自动生成 `6.json`（**要一起提交**） |

**待确认**：DAO 放 `core-common`（现状所有 DAO 都在 core）还是放 `core-extension`。
倾向 `core-common`：现有约定是"一个 AppDatabase 一家管"，`core-extension` 通过 `CoreApi.provideAppDatabase()` 拿。

---

## 7. 注册表 API（`core-extension` 对外契约）

```kotlin
// 对外只暴露这几个类型给 feature-* 消费
interface ExtensionRegistry {
    /** 内存快照，冷启动后由 refresh() 填充；UI 不要直接读 Room */
    fun contributions(): ExtensionContributions
    fun observe(): Flow<ExtensionContributions>
    /** 全量重建快照：读 Room + 扫描目录 + 解析清单。失败项进 [ExtensionContributions.errors] */
    suspend fun refresh()
}

data class ExtensionContributions(
    val languages: List<ContributedLanguage> = emptyList(),
    val themes: List<ContributedTheme> = emptyList(),
    val fonts: List<ContributedFont> = emptyList(),
    val errors: List<ContributionError> = emptyList(),   // { extensionId, message }
)

data class ContributedLanguage(
    val extensionId: String,
    val scopeName: String,
    val name: String,
    val displayName: String,
    val extensions: List<String>,      // [".toml"]
    val filenames: List<String>,       // ["Cargo.lock"]
    val mimeTypes: List<String>,
    val grammarPath: String,           // "extension://acme.toml-support/syntaxes/toml.tmLanguage.json"
    val languageConfigurationPath: String?,
    val embeddedLanguages: Map<String, String>,
)

data class ContributedTheme(
    val extensionId: String,
    val themeId: String,               // 全局 id：ext:acme.toml-support/toml-dark
    val displayName: String,
    val uiTheme: String,               // "dark" | "light"
    val file: File,
)

data class ContributedFont(
    val extensionId: String,
    val fontId: String,                // 全局 id：ext:acme.toml-support/acme-mono
    val displayName: String,
    val file: File,
)
```

`ExtensionInteractor`（UI 侧用的用例层，放 `feature-extensions/api`）：

```kotlin
interface ExtensionInteractor {
    fun observeInstalled(): Flow<List<ExtensionModel>>
    suspend fun loadIndex(force: Boolean): List<IndexEntryModel>
    suspend fun installFromIndex(id: String): Result<Unit>
    suspend fun installFromFile(uri: Uri): Result<Unit>
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun uninstall(id: String)
}
```

---

## 8. 四处接入改造

### 8.1 语法 + 文件关联（`feature-editor/impl`）

**现状问题**：`LanguageInteractorImpl` 在 `init` 里只注册了 `AssetsFileResolver`；`FileAssociation` 是 `HashMap` 常量表。

**改造 A：grammar 来源抽象**

```kotlin
// core-extension 提供
interface GrammarSource {
    suspend fun load(): List<GrammarDefinitionModel>
}
```

- `AssetsGrammarSource`：现有 `assets/languages.json` 逻辑原样搬过来
- `ExtensionGrammarSource`：从 `ExtensionRegistry.contributions().languages` 映射

`LanguageInteractorImpl.loadGrammars()` 改为合并两个来源，**按 scopeName 去重且内置优先**。

**改造 B：FileProvider 注册**

```kotlin
// LanguageInteractorImpl.init
FileProviderRegistry.getInstance().apply {
    addFileProvider(AssetsFileResolver(context.assets))
    addFileProvider(ExtensionFileResolver(context))   // canProcess("extension://…")
}
```

`ExtensionFileResolver` 逻辑：`path` 形如 `extension://<id>/<relative>` → 拼 `files/extensions/<id>/<relative>` → 做**路径逃逸校验**后返回 `FileInputStream`。

> ⚠️ 待验证：sora-editor 的 `FileProvider` / `FileProviderRegistry` 具体 SPI（`canProcess` / `getInputStream` 的签名、是否支持任意 scheme）。本地 `sora-editor/` 子模块未 checkout，需先 `git submodule update --init` 确认。若不支持多 scheme，退路是 fork 里加 `Map<String, FileProvider>` 前缀路由（这个仓库本来就是自家 fork，成本可控）。

**改造 C：文件关联注册表**

```kotlin
// 替换 FileAssociation.guessLanguage(extension)
interface LanguageRegistry {
    fun guessLanguage(fileUri: String): String?   // 支持扩展名 + 文件名（Dockerfile / Cargo.lock）
    fun all(): List<LanguageDescriptor>           // 供"强制语法"列表用
}
```

- 内置数据从 `FileAssociation`/`LanguageScope` 原样搬进 `BuiltinLanguageSource`
- 扩展数据来自 `ExtensionContributions.languages`
- 消费点只有 3 处：`DocumentRepositoryImpl:77`、`FileIconProviderImpl:28`、`ForceSyntaxViewModel:67`

> 顺带修一个现有小瑕疵：`FileAssociation` 里写的是 `associations[".Dockerfile"]`（把文件名当扩展名），新 schema 用 `filenames` 显式表达，内置数据迁移时 `Dockerfile`/`Containerfile` 改走 `filenames`。

### 8.2 主题（`feature-themes/impl`）

`ThemeInteractorImpl.loadTheme(themeId)` 变三段查找：

```kotlin
1) AssetsTheme.find(themeId)                     // 现有
2) File(themesDir, themeId + ".json")            // 现有（用户手放）
3) themeId.startsWith("ext:") → registry 查扩展  // 新增
```

- 扩展主题用既有 `IThemeSource.fromFile(themeFile)` + 同一套 `ExternalTheme` 反序列化，`ThemeMapper` 零改动
- `SettingsManager.KEY_EDITOR_THEME` 存的就是这套 id，扩展主题天然兼容；**卸载扩展后**该设置找不到 → 回落到 `"darcula"`（现有 `getString(..., "darcula")` 默认值正好兜住，无需 migration）

### 8.3 字体（`feature-fonts/impl`）

`FontsInteractorImpl.loadFont(fontId)` 同样改三段查找，并修一处硬编码：

```kotlin
// 现在：val fontFile = File(fontsDir, fontId + FileType.TTF)
// 问题：只认 .ttf，扩展字体若是 .otf 会找不到
```

改为扩展字体由 `ContributedFont.file` 直接给出绝对路径（`Typeface.createFromFile`），不再拼接文件名。**用户手放字体**仍走原逻辑（保持行为不变）。

### 8.4 snippets / keybindings

L1 只解析 + 存储 + 在扩展详情页展示"该扩展包含 3 个代码片段（需 L1.5 支持）"。
**不动** `feature-shortcuts`。

---

## 9. 卸载与内存生命周期

sora-editor 的 `GrammarRegistry` / `ThemeRegistry` / `FileProviderRegistry` 都是进程级单例，**没有公开的 unregister**。

**L1 策略（不 fork 也能落地）**

1. 卸载 = 删目录 + 删 Room 行 + `registry.refresh()`（快照里不再有该扩展）
2. 已注册进 sora-editor 的 grammar/theme **本次进程内继续存在**，但新的加载不再使用它
3. 在卸载确认弹窗里明确写："该扩展提供的语法将在下次启动 App 后完全移除"
4. `SquircleApp` 初始化时做一次孤儿清理：`files/extensions/` 下的目录若在 Room 里没有对应行 → 删除

**升级路径（可选，L1.5）**：在 fork 里给 `GrammarRegistry`/`ThemeRegistry` 加 `unload(scopeName)` / `unload(themeId)`，即可做到即时生效。

---

## 10. UI（`feature-extensions`）

### 10.1 模块

```
feature-extensions/api/
├── interactor/ExtensionInteractor.kt
├── model/{ExtensionModel, IndexEntryModel, ExtensionState}.kt
├── navigation/ExtensionsRoute.kt        // @Serializable data object ExtensionsRoute : NavKey
└── internal/{ExtensionsApi.kt, ExtensionsApiProvider.kt}
feature-extensions/impl/
├── data/{interactor,repository,model,mapper}/
├── internal/{ExtensionsComponent, ExtensionsModule, ExtensionsScope, api/ExtensionsApiModule}
└── ui/{ExtensionsEntryProvider, extensions/{ExtensionsScreen, ExtensionsViewModel, ExtensionsViewState, ExtensionsViewEvent}, detail/, browse/}
```

完全照抄 `feature-themes` 的骨架（`ThemesApiModule` + `ThemesEntryProvider` + `Store`/`UiAction`/`CommandAction`/middleware）。

### 10.2 页面

- **ExtensionsScreen**：两个分组（已安装 / 可安装），复用 `ds/preference/PreferenceHeader` 与 `Preference`；每行右侧 `SwitchPreference` 式启用开关 + 溢出菜单（详情 / 卸载 / 重装）
- **ExtensionDetailScreen**：清单渲染（版本、发布者、贡献点数量、错误列表）、更新按钮
- 空态：`ds/emptyview`；错误态：区分"网络失败"与"清单非法"

### 10.3 入口

`feature-settings/impl/.../ui/header/HeaderListScreen.kt` 增加第三条 `PreferenceHeader`（放在 Application 之后），跳 `ExtensionsRoute`。
`feature-settings/impl` 已有对 `feature-servers/api`、`feature-shortcuts/api` 的路由依赖，加 `feature-extensions/api` 与现状一致。

### 10.4 装配（三处必须同步改）

1. `settings.gradle.kts`：`":core-extension"`、`":feature-extensions:api"`、`":feature-extensions:impl"`
2. `app/build.gradle.kts`：两个 `implementation(project(...))` + `implementation(project(":core-extension"))`
3. `app/.../internal/AppComponent.kt`：`modules += ExtensionsApiModule::class`，`AppComponent : … , ExtensionsApi`

---

## 11. 安全模型

扩展内容是**不可信输入**，即使数据型也要防。

| 威胁 | 对策 |
|---|---|
| zip slip（`../` 逃逸） | 解压前对每个 entry 的规范化绝对路径做 `startsWith(targetDir)` 校验；拒绝绝对路径 |
| zip 炸弹 | 解压前检查 `entryCount ≤ 500`、单文件 ≤ 5 MB、总解压 ≤ 50 MB、压缩比 ≤ 100:1；超出即中止 |
| 符号链接 / 权限位 | `ZipParameters` 不创建目录外的符号链接；只写普通文件，忽略 entry 的 unix 权限 |
| 未知可执行内容 | 白名单目录（§3.1）+ 根目录只允许 `extension.json`；`.so`/`.dex`/`.jar`/`classes.dex` 一律拒绝 |
| 恶意清单 | 严格长度上限（description ≤ 2 KB、名称 ≤ 64 字符）、正则校验 id、`schemaVersion` 白名单 |
| 中间人 | 索引与下载强制 HTTPS（`cleartextTrafficPermitted=false` 已经在 Android 9+ 默认）；`sha256` 必须匹配 |
| 恶意索引 | 索引 URL 固定 + 可选用户覆盖；索引 provider 有**最终签名校验**（sha256），索引被篡改最多让用户看到假条目，装不上 |
| 路径遍历（运行时） | `ExtensionFileResolver` 每次请求都重新校验 `canonicalPath.startsWith(extensionDir)` |
| 资源耗尽 | 语法/主题解析超时（`withTimeout(5s)`）+ 单文件大小上限；解析失败只影响该扩展 |
| 崩溃 | 所有解析都在 `try/catch` + `Timber`，错误进 `contributions.errors`，**绝不**向上抛到 UI |

---

## 12. 版本与兼容

| 维度 | 策略 |
|---|---|
| 清单格式 | `schemaVersion`（整数）；不认识的版本 → 拒绝安装（不"尽力解析"） |
| App 兼容 | `minVersionCode`（整数），来自 `PackageInfoCompat.getLongVersionCode` |
| 索引格式 | `schemaVersion`；高版本索引 → 只用内置离线索引，提示"请更新 App" |
| 未来删除字段 | `Json { ignoreUnknownKeys = true }` 已就位，扩展端可提前写新字段 |

---

## 13. 网络与合规（F-Droid）

- **不引入任何新权限**：`INTERNET` 已在 `AndroidManifest.xml` 声明
- Retrofit 复用：`CoreApi.provideRetrofit()`（`baseUrl = http://blacksquircle.com`），索引 URL 走 `@Url` 绝对地址即可，**无需新增依赖**
- 下载 zip：`@Streaming @GET suspend fun download(@Url url: String): ResponseBody`（`okhttp3` 已由 retrofit 传递依赖）
- `foss` flavor：默认索引 URL 与 `gms` 一致（纯数据），但**不做任何后台自动检查**
- 需要更新 `PRIVACY-POLICY.md`：说明"安装扩展时会向 `<索引域名>` 发起请求，不携带设备标识"
- README 增加"扩展开发"章节链接

---

## 14. 测试计划

**单元测试（`core-extension`，JUnit4 + MockK + `TestDispatcherProvider`）**

- 清单解析：最小清单 / 全字段清单 / 未知字段 / 缺字段 / 非法 id / 非法 schemaVersion
- 校验器：白名单目录、`extension://` 前缀、文件名正则
- 解压安全：zip slip 用例（构造带 `../` 的 zip）、条目数超限、超大文件
- sha256 不匹配 → `ChecksumMismatch`，且不落盘
- 版本门禁：`minVersionCode` 高于当前 → 拒绝
- 冲突：扩展 scopeName 与内置相同 → 内置赢
- 快照：refresh 后 `contributions()` 正确合并；坏扩展只进 `errors` 不影响其他扩展

**androidTest（`core-common`）**

- `MigrationTest`：5→6 建表 + 已有数据不丢

**手工验证清单**

- 装 TOML 扩展 → 打开 `.toml` 有高亮、文件树图标变化
- 卸载 → 重启后高亮消失，设置里主题回落
- 飞行模式下打开 Browse → 显示内置离线索引，安装时明确报"网络不可用"

---

## 15. PR 拆分

| # | 内容 | 预估 | 依赖 |
|---|---|---|---|
| 1 | `core-extension` 清单模型 + 解析 + 校验 + 单测（无 UI、无网络） | 1~2d | — |
| 2 | Room 实体/DAO/迁移 5→6 + 安装器（zip4j 解压 + 安全校验 + staging） | 2d | 1 |
| 3 | `ExtensionRegistry` 快照 + `refresh()` + 孤儿清理 | 1d | 2 |
| 4 | `feature-themes` 接入扩展主题 | 0.5d | 3 |
| 5 | `feature-editor` 语法源 + `ExtensionFileResolver` + `LanguageRegistry` | 2d | 3, sora SPI 确认 |
| 6 | `feature-extensions` UI：已安装列表 + 启停 + 卸载（无网络） | 2d | 2 |
| 7 | 索引客户端 + Browse + 安装 + 离线兜底 | 2d | 6 |
| 8 | 文档（PRIVACY-POLICY、README、`extension.schema.json`） | 0.5d | 7 |

合计约 11 人日（不含 sora-editor fork 改动）。

---

## 16. 待验证 / 开放问题

1. **sora-editor SPI**（阻塞 PR 5）：`FileProvider` 是否支持自定义 scheme（`extension://`）；`GrammarRegistry` 是否有 unload。本地子模块未 checkout，需先拉取确认。仓库是自家 fork（`.gitmodules` → `massivemadness/sora-editor`），必要时改。
2. **字体格式**：`FileType.TTF` 硬编码是否影响现有"用户手放 .otf 字体"的行为？（L1 只对扩展字体绕开该限制）
3. **`feature-settings` → `feature-extensions` 的依赖方向**：目前 settings 已依赖其他 feature 的 `api` 模块，符合现状，但若想更干净，可以让 Extensions 入口放在 `AppEntryProvider` 里（顶栏溢出菜单）。
4. **索引托管位置**：独立仓库（推荐，便于 CI 生成 + 第三方 PR）还是主仓库 `docs/`？
5. **是否需要安装确认弹窗**：F-Droid 审核偏好"用户明确知情"，建议保留（展示贡献点数量 + 发布者）。

---

## 17. 语义能力：补全与 LSP（L1.5 / L2 / L3）

> 本节回答一个必然会被问到的问题："L1 只做数据，那新语言的**补全**和 **LSP** 怎么办？"

### 17.1 前提：语义引擎必须执行代码，但不必在我们的进程里

Android 的硬约束：

- **API 29+ 禁止从 app 可写目录 exec 文件**（SELinux W^X）。所以"把一个 python / node / pylsp 打进 APK 解压到 `filesDir` 再 exec"这条路**不存在**。
- 可以 exec 的只有系统分区二进制（`/system/bin/sh`、`/system/bin/linker64`…），而系统里没有语言工具链。
- 唯一"in-process 跑代码"的合法形态是：把解释器编成 `.so` 放进 `jniLibs`，从 APK 的 lib 目录 `System.loadLibrary` 后通过 JNI 驱动（这是 Termux 之下少数可行方案，但体积 + 许可 + 跨架构成本极高）。

**结论：语义能力只能是外部进程。** 可选宿主（按推荐度排序）：

| 宿主 | 代码在哪跑 | 需不需要我们签名/审核 | 崩溃能否波及宿主 | 需要维护插件 API 吗 |
|---|---|---|---|---|
| **Termux 进程**（推荐） | 用户自己的 Termux userland | 不需要 | 不能 | **不需要（说 LSP）** |
| 独立 APK 插件 | 插件自己的进程 | 需要（签名白名单） | 不能 | 需要（自定义 AIDL/契约 jar） |
| 远程开发机 | 服务器 | 不需要 | 不能 | 不需要（LSP over 网络） |
| in-process WASM / JS | **宿主进程** | 需要 | **能** | 需要（JS API） |

**关键收益**：LSP 把"插件 API 兼容性"这个最贵的问题外包给了协议。我们**不设计插件 API**，只实现一个 LSP client（这是核心功能，不是插件）；扩展清单只声明"怎么连到 server"——仍然是**纯数据**。这正好满足 §0 的原则：不把第三方可执行代码装进自己的进程。

### 17.2 能力分级：新语言支持到底能做到什么程度

| 能力 | 依赖 | 要执行代码吗 | 阶段 |
|---|---|---|---|
| 语法高亮 | `syntaxes/*.tmLanguage.json` | 否 | **L1 ✔** |
| 注释 / 括号 / 自动缩进 / 自动闭合 | `language-configuration.json` | 否 | **L1 ✔**（`GrammarMapper.toDefinition` 已使用） |
| 文件识别 + 图标 | `contributes.languages` | 否 | **L1 ✔** |
| 词级补全（buffer 内标识符） | sora 的 `IdentifierAutoComplete`（`TextMateLanguage.create(scope, codeCompletion = true)`，`view/Extensions.kt:88` 已经开了） | 宿主自带 | **L1 免费** |
| 静态补全包（关键字 / 标准库 / API 名） | `contributes.completions` | 否 | **L1.5** |
| snippets | `contributes.snippets` | 否 | **L1.5** |
| 语义补全 / 诊断 / 悬停 / 跳转 / 引用 / 重命名 / 格式化 | **LSP** | **是（进程外）** | **L3** |

> 所以"新语言支持"是一个**阶梯**，不是二选一：一个 TOML 扩展包里 90% 是数据，就能拿到高亮 + 缩进 + 词补全 + 静态补全；想再往上就直接指定一个 Termux 里的 server。

### 17.3 L1 就要留好的接口（避免 L2/L3 再改 schema）

- 清单预留 `completions` / `lsp` 字段：解析、校验、存进 `ExtensionEntity.manifest`，**不消费**
- `LanguageRegistry` 的 `LanguageDescriptor` 增加 `staticCompletionId: String?` 与 `lspId: String?`
- `feature-editor` 的 `TextMateLanguage.create(language, codeCompletion)` **不动**（词级补全白拿）

### 17.4 L1.5：静态补全包（零代码，性价比最高）

- 白名单目录增加 `completions/`，格式：

```json
{
  "items": [
    { "label": "unwrap", "insertText": "unwrap()", "kind": "function", "detail": "Option::unwrap", "documentation": "…" },
    { "label": "toml::Value", "kind": "class" }
  ]
}
```

- 实现：`core-extension` 提供 `StaticCompletionSource`；`feature-editor` 新增一个实现 sora `Completer` 的 `CompositeCompleter`（静态包 + buffer 词合并），经 `TextMateLanguage.setCompleter(...)` 接入
  **待确认**：sora 版本里 `Completer` / `CompletionItem` / `setCompleter` 的确切签名（子模块未 checkout）
- 已经有的 UI：`view/SquircleCompletion.kt`（`CompletionLayout` + `EditorCompletionAdapter`），只需换数据源，不用重做界面
- 上限：没有上下文感知（不能按作用域/类型过滤），但能覆盖"标准库函数名 / HTML 标签 / 常用 API"这类大头

### 17.5 L3：LSP client（核心功能，不是插件）

新模块 `core-lsp`，不依赖任何 `feature-*`。

**传输：TCP loopback 优先（不是 stdio）**

理由：通过 Termux `RUN_COMMAND` 启动的进程**不是我们的子进程**，拿不到它的 stdin/stdout。所以：

- 优先用 server 自带的 TCP 模式（`pylsp --tcp 127.0.0.1:38123`、`clangd --port=…`）
- 只有 `--stdio` 的 server 用 `socat` 桥接（用户在 Termux 里跑一次即可）：

```bash
socat TCP-LISTEN:38123,reuseaddr,fork EXEC:'typescript-language-server --stdio'
```

**协议栈**

- JSON-RPC 2.0 + `Content-Length: <n>\r\n\r\n` 帧；用 okio 的 `BufferedSource`/`BufferedSink`（retrofit 传递依赖，不新增库）
- `kotlinx.serialization` 建最小 DTO：`initialize`/`initialized`/`textDocument.didOpen|didChange|didSave|didClose`/`completion`/`hover`/`definition`/`publishDiagnostics`
- 起步用 **full sync**（`TextDocumentSyncKind.Full`），后续再上 incremental

**会话生命周期**

- 一个 workspace 一个 session；tab 切换走 `didOpen`/`didClose`
- App 回前台 → 重连 + 对所有已打开文档重发 `didOpen`（server 无状态可恢复）
- 断线指数退避重连，上限 5 次，之后 UI 降级为词级补全并显示"语言服务未连接"
- `didChange` 做 150–300ms debounce；单文件 > 1 MB 直接禁用 LSP

**UI 映射**

- `CompletionItem` → `SquircleCompletion` 的数据源（含 `insertText`/`textEdit` 的 range 替换：必须尊重 server 给的 range，否则会吃掉前缀）
- `publishDiagnostics` → sora `Diagnostic`/`DiagnosticRegion`（待确认 API），底部面板列消息
- `hover` / `definition` → 底部 popup / 跳转（`EditorViewModel` 已有 `GoToLine` 之类的导航骨架可复用）
- **能力协商驱动降级**：server 没声明 `completionProvider` 就不显示补全入口，不猜测、不硬编码

**扩展清单（纯数据）**

```json
"lsp": [
  {
    "language": "source.python",
    "transport": { "type": "tcp", "host": "127.0.0.1", "port": 38123 },
    "launch": { "type": "termux", "command": "pylsp --tcp 127.0.0.1:38123", "background": true },
    "installHint": "pkg install python-lsp-server",
    "initializationOptions": {}
  }
]
```

`launch.type` 三档，**MVP 只做 `none`**：

| 值 | 行为 | 成本 |
|---|---|---|
| `none`（MVP） | 只做连接 + 重连，命令提示用户自己复制去跑 | 零权限、零 Termux API 依赖 |
| `termux` | 走已有的 `TerminalInteractorImpl.openTermux(...)`；需给 `ShellArgs` 加 `command: String?` 并传 `EXTRA_ARGUMENTS`（现有只传 `EXTRA_COMMAND_PATH`/`EXTRA_WORKDIR`） | 需要用户开 Termux 的 `allow-external-apps` |
| `external` | 弹出可复制命令 + `termux-open-url` 跳 Termux | 极低 |

### 17.6 Termux 侧的现实约束（写进文档避免踩坑）

- aarch64 可装的 server：`pylsp`（`pkg install python-lsp-server`）、`clangd`（`pkg install clangd`）、`gopls`（`pkg install golang` + `go install golang.org/x/tools/gopls@latest`）、`rust-analyzer`（`pkg install rust`）、`typescript-language-server`（node）、`lua-language-server`、`bash-language-server`
- **不推荐默认开** JVM 系（`kotlin-language-server`、`jdtls`）：手机内存吃不消
- 性能：限 `initializationOptions` 并发、`didChange` debounce、大仓库提示用户可能卡
- `RUN_COMMAND` 需要 Termux 设置里打开 `allow-external-apps=true`，否则静默失败；除了 `isTermuxPermissionGranted()` 还要给设置指引
- 网络：loopback **不需要** `INTERNET`；远程 server 才需要，且明文**只允许 loopback**，跨设备必须走 SSH 转发（复用 `feature-servers` 里已有的 JSch）
- server 输出不可信：补全插入文本要过滤控制字符 / 长度上限；诊断消息只渲染纯文本（不解析链接，避免钓鱼）

### 17.7 验收（L3 完成时）

- Termux 里装好 `pylsp` → 打开 `.py` 文件能看到悬停、语义补全、`publishDiagnostics`
- 直接杀掉 Termux 会话 → 编辑器不崩，降级为词级补全，显示可控的错误提示
- 无 Termux 设备 → L1/L1.5 能力完全不受影响

### 17.8 路线图影响

原 §15 的 8 个 PR 不变（L1）。新增：

| # | 内容 | 预估 | 依赖 |
|---|---|---|---|
| 9 | `completions` 贡献点 + 静态补全包 + `CompositeCompleter`（L1.5） | 1.5d | PR 5 |
| 10 | `core-lsp`：JSON-RPC 帧 + TCP 连接 + `initialize` 握手 + 单测（假 server） | 3d | — |
| 11 | 编辑器接入：补全数据源 + 诊断面板 + 状态指示（L3） | 3d | 10 |
| 12 | `launch.type = termux`：`ShellArgs.command` + `EXTRA_ARGUMENTS` | 1d | 11 |

---

## 18. 实现 v1：平台骨架（已落地）

落地的是"平台"这一层，不含消费端接入与 UI，因此可以独立合并、独立回滚。

### 18.1 新增模块

```
core-extension/                                # Android library, namespace com.blacksquircle.ui.extension
├── build.gradle.kts                           # com.blacksquircle.feature + kotlin.android/ksp/serialization
└── src/main/kotlin/com/blacksquircle/ui/extension/
    ├── ExtensionsApi.kt                       # 对外契约：registry + installer（给 feature-* 当 component dependency）
    ├── ExtensionInstaller.kt                  # install / uninstall / setEnabled
    ├── ExtensionRegistry.kt                   # contributions() / observe*() / refresh()
    ├── ExtensionPaths.kt                      # files/extensions/<id>/… + extension:// 解析 + 路径逃逸校验
    ├── AppVersionCodeProvider.kt              # minVersionCode 比对（PackageInfoCompat，不开 BuildConfig）
    ├── model/
    │   ├── ExtensionManifest.kt               # schema v1，含预留的 snippets/keybindings/completions/lsp
    │   ├── ExtensionContributions.kt          # ContributedLanguage/Theme/Font + failures + 快照
    │   ├── InstalledExtension.kt              # InstallType / InstallResult
    │   ├── ExtensionFailure.kt                # 失败即值，绝不向 UI 抛异常
    │   └── ExtensionIds.kt                    # ext:<extensionId>/<localId>
    └── internal/
        ├── ExtensionManifestParser.kt         # kotlinx.serialization（沿用 CoreModule 的 ignoreUnknownKeys）
        ├── ExtensionManifestValidator.kt      # 结构 + 版本 + 路径 + 重复项检查，返回全部问题
        ├── ZipEntriesValidator.kt             # zip slip / 炸弹 / 可执行内容 / 目录白名单（解压前）
        ├── PathSanitizer.kt                   # 纯函数，唯一路径可信来源
        ├── Sha256.kt                          # 索引来源的完整性校验
        ├── ExtensionInstallerImpl.kt           # 校验 → staging → 原子替换 → Room → refresh
        ├── ExtensionRegistryImpl.kt           # Room + 目录 → 内存快照 + Flow + 孤儿目录清理
        └── ExtensionModule.kt                 # Dagger
```

### 18.2 改动的现有模块

| 文件 | 改动 |
|---|---|
| `core-common/.../database/utils/Tables.kt` | `EXTENSIONS = "tbl_extensions"` |
| `core-common/.../database/entity/extension/ExtensionEntity.kt` | 新增（清单原文存 `manifest` 列） |
| `core-common/.../database/dao/extension/ExtensionDao.kt` | 新增（Flow + upsert + setEnabled + delete） |
| `core-common/.../database/AppDatabase.kt` / `AppDatabaseImpl.kt` | `extensionDao()`、`version = 6` |
| `core-common/.../database/utils/Migrations.kt` | `MIGRATION_5_6` |
| `core-common/.../internal/CoreModule.kt` | 注册 `MIGRATION_5_6` |
| `core-common/.../files/Directories.kt` | `extensionsDir(context)`，与 fonts/themes/keys 并列 |
| `settings.gradle.kts` / `app/build.gradle.kts` | include + `implementation(project(":core-extension"))` |
| `app/.../internal/AppComponent.kt` | `ExtensionModule::class` + `AppComponent : ExtensionsApi` |

### 18.3 关键行为约定（实现即契约）

1. **staging + 原子替换**：先解到 `files/extensions/.staging/install-<nano>`，全部校验通过后才 `deleteRecursively` 旧目录并拷贝。失败时 staging 在 `finally` 里清掉，旧版本原样保留。
2. **解压前校验**：`ZipEntriesValidator` 只看 zip 目录（名称/大小/压缩比），恶意包连一个字节都不会落盘。
3. **快照隔离**：`refresh()` 一次构建 `ExtensionContributions`；单个扩展的坏文件只进 `failures`，不影响其他扩展。
4. **孤儿清理**：`refresh()` 顺带删除"目录存在但数据库无记录"的残留（崩溃中断的安装）。
5. **卸载语义**：删目录 + 删记录 + `refresh()`；sora 单例里已注册的语法/主题本次进程内残留，下次冷启动彻底消失（见 §9）。
6. **凭据零依赖**：安装不读取 keystore、不需要 `BuildConfig`、不新增权限。

### 18.4 单元测试

`core-extension/src/test`（JUnit4，`core-test` 提供依赖）：

- `PathSanitizerTest` — traversal / 绝对路径 / Windows 分隔符 / 空段 / 目录前缀按段匹配
- `ZipEntriesValidatorTest` — 缺清单、坏路径、非法目录、可执行内容、超大条目、压缩比炸弹、条目数上限
- `ExtensionManifestValidatorTest` — schema 版本、publisher 前缀、`minVersionCode` 门禁、空贡献、跨扩展 grammar、重复 scope/扩展名、未知 uiTheme、字体格式
- `ExtensionManifestParserTest` — 完整清单、未知字段（前向兼容）、坏 JSON、文件缺失
- `ExtensionIdsTest` — `ext:` 命名空间与反解

### 18.5 验证方式（本机限制）

当前开发机（Termux/aarch64）**无法跑 Android 构建**，原因有两个且都是硬限制：

1. `sora-editor` 子模块未 checkout，`includeBuild("sora-editor")` 会让 Gradle 配置直接失败
2. 已安装的 Android SDK 里 `build-tools/aapt2` 是 x86-64 ELF，在 aarch64 上无法执行（`Exec format error`），AGP 资源处理必然失败

所以验证走 CI（仓库已有 `android.yml`：`assembleFossNonMinifiedRelease` + `testDebugUnitTest` + `ktlintCheck`）或本地桌面环境：

```bash
git submodule update --init --recursive   # 必需，否则无法配置
./gradlew :core-extension:testDebugUnitTest
./gradlew :app:assembleFossNonMinifiedRelease
./gradlew ktlintCheck
```

**待办**：首次成功构建后，把 Room 生成的 `core-common/schemas/.../6.json` 一并提交（仓库约定 schema 入库）。

### 18.6 已落地（第二轮）：`feature-extensions` UI 壳

平台现在有入口，可以手工验证"导入 zip → 列表出现 → 启停 → 卸载"。

```
feature-extensions/api/                          # 纯 Kotlin（com.blacksquircle.kotlin）
└── navigation/ExtensionsRoute.kt                # 只暴露路由，settings 用它做入口

feature-extensions/impl/                         # Android library + Compose
├── internal/{ExtensionsComponent, ExtensionsModule, ExtensionsScope, api/ExtensionsApiModule}
├── domain/model/{ExtensionModel, InstallOutcome}
├── domain/repository/ExtensionsRepository.kt
├── data/{mapper/ExtensionMapper, repository/ExtensionsRepositoryImpl}
└── ui/
    ├── ExtensionsEntryProvider.kt               # @IntoSet 注册 ExtensionsRoute
    └── extensions/
        ├── ExtensionsScreen.kt                  # 列表 + 详情弹窗 + 卸载确认 + 导入 FAB
        ├── ExtensionsViewModel.kt / ExtensionsViewState.kt
        ├── compose/ExtensionItem.kt             # Preference + Switcher + 分隔线
        └── store/                               # Action / State / Event / Store / Reducer / Mapper / Middleware
```

**设计要点**

1. **Room 流就是唯一真相**：`ExtensionsMiddleware.onInstalledExtensions()` 直接把 `repository.observeExtensions()` 并入 action 流，不需要 `Load`/`Reload` 动作，启停/卸载后列表自然刷新。
2. **导入走 SAF → 拷贝到 cache**：`rememberOpenFileContract` 只能给 `content://` uri，`ExtensionsRepositoryImpl` 先 `copyToCache()` 到 `cache/tmp/`，再交给 `ExtensionInstaller`（安装器要求真实文件做 sha256/解压）。
3. **失败即值传到 UI**：`InstallOutcome.Rejected(failure)` → `ExtensionsAction.CommandAction.ExtensionRejected` → Toast 显示 `failure.message`（例如 "Extension requires version code 10028…"），不抛异常。
4. **卸载确认文案写明延迟生效**：「…disappear after the next app start」，与 §9 的单例约束一致。
5. **入口在设置页**：`feature-settings/impl` 的 `HeaderListScreen` 在 Application 之后插入 Extensions 条目，`feature-settings:impl` 只依赖 `feature-extensions:api`（拿路由），不依赖 impl。

**装配改动**

| 文件 | 改动 |
|---|---|
| `settings.gradle.kts` | `:feature-extensions:api` / `:feature-extensions:impl` |
| `app/build.gradle.kts` | 两个 `implementation` |
| `app/.../AppComponent.kt` | `ExtensionsApiModule::class` |
| `app/.../SquircleApp.kt` | 实现 `ExtensionsApiProvider`（core-extension 提供） |
| `core-extension` | 新增 `ExtensionsApiProvider.kt`；DI 模块改名 `ExtensionModule` → `CoreExtensionModule`（避免与 feature 的同名模块混淆） |

### 18.7 下一层（未做，按顺序）

1. 索引客户端：`index.json` 拉取（Retrofit `@Url`）+ `@Streaming` 下载 + `assets` 离线兜底 + sha256 校验 → 安装
2. 消费端接入：`feature-themes` → `feature-fonts` → `feature-editor`（语法源 + `ExtensionFileResolver` + `LanguageRegistry`）
3. `completions` 静态补全包（L1.5）与 LSP（§17）
