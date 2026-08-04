package com.minialalipay.business.application.port;

/** 业务 ID、确认令牌和摘要生成端口，使应用层不依赖安全基础设施实现。 */
public interface SecurityMaterialPort {
    String newId();
    String newTraceId();
    String newConfirmationToken();
    byte[] digest(String value);
    String stableId(String value);
    long stablePositiveLong(String value);
}
