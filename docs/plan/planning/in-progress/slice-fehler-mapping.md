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

- [ ] Mapping-Funktion ohne Netz und ohne Schreiben: Jeder eigene Code (`SPEC-011` bis `SPEC-016`, `SPEC-037` bis `SPEC-039`, `SPEC-048`, `SPEC-049`; `TDS_LOGIN_FAILED` nur lesend) liefert Nummer, Class, State, festen Text und Verbindungsfolge (offen · schließt nach ERROR und DONE · schließt ohne Antwort). Ein Test je Code gegen Handvektoren der Bytes; ein Vollständigkeitstest scheitert, sobald ein Code ohne Zuordnung existiert. ([TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), Negative)
- [ ] Eine Ausnahme ohne Code erreicht den Client nur als `TDS_INTERNAL_ERROR` mit festem Text; Test: ein Ausnahmetext mit einem Konfigurationswert steht weder in den Antwortbytes noch im Log-Eintrag, der Log-Eintrag trägt keine Parameterwerte ([TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) Nr. 5, `SPEC-048`).
- [ ] `spec/spezifikation.md` fortgeschrieben: Zuordnungstabelle je Code (Nummer, Class, Folge), jede Zeile als *gemessen* oder *Setzung* gekennzeichnet; die Aussage „Die Zuordnung jedes Codes legt das Fehler-Mapping fest“ in [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) ist erfüllt oder nennt den verbleibenden Rest.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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
- Entscheidungsbedarf (Nutzer): Zuordnung jedes Codes ohne Gruppe zu einer Class und Folge — `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT` und jeder weitere. Gesetzt sind nur die Gruppen (Class 16 offen; `TDS_PROTOCOL_ERROR`, `TDS_INTERNAL_ERROR` Class 20 schließt) und die Voreinstellung für `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE`. Der Slice entscheidet das nicht, er legt dem Nutzer eine Tabelle zur Bestätigung vor. Die Entscheidung lag zum Start nicht vor: die Codes ohne Gruppe (`TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`) stehen unter der Grundregel A (Class 20, schließt); die Tabelle zur Bestätigung ist die Zuordnung in `spec/spezifikation.md` §4. — **Ausgang:** weiter offen: → Zuordnung in `spec/spezifikation.md` §4 (Entscheidung des Nutzers steht aus)
- Entscheidungsbedarf (Nutzer): ob Cancel und Timeout als Fehler gelten (`SPEC-016` nennt „Fehler/Disconnect“). Bis dahin bleibt `TDS_TIMEOUT` ohne Gruppe und fällt unter Grundregel A (Class 20, schließt). — **Ausgang:** weiter offen: → [ADR-0007](../../adr/0007-fehler-und-attention.md) §Nicht entschieden und `slice-attention-cancel-timeout`
- Abstimmung mit der Welle `welle-backend-streaming`: `Failure` trägt keine Unterscheidung statement- und batch-abbrechender Fehler (R04 bis R07 der ADR-Messung); ob das Mapping sie braucht und der Port sie liefern muss, ist nicht entschieden. — **Ausgang:** offen bis zur Closure
- Die Statusbits des DONE bei Fehler liegen nur als Handvektor vor (`welle-protokollcodec-results`); ob der Referenzclient die Antwort so annimmt, ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Abweichung vom gemessenen DONE-Muster: der echte SQL Server sendet bei Class 20 und 25 ein DONE mit `ERROR` und `SRVERR` und weitere ERROR-Token ([ADR-0007](../../adr/0007-fehler-und-attention.md), Messung R10); das Mapping sendet für jeden Code nur ERROR und DONE mit `ERROR`. Ob der Referenzclient die kürzere Folge bei Class 20 annimmt, ist nicht am Produkt gemessen (der Client trennt ab Class 20 selbst, S03/S04). — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Class des Codes 208: der echte Server sendet ihn mit Class 16 (Messung R05), das Mapping mit Class 20 (Festlegung, Einzelzuordnung offen); ein Client sieht dieselbe Nummer in einer Klasse, bei der er die Verbindung selbst trennt. — **Ausgang:** weiter offen: → Zuordnung in `spec/spezifikation.md` §4 (Entscheidung des Nutzers steht aus)
- Schnittstelle zum Sequenzierer: `ErrorMapper.sequencerMapping()` liefert dem Sequenzierer die Meldungen aus denselben Regeln; die Verbindungsfolge einer Meldung liefert `ErrorMapper.fateOf`, nicht die Severity. `ErrorMapping.internal` ist ein Wert, das Log dieses Falls schreibt der Aufrufer. Ein Integrationstest über beide Seiten ohne Fake belegt gleiche Bytes und gleiche Folge je Code. — **Ausgang:** weiter offen: → `slice-fehler-zustandsuebergang` (der Aufrufer, der `fateOf` liest, entsteht dort); die Schnittstelle selbst ist durch den Integrationstest belegt
- Überschneidung mit `slice-handshake-login`: zwei Stellen, die ERROR 18456 serialisieren, können auseinanderlaufen. — **Ausgang:** offen bis zur Closure (Gegenmittel: der Gleichheitstest der Bytes)

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

Wird bei der Closure gefüllt.

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
