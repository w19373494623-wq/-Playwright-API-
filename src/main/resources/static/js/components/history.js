/**
 * History 组件。
 *
 * 职责：
 *  - 历史面板侧边栏打开/关闭
 *  - 历史记录列表加载
 *  - 历史记录查看/删除/重命名
 *  - 所有 DOM 操作通过事件委托
 */
import { bus } from '../events.js';
import { store, updateStore } from '../store/state.js';
import { HistoryApi } from '../api/HistoryApi.js';
import { $, escapeHtml } from '../utils/dom.js';
import { setStatus, showLoading } from '../services/ui.js';
import { formatTime } from '../utils/time.js';

export function initHistory() {
  // fallback 已接管按钮，跳过模块绑定
  if (window.__fallbackActive) return;

  // 历史按钮
  const toggleBtn = $('historyToggleBtn');
  if (toggleBtn) toggleBtn.addEventListener('click', toggleHistoryPanel);

  // 关闭按钮（backdrop + close-btn）
  const backdrop = $('historyBackdrop');
  const sidebar = $('historySidebar');
  if (backdrop) backdrop.addEventListener('click', closeHistoryPanel);
  if (sidebar) {
    const closeBtn = sidebar.querySelector('.close-btn');
    if (closeBtn) closeBtn.addEventListener('click', closeHistoryPanel);
  }

  // 事件委托：历史列表操作
  $('historyList').addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-history-id]');
    if (!btn) return;
    const id = btn.dataset.historyId;
    e.stopPropagation();
    switch (btn.dataset.action) {
      case 'view':
        loadHistoryRecord(id);
        break;
      case 'rename':
        renameHistoryTitle(id);
        break;
      case 'delete':
        confirmDeleteHistory(id, btn.dataset.historyTitle || id);
        break;
    }
  });
}

// ============================================================
// 面板开关
// ============================================================

function toggleHistoryPanel() {
  const sidebar = $('historySidebar');
  const backdrop = $('historyBackdrop');
  const isOpen = sidebar.classList.contains('open');
  if (isOpen) {
    closeHistoryPanel();
  } else {
    sidebar.classList.add('open');
    backdrop.classList.add('open');
    loadHistoryList();
  }
}

function closeHistoryPanel() {
  $('historySidebar').classList.remove('open');
  $('historyBackdrop').classList.remove('open');
}

// ============================================================
// 列表加载
// ============================================================

async function loadHistoryList() {
  const listEl = $('historyList');
  listEl.innerHTML = '<div class="history-loading">加载中...</div>';
  try {
    const list = await HistoryApi.list();
    if (!list || list.length === 0) {
      listEl.innerHTML = '<div class="history-empty">暂无历史记录</div>';
      return;
    }
    let html = '';
    list.forEach(item => {
      const time = item.createdAt ? formatTime(item.createdAt) : '-';
      html += '<div class="history-item">'
        + '<div class="hi-title">' + escapeHtml(item.title || '未命名') + '</div>'
        + '<div class="hi-meta">'
        + '<span>' + time + '</span>'
        + '<span>' + (item.apiCount || 0) + ' 个接口</span>'
        + '</div>'
        + '<div class="hi-actions">'
        + '<button class="hi-view" data-history-id="' + item.id + '" data-action="view">查看</button>'
        + '<button class="hi-reload" data-history-id="' + item.id + '" data-action="rename">改名称</button>'
        + '<button class="hi-delete" data-history-id="' + item.id + '" data-action="delete" data-history-title="' + escapeHtml(item.title || '') + '">删除</button>'
        + '</div>'
        + '</div>';
    });
    listEl.innerHTML = html;
  } catch (e) {
    listEl.innerHTML = '<div class="history-empty">加载失败: ' + escapeHtml(e.message) + '</div>';
  }
}

// ============================================================
// 加载历史记录
// ============================================================

async function loadHistoryRecord(id) {
  showLoading(true, '加载历史记录...');
  updateStore('currentHistoryId', id);
  try {
    const record = await HistoryApi.get(id);
    if (!record.apis || record.apis.length === 0) {
      setStatus('done', '历史记录无接口数据');
      showLoading(false);
      return;
    }

    const newAssets = record.apis.map((a, i) => ({
      method: a.method || 'GET',
      url: a.apiName || (a.method + ' ' + (a.resource || '/')),
      resource: a.resource,
      statusCode: '-',
      domain: record.mainDomain || '-',
      category: a.category || '',
      businessStep: '-',
      confidence: null,
      ruleTags: a.category ? [a.category] : [],
      mergedFrom: null,
      request: {},
      response: {},
      sessionId: record.id,
      apiName: a.apiName || (a.method + ' ' + (a.resource || '/'))
    }));

    // 先加载历史到后端会话（让 stats/AI/导出等功能能读取数据）
    try {
      await fetch('/capture/load-history/' + id, { method: 'POST' });
    } catch (e) {
      console.warn('加载历史到会话失败:', e);
    }

    updateStore('assets', newAssets);
    closeHistoryPanel();

    updateStore('activeFeature', null);
    document.querySelectorAll('.tb-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.content-panel').forEach(p => p.classList.remove('active'));
    $('panel-table').classList.add('active');
    document.querySelector('.tb-btn.tb-export').textContent = '📥 Apifox 导出';

    const label = record.title || '历史记录';
    setStatus('done', '已加载: ' + label + ' (' + newAssets.length + ' 个接口)');
    showLoading(false);

    bus.emit('env-vars:load', id);
  } catch (e) {
    setStatus('error', '加载历史记录失败: ' + e.message);
    showLoading(false);
  }
}

// ============================================================
// 删除
// ============================================================

async function confirmDeleteHistory(id, title) {
  if (!confirm('确定删除历史记录「' + (title || id) + '」？')) return;
  try {
    await HistoryApi.delete(id);
    loadHistoryList();
    setStatus('done', '历史记录已删除');
  } catch (e) {
    setStatus('error', '删除失败: ' + e.message);
  }
}

// ============================================================
// 重命名
// ============================================================

async function renameHistoryTitle(id) {
  const newTitle = prompt('请输入新的标题名称：');
  if (!newTitle || !newTitle.trim()) return;
  try {
    const data = await HistoryApi.rename(id, newTitle.trim());
    setStatus('done', '标题已更新为: ' + data.title);
    loadHistoryList();
  } catch (e) {
    setStatus('error', '重命名失败: ' + e.message);
  }
}
