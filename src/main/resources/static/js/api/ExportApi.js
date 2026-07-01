import { api } from './client.js';

/** 导出相关 API */
export const ExportApi = {
  /** 导出 Postman Collection */
  postman: () => api.get('/capture/export/postman'),

  /** 导出 Apifox 格式（支持历史记录指定） */
  apifox: (historyId) => {
    const url = historyId
      ? `/capture/history/${historyId}/export/apifox`
      : '/capture/export/apifox';
    return api.get(url);
  }
};
