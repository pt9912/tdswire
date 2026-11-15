#!/bin/bash
# publish-probe.sh — Erzeuger der Messungen zum Maven-Paket (TDS-RB-01, TDS-RB-05, TDS-QA-06, ADR-0047); kein Gate, kein
# Make-Target, schreibt nirgends hin ausser in Containern und ein Wegwerf-Verzeichnis. Das Messprotokoll
# docs/messungen/maven-paket-2026-11-14.md nennt die Laeufe von `konsument` und `token`; `ziele`, `registry` und `url`
# (ADR-0049) sind die Erzeuger der Messzahlen fuer das zweite Ziel.
#
# Aufruf: publish-probe.sh konsument   Messungen (a) und (b): ein Probe-Konsument bindet die Bibliothek nur ueber
#                                      publishToMavenLocal (mit gradleMetadata() und ohne es in den Quellen; die `.module` liest Gradle auch
#                                      dann, siehe `marker`), einmal mit dem Scope
#                                      `api` und einmal mit `implementation` fuer kotlinx-coroutines-core; Gegenfall ohne
#                                      transitive Abhaengigkeiten, mit und ohne lib/ neben dem Jar (der Class-Path greift nur, wenn
#                                      lib/ liegt).
#         publish-probe.sh token       Messungen (c) und (d): tools/publish.sh in einem Wegwerf-Repo (Version 0.0.1, Tag
#                                      v0.0.1) gegen einen Dummy-Server in einem Docker-Netz ohne Zugang nach aussen, mit einem
#                                      Platzhalter-Token; das Ziel ist nicht GitHub.
#         publish-probe.sh marker      ob `mavenLocal` die `.module` bei Quellen ohne gradleMetadata() liest (Muell statt `.module`; Auswertung am Exit).
#         publish-probe.sh ziele       beide Ziele (github, cloudsmith) ueber tools/publish.sh --ziel gegen den Dummy-Server: je Ziel ein
#                                      Lauf mit leerem Schluessel (bricht vor dem Netz) und einer mit Platzhalter; gelesen werden der Name der
#                                      Gradle-Task im Lauf, die am Dummy angekommenen Dateien (Jar, POM, `.module`, Pruefsummen,
#                                      Metadaten) und die Suche nach dem Platzhalter.
#         publish-probe.sh registry <github|cloudsmith> <anonym|zugang>
#                                      der Bau-Lauf des Ziels schreibt Version 0.0.1 an den Dummy, der sie ausliefert; ein Konsument
#                                      (Kotlin, Gradle) loest sie aus der Registry-URL auf, anonym oder mit Zugangsdaten aus der
#                                      Umgebung, mit `.module` und ohne (je ein frischer Container), uebersetzt und startet den Server.
#         publish-probe.sh url <registry-url> <version> [anonym|zugang]
#                                      derselbe Konsument gegen eine echte Registry-URL, nur lesend (Docker-Standardnetz); bei `zugang`
#                                      liest er REG_USER und REG_TOKEN der Umgebung. Ein 404 trennt "leer" nicht von "nicht oeffentlich".
# Voraussetzung: Docker, bash, git, tar. Es wird nichts nach GitHub geschrieben, kein Tag im Repo gesetzt, kein echtes Token
# gelesen: das Token ist ein Platzhalter, den das Skript selbst erzeugt, und GITHUB_TOKEN der Umgebung wird nicht gelesen.
# Grenzen: Der Dummy (`ziele`, `registry`) ersetzt weder das Sammelfenster von Cloudsmith noch die asynchrone Verarbeitung noch die
# Annahme der `.module` bei Cloudsmith; belegt ist der Ablauf von Gradle und Skript, nicht Cloudsmith. Im Modus `registry` ist `.module`
# nur durch ignoreGradleMetadataRedirection() abgeschaltet: `registry` (Art `pom-ohne-schutz`, Anfragen am Dummy-Server) und `marker` (mavenLocal)
# zeigen, ob Gradle die `.module` ohne diese Zeile auch ohne gradleMetadata() liest; die Ergebnisse stehen in der Ausgabe der Laeufe
# (Einzellaeufe), nicht in diesem Kommentar. Der Grund (Marker in der POM) ist nicht gemessen.
# Die Messung (c) und (d) sucht den Platzhalter im Klartext, als Base64 des Tokens und als Base64 von
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

# Der Konsument, den beide Modi uebersetzen und starten: startet den Server der Bibliothek ueber ihre oeffentliche Flaeche.
probe_kt() {
  cat <<'KOTLIN'
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
}

# Liest der Konsument aus `mavenLocal` die `.module`, wenn die Quellen sie nicht nennen? Die `.module` der veroeffentlichten Version wird
# durch Muell ersetzt: liest Gradle sie, bricht die Aufloesung; liest es sie nicht, bleibt der Lauf gruen. Gemessen wird die
# Konfiguration von `konsument` (ohne gradleMetadata()) und dieselbe mit ignoreGradleMetadataRedirection(). Liest Gradle die `.module`,
# nennt die Ausgabe des ersten Laufs `Could not parse module metadata` mit dem Pfad der `.module` (gemessen 2026-11-15, Einzellauf).
marker() {
  # shellcheck disable=SC2086
  docker build ${BUILDER_FLAGS:-} --target publish -t tdswire-publish-probe:lokal --progress=plain "$wurzel" > "$tmp/bau.log" 2>&1 \
    || { tail -20 "$tmp/bau.log" >&2; exit 1; }
  probe_kt > "$tmp/Probe.kt"
  cat > "$tmp/marker.sh" <<'CONTAINER'
set -eu
cd /src
gradle --no-daemon --offline -q publishToMavenLocal
repo=$HOME/.m2/repository/io/github/pt9912/tdswire/0.1.0-SNAPSHOT
echo "== Dateien der Version: $(ls "$repo" | tr '\n' ' ')"
echo 'KEIN-GRADLE-MODULE' > "$repo/tdswire-0.1.0-SNAPSHOT.module"
mkdir -p /probe/src/main/kotlin
cd /probe
cp /Probe.kt src/main/kotlin/Probe.kt
echo 'rootProject.name = "probe"' > settings.gradle.kts
cat > build.gradle.kts <<'KTS'
plugins {
    kotlin("jvm") version "2.4.10"
    application
}
repositories {
    mavenLocal {
        metadataSources {
            if (providers.gradleProperty("ohneUmleitung").isPresent) ignoreGradleMetadataRedirection()
            mavenPom()
            artifact()
        }
    }
    mavenCentral()
}
dependencies {
    implementation("io.github.pt9912:tdswire:0.1.0-SNAPSHOT")
}
application { mainClass.set("ProbeKt") }
KTS
lauf() { # <name> <gradle-argumente...>
  name="$1"; shift
  ist=0
  gradle --no-daemon --offline -q "$@" run > /tmp/lauf.log 2>&1 || ist=$?
  if [ "$ist" -eq 0 ] && grep -q '^PROBE-OK' /tmp/lauf.log; then echo "== $name: Exit 0, $(grep '^PROBE-OK' /tmp/lauf.log)"
  else echo "== $name: Exit $ist"; grep -E 'Could not parse module metadata' /tmp/lauf.log | head -1 | sed 's#/root/.m2/[^ ]*/##' | cut -c1-240; fi
}
lauf "mavenLocal ohne gradleMetadata(), .module durch Muell ersetzt"
lauf "wie zuvor, mit ignoreGradleMetadataRedirection()" -PohneUmleitung
CONTAINER
  docker run --rm -i -v "$tmp/Probe.kt:/Probe.kt:ro" tdswire-publish-probe:lokal sh -s < "$tmp/marker.sh"
}

konsument() {
  probe_kt > "$tmp/Probe.kt"
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
cp /Probe.kt src/main/kotlin/Probe.kt
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
lauf "nur POM (die .module wird trotzdem angefragt, wenn die Umleitung nicht abgeschaltet ist)" -PnurPom
lauf "nur POM, Bibliothek ohne transitive Abhaengigkeiten, coroutines nur zur Uebersetzung (Gegenfall: der Class-Path ersetzt sie nicht; die .module wird trotzdem angefragt, wenn die Umleitung nicht abgeschaltet ist)" -PnurPom -PnichtTransitiv
(cd /src && gradle --no-daemon --offline -q laufzeitBibliotheken)
cp -r /src/build/libs/lib "$repo/lib"
lauf "wie zuvor, aber lib/ neben dem Jar im lokalen Repository (Gegenfall: der Class-Path greift, wenn lib/ liegt)" -PnurPom -PnichtTransitiv
rm -rf "$repo/lib"
CONTAINER
  for scope in api implementation; do
    docker run --rm -i -v "$tmp/Probe.kt:/Probe.kt:ro" tdswire-publish-probe:lokal sh -s "$scope" < "$tmp/konsument.sh"
  done
}

# Der Dummy-Server (Java, ein Prozess): PUT mit Basic-Anmeldung speichert die Datei im Speicher; GET und HEAD liefern eine
# gespeicherte Datei aus (200, sonst 404), anonym bei REG_LESEN=anonym, sonst nur mit der Anmeldung (REG_USER, REG_TOKEN).
# Grenze: er bildet weder das Sammelfenster noch die asynchrone Verarbeitung von Cloudsmith nach und prueft nicht,
# ob Cloudsmith die `.module` annimmt.
registry_java() {
  cat <<'JAVA'
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Registry {
    public static void main(String[] args) throws Exception {
        String erwartet = "Basic " + Base64.getEncoder().encodeToString(
            (System.getenv("REG_USER") + ":" + System.getenv("REG_TOKEN")).getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> dateien = new ConcurrentHashMap<>();
        boolean anonym = "anonym".equals(System.getenv("REG_LESEN"));
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", ex -> {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            byte[] inhalt = ex.getRequestBody().readAllBytes();
            String methode = ex.getRequestMethod();
            String pfad = ex.getRequestURI().getPath();
            boolean richtig = auth != null && auth.equals(erwartet);
            String auth_text = auth == null ? "fehlt" : (richtig ? "ok" : "falsch");
            int status;
            byte[] antwort = new byte[0];
            if (methode.equals("PUT")) {
                if (richtig) { dateien.put(pfad, inhalt); status = 201; } else { status = 401; }
            } else if (methode.equals("GET") || methode.equals("HEAD")) {
                if (!richtig && !anonym) { status = 401; }
                else if (dateien.containsKey(pfad)) { antwort = dateien.get(pfad); status = 200; }
                else { status = 404; }
            } else {
                status = richtig ? 404 : 401;
            }
            if (status == 401) ex.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"dummy\"");
            System.out.println("REQ " + methode + " " + pfad + " auth=" + auth_text + " bytes=" + inhalt.length
                + " -> " + status);
            System.out.flush();
            if (methode.equals("HEAD") || antwort.length == 0) {
                ex.sendResponseHeaders(status, -1);
            } else {
                ex.sendResponseHeaders(status, antwort.length);
                ex.getResponseBody().write(antwort);
            }
            ex.close();
        });
        server.start();
        System.out.println("BEREIT");
        System.out.flush();
    }
}
JAVA
}

# Platzhalter, Wegwerf-Repo, Docker-Netz ohne Zugang nach aussen und Dummy-Server (Alias `registry`).
vorbereiten() {
  hex=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
  export REG_USER="probe-aktor" REG_TOKEN="PLATZHALTER-$hex"
  # Der Server im Container prueft den Benutzer und den Platzhalter; beide Namen gehen nur als Umgebung an ihn.
  registry_java > "$tmp/Registry.java"
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
  docker run -d --name "$dummy" --network "$netz" --network-alias registry -e REG_USER -e REG_TOKEN -e REG_LESEN \
    -v "$tmp/Registry.java:/Registry.java:ro" "$(basis_image)" java /Registry.java > /dev/null
  bereit=0
  for _ in $(seq 1 60); do
    if docker logs "$dummy" 2>&1 | grep -q '^BEREIT'; then bereit=1; break; fi
    sleep 1
  done
  [ "$bereit" -eq 1 ] || { docker logs "$dummy" >&2; echo "publish-probe: Dummy-Server nicht bereit" >&2; exit 1; }

}

token() {
  vorbereiten

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

# Gemeinsame Zeilen: Anfragen am Dummy-Server nach Pfad-Anfang, ohne Groessen, gezaehlt.
anfragen() { docker logs "$dummy" 2>&1 | grep '^REQ' | grep -- "$1" | sed 's/ bytes=[0-9]*//' | sort | uniq -c; }

# Beide Ziele gegen den Dummy (ADR-0049): je Ziel ein Lauf mit leerem Schluessel (bricht vor dem Netz) und ein Lauf mit
# Platzhalter ueber tools/publish.sh --ziel; gelesen werden der Name der Gradle-Task im Lauf, die am Dummy angekommenen
# Dateien und die Suche nach dem Platzhalter. Die Umgebung des anderen Ziels ist in beiden Laeufen nicht gesetzt.
ziele() {
  vorbereiten
  for ziel in github cloudsmith; do
    case "$ziel" in
      github) bvar=GITHUB_ACTOR; gvar=GITHUB_TOKEN ;;
      cloudsmith) bvar=CLOUDSMITH_USERNAME; gvar=CLOUDSMITH_API_KEY ;;
    esac
    url="http://registry:8080/$ziel/maven"
    echo "== Ziel $ziel, Lauf 1: leerer Schluessel ($gvar) bricht vor dem Netz"
    ist=0
    env -u GITHUB_ACTOR -u GITHUB_TOKEN -u CLOUDSMITH_USERNAME -u CLOUDSMITH_API_KEY "$bvar=$REG_USER" "$gvar=" PUBLISH_WURZEL="$klon" PUBLISH_DOCKER_NETZ="$netz" \
      bash "$klon/tools/publish.sh" --ziel "$ziel" --url "$url" > "$tmp/leer-$ziel.log" 2>&1 || ist=$?
    echo "Exit $ist; Meldung: $(tr '\n' ' ' < "$tmp/leer-$ziel.log")"
    echo "== Ziel $ziel, Lauf 2: Platzhalter gegen den Dummy-Server (Docker-Netz ohne Zugang nach aussen), Umgebung des anderen Ziels nicht gesetzt"
    ist=0
    env -u GITHUB_ACTOR -u GITHUB_TOKEN -u CLOUDSMITH_USERNAME -u CLOUDSMITH_API_KEY "$bvar=$REG_USER" "$gvar=$REG_TOKEN" PUBLISH_WURZEL="$klon" PUBLISH_DOCKER_NETZ="$netz" \
      bash "$klon/tools/publish.sh" --ziel "$ziel" --url "$url" > "$tmp/publish-$ziel.log" 2>&1 || ist=$?
    echo "Exit $ist"
    echo "-- Tasks des Gradle-Laufs, die ein Repository ansprechen (Name im Lauf gelesen):"
    grep -E '^> Task :publish' "$tmp/publish-$ziel.log" | sort | uniq -c
    echo "-- am Dummy angekommene Dateien (PUT mit 201, Pfad und Groesse):"
    docker logs "$dummy" 2>&1 | grep -E "^REQ PUT /$ziel/.* -> 201$" | sed 's/^REQ PUT \([^ ]*\) auth=ok bytes=\([0-9]*\) -> 201$/\1 \2 Bytes/' | sort
    echo "-- Anfragen ausser PUT am Dummy (die PUT-Anfragen sind die Dateien oben, je eine mit 201):"
    anfragen "/$ziel/" | grep -v ' PUT ' | sed 's#/[^ ]*/#/.../#' || true
  done

  echo "== Suche nach dem Platzhalter (Klartext, Base64 des Schluessels, Base64 von Benutzer:Schluessel)"
  b64_token=$(printf '%s' "$REG_TOKEN" | base64 -w0)
  b64_paar=$(printf '%s:%s' "$REG_USER" "$REG_TOKEN" | base64 -w0)
  docker history --no-trunc tdswire-publish:lokal > "$tmp/history.txt"
  docker image inspect tdswire-publish:lokal > "$tmp/inspect.json"
  for form in "$REG_TOKEN" "$b64_token" "$b64_paar"; do
    for datei in publish-github.log publish-cloudsmith.log leer-github.log leer-cloudsmith.log history.txt inspect.json; do
      echo "Treffer in $datei: $(grep -c -F -- "$form" "$tmp/$datei" || true)  (Form $(printf '%s' "$form" | cut -c1-8)...)"
    done
  done
  docker inspect "$dummy" > "$tmp/dummy-inspect.json"
  echo "Positivprobe (die Suche findet den Platzhalter, wo er steht: Umgebung des Dummy-Containers im docker inspect): $(grep -c -F -- "$REG_TOKEN" "$tmp/dummy-inspect.json" || true) Treffer"
  docker rmi tdswire-publish:lokal > /dev/null 2>&1 || true
}

# Das Skript, das im Container laeuft: ein Gradle-Projekt mit dem Konsumenten (Probe.kt), das io.github.pt9912:tdswire:$REG_VERSION
# aus $REG_URL aufloest, anonym oder (REG_LESEN=zugang) mit REG_USER und REG_TOKEN; Argument `pom`: ohne .module, sonst mit.
konsument_registry_skript() {
  cat <<'CONTAINER'
set -eu
mkdir -p /probe/src/main/kotlin
cd /probe
cp /Probe.kt src/main/kotlin/Probe.kt
echo 'rootProject.name = "probe"' > settings.gradle.kts
cat > build.gradle.kts <<'KTS'
plugins {
    kotlin("jvm") version "2.4.10"
    application
}
repositories {
    maven {
        url = uri(System.getenv("REG_URL"))
        isAllowInsecureProtocol = true
        if (System.getenv("REG_LESEN") == "zugang") {
            credentials {
                username = System.getenv("REG_USER")
                password = System.getenv("REG_TOKEN")
            }
        }
        // Ohne .module: ignoreGradleMetadataRedirection() haelt Gradle von der .module ab; gradleMetadata() wegzulassen reicht nicht
        // (Art `pom-ohne-schutz`: nur POM und Artefakt in den Quellen, ohne diese Zeile).
        metadataSources {
            if (!providers.gradleProperty("nurPom").isPresent) gradleMetadata()
            else if (!providers.gradleProperty("ohneSchutz").isPresent) ignoreGradleMetadataRedirection()
            mavenPom()
            artifact()
        }
        content { includeGroup("io.github.pt9912") }
    }
    mavenCentral()
}
dependencies {
    implementation("io.github.pt9912:tdswire:" + System.getenv("REG_VERSION"))
}
application { mainClass.set("ProbeKt") }
KTS
lauf() { # <name> <gradle-argumente...>
  name="$1"; shift
  ist=0
  gradle --no-daemon -q "$@" run > /tmp/lauf.log 2>&1 || ist=$?
  if [ "$ist" -eq 0 ] && grep -q '^PROBE-OK' /tmp/lauf.log; then
    echo "== $name: Exit 0, $(grep '^PROBE-OK' /tmp/lauf.log)"
  else
    echo "== $name: Exit $ist"
    grep -E '^e: |Exception|Error|error:|NoClassDef|Could not|> ' /tmp/lauf.log | head -8 | cut -c1-240
  fi
}
case "$1" in
  pom) lauf "nur POM, ohne Umleitung auf die .module" -PnurPom ;;
  pom-ohne-schutz) lauf "nur POM und Artefakt in den Quellen, ohne ignoreGradleMetadataRedirection()" -PnurPom -PohneSchutz ;;
  *) lauf "mit Gradle-Metadaten (.module)" ;;
esac
CONTAINER
}

# Probe-Konsument gegen eine Registry-URL (ADR-0049 Entscheidung 5): der Bau-Lauf von tools/publish.sh --ziel <ziel> schreibt
# die Version 0.0.1 an den Dummy; der Dummy liefert sie danach aus, anonym (lesen=anonym) oder nur mit den Zugangsdaten aus der
# Umgebung (lesen=zugang). Der Konsument (Kotlin, Gradle) loest io.github.pt9912:tdswire:0.0.1 aus der URL auf, mit den
# Gradle-Metadaten (`.module`) und nur ueber die POM, uebersetzt und startet den Server der Bibliothek.
registry() {
  ziel="${1:-}"; lesen="${2:-}"
  case "$ziel" in
    github) bvar=GITHUB_ACTOR; gvar=GITHUB_TOKEN ;;
    cloudsmith) bvar=CLOUDSMITH_USERNAME; gvar=CLOUDSMITH_API_KEY ;;
    *) echo "Aufruf: $0 registry github|cloudsmith anonym|zugang" >&2; exit 2 ;;
  esac
  case "$lesen" in anonym | zugang) ;; *) echo "Aufruf: $0 registry github|cloudsmith anonym|zugang" >&2; exit 2 ;; esac
  export REG_LESEN="$lesen"
  vorbereiten
  probe_kt > "$tmp/Probe.kt"
  export REG_URL="http://registry:8080/$ziel/maven" REG_VERSION=0.0.1
  echo "== Bau-Lauf: tools/publish.sh --ziel $ziel gegen den Dummy-Server (lesen=$lesen)"
  ist=0
  env -u GITHUB_ACTOR -u GITHUB_TOKEN -u CLOUDSMITH_USERNAME -u CLOUDSMITH_API_KEY "$bvar=$REG_USER" "$gvar=$REG_TOKEN" PUBLISH_WURZEL="$klon" PUBLISH_DOCKER_NETZ="$netz" \
    bash "$klon/tools/publish.sh" --ziel "$ziel" --url "$REG_URL" > "$tmp/publish.log" 2>&1 || ist=$?
  echo "Exit $ist"
  [ "$ist" -eq 0 ] || { tail -30 "$tmp/publish.log" >&2; exit 1; }
  echo "-- angekommene Dateien:"
  docker logs "$dummy" 2>&1 | grep -E "^REQ PUT /$ziel/.* -> 201$" | sed 's/^REQ PUT \([^ ]*\) auth=ok bytes=\([0-9]*\) -> 201$/\1 \2 Bytes/' | sort

  konsument_registry_skript > "$tmp/konsument-registry.sh"
  echo "== Konsument gegen $REG_URL (lesen=$lesen); je Lauf ein frischer Container (Gradle-Cache des Images)"
  for art in metadaten pom pom-ohne-schutz; do
    vorher=$(docker logs "$dummy" 2>&1 | grep -c -E '^REQ (GET|HEAD) ' || true)
    ist=0
    docker run --rm -i --network "$netz" -e REG_URL -e REG_VERSION -e REG_LESEN -e REG_USER -e REG_TOKEN -v "$tmp/Probe.kt:/Probe.kt:ro" \
      tdswire-publish:lokal sh -s "$art" < "$tmp/konsument-registry.sh" || ist=$?
    echo "Exit des Konsumenten-Containers ($art): $ist"
    echo "-- Lese-Anfragen dieses Laufs am Dummy (GET und HEAD, Authentifizierung und Ergebnis):"
    docker logs "$dummy" 2>&1 | grep -E '^REQ (GET|HEAD) ' | tail -n +$((vorher + 1)) | sed 's/ bytes=[0-9]*//' \
      | sed 's#/io/github/pt9912/tdswire#/.../tdswire#' | sort | uniq -c
  done
  docker rmi tdswire-publish:lokal > /dev/null 2>&1 || true
}

# Probe-Konsument gegen eine echte Registry-URL, nur lesend (ADR-0049 Entscheidung 5): derselbe Konsument wie in `registry`,
# aber im Netz des Docker-Standards statt im Netz ohne Zugang nach aussen; es wird nichts geschrieben, kein Zugang gelesen
# ausser REG_USER und REG_TOKEN der Umgebung bei `zugang`. Ein 404 der Registry (leer oder nicht oeffentlich) steht im
# Ergebnis als Exit des Gradle-Laufs; er trennt "leer" nicht von "nicht oeffentlich".
url() {
  ziel_url="${1:-}"; version="${2:-}"; lesen="${3:-anonym}"
  case "$ziel_url" in http://?* | https://?*) ;; *) echo "Aufruf: $0 url <registry-url> <version> [anonym|zugang]" >&2; exit 2 ;; esac
  [ -n "$version" ] || { echo "Aufruf: $0 url <registry-url> <version> [anonym|zugang]" >&2; exit 2; }
  case "$lesen" in anonym | zugang) ;; *) echo "Aufruf: $0 url <registry-url> <version> [anonym|zugang]" >&2; exit 2 ;; esac
  export REG_URL="$ziel_url" REG_VERSION="$version" REG_LESEN="$lesen"
  # shellcheck disable=SC2086
  docker build ${BUILDER_FLAGS:-} --target publish -t tdswire-publish:lokal --progress=plain "$wurzel" > "$tmp/bau.log" 2>&1 \
    || { tail -20 "$tmp/bau.log" >&2; exit 1; }
  probe_kt > "$tmp/Probe.kt"
  konsument_registry_skript > "$tmp/konsument-registry.sh"
  for art in metadaten pom; do
    ist=0
    docker run --rm -i -e REG_URL -e REG_VERSION -e REG_LESEN -e REG_USER -e REG_TOKEN -v "$tmp/Probe.kt:/Probe.kt:ro" \
      tdswire-publish:lokal sh -s "$art" < "$tmp/konsument-registry.sh" || ist=$?
    echo "Exit des Konsumenten-Containers ($art, $REG_URL, Version $REG_VERSION, lesen=$lesen): $ist"
  done
  docker rmi tdswire-publish:lokal > /dev/null 2>&1 || true
}

# Anfragen zaehlen (Hilfe von Lauf 1).
grc_req() { grep -c '^REQ' || true; }

case "${1:-}" in
  konsument) konsument ;;
  marker) marker ;;
  token) token ;;
  ziele) ziele ;;
  registry) shift; registry "$@" ;;
  url) shift; url "$@" ;;
  *) echo "Aufruf: $0 konsument|token|marker|ziele|registry github|cloudsmith anonym|zugang|url <registry-url> <version> [anonym|zugang]" >&2; exit 2 ;;
esac
