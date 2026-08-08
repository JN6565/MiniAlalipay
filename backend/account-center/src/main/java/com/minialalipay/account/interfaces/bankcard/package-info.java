/**
 * 银行卡接口层。
 *
 * <p>职责：对外暴露 /api/v1/bank-cards 网关路由下的绑卡、列表、详情、
 * 设默认与解绑 REST 接口，只接收与返回 API DTO，禁止返回持久化 PO 或聚合根。
 * 绑卡请求体含卡号与四要素明文，禁止记录请求体日志；
 * 用户身份只信任网关透传的 X-User-Id 请求头。</p>
 */
package com.minialalipay.account.interfaces.bankcard;
