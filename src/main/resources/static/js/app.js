/**
 * 应用入口。
 * 导入所有模块，初始化组件。
 */
import { bus } from './events.js';
import { store } from './store/state.js';
import { CaptureApi, HistoryApi, AiApi, ExportApi, SmokeApi } from './api/index.js';
import { $, escapeHtml } from './utils/dom.js';
import { parseJwt } from './utils/jwt.js';
import { downloadBlob, downloadFile } from './utils/download.js';
import { formatTime } from './utils/time.js';
import { setStatus, showLoading } from './services/ui.js';
import { initToolbar } from './components/toolbar.js';
import { initRecorder } from './components/recorder.js';
import { initApiTable } from './components/apiTable.js';
import { initStats } from './components/stats.js';
import { initDedup } from './components/dedup.js';
import { initDocs } from './components/docs.js';
import { initExport } from './components/export.js';
import { initScenario } from './components/scenario.js';
import { initSmokeTest } from './components/smokeTest.js';
import { initHistory } from './components/history.js';
import { initEnvVars } from './components/envVars.js';

// ============================================================
// 暴露给 DevTools 调试用
// ============================================================
window.__store = store;
window.__api = { CaptureApi, HistoryApi, AiApi, ExportApi, SmokeApi };
window.__utils = { $, escapeHtml, formatTime, parseJwt, downloadBlob, downloadFile, setStatus, showLoading };

// ============================================================
// 全局事件日志（调试用）
// ============================================================
bus.on('recording:started', (data) => console.log('[Event] recording:started', data));
bus.on('recording:stopped', (data) => console.log('[Event] recording:stopped', `${data.assets?.length || 0} assets`));

// ============================================================
// 初始化各组件（按依赖顺序）
// 每个 init 单独 try-catch，一个组件失败不影响其他组件
// ============================================================
const inits = [
  ['initToolbar', initToolbar],
  ['initRecorder', initRecorder],
  ['initApiTable', initApiTable],
  ['initStats', initStats],
  ['initDedup', initDedup],
  ['initDocs', initDocs],
  ['initExport', initExport],
  ['initScenario', initScenario],
  ['initSmokeTest', initSmokeTest],
  ['initHistory', initHistory],
  ['initEnvVars', initEnvVars],
];
for (const [name, fn] of inits) {
  try { fn(); } catch (e) { console.error('[App] 组件初始化失败: ' + name, e); }
}

console.log('[App] 所有组件已就绪');
// 标记 ES Module 初始化完成（供 fallback 脚本检测）
document.documentElement.dataset.appReady = 'true';
