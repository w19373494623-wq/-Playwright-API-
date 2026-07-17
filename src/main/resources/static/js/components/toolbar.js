/**
 * Toolbar 组件。
 *
 * 职责：
 *  - URL 输入框
 *  - 开始/停止录制按钮
 *  - 功能 Tab 切换（统计/AI去重/场景/文档/导出/烟雾测试）
 *  - 面板可见性切换
 *  - 录制生命周期编排（调用 CaptureApi，通过事件通知其他组件）
 *
 * 不做：
 *  - 不调用其他组件模块的函数
 *  - 不操作 API Table / History / EnvVars 的 DOM
 *  - 不发起除录制以外的网络请求
 */
import { bus } from '../events.js';
import { store, updateStore } from '../store/state.js';
import { CaptureApi } from '../api/CaptureApi.js';
import { $ } from '../utils/dom.js';
import { setStatus } from '../services/ui.js';

let startInProgress = false;

// ============================================================
// 初始化
// ============================================================

export function initToolbar() {
  // fallback 已接管按钮，跳过模块绑定
  if (window.__fallbackActive) return;

  const startBtn = $('startBtn');
  const stopBtn = $('stopBtn');
  if (startBtn) startBtn.addEventListener('click', handleStart);
  if (stopBtn) stopBtn.addEventListener('click', handleStop);

  // Tab 按钮统一绑定（跳过已有 onclick 属性的按钮，避免与 HTML onclick 双重触发）
  document.querySelectorAll('.tb-btn').forEach((btn) => {
    if (btn.hasAttribute('onclick')) return;
    btn.addEventListener('click', () => {
      const cls = [...btn.classList].find((c) => c.startsWith('tb-'));
      if (!cls) return;
      const feature = cls.replace('tb-', '');
      switchTo(feature);
    });
  });
}

// ============================================================
// 开始录制
// ============================================================

async function handleStart() {
  if (startInProgress) return;
  const url = $('urlInput').value.trim();
  if (!url) {
    alert('请输入网址');
    return;
  }
  startInProgress = true;

  // --- Toolbar UI ---
  setStatus('active', '浏览器正在打开，请等待...');
  $('startBtn').disabled = true;
  $('stopBtn').disabled = false;

  // --- Store ---
  updateStore('assets', []);
  updateStore('currentHistoryId', null);
  updateStore('activeFeature', null);
  updateStore('currentEnvVars', {});
  updateStore('recording.active', true);

  // [Bridge] 隐藏环境变量栏（迁移到 EnvVars 组件后移除）
  $('envVarBar').style.display = 'none';
  document.querySelectorAll('.tb-btn').forEach((b) => b.classList.remove('active'));
  document.querySelector('.tb-btn.tb-export').textContent = '📥 Apifox 导出';

  try {
    await CaptureApi.start(url);
    setStatus('active', '浏览器已打开，请在打开的浏览器窗口中操作页面');
    $('stepTip').innerHTML =
      '<strong>正在录制中...</strong> 操作完成后点击下方「停止录制」';
    bus.emit('recording:started', { url });
  } catch (e) {
    setStatus('error', '启动失败：' + e.message);
    $('startBtn').disabled = false;
    $('stopBtn').disabled = true;
    updateStore('recording.active', false);
  } finally {
    startInProgress = false;
  }
}

// ============================================================
// 停止录制
// ============================================================

async function handleStop() {
  setStatus('active', '正在停止录制...');
  $('stopBtn').disabled = true;

  try {
    const data = await CaptureApi.stop();
    const newAssets = data.assets || [];
    const historyId = data.historyId || null;

    // --- Store ---
    updateStore('assets', newAssets);
    updateStore('currentHistoryId', historyId);

    setStatus(
      'done',
      '录制完成，共 ' +
        newAssets.length +
        ' 个接口（已指纹合并去重）'
    );
    $('startBtn').disabled = false;
    $('stepTip').innerHTML =
      '<strong>使用步骤：</strong><br>' +
      '① 输入网址 → ② 点击「开始录制」→ ③ 在打开的浏览器中操作页面<br>' +
      '④ 操作完成后点击「停止录制」→ ⑤ 使用下方工具栏分析/导出';

    bus.emit('recording:stopped', { assets: newAssets, historyId });
  } catch (e) {
    setStatus('error', '停止失败：' + e.message);
    $('stopBtn').disabled = false;
  }
}

// ============================================================
// 面板切换
// ============================================================

function switchTo(feature) {
  const panel = $('panel-' + feature);
  if (!panel) return;

  const isActive = panel.classList.contains('active');

  // 点击已激活的 Tab → 回到表格视图
  if (isActive) {
    updateStore('activeFeature', null);
    document.querySelectorAll('.content-panel').forEach((p) =>
      p.classList.remove('active')
    );
    $('panel-table').classList.add('active');
    document.querySelectorAll('.tb-btn').forEach((b) =>
      b.classList.remove('active')
    );
    bus.emit('panel:switched', { feature: null });
    return;
  }

  // 切换到目标面板
  updateStore('activeFeature', feature);
  document.querySelectorAll('.content-panel').forEach((p) =>
    p.classList.remove('active')
  );
  panel.classList.add('active');
  document.querySelectorAll('.tb-btn').forEach((b) =>
    b.classList.remove('active')
  );
  const tabBtn = document.querySelector('.tb-btn.tb-' + feature);
  if (tabBtn) tabBtn.classList.add('active');

  bus.emit('panel:switched', { feature });

  // [Bridge] 触发对应功能的数据加载（迁移到各组件后移除）
  triggerFeatureLoad(feature);
}

// ============================================================
// [Bridge] 功能数据加载转发
// ============================================================

function triggerFeatureLoad(feature) {
  // 所有组件已通过 panel:switched 事件自行触发
}

// [Bridge] 暴露给内联 onclick 兼容（全部迁移完成后移除）
window.switchTo = switchTo;
