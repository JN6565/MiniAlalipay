package com.minialalipay.account.interfaces;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 账户与账本内核跨服务 HTTP 契约测试。 */
class InternalAccountApiContractTest {

    @Test
    void internalFundOperationsHaveStablePathsAndSchemas() throws Exception {
        Path contract = Path.of("..", "..", "contracts", "openapi", "minialalipay-api.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) root.get("paths");
            assertInternalOperation(paths, "/internal/v1/accounts/registrations/{registrationId}",
                    "put", "provisionAccount");
            assertInternalOperation(paths, "/internal/v1/accounts/by-user/{userId}",
                    "get", "resolvePersonalAccount");
            assertInternalOperation(paths, "/internal/v1/credit-accounts/by-user/{userId}",
                    "get", "resolveCreditAccount");
            assertInternalOperation(paths, "/internal/v1/tcc/balance/{role}/{action}",
                    "post", "executeBalanceTccBranch");
            assertInternalOperation(paths, "/internal/v1/tcc/ledger/{action}",
                    "post", "executeLedgerTccBranch");
            assertInternalOperation(paths, "/internal/v1/tcc/credit-ledger/{action}",
                    "post", "executeCreditPayLedgerTccBranch");
            assertInternalOperation(paths, "/internal/v1/transaction-facts/{transactionId}",
                    "get", "getTransactionFacts");
            assertInternalOperation(paths, "/internal/v1/reconciliation-diffs",
                    "post", "recordReconciliationDiff", "204");

            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            @SuppressWarnings("unchecked")
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            assertThat(schemas).containsKeys("ProvisionAccountRequest", "InternalAccountReference", "InternalCreditAccountReference",
                    "BalanceTccCommand", "TccBranchResult", "LedgerTccCommand", "CreditPayLedgerTccCommand",
                    "TransactionFacts", "ReconciliationDiffRequest");

            @SuppressWarnings("unchecked")
            Map<String, Object> provisionProperties = (Map<String, Object>)
                    ((Map<String, Object>) schemas.get("ProvisionAccountRequest")).get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> userId = (Map<String, Object>) provisionProperties.get("userId");
            assertThat(userId.get("minLength")).isEqualTo(26);
            assertThat(userId.get("maxLength")).isEqualTo(26);
            assertThat(userId.get("pattern")).isEqualTo("^[0-9A-HJKMNP-TV-Z]{26}$");
        }
    }

    private void assertInternalOperation(Map<String, Map<String, Object>> paths, String path,
                                         String method, String operationId) {
        assertInternalOperation(paths, path, method, operationId, "200");
    }

    private void assertInternalOperation(Map<String, Map<String, Object>> paths, String path,
                                         String method, String operationId, String successStatus) {
        assertThat(paths).containsKey(path);
        @SuppressWarnings("unchecked")
        Map<String, Object> operation = (Map<String, Object>) paths.get(path).get(method);
        assertThat(operation).isNotNull();
        assertThat(operation.get("operationId")).isEqualTo(operationId);
        assertThat(operation.get("x-client-scope")).isEqualTo("INTERNAL");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> servers = (List<Map<String, Object>>) operation.get("servers");
        assertThat(servers).extracting(server -> server.get("url"))
                .contains("http://localhost:8083");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> security = (List<Map<String, Object>>) operation.get("security");
        assertThat(security).anySatisfy(requirement -> assertThat(requirement)
                .containsKey("internalServiceAuth"));
        @SuppressWarnings("unchecked")
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        assertThat(responses).containsKey(successStatus);
    }
}
