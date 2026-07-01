/**
 * SmokeTest 组件。
 *
 * 职责：
 *  - 监听 panel:switched({ feature: 'smoke' }) 触发烟雾测试
 *  - 获取 Postman Collection、注入 token、执行测试
 *  - 渲染 #panel-smoke 测试报告
 *  - 报告历史列表展示（事件委托）
 */
import { bus } from '../events.js';
import { store } from '../store/state.js';
import { $, escapeHtml } from '../utils/dom.js';
import { setStatus, showLoading } from '../services/ui.js';
import { SmokeApi } from '../api/SmokeApi.js';
import { ExportApi } from '../api/ExportApi.js';

export function initSmokeTest() {
  bus.on('panel:switched', ({ feature }) => {
    if (feature === 'smoke') runSmokeTest();
  });

  // 事件委托：报告历史链接
  $('panel-smoke').addEventListener('click', (e) => {
    const link = e.target.closest('[data-smoke-action]');
    if (link && link.dataset.smokeAction === 'list-reports') {
      e.preventDefault();
      listSmokeReports();
    }
  });
}

async function runSmokeTest() {
  showLoading(true, '🚀 正在执行烟雾测试...');
  try {
    const collection = await ExportApi.apifox(store.currentHistoryId);

    // 从环境变量注入所有变量（token、userId、username 等）
    const envVars = store.currentEnvVars || {};
    const collVars = collection.variable || [];
    for (const [key, value] of Object.entries(envVars)) {
      if (!value) continue;
      const existing = collVars.find(v => v.key === key);
      if (existing) {
        existing.value = value;
      } else {
        collVars.push({ key, value });
      }
    }
    collection.variable = collVars;

    // 如果 token 仍然为空，提醒用户输入
    const tokenVar = collVars.find(v => v.key === 'token');
    const tokenEmpty = !tokenVar || !tokenVar.value;
    if (tokenEmpty) {
      showLoading(false);
      const token = prompt(
        '{{token}} 为空，请粘贴登录后的 token 值（Bearer 后面的部分）\n输入后会自动保存到环境变量，下次无需再输：');
      if (token && token.trim()) {
        const trimmedToken = token.trim();
        if (tokenVar) {
          tokenVar.value = trimmedToken;
        } else {
          collVars.push({ key: 'token', value: trimmedToken });
          collection.variable = collVars;
        }
        // 保存到环境变量供后续使用
        if (store.currentHistoryId) {
          try {
            const updatedVars = { ...store.currentEnvVars, token: trimmedToken };
            await fetch('/capture/history/' + store.currentHistoryId + '/env-vars', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ envVars: updatedVars })
            });
          } catch (e) { /* ignore */ }
        }
        // [Bridge] 通知 EnvVars 组件刷新
        bus.emit('env-vars:changed', store.currentHistoryId);
      }
      showLoading(true, '🚀 正在执行烟雾测试...');
    }

    const result = await SmokeApi.run(collection);

    const panel = $('panel-smoke');
    panel.innerHTML = '';

    let html = '<h3>🚀 烟雾测试报告</h3>';
    html += '<div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:16px;">';
    html += '<div style="background:#f6ffed;padding:12px 20px;border-radius:8px;"><strong style="color:#52c41a;font-size:24px;">' + result.passed + '</strong><br><span style="color:#666;font-size:13px;">通过</span></div>';
    html += '<div style="background:#fff2f0;padding:12px 20px;border-radius:8px;"><strong style="color:#ff4d4f;font-size:24px;">' + result.failed + '</strong><br><span style="color:#666;font-size:13px;">失败</span></div>';
    html += '<div style="background:#f0f5ff;padding:12px 20px;border-radius:8px;"><strong style="color:#1677ff;font-size:24px;">' + result.total + '</strong><br><span style="color:#666;font-size:13px;">总接口</span></div>';
    html += '<div style="background:#f9f0ff;padding:12px 20px;border-radius:8px;"><strong style="color:#722ed1;font-size:24px;">' + result.successRate + '</strong><br><span style="color:#666;font-size:13px;">成功率</span></div>';
    html += '</div>';

    if (result.failed > 0) {
      html += '<p><a href="/smoke/report/html" target="_blank" style="color:#ff4d4f;">查看详细 HTML 报告 →</a>';
    } else {
      html += '<p><a href="/smoke/report/html" target="_blank">查看详细 HTML 报告 →</a>';
    }
    html += ' &nbsp;|&nbsp; <a href="javascript:void(0)" data-smoke-action="list-reports" style="color:#1677ff;">查看历史报告 →</a></p>';
    html += '<div id="smokeReportList" style="display:none;margin-top:8px;"></div>';

    panel.innerHTML = html;
    setStatus('done', '烟雾测试完成 ✅ ' + result.passed + '/' + result.total + ' 通过，成功率 ' + result.successRate);
    showLoading(false);
  } catch (e) {
    setStatus('error', '烟雾测试失败：' + e.message);
    showLoading(false);
  }
}

async function listSmokeReports() {
  const container = $('smokeReportList');
  if (!container) return;
  if (container.style.display === 'block') {
    container.style.display = 'none';
    return;
  }
  try {
    const data = await SmokeApi.listReports();
    const reports = data.reports || [];
    if (reports.length === 0) {
      container.innerHTML = '<span style="color:#999;font-size:13px;">暂无历史报告</span>';
    } else {
      let html = '<div style="background:#fafafa;border:1px solid #e8e8e8;border-radius:4px;padding:8px 12px;">';
      html += '<div style="font-weight:600;font-size:13px;margin-bottom:6px;">📋 历史烟雾测试报告</div>';
      reports.forEach(r => {
        html += '<div style="font-size:13px;padding:4px 0;"><a href="' + r.path + '" target="_blank">' + r.id + '</a> <span style="color:#999;font-size:12px;">(' + r.date + ')</span></div>';
      });
      html += '</div>';
      container.innerHTML = html;
    }
    container.style.display = 'block';
  } catch (e) {
    container.innerHTML = '<span style="color:#ff4d4f;font-size:13px;">加载失败: ' + e.message + '</span>';
    container.style.display = 'block';
  }
}
