# 前端架构规范

## 核心原则

### 1. 组件职责隔离

每个组件文件（`js/components/*.js`）**只负责自己的 UI 和交互逻辑**：

- 组件拥有自己的 DOM 元素引用和事件绑定
- 组件不直接操作其他组件的 DOM
- 组件的 `init()` 在 `app.js` 中被调用完成注册

### 2. 网络请求收敛

**所有 HTTP 请求必须经过对应的 Api 模块**（`js/api/*.js`）：

```javascript
// ✅ 正确
import { CaptureApi } from '../api/CaptureApi.js';
const data = await CaptureApi.stop();

// ❌ 错误：组件内直接调用 fetch
const res = await fetch('/capture/stop', { method: 'POST' });
```

Api 模块统一处理：
- 响应类型自动识别（JSON / text）
- HTTP 错误统一抛出
- URL 路径集中管理

### 3. 状态管理

**所有共享状态只能通过 `store/state.js` 读写**：

```javascript
// ✅ 正确：通过 updateStore 更新
import { store, updateStore } from '../store/state.js';
updateStore('assets', newAssets);

// ✅ 正确：通过 store 读取
console.log(store.assets.length);

// ❌ 错误：组件持有自己的状态副本
let localAssets = [];
```

`updateStore()` 会自动触发 `store:<key>:changed` 事件，其他组件通过事件总线感知变化。

### 4. 跨组件通信

**组件之间不允许直接调用**。所有跨组件交互通过事件总线（`events.js`）：

```javascript
// ✅ 正确：发布事件
import { bus } from '../events.js';
bus.emit('recording:stopped', { assets, historyId });

// ✅ 正确：订阅事件
bus.on('recording:stopped', (data) => { /* 更新自己的 UI */ });

// ❌ 错误：直接调用其他组件的方法
import { renderTable } from './apiTable.js';  // 禁止
```

#### 事件命名规范

格式：`<领域>:<动作>`，小写英文：

| 事件 | 荷载 | 说明 |
|---|---|---|
| `recording:started` | `{url}` | 录制开始 |
| `recording:stopped` | `{assets, historyId}` | 录制结束 |
| `recording:progress` | `{rawCount, duration}` | 录制进度更新 |
| `assets:updated` | `{assets, source}` | 资产数据变化 |
| `history:loaded` | `{id, title, assets}` | 历史记录加载 |
| `env-vars:loaded` | `{envVars, historyId}` | 环境变量加载 |
| `panel:switched` | `{feature}` | 面板切换 |

### 5. 入口文件职责

`app.js` **只做初始化和事件绑定，不承担业务逻辑**：

```javascript
// app.js 应该做的事：
import { initRecorder } from './components/recorder.js';
import { initToolbar } from './components/toolbar.js';

initRecorder();
initToolbar();

// app.js 不应该做的事：
// 直接操作 DOM、发起 API 请求、管理组件状态
```

### 6. 工具函数

纯函数放置在 `js/utils/` 中，**不依赖外部状态**：

```javascript
// ✅ 正确：纯函数，入参决定输出
export function escapeHtml(str) { ... }

// ❌ 错误：依赖外部状态
export function getStatus() { return document.getElementById('status'); }
```

---

## 迁移规范

### 增量迁移步骤

1. 创建组件文件到 `js/components/`
2. 在 `app.js` 中添加 `import` 和 `init()`
3. 从内联 `<script>` 中移除对应的旧函数和变量
4. `let` → `var`：被模块引用的全局变量改为 `var` 以支持桥接
5. 验证页面功能正常
6. 提交 Git

### 桥接兼容期

全部组件迁移完成前，内联脚本和 ES Module 共存：

- 模块通过 `window.xxx` 同步关键状态给内联代码
- 内联 `onclick` 属性调用的函数由模块通过 `window.fnName = fn` 暴露
- 桥接代码标记 `// [Bridge]` 注释，全部迁移完成后统一清除

### 清理条件

当以下条件全部满足时，可以移除内联 `<script>` 和桥接代码：

- [ ] 所有组件已迁移到 `js/components/`
- [ ] 所有 `onclick` 属性已替换为 `addEventListener`
- [ ] 所有全局 `var` 变量已移除
- [ ] CSS 已迁移到 `css/` 目录
