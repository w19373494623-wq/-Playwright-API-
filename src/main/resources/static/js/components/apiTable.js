/**
 * ApiTable 组件。
 *
 * 职责：
 *  - API 列表渲染（从 store.assets 读取）
 *  - 详情展示
 *  - 行点击事件
 *  - 自身 DOM 管理（#resultCard、#resultBody、#detailArea）
 *
 * 通信：
 *  - 订阅 store:assets:changed 事件自动刷新
 *  - 不依赖 window.renderTable / window.assets
 *  - 不直接调用其他组件
 */
import { bus } from '../events.js';
import { store } from '../store/state.js';
import { $, escapeHtml } from '../utils/dom.js';

// ============================================================
// 初始化
// ============================================================

export function initApiTable() {
  bus.on('store:assets:changed', () => {
    renderTable();
  });
}

// ============================================================
// 表格渲染
// ============================================================

function renderTable() {
  const list = store.assets;
  const hasData = list && list.length > 0;

  const tbody = $('resultBody');
  tbody.innerHTML = '';

  if (!hasData) {
    return;
  }

  list.forEach((a, i) => {
    const tr = document.createElement('tr');
    tr.onclick = () => showDetail(i);

    let mergeLabel = '';
    if (a.mergedFrom && a.mergedFrom.length > 0) {
      mergeLabel = ' <span class="merged-badge">+' + a.mergedFrom.length + '</span>';
    }

    let confHtml = '-';
    if (a.confidence != null) {
      const pct = Math.round(a.confidence * 100);
      confHtml = pct + '%<span class="conf-bg"><span class="conf-bar" style="width:' + pct + 'px;"></span></span>';
    }

    let tagsHtml = '';
    if (a.ruleTags && a.ruleTags.length) {
      tagsHtml = a.ruleTags.map((t) => '<span class="tag">' + t + '</span>').join('');
    }

    tr.innerHTML =
      '<td>' +
      (i + 1) +
      mergeLabel +
      '</td>' +
      '<td><span class="method-badge method-' +
      a.method +
      '">' +
      a.method +
      '</span></td>' +
      '<td class="url-cell">' +
      a.url +
      '</td>' +
      '<td>' +
      a.statusCode +
      '</td>' +
      '<td>' +
      (a.domain || '-') +
      '</td>' +
      '<td>' +
      (a.resource || '-') +
      ' ' +
      tagsHtml +
      '</td>' +
      '<td>' +
      (a.businessStep || '-') +
      '</td>' +
      '<td>' +
      confHtml +
      '</td>';
    tbody.appendChild(tr);
  });

  if (list.length > 0) showDetail(0);
}

// ============================================================
// 详情展示
// ============================================================

function showDetail(index) {
  const a = store.assets[index];
  if (!a) return;

  const div = $('detailArea');
  let html = '';

  // 原始层
  html += '<div class="detail-block"><h4>📋 原始层</h4>';
  html +=
    '<div style="font-size:12px;color:#666;">' +
    'Session: ' +
    (a.sessionId || '-') +
    ' | 序号: ' +
    (a.sequenceNumber || index + 1) +
    ' | 时间: ' +
    new Date(a.timestamp).toLocaleTimeString() +
    '</div></div>';

  // 规则层
  html += '<div class="detail-block"><h4>🔧 规则层</h4>';
  html +=
    '<div style="font-size:12px;">' +
    'domain: <strong>' +
    escapeHtml(a.domain || '-') +
    '</strong>' +
    ' | resource: <strong>' +
    escapeHtml(a.resource || '-') +
    '</strong>' +
    ' | fingerprint: <code>' +
    escapeHtml(a.fingerprint || '-') +
    '</code>' +
    ' | tags: ' +
    (a.ruleTags || [])
      .map((t) => '<span class="tag">' + escapeHtml(t) + '</span>')
      .join(' ');
  if (a.mergedFrom && a.mergedFrom.length > 0) {
    html +=
      '<br>合并: ' +
      a.mergedFrom
        .map((u) => '<code>' + escapeHtml(u) + '</code>')
        .join(', ');
  }
  html += '</div></div>';

  // AI 层
  html += '<div class="detail-block"><h4>🤖 AI 层</h4>';
  html +=
    '<div style="font-size:12px;">' +
    'businessStep: <strong>' +
    escapeHtml(
      a.businessStep || '<span style="color:#999;">未分析</span>'
    ) +
    '</strong>' +
    ' | intent: <strong>' +
    escapeHtml(a.intent || '<span style="color:#999;">未分析</span>') +
    '</strong>' +
    ' | confidence: ';
  if (a.confidence != null) {
    html += '<strong>' + Math.round(a.confidence * 100) + '%</strong>';
  } else {
    html += '<span style="color:#999;">未分析</span>';
  }
  html += '</div></div>';

  // 请求/响应内容
  html +=
    '<div class="detail-block"><h4>请求头</h4><pre>' +
    safeJson(a.request?.headers) +
    '</pre></div>';
  html +=
    '<div class="detail-block"><h4>请求体</h4><pre>' +
    safeJson(a.request?.body) +
    '</pre></div>';
  html +=
    '<div class="detail-block"><h4>响应头</h4><pre>' +
    safeJson(a.response?.headers) +
    '</pre></div>';
  html +=
    '<div class="detail-block"><h4>响应体</h4><pre>' +
    safeJson(a.response?.body) +
    '</pre></div>';

  div.innerHTML = html;
}

// ============================================================
// 工具函数
// ============================================================

function safeJson(data) {
  if (!data) return '-';
  try {
    if (typeof data === 'object')
      return escapeHtml(JSON.stringify(data, null, 2));
    return escapeHtml(JSON.stringify(JSON.parse(data), null, 2));
  } catch (e) {
    return escapeHtml(String(data));
  }
}
