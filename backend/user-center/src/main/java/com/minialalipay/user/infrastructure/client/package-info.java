/**
 * 外部服务客户端。
 *
 * <p>负责调用其他微服务的 HTTP 接口，完成跨服务协作。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责 HTTP 调用和响应解析</li>
 *   <li>不包含业务逻辑（由应用服务负责）</li>
 *   <li>处理网络异常和业务异常</li>
 * </ul>
 * </p>
 */
package com.minialalipay.user.infrastructure.client;
