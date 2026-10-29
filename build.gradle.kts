plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

group = "io.github.pt9912"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Jede aufgeloeste Version steht in gradle.lockfile: ein Build loest dieselben Abhaengigkeiten
// auf, bis die Sperre mit `make deps-lock` bewusst neu geschrieben wird.
dependencyLocking {
    lockAllConfigurations()
}

kotlin {
    jvmToolchain(21)
}

// Statische Analyse: `gradle detekt`, im Dockerfile die Stage `detekt`, aufgerufen ueber
// `make detekt`. config/detekt/detekt.yml fuehrt nur die Abweichungen von detekts Standard.
// Keine Baseline und kein @Suppress: ein Befund wird im Code behoben. detekt 2.0 ist noch ein
// Pre-Release; 2.0.0-alpha.6 ist gegen genau dieses Kotlin gebaut, ein Kotlin-Wechsel wartet
// auf ein detekt, das ihn unterstuetzt.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
}

// Selbsttest der Importregeln: dieselbe Konfiguration, aber ueber Testdaten statt ueber src/.
// config/detekt/selbsttest/ traegt die erlaubten Importe (Allowed.kt), build/selbsttest/ die
// verbotenen, die tools/detekt-selbsttest.sh aus detekt.yml erzeugt. Befunde brechen diese Task
// nicht - das Skript wertet den Bericht aus (`make detekt-selbsttest`).
tasks.register<dev.detekt.gradle.Detekt>("detektSelbsttest") {
    setSource(files("config/detekt/selbsttest", layout.buildDirectory.dir("selbsttest")))
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    ignoreFailures = true
    reports {
        checkstyle.required.set(true)
        checkstyle.outputLocation.set(layout.buildDirectory.file("reports/detekt-selbsttest/detekt.xml"))
    }
}
