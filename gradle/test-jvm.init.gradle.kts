// Init-Skript der Stages `test` und `spec-abdeckung` (Dockerfile): die Test-JVM setzt das Flag des
// Sicherheitsprofils (ohne es sendet der JDK-Server in TLS 1.3 ein NewSessionTicket im Handshake,
// contracts/security-profile.md). `tdswire.testkeys` nennt das Verzeichnis der PKCS#12-Speicher, die
// die Stage `testkeys` erzeugt; `tdswire.testclasspath` den Klassenpfad für die Kind-JVM eines Tests.
allprojects {
    tasks.withType<Test>().configureEach {
        jvmArgs("-Djdk.tls.server.newSessionTicketCount=0")
        systemProperty("tdswire.testkeys", layout.buildDirectory.dir("testkeys").get().asFile.path)
        doFirst { systemProperty("tdswire.testclasspath", classpath.asPath) }
    }
}
