# Verifikationsbericht: slice-testserver-manuell-doku (HEAD 97b47e6)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-testserver-manuell-doku](../plan/planning/done/slice-testserver-manuell-doku.md) (HEAD `97b47e6`; Commits `76c72d8`, `68ea37f`, `97b47e6`; Basis `main` `3cfdd01`) gegen DoD §2, Plan §3, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit) und `AGENTS.md` §3.7, §3.8. Eingang: Review-Report [2026-11-05-76c72d8-slice-testserver-manuell-doku](2026-11-05-76c72d8-slice-testserver-manuell-doku.md) (0 HIGH, 2 MEDIUM, 3 LOW, 3 INFO).
* Selbst gefahren (Arbeitsbaum sauber, HEAD `97b47e6`, Port 24733, `TESTSERVER_NAME=tdswire-verif-mt`, Client-Image `tdswire-laufzeit-client:verif`): `make testserver-up` (4,0 s, Image im Cache), `-status`, `-logs`, zweiter `up`, Rohbytes-Client mit richtigem und falschem Kennwort, `-down`, zweiter `down`, `-status` nach `down`, `up` mit `TESTSERVER_HOST=0.0.0.0` (Warnung), `make docs-check` (724 Dateien, 0 Befunde). Eigene Container und das Client-Image sind entfernt; Container des parallelen `make e2e` nicht berührt.
* Nicht selbst gefahren: `make gates` (Haken im Plan offen, siehe B-1); `make testserver-selbsttest`; ein Start mit `REQUIRED` (kein Zertifikat vorhanden; die Anleitung nennt ihn selbst „nicht gefahren“); eine SQL-Anfrage (ohne Client mit Anfragefähigkeit; die Anleitung nennt sie „nicht gemessen“). Last des Rechners durch den Parallellauf nicht erhoben.

## Gesamturteil

**Closure-fähig: ja, mit Bedingungen B-1 bis B-4.** Kein HIGH, kein MEDIUM, 2 LOW, 2 INFO. Die Beispielaufrufe und Ausgaben der Anleitung stimmen mit dem Handlauf überein; die Befunde F-1 bis F-5 und F-8 des Reviews sind so behoben, wie `68ea37f` sagt. Offen sind nur Pflichten, die der Plan selbst für die Closure vorsieht.

Bedingungen:

* B-1: `make gates` grün, mit Ausgabe in der Closure-Notiz. Der DoD-Haken ist offen; ich habe `make gates` nicht reproduziert (nur `make docs-check`, grün).
* B-2: Review-Befunde F-6 und F-7 des Reviews (Beleg-Ablage in §7; Verweis in `harness/README.md`) sind erledigt beziehungsweise zu schließen: F-7 durch `97b47e6` (siehe unten), F-6 durch die Ausgabe dieses Berichts und die Notiz in §7.
* B-3: Closure-Notiz mit Steering-Loop-Lerneintrag, Register-Fortschreibung (Treffer `BEO-TDS/aussagen-ueber-messung-hinaus`), Ausgang für jedes der drei Risiken aus §6 (Ort `docs/user/`, Client-Wahl, nicht gemessene Clients) und die drei Paarungen. Alle noch nicht geschrieben („Noch nicht geschrieben“ in §7), kein Befund. Die zwei offenen Nutzerentscheidungen in §6 (Ort, Clients) sind dem Repo nicht als getroffen belegt; die Notiz nennt sie.
* B-4: Der Nachvollzug von einem frischen Klon durch eine zweite Person (Closure-Trigger der Welle [welle-testserver-manuell](../plan/planning/welle-testserver-manuell.md)) bleibt offen und ist kein Befund dieses Slice.

## DoD-Punkte (§2)

* **Punkt 1, Anleitung mit Befehlen, Einstellungen, Tabellen, zwei Clients, Belegpflicht: bestätigt, mit F-1 (LOW).**
  * `testserver-up`, `-down`, `-logs`, `-status`: Tabelle „Befehle“. Wirkung gefahren und wörtlich bestätigt (siehe Ausgabeabgleich).
  * Host, Port, Benutzer, Verschlüsselungsrichtlinie: Tabelle „Host, Port, Benutzer, Verschlüsselung“; Voreinstellungen und Variablen gegen den Kopf von `tools/testserver.sh` geprüft (`127.0.0.1`, `1433`, `alice`/`secret` als `{plain}secret`, `TEST_UNENCRYPTED`, Container und Image, `TESTSERVER_BAUEN`, `TESTSERVER_WARTEN` mit Voreinstellung 60, `TESTSERVER_FOLGEN`).
  * Beispiel-Tabellen mit Spalten: `kunden` (`Id`, `Name`, `Eintritt`) und `bestellungen` (`Id`, `KundenId`, `Betrag`); Typen laut Review gegen `src/testserver/resources/manuell/sim.yaml` und [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) geprüft (nicht von mir erneut Zeile für Zeile).
  * Je ein Beispielaufruf für zwei Clients: Beispiel 1 Rohbytes-Client (gefahren, gemessen), Beispiel 2 Microsoft.Data.SqlClient (Zeichenfolge aus `SimulationBackendTests.cs` Zeile 36, die Teile inklusive `Command Timeout=120` stimmen wörtlich; als „nicht gegen den Handlauf gemessen“ gekennzeichnet). Das Beispiel 2 ist keine Anfrage, die ich gefahren habe; die DoD verlangt „einen Beispielaufruf“ und Belegpflicht-oder-Kennzeichnung. Die Kennzeichnung steht im Titel und im Text. Ob das für „zwei Clients“ reicht, ist die Auslegung des Plans (§6 Risiko 2 offen, Vorschlag war Rohbytes plus JDBC mit Quellenangabe, der Nutzer hat nicht entschieden): hier INFO, siehe F-3.
  * Belegpflicht: Aussagen über Serververhalten tragen Test (`Leeres_Ergebnis_hat_Metadaten_und_keine_Zeile`, `ohne Callback endet die Anweisung mit TDS_UNSUPPORTED_SQL`), Messung (3,6 s) oder den Vermerk „nicht gemessen“ / „Setzung“. Stichproben ohne Gegenbeleg.
* **Punkt 2, Grenzen: bestätigt.** Abschnitt „Grenzen und nicht Belegtes“: kein Produkt mit Verweis auf [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md); keine Daten; nur [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig)-Formen (Abschließlichkeit als Setzung); keine Windows-Authentifizierung (Lastenheft Zeile 48 „Out-of-Scope“ bestätigt); keine Lastaussage; Cancel ([CO-004](../plan/carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)); die Testwerte nicht für schutzbedürftige Daten. Alle drei in der DoD genannten Beispiele (Anfragearten außerhalb der Spec, Last, Windows-Authentifizierung) stehen darin.
* **Punkt `make gates` grün: nicht bestätigt (B-1).** `make docs-check` gefahren: 724 Dateien, 0 Befunde. Der Slice berührt nur Markdown; `make gates` ist dennoch Pflicht-Haken.
* **Punkt Review: erfüllt.** Report unter `docs/reviews/` liegt vor; die Befunde sind eingearbeitet (siehe unten). Der Haken im Plan ist noch nicht gesetzt (Closure).
* **Punkt Verweise aus `README.md` und `harness/README.md`, `make docs-check` grün: bestätigt.** `README.md` hat eine neue Zeile mit Link auf `docs/user/testserver-manuell.md`; `harness/README.md` nennt die Anleitung in der Zeile `make testserver-up` statt des Verweises auf den Skriptkopf (`97b47e6`); der Link löst auf. Die Zeile verliert dabei den Zeiger auf die Warnung bei anderem Host im Skriptkopf; die Anleitung trägt sie (Abschnitt „Anderer Host“), kein Befund.
* **Punkte Closure-Notiz, Register, Risiko-Ausgänge, Paarungen:** offen, siehe B-3; kein Befund.

## Ausgabeabgleich der Anleitung (selbst gefahren)

| Zeile der Anleitung | Handlauf | Urteil |
|---|---|---|
| `testserver-up: bereit auf 127.0.0.1:<Port> (Container …, Image tdswire-testserver:lokal)` | wörtlich gleich (Port 24733, Container `tdswire-verif-mt`) | bestätigt |
| `testserver-up: Benutzer alice, Kennwort secret` | gleich | bestätigt |
| `testserver-up: Testwerte, keine Verschluesselung (TDS 7.4 im Klartext)` | gleich | bestätigt |
| `testserver-up: Stop mit 'make testserver-down', Log mit 'make testserver-logs'` | gleich | bestätigt |
| `testserver-status: laeuft (Container, Host:Port)` | gleich, Exit 0 | bestätigt |
| `tdswire: gestartet, Port 1433` (logs) | gleich | bestätigt |
| `laufzeit-client: Login ok` (Exit 0) | gleich | bestätigt |
| `laufzeit-client: Login fehlgeschlagen` (falsches Kennwort, Exit 1) | gleich, Exit 1; davor und danach folgt die Gradle-Meldung `BUILD FAILED` mit Nutzerhinweisen | bestätigt, siehe F-2 |
| `testserver-down: Container … gestoppt und entfernt` | gleich | bestätigt |
| zweiter `up`: bricht ab und nennt `make testserver-down` | `der Container … existiert schon; zuerst 'make testserver-down'`, make „Fehler 1“ | bestätigt |
| `down` ohne Container: Erfolg ohne Wirkung | `kein Container …, nichts zu tun`, Exit 0 | bestätigt |
| `status` ohne Container: `nicht gestartet` und ein Fehler | `nicht gestartet`, make „Fehler 1“ | bestätigt |
| Warnung `WARNUNG: <Host> ist nicht Loopback; der Server ist im Netz erreichbar` | mit `TESTSERVER_HOST=0.0.0.0` wörtlich gleich | bestätigt |
| Rückkehr nach 3,6 s | 4,0 s (Cache, Parallellast); Anleitung nennt Einzelmessung und fehlende Lasterhebung | bestätigt |

## Plan-vs-Code-Diff (§3)

| Plan | Diff | Urteil |
|---|---|---|
| `docs/user/testserver-manuell.md` neu | liegt, 131 Zeilen | gedeckt |
| `README.md` update, Verweis | eine Zeile | gedeckt |
| Optional: Beispielaufruf gegen laufenden `testserver-up` vor Aufnahme, Ausgabe als Beleg in §7 | Beispiel 1 gefahren (hier bestätigt); §7 noch nicht geschrieben; Beispiel 2 nicht gefahren, gekennzeichnet | teilgedeckt, B-3 |
| Nicht im Plan, im Diff | `harness/README.md` (Zeile `testserver-up`), Review-Report, Kopf des Slice (`Verantwortlich`) | von DoD Punkt 5 beziehungsweise Review verlangt; kein Befund |

Keine Änderung an Code, Dockerfile, Makefiles oder `GATE_CHECKS`: die Ausschlüsse aus §1 (Targets selbst, Einbettung/Release, Nachvollzug vom frischen Klon) sind gehalten.

## Umgang mit dem Review-Report: Stimmt `68ea37f`?

| Befund | Behauptung | Prüfung |
|---|---|---|
| F-1 (`REQUIRED` und `plain`) | eingearbeitet | stimmt: Abschnitt „Verschlüsselung `REQUIRED`“ nennt `AUTH_PLAIN_TESTMODE`-Kopplung, die Voreinstellungsliste als Startabbruch („der Server wurde nicht bereit“ steht wörtlich im Skript, Zeile 93), `TESTSERVER_BENUTZER_DATEI` mit `{pbkdf2-sha256}`, und dass das Skript nicht früh abbricht (kein Check im Skript gefunden). Als abgeleitet und nicht gefahren gekennzeichnet; [SPEC-051](../../spec/spezifikation.md#authentifizierungsadapter-vertrag-zu-spec-051) stützt es („`plain` nur bei ausdrücklich eingeschaltetem Testmodus, sonst Startabbruch“). Auch „Benutzerliste“ nennt `{plain}` nur im Testmodus. Ich habe den Start nicht gefahren. |
| F-2 (Setzung als Tatsache) | eingearbeitet | stimmt: „Setzung der Spezifikation … vom Nutzer nicht bestätigt … nicht am Server gemessen“ im Absatz „Anfragen“ und in den Grenzen („Setzung (nicht gemessen)“). |
| F-3 (Command Timeout) | eingearbeitet | stimmt: Zeichenfolge enthält `Command Timeout=120` und erklärt Herkunft (Test, Zeile 36). |
| F-4 (Zusammenstellung statt Lauf) | eingearbeitet | stimmt: Überschrift „Zusammenstellung der Ausgaben mehrerer Aufrufe … nicht ein einzelner Lauf“; Zeilen mit Quell-Aufruf in Klammern. |
| F-5 (Zeiger Skriptkopf) | eingearbeitet | stimmt: „Variablen stehen im Kopf …, ihre Prüfungen und Meldungen im Rumpf von `up`“; so im Skript. |
| F-8 (`TESTSERVER_BAUEN`, `TESTSERVER_WARTEN`) | eingearbeitet | stimmt: zwei Zeilen in der Einstellungstabelle, Voreinstellung 60 wie im Skript. |
| F-6 (Beleg-Ablage) | nicht in `68ea37f` | offen, B-2 |
| F-7 (`harness/README.md` Verweis) | `97b47e6` | stimmt, siehe DoD Punkt 5 |

## Konformität zu Entscheidungen und Hard Rules

* [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md): Die Anleitung sagt in Einleitung und Grenzen, der Testserver sei kein Produkt, das Image ein lokales Test-Image ohne Registry (Entscheidungen 2 und 4). Konform.
* [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md): Die Anleitung nennt die Targets als nicht in `make gates`; kein `GATE_CHECKS` berührt. Konform.
* `AGENTS.md` §3.7: die Anleitung nennt Zustand und Stand-Datum („Stand: Handlauf am 2026-11-05“), keine Chronik über Verworfenes. Konform.
* `AGENTS.md` §3.8: Aussagen tragen Beleg oder Kennzeichnung. Konform, mit F-1 und F-2 als Rest (LOW).
* Docker-only (§3.1): alle Befehle über `make` und `docker`. Konform.

## Findings

Kein HIGH, kein MEDIUM.

* **F-1 (LOW):** Die Anleitung sagt unter „Beispiel 1“, `laufzeit-client: Login fehlgeschlagen` mit „Exit 1“. Gefahren: Bei falschem Kennwort steht die Zeile in einer Ausgabe mit `FAILURE: Build failed … BUILD FAILED in 12s` und Gradle-Hinweisen, weil der Client als Gradle-Aufgabe läuft. Wer die Ausgabe wörtlich vergleicht, sieht mehr Text als die Anleitung zeigt. Exit 1 und die Zeile stimmen. Fix-Option: Hinweis „zusätzlich Gradle-Fehlermeldung“ in die Klammer der Zeile.
* **F-2 (LOW):** Die Einzelmessung „3,6 s“ steht mit Datum und fehlender Lasterhebung; mein Wiederholungslauf: 4,0 s, ebenfalls mit Cache und mit Parallellast. Die Anleitung trägt, was sie misst (AGENTS §5 Regel 7 verlangt für ein Messprotokoll den Host; die Zeile nennt ihn nicht). Fix-Option: Host nennen oder die Zahl aus der Nutzer-Doku nehmen.
* **F-3 (INFO):** Beispiel 2 ist kein gemessener Beispielaufruf. Das Verfahren ist durch §6 Risiko 3 vorgesehen und gekennzeichnet; ob die DoD „je einen Beispielaufruf für zwei Clients“ damit erfüllt ist, entscheidet der Planner. Mein Urteil: erfüllt als Aufruf mit Kennzeichnung; der Plan verlangt in §5 „die Verifikation führt die Beispielaufrufe selbst aus“, und ein Aufruf mit Microsoft.Data.SqlClient war für mich ohne .NET-Client im Handlauf nicht ausführbar.
* **F-4 (INFO):** Die Behauptung unter „Verschlüsselung `REQUIRED`“, ein Start mit der Voreinstellungsliste ende mit „der Server wurde nicht bereit“, ist nicht gefahren (kein Zertifikat zur Hand); sie ist in der Anleitung so gekennzeichnet. Nachweis steht in der Closure aus, falls der Planner ihn will.

## Offen und kein Befund

Closure-Notiz, Beobachtungs-Register, Risiko-Ausgänge, Paarungen und der Nachvollzug von einem frischen Klon durch eine zweite Person (Closure-Trigger der Welle).
