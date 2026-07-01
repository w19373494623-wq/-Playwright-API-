/**
 * 解析 JWT 的 payload 部分。
 * @param {string} token
 * @returns {object|null} 解码后的 payload，或 null
 */
export function parseJwt(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(
      atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    );
    return payload;
  } catch (e) {
    return null;
  }
}
