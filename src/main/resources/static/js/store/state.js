import { bus } from '../events.js';

/**
 * 全局状态存储。
 * 所有组件从这里读取状态，通过 updateStore() 写入。
 * 写入自动触发 store:<key>:changed 事件。
 */
export const store = {
  assets: [],
  currentHistoryId: null,
  activeFeature: null,
  currentEnvVars: {},
  recording: {
    active: false,
    startTime: null,
    rawCount: 0,
    timer: null
  }
};

/**
 * 更新 store 中指定路径的值，并发布事件。
 * @param {string} key 点号路径如 'recording.active' 或 'assets'
 * @param {*} value 新值
 */
export function updateStore(key, value) {
  const keys = key.split('.');
  let target = store;
  for (let i = 0; i < keys.length - 1; i++) {
    if (target[keys[i]] === undefined) target[keys[i]] = {};
    target = target[keys[i]];
  }
  const oldVal = target[keys[keys.length - 1]];
  target[keys[keys.length - 1]] = value;
  bus.emit(`store:${key}:changed`, { oldValue: oldVal, newValue: value });
}

/**
 * 重置 store 到初始状态（开始新录制时调用）。
 */
export function resetStore() {
  store.assets = [];
  store.currentHistoryId = null;
  store.activeFeature = null;
  store.currentEnvVars = {};
  store.recording.active = false;
  store.recording.startTime = null;
  store.recording.rawCount = 0;
  store.recording.timer = null;
  bus.emit('store:reset');
}
