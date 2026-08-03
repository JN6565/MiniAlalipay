package com.minialalipay.account;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class AccountCenterArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.minialalipay.account");

    @Test
    void domainDoesNotDependOnFrameworkOrAdapters() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
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
                        "com.minialalipay.user..",
                        "com.minialalipay.ai..",
                        "com.minialalipay.gateway.."
                )
                .check(classes);
    }
}
