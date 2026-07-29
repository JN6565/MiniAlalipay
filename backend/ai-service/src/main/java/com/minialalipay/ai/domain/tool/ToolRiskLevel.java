package com.minialalipay.ai.domain.tool;

public enum ToolRiskLevel {
    READ_ONLY,
    LOW,
    HIGH;

    public boolean requiresConfirmation() {
        return this == HIGH;
    }
}
