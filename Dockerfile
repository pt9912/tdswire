# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e
# ---------------------------------------------------------------------------
# tdswire - Kotlin/JVM-TDS-Server
#
# Build und statische Analyse laufen nur hier, nie auf dem Host (TDS-QA-06):
#   make detekt   -> docker build --target detekt --progress=plain .
#   make test     -> docker build --target test --progress=plain .
#   make build    -> docker build -t tdswire:latest .   (Jar, ohne Tests)
# Das Dockerfile-Frontend und beide Basis-Images (Gradle, Distroless) sind per Digest gepinnt,
# damit der Build reproduzierbar bleibt; ein neuer Digest ist eine bewusste Aenderung dieser
# Datei.
# ---------------------------------------------------------------------------

FROM gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06 AS basis

# --- Deps: laedt die Artefakte aller Konfigurationen, die compile, test, detekt und assemble
# brauchen (Gradle-Task `abhaengigkeitenLaden`, build.gradle.kts), mit aktiver Sperre und
# Verifikation. Die Stage steht direkt nach `basis` und kopiert genau die Dateien, die die
# Aufloesung bestimmen; src/, config/, tools/, Doku und Harness liegen nicht im Eingang. compile,
# test, detekt, detekt-selbsttest und spec-abdeckung erben von ihr, eine Quelltextaenderung loest dort
# also keinen Download aus; testkeys, lock und spec-abdeckung-selbsttest erben von `basis`. Eine neue
# Eingangsdatei der Aufloesung gehoert in die COPY-Zeilen dieser Stage und nirgends sonst hin.
# Kopplung: build.gradle.kts ist Eingang; jede Aenderung dort, auch an einem Kommentar, laesst die
# Stage neu laufen und laedt alles neu. ----
FROM basis AS deps
WORKDIR /src
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties gradle.lockfile settings-gradle.lockfile ./
COPY --chown=gradle:gradle gradle/libs.versions.toml gradle/verification-metadata.xml gradle/
RUN gradle --no-daemon abhaengigkeitenLaden

# --- Compile: kompiliert den Produktionscode. Es kommt nur src/ hinzu, damit eine Aenderung an
# Doku oder Harness diese Stage nicht neu laufen laesst. -----------------------
FROM deps AS compile
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon classes

# --- Testkeys: erzeugt per `keytool` die PKCS#12-Speicher der TLS-Tests (ein gueltiges Zertifikat fuer
# localhost, eines fuer einen anderen Namen, ein abgelaufenes). Kein Schluessel liegt im Repo; die
# Stage haengt weder an src/ noch an Doku und kommt aus dem Cache, bis sich die Toolchain aendert.
# `-validity 3650` zaehlt ab dem Bauzeitpunkt des Layers: bleibt er zehn Jahre im Cache, sind
# gueltig.p12 und falscher-name.p12 abgelaufen und die TLS-Tests rot (Neubau mit --no-cache-filter
# testkeys behebt es). abgelaufen.p12 haengt nicht an der Bauzeit: sein Blatt hat ein festes
# `-startdate` (2020/01/01, 30 Tage). blatt.p12 (Schluessel) mit blatt.pem, zwischen-abgelaufen.pem und
# wurzel.pem ist eine Kette, deren Zwischenzertifikat abgelaufen ist und deren Blatt gilt. -----
FROM basis AS testkeys
WORKDIR /testkeys
RUN keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 \
      -keystore gueltig.p12 -storepass tdswire-test -dname "CN=localhost" -ext "san=dns:localhost" \
 && keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 \
      -keystore falscher-name.p12 -storepass tdswire-test -dname "CN=andere.invalid" -ext "san=dns:andere.invalid" \
 && keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 -ext bc:c \
      -keystore ca.p12 -storepass tdswire-test -dname "CN=tdswire-test-ca" \
 && keytool -exportcert -alias ca -keystore ca.p12 -storepass tdswire-test -rfc -file ca.pem \
 && keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 30 -startdate "2020/01/01 00:00:00" \
      -storetype PKCS12 -keystore abgelaufen.p12 -storepass tdswire-test -dname "CN=localhost" \
      -ext "san=dns:localhost" \
 && keytool -certreq -alias server -keystore abgelaufen.p12 -storepass tdswire-test -file abgelaufen.csr \
 && keytool -gencert -alias ca -keystore ca.p12 -storepass tdswire-test -infile abgelaufen.csr -rfc \
      -startdate "2020/01/01 00:00:00" -validity 30 -ext "san=dns:localhost" -outfile abgelaufen.pem \
 && keytool -importcert -alias ca -file ca.pem -keystore abgelaufen.p12 -storepass tdswire-test -noprompt \
 && keytool -importcert -alias server -file abgelaufen.pem -keystore abgelaufen.p12 -storepass tdswire-test \
 && rm ca.p12 ca.pem abgelaufen.csr abgelaufen.pem \
 && keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 -ext bc:c \
      -keystore wurzel.p12 -storepass tdswire-test -dname "CN=tdswire-wurzel" \
 && keytool -exportcert -alias ca -keystore wurzel.p12 -storepass tdswire-test -rfc -file wurzel.pem \
 && keytool -genkeypair -alias zw -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 -ext bc:c \
      -keystore zwischen.p12 -storepass tdswire-test -dname "CN=tdswire-zwischen" \
 && keytool -certreq -alias zw -keystore zwischen.p12 -storepass tdswire-test -file zwischen.csr \
 && keytool -gencert -alias ca -keystore wurzel.p12 -storepass tdswire-test -infile zwischen.csr -rfc \
      -startdate "2020/01/01 00:00:00" -validity 30 -ext bc:c -outfile zwischen-abgelaufen.pem \
 && keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 \
      -keystore blatt.p12 -storepass tdswire-test -dname "CN=localhost" -ext "san=dns:localhost" \
 && keytool -certreq -alias server -keystore blatt.p12 -storepass tdswire-test -file blatt.csr \
 && keytool -gencert -alias zw -keystore zwischen.p12 -storepass tdswire-test -infile blatt.csr -rfc \
      -validity 3650 -ext "san=dns:localhost" -outfile blatt.pem \
 && rm wurzel.p12 zwischen.p12 zwischen.csr blatt.csr

# --- Test: `docker build --target test .` - fuehrt die Unit-Tests aus (kotlin.test auf der
# JUnit-Plattform, SPEC-024). Es kommt nur src/ hinzu; ein roter Test bricht den Build. -----------
FROM deps AS test
COPY --chown=gradle:gradle LICENSE LICENSE
COPY --chown=gradle:gradle src src
COPY --from=testkeys --chown=gradle:gradle /testkeys build/testkeys
COPY --chown=gradle:gradle gradle/test-jvm.init.gradle.kts gradle/
RUN gradle --no-daemon -I gradle/test-jvm.init.gradle.kts test

# --- Smoke: `docker build --target smoke .` - der Rohbytes-Smoke der Welle allein (ServerSmokeTest): die
# Kind-JVM startet `main` mit dem Flag der Test-JVM und einer Testkonfiguration, ein Rohbytes-Client spricht den
# Login-Pfad. Dieselben Tests laufen auch in der Stage `test`; die Stage ist der engste Sensor dafuer. ----
FROM deps AS smoke
COPY --chown=gradle:gradle src src
COPY --from=testkeys --chown=gradle:gradle /testkeys build/testkeys
COPY --chown=gradle:gradle gradle/test-jvm.init.gradle.kts gradle/
RUN gradle --no-daemon -I gradle/test-jvm.init.gradle.kts test --tests 'io.github.pt9912.tdswire.runtime.ServerSmokeTest'

# --- Detekt: `docker build --target detekt .` - zuerst das Suppression-Verbot (AGENTS.md 3.2,
# tools/suppression-check.sh), dann die statische Analyse samt ktlint-Regelsatz und Regeln der
# Schichten (config/detekt/detekt.yml). `detekt` liest nur Quelltext; `detektMain` und
# `detektTest` loesen Typen auf und kompilieren dafuer, ForbiddenMethodCall wirkt nur dort. Jeder
# Befund bricht den Build, es gibt keine Baseline. ----------------------------
FROM deps AS detekt
COPY --chown=gradle:gradle src src
COPY --chown=gradle:gradle config config
COPY --chown=gradle:gradle tools/suppression-check.sh tools/suppression-check.sh
RUN sh tools/suppression-check.sh src \
 && gradle --no-daemon detekt detektMain detektTest

# --- Detekt-Selbsttest: `docker build --target detekt-selbsttest .` - belegt, dass jede
# Importregel verbotene Importe meldet und erlaubte nicht, dass jeder verbotene Aufruf gemeldet
# wird, und dass das Suppression-Verbot die Formen von @Suppress meldet, die das Skript nennt
# (tools/detekt-selbsttest.sh). Kopiert weder src/ noch Doku: die Stage
# laeuft nur neu, wenn sich Regeln, Testdaten, Skripte oder Toolchain aendern. -----
FROM deps AS detekt-selbsttest
COPY --chown=gradle:gradle config config
COPY --chown=gradle:gradle tools/detekt-selbsttest.sh tools/suppression-check.sh tools/
RUN sh tools/detekt-selbsttest.sh generate \
 && gradle --no-daemon detektSelbsttest detektSelbsttestAufrufe \
 && sh tools/detekt-selbsttest.sh check \
 && sh tools/detekt-selbsttest.sh aufrufe \
 && sh tools/detekt-selbsttest.sh suppression

# --- Spec-Abdeckung: `make spec-abdeckung` - fuehrt die Tests aus (rote Tests brechen die Stage
# nicht: sie stehen dann in der Matrix als nicht bestanden) und erzeugt aus den @spec-Tags und den
# Berichten die Abdeckungsmatrix (tools/spec-abdeckung.sh). `spec-abdeckung-export` gibt Matrix und
# Waisenzahl per `docker build --output` heraus, nichts wird gemountet. ------------------
FROM deps AS spec-abdeckung
COPY --chown=gradle:gradle LICENSE LICENSE
COPY --chown=gradle:gradle src src
COPY --from=testkeys --chown=gradle:gradle /testkeys build/testkeys
COPY --chown=gradle:gradle gradle/test-jvm.init.gradle.kts gradle/
COPY --chown=gradle:gradle spec/spezifikation.md spec/spezifikation.md
COPY --chown=gradle:gradle tools/spec-abdeckung.sh tools/
RUN gradle --no-daemon -I gradle/test-jvm.init.gradle.kts test --continue || echo "spec-abdeckung: der Testlauf ist rot; rote Tests zaehlen nicht als Nachweis"
RUN mkdir -p /tmp/ausgabe \
 && ZIEL=/tmp/ausgabe/spec-abdeckung.md WAISEN_DATEI=/tmp/ausgabe/waisen sh tools/spec-abdeckung.sh

FROM scratch AS spec-abdeckung-export
COPY --from=spec-abdeckung /tmp/ausgabe/ /

# --- Spec-Abdeckung-Selbsttest: `docker build --target spec-abdeckung-selbsttest .` - belegt am
# erzeugten Mini-Bestand, dass der Erzeuger nur bestandene Tests zaehlt (tools/spec-abdeckung-selbsttest.sh).
FROM basis AS spec-abdeckung-selbsttest
WORKDIR /src
COPY tools/spec-abdeckung.sh tools/spec-abdeckung-selbsttest.sh tools/
RUN sh tools/spec-abdeckung-selbsttest.sh

# --- Lock: schreibt gradle.lockfile, settings-gradle.lockfile und gradle/verification-metadata.xml
# neu (`make deps-lock`, braucht Netz). Es wird nichts gemountet: die Stage `lock-export` liefert
# die drei Dateien per `docker build --output`. DEPS_TASKS nennt die Gradle-Tasks, die dieses
# Dockerfile ausfuehrt; manche Konfigurationen loest Gradle erst dabei auf. Die Task `test` laeuft
# mit denselben Eingaengen wie die Stage `test` (Schluessel aus `testkeys`, Init-Skript). ---------------
FROM basis AS lock
ARG DEPS_TASKS
WORKDIR /src
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle gradle/libs.versions.toml gradle/
COPY --chown=gradle:gradle src src
COPY --chown=gradle:gradle config config
COPY --from=testkeys --chown=gradle:gradle /testkeys build/testkeys
COPY --chown=gradle:gradle gradle/test-jvm.init.gradle.kts gradle/
RUN gradle --no-daemon -q -I gradle/test-jvm.init.gradle.kts --write-locks --write-verification-metadata sha256 ${DEPS_TASKS}

FROM scratch AS lock-export
COPY --from=lock /src/gradle.lockfile /src/settings-gradle.lockfile /
COPY --from=lock /src/gradle/verification-metadata.xml /gradle/

# --- Build-Jar: der Jar mit vollem Gradle/JDK - nur ein Zwischenschritt fuer `build`. -------
FROM compile AS build-jar
COPY --chown=gradle:gradle LICENSE LICENSE
RUN gradle --no-daemon assemble \
 && jar tf build/libs/tdswire.jar | grep -qx 'META-INF/LICENSE' \
 && unzip -p build/libs/tdswire.jar META-INF/LICENSE | cmp - LICENSE

# --- Laufzeit: `make laufzeittest` baut diese Stage. Distroless Java 21 (Variante nonroot) mit JRE,
# per Digest gepinnt (Messung vom 2026-10-31 am Tag `nonroot`; ein neuer Digest ist eine bewusste
# Aenderung dieser Datei). Nutzer 65532 ohne Root-Rechte. Das Image enthaelt Jar, Bibliotheken und
# Lizenz, keine Konfiguration, keine Benutzerliste, kein Geheimnis: alles kommt zur Laufzeit ueber
# die Umgebung (ConfigKeys) und Dateien im Container. Betreiber-Optionen der JVM kommen ueber
# JAVA_TOOL_OPTIONS (z. B. -XX:+ExitOnOutOfMemoryError, ADR-0013); das Flag gegen das
# NewSessionTicket steht in der Startzeile (SPEC-023). ----
FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d AS runtime
COPY --from=build-jar /src/build/libs/tdswire.jar /app/tdswire.jar
COPY --from=build-jar /src/build/libs/lib/ /app/lib/
COPY --from=build-jar /src/LICENSE /app/LICENSE
USER 65532:65532
EXPOSE 1433
ENTRYPOINT ["java", "-Djdk.tls.server.newSessionTicketCount=0", "-jar", "/app/tdswire.jar"]

# --- Laufzeit-Client: der Rohbytes-Client des Laufzeittests als zweiter Container im selben Netzwerk;
# die Abhaengigkeiten liegen schon in `deps`, der Start braucht kein Netz. ----
FROM deps AS laufzeit-client
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon --offline testClasses
CMD ["gradle", "--no-daemon", "--offline", "-q", "laufzeitClient"]

# --- Build: `docker build .` (letzte Stage = Vorgabe) - minimales Artefakt-Image (Distroless
# static, Variante nonroot) mit nur dem fertigen Jar zum Herausholen (z. B. per `docker cp`);
# nichts darin wird ausgefuehrt. --------------------------------------------------
FROM gcr.io/distroless/static-debian12@sha256:afa5c872c891853ca7fcf1f12c3edb23f7eeef36189728842dd51042ff57f7ab AS build
COPY --from=build-jar /src/build/libs/*.jar /app/
