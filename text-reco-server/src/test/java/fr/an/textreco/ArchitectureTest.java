package fr.an.textreco;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureTest {

    private static final String BASE_PACKAGE = "fr.an.textreco";
    private static final String PROCESSING_PACKAGE = "fr.an.textreco.processing..";
    private static final String MODEL_PACKAGE = "fr.an.textreco.model..";

    private static final JavaClasses classes = new ClassFileImporter()
            .importPackages(BASE_PACKAGE);

    @Test
    void noJavaFxImports() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javafx..",
                        "com.sun.javafx..",
                        "de.saxsys.mvvmfx.."
                )
                .because("JavaFX and MvvmFX have been removed; no class may depend on them");

        rule.check(classes);
    }

    @Test
    void processingPackageMustNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(PROCESSING_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.."
                )
                .because("processing classes must remain framework-agnostic");

        rule.check(classes);
    }

    @Test
    void modelPackageMustNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(MODEL_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.."
                )
                .because("model classes must remain framework-agnostic");

        rule.check(classes);
    }
}
