# Verifikationsbericht: slice-prelogin-serverversion (HEAD bc7d7fe)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-prelogin-serverversion](../plan/planning/done/slice-prelogin-serverversion.md) (Zweig `slice-prelogin-serverversion`, HEAD `bc7d7fe`; Commits `fbd2a44`, `5b1ad85`, `bc7d7fe`; Basis `ea528ad`) gegen DoD §2, Plan §3, [ADR-0001](../plan/adr/0001-tds-version-und-clientprofil.md), [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) und `AGENTS.md` §3.7, §3.8. Eingang: Review-Report [2026-11-05-5b1ad85-slice-prelogin-serverversion](2026-11-05-5b1ad85-slice-prelogin-serverversion.md) (0 HIGH, 1 MEDIUM, 3 LOW).
* Selbst gefahren (Arbeitsbaum sauber, HEAD `bc7d7fe`):
  * `make docs-check`: 751 Dateien, 0 Befunde.
  * `make test`: Exit 0, aber alle Stufen `CACHED` (gleiche Eingaben wie der Lauf des Implementers). Das ist kein frischer Beleg; deshalb zusätzlich `make clean-build` (`--no-cache`, 7 min 49 s, Exit 0): 907 Testfälle `PASSED`, keiner `FAILED`; alle neun `PreloginNegotiationTest`-Fälle `PASSED`, darunter `REQUIRED antwortet ENCRYPT_REQ und beginnt TLS nach der Antwort` (der Handvektor mit `10 00 03 E8 00 06`). `make clean-build` führt detekt mit, ohne .NET-Restore.
  * `make referenztest-dotnet`: Exit 0, ebenfalls vollständig `CACHED`, und er ist ein Smoke ohne Server (Kopf des Targets). Er sagt über VERSION nichts.
  * `make kompatibilitaet` (Referenzclient gegen den Serverprozess am HEAD): Exit 0, 13 Tests `Passed`, Zeile `Anmeldung: ServerVersion '01.00.0000', PacketSize 8000`.
  * JDBC-Beleg: `make testserver-up` mit `TESTSERVER_NAME=tdswire-verif-pre`, `TESTSERVER_PORT=24871`, `TESTSERVER_IMAGE=tdswire-testserver:verif-pre` (bereit auf 127.0.0.1:24871); d-migrate 1.12.0 (Digest `sha256:308f2775…6491`) `schema reverse --source "mssql://alice:secret@127.0.0.1:24871/master?encrypt=false"`: `[ERROR] Connection or metadata error: Failed to initialize pool: Unsupported SQL.`, Exit 4, keine Meldung „version 0“. Danach `make testserver-down` (Container entfernt), Image `tdswire-testserver:verif-pre` entfernt, `/tmp/rev.yaml` nicht angelegt.
* Nicht selbst gefahren: `make gates` als Ganzes (Closure-Pflicht auf `main`, siehe B-1); Mutation des Handvektors (am Code gelesen, nicht gefahren). Last: nicht erfasst (Host zur Zeit unter Last, die Läufe sind keine Zeitmessung).

## Gesamturteil

**Closure-fähig: ja, mit Bedingungen B-1 bis B-3.** Kein HIGH, kein MEDIUM, 1 LOW, 3 INFO. Die drei Liefer-Punkte der DoD sind am Code und an eigenen Läufen bestätigt; F-1 bis F-4 des Review-Reports sind in `bc7d7fe` behoben. Offen sind nur Pflichten, die der Plan für die Closure selbst vorsieht, und eine ehrlich benannte Messgrenze zu DoD 2.

Bedingungen:

* B-1: `make gates` auf `main` mit Ausgabe in die Closure-Notiz (hier nicht gefahren; `make test` war nur aus dem Cache grün, `make clean-build` frisch).
* B-2: Die drei Ausgänge in §6 stehen noch auf „weiter offen“. Die Closure-Notiz muss sie schließen oder benennen: Wert als Setzung gesetzt (Frage zum Wert), keine ADR (JDBC/d-migrate bleibt außerhalb des Clientprofils, Spec sagt es ausdrücklich), Referenzclient mit dem neuen Wert grün (siehe DoD 2 unten, mit der Messgrenze).
* B-3: Die Aussage in der Closure-Notiz zu DoD 2 trägt nur so weit wie unten (Anmeldung gelingt, Wert am Client nicht gesondert gemessen).

## DoD-Punkte (§2)

* **Punkt 1, Wert als Setzung in der Spec: bestätigt.**
  * Die Tabelle *Login-Antwort des Servers (Festlegung)* nennt `16.0.1000.6` mit „Setzung“ und verweist auf den Absatz darunter. Der Absatz *VERSION der PRELOGIN-Antwort (Setzung, nicht gemessen)* sagt: gesetzt, nicht an einem echten Server abgelesen; Byteordnung des Unter-Builds bleibt Festlegung; mindestens verlangte Hauptversion von mssql-jdbc nicht gemessen; Wert nicht Teil des Clientprofils.
  * Die Messung der Ablehnung von `0.0.0.0` ist mit Datum (2026-11-05), Client (mssql-jdbc 13.4.0 in d-migrate 1.12.0), Fehlertext (`SQL Server version 0 is not supported by this driver`) und Exit 4 genannt.
  * F-1 (Kernpunkt dieser Prüfung): Das Messprotokoll [prelogin-serverversion-2026-11-05](../messungen/prelogin-serverversion-2026-11-05.md) enthält jetzt beide Läufe nebeneinander. Lauf A (Commit `ea528ad`, VERSION `0.0.0.0`, Port 24862): Exit 4, `Server major version:0 is not supported by this driver` und `SQL Server version 0 is not supported by this driver`. Lauf B (Commit `fbd2a44`, `16.0.1000.6`): Exit 4, `Unsupported SQL.`. Die Spec-Formulierung ist mit dem Protokoll deckungsgleich: Fehlertext, Exit-Code, Client und Datum stimmen, die Spec behauptet nicht mehr („wie SQL Server 2022“ ist entfallen, F-3). Den Lauf A habe ich nicht neu gemessen; er stützt sich auf das Protokoll des Implementers, Lauf B habe ich unabhängig bestätigt (siehe Punkt 3). Das ist eine Einzelmessung, in Spec und Protokoll als solche geführt.
* **Punkt 2, Code, Test, .NET-Referenztests: Code und Test bestätigt, .NET nur eingeschränkt belegt.**
  * `PreloginNegotiation.kt` sendet `PreloginOption.version(SERVER_MAJOR, SERVER_MINOR, SERVER_BUILD, SERVER_SUB_BUILD)` mit 16, 0, 1000, 6; KDoc nennt `16.0.1000.6`. `PreloginNegotiationTest` hält den Handvektor `FF  10 00 03 E8 00 06  03  00  00` (1000 = 0x03E8, Big-Endian) für die ganze Nutzlast; er lief frisch grün. Kein `@Suppress`, Docker-only.
  * Die .NET-Referenztests: `make referenztest-dotnet` ist ein Smoke ohne Server und kommt aus dem Cache; er belegt hier nichts. Den Wert prüft erst `make kompatibilitaet` (Referenzclient gegen den Serverprozess am HEAD): 13 Tests grün, die Anmeldung gelingt. Der Test prüft `ServerVersion` nur auf nicht leer (`SzenarienTests.cs`), und der gemessene Wert `01.00.0000` stammt aus dem LOGINACK, nicht aus der PRELOGIN-VERSION. **Ehrlich:** „Referenztests bleiben grün“ ist erfüllt (Anmeldung und alle Szenarien gelingen mit `16.0.1000.6`); „der .NET-Client nimmt den Wert an“ ist nur über das Gelingen der Anmeldung belegt, der Wert selbst ist am Client nicht gesondert gemessen. Spec und Protokoll sagen genau das.
* **Punkt 3, Beleg am JDBC-Treiber: bestätigt.** Eigener Lauf (siehe oben): Der Treiber meldet nicht mehr „version 0“, Exit 4 mit `Unsupported SQL.`; das gleicht dem Lauf B des Protokolls. Das Protokoll nennt Client, Version, Datum, Host (Linux, Docker, `--network host`) und Bedingung (Last von Lauf A; für Lauf B ausdrücklich „nicht erfasst“, F-2 behoben). Die Lesart „PRELOGIN und LOGIN7 angenommen“ ist als Schluss aus dem Wechsel der Meldung gekennzeichnet, nicht als Mitschnitt; das trägt nicht weiter als der Beleg. Was danach scheitert (`Unsupported SQL.`), ist gemeldet und nicht behoben, wie die DoD verlangt.
* **Haken, Closure-Notiz, Register, Risiko-Ausgänge §6, Merge:** offen und kein Befund.

## Plan-vs-Code-Diff (§3, `git diff ea528ad..HEAD`)

| Plan | Diff | Urteil |
|---|---|---|
| `spec/spezifikation.md` update | Tabellenzeile und neuer Absatz zur Setzung | gedeckt |
| `PreloginNegotiation.kt` update | Konstanten `SERVER_*`, Aufruf, KDoc | gedeckt |
| `PreloginNegotiationTest.kt` update | Handvektor und KDoc | gedeckt |
| nicht im Plan, im Diff | `docs/plan/kompatibilitaetsmatrix.md` (Zeile PRELOGIN: `0.0.0.0` → `16.0.1000.6`, „Festlegung ohne Messung“) | folgt aus der Spec-Änderung, kein Überschuss; hält die Matrix (Drift-Vermeidung) |
| nicht im Plan, im Diff | `docs/messungen/prelogin-serverversion-2026-11-05.md`, Review-Report, Kopfzeile `Verantwortlich` | von DoD Punkt 1 und 3 bzw. dem Workflow verlangt |

Keine Änderung außerhalb der benannten Stellen; die Ausschlüsse aus §1 (Katalogabfragen, Clientprofil, Messung am echten Server) sind gehalten. Das Alt-Literal `0.0.0.0` steht in Spec, Code und Matrix nur noch als Bind-Adresse (`SPEC-005`) und als Messung des abgelehnten Werts.

## Konformität zu Entscheidungen, Lastenheft und Hard Rules

* [ADR-0001](../plan/adr/0001-tds-version-und-clientprofil.md): Das Profil führt `Microsoft.Data.SqlClient` 7.1.1 mit TDS 7.4 und 8.0. Der Slice ändert weder Clientpaket noch TDS-Version im LOGIN7/LOGINACK; er setzt nur die PRELOGIN-VERSION des Servers, die die ADR nicht festlegt (sie nennt sie nur als Unterscheidungsmerkmal zwischen Clientpaketen). Die Spec sagt ausdrücklich, der Wert gehöre nicht zum Clientprofil. Konform; keine neue ADR nötig, solange JDBC/d-migrate nicht ins Profil wandert (so auch Plan §6).
* [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung): PRELOGIN-Antwort bleibt gültig (Optionstabelle, Terminator, ENCRYPT-Aushandlung unverändert; die Tests der Aushandlung laufen grün). Konform.
* [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität): Der Referenzclient meldet sich weiter an (`make kompatibilitaet` grün); der Wert ist als Festlegung ohne Messung am echten Server ausgewiesen. Konform.
* `AGENTS.md` §3.7: Die Konstanten tragen nur die Zusage und den Rang-Zeiger („Setzung ohne Messung am echten Server (spec/spezifikation.md, Login-Antwort)“); der verworfene Wert und der Treiber stehen nicht mehr im Kommentar. F-4 behoben.
* `AGENTS.md` §3.8: Die Spec trägt nicht weiter als das Protokoll (Einzelmessung, ein Host; Mindestversion und .NET-Wert ausdrücklich nicht gemessen). F-3 behoben. Hinweis (LOW, F-V1): Die Spec verweist auf das Protokoll nur als „Messprotokoll-Verzeichnis des Repos“ ohne Link; das ist durch die Richtungsregel der Spec-Straten bedingt (kein Verweis von Rang 2 nach `docs/`), das Protokoll selbst ist über Slice und Matrix auffindbar. Kein Handlungszwang.

## Behebung der Review-Befunde

| Befund | Stand in `bc7d7fe` | Urteil |
|---|---|---|
| F-1 (MEDIUM) Ablehnung ohne Protokoll | Lauf A im Messprotokoll mit Testserver-Stand, Port, Fehlertext, Exit; Spec deckungsgleich | behoben |
| F-2 (LOW) Last, Stand, Host | Testserver-Stand je Lauf, Host, Lastmittel von Lauf A, Lücke bei Lauf B benannt; Lesart als Schluss gekennzeichnet | behoben (Lücke bei Lauf B ehrlich benannt) |
| F-3 (LOW) Außenfakt „wie SQL Server 2022“ | Aussage entfernt, Spec nennt nur Setzung und Messung | behoben |
| F-4 (LOW) Kommentar mit verworfenem Wert | Kommentar auf Zusage und Rang-Zeiger gekürzt | behoben |

## Zählung

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 1 (F-V1, kein Handlungszwang) |
| INFO | 3 (`make test` und `make referenztest-dotnet` nur aus dem Cache, ersetzt durch `make clean-build` und `make kompatibilitaet`; Lauf A nicht neu gemessen; Wert am .NET-Client nicht gesondert gemessen) |

## Übergabe an den Planner

Closure erst nach B-1 bis B-3. Für die Closure-Notiz (§7): Die Messgrenze zu DoD 2 gehört wörtlich hinein; ein eigener Test für den Wert am .NET-Client (z. B. Rohbytes-Mitschnitt oder Treiberlauf als Test) wäre ein eigener Gegenstand, kein Mangel dieses Slice. Die Frage, was `Unsupported SQL.` auslöst, bleibt bei [slice-d-migrate-verbindung-und-export](../plan/planning/next/slice-d-migrate-verbindung-und-export.md).
