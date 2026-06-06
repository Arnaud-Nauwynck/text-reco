package fr.an.textreco;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureTest {

    private static final String BASE_PACKAGE = "fr.an.textreco";
    private static final String UI_PACKAGE = "fr.an.textreco.ui..";

    private static final JavaClasses classes = new ClassFileImporter()
            .importPackages(BASE_PACKAGE);

    @Test
    void onlyUiPackageCanUseJavaFx() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(UI_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javafx..",
                        "com.sun.javafx.."
                )
                .because("JavaFX classes must only be used in the fr.an.textreco.ui package");

        rule.check(classes);
    }
}
