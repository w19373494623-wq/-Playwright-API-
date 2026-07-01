/**
 * Stats 组件。
 *
 * 职责：
 *  - 监听 store:assets:changed 自动获取统计
 *  - 更新 #statsBar（表格上方紧凑统计）
 *  - 更新 #panel-stats（统计面板详情）
 *
 * 通信：
 *  - 订阅 store:assets:changed 事件
 *  - 通过 CaptureApi.fetchStats 获取数据
 *  - 不依赖 window.assets
 */
import { bus } from '../events.js';
import { store } from '../store/state.js';
import { CaptureApi } from '../api/CaptureApi.js';
import { $, escapeHtml } from '../utils/dom.js';

// ============================================================
// 初始化
// ============================================================

export function initStats() {
  bus.on('store:assets:changed', () => {
    renderStats();
  });

  // 点击统计 Tab 时重新加载
  bus.on('panel:switched', ({ feature }) => {
    if (feature === 'stats') renderStats();
  });
}

// ============================================================
// 统计渲染
// ============================================================

async function renderStats() {
  const list = store.assets;
  if (!list || list.length === 0) {
    $('statsBar').style.display = 'none';
    $('panel-stats').innerHTML = '<div style="text-align:center;padding:40px 20px;color:#999;">暂无可用的统计数据，请先录制或加载历史记录</div>';
    return;
  }

  try {
    const stats = await CaptureApi.getStats();

    // --- statsBar（表格上方紧凑统计） ---
    const bar = $('statsBar');
    bar.style.display = 'flex';
    let html = '';
    if (stats.success > 0) html += '<span class="stat-ok">✅ ' + stats.success + ' 成功</span>';
    if (stats.redirect > 0) html += '<span class="stat-redirect">↪ ' + stats.redirect + ' 重定向</span>';
    if (stats.clientError > 0) html += '<span class="stat-err">⚠ ' + stats.clientError + ' 客户端错误</span>';
    if (stats.serverError > 0) html += '<span class="stat-err">❌ ' + stats.serverError + ' 服务端错误</span>';
    html += '<span class="stat-muted">共 ' + stats.total + ' 个</span>';
    if (stats.byDomain) {
      const domains = Object.entries(stats.byDomain).map(([k, v]) => k + ':' + v).join(', ');
      html += '<span class="stat-muted">| 领域: ' + domains + '</span>';
    }
    bar.innerHTML = html;

    // --- 统计面板详情 ---
    const panel = $('panel-stats');
    let detailHtml = '<h3>📊 接口统计</h3>';
    detailHtml += '<div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:16px;">';
    detailHtml +=
      '<div style="background:#f6ffed;padding:12px 20px;border-radius:8px;"><strong style="color:#52c41a;font-size:24px;">' +
      stats.success +
      '</strong><br><span style="color:#666;font-size:13px;">成功 (2xx)</span></div>';
    detailHtml +=
      '<div style="background:#fffbe6;padding:12px 20px;border-radius:8px;"><strong style="color:#d48806;font-size:24px;">' +
      stats.redirect +
      '</strong><br><span style="color:#666;font-size:13px;">重定向 (3xx)</span></div>';
    detailHtml +=
      '<div style="background:#fff2f0;padding:12px 20px;border-radius:8px;"><strong style="color:#ff4d4f;font-size:24px;">' +
      (stats.clientError + stats.serverError) +
      '</strong><br><span style="color:#666;font-size:13px;">错误 (4xx/5xx)</span></div>';
    detailHtml +=
      '<div style="background:#f0f5ff;padding:12px 20px;border-radius:8px;"><strong style="color:#1677ff;font-size:24px;">' +
      stats.total +
      '</strong><br><span style="color:#666;font-size:13px;">总接口数</span></div>';
    detailHtml += '</div>';

    if (stats.byDomain) {
      detailHtml += '<h4>按领域分布</h4><div style="display:flex;gap:8px;flex-wrap:wrap;">';
      for (const [domain, count] of Object.entries(stats.byDomain)) {
        detailHtml +=
          '<span style="background:#f0f5ff;padding:4px 12px;border-radius:4px;">' +
          escapeHtml(domain) +
          ': <strong>' +
          count +
          '</strong></span>';
      }
      detailHtml += '</div>';
    }
    if (stats.byIntent) {
      detailHtml += '<h4 style="margin-top:12px;">按意图分布</h4><div style="display:flex;gap:8px;flex-wrap:wrap;">';
      for (const [intent, count] of Object.entries(stats.byIntent)) {
        detailHtml +=
          '<span style="background:#f9f0ff;padding:4px 12px;border-radius:4px;">' +
          escapeHtml(intent) +
          ': <strong>' +
          count +
          '</strong></span>';
      }
      detailHtml += '</div>';
    }
    panel.innerHTML = detailHtml;
  } catch (e) {
    console.error('[Stats] 获取统计失败', e);
  }
}
