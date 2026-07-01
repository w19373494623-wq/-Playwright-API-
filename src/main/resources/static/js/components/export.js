/**
 * Export 组件。
 *
 * 职责：
 *  - 监听 panel:switched({ feature: 'export' }) 触发 Apifox 导出
 *  - 提供 Postman 导出函数
 *  - 渲染 #panel-export 面板
 */
import { bus } from '../events.js';
import { $, escapeHtml } from '../utils/dom.js';
import { setStatus, showLoading } from '../services/ui.js';
import { downloadBlob } from '../utils/download.js';
import { ExportApi } from '../api/ExportApi.js';

export function initExport() {
  bus.on('panel:switched', ({ feature }) => {
    if (feature === 'export') runExport();
  });
}

/** Postman 导出（当前未绑定 UI） */
export async function exportPostman() {
  try {
    const json = await ExportApi.postman();
    const envVars = (json.variable && json.variable.variable) ? json.variable.variable : [];
    const varNames = envVars.map(v => v.key).join(', ');
    downloadBlob(JSON.stringify(json, null, 2), 'postman-collection.json', 'application/json');
    setStatus('done', 'Postman 集合已下载 🎯 环境变量: {{' + varNames + '}}');
  } catch (e) {
    setStatus('error', '导出失败：' + e.message);
  }
}

async function runExport() {
  showLoading(true, '📥 正在导出 Apifox 格式...');
  try {
    const collection = await ExportApi.apifox();
    const name = (collection.info && collection.info.name) ? collection.info.name : 'apifox-export';
    const isDedup = name.indexOf('去重') >= 0;
    downloadBlob(JSON.stringify(collection, null, 2), name + '.json', 'application/json');

    const panel = $('panel-export');
    const itemCount = (collection.item || []).reduce((sum, f) => sum + (f.item || []).length, 0);
    panel.innerHTML = '<div style="text-align:center;padding:40px 20px;">'
      + '<div style="font-size:48px;margin-bottom:16px;">✅</div>'
      + '<h3>导出成功</h3>'
      + '<p style="color:#666;margin-top:8px;">文件: ' + escapeHtml(name) + '.json</p>'
      + '<p style="color:#666;">共 ' + itemCount + ' 个接口</p>'
      + '<p style="color:#999;font-size:13px;margin-top:16px;">打开 Apifox → 导入 → Postman 格式 → 选择下载的文件</p>'
      + '</div>';

    if (isDedup) {
      setStatus('done', 'AI 去重结果已导出 🎯 共 ' + itemCount + ' 个接口');
    } else {
      setStatus('done', 'Apifox 导出完成');
    }
  } catch (e) {
    setStatus('error', 'Apifox 导出失败：' + e.message);
    $('panel-export').innerHTML = '<div style="text-align:center;padding:40px 20px;color:#cf1322;">导出失败: ' + escapeHtml(e.message) + '</div>';
  } finally {
    showLoading(false);
  }
}
