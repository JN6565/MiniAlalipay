/**
 * 受控模拟充值的策略快照、日额度预占和来源订单。
 *
 * <p>本包只负责受理前限额与渠道状态，禁止直接修改余额、冻结、账本或资金交易终态；资金执行须等待统一交易与 TCC 端口稳定。</p>
 */
package com.minialalipay.business.domain.recharge;
