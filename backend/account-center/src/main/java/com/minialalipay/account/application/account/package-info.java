/**
 * 账户与余额应用编排。
 *
 * <p>负责开户、本人与余额查询、冻结/确认/释放的事务边界；只依赖账户领域端口，
 * 不直接调用 Mapper 或其他服务内部类型。</p>
 */
package com.minialalipay.account.application.account;
