package com.pairion.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ArchUnit tests enforcing architectural invariants across the Pairion codebase. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.pairion");
    }

    @Test
    void noSystemOutInMainCode() {
        ArchRule rule =
                ArchRuleDefinition.noClasses()
                        .should()
                        .callMethod(java.io.PrintStream.class, "println", String.class)
                        .orShould()
                        .callMethod(java.io.PrintStream.class, "println", Object.class)
                        .because(
                                "All output must go through SLF4J logging, not"
                                        + " System.out/System.err");
        rule.check(classes);
    }

    @Test
    void nativeWhisperBindingsOnlyAccessedByStttAdapter() {
        ArchRule rule =
                ArchRuleDefinition.noClasses()
                        .that()
                        .resideOutsideOfPackage("com.pairion.adapters.stt.whispercpp..")
                        .and()
                        .resideOutsideOfPackage("com.pairion.nativelib.whisper..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.pairion.nativelib.whisper..")
                        .because(
                                "Jextract-generated native bindings for whisper.cpp must only be"
                                        + " accessed through the STT adapter");
        rule.check(classes);
    }

    @Test
    void nativePiperBindingsOnlyAccessedByTtsAdapter() {
        ArchRule rule =
                ArchRuleDefinition.noClasses()
                        .that()
                        .resideOutsideOfPackage("com.pairion.adapters.tts.piper..")
                        .and()
                        .resideOutsideOfPackage("com.pairion.nativelib.piper..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.pairion.nativelib.piper..")
                        .because(
                                "Jextract-generated native bindings for Piper TTS must only be"
                                        + " accessed through the TTS adapter");
        rule.check(classes);
    }

    @Test
    void noVendorSdkOutsideAdapters() {
        ArchRule rule =
                ArchRuleDefinition.noClasses()
                        .that()
                        .resideOutsideOfPackage("com.pairion.adapters..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "com.anthropic..",
                                "com.openai..",
                                "io.github.ollama..",
                                "ai.onnxruntime..")
                        .because(
                                "Vendor SDK imports are only permitted inside adapter packages"
                                        + " (com.pairion.adapters.*)");
        rule.check(classes);
    }
}
