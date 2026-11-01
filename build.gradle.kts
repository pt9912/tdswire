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

// Das Protokoll der Stage `test` nennt jeden Test mit Ergebnis und bei einem Fehlschlag die Ursache; die Regel
// gilt fuer `test` und `integratorTest`.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// Der Einbettungstest (ADR-0021): ein Source-Set unter src/integrator, das gegen die Klassen des Hauptmoduls
// uebersetzt und nicht mit ihm assoziiert ist. Kotlin gibt `internal` nur Quelltext desselben Moduls oder eines
// assoziierten Source-Sets frei; hier ist nur die oeffentliche Flaeche uebersetzbar. Es laeuft im selben Prozess
// wie der Server (`gradle test integratorTest` in der Stage `test`, kein E2E-Lauf). Grenze: `internal` ist im
// Bytecode oeffentlich, der Nachweis gilt fuer Kotlin-Quellen im Source-Set.
val integrator: SourceSet by sourceSets.creating

dependencies {
    "integratorImplementation"(sourceSets.main.get().output)
    "integratorImplementation"(libs.kotlinx.coroutines.core)
    "integratorImplementation"(libs.kotlin.test)
    "integratorImplementation"(libs.kotlin.test.junit5)
    "integratorImplementation"(platform(libs.junit.bom))
    "integratorRuntimeOnly"(libs.junit.jupiter.engine)
    "integratorRuntimeOnly"(libs.junit.platform.launcher)
}

// Sensor: das Source-Set ist mit keiner anderen Kompilierung assoziiert. Eine Assoziation mit `main` gaebe ihm
// `internal` frei und machte den Einbettungstest wertlos; die Task bricht dann vor der Uebersetzung.
val integratorOhneAssoziation = tasks.register("integratorOhneAssoziation") {
    group = "verification"
    doLast {
        val associated = kotlin.target.compilations.getByName("integrator").associatedCompilations.map { it.name }
        check(associated.isEmpty()) { "Source-Set integrator ist assoziiert mit $associated" }
    }
}

tasks.named("compileIntegratorKotlin") { dependsOn(integratorOhneAssoziation) }

tasks.register<Test>("integratorTest") {
    description = "Einbettungstest ueber die oeffentliche Flaeche (ADR-0021)"
    group = "verification"
    testClassesDirs = integrator.output.classesDirs
    classpath = integrator.runtimeClasspath
}

// Der Testserver (ADR-0025): ein Source-Set unter src/testserver, das wie `integrator` gegen die Klassen des
// Hauptmoduls uebersetzt und nicht mit ihm assoziiert ist; er bindet die Bibliothek nur ueber ihre oeffentliche
// Flaeche (`TdsServer.start`). Jackson liest die Datei der Tabellenbeschreibung und gehoert nur diesem Source-Set;
// es steht weder im Klassenpfad der Bibliothek noch in `build/libs/lib`. Seine Tests laufen als `testserverTest`
// in der Stage `test`. Grenze wie bei `integrator`: der Nachweis gilt fuer Kotlin-Quellen im Source-Set.
val testserver: SourceSet by sourceSets.creating

// Die Tests des Testservers liegen unter src/testserver/test: detekt behandelt den Pfad `**/test/**` wie `src/test`
// (Standardausschluesse von Namens-, Magic-Number- und Groessenregeln fuer Tests).
kotlin.sourceSets.getByName("testserver").kotlin.srcDir("src/testserver/test/kotlin")

dependencies {
    "testserverImplementation"(sourceSets.main.get().output)
    "testserverImplementation"(libs.kotlinx.coroutines.core)
    "testserverImplementation"(libs.jackson.dataformat.yaml)
    "testserverImplementation"(libs.kotlin.test)
    "testserverImplementation"(libs.kotlin.test.junit5)
    "testserverImplementation"(platform(libs.junit.bom))
    "testserverRuntimeOnly"(libs.junit.jupiter.engine)
    "testserverRuntimeOnly"(libs.junit.platform.launcher)
}

// Sensor: das Source-Set ist mit keiner anderen Kompilierung assoziiert; wie `integratorOhneAssoziation`.
val testserverOhneAssoziation = tasks.register("testserverOhneAssoziation") {
    group = "verification"
    doLast {
        val associated = kotlin.target.compilations.getByName("testserver").associatedCompilations.map { it.name }
        check(associated.isEmpty()) { "Source-Set testserver ist assoziiert mit $associated" }
    }
}

tasks.named("compileTestserverKotlin") { dependsOn(testserverOhneAssoziation) }

tasks.register<Test>("testserverTest") {
    description = "Tests des Testservers: Lader, Leser der Beschreibung, Start (ADR-0025)"
    group = "verification"
    testClassesDirs = testserver.output.classesDirs
    classpath = testserver.runtimeClasspath
}

// Der Klassenpfad des Source-Sets als Datei: die Stage `testserver` im Dockerfile startet die JVM damit.
tasks.register("testserverKlassenpfad") {
    val ziel = layout.buildDirectory.file("testserver-klassenpfad.txt")
    inputs.files(testserver.runtimeClasspath)
    outputs.file(ziel)
    doLast { ziel.get().asFile.writeText(testserver.runtimeClasspath.asPath) }
}

// Die Tests der Bibliothek starten den Testserver als Kind-JVM (`ServerProcess`); sein Klassenpfad kommt als
// Systemeigenschaft.
tasks.named<Test>("test") {
    inputs.files(testserver.runtimeClasspath)
    doFirst { systemProperty("tdswire.testserverclasspath", testserver.runtimeClasspath.asPath) }
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

// Statische Analyse: `gradle detekt` liest den Quelltext, `gradle detektMain` und `detektTest` loesen
// zusaetzlich Typen auf (ForbiddenMethodCall wirkt nur dort). Im Dockerfile laufen alle fuenf (mit `detektIntegrator` und `detektTestserver`) in der
// Stage `detekt`, aufgerufen ueber `make detekt`. config/detekt/detekt.yml fuehrt nur die
// Abweichungen von detekts Standard. Keine Baseline und kein @Suppress: ein Befund wird im Code
// behoben. detekt 2.0 ist noch ein Pre-Release, die Wahl begruendet ADR-0010.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/integrator/kotlin", "src/testserver/kotlin", "src/testserver/test/kotlin")
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

// Laufzeit-Artefakt (Stage `runtime` im Dockerfile): das Jar traegt die Startklasse und die Lizenz, die
// Bibliotheken der Laufzeit liegen in build/libs/lib und stehen im Class-Path des Manifests. Die Lizenz
// kommt aus dem Wurzelverzeichnis; fehlt sie dort, ist das Jar ohne sie, und die Stage `build-jar` bricht.
val laufzeitBibliotheken = tasks.register<Copy>("laufzeitBibliotheken") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("libs/lib"))
}

tasks.jar {
    archiveFileName.set("tdswire.jar")
    from(layout.projectDirectory.file("LICENSE")) { into("META-INF") }
    manifest.attributes("Main-Class" to "io.github.pt9912.tdswire.runtime.MainKt")
    doFirst {
        manifest.attributes(
            "Class-Path" to configurations.runtimeClasspath.get().files.joinToString(" ") { "lib/${it.name}" },
        )
    }
}

tasks.assemble { dependsOn(laufzeitBibliotheken) }

// Der Rohbytes-Client des Laufzeittests (`make laufzeittest`): Klasse im Test-Source-Set, Ziel aus TDSWIRE_ZIEL.
tasks.register<JavaExec>("laufzeitClient") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.pt9912.tdswire.runtime.LaufzeitClientKt")
}

// Der Testserver des Integrationslaufs (Stage `integrationsserver`, `make simulations-integration`): Klasse im
// Source-Set `testserver`, Heap auf 48 MiB begrenzt, damit der Text der Tabelle `gross` den Heap uebersteigt.
tasks.register<JavaExec>("integrationsServer") {
    classpath = testserver.runtimeClasspath
    mainClass.set("io.github.pt9912.tdswire.testserver.IntegrationServerKt")
    maxHeapSize = "48m"
    jvmArgs("-Djdk.tls.server.newSessionTicketCount=0")
}

// Der Testserver des Kompatibilitaetslaufs (Stage `kompatibilitaetsserver`, `make kompatibilitaet`): Klasse im
// Source-Set `testserver`, Richtlinie REQUIRED mit Schluesselspeicher aus der Umgebung der Stage.
tasks.register<JavaExec>("kompatibilitaetsServer") {
    classpath = testserver.runtimeClasspath
    mainClass.set("io.github.pt9912.tdswire.testserver.KompatibilitaetsServerKt")
    jvmArgs("-Djdk.tls.server.newSessionTicketCount=0")
}
