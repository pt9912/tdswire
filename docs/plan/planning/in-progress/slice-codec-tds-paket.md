# Slice slice-codec-tds-paket: TDS-Paketheader, Paket-Codec und Token-Grundstock ohne Netzwerk

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-protokollcodec

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-003 · SPEC-006 · SPEC-007 · SPEC-011 · SPEC-024 · SPEC-027 · SPEC-028 · SPEC-030 · SPEC-041 · SPEC-046 · ARC-003 · ARC-007

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein reiner Codec ohne Netzwerk liest und schreibt TDS-Pakete (Header, Fragmentierung, Packet-Size, Status-Bits) und die Nachrichten, die Verbindungsaufbau, Login, SQL_BATCH und RPC auf der Antwortseite brauchen (PRELOGIN-Optionen, LOGIN7-Felder, LOGINACK, ENVCHANGE, INFO, ERROR, DONE-Familie, COLMETADATA, ROW, RETURNSTATUS, RETURNVALUE); ein neues Test-Gate (`make test`) belegt das.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Netzwerk, Sockets, `SSLEngine`, Coroutinen-I/O und die Aufnahme von `kotlinx-coroutines-core` — der Codec arbeitet auf Bytes im Speicher; die Laufzeit nach [ADR-0003](../../adr/0003-transportframework.md) kommt mit dem Transport-Code, der I/O macht (siehe §6).
- Handshake- und Session-Ablauf, Verbindungs-Zustandsautomat (SPEC-001), TLS-Kapselung in Paketen vom Typ `0x12` — das ist Ablauf, nicht Codec; der Zustandsautomat ist [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Proposed`). Der Codec kennt `0x12` nur als Pakettyp.
- Parser der Anfrageseite von SQL_BATCH und RPC (ALL_HEADERS, `sp_executesql` und folgende, SPEC-030) — der Katalog ist [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (`Accepted`), sein Parser aber eine eigene Lieferung der funktionalen Welle; im Codec stehen nur die Antwort-Tokens `RETURNSTATUS` und `RETURNVALUE`.
- Abbildung zwischen Backend-Ereignissen (`QueryEvent`, `SqlType`) und Tokens — der Backend-Vertrag ist [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) (`Proposed`); der Codec hat ein eigenes Token-Modell und legt nichts in den Packages `domain` oder `port` an.
- Fehlerabbildung und ATTENTION-Semantik — der Codec serialisiert Nummer, Severity, State und Meldung, die man ihm gibt, und setzt das ATTN-Bit, wenn man es ihm sagt; die Zuordnung steht in [ADR-0007](../../adr/0007-fehler-und-attention.md) (`Proposed`).
- Authentifizierung: Adapter-Signatur, Prüfung von Kennung und Passwort, die feste Ablehnungsantwort (SPEC-012) — der LOGIN7-Parser liefert Felder und entscheidet nichts ([ADR-0009](../../adr/0009-authentifizierungsmodus.md), `contracts/auth-adapter.md`).
- NBCROW, PLP (MAX-Typen) in ROW und Typen außerhalb des Mindestkatalogs von SPEC-003 (`text`, `ntext`, `image`, `sql_variant`, UDT, `json`, Table-Valued-Parameter) — das Clientprofil sendete sie in den gemessenen Szenarien nicht oder sie sind ungemessen.
- Läufe gegen den Referenzclient und gegen einen echten SQL Server, Mitschnitte — sie gehören in das Kompatibilitäts- und Referenzabgleich-Gate (SPEC-032, SPEC-050) der Welle `welle-qualitaet-grundlage`; dieser Slice belegt den Codec, nicht die Kompatibilität.
- Das Abdeckungs-Dokument zu den `SPEC-*` und sein Erzeuger — das liefert `slice-qa-spec-abdeckung`; dieser Slice nutzt nur dessen Tag-Konvention (§2). Ebenso bleibt die Kompatibilitätsmatrix bei `slice-qa-wartbarkeit`.
- Folge-Slices für die ausgeschlossenen Gegenstände sind noch nicht angelegt; die Closure der Welle legt sie an oder begründet, warum nicht.


## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Test-Gate** (SPEC-024, [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): `make test` führt die Unit-Tests in Docker aus (Dockerfile-Stage `test`, `tools/mk/test.mk`, nur `kotlin.test`), ist Teil von `make gates` und hat seine Zeile in `harness/README.md` §Sensors; die neuen Abhängigkeiten stehen in `gradle/libs.versions.toml`, `gradle.lockfile` und `gradle/verification-metadata.xml`. Belegt ist, dass ein roter Test `make gates` rot macht (ein absichtlich roter Lauf, im Closure-Bericht).
- [ ] **Paket-Codec** im Package `transport` (ARC-003, SPEC-006, SPEC-007, SPEC-028, SPEC-046): 8-Byte-Header lesen und schreiben (Pakettyp, Status-Bits, Länge Big-Endian, Packet-ID mit Überlauf), Längenprüfung gegen Untergrenze und konfigurierte Obergrenze, Zusammensetzen beliebig zerstückelter Eingabe bis zum EOM-Status mit Abbruch, sobald die Summe der Paketlängen die Nachrichtengrenze überschreitet und bevor die Nachricht gepuffert ist, und Zerlegen einer Nachricht in Pakete bis zur Packet-Size. Fehler sind Ergebniswerte mit Kategorie (SPEC-011), keine ungefangene Ausnahme.
- [ ] **Nachrichten-Codec** im Package `protocol` (ARC-007, SPEC-003, SPEC-028, SPEC-030, [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)): Encoder und Decoder der Antwort-Tokens LOGINACK, ENVCHANGE (Datenbank, Packet Size, Collation Typ 7), INFO, ERROR, DONE/DONEPROC/DONEINPROC mit Statusbits und Zeilenzahl, COLMETADATA und ROW mit TYPE_INFO des Mindestkatalogs (SPEC-003), RETURNSTATUS, RETURNVALUE; PRELOGIN (Anfrage lesen, Antwort schreiben, Offset-/Längen-Tabelle samt Terminator geprüft); LOGIN7-Parser-Grundlage (Kopf, Offset-/Längen-Tabelle, UTF-16-Felder, Entschleierung des Passworts, Erkennung von `fIntSecurity`, `fChangePassword` und FeatureExt-Option `0x02`). Das Passwort erscheint in keiner Textdarstellung des Parse-Ergebnisses.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage des Codecs steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements): ein Byte-Vektor je Token, von Hand aus MS-TDS abgeleitet, ist das Orakel; ein Roundtrip aus Encoder und Decoder allein belegt Selbstkonsistenz, keine Protokolltreue. Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (Register, `BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [ ] Ein gemeinsamer Test lässt eine Nachrichtenfolge (PRELOGIN-Anfrage und -Antwort, LOGIN7 und die Login-Antwort-Tokens, ein Resultset mit RETURNSTATUS) durch Nachrichten-Codec und Paket-Codec hin und zurück laufen, byteweise zerstückelt (Smoke der Welle).
- [ ] Zerstückelte, überlange, abgeschnittene und zufällig erzeugte Eingaben (fester Startwert) enden in einem Ergebniswert, nie in einer ungefangenen Ausnahme (SPEC-027, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)); das ist der Codec-Anteil, nicht das Fuzz-Gate.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Sensors für `make test` ist Pflicht; `contracts/` bleibt unverändert, solange die Codec-Festlegungen nichts am Vertrag ändern).
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
| `Dockerfile` | update | Stage `test` auf `deps` (nur `src/` hinzu); der Kopfkommentar „Einen Test-Stage gibt es noch nicht“ wird nachgezogen |
| `build.gradle.kts`, `gradle/libs.versions.toml` | update | `kotlin.test` mit JUnit-Plattform als Testlaufzeit; Versionen nur in der Katalogdatei |
| `gradle.lockfile`, `gradle/verification-metadata.xml` | update | per `make deps-lock` (braucht Netz) neu geschrieben, nicht von Hand |
| `tools/mk/test.mk` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Target `test`, `GATE_CHECKS += test`; `DEPS_TASKS` in `tools/mk/build.mk` um `test` ergänzen |
| `harness/README.md` | update | Zeile für `make test` in §Sensors (sonst meldet das Doku-Gate `gate-undocumented`) |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/` (Unterpackage, z. B. `framing`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Paketheader, Pakettyp, Status-Bits, Paket-Leser mit Zusammensetzen, Paket-Schreiber mit Fragmentierung; kein `java.nio.channels`, keine Imports aus `port`, `simulation`, `runtime` (SPEC-041) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackages, z. B. `prelogin`, `login7`, `token`) <!-- d-check:ignore (geplant: neue Packages) --> | neu | Nachrichten-Codec mit eigenem Token-Modell; keine Imports aus `simulation`, `runtime`; keine Abhängigkeit auf `transport`, weil beide Seiten über `ByteArray`-Nachrichten sprechen |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/` <!-- d-check:ignore (geplant: neues Package) --> | neu | Happy/Boundary/Negative und Zerstückelung mit festem Startwert, nach SPEC-006, SPEC-007, SPEC-027, SPEC-046; `@spec`-Tag je Test |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` <!-- d-check:ignore (geplant: neues Package) --> | neu | Byte-Vektoren je Token aus MS-TDS, Roundtrip, Negativfälle der Offset-/Längen-Tabellen, kein Passwort in der Textdarstellung (TDS-QA-03), gemeinsamer Smoke mit dem Paket-Codec; nach SPEC-003, SPEC-028, SPEC-030 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: Test-Gate zuerst (ein erster trivialer Test genügt, damit die Stage etwas ausführt), dann Paket-Codec, dann Nachrichten-Codec. `make detekt` läuft auch über `detektTest`; jeder Befund in neuem Code wird im Code behoben, nie unterdrückt oder in `config/detekt/detekt.yml` gelockert (`AGENTS.md` §3.2, §3.6).
- Die Layout-Quelle ist MS-TDS (SPEC-020, extern). Wo die Spezifikation eine Kennung offen lässt (SPEC-003: „Token-/Typkennungen … zu verifizieren“), nimmt der Implementer sie aus MS-TDS und kennzeichnet sie im KDoc als Festlegung.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-protokollcodec` ist eingetreten (`make gates` auf `main` grün; die fünf ADRs des Bezugs stehen im ADR-Index auf `Accepted`) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder der Nachrichten-Codec sprengt den Rahmen. Schnittvorschlag für diesen Fall: Slice A = Test-Gate und Paket-Codec, Slice B = Nachrichten-Codec; die Welle bekommt dann zwei Slices.
- `in-progress` → `open` (blockiert — Carveout?): `make deps-lock` scheitert oder `kotlin.test` samt Testlaufzeit lässt sich nicht per Lock und Prüfsumme fixieren, sodass das Test-Gate nicht reproduzierbar ist.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage des Codecs sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Es gibt kein Test-Gate; `kotlin.test`, die Testlaufzeit und ihre Prüfsummen sind neue Abhängigkeiten, die `make deps-lock` (Netz) erst schreiben muss, und der Build läuft danach nur noch mit dem Lock. — **Ausgang:** offen bis zur Closure.
- Überschneidung mit `slice-qa-wartbarkeit`: dessen erster Liefer-Punkt ist ebenfalls das Unit-Test-Gate (SPEC-024). Wer zuerst schließt, liefert es; der andere Plan wird auf den Rest gekürzt (Kompatibilitätsmatrix, Modultrennung). Dieser Plan ändert `slice-qa-wartbarkeit` nicht. — **Ausgang:** offen bis zur Closure.
- Der Codec belegt Selbstkonsistenz und MS-TDS-Vektoren, nicht die Annahme durch den Referenzclient: Mitschnitte und Spike-Code liegen nicht im Repo, gemessen ist nur gegen einen Testserver in TDS 7.4 ohne TLS und Pool, und ein echter SQL Server ist nicht gemessen (Tokenreihenfolge, ordinale Zählung der RETURNVALUE, DONE-Statusbits). Eine Aussage wie „der Client akzeptiert das“ darf in Code, KDoc und Notiz nicht stehen. — **Ausgang:** offen bis zur Closure; Träger der Messung sind `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit` (`BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
- Behauptungen über das Gemessene hinaus (`BEO-TDS/aussagen-ueber-messung-hinaus`, bisher 6×): ein Implementierungs-Slice wiederholt das Muster, wenn KDoc oder Notiz einen Test oder eine Festlegung nur behaupten. Gegenmittel in §2: jede Aussage an einem Test oder als Festlegung gekennzeichnet. — **Ausgang:** offen bis zur Closure.
- Das LOGIN7-Parse-Ergebnis trägt das Passwort; eine automatisch erzeugte Textdarstellung (`toString` einer `data class`) brächte es in ein Log. Ein Test belegt, dass keine Textdarstellung es enthält ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). Die Kotlin-Signatur des Adapters bleibt offen (`BEO-TDS/adapter-festlegungen-ohne-slice`). — **Ausgang:** offen bis zur Closure.
- Die Schichtgrenzen belegt `make detekt` nur über `import`-Zeilen (SPEC-041); `protocol` darf nach der Konfiguration `transport` importieren, die Architektur verbietet ihm dessen Implementierung. Der Plan hält beide Seiten importfrei voneinander. — **Ausgang:** offen bis zur Closure.
- Strenge der statischen Analyse: ein Token-Decoder neigt zu großen `when`-Blöcken, die `CognitiveComplexMethod`, `ComplexInterface` und die Größenregeln treffen; die Abhilfe ist Zerlegung im Code. — **Ausgang:** offen bis zur Closure.
- `kotlinx-coroutines-core` kommt nach [ADR-0003](../../adr/0003-transportframework.md) „sobald der erste Transport-Code entsteht“ in den Katalog. Dieser Plan liest das als „sobald Code mit I/O entsteht“; der Paket-Codec liegt im Package `transport`, hat aber keine I/O. — **Ausgang:** offen bis zur Closure (Nutzerentscheidung nötig, falls anders gelesen).
- Größe: drei Liefer-Punkte, davon der Nachrichten-Codec mit einem Dutzend Nachrichtenformen. — **Ausgang:** offen bis zur Closure; Rückführung und Schnitt in §4.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die Pfade `transport` und `protocol` sind ein Pfad-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage` (deren Closure schreibt die Regel). Betrifft diesen Slice als Implementierungs-Slice: jede Behauptung trägt einen Test oder ist als Festlegung gekennzeichnet (§2, §6). Kein weiterer Folge-Slice; die Closure-Notiz hält fest, ob das Muster wieder auftrat.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 2×, offen. Betrifft Tokenreihenfolge, RETURNVALUE-Zählung und DONE-Statusbits des Codecs (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 2×, offen. Betrifft die Aussage, dass der Codec für beide Protokollpfade gilt: nicht gemessen, nur MS-TDS-gestützt (§6).
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Betrifft das Passwort im LOGIN7-Parse-Ergebnis (§6); Zeitgleichheit und Prüfwertformat bleiben bei `slice-qa-sicherheit`.
- `BEO-TDS/adapter-festlegungen-ohne-slice` — 1×, offen. Betrifft die offene Adapter-Signatur; der Slice legt sie nicht fest.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft dieses Dokument und die Closure-Notiz: `ADR-*` und `TDS-*` stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
