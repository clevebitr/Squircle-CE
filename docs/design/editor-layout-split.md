# 动态布局系统设计（VSCode 风格分屏）

> 状态：设计草案（未实现）
> 目标：编辑器支持水平/垂直分屏、tab 跨组拖动、布局持久化，形状与 VSCode 一致
> 前置结论（见 `DECISIONS.md #9`）：分屏是**内核功能**，不能由插件提供；但布局必须以数据驱动，未来插件只能贡献"预设"

---

## 1. 现状（改造起点）

| 位置 | 现状 |
|---|---|
| `EditorViewState` | 扁平 `documents: List<DocumentState>` + `selectedDocument: Int` |
| `EditorScreen` | 一个 tab 行 + 一个 `CodeEditor`（`view/CodeEditor.kt`，screen 级单例） |
| `DocumentTabLayout` | 用 `sh.calvin.reorderable` 做**组内**重排（`longPressDraggableHandle`） |
| `DocumentModel` | 带 `position / scrollX / scrollY / selectionStart / selectionEnd`（持久化在 Room） |
| `SettingsManager` | `KEY_SELECTED_DOCUMENT_ID` 单个活动文档 |
| `EditorViewModel` | ~1400 行，持有 `documents`、`selectedPosition`、`currentJob` 等单实例状态 |

关键约束：**`DocumentState` 里挂着 `content: Content`（undo 栈）、搜索状态、错误状态**。跨组移动必须移动"归属"而不是重建 `DocumentState`，否则 undo 历史丢失。

---

## 2. 目标 / 非目标

**v1 目标**

1. 水平 / 垂直分屏，层级任意但限制最多 4 个组
2. tab 跨组拖动：落区 = 组中央（移入）+ 四边（拆分）
3. 组内重排（复用现有 reorderable）
4. 关闭组：组内最后一个 tab 关闭时组塌缩，兄弟节点提升
5. 分隔条可拖动调整比例
6. 布局 + 每组活动文档持久化，冷启动恢复
7. 布局操作命令化（`editor.splitRight` 等），为键位/插件预留

**非目标（v1 明确不做）**

- **同一文档出现在多个组**（`DocumentModel` 上的滚动/选区/undo 是文档级，双视图会互相踩）
- 网格类不规则布局（>2 级）→ v2
- 停靠面板（并排终端、迷你地图、问题面板）→ v2
- 插件贡献布局预设 → 等 L2 命令系统

---

## 3. 数据模型

### 3.1 布局树（JSON，存 `SettingsManager`）

```kotlin
@Serializable
data class EditorLayout(
    val root: LayoutNode = SplitNode(
        orientation = Orientation.HORIZONTAL,
        children = listOf(GroupNode(DEFAULT_GROUP_ID)),
        weights = listOf(1f),
    ),
    val activeGroupId: String = DEFAULT_GROUP_ID,
    val activeDocumentByGroup: Map<String, String> = emptyMap(),
)

@Serializable
sealed interface LayoutNode

@Serializable
@SerialName("split")
data class SplitNode(
    val orientation: Orientation,
    val children: List<LayoutNode>,
    val weights: List<Float>,
) : LayoutNode

@Serializable
@SerialName("group")
data class GroupNode(val id: String) : LayoutNode
```

**组不持有 tab 列表** —— tab 归属由数据库的 `DocumentEntity.group_id` 决定（见 3.2），这样"文档顺序"只有一个真相，避免布局 JSON 与 Room 各存一份顺序然后漂移。

### 3.2 数据库（6 → 7）

```sql
ALTER TABLE `tbl_documents` ADD COLUMN `group_id` TEXT NOT NULL DEFAULT ''
```

- `position` 语义从"全局 tab 序号"改为"**组内**序号"（现有数据 `group_id=''` → 首次加载全部归入 `DEFAULT_GROUP_ID`，顺序按原 `position`）
- 迁移后 `MIGRATION_6_7` 只加列；把 `group_id=''` 归一化放到加载期 reconcile（不写迁移里的 UPDATE，避免默认值耦合）

### 3.3 视图状态

```kotlin
data class EditorViewState(
    val groups: List<EditorGroupViewState> = emptyList(),
    val layout: EditorLayout = EditorLayout(),
    val settings: EditorSettings = EditorSettings(),
    ...
)

data class EditorGroupViewState(
    val id: String,
    val documents: List<DocumentState>,
    val activeIndex: Int,
    val isActive: Boolean,
)
```

`EditorViewState.currentDocument` 改为"活动组里的活动文档"，现有调用点（外部键盘、搜索、撤销等）只需换取值来源。

---

## 4. 交互语义

| 操作 | 行为 |
|---|---|
| tab 拖到组**中央** | 移入该组（追加到末尾） |
| tab 拖到组**左/右/上/下边缘**（各 20% 区域） | 若源组只剩这一个 tab：等价于"该组变成拆分"；否则新建组插入该侧 |
| tab 拖到窗口外 / 非法区域 | 取消，回原位 |
| 组内拖动 | 复用 `sh.calvin.reorderable`（现有实现） |
| 拖动分隔条 | 调整相邻两个兄弟的 `weights`，clamp 到 `[0.2, 0.8]` |
| 关闭最后一个 tab | 组塌缩：父 `SplitNode` 只剩一个孩子时用孩子替换自己，权重归一化 |
| 点击组内任意位置 | 该组变为 `activeGroupId`（键盘/扩展键盘只作用于活动组） |

**命令**（先内部 API，P5 接键位）

```
editor.splitRight / editor.splitDown
editor.closeGroup
editor.focusGroupNext / editor.focusGroupPrevious
editor.moveActiveTabToGroup(direction)
editor.resetLayout
```

---

## 5. 拖拽实现

现有 `sh.calvin.reorderable` **只能在一个 LazyList 内重排**，跨组拖拽必须自己实现：

- 拖动期间禁用组内 reorderable，改为自绘 ghost（半透明 tab 卡片跟随手指）
- 落区判定：所有组的矩形在拖动开始时记录，命中时计算相对位置 → `CENTER` / `LEFT` / `RIGHT` / `TOP` / `BOTTOM`
- 落区高亮：在根容器上叠一层 overlay 绘制目标区域（VSCode 那种半透明矩形）
- 不用系统 `DragAndDrop`：跨 Composable 的坐标与回调在 Android 上不一致，调试成本高

手势：`pointerInput` + `detectDragGesturesAfterLongPress`，与现有 tab 长按拖动习惯一致。

---

## 6. 渲染结构

```
EditorScreen
└── EditorSplitContainer(node = layout.root)          // 递归
    ├── SplitNode → Row/Column(weights) + 分隔条
    └── GroupNode → EditorGroupPane(groupId)
        ├── DocumentTabLayout(tabs)                  // 现有组件，加拖拽回调
        └── EditorPane(groupId)                      // 独立 CodeEditor 实例
            └── CodeEditor + ExtendedKeyboard + SearchPanel
```

要点：

- **每组一个 `CodeEditor` 实例**：`view/CodeEditor.kt` 现在按 screen 构造，需要改为 `remember(groupId)` 持有；`TextMateLanguage` 每实例一份，`GrammarRegistry` 进程共享（已验证，扩展语法不受影响）
- `EditorViewModel` 保持**单个**（state 变 `groups`），不引入 N 个 ViewModel —— 现有 Dagger Factory 模式不适合动态数量；编辑器实例与语言对象下沉到 composable 层
- `settings` 变更（字号/主题/换行/软键盘）需广播到所有组：显示层统一从 `viewState.settings` 读，天然一致

---

## 7. 持久化与恢复

- 布局 JSON 写 `SettingsManager.KEY_EDITOR_LAYOUT`（新 key）
- `KEY_SELECTED_DOCUMENT_ID` 继续维护"当前活动文档"，保持向后兼容
- 加载期 reconcile（纯函数，便于测试）：
  1. 布局解析失败 → 用默认单组
  2. 文档 `group_id` 不在布局里 → 归入活动组
  3. 布局里的组没有文档 → 移除组并塌缩
  4. `activeDocumentByGroup` 指向不存在的文档 → 退回该组第一个 tab
  5. `weights` 长度与 `children` 不一致 / 全为 0 → 归一化
- 布局树深度上限 3，组数上限 4（防御畸形 JSON）

---

## 8. 分阶段计划

| 阶段 | 内容 | 验收 | 估时 |
|---|---|---|---|
| **P1 数据层** | `LayoutNode`/`EditorLayout` + 序列化；`group_id` 迁移（6→7）；reconcile 纯函数 + 单测 | 单组下行为与现在**完全一致**；单测覆盖 reconcile 与畸形 JSON | 1.5d |
| **P2 渲染** | `EditorSplitContainer` + 每组独立 `CodeEditor`；只支持**命令**创建两栏（无拖拽） | 命令分屏后两组各自独立编辑/滚动/保存；关闭组正常 | 2d |
| **P3 拖拽** | 跨组拖动 + 边缘落区 + 落区高亮 + 分隔条拖动 | 拖到边缘生成新组、拖到中心移动、组塌缩 | 3d |
| **P4 持久化** | 布局读写 + 冷启动恢复 + 预设（单栏/两栏/上下） | 杀进程重启后布局与活动 tab 恢复 | 1d |
| **P5 命令化** | 布局命令接入 `feature-shortcuts`，为 L2 插件预留 `contributes.layouts` | 键位可触发分屏；预设可由 JSON 描述 | 1d |

每阶段单独 PR，P1 不改任何 UI，可安全先合。

---

## 9. 风险与坑（按踩坑概率排序）

1. **undo 栈丢失**：跨组移动只能改归属，不能重建 `DocumentState`/`Content`
2. **双视图冲突**：同一文档进两个组会同时写 `scrollX/selection/undo` → v1 用"文档只属于一个组"硬约束（拖拽 = 移动）
3. **`Content` 与编辑器绑定**：`CodeEditor.setText(content)` 会重置 undo；切换组时必须复用同一 `Content` 实例
4. **搜索/错误状态挂在 `DocumentState`**：移动时随文档一起走，逻辑上正确，但 `SearchPanel` 只应显示在活动组
5. **Dagger 动态数量**：不用 `daggerViewModel` per group，避免 factory 爆炸
6. **折叠屏/分屏模式**：布局用 weight 表达，天然适配；但 `onConfigurationChanged` 后需要重新测量落区矩形的时机（P3 处理）
7. **性能**：N 个 TextMate 实例 + 大文件；限制 4 组，且非活动组可降级为不渲染（`SubcomposeLayout` 或简单 `if`）

---

## 10. 人工验收清单

1. 单栏状态下所有既有行为不变（打开/切换/关闭/重排/搜索/扩展键盘）
2. 命令分屏成左右两栏，两边可独立编辑与保存，tab 行各自独立
3. 拖 tab 到另一组中央 → 移入；拖到组右边缘 → 生成第三栏
4. 关掉某组最后一个 tab → 组消失、兄弟占满
5. 拖动分隔条，比例保存；重启后保持
6. 冷启动恢复：布局、每组活动 tab、滚动位置
7. 扩展语法在分屏后依然生效（`.swift` 两组都能高亮）
8. 畸形布局 JSON（手改 prefs）→ 回退单栏且不崩
