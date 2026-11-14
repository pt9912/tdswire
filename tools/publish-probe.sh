#!/bin/bash
# publish-probe.sh — Erzeuger der Messungen zum Maven-Paket (TDS-RB-01, TDS-RB-05, TDS-QA-06, ADR-0047); kein Gate, kein
# Make-Target, schreibt nirgends hin ausser in Containern und ein Wegwerf-Verzeichnis. Das Messprotokoll
# docs/messungen/maven-paket-2026-11-14.md nennt die Laeufe dieses Skripts.
#
# Aufruf: publish-probe.sh konsument   Messungen (a) und (b): ein Probe-Konsument bindet die Bibliothek nur ueber
#                                      publishToMavenLocal (mit Gradle-Metadaten und nur ueber die POM), einmal mit dem Scope
#                                      `api` und einmal mit `implementation` fuer kotlinx-coroutines-core; Gegenfall ohne
#                                      transitive Abhaengigkeiten, mit und ohne lib/ neben dem Jar (der Class-Path greift nur, wenn
#                                      lib/ liegt).
#         publish-probe.sh token       Messungen (c) und (d): tools/publish.sh in einem Wegwerf-Repo (Version 0.0.1, Tag
#                                      v0.0.1) gegen einen Dummy-Server in einem Docker-Netz ohne Zugang nach aussen, mit einem
#                                      Platzhalter-Token; das Ziel ist nicht GitHub.
# Voraussetzung: Docker, bash, git, tar. Es wird nichts nach GitHub geschrieben, kein Tag im Repo gesetzt, kein echtes Token
# gelesen: das Token ist ein Platzhalter, den das Skript selbst erzeugt, und GITHUB_TOKEN der Umgebung wird nicht gelesen.
# Grenzen: die Messung (c) und (d) sucht den Platzhalter im Klartext, als Base64 des Tokens und als Base64 von
# `Benutzer:Token`; andere abgeleitete Formen findet sie nicht. Ein Weg zu GitHub ist damit nicht belegt (Ziel ist ein Dummy).
set -eu

wurzel=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
netz=""
dummy=""
aufraeumen() {
  [ -z "$dummy" ] || docker rm -f "$dummy" > /dev/null 2>&1 || true
  [ -z "$netz" ] || docker network rm "$netz" > /dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap aufraeumen EXIT

basis_image() { sed -n 's/^FROM \(gradle:[^ ]*\) AS basis$/\1/p' "$wurzel/Dockerfile"; }

konsument() {
  # shellcheck disable=SC2086
  docker build ${BUILDER_FLAGS:-} --target publish -t tdswire-publish-probe:lokal --progress=plain "$wurzel" > "$tmp/bau.log" 2>&1 \
    || { tail -20 "$tmp/bau.log" >&2; exit 1; }
  cat > "$tmp/konsument.sh" <<'CONTAINER'
set -eu
scope="$1"
cd /src
sed -i "s/^    \(api\|implementation\)(libs.kotlinx.coroutines.core)\$/    $scope(libs.kotlinx.coroutines.core)/" build.gradle.kts
grep -n "$scope(libs.kotlinx.coroutines.core)" build.gradle.kts > /dev/null || { echo "Scope $scope nicht gesetzt"; exit 1; }
gradle --no-daemon --offline -q publishToMavenLocal
repo=$HOME/.m2/repository/io/github/pt9912/tdswire/0.1.0-SNAPSHOT
echo "== Scope $scope: POM-Eintrag der Abhaengigkeit"
grep -B2 -A3 'kotlinx-coroutines-core-jvm' "$repo/tdswire-0.1.0-SNAPSHOT.pom" | grep -E 'artifactId|scope'
echo "== Scope $scope: Class-Path im veroeffentlichten Jar und Verzeichnis daneben"
unzip -p "$repo/tdswire-0.1.0-SNAPSHOT.jar" META-INF/MANIFEST.MF | tr -d '\r' | awk '/^ / { sub(/^ /, ""); buf = buf $0; next } { if (NR > 1) print buf; buf = $0 } END { print buf }' | grep '^Class-Path'
ls "$repo"
mkdir -p /probe/src/main/kotlin
cd /probe
echo 'rootProject.name = "probe"' > settings.gradle.kts
cat > build.gradle.kts <<'KTS'
plugins {
    kotlin("jvm") version "2.4.10"
    application
}
repositories {
    mavenLocal {
        metadataSources {
            if (!providers.gradleProperty("nurPom").isPresent) gradleMetadata()
            mavenPom()
            artifact()
        }
    }
    mavenCentral()
}
dependencies {
    implementation("io.github.pt9912:tdswire:0.1.0-SNAPSHOT") {
        isTransitive = !providers.gradleProperty("nichtTransitiv").isPresent
    }
    if (providers.gradleProperty("nichtTransitiv").isPresent) {
        compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    }
}
application { mainClass.set("ProbeKt") }
KTS
cat > src/main/kotlin/Probe.kt <<'KOTLIN'
import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

suspend fun main() {
    val users = (UserList.parse("alice\t{plain}secret\n", true) as UserListResult.Valid).list
    val config = ServerConfig(
        listen = ListenSettings(host = "127.0.0.1", port = 0),
        encryption = EncryptionSetting.TEST_UNENCRYPTED,
        auth = AuthSettings(users, plainTestMode = true),
    )
    val backend = object : QueryBackend {
        override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
            override val events: Flow<QueryEvent> = emptyFlow()
            override suspend fun close() {}
        }
        override suspend fun cancel(sessionId: String, requestId: String) {}
    }
    when (val outcome = TdsServer.start(config, BackendSource.Custom(backend))) {
        is StartOutcome.Running -> {
            println("PROBE-OK port=" + outcome.server.port)
            outcome.server.stop()
        }
        is StartOutcome.Aborted -> error("Start abgebrochen: " + outcome.reason)
    }
}
KOTLIN
lauf() { # <name> <gradle-argumente...>
  name="$1"; shift
  ist=0
  gradle --no-daemon --offline -q "$@" run > /tmp/lauf.log 2>&1 || ist=$?
  if [ "$ist" -eq 0 ] && grep -q '^PROBE-OK' /tmp/lauf.log; then
    echo "== Scope $scope, $name: Exit 0, $(grep '^PROBE-OK' /tmp/lauf.log)"
  else
    echo "== Scope $scope, $name: Exit $ist"
    grep -E '^e: |Exception|Error|error:|NoClassDef' /tmp/lauf.log | head -4 | cut -c1-240
  fi
}
lauf "Gradle-Metadaten"
lauf "nur POM" -PnurPom
lauf "nur POM, Bibliothek ohne transitive Abhaengigkeiten, coroutines nur zur Uebersetzung (Gegenfall: der Class-Path ersetzt sie nicht)" -PnurPom -PnichtTransitiv
(cd /src && gradle --no-daemon --offline -q laufzeitBibliotheken)
cp -r /src/build/libs/lib "$repo/lib"
lauf "wie zuvor, aber lib/ neben dem Jar im lokalen Repository (Gegenfall: der Class-Path greift, wenn lib/ liegt)" -PnurPom -PnichtTransitiv
rm -rf "$repo/lib"
CONTAINER
  for scope in api implementation; do
    docker run --rm -i tdswire-publish-probe:lokal sh -s "$scope" < "$tmp/konsument.sh"
  done
}

token() {
  hex=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
  export REG_USER="probe-aktor" REG_TOKEN="PLATZHALTER-$hex"
  # Der Server im Container prueft den Benutzer und den Platzhalter; beide Namen gehen nur als Umgebung an ihn.
  cat > "$tmp/Registry.java" <<'JAVA'
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Registry {
    public static void main(String[] args) throws Exception {
        String erwartet = "Basic " + Base64.getEncoder().encodeToString(
            (System.getenv("REG_USER") + ":" + System.getenv("REG_TOKEN")).getBytes(StandardCharsets.UTF_8));
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", ex -> {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            int laenge = ex.getRequestBody().readAllBytes().length;
            int status;
            String auth_text;
            if (auth == null) { auth_text = "fehlt"; status = 401; }
            else if (!auth.equals(erwartet)) { auth_text = "falsch"; status = 401; }
            else { auth_text = "ok"; status = ex.getRequestMethod().equals("PUT") ? 201 : 404; }
            if (status == 401) ex.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"dummy\"");
            System.out.println("REQ " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                + " auth=" + auth_text + " bytes=" + laenge + " -> " + status);
            System.out.flush();
            ex.sendResponseHeaders(status, -1);
            ex.close();
        });
        server.start();
        System.out.println("BEREIT");
        System.out.flush();
    }
}
JAVA
  # Wegwerf-Repo: der Arbeitsbaum dieses Repos (versioniert und unversioniert, ohne Ignoriertes) mit Version 0.0.1 und Tag.
  klon="$tmp/klon"
  mkdir -p "$klon"
  (cd "$wurzel" && git ls-files -co --exclude-standard -z | tar --null -T - -cf -) | tar -xf - -C "$klon"
  git -C "$klon" init -q
  git -C "$klon" config user.name probe
  git -C "$klon" config user.email probe@invalid
  git -C "$klon" config commit.gpgsign false
  git -C "$klon" config tag.gpgsign false
  sed -i 's/^version = ".*"$/version = "0.0.1"/' "$klon/build.gradle.kts"
  git -C "$klon" add -A
  git -C "$klon" commit -q -m probe
  git -C "$klon" tag v0.0.1

  netz="tdswire-probe-$$"
  docker network create --internal "$netz" > /dev/null
  dummy="tdswire-probe-registry-$$"
  docker run -d --name "$dummy" --network "$netz" --network-alias registry -e REG_USER -e REG_TOKEN \
    -v "$tmp/Registry.java:/Registry.java:ro" "$(basis_image)" java /Registry.java > /dev/null
  bereit=0
  for _ in $(seq 1 60); do
    if docker logs "$dummy" 2>&1 | grep -q '^BEREIT'; then bereit=1; break; fi
    sleep 1
  done
  [ "$bereit" -eq 1 ] || { docker logs "$dummy" >&2; echo "publish-probe: Dummy-Server nicht bereit" >&2; exit 1; }

  echo "== Lauf 1: leeres Token bricht vor dem Netz"
  ist=0
  GITHUB_ACTOR="$REG_USER" GITHUB_TOKEN= PUBLISH_WURZEL="$klon" PUBLISH_DOCKER_NETZ="$netz" \
    bash "$klon/tools/publish.sh" --url http://registry:8080/maven > "$tmp/leer.log" 2>&1 || ist=$?
  echo "Exit $ist; Meldung: $(tr '\n' ' ' < "$tmp/leer.log")"
  echo "Anfragen am Dummy-Server nach Lauf 1: $(docker logs "$dummy" 2>&1 | grc_req)"

  echo "== Lauf 2: Platzhalter-Token gegen den Dummy-Server (Docker-Netz ohne Zugang nach aussen)"
  ist=0
  GITHUB_ACTOR="$REG_USER" GITHUB_TOKEN="$REG_TOKEN" PUBLISH_WURZEL="$klon" PUBLISH_DOCKER_NETZ="$netz" \
    bash "$klon/tools/publish.sh" --url http://registry:8080/maven > "$tmp/publish.log" 2>&1 || ist=$?
  echo "Exit $ist; Zeilen des Logs: $(wc -l < "$tmp/publish.log"); letzte: $(tail -3 "$tmp/publish.log" | tr '\n' '|')"
  echo "-- Ausgabe des Gradle-Laufs (ab der Zeile des Ziels; ohne die Fortschrittszeilen von docker build):"
  sed -n '/^publish: Version/,$p' "$tmp/publish.log" | grep -v '^#[0-9]' | tail -40
  docker logs "$dummy" 2>&1 | grep '^REQ' | sed 's/ bytes=[0-9]*//' | sort | uniq -c | sort -k3 | head -40

  echo "== Suche nach dem Platzhalter (Klartext, Base64 des Tokens, Base64 von Benutzer:Token)"
  b64_token=$(printf '%s' "$REG_TOKEN" | base64 -w0)
  b64_paar=$(printf '%s:%s' "$REG_USER" "$REG_TOKEN" | base64 -w0)
  docker history --no-trunc tdswire-publish:lokal > "$tmp/history.txt"
  docker image inspect tdswire-publish:lokal > "$tmp/inspect.json"
  for form in "$REG_TOKEN" "$b64_token" "$b64_paar"; do
    for datei in publish.log history.txt inspect.json leer.log; do
      echo "Treffer in $datei: $(grep -c -F -- "$form" "$tmp/$datei" || true)  (Form $(printf '%s' "$form" | cut -c1-8)...)"
    done
  done
  docker inspect "$dummy" > "$tmp/dummy-inspect.json"
  echo "Positivprobe (die Suche findet den Platzhalter, wo er steht: Umgebung des Dummy-Containers im docker inspect): $(grep -c -F -- "$REG_TOKEN" "$tmp/dummy-inspect.json" || true) Treffer"
  echo "docker history: $(wc -l < "$tmp/history.txt") Zeilen"
  docker rmi tdswire-publish:lokal > /dev/null 2>&1 || true
}

# Anfragen zaehlen (Hilfe von Lauf 1).
grc_req() { grep -c '^REQ' || true; }

case "${1:-}" in
  konsument) konsument ;;
  token) token ;;
  *) echo "Aufruf: $0 konsument|token" >&2; exit 2 ;;
esac
