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

# --- Deps: Gradle-Metadaten zuerst, damit die Aufloesung der Abhaengigkeiten unabhaengig von
# Quelltextaenderungen aus dem Cache kommt. ----------------------------------
FROM gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06 AS basis

FROM basis AS deps
WORKDIR /src
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties gradle.lockfile settings-gradle.lockfile ./
COPY --chown=gradle:gradle gradle/libs.versions.toml gradle/verification-metadata.xml gradle/
RUN gradle --no-daemon help

# --- Compile: kompiliert den Produktionscode. Es kommt nur src/ hinzu, damit eine Aenderung an
# Doku oder Harness diese Stage nicht neu laufen laesst. -----------------------
FROM deps AS compile
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon classes

# --- Test: `docker build --target test .` - fuehrt die Unit-Tests aus (kotlin.test auf der
# JUnit-Plattform, SPEC-024). Es kommt nur src/ hinzu; ein roter Test bricht den Build. -----------
FROM deps AS test
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon test

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
COPY --chown=gradle:gradle src src
COPY --chown=gradle:gradle spec/spezifikation.md spec/spezifikation.md
COPY --chown=gradle:gradle tools/spec-abdeckung.sh tools/
RUN gradle --no-daemon test --continue || echo "spec-abdeckung: der Testlauf ist rot; rote Tests zaehlen nicht als Nachweis"
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
# Dockerfile ausfuehrt; manche Konfigurationen loest Gradle erst dabei auf. ---------------
FROM basis AS lock
ARG DEPS_TASKS
WORKDIR /src
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle gradle/libs.versions.toml gradle/
COPY --chown=gradle:gradle src src
COPY --chown=gradle:gradle config config
RUN gradle --no-daemon -q --write-locks --write-verification-metadata sha256 ${DEPS_TASKS}

FROM scratch AS lock-export
COPY --from=lock /src/gradle.lockfile /src/settings-gradle.lockfile /
COPY --from=lock /src/gradle/verification-metadata.xml /gradle/

# --- Build-Jar: der Jar mit vollem Gradle/JDK - nur ein Zwischenschritt fuer `build`. -------
FROM compile AS build-jar
RUN gradle --no-daemon assemble

# --- Build: `docker build .` (letzte Stage = Vorgabe) - minimales Artefakt-Image (Distroless
# static, Variante nonroot) mit nur dem fertigen Jar zum Herausholen (z. B. per `docker cp`);
# nichts darin wird ausgefuehrt. --------------------------------------------------
FROM gcr.io/distroless/static-debian12@sha256:afa5c872c891853ca7fcf1f12c3edb23f7eeef36189728842dd51042ff57f7ab AS build
COPY --from=build-jar /src/build/libs/*.jar /app/
