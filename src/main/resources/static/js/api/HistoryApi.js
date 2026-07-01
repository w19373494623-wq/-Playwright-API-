import { api } from './client.js';

/** 历史记录 CRUD API */
export const HistoryApi = {
  /** 获取历史记录列表 */
  list: () => api.get('/capture/history'),

  /** 获取单条历史记录详情 */
  get: (id) => api.get(`/capture/history/${id}`),

  /** 删除历史记录 */
  delete: (id) => api.del(`/capture/history/${id}`),

  /** 重命名历史记录 */
  rename: (id, title) => api.put(`/capture/history/${id}/rename`, { title }),

  /** 获取历史记录的环境变量 */
  getEnvVars: (id) => api.get(`/capture/history/${id}/env-vars`),

  /** 更新历史记录的环境变量 */
  updateEnvVars: (id, envVars) =>
    api.put(`/capture/history/${id}/env-vars`, { envVars }),

  /** 加载历史记录到后端会话 */
  loadToSession: (id) => api.post(`/capture/load-history/${id}`)
};
