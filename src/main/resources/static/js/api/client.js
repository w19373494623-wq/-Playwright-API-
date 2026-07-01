/**
 * 基础 HTTP 客户端。
 * 自动识别 JSON / text 响应，统一错误处理。
 */
async function handleResponse(res) {
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `HTTP ${res.status}`);
  }
  const ct = res.headers.get('content-type') || '';
  if (ct.includes('json')) return res.json();
  return res.text();
}

export const api = {
  get: (url) => fetch(url).then(handleResponse),

  post: (url, body, signal) =>
    fetch(url, {
      method: 'POST',
      signal,
      ...(body !== undefined
        ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
        : {})
    }).then(handleResponse),

  put: (url, body) =>
    fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(handleResponse),

  del: (url) =>
    fetch(url, { method: 'DELETE' }).then(handleResponse)
};
