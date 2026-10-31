// Init-Skript der Stages `test` und `spec-abdeckung` (Dockerfile): die Test-JVM setzt das Flag des
// Sicherheitsprofils (ohne es sendet der JDK-Server in TLS 1.3 ein NewSessionTicket im Handshake,
// spec/spezifikation.md SPEC-023). `tdswire.testkeys` nennt das Verzeichnis der PKCS#12-Speicher, die
// die Stage `testkeys` erzeugt; `tdswire.testclasspath` den Klassenpfad für die Kind-JVM eines Tests.
// Die Test-JVM läuft mit 128 MiB Heap: der Speicher-Beleg in `StreamingSmokeTest` überträgt ein Ergebnis,
// dessen Texte allein größer sind als dieser Heap; der Test prüft den Wert zur Laufzeit gegen
// `Runtime.maxMemory()`.
allprojects {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "128m"
        jvmArgs("-Djdk.tls.server.newSessionTicketCount=0")
        systemProperty("tdswire.testkeys", layout.buildDirectory.dir("testkeys").get().asFile.path)
        doFirst { systemProperty("tdswire.testclasspath", classpath.asPath) }
    }
}
