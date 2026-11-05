// Init-Skript der Stages `test` und `spec-abdeckung` (Dockerfile): die Test-JVM setzt keine TLS-Option, der
// JDK-Server sendet sein Voreinstellungs-Ticket (spec/spezifikation.md SPEC-023). `tdswire.testkeys` nennt das
// Verzeichnis der PKCS#12-Speicher, die die Stage `testkeys` erzeugt.
// Die Test-JVM läuft mit 128 MiB Heap: der Speicher-Beleg in `StreamingSmokeTest` überträgt ein Ergebnis,
// dessen Texte allein größer sind als dieser Heap; der Test prüft den Wert zur Laufzeit gegen
// `Runtime.maxMemory()`.
allprojects {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "128m"
        systemProperty("tdswire.testkeys", layout.buildDirectory.dir("testkeys").get().asFile.path)
    }
}
