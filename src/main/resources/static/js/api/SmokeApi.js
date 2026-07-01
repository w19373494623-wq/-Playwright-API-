import { api } from './client.js';

/** 烟雾测试 API */
export const SmokeApi = {
  /** 执行烟雾测试 */
  run: (collection) =>
    api.post('/smoke/run', collection),

  /** 获取 JSON 报告 */
  getReport: () => api.get('/smoke/report'),

  /** 获取 HTML 报告 */
  getHtmlReport: () => api.get('/smoke/report/html'),

  /** 获取历史报告列表 */
  listReports: () => api.get('/smoke/reports')
};
