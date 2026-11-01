#!/usr/bin/env bash
# tools/e2e.sh - Produkt-E2E: die Laeufe, die den Serverprozess ueber seine Aussengrenze pruefen, als Stufen
# nacheinander (TDS-QA-01, TDS-QA-06, ADR-0022). Aufruf ueber `make e2e`; kein Gate, nicht in GATE_CHECKS.
#
# Jede Stufe ruft ein `make`-Target (ein Docker-Lauf gleichzeitig) und traegt eine Deklaration
#   e2e_abdeckung "<Kennungen>" "<Kurzbeschreibung>" "<Anker>"
# aus der `make e2e-abdeckung` die Sicht docs/plan/e2e-abdeckung.md erzeugt (Marker in repo.mk). Die Sicht ist eine
# Deklaration, kein Lauf-Beleg: dass eine Stufe ihre Kennungen traegt, belegt ihr gruener Lauf, nicht die Datei.
# Setzung: die erste rote Stufe bricht den Durchgang ab (Exit 1), und die Ausgabe nennt die Stufe; die folgenden
# Stufen laufen nicht (gehalten von `make skript-selbsttest`). Die Harness-Selbstpruefung (tools/harness/selbstpruefung.sh) ist keine Stufe.
set -u

# Aufruf der Stufen; tools/e2e-selbsttest.sh setzt E2E_MAKE auf einen Ersatz und prueft damit Abbruch und Ausgabe.
make_cmd="${E2E_MAKE:-make}"

e2e_abdeckung() { echo "abdeckung: $1 — $2 (Anker: $3)"; }

lauf() {
	local nr="$1" t0="$SECONDS"
	shift
	local anzeige="$*"
	if [ "$1" = make ]; then
		shift
		set -- "$make_cmd" "$@"
	fi
	if ! "$@"; then
		echo "e2e: ROT - Stufe $nr ($anzeige) nach $((SECONDS - t0)) s; der Durchgang bricht ab, folgende Stufen laufen nicht." >&2
		exit 1
	fi
	echo "e2e: Stufe $nr gruen ($anzeige, $((SECONDS - t0)) s)"
}

echo "e2e: Stufe 1 Rohbytes-Login und Startverhalten des Prozesses ..."
e2e_abdeckung "TDS-FA-01 TDS-FA-02 TDS-FA-09" "Kindprozess main mit Rohbytes-Client: PRELOGIN und LOGIN7 mit LOGINACK, Start ohne JVM-Flag bricht ab, fehlende Pflichtoption, ungültiger LOGIN_TIMEOUT und belegter Port beenden den Start mit Exit-Code 1 und der erwarteten Diagnosezeile (Wortlaut geprüft, ohne Wert der Option). Nicht belegt: Referenzclient, TLS, Anfragepfad, beschädigtes oder unzulässiges PRELOGIN (FA-01 Negative), abweichende Clientoption (FA-01 Boundary), protokollierte Zustandsereignisse bei gültigem Start (FA-09 Happy Path), ungültige sicherheitsrelevante Konfiguration über die geprüften Fälle hinaus (FA-09 Negative)" "make smoke"
lauf 1 make smoke

echo "e2e: Stufe 2 Container-Artefakt ..."
e2e_abdeckung "TDS-RB-01 TDS-QA-06 TDS-QA-03" "Image startet, Login mit TEST_UNENCRYPTED aus zweitem Container gelingt, docker stop endet mit Exit-Code 143, die Ausgabe nennt weder Benutzer noch Passwort. Nicht belegt: TLS-Modus, Fehlpfade, Clean-Build in der CI (QA-06 nur der Docker-Teil)" "make laufzeittest"
lauf 2 make laufzeittest

echo "e2e: Stufe 3 Referenzclient gegen Simulations-Backend ..."
e2e_abdeckung "TDS-FA-11 TDS-FA-03 TDS-FA-06" "Microsoft.Data.SqlClient gegen den Serverprozess mit Einstieg runServer im Test-Quelltext, nicht main: Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle über dem Heap, Fehler der Zeilenquelle als SqlException mit nutzbarer Verbindung, ohne Parameter, TLS und Pool. Nicht belegt: Einfüge-, Änderungs- und Löschanweisungen, parametrisierte Abfrage, Cancel, Anweisung außerhalb der erkannten Formen" "make simulations-integration"
lauf 3 make simulations-integration

echo "e2e: Stufe 4 Referenztest-Gerüst ..."
e2e_abdeckung "TDS-RB-02" "Das versionierte Testprojekt baut gesperrt und läuft, der Smoke-Test verbindet zu einem unbelegten Port und erhält eine SqlException. Nicht belegt: irgendein Serververhalten, kein Serverprozess läuft" "make referenztest-dotnet"
lauf 4 make referenztest-dotnet FORCE=1

echo "e2e: Stufe 5 Referenzclient: Szenarien gegen den Serverprozess ..."
e2e_abdeckung "TDS-FA-01 TDS-FA-02 TDS-FA-03 TDS-FA-04 TDS-FA-05 TDS-FA-06 TDS-QA-01 TDS-RB-02" "Microsoft.Data.SqlClient 7.1.1 in einem zweiten Container gegen den Serverprozess mit Einstieg runServer im Test-Quelltext (Richtlinie REQUIRED), TDS 7.4 mit TLS, Kette und Name geprüft: Anmeldung, gleiche Ablehnung bei falschem Kennwort und unbekanntem Benutzer, Auswahl im Batch mit Bedingung, Stern, NULL und alle acht Typen von SqlType, parametrisierte Abfrage (Int, Text) und Einfügen mit NULL-Parameter, Einfügen, Ändern und Löschen mit Zeilenzahl, Fehler vor Zeilen (Verbindung geschlossen), außerhalb der Formen und nach Zeilen (Verbindung nutzbar); gemessen als Abweisung: zwei Anweisungen in einem Text, InputOutput-Parameter ohne Rückgabewert. Nicht belegt: mehrteilige Antworten und Ausgabewerte (das Simulations-Backend liefert sie nicht), Cancel (CO-004), andere Typen des Clients, ein echter SQL Server" "make kompatibilitaet KATEGORIE=Szenario"
lauf 5 make kompatibilitaet KATEGORIE=Szenario

echo "e2e: Stufe 6 Referenzclient: Anmeldung, TDS 8.0 und RPC-Messungen ..."
e2e_abdeckung "TDS-FA-01 TDS-FA-02 TDS-FA-04 TDS-QA-01 TDS-QA-03" "Microsoft.Data.SqlClient 7.1.1 im zweiten Container gegen den Serverprozess (Richtlinie REQUIRED): TDS 8.0 (Encrypt=Strict) mit SQL-Authentifizierung für Batch und RPC, Kette und Name des Zertifikats geprüft (falscher Name scheitert in TDS 7.4 und 8.0, je mit Gegenprobe), Standardeinstellungen des Clients, Paketgröße 512 mit den längsten Feldern (die Annahme durch den Client, nicht die Paketierung der Antworten), Pooling (zweite Nutzung auf derselben Sitzung laut Log), Prepare als unbekannte RPC abgewiesen, die SET-Texte von SchemaOnly und KeyInfo im Log, BeginTransaction schließt die Verbindung, RPC-Parametertypen FLTN, MONEYN, DATETIMN, GUID und DATETIMEOFFSETN gelesen und als RPC abgewiesen. Nicht belegt: Windows-SSPI, Paketzahl eines LOGIN7, Paketgröße der Antworten des Servers, das RESETCONNECTION-Bit, TLS 1.2 und andere Suiten, sp_unprepare, ein echter SQL Server" "make kompatibilitaet KATEGORIE=Anmeldung"
lauf 6 make kompatibilitaet KATEGORIE=Anmeldung

echo "e2e: Stufe 7 Referenzclient: Lücken 3, 4 und 14 der Kompatibilitätsmatrix ..."
e2e_abdeckung "TDS-FA-02 TDS-FA-04 TDS-QA-01" "Microsoft.Data.SqlClient 7.1.1 im zweiten Container gegen den Serverprozess: xml als RPC-Parameter endet mit 50003 und geschlossener Verbindung, mehrere RPCs in einer Nachricht (SqlDataAdapter mit UpdateBatchSize 2) mit Protokollfehler 50006 und geschlossener Verbindung (UpdateBatchSize 1 gelingt), Kennwortwechsel und Zugriffstoken mit 18456, der Grund steht im Log des Servers. Nicht belegt: Windows-SSPI, dass der Client die FeatureExt 0x02 sendet, das Batch-Flag am Draht, ein echter SQL Server" "make kompatibilitaet KATEGORIE=Luecken"
lauf 7 make kompatibilitaet KATEGORIE=Luecken

echo "e2e: gruen (7 Stufen, $SECONDS s)"
