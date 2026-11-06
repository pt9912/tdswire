plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

group = "io.github.pt9912"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Der Testserver (ADR-0034): ein eigenes Gradle-Modul, das die Bibliothek als Projekt-Abhaengigkeit bindet und
// nur ihre oeffentliche Flaeche (`TdsServer.start`) sieht. `internal` der Bibliothek ist hier nicht uebersetzbar, solange die
// Modulgrenze zu bleibt (tools/modulgrenze-sperre.sh prueft diese Datei; Messung M1:
// docs/messungen/testserver-gradle-modul-2026-11-06.md).
// Jackson liest die Datei der Tabellenbeschreibung und gehoert nur diesem Modul; es steht weder im Klassenpfad der
// Bibliothek noch in `build/libs/lib`. Grenze des Nachweises: `internal` ist im Bytecode oeffentlich, er gilt fuer
// Kotlin-Quellen im Modul.
// Jede aufgeloeste Version steht in testserver/gradle.lockfile; `make deps-lock` schreibt sie bewusst neu.
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    // ktlint-Regelsatz (Abschnitt `ktlint:` in config/detekt/detekt.yml); die Version ist die des Plugins.
    detektPlugins(libs.detekt.ktlint)

    implementation(project(":"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Die Verzeichnisse des Moduls: Quellen unter kotlin, Tests unter test/kotlin,
// Ressourcen unter resources. Der Pfad `**/test/**` der Tests ist fuer detekt der Standardausschluss fuer Tests.
sourceSets {
    main {
        kotlin.setSrcDirs(listOf("kotlin"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        kotlin.setSrcDirs(listOf("test/kotlin"))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// Dieselbe Konfiguration wie die Bibliothek: eine Datei, keine Baseline.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("kotlin", "test/kotlin")
}

// Der Klassenpfad des Moduls als Datei: die Stage `testserver` im Dockerfile startet die JVM damit, und die Task
// `test` der Bibliothek reicht ihn an die Kind-JVM von `ServerProcess` weiter. Er nennt das Jar `tdswire.jar` der
// Bibliothek (mit Manifest und Lizenz), nicht ihr Klassenverzeichnis; die Task bricht, wenn das Jar fehlt oder ein
// Verzeichnis ausserhalb des Moduls im Klassenpfad steht (Messpunkt M2, ADR-0034 Entscheidung 2).
tasks.register("testserverKlassenpfad") {
    val ziel = layout.buildDirectory.file("testserver-klassenpfad.txt")
    val klassenpfad = sourceSets.main.get().runtimeClasspath
    val modul = projectDir
    inputs.files(klassenpfad)
    outputs.file(ziel)
    doLast {
        val eintraege = klassenpfad.files
        check(eintraege.any { it.name == "tdswire.jar" }) { "Klassenpfad des Testservers nennt tdswire.jar nicht" }
        val fremd = eintraege.filter { it.isDirectory && !it.startsWith(modul) }
        check(fremd.isEmpty()) { "Klassenpfad des Testservers nennt Verzeichnisse ausserhalb des Moduls: $fremd" }
        ziel.get().asFile.writeText(klassenpfad.asPath)
    }
}

// Der Testserver des Integrationslaufs (Stage `integrationsserver`, `make simulations-integration`): Heap auf 48 MiB
// begrenzt, damit der Text der Tabelle `gross` den Heap uebersteigt.
tasks.register<JavaExec>("integrationsServer") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.pt9912.tdswire.testserver.IntegrationServerKt")
    maxHeapSize = "48m"
}

// Der Testserver des Kompatibilitaetslaufs (Stage `kompatibilitaetsserver`, `make kompatibilitaet`): Richtlinie
// REQUIRED mit Schluesselspeicher aus der Umgebung der Stage.
tasks.register<JavaExec>("kompatibilitaetsServer") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.pt9912.tdswire.testserver.KompatibilitaetsServerKt")
}
