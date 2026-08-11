/**
 * 网关协议级异常处理。
 *
 * <p>负责把网关自身异常和下游错误转换为统一 API 响应，并保留请求编号、链路编号和
 * 可公开的错误语义；不得根据 HTTP 状态码推断资金交易最终结果。</p>
 */
package com.minialalipay.gateway.interfaces.error;
