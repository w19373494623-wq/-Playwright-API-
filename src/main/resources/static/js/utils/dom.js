/** DOM 快捷操作 */
export const $ = (id) => document.getElementById(id);

/** HTML 转义 */
export function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** 更新状态栏消息 */
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

/** 格式化时间戳 */
export function formatTime(ts) {
  const d = new Date(ts);
  return (
    d.getFullYear() +
    '-' +
    String(d.getMonth() + 1).padStart(2, '0') +
    '-' +
    String(d.getDate()).padStart(2, '0') +
    ' ' +
    String(d.getHours()).padStart(2, '0') +
    ':' +
    String(d.getMinutes()).padStart(2, '0')
  );
}
