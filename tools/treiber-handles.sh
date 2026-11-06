#!/usr/bin/env bash
# tools/treiber-handles.sh - Beleg am Treiber: Prepared-Handles von mssql-jdbc 13.4.0 gegen den gebauten Testserver
# (TDS-FA-04, TDS-QA-02, ADR-0036; Aufruf ueber `make messung-treiber-handles`). KEIN Gate: der Lauf baut das Image des
# Testservers, startet einen Container und einen Container mit dem Treiber, und braucht dafuer Docker (mit Netz fuer den
# Pull des Treiber-Images, falls es lokal fehlt); er steht nicht in `make gates` (ADR-0022).
#
# Ablauf: `tools/testserver.sh up` (Image aus dem Arbeitsbaum, Voreinstellungen, Beispieltabelle `bestellungen` des
# Testservers, Port TREIBER_PORT, Voreinstellung 14339) -> Messung.java uebersetzen mit dem JDK des Gradle-Images aus dem
# Dockerfile (das Image von d-migrate bringt nur eine JRE) -> Lauf im Image von d-migrate 1.12.0 (Treiber und JRE 21
# daraus, Digest unten; d-migrate selbst laeuft nicht) -> `tools/testserver.sh down`. Die Messung ist
# tools/treiber-handles/Messung.java; sie zaehlt die Nachrichten des Clients an einem TCP-Durchlauf zwischen Treiber und
# Server und endet mit Exit 1, wenn eine Erwartung verfehlt wird:
# - m1: ein PreparedStatement, 200 Ausfuehrungen: 200 gelingen, 1 sp_prepexec und 198 sp_execute.
# - m2: 1100 neue PreparedStatements mit enablePrepareOnFirstPreparedStatementCall=true: alle 1100 gelingen (kein Abbruch
#   an der Grenze von 1000 Handles), und der Treiber sendet mindestens einen Batch mit sp_unprepare.
# Grenzen: gemessen ist der Treiber allein gegen den Server dieses Repos, nicht d-migrate und nicht gegen einen echten
# SQL Server; eine Verbindung je Reihe, TDS 7.4 im Klartext, ein Host.
# Eingaenge (Umgebung): TREIBER_PORT, BUILDER_FLAGS (wie `make testserver-up`), TESTSERVER_BAUEN (0 = Image nicht bauen).
set -u

NAME="tdswire-treiber-handles"
PORT="${TREIBER_PORT:-14339}"
TREIBER_IMAGE="ghcr.io/pt9912/d-migrate@sha256:af9d3eb323a6cfd13eb63f012823788e510f764509918933d2ff98c8e68e4212"
TREIBER_JAR="/opt/d-migrate/lib/mssql-jdbc-13.4.0.jre11.jar"
JDK_IMAGE="gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06"

cd "$(dirname "$0")/.." || exit 1

klassen="$(mktemp -d)"
chmod 777 "$klassen"
trap 'rm -rf "$klassen"; TESTSERVER_NAME="$NAME" bash tools/testserver.sh down' EXIT

docker run --rm -v "$PWD/tools/treiber-handles:/messung:ro" -v "$klassen:/klassen" --entrypoint javac "$JDK_IMAGE" \
  --release 21 -d /klassen /messung/Messung.java || exit 1
TESTSERVER_NAME="$NAME" TESTSERVER_PORT="$PORT" bash tools/testserver.sh up || exit 1

docker run --rm --network host -v "$klassen:/klassen:ro" --entrypoint java "$TREIBER_IMAGE" \
  -cp "/klassen:$TREIBER_JAR" Messung "$PORT"
status=$?
echo "treiber-handles: Treiber mssql-jdbc 13.4.0 aus $TREIBER_IMAGE, Exit $status"
exit "$status"
