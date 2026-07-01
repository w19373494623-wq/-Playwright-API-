/** 下载 Blob 内容为文件 */
export function downloadBlob(content, filename, mimeType) {
  const blob = new Blob([content], { type: mimeType });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

/** 从 URL 获取内容并下载为文件 */
export async function downloadFile(url, filename) {
  const res = await fetch(url);
  const text = await res.text();
  downloadBlob(text, filename, 'text/markdown');
}
