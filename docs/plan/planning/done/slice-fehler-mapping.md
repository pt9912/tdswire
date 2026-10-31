# Slice slice-fehler-mapping: Fehler-Mapping: Zuordnung jedes Fehlercodes zu ERROR-Nummer, Class, State, Text und Verbindungsfolge

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0007](../../adr/0007-fehler-und-attention.md)

**Berührte Spec-Stellen:** SPEC-011 · SPEC-012 · SPEC-013 · SPEC-014 · SPEC-015 · SPEC-016 · SPEC-037 · SPEC-038 · SPEC-039 · SPEC-048 · SPEC-049 · ARC-015

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine reine Funktion bildet jeden Fehler der Protokollschicht und des Backends (`Failure` mit Code, unerwartete Ausnahme) auf ERROR und DONE mit Fehlerstatus samt Class und Verbindungsfolge ab, und die Zuordnung jedes eigenen Codes steht in der Spezifikation (Auftrag des Fehler-Mappings nach [ADR-0007](../../adr/0007-fehler-und-attention.md) und [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Übergang der Sitzung nach dem Fehler und die Frage, wann ein Strom nicht mehr abschließbar ist — `slice-fehler-zustandsuebergang`; das Mapping ist rein und kennt keinen Strom.
- ATTENTION, Cancel und der Ablauf von `TDS_TIMEOUT` — `slice-attention-cancel-timeout`; hier nur die Zuordnung des Codes.
- Die Bytes der festen Login-Ablehnung (ERROR 18456, Class 14, `SPEC-012`) — sie gehören `slice-handshake-login` (Welle `welle-handshake-login`); das Mapping nimmt den Code lesend auf, ein Test belegt die Gleichheit, der Slice ändert die Bytes nicht.
- Die Unterscheidung statement- und batch-abbrechender Fehler im Strom — `Failure` trägt sie nicht, und der Port gehört der Welle `welle-backend-streaming`; hier steht nur der Abstimmungsbedarf (§6).
- Messung von Nummern, States und Texten an einem echten SQL Server — `slice-qa-reproduzierbarkeit` (Vergleichslauf D) und `BEO-TDS/messung-am-echten-sql-server-fehlt`; der Slice baut kein Messwerkzeug.
- Observability-Adapter und strukturierte Logs (`SPEC-017`) — Träger: Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md); der Slice ruft nur den minimalen Log-Anschluss der Welle `welle-handshake-login`.
- Vollständige Nachbildung aller SQL-Server-Fehlernummern — ausdrücklich Out-of-Scope von [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Mapping-Funktion ohne Netz und ohne Schreiben: Jeder eigene Code (`SPEC-011` bis `SPEC-016`, `SPEC-037` bis `SPEC-039`, `SPEC-048`, `SPEC-049`; `TDS_LOGIN_FAILED` nur lesend) liefert Nummer, Class, State, festen Text und Verbindungsfolge (offen · schließt nach ERROR und DONE · schließt ohne Antwort). Ein Test je Code gegen Handvektoren der Bytes; ein Vollständigkeitstest scheitert, sobald ein Code ohne Zuordnung existiert. ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), Negative) *Bestätigt vom Verifier: elf Codes, elf Handvektor-Tests; der Vollständigkeitstest vergleicht die Enum mit einer Liste im Test, nicht mit der Tabelle in §4 (V-4, als Risiko in §6 geführt); der Abgleich von Tabelle und Enum war Handarbeit des Verifiers.*
- [x] Eine Ausnahme ohne Code erreicht den Client nur als `TDS_INTERNAL_ERROR` mit festem Text; Test: ein Ausnahmetext mit einem Konfigurationswert steht weder in den Antwortbytes noch im Log-Eintrag, der Log-Eintrag trägt keine Parameterwerte ([TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) Nr. 5, `SPEC-048`).
- [x] `spec/spezifikation.md` fortgeschrieben: Zuordnungstabelle je Code (Nummer, Class, Folge), jede Zeile als *gemessen* oder *Setzung* gekennzeichnet; die Aussage „Die Zuordnung jedes Codes legt das Fehler-Mapping fest“ in [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) ist erfüllt oder nennt den verbleibenden Rest. *Die Korrektur zu V-3 (Werte von 18456 in der Tabelle) kam in der Restrunde `d827919` und ist nicht nachverifiziert.*
- [x] `make gates` grün. *(Exit 0 auf dem Stand der Closure; der Verifier fuhr `make gates` nicht als Ganzes, V-7.)*
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/` (Package-Name ein Vorschlag) | neu | Mapping: Fehlerart → ERROR-Tokenfelder, Class, Folge; reine Funktion |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/TokenEncoder.kt` | nutzen | ERROR und DONE-Familie sind aus `welle-protokollcodec` vorhanden; keine Änderung geplant |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/error/` | neu | Test je Code, Vollständigkeitstest, Test des festen Textes ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) Negative, [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) Nr. 5) |
| `spec/spezifikation.md` | update | Zuordnungstabelle (§4 Fehler-Codes) und Geschichte-Zeile |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

Ist-Zustand des ersten Laufs (gegen die Tabelle oben gemessen):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt` | neu | Zuordnungstabelle: `ErrorCode` mit `ErrorRule` (Nummer, Class, State, Text, Verbindungsfolge) und `ConnectionFate`; die Package-Wahl `protocol.error` ist der Vorschlag des Plans, übernommen |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapper.kt` | neu | die Funktionen `code`, `failure`, `protocol`, `unexpected` (Schnittstelle für den Sequenzierer), `ErrorResponse`, `ErrorLog`; schreibt ERROR und DONE über `TokenEncoder`, der unverändert bleibt |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapperTest.kt` | neu | Handvektor je Code ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) Negative), Vollständigkeit, Gruppen, Gleichheit mit der Login-Ablehnung, fester Text ohne Ausnahmetext ([TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) Nr. 5) |
| `spec/spezifikation.md` | update | Zuordnung in §4, Verweis in `TDS-FA-06.a`, Geschichte-Zeile |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseErrorMapperTest.kt` | neu | Integrationstest Sequenzierer und `ErrorMapper` ohne Fake: gleiche Bytes und gleiche Folge je Code ([TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)) |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-fehler-abbruch` ist eingetreten, `slice-handshake-login` liegt in `done/` (die feste Ablehnung steht als Vergleichsbasis), [ADR-0007](../../adr/0007-fehler-und-attention.md) steht im ADR-Index auf `Accepted`, und ein Verantwortlicher ist im Kopf eingetragen. Die Nutzerentscheidung zur Zuordnung der Codes ohne Gruppe (§6) liegt vor; fehlt sie, gilt für diese Codes die Grundregel A der ADR (schließen) und der Slice führt die Einzelzuordnung als offen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die Zuordnung eines Codes widerspricht einem Satz der Spezifikation, den nur eine Folge-ADR klären kann (zum Beispiel die Class eines Codes gegen [ADR-0007](../../adr/0007-fehler-und-attention.md)).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`) und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Nummern, States und Texte der eigenen Codes sind nicht am echten SQL Server gemessen; die vorgesehenen Nummern 208, 207, 245 und 8115 stehen in der Spezifikation als „an der Referenzinstanz zu messen“, die Nummer für `TDS_SHUTDOWN` ist offen, und die Class-Schwelle ab 20 gilt für Class 21 bis 24 nur als Setzung. Das Mapping ist *Festlegung*, kein Messwert. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt` (Beleg dieses Slice)
- Entscheidungsbedarf (Nutzer): Zuordnung jedes Codes ohne Gruppe zu einer Class und Folge — `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT` und jeder weitere. Gesetzt sind nur die Gruppen (Class 16 offen; `TDS_PROTOCOL_ERROR`, `TDS_INTERNAL_ERROR` Class 20 schließt) und die Voreinstellung für `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE`. Der Slice entscheidet das nicht, er legt dem Nutzer eine Tabelle zur Bestätigung vor. Die Entscheidung lag zum Start nicht vor: die Codes ohne Gruppe (`TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`) stehen unter der Grundregel A (Class 20, schließt); die Tabelle zur Bestätigung ist die Zuordnung in `spec/spezifikation.md` §4. — **Ausgang:** weiter offen: → `slice-fehler-zustandsuebergang` §6 (Einzelzuordnung, seit dieser Closure eingetragen) und die Zuordnung in [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (Entscheidung des Nutzers steht aus)
- Entscheidungsbedarf (Nutzer): ob Cancel und Timeout als Fehler gelten (`SPEC-016` nennt „Fehler/Disconnect“). Bis dahin bleibt `TDS_TIMEOUT` ohne Gruppe und fällt unter Grundregel A (Class 20, schließt). — **Ausgang:** weiter offen: → [ADR-0007](../../adr/0007-fehler-und-attention.md) §Nicht entschieden und `slice-attention-cancel-timeout`
- Abstimmung mit der Welle `welle-backend-streaming`: `Failure` trägt keine Unterscheidung statement- und batch-abbrechender Fehler (R04 bis R07 der ADR-Messung); ob das Mapping sie braucht und der Port sie liefern muss, ist nicht entschieden. — **Ausgang:** weiter offen: → `slice-fehler-zustandsuebergang` §6 (statement- gegen batch-abbrechende Fehler, seit dieser Closure eingetragen); die Lücke steht auch unter den offenen Punkten des Backend-Vertrags der Spezifikation
- Die Statusbits des DONE bei Fehler liegen nur als Handvektor vor (`welle-protokollcodec-results`); ob der Referenzclient die Antwort so annimmt, ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Abweichung vom gemessenen DONE-Muster: der echte SQL Server sendet bei Class 20 und 25 ein DONE mit `ERROR` und `SRVERR` und weitere ERROR-Token ([ADR-0007](../../adr/0007-fehler-und-attention.md), Messung R10); das Mapping sendet für jeden Code nur ERROR und DONE mit `ERROR`. Ob der Referenzclient die kürzere Folge bei Class 20 annimmt, ist nicht am Produkt gemessen (der Client trennt ab Class 20 selbst, S03/S04). — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Class des Codes 208: der echte Server sendet ihn mit Class 16 (Messung R05), das Mapping mit Class 20 (Festlegung, Einzelzuordnung offen); ein Client sieht dieselbe Nummer in einer Klasse, bei der er die Verbindung selbst trennt. — **Ausgang:** weiter offen: → Zuordnung in [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) und `slice-fehler-zustandsuebergang` §6 (Entscheidung des Nutzers steht aus)
- Schnittstelle zum Sequenzierer: `ErrorMapper.sequencerMapping()` liefert dem Sequenzierer die Meldungen aus denselben Regeln; die Funktionen liefern `MappedError` (Meldung und ob die Verbindung schließt), die Verbindungsfolge eines Sequenzierer-Ausgangs liefert `ErrorMapper.fateOf(outcome)`, nicht die Severity. `ErrorMapping.internal` erhält die Ausnahme (oder `null` bei einem Reihenfolgeverstoß) und loggt über `unexpected`. Ein Integrationstest über beide Seiten ohne Fake belegt gleiche Bytes und gleiche Folge je Code. — **Ausgang:** weiter offen: → `slice-sql-batch-dispatch` (erzeugt die Antwort über `ResponseSequencer` mit `ErrorMapper.sequencerMapping()` und liest `ErrorMapper.fateOf(outcome)`) und `slice-fehler-zustandsuebergang` (führt die Folge aus); die Schnittstelle selbst ist durch den Integrationstest belegt, einen Leser in `src/main` gibt es bis dahin nicht
- Der Vollständigkeitstest der Codes (`Die Enum gleicht der im Test ausgeschriebenen Liste …`) vergleicht `ErrorCode` mit einer Liste im Test, nicht mit der Tabelle in `spec/spezifikation.md` §4; die Testumgebung enthält die Spezifikation nicht. Eine geänderte Nummer, Class oder Folge in der Tabelle lässt alle Tests grün, der Abgleich von Tabelle und Enum ist Handarbeit (zuletzt in der Verifikation vom 2026-10-30 von Hand gemacht), kein Sensor; der Test trägt deshalb kein `@spec`. — **Ausgang:** weiter offen: → `BEO-TDS/aussagen-ueber-messung-hinaus` (verwandte Klasse: eine Zusage, deren Beleg enger ist als der Text)
- Überschneidung mit `slice-handshake-login`: zwei Stellen, die ERROR 18456 serialisieren, können auseinanderlaufen. — **Ausgang:** entfallen: der Test `Die Login-Ablehnung des Mappings gleicht den Bytes des Handshakes` macht ein Auseinanderlaufen der beiden Stellen rot (Mutanten V05 und V13 der Verifikation tot); die zwei Stellen bleiben bestehen, das Risiko ist durch den Test gedeckt, nicht beseitigt

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

- **Was hat funktioniert:** Der Schnitt trug: Zuordnung, fester Text und Spezifikation blieben in einem Slice mit drei Liefer-Punkten, ohne Rückführung nach §4. Das Mapping ist rein (`protocol.error` importiert `protocol.response`, `protocol.token`, `protocol.wire`, `domain`, `transport.framing`; `protocol.response` importiert nichts aus `protocol.error`, kein Zyklus; die Schichtregel hält `make detekt`). Die Mutationen des Reviews (zehn, einzeln gefahren) und die der Verifikation, die das Mapping betreffen (V05, V09, V11a bis V13, V16), waren alle tot; die Gleichheit der Bytes mit der Login-Ablehnung des Handshake-Slice fängt ein Auseinanderlaufen der beiden Stellen.
- **Was ging anders als geplant:** (a) **Die Schnittstelle zum Sequenzierer passte nicht** (Review F-1, derselbe Fund wie im Review des Sequenzierers von der anderen Seite): `failure` und `protocol` lieferten eine fertige Nachricht, der Sequenzierer schreibt ERROR und DONE in die laufende Nachricht. Gelöst durch `ErrorMapper.sequencerMapping()` und `fateOf`, eine Quelle für Meldung und Verbindungsfolge. (b) **Der Vollständigkeitstest belegte weniger, als sein Name sagte** (F-2): er vergleicht die Enum mit einer Liste im Test, nicht mit der Tabelle in §4; die Testumgebung enthält die Spezifikation nicht. Der Name ist ehrlich, das `@spec` entfernt, die Lücke steht als Risiko in §6. (c) **Die Herkunft „Grundregel“ in §4 war breiter als der Beleg** (F-3): die Grundregel A trägt die Folge, die Class 20 der Codes ohne Gruppe ist die Wahl dieses Slice. (d) **Jeder Failure-Code wurde abgebildet, auch `TDS_LOGIN_FAILED`** (F-4): jetzt gilt eine Positivliste von sechs Backend-Codes. (e) **Die Tabelle widersprach sich zu 18456** (Verifikation V-3: „Kein Wert dieser Tabelle ist gemessen“ gegen die einmal beobachteten Werte) und der Plan führte die Lücke des Vollständigkeitstests nicht (V-4); beides in der Restrunde `d827919` nachgeführt. **Diese Restrunde hat kein Verifier nachgeprüft.**
- **Reports:** [Review](../../../reviews/2026-10-30-a8072b5-slice-fehler-mapping.md) (kein HIGH; F-1 bis F-3 MEDIUM, F-4, F-5 LOW, F-6 bis F-9 INFO) und Verifikation (Report `2026-10-30-1b5f6f4-verifikation-slice-backend-sequenzierer-und-fehler-mapping`, mit der Archivierung von welle-backend-streaming aus `docs/reviews/` entfernt, im Archiv der Welle) (gemeinsam mit `slice-backend-sequenzierer`, Stand `1b5f6f4`; kein HIGH). Stand der Befunde: F-1, F-3 bis F-6 und F-8 behoben, F-2 teilweise (Name und Tag ehrlich, die Lücke bleibt und ist als Risiko geführt), F-7 unverändert (Default `streamSynchronous = true`, kein Aufrufer außerhalb der Tests), F-9 bestätigt und in §4 benannt; V-1, V-3, V-4 laut Restrunde behoben bzw. benannt (ungeprüft). Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, der `ErrorMapper` hat keinen Leser in `src/main`.

**Zuordnung Beleg | Aussage.** Fünf Stärken: *gemessen* heißt an einem echten Client oder Server beobachtet, hier nur die Messungen aus [ADR-0007](../../adr/0007-fehler-und-attention.md) (Referenzclient gegen einen echten SQL Server 2022, Messreihe R) und eine einmalige Beobachtung aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md), nie am Produkt; *Handvektor* heißt von Hand abgeleitet; *Selbstkonsistenz* heißt Code gegen Code; *Festlegung* heißt Setzung des Nutzers oder des Slice ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (nicht am Produkt) | Class bis 19 lässt die Verbindung offen, ab 20 schließt der Server; bei Class 20 und 25 sendet der echte Server ein DONE mit `ERROR` und `SRVERR` und weitere ERROR-Token; die Nummer 208 sendet er mit Class 16 (R05, R10); 18456 mit State 1 und Class 14 einmal beobachtet | [ADR-0007](../../adr/0007-fehler-und-attention.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen; das Mapping weicht in zwei Punkten ab (nur ERROR; 208 mit Class 20) und sagt es in [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) |
| Handvektor | ERROR- und DONE-Bytes je Code (elf), Texte, Nummern, Class, State | `ErrorMapperTest` (`@spec`-Tags je Code); von Hand abgeleitet, ein Bericht über Nachrechnen mit anderem Werkzeug liegt nicht vor |
| Selbstkonsistenz | Gleichheit der Bytes des Mappings mit `LoginResponses.rejection` (zwei Stellen derselben Autorenschaft); Sequenzierer und Mapper liefern gleiche Bytes und gleiche Folge je Backend-Code; die Enum gleicht einer Liste im Test | `ErrorMapperTest`, `ResponseErrorMapperTest`; die Liste im Test ist eine Kopie, kein Abgleich mit §4 |
| Selbstkonsistenz (Verhalten) | Ausnahmetext, Failure-Text und fremder Code erreichen weder Antwort noch Log (ASCII und UTF-16LE, Log nur Typname); unbekannter Failure-Code und Code außerhalb der Positivliste werden zu `TDS_INTERNAL_ERROR`; jeder Einstieg schließt bei nicht synchronem Strom ohne Antwort; `fateOf` ist total | `ErrorMapperTest`; Mutanten: Review zehn einzeln, Verifikation zehn, alle tot |
| Festlegung (Nutzerentscheidung 2026-10-30) | Gruppen: Class bis 19 offen; `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` mit Class 20 und Schließen; Voreinstellung für `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE` (Class 16, offen) | [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), KDoc von `ErrorCode`; Gruppentest und Schwellentest |
| Festlegung (Grundregel A) | Die Folge „schließt“ der Codes ohne Gruppe | [ADR-0007](../../adr/0007-fehler-und-attention.md) Grundregel A; Handvektor-Tests |
| Festlegung (Slice, vom Nutzer nicht bestätigt) | Class 20 für `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`; Nummern 50001 bis 50008 und alle festen Texte; Nummern 208 und 245 als Vorschlag; Positivliste von sechs Backend-Codes; Log nur mit Typname (enger als `SPEC-048`) | §4 mit „Einzelzuordnung offen“, KDoc von `ErrorCode`; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` |
| nicht gemessen | Annahme der Antwort (ERROR und DONE nur mit `ERROR`) durch den Referenzclient bei Class 20 und 25; Nummern, States und Texte am Produkt; Class 21 bis 24 (Setzung der Schwelle) | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft; „Nutzerentscheidung“ ist kein Träger):

| Offener Punkt | Träger | Stand |
|---|---|---|
| `ErrorMapper` (`sequencerMapping`, `fateOf`) hat keinen Leser in `src/main` | `slice-sql-batch-dispatch` (`open/`) und `slice-fehler-zustandsuebergang` (`open/`) | DoD-Zeile „Verdrahtung des Fehler-Mappings“ im Dispatch-Slice; §6 im Zustandsslice |
| Statement- gegen batch-abbrechende Fehler | `slice-fehler-zustandsuebergang` (`open/`) | §6, mit dieser Closure eingetragen |
| Einzelzuordnung von `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT` und Class von 208 (Entscheidung des Nutzers) | `slice-fehler-zustandsuebergang` (`open/`) und Tabelle in §4 der Spezifikation | §6, mit dieser Closure eingetragen |
| Cancel und Timeout als Fehler | `slice-attention-cancel-timeout` (`open/`) | §6 und DoD nennen die Entscheidung und die Folge für `TDS_TIMEOUT` |
| DONE mit `ERROR` und `SRVERR` bei Class 20 und 25, Class 21 bis 24 | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (*geplant*, `slice-qa-vergleichslauf-d`) | die DoD des Vergleichslaufs nennt die DONE-Statusbits allgemein, `SRVERR` und Class 21 bis 24 nicht ausdrücklich; kein Slice trägt sie |
| Abgleich der Tabelle in §4 mit `ErrorCode` nicht maschinell | Register `BEO-TDS/aussagen-ueber-messung-hinaus` | Handarbeit, kein Sensor; ein Abgleich in der Stage mit Kopie der Spezifikation ist nicht gebaut |
| Geheimnisfreiheit des `detail` eines `ProtocolError` hängt am Mapper | Register `BEO-TDS/aussagen-ueber-messung-hinaus` | der Mapper liest `detail` nicht; ein Test, der einen Mapper mit Übernahme in beiden Pfaden rot macht, existiert nicht |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Vier Feststellungen. (1) **Gelernt: Ein Test, dessen Name eine Prüfung gegen die Spezifikation behauptet, belegt sie nur, wenn die Testumgebung die Spezifikation enthält.** Die Stage `test` kopiert nur `src`; der Vollständigkeitstest prüfte eine Kopie, und eine geänderte Nummer in §4 ließ alles grün. Die Lücke ist benannt (Name, Tag, Risiko in §6), der Abgleich bleibt Handarbeit; ein Sensor in der Stage `spec-abdeckung` wäre die Lösung, ist hier nicht gebaut. Klasse `BEO-TDS/aussagen-ueber-messung-hinaus`. (2) **Die Herkunftsstufen wurden an zwei Orten verschieden benannt, und „Grundregel“ deckte mehr als die Folge** (F-3, F-5, V-3): ein weiteres Auftreten derselben Klasse, trotz der verkörperten Regel (`AGENTS.md` §3.8 war der Maßstab), keine Schärfung. (3) **Schwellen erreicht, nicht verkörpert:** `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` hat mit diesem Slice drei Belege (`slice-transport-listener-lebenszyklus`, `slice-backend-sequenzierer`, `slice-fehler-mapping`), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` vier. Eine Regel ist zu keiner geschrieben; die Wellen laufen, also liegt der Lese-Schritt bei der nächsten Welle-Closure (`welle-fehler-abbruch` oder `welle-backend-streaming`, wer zuerst schließt), und die `state.md` bleiben `offen`. Kandidat für die Regel zu `zeile-oder-zweig-ohne-unterscheidenden-test`, keine Setzung: vor dem Handoff je Zweig mit Wirkung eine Mutation fahren (Zeile entfernen, ein Test rot). (4) **Benannte Spec-Lücke:** die Class der Codes ohne Gruppe und die Frage, ob Cancel und Timeout Fehler sind; sie stehen in [§4 der Spezifikation](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) als „Einzelzuordnung offen“ und sind damit in einer versionierten Spec verkörpert. Dazu: **die Restrunde ohne Verifier** ist in DoD und Notiz offen als solche geführt.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-fehler-mapping.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-2, F-3, F-5; Verifikation V-3, V-4), `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (Review F-6), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (Review F-3) und `BEO-TDS/offene-punkte-ohne-traeger` (die Träger zu statement- gegen batch-abbrechend und zur Einzelzuordnung standen in keiner Slice-Datei). Kein neues Auftreten: `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt` (derselbe Fund wie im Sequenzierer-Slice, dort einmal gezählt), `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (die Login-Ablehnung ist nur lesend aufgenommen, der Gleichheitstest hält sie), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` und die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle zehn tragen genau einen Ausgang (siehe §6): neun weiter offen mit Träger oder Register-Eintrag (Messung der Nummern, Einzelzuordnung, Cancel und Timeout, statement- gegen batch-abbrechend, Statusbits, Abweichung vom DONE-Muster, Class von 208, Schnittstelle zum Sequenzierer, Vollständigkeitstest), eines entfallen (Überschneidung mit dem Login: der Gleichheitstest deckt es). Kein Risiko ist eingetreten, das einen Carveout verlangt.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-sql-batch-dispatch`, `slice-fehler-zustandsuebergang` und `slice-attention-cancel-timeout` existieren als Dateien und führen die Punkte (gegen den Inhalt geprüft). Register: fünf Evidence-Dateien in vorhandenen Beobachtungen, siehe oben.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Packages (`protocol`, `transport`, `runtime`) sind Pfad-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, Stand *geplant* (Träger `slice-qa-reproduzierbarkeit`). Treffer: Nummern, State, Texte und Class-Werte der eigenen Codes sind ungemessen (§6). Ein weiterer Beleg kommt bei der Closure.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, Stand *verkörpert* (`AGENTS.md` §3.8). Treffer: Jede Zeile der Zuordnungstabelle trägt *gemessen* oder *Setzung* (§2).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `TDS-*`- und `ADR-*`-Kennungen stehen in diesem Plan als Link.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Treffer: die Login-Ablehnung ist im Mapping nur lesend aufgenommen; die Byte-Gleichheit bleibt Vorleistung des Handshake-Slice.
- Ohne Treffer: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
