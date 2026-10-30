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

dependencies {
    // ktlint-Regelsatz (Abschnitt `ktlint:` in config/detekt/detekt.yml); die Version ist die des Plugins.
    detektPlugins(libs.detekt.ktlint)

    // Laufzeit des Transports (ADR-0003); die Version steht nur im Katalog.
    implementation(libs.kotlinx.coroutines.core)

    // Unit-Tests: kotlin.test; die BOM gilt für Compile- und Laufzeit-Classpath, damit der Lock eine Version je Artefakt führt.
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Das Protokoll der Stage `test` nennt jeden Test mit Ergebnis und bei einem Fehlschlag die Ursache.
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

kotlin {
    jvmToolchain(21)
}

// Statische Analyse: `gradle detekt` liest den Quelltext, `gradle detektMain` und `detektTest` loesen
// zusaetzlich Typen auf (ForbiddenMethodCall wirkt nur dort). Im Dockerfile laufen alle drei in der
// Stage `detekt`, aufgerufen ueber `make detekt`. config/detekt/detekt.yml fuehrt nur die
// Abweichungen von detekts Standard. Keine Baseline und kein @Suppress: ein Befund wird im Code
// behoben. detekt 2.0 ist noch ein Pre-Release, die Wahl begruendet ADR-0010.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
}

// Selbsttest der Importregeln: dieselbe Konfiguration, aber ueber Testdaten statt ueber src/.
// config/detekt/selbsttest/ traegt die erlaubten Importe (Allowed.kt) und die Fremdbibliotheken des
// Kerns (Fremd.kt), build/selbsttest/ die verbotenen, die tools/detekt-selbsttest.sh aus detekt.yml
// erzeugt. Befunde brechen diese Task nicht - das Skript wertet den Bericht aus
// (`make detekt-selbsttest`).
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

// Selbsttest der Aufrufregeln: ForbiddenMethodCall braucht Typen, die Beispiele muessen also
// kompilieren. Sie liegen in einem eigenen Source-Set und gehen nie in das Jar (`gradle assemble`
// baut nur main). Die Task `detektSelbsttestAufrufe` legt das detekt-Plugin dazu an.
sourceSets.create("selbsttestAufrufe")
kotlin.sourceSets.getByName("selbsttestAufrufe").kotlin.srcDir("config/detekt/selbsttest-aufrufe")

// Auch diese Task bricht nicht bei Befunden; tools/detekt-selbsttest.sh wertet ihren Bericht aus.
tasks.withType<dev.detekt.gradle.Detekt>().matching { it.name == "detektSelbsttestAufrufe" }.configureEach {
    ignoreFailures = true
    reports {
        checkstyle.required.set(true)
        checkstyle.outputLocation.set(layout.buildDirectory.file("reports/detekt-selbsttest/aufrufe.xml"))
    }
}

// Laedt die Artefakte der Konfigurationen, die compile, test, detekt und assemble brauchen, in den
// Gradle-Cache (Stage `deps` des Dockerfiles), damit eine Quelltextaenderung nichts mehr aus dem
// Netz holt. Die Menge ist die der Sperrdatei ohne `nichtGebraucht`: Konfigurationen, die nur
// `gradle dependencies` aufloest (ABI-Pruefung, Native, Klib-Commonizer), laedt kein Build der
// Stages, ihre Artefakte waeren tote Last. Sperre und Verifikation bleiben aktiv; eine Version,
// die nicht aufloest, bricht die Task. Sie schreibt weder Sperre noch Pruefsummen.
// Kopplung: `-PnurNamen=<a,b>` schraenkt die Menge ein (Pruefung der Task ohne Netz).
// Grenze: Ein Name, den `findByName` nicht findet, und eine nicht aufloesbare Konfiguration werden
// uebersprungen; kein Sensor meldet das, die Task laeuft auch mit null geladenen Konfigurationen gruen.
tasks.register("abhaengigkeitenLaden") {
    val sperre = layout.projectDirectory.file("gradle.lockfile").asFile
    val nichtGebraucht = setOf(
        "kotlinAbiValidationCompatClasspath",
        "kotlinKlibCommonizerClasspath",
        "kotlinNativeCompilerPluginClasspath",
    )
    val nurNamen = (findProperty("nurNamen") as String?)?.split(',')?.toSet()
    doLast {
        val namen = sperre.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains('=') }
            .flatMap { it.substringAfter('=').split(',') }
            .toSortedSet()
        namen.removeAll(nichtGebraucht)
        if (nurNamen != null) namen.retainAll(nurNamen)
        for (name in namen) {
            val konfiguration = configurations.findByName(name)?.takeIf { it.isCanBeResolved } ?: continue
            logger.lifecycle("abhaengigkeitenLaden: $name (${konfiguration.resolve().size} Dateien)")
        }
    }
}
