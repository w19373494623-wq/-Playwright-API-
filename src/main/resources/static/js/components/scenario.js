/**
 * Scenario 组件。
 *
 * 职责：
 *  - 监听 panel:switched({ feature: 'scenario' }) 触发场景识别
 *  - 渲染 #panel-scenario（业务场景链路）
 *  - 提供链路分析函数
 */
import { bus } from '../events.js';
import { AiApi } from '../api/AiApi.js';
import { $, escapeHtml } from '../utils/dom.js';
import { setStatus, showLoading } from '../services/ui.js';
import { updateStore } from '../store/state.js';

export function initScenario() {
  bus.on('panel:switched', ({ feature }) => {
    if (feature === 'scenario') loadScenarios();
  });
}

/** AI 链路分析（回填 AI 字段到 assets） */
export async function aiAnalyze() {
  showLoading(true, '🤖 AI 正在分析业务链路...');
  try {
    const data = await AiApi.analyze();
    if (data.assets) {
      updateStore('assets', data.assets);
    }

    const area = $('panel-scenario');
    area.innerHTML = '';
    const s = data.structured || {};

    let html = '<h3>🔗 ' + escapeHtml(s.flowName || '业务链路分析') + '</h3>';
    if (s.description) html += '<p style="color:#666;margin-bottom:12px;">' + escapeHtml(s.description) + '</p>';

    if (s.steps && s.steps.length) {
      html += '<h4>业务流程步骤</h4>';
      s.steps.forEach(step => {
        html += '<div class="flow-step">';
        html += '<strong>Step ' + step.step + ':</strong> ' + escapeHtml(step.action || '');
        html += ' → <code>' + (step.apis || []).join(', ') + '</code>';
        if (step.intent) html += ' <span class="tag tag-ai">' + escapeHtml(step.intent) + '</span>';
        if (step.description) html += '<br><small>' + escapeHtml(step.description) + '</small>';
        html += '</div>';
      });
    }

    if (s.dataFlow && s.dataFlow.length) {
      html += '<h4>数据流转</h4>';
      s.dataFlow.forEach(df => {
        html += '<div class="flow-step flow-data">';
        html += '<code>' + escapeHtml(df.from || '') + '</code>';
        html += ' → <code>' + escapeHtml(df.to || '') + '</code>';
        if (df.value) html += ' <span style="color:#666;">(' + escapeHtml(df.value) + ')</span>';
        html += '</div>';
      });
    }

    html += '<details style="margin-top:12px;"><summary>原始分析文本</summary>'
      + '<pre style="font-size:12px;">' + escapeHtml(data.raw || '') + '</pre></details>';
    area.innerHTML = html;

    setStatus('done', '链路分析完成 ' + (s.steps ? '(' + s.steps.length + ' 个步骤)' : '')
      + ' <span class="ctx-badge ctx-shared">继承验证上下文</span>');
    showLoading(false);
  } catch (e) {
    setStatus('error', '链路分析失败：' + e.message);
    showLoading(false);
  }
}

/** 业务场景识别 */
async function loadScenarios() {
  showLoading(true, '🔗 正在识别业务场景，请稍候...');
  try {
    const data = await AiApi.scenarios();
    const panel = $('panel-scenario');
    panel.innerHTML = '';

    if (data.error) {
      panel.innerHTML = '<div style="padding:16px;color:#cf1322;">' + escapeHtml(data.error) + '</div>';
      setStatus('error', data.error);
      return;
    }

    const scenarios = data.scenarios || [];
    if (scenarios.length === 0) {
      panel.innerHTML = '<p style="color:#666;">未识别出业务场景</p>';
      setStatus('done', '未识别出业务场景');
      return;
    }

    let html = '<h3>🔗 业务场景链路</h3>';
    html += '<p style="color:#666;margin-bottom:12px;">共识别 <strong>' + scenarios.length + '</strong> 个业务场景</p>';

    scenarios.forEach((s, si) => {
      html += '<div class="scenario-card">';
      html += '<h4>' + (si + 1) + '. ' + escapeHtml(s.name || '未命名场景') + '</h4>';
      if (s.description) html += '<div class="scenario-desc">' + escapeHtml(s.description) + '</div>';
      const steps = s.steps || [];
      steps.forEach(step => {
        const methodClass = 'method-' + (step.method || 'GET');
        html += '<div class="scenario-step">';
        html += '<span class="step-num">' + (step.step || '-') + '</span>';
        html += '<span class="method-badge ' + methodClass + '">' + (step.method || '') + '</span>';
        html += '<code>' + escapeHtml(step.resource || step.url || '') + '</code>';
        html += '<span style="color:#666;font-size:12px;margin-left:auto;">' + escapeHtml(step.action || '') + '</span>';
        html += '</div>';
      });
      html += '</div>';
    });

    if (data.raw) {
      html += '<details style="margin-top:12px;"><summary>AI 原始响应</summary>'
        + '<pre style="font-size:12px;">' + escapeHtml(data.raw) + '</pre></details>';
    }

    panel.innerHTML = html;
    setStatus('done', '业务场景识别完成，共 ' + scenarios.length + ' 个场景');
    showLoading(false);
  } catch (e) {
    setStatus('error', '业务场景识别失败：' + e.message);
    showLoading(false);
  }
}
