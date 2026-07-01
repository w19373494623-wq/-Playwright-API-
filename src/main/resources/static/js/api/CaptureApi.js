import { api } from './client.js';

/** 录制相关 API */
export const CaptureApi = {
  /** 开始录制 */
  start: (url) => api.post('/capture/start?url=' + encodeURIComponent(url)),

  /** 停止录制 */
  stop: () => api.post('/capture/stop'),

  /** 获取当前原始捕获计数 */
  getRawCount: () => api.get('/capture/raw'),

  /** 获取录制统计 */
  getStats: () => api.get('/capture/stats'),

  /** 获取当前会话环境变量 */
  getEnvVars: () => api.get('/capture/env-vars')
};
