/**
 * 事件总线 - 组件间唯一通信方式。
 *
 * 组件不直接调用其他组件的函数，而是：
 *   发布事件: bus.emit('recording:stopped', { assets, historyId })
 *   订阅事件: bus.on('recording:stopped', (data) => { ... })
 */
const _handlers = {};

export const bus = {
  on(event, fn) {
    (_handlers[event] = _handlers[event] || []).push(fn);
    return () => { _handlers[event] = (_handlers[event] || []).filter(h => h !== fn); };
  },

  emit(event, data) {
    const h = _handlers[event];
    if (h) [...h].forEach(fn => fn(data));
  },

  off(event, fn) {
    const h = _handlers[event];
    if (h) _handlers[event] = h.filter(f => f !== fn);
  }
};
