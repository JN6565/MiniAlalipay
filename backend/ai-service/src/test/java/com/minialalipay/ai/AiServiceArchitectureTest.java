package com.minialalipay.ai;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * AI 服务分层和跨服务边界测试。
 */
class AiServiceArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.minialalipay.ai");

    @Test
    void domainDoesNotDependOnFrameworkOrAdapters() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.apache.ibatis..",
                        "feign..",
                        "org.springframework.data.redis..",
                        "..application..",
                        "..interfaces..",
                        "..infrastructure.."
                )
                .check(classes);
    }

    @Test
    void layersFollowDependencyDirection() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..interfaces..", "..infrastructure..")
                .check(classes);
        noClasses().that().resideInAPackage("..interfaces..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(classes);
        noClasses().that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..interfaces..")
                .check(classes);
    }

    @Test
    void doesNotDependOnOtherServiceInternals() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.minialalipay.user..",
                "com.minialalipay.business..",
                "com.minialalipay.account..",
                "com.minialalipay.gateway.."
        ).check(classes);
    }
}
