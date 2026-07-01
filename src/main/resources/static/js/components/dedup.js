/**
 * Dedup 组件。
 *
 * 职责：
 *  - 监听 panel:switched 事件（feature === 'dedup'）触发去重分析
 *  - 调用 AiApi.dedup() 获取去重结果
 *  - 渲染 #panel-dedup（去重表格 + 详情区）
 *  - 行点击事件委托展示详情
 *
 * 通信：
 *  - 订阅 panel:switched 事件
 *  - 不依赖 window 全局变量
 */
import { bus } from '../events.js';
import { AiApi } from '../api/AiApi.js';
import { $, escapeHtml } from '../utils/dom.js';
import { setStatus, showLoading } from '../services/ui.js';

// ============================================================
// 模块级状态
// ============================================================

let dedupData = { apis: [], totalRaw: 0, total: 0 };

// ============================================================
// 初始化
// ============================================================

export function initDedup() {
  bus.on('panel:switched', ({ feature }) => {
    if (feature === 'dedup') runDedup();
  });

  // 事件委托：点击去重表格行展示详情
  $('panel-dedup').addEventListener('click', (e) => {
    const tr = e.target.closest('tr[data-index]');
    if (tr) showDedupDetail(parseInt(tr.dataset.index));
  });
}

// ============================================================
// 去重分析
// ============================================================

async function runDedup() {
  showLoading(true, '🎯 AI 正在去重分析，请稍候...');
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 30000);

    const data = await AiApi.dedup(controller.signal);
    clearTimeout(timeout);

    dedupData = data;
    const panel = $('panel-dedup');
    panel.innerHTML = '';

    let html = '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">';
    html += '<h3 style="margin:0;">🎯 去重后接口清单</h3>';
    html += '<span style="font-size:14px;color:#666;">原始 ' + data.totalRaw + ' 个 → <strong style="color:#1677ff;">' + data.total + '</strong> 个</span>';
    html += '</div>';

    if (data.fallback) {
      html += '<div style="padding:8px 12px;background:#fffbe6;border-radius:4px;margin-bottom:12px;font-size:13px;border:1px solid #ffe58f;">'
        + '⚠️ AI 结果不可用，已回退到机械去重</div>';
    }

    html += '<div style="overflow-x:auto;">';
    html += '<table class="result-table"><thead><tr>'
      + '<th>#</th><th>方法</th><th>URL</th><th>接口名称</th><th>Resource</th><th>调用次数</th>'
      + '</tr></thead><tbody>';

    (data.apis || []).forEach((a, i) => {
      html += '<tr data-index="' + i + '" style="cursor:pointer;">'
        + '<td>' + (i + 1) + '</td>'
        + '<td><span class="method-badge method-' + a.method + '">' + a.method + '</span></td>'
        + '<td class="url-cell">' + escapeHtml(a.urlExample || '-') + '</td>'
        + '<td><strong>' + escapeHtml(a.apiName || '-') + '</strong></td>'
        + '<td><code>' + escapeHtml(a.resource || '-') + '</code></td>'
        + '<td>' + (a.callCount || 1) + ' 次</td>'
        + '</tr>';
    });

    html += '</tbody></table>';
    html += '</div>';

    // 详情区
    html += '<div class="detail-grid" id="dedupDetailArea" style="margin-top:12px;">';
    html += '<div class="detail-block" style="grid-column:1/-1;color:#999;">点击上方条目查看详情</div>';
    html += '</div>';

    if (data.raw) {
      html += '<details style="margin-top:12px;"><summary style="cursor:pointer;color:#666;font-size:13px;">AI 原始响应</summary>'
        + '<pre style="font-size:12px;background:#fff;padding:10px;border-radius:4px;max-height:200px;overflow:auto;">' + escapeHtml(data.raw) + '</pre></details>';
    }

    panel.innerHTML = html;

    // [Bridge] 通知 Export 组件更新按钮文案（迁移 Export 后改为事件驱动）
    document.querySelector('.tb-btn.tb-export').textContent = '📥 Apifox（去重后）';

    setStatus('done', 'AI 去重完成：' + data.totalRaw + ' → ' + data.total + ' 个');
    showLoading(false);
  } catch (e) {
    if (e.name === 'AbortError') {
      setStatus('error', 'AI 去重超时，请重试');
    } else {
      setStatus('error', 'AI 去重失败：' + e.message);
    }
    showLoading(false);
  }
}

// ============================================================
// 详情展示
// ============================================================

function showDedupDetail(index) {
  const apis = dedupData.apis || [];
  const a = apis[index];
  if (!a) return;
  const div = $('dedupDetailArea');
  if (!div) return;

  let html = '';
  html += '<div class="detail-block"><h4>📋 基本信息</h4>';
  html += '<div style="font-size:12px;">'
    + '接口名称: <strong>' + escapeHtml(a.apiName || '-') + '</strong><br>'
    + '请求方法: <span class="method-badge method-' + a.method + '">' + a.method + '</span><br>'
    + 'Resource: <code>' + escapeHtml(a.resource || '-') + '</code><br>'
    + '调用次数: ' + (a.callCount || 1) + ' 次<br>'
    + '</div></div>';

  html += '<div class="detail-block"><h4>🔗 URL 示例</h4>';
  html += '<pre style="word-break:break-all;white-space:pre-wrap;">' + escapeHtml(a.urlExample || '-') + '</pre></div>';

  if (a.mergedUrls && a.mergedUrls.length > 0) {
    html += '<div class="detail-block"><h4>📎 合并的 URL</h4>';
    html += '<div style="font-size:12px;">' + a.mergedUrls.map(u => '<div><code>' + escapeHtml(u) + '</code></div>').join('') + '</div></div>';
  }

  div.innerHTML = html;
}
