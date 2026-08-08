import {
  SlowRequestTracker,
  friendlyNetworkError,
  isRetryableRequest,
} from '../../src/services/networkFeedback';

describe('弱网反馈', () => {
  test('存在慢请求时通知页面显示提示，全部完成后隐藏', () => {
    const tracker = new SlowRequestTracker();
    const states: boolean[] = [];
    const unsubscribe = tracker.subscribe((visible) => states.push(visible));

    tracker.start();
    tracker.start();
    tracker.finish();
    tracker.finish();

    expect(states).toEqual([false, true, false]);
    unsubscribe();
  });

  test('网络超时和网关超时使用耐心等待与重试提示', () => {
    expect(friendlyNetworkError('ECONNABORTED')).toBe('当前网络环境较差，数据暂未返回，请稍后重试');
    expect(friendlyNetworkError('NETWORK_TIMEOUT')).toBe('当前网络环境较差，数据暂未返回，请稍后重试');
    expect(friendlyNetworkError(undefined, 504)).toBe('当前网络环境较差或响应较慢，请稍后重试');
    expect(friendlyNetworkError(undefined, 503)).toBe('当前网络环境较差或响应较慢，请稍后重试');
  });

  test('普通业务错误不应被误判为弱网错误', () => {
    expect(friendlyNetworkError(undefined, 400)).toBeUndefined();
    expect(friendlyNetworkError(undefined, 409)).toBeUndefined();
  });

  test('只自动重试只读请求或带幂等键的写请求', () => {
    expect(isRetryableRequest('GET', false)).toBe(true);
    expect(isRetryableRequest('POST', false)).toBe(false);
    expect(isRetryableRequest('POST', true)).toBe(true);
  });
});
