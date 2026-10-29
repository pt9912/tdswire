# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e
# ---------------------------------------------------------------------------
# tdswire - Kotlin/JVM-TDS-Server
#
# Build und statische Analyse laufen nur hier, nie auf dem Host (TDS-QA-06):
#   make detekt   -> docker build --target detekt --progress=plain .
#   make build    -> docker build -t tdswire:latest .   (Jar, ohne Tests)
# Einen Test-Stage gibt es noch nicht; er entsteht mit dem ersten Test.
# Das Dockerfile-Frontend und beide Basis-Images (Gradle, Distroless) sind per Digest gepinnt,
# damit der Build reproduzierbar bleibt; ein neuer Digest ist eine bewusste Aenderung dieser
# Datei.
# ---------------------------------------------------------------------------

# --- Deps: Gradle-Metadaten zuerst, damit die Aufloesung der Abhaengigkeiten unabhaengig von
# Quelltextaenderungen aus dem Cache kommt. ----------------------------------
FROM gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06 AS deps
WORKDIR /src
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties gradle.lockfile settings-gradle.lockfile ./
COPY --chown=gradle:gradle gradle/libs.versions.toml gradle/verification-metadata.xml gradle/
RUN gradle --no-daemon help

# --- Compile: kompiliert den Produktionscode. Es kommt nur src/ hinzu, damit eine Aenderung an
# Doku oder Harness diese Stage nicht neu laufen laesst. -----------------------
FROM deps AS compile
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon classes

# --- Detekt: `docker build --target detekt .` - zuerst das Suppression-Verbot (AGENTS.md 3.2,
# tools/suppression-check.sh), dann die statische Analyse samt Importregeln der Schichten
# (config/detekt/detekt.yml). Baut auf `deps` auf: detekt ohne Typaufloesung liest nur Quelltext.
# Jeder Befund bricht den Build, es gibt keine Baseline. ----------------------------
FROM deps AS detekt
COPY --chown=gradle:gradle src src
COPY --chown=gradle:gradle config config
COPY --chown=gradle:gradle tools/suppression-check.sh tools/suppression-check.sh
RUN sh tools/suppression-check.sh src \
 && gradle --no-daemon detekt

# --- Detekt-Selbsttest: `docker build --target detekt-selbsttest .` - belegt, dass jede
# Importregel verbotene Importe meldet und erlaubte nicht, und dass das Suppression-Verbot jede
# Form von @Suppress meldet (tools/detekt-selbsttest.sh). Kopiert weder src/ noch Doku: die Stage
# laeuft nur neu, wenn sich Regeln, Testdaten, Skripte oder Toolchain aendern. -----
FROM deps AS detekt-selbsttest
COPY --chown=gradle:gradle config config
COPY --chown=gradle:gradle tools/detekt-selbsttest.sh tools/suppression-check.sh tools/
RUN sh tools/detekt-selbsttest.sh generate \
 && gradle --no-daemon detektSelbsttest \
 && sh tools/detekt-selbsttest.sh check \
 && sh tools/detekt-selbsttest.sh suppression

# --- Build-Jar: der Jar mit vollem Gradle/JDK - nur ein Zwischenschritt fuer `build`. -------
FROM compile AS build-jar
RUN gradle --no-daemon assemble

# --- Build: `docker build .` (letzte Stage = Vorgabe) - minimales Artefakt-Image (Distroless
# static, Variante nonroot) mit nur dem fertigen Jar zum Herausholen (z. B. per `docker cp`);
# nichts darin wird ausgefuehrt. --------------------------------------------------
FROM gcr.io/distroless/static-debian12@sha256:afa5c872c891853ca7fcf1f12c3edb23f7eeef36189728842dd51042ff57f7ab AS build
COPY --from=build-jar /src/build/libs/*.jar /app/
