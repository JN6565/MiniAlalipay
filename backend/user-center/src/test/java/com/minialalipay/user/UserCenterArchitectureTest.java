package com.minialalipay.user;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class UserCenterArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.minialalipay.user");

    @Test
    void domainDoesNotDependOnFrameworkOrAdapters() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.apache.ibatis..",
                        "feign..",
                        "org.springframework.data.redis..",
                        "io.lettuce..",
                        "redis.clients.jedis..",
                        "..application..",
                        "..interfaces..",
                        "..infrastructure.."
                )
                .check(classes);
    }

    @Test
    void applicationAndAdaptersFollowLayerDirection() {
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
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.minialalipay.business..",
                        "com.minialalipay.account..",
                        "com.minialalipay.ai..",
                        "com.minialalipay.gateway.."
                )
                .check(classes);
    }
}
