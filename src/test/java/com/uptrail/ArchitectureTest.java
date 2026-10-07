package com.uptrail;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Layering rules: controllers talk to services, services own repositories, domain classes do not know
 * about the web or service layers, and REST responses never serialise JPA entities.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.uptrail");

    @Test
    void webAndApiLayersDoNotUseRepositories() {
        noClasses().that().resideInAnyPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repo..")
                .check(CLASSES);
    }

    @Test
    void controllersLiveInWebOrApiPackages() {
        classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class)
                .should().resideInAnyPackage("..controller..")
                .check(CLASSES);
    }

    @Test
    void repositoriesAreOnlyUsedByServicesAndSampleData() {
        classes().that().resideInAPackage("..repo..")
                .should().onlyBeAccessed().byAnyPackage("..repo..", "..service..", "..sample..")
                .check(CLASSES);
    }

    @Test
    void domainDoesNotDependOnOuterLayers() {
        noClasses().that().resideInAPackage("..model..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..",
                        "..repo..")
                .check(CLASSES);
    }

    @Test
    void servicesDoNotDependOnControllers() {
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .check(CLASSES);
    }

    @Test
    void springDataRestIsNotUsed() {
        noClasses().should().dependOnClassesThat().resideInAPackage("org.springframework.data.rest..")
                .check(CLASSES);
    }

    @Test
    void restControllersDoNotReturnEntities() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should(notReturnJpaEntities())
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    private static ArchCondition<JavaMethod> notReturnJpaEntities() {
        return new ArchCondition<>("not return JPA entities") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean entity = method.getRawReturnType().isAnnotatedWith(Entity.class)
                        || method.getReturnType().getAllInvolvedRawTypes().stream()
                                .anyMatch(type -> type.isAnnotatedWith(Entity.class));
                if (entity) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getName() + " exposes a JPA entity in its response"));
                }
            }
        };
    }
}
