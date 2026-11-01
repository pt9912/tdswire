# Verifikation: slice-anfragefolge-gesamttest (HEAD 8468425)

* Rolle: Verifier (Modul 11), frischer Kontext. Branch `worktree-agent-a63412e9e57bc9ba2`, HEAD `8468425`. Geprueft gegen DoD des Slice, Welle-Wortlaut (§1, §3), [TDS-FA-03](../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) (Abbruch, hier nur Abgrenzung), AGENTS 3.7, 3.8. Keine Repo-Datei ausser diesem Bericht geaendert; Mutationen in Wegwerf-Kopien (`git archive HEAD`, ausserhalb des Worktrees).

## Gesamturteil: closure-faehig mit Bedingungen

Der Test (Liefer-Punkt 1) und sein Waechter (Liefer-Punkt 2) sind am Code belegt: Gate gruen, 5 von 5 eigenen Mutationen rot, Grundlinie gruen, die Fixes F-1, F-3, F-4, F-5, F-7 schliessen die Review-Findings. Offen sind keine Code-Maengel, sondern (a) die Closure-Pflichten, die noch nicht erledigt sind (Notiz §7 ist Vorlage, DoD-Haken leer), und (b) die Nutzerentscheidung zur Lesart "Nachrichten statt Transport" (F-2 des Reviews), die ich hier nicht bestaetige.

## Sensor-Belege

* `make gates` im Worktree: Exit 0, `real 12,378 s` (Einzelmessung 2026-11-01, Host-Last: load average 8,35 / 5,23 / 5,06 zu Beginn, 8,43 / 5,35 / 5,10 am Ende; parallel lief ein fremder Host-Prozess `make test`, nicht angefasst). Die Test-Stage war CACHED (Inhalt von HEAD wurde schon gebaut): die 12 s sind eine Cache-Messung, **kein** Beleg fuer die Gate-Kosten des Tests und kein Frischlauf.
* Frischlauf des Tests: eigener Docker-Build der Stage `test` auf einer Kopie von HEAD mit `--no-cache-filter test` und Filter `--tests '*RequestSequenceTest'` (nur Zeile 87 des Dockerfile in der Kopie geaendert): `RequestSequenceTest > Die Gesamtfolge der Anfragearten laeuft auf einer Sitzung ohne Zustandsverlust() PASSED`, `BUILD SUCCESSFUL in 55s` (Gradle-Gesamtzeit inkl. Start, Last ~8). Die Testdauer selbst (DoD: unter 2 s) habe ich **nicht** aus dem Gradle-Bericht gelesen; Beleg dafuer steht aus (Bedingung B3).
* Fehlt dem Implementer-Handoff ein Sensor-Beleg? Die Behauptung "gelaufen" war nicht aus einer Ausgabe ersichtlich; die Belege oben sind von mir selbst gefahren.

## DoD Punkt fuer Punkt

| DoD-Punkt | Befund |
|---|---|
| Gesamtfolge-Test, `@spec` SPEC-001, 002, 030, 057, 013 im KDoc der Testfunktion, Source-Set `test` | erfuellt: `RequestSequenceTest.kt` Z. 174 bis 178, eine Klasse, eine Testfunktion |
| Rohbytes-Folge auf **einer** Sitzung ab READY gegen Backend-Port-Implementierung | erfuellt mit Lesart: `Session.login(*messages)` einmal, `session.serve` einmal, `ScriptedBackend.inSequence`; Handvektoren fuer Antworten. Eingabe sind fertige Nachrichten, siehe F-2 |
| Batch mit Ergebnis / leer / zwei Ergebnisse | erfuellt (Schritte 1 bis 3, Hex-Vergleich) |
| `sp_executesql` mit NULL- und OUTPUT-Parameter | erfuellt (Schritt 4; `expectedCalls` prueft `null` und `ParameterDirection.OUTPUT`) |
| `sp_prepexec`, dann `sp_execute` mit gelieferten Handle | erfuellt (Schritt 5, Handle 1 im Hex der Antwort, dann `execute(1)`) |
| unbekannter RPC, unbekanntes Handle (je definierter Fehler) | erfuellt (Schritte 6, 7; Codes 50002, 50009, `errorMessage()`, `DoneStatus.ERROR`, 2 Tokens, Log `TDS_UNSUPPORTED_RPC`, `TDS_UNKNOWN_HANDLE`) |
| danach noch erfolgreiche Anfrage auf derselben Verbindung | erfuellt (Schritt 8 plus Zusatz 9, 10) |
| keine PLP-/NBCROW-Form | erfuellt (nur Int32 und `D1`-ROW), jetzt im KDoc benannt (F-7) |
| Anfragezustand kehrt nach jeder Anfrageart zurueck | erfuellt, indirekt: `StateProbe` belegt RUNNING je Backend-Aufruf; Rueckkehr belegt die Annahme der Folgenachricht (`RequestLoop.step`: Schleife endet ausserhalb READY). Fuer die Fehlerarten (6, 7) beobachtet der Probe nichts (Backend nicht gerufen), die Rueckkehr belegt dort nur die Folgenachricht. Das KDoc sagt es so |
| Handle-Tabelle ueberlebt, Fehler beendet Sitzung nicht | erfuellt (Handle 1 aus (5) wird in (10) nach Batch, RPC, zwei Fehlern wieder ausgefuehrt; Schritte nach 6 und 7 laufen) |
| Waechter: Mutationen gesehen rot, Liste in §3 (M1 bis M8), Messzeilen in Closure-Notiz | Test-Seite erfuellt (siehe Mutationen). **Messzeilen in der Closure-Notiz stehen noch nicht** (§7 Vorlage); das ist Closure-Arbeit, siehe Bedingungen |
| Zuordnung gemessen / Festlegung | im Klassen-KDoc vorhanden und stimmig (Festlegung: Tokenfolge RPC, Codes, Handle 1, Ende der Folge); die Spalten in der Closure-Notiz stehen aus |
| Gate-Zeit (Test unter 2 s; `make gates` nicht ueber Streuung) | **nicht belegt**: Testdauer nicht gelesen, `make gates` war Cache. Kein Widerspruch, aber keine Zahl (B3) |
| `make gates` gruen | erfuellt (Exit 0, siehe oben; Cache-Lauf, Inhalt von HEAD) |
| Review, Report liegt vor | erfuellt: `docs/reviews/2026-11-01-b38925f-slice-anfragefolge-gesamttest.md` |
| Doku-Update | keiner noetig; Produktivcode und Vertraege unberuehrt (`git diff a3abec4..HEAD --stat`: nur Testdatei, Slice, Review-Report) |
| Closure-Notiz, Steering-Loop-Eintrag, Register, Risiko-Ausgaenge, drei Paarungen | **offen** (§7 enthaelt Platzhalter, DoD-Haken alle leer). Erwartet vor `done/`, kein Mangel des Codes |

## Plan-vs-Diff

Plan §3 nennt genau eine Datei (neu): `RequestSequenceTest.kt`. Diff `a3abec4..HEAD`: diese Datei (273 Zeilen), der Slice-Move/Text, der Review-Report. `src/main`, `config/`, `Dockerfile`, `build.gradle.kts` unveraendert. Folge 1 bis 8 im Wortlaut der Welle, 9 und 10 als im Plan benannte Erweiterung; Zahlen 9 Backend-Aufrufe, 11 Antworten wie im Plan (§6), als `assertEquals` (Gleichheit statt Obergrenze), plus `closeCalls == 9`, `channel.exhausted`, Endzustand `CLOSED`. Abweichung vom Plan: keine, die nicht dokumentiert waere. Plan-Mutationen M1 bis M8 sind im Reviewer-Lauf (11 Mutationen) und hier (5) rot gesehen; M3/M6/M8 des Plans habe ich nicht einzeln gefahren (M3 und M6 sind von meiner M2/M5 strukturell gedeckt, M8 = M4).

## Pruefung der Fixes gegen die Review-Findings

* F-1 (HIGH, Konjunktiv "bliebe"): geschlossen. Neue Fassung (Z. 160 bis 162) ist Indikativ: "sie pruefen die Handle-Tabelle nach `sp_executesql`". Kein "ohne ... bliebe" mehr im Test (gelesen).
* F-3 (LOW, Vektor-Herkunft): geschlossen. Die Konstante `outputReply` traegt jetzt "Selbstkonsistenz ... nur dadurch belegt, dass dieser Test besteht" statt "Quelle". AGENTS 3.8 eingehalten.
* F-4 (LOW, Zaehlung): geschlossen. KDoc "elf Nachrichten der zehn Schritte", Zaehlungsabsatz erklaert 0-basiert vs. Schrittnummer; Kommentar zu den Index-Konstanten nennt "Schritt 5 belegt die Indizes 4 und 5". Gelesen und gegen `messages()` abgezaehlt: stimmig.
* F-5 (LOW, Meldungen): geschlossen und wirksam. In M1 lautete die Meldung "Antworten: nach 1 folgt Antwort 1 (Schritt 2, Batch leer) ==> expected: <11> but was: <1>"; in M2 und M5 "nach 8 folgt Aufruf 8 (Schritt 10, sp_execute mit Handle aus Schritt 5: Handle-Tabelle geleert?)". Der erste rote Schritt ist jetzt lesbar. Randbefund: die `getOrNull`-Tabellen sind Handpflege, die bei Folgeaenderung driftet (INFO).
* F-7 (INFO, PLP/NBCROW nicht im KDoc): geschlossen (Z. 161 bis 162).
* F-2 (MEDIUM) und F-6 (LOW) sind laut Auftrag nur Slice-Vermerke (§6 Stand nach dem Review, §7 Implementer-Vermerk), kein Code. Vermerke vorhanden und sachlich richtig, F-2 bleibt offen (siehe unten), F-6 ist Planner-Sache.

## Mutationen (Wegwerf-Kopie, Stage `test`, Filter `*RequestSequenceTest`, je neuer Build; Einzelmessungen unter Last ~8)

Grundlinie: PASSED. Alle Mutationen im Produktivcode `RequestLoop.kt`; nach jeder Kopie neu aus `git archive`.

| Mutation | Wirkung | Erster roter Schritt (Meldung) |
|---|---|---|
| M1 Zustand kehrt nach SQL_BATCH nicht nach READY zurueck (`... && event.message.type != PacketType.SQL_BATCH` in `step`) | rot | "Antworten: nach 1 folgt Antwort 1 (Schritt 2, Batch leer)": expected 11, was 1 |
| M2 `handles.clear()` nach `sp_executesql` (`RpcBinding.Query`) | rot | "Backend-Aufrufe: nach 8 folgt Aufruf 8 (Schritt 10 ...)": expected 9, was 8 |
| M4 definierter Fehler (und jeder Abschluss) setzt `connectionOpen = false` in `requestFinished` | rot | "Antworten: nach 7 folgt Antwort 7 (Schritt 7 ...)": expected 11, was 7 (Sitzung endet nach Schritt 6) |
| M5 `handles.clear()` nach Batch | rot | wie M2 (Schritt 10) |
| M7 `handles.clear()` im Fehlerpfad `refuse` | rot | wie M2 (Schritt 10) |

Das deckt Plan-M1/M4 (Zustand), M2/M5-Plan (Handle-Leerung Batch, executesql), M7 (Fehlerpfad), M8 (Fehler beendet Sitzung). Nicht selbst gefahren: Zustandsverlust nach `sp_executesql`/`sp_prepexec` einzeln (Plan-M2, M3) und Antwortform-/Handle-Wert-Mutationen; der Review hat dazu m3, m5, m8 bis m11 rot gesehen, ich uebernehme das als Fremdbeleg, nicht als eigene Messung. Beobachtung: M2, M5, M7 sind am Test nur ueber denselben Schritt (10) unterscheidbar; die Unterscheidung steht im Mutationsort, nicht in der Meldung (INFO, kein Mangel; bekannte Grenze von `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge`).

## Traegt "Gesamtfolge" der Beleg? (AGENTS 3.8)

Ja fuer: Dispatch von Batch- und RPC-**Nachrichten** (Pakettyp und Nutzlast) durch `RequestLoop` einer Sitzung gegen ein skriptgesteuertes Backend; Zustandsfolge READY/RUNNING, Handle-Tabelle ueber Anfragearten, Fehler ohne Verbindungsende, Antwort-Nutzlast als Hex.
Nein (nicht belegt, und das KDoc sagt es): Paket-Framing, Zerlegung in Pakete der Packet Size, EOM/Status, Socket, TLS, Login-Bytes, Referenzclient, echter SQL Server, Token-/Statusbits gegen reale Clients (Festlegung). Die Antworten sind aus `session.channel.sent` als Nachrichten gelesen.

**Offene Nutzerentscheidung (nicht stillschweigend bestaetigt):** Die Welle (§1) sagt "diesen Rohbytes-Client" und (§3) "Rohbytes-Lauf auf einer Sitzung". Der Test liest "Rohbytes" als Nachrichten-Nutzlast (Plan-Wahl `Session`, nicht `WireSession`; Alternative `WireSession` mit Ueberlader ist im Plan benannt). Ob das fuer den Closure-Trigger der Welle genuegt oder ob die Folge ueber Leser/Schreiber/Paket-Codec (oder den Socket) laufen muss, ist **Entscheidung des Nutzers**; ich bestaetige die Lesart weder noch lehne ich sie ab. Folge einer Nein-Entscheidung: Rueckfuehrung/Folge-Slice (Test mit `WireSession` fuer RPC), nicht Reparatur hier. Bis zur Entscheidung darf die Welle den Satz "Rohbytes-Lauf" nicht ohne die Einschraenkung "Nachrichten, nicht Transport" schliessen.

Weitere Reichweite der Aussage: "Zustand kehrt nach IDLE zurueck" ist durch die Annahme der Folgenachricht belegt (READY ist IDLE, `COMPLETED` nicht abgebildet, SPEC-001): fuer den Leser eine Folgerung am Schleifencode, keine Direktbeobachtung des Zustands zwischen den Anfragen.

## Findings

* **F-V1 (MEDIUM, Entscheidung beim Nutzer, Klasse "Aussage traegt weniger als der Wortlaut"):** Lesart "Nachrichten statt Transport" der Welle-Formulierung "Rohbytes-Lauf/Rohbytes-Client" (AGENTS 3.8). Test und Slice benennen die Grenze korrekt; der Wellen-Wortlaut tut es nicht. Aufnahme in die Welle-Closure und Entscheidung vor `done/` der Welle.
* **F-V2 (LOW, Closure-Pflichten offen):** Slice §7 ist Vorlage, alle DoD-Haken leer; Mutations-Messzeilen (M1 bis M8 mit erstem rotem Schritt), Spalten gemessen/Festlegung, Risiko-Ausgaenge (§6 sagt "weiter offen" vier Mal, setzt der Implementer endgueltig), Steering-Loop-Eintrag, Register-Fortschreibung (zwei offene BEO-Zaehler beruehrt: `schranken-test-mit-ungeprueftem-zaehler`, `mutation-haengt-an-ungesteuerter-reihenfolge`; ein Evidence-Beleg auf 3x braucht Lese-Schritt-Entscheidung) und Paarungen sind zu schreiben. Meine Mutationsmeldungen oben koennen als Quelle dienen.
* **F-V3 (LOW, Beleg fehlt):** Gate-Zeit-Zusage der DoD nicht belegt: Testdauer aus dem Gradle-Bericht nicht gelesen (Review: 0,004 s, nicht nachgemessen); `make gates` hier ein Cache-Lauf (12,4 s). Fuer die Closure-Notiz ist ein Frischlauf (`make clean-build` oder `--no-cache-filter test`) mit gelesener Testdauer und Last zu messen; sonst Risiko "Gate-Zeit" bleibt ohne Ausgang.
* **F-V4 (LOW, Prozess, aus Review F-6):** WIP-Limit: `slice-e2e-geruest` und dieser Slice beide in `in-progress/` mit `Verantwortlich: pt9912` ohne Zweigunterscheidung; Planner kennzeichnet Inhaber (nicht Code).
* **F-V5 (INFO):** Rueckkehr des Anfragezustands nach den Fehlerarten (6, 7) und die Zustandsfolge zwischen den Anfragen ist nur indirekt (Annahme der Folgenachricht), nicht per Zustandsbeobachtung belegt; `StateProbe` sieht nur Backend-Aufrufbeginn. Die Zusage im KDoc ist entsprechend formuliert; keine Aenderung noetig.
* **F-V6 (INFO):** `REPLY_KINDS`/`CALL_KINDS` sind von Hand gepflegte Tabellen neben `messages()`; bei Aenderung der Folge driften sie. Nutzen (lesbare Meldung) belegt; Risiko klein.
* **HIGH:** keine.

## Bedingungen fuer die Closure (in dieser Reihenfolge)

* B1: Nutzerentscheidung zur Lesart (F-V1) vor Welle-Closure; ggf. Folge-Slice.
* B2: Closure-Notiz vollstaendig (F-V2), inklusive der Mutationszeilen M1 bis M8 mit erstem rotem Schritt (gestuetzt auf Review plus diese Messungen) und gemessen/Festlegung.
* B3: Gate-Zeit mit gelesener Testdauer und frischem Lauf (F-V3), Last nennen.
* B4: Reiner `git mv` nach `done/` erst nach der Notiz (AGENTS 3.3).

## Nicht-Aktionen

Kein Code geaendert, kein `--no-verify`, kein `pkill`; der fremde Host-Prozess `make test` (seit 31.10.) wurde nicht beruehrt. Wegwerf-Kopien liegen im Scratchpad (`base/`, `mut/`, `mut-*.log`), nicht im Worktree.
