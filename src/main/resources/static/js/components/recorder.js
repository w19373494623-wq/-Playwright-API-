/**
 * 录制状态组件。
 *
 * 职责：
 *  - 录制轮询（计数、计时）
 *  - 录制状态栏 #recStatus 的 UI 更新
 *
 * 不再处理：
 *  - 录制按钮绑定（已移交 Toolbar）
 *  - CaptureApi.start / stop 调用（已移交 Toolbar）
 *  - 录制生命周期编排（已移交 Toolbar）
 *
 * 通信：仅通过事件总线接收通知
 */
import { bus } from '../events.js';
import { store, updateStore } from '../store/state.js';
import { CaptureApi } from '../api/CaptureApi.js';
import { $ } from '../utils/dom.js';

let pollTimer = null;
let pollStartTime = null;

// ============================================================
// 初始化：订阅录制事件
// ============================================================

export function initRecorder() {
  bus.on('recording:started', () => {
    startPolling();
  });

  bus.on('recording:stopped', () => {
    stopPolling();
  });
}

// ============================================================
// 录制状态栏管理
// ============================================================

function startPolling() {
  pollStartTime = Date.now();
  updateStore('recording.startTime', pollStartTime);

  const el = $('recStatus');
  el.className = 'rec-status recording';
  el.innerHTML =
    '<span><span class="dot dot-red"></span>正在录制</span>' +
    '<span class="stat-item">已捕获: <strong id="recRawCount">0</strong></span>' +
    '<span class="stat-item">时长: <strong id="recDuration">00:00</strong></span>';

  pollTimer = setInterval(poll, 2000);
  poll();
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  updateStore('recording.timer', null);

  const el = $('recStatus');
  el.className = 'rec-status done';
  el.innerHTML =
    '<span><span class="dot dot-green"></span>录制完成</span>' +
    '<span class="stat-item">核心接口: <strong>' +
    store.assets.length +
    '</strong></span>' +
    '<span class="stat-item">历史已保存</span>';
}

async function poll() {
  try {
    const data = await CaptureApi.getRawCount();
    updateStore('recording.rawCount', data.total || 0);
    const rawEl = $('recRawCount');
    if (rawEl) rawEl.textContent = data.total || 0;
  } catch (e) {
    /* ignore polling errors */
  }

  const secs = Math.floor((Date.now() - pollStartTime) / 1000);
  const mm = String(Math.floor(secs / 60)).padStart(2, '0');
  const ss = String(secs % 60).padStart(2, '0');
  const durEl = $('recDuration');
  if (durEl) durEl.textContent = mm + ':' + ss;
}
