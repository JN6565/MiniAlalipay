/**
 * AI 服务持久化层。
 *
 * <p>职责：
 * <ul>
 *   <li>PO（Persistent Object）类：一映射 agent_db 物理表结构</li>
 *   <li>Mapper 接口：MyBatis 注解式 SQL，所有查询参数化</li>
 *   <li>Repository 实现：将 PO 与领域对象互转，封装 CAS 更新和影响行数检查</li>
 * </ul>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>PO 不得暴露到 domain 或 interfaces 层</li>
 *   <li>Mapper 不得跨 Schema 查询其他服务的表</li>
 *   <li>动态排序列必须使用服务端白名单，禁止拼接用户输入</li>
 * </ul>
 */
package com.minialalipay.ai.infrastructure.persistence;
