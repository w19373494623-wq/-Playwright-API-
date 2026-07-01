import { api } from './client.js';

/** AI 分析相关 API */
export const AiApi = {
  /** AI 去重 */
  dedup: (signal) => api.post('/capture/ai-dedup', undefined, signal),

  /** AI 链路分析 */
  analyze: () => api.post('/capture/analyze'),

  /** 业务场景识别 */
  scenarios: () => api.post('/capture/scenarios'),

  /** 生成接口文档（返回 Markdown） */
  docs: () => api.get('/capture/docs')
};
