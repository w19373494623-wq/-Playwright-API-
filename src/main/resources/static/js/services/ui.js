/**
 * 公共 UI 服务。
 * 提供加载遮罩和状态栏操作。
 */
import { $ } from '../utils/dom.js';

/** 设置状态栏消息 */
export function setStatus(type, msg) {
  const el = $('status');
  el.className = 'status ' + type;
  el.innerHTML = msg;
}

/** 显示/隐藏加载遮罩 */
export function showLoading(show, msg) {
  const el = $('loadingOverlay');
  if (show) {
    el.querySelector('.loading-text').textContent = msg || '处理中...';
    el.style.display = 'flex';
  } else {
    el.style.display = 'none';
  }
}
