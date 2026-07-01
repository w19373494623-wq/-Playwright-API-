/**
 * Docs 组件。
 *
 * 职责：
 *  - 监听 panel:switched({ feature: 'docs' }) 生成接口文档
 *  - 渲染 #panel-docs 并绑定下载按钮
 */
import { bus } from '../events.js';
import { AiApi } from '../api/AiApi.js';
import { $, escapeHtml } from '../utils/dom.js';
import { setStatus, showLoading } from '../services/ui.js';
import { downloadFile } from '../utils/download.js';

export function initDocs() {
  bus.on('panel:switched', ({ feature }) => {
    if (feature === 'docs') renderDocs();
  });
}

async function renderDocs() {
  showLoading(true, '📄 正在生成接口文档...');
  try {
    const md = await AiApi.docs();
    const panel = $('panel-docs');
    panel.innerHTML = '<h3>📄 API 接口文档</h3>'
      + '<pre style="font-size:12px;white-space:pre-wrap;max-height:500px;overflow:auto;">' + escapeHtml(md) + '</pre>'
      + '<button class="btn btn-export" style="margin-top:8px;" id="docsDownloadBtn">下载 .md</button>';
    $('docsDownloadBtn').onclick = () => downloadFile('/capture/docs', 'api-docs.md');
    showLoading(false);
  } catch (e) {
    setStatus('error', '生成文档失败：' + e.message);
    showLoading(false);
  }
}
