package com.hyped.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.hyped.app",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_INDEPENDENT = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..api..",
                    "..application..",
                    "..infrastructure..",
                    "org.springframework..",
                    "jakarta.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule API_DOES_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..")
            .allowEmptyShould(true);
}

