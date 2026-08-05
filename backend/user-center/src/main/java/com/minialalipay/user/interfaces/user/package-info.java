/**
 * 用户接口层。
 *
 * <p>负责用户相关的 REST API 端点，包括用户搜索等查询操作。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责接收和返回 API DTO</li>
 *   <li>不包含业务逻辑（由应用服务负责）</li>
 *   <li>不直接访问数据库（由仓储负责）</li>
 * </ul>
 * </p>
 */
package com.minialalipay.user.interfaces.user;
