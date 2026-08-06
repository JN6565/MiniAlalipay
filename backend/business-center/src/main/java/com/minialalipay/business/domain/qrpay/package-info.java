/**
 * 动态扫码收款来源订单、二维码令牌摘要与受理前状态机。
 *
 * <p>本包只保存二维码和付款方锁定等业务事实，禁止依赖账户 Mapper、账本类型、Spring 或 TCC 实现；
 * 资金交易只能在阶段四端口稳定后由应用层适配器发起。</p>
 */
package com.minialalipay.business.domain.qrpay;
