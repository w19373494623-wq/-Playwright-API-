/**
 * EnvVars 组件。
 *
 * 职责：
 *  - 监听 env-vars:load, env-vars:changed, recording:stopped 事件自动加载
 *  - 渲染 #envVarBar（环境变量展示栏）
 *  - 环境变量编辑（弹窗式）
 *  - Token 有效期检测（JWT 解析）
 *
 * 通信：
 *  - 监听 env-vars:load 事件（由 History/SmokeTest 触发）
 *  - 通过 store.currentEnvVars 共享环境变量
 */
import { bus } from '../events.js';
import { store, updateStore } from '../store/state.js';
import { $, escapeHtml } from '../utils/dom.js';
import { setStatus } from '../services/ui.js';
import { formatTime } from '../utils/time.js';

export function initEnvVars() {
  bus.on('env-vars:load', (historyId) => {
    loadEnvVars(historyId);
  });

  // 环境变量变更后刷新（smokeTest 手动输入 token 后触发）
  bus.on('env-vars:changed', (historyId) => {
    loadEnvVars(historyId || store.currentHistoryId);
  });

  // toolbar 中 handleStop 后自动加载当前会话的环境变量
  bus.on('recording:stopped', ({ historyId }) => {
    if (historyId) loadEnvVars(historyId);
  });

  // 编辑按钮
  const editBtn = $('envVarEditBtn');
  if (editBtn) editBtn.onclick = editEnvVars;
}

/** 从环境变量中获取 token */
export function getTokenFromEnv() {
  return store.currentEnvVars['token'] || '';
}

/**
 * 加载并显示环境变量。
 * @param {string|null} historyId - 历史记录 ID，null 表示当前会话
 */
async function loadEnvVars(historyId) {
  const bar = $('envVarBar');
  const list = $('envVarList');
  const tokenStatus = $('tokenStatus');

  try {
    let envVars = {};
    if (historyId) {
      const res = await fetch('/capture/history/' + historyId + '/env-vars');
      if (res.ok) {
        const data = await res.json();
        envVars = data.envVars || {};
      }
    } else {
      const res = await fetch('/capture/env-vars');
      if (res.ok) {
        const data = await res.json();
        envVars = data.envVars || {};
      }
    }

    updateStore('currentEnvVars', envVars);
    const keys = Object.keys(envVars);
    if (keys.length === 0) {
      bar.style.display = 'none';
      return;
    }

    bar.style.display = 'block';
    let html = '';
    keys.forEach(key => {
      const val = envVars[key] || '';
      const displayVal = val.length > 32 ? val.substring(0, 16) + '...' + val.substring(val.length - 16) : val;
      html += '<span style="background:#fff;padding:4px 10px;border-radius:4px;border:1px solid #d9d9d9;">'
        + '<strong>' + escapeHtml(key) + ':</strong> '
        + '<code style="font-size:12px;">' + escapeHtml(displayVal) + '</code>'
        + '</span>';
    });
    list.innerHTML = html;

    // Token 有效期检测
    if (envVars['token']) {
      const parsed = parseJwt(envVars['token']);
      if (parsed && parsed.exp) {
        const expDate = new Date(parsed.exp * 1000);
        const now = Date.now();
        const diff = expDate.getTime() - now;
        const hours = Math.floor(diff / 3600000);
        const minutes = Math.floor((diff % 3600000) / 60000);
        if (diff > 0) {
          tokenStatus.innerHTML = '<span style="color:#389e0d;">✅ Token 有效，过期时间: '
            + formatTime(expDate.getTime())
            + '（剩余 ' + hours + ' 小时 ' + minutes + ' 分）</span>';
        } else {
          tokenStatus.innerHTML = '<span style="color:#cf1322;">❌ Token 已过期（'
            + formatTime(expDate.getTime()) + '）</span>'
            + ' <button class="tb-btn" id="envVarEditBtn2" style="font-size:12px;padding:2px 8px;color:#cf1322;">更新 Token</button>';
          // 绑定过期状态下的更新按钮
          const edit2 = $('envVarEditBtn2');
          if (edit2) edit2.onclick = editEnvVars;
        }
      } else {
        tokenStatus.innerHTML = '<span style="color:#8c8c8c;">Token 已保存（非 JWT 格式，无法解析有效期）</span>';
      }
    } else {
      tokenStatus.innerHTML = '';
    }
  } catch (e) {
    console.warn('加载环境变量失败:', e);
    bar.style.display = 'none';
  }
}

/** 解析 JWT 的 payload 部分 */
function parseJwt(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload;
  } catch (e) {
    return null;
  }
}

/** 编辑环境变量 */
async function editEnvVars() {
  const keys = Object.keys(store.currentEnvVars);
  let currentStr = keys.map(k => k + '=' + (store.currentEnvVars[k] || '')).join('\n');
  const input = prompt('编辑环境变量（每行 key=value）：\n当前值可直接修改', currentStr);
  if (!input) return;

  const lines = input.trim().split('\n');
  const newVars = {};
  lines.forEach(line => {
    const idx = line.indexOf('=');
    if (idx > 0) {
      const key = line.substring(0, idx).trim();
      const val = line.substring(idx + 1).trim();
      if (key) newVars[key] = val;
    }
  });

  if (Object.keys(newVars).length === 0) return;
  updateStore('currentEnvVars', newVars);

  if (store.currentHistoryId) {
    try {
      await fetch('/capture/history/' + store.currentHistoryId + '/env-vars', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ envVars: newVars })
      });
    } catch (e) {
      console.warn('保存环境变量失败:', e);
    }
  }

  loadEnvVars(store.currentHistoryId);
  setStatus('done', '环境变量已更新');
}
