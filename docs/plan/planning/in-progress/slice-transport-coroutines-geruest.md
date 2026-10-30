# Slice slice-transport-coroutines-geruest: Coroutine-Gerüst: kotlinx-coroutines-Pin, Kanal-Abstraktion und Nachrichtenleser über dem Paket-Codec

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-transport-tls

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-007 · SPEC-024 · SPEC-027 · SPEC-046 · ARC-003 · ARC-013

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Transport hat eine suspendierende Kanal-Abstraktion über `AsynchronousSocketChannel` und einen Nachrichtenleser und -schreiber, der beliebig zerstückelte Bytes mit dem Paket-Codec aus `slice-codec-tds-paket` zu Nachrichten zusammensetzt und Nachrichten rückstaufest schreibt; `kotlinx-coroutines-core` ist per Lock und Prüfsumme gepinnt. Ein Loopback-Test belegt das in Docker.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `SSLEngine`, TLS-Handshake und die Kapselung in Paketen vom Typ `0x12` — das ist `slice-transport-tls-schleife`; dieser Slice liefert nur den Kanal, auf dem sie aufsetzt ([ADR-0002](../../adr/0002-tls-und-verschluesselung.md)).
- `AsynchronousServerSocketChannel`, Annahmeschleife, Verbindungs-Scopes, Timeouts und Shutdown — das ist `slice-transport-listener-lebenszyklus`; hier gibt es nur einen verbundenen Kanal.
- Verbindungs-Zustandsautomat, PRELOGIN-/LOGIN7-Ablauf und jede Entscheidung, welche Nachricht in welchem Zustand zulässig ist — der Zustandsautomat ist [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Proposed`); der Leser liefert Nachrichten (Typ und Bytes) und urteilt nicht über deren Reihenfolge.
- Inhalt und Struktur der Nachrichten (Token-Codec, PRELOGIN-Tabelle): der Leser reicht Bytes weiter und kennt keine Tokens.
- Neue Paket-Codec-Funktionen: Header, Fragmentierung und Grenzprüfung kommen aus `slice-codec-tds-paket` und werden nicht dupliziert; fehlt dort etwas, ist das eine Rückmeldung an diesen Slice, kein Nachbau.
- Lasttests, Latenz-Perzentile und mehr als einige Dutzend gleichzeitige Verbindungen — [ADR-0003](../../adr/0003-transportframework.md) nennt sie als nicht gemessen; sie gehören in `slice-qa-performance`.
- Netty, Java-NIO-Selektoren und alle anderen Laufzeiten — die Wahl ist getroffen.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Abhängigkeit** ([ADR-0003](../../adr/0003-transportframework.md), SPEC-042, [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): `kotlinx-coroutines-core` steht in `gradle/libs.versions.toml`, `gradle.lockfile` und `gradle/verification-metadata.xml` (geschrieben von `make deps-lock`, nicht von Hand); `make build`, `make detekt` und das Test-Gate laufen mit dem Lock. Die Version nennt der Slice in §7; der Spike maß 1.10.1 (Frage an den Nutzer, siehe §6).
- [ ] **Kanal-Abstraktion** im Package `transport` (ARC-003, ARC-013): suspendierende Schnittstelle für Lesen in einen Puffer, vollständiges Schreiben und Schließen; Adapter für `AsynchronousSocketChannel` (Abbruch der Coroutine schließt den Kanal, Lese- und Schreibfehler sind Ergebniswerte mit Kategorie nach SPEC-011, keine ungefangene Ausnahme) und ein Speicher-Kanal für Tests ohne Netz.
- [ ] **Nachrichtenleser und -schreiber** (SPEC-007, SPEC-046, SPEC-027): der Leser setzt über den Kanal beliebig zerstückelte Eingabe mit dem Paket-Codec zu Nachrichten zusammen und bricht ab, bevor die Nachricht über der Grenze gepuffert ist (Grenze als Parameter: SPEC-046 vor dem Login, SPEC-007 danach); der Schreiber zerlegt eine Nachricht in Pakete der Packet-Size und schreibt suspendierend, sodass kein Schreibpfad unbegrenzt puffert.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Loopback-Test (`127.0.0.1`, Docker-Container, kein Mount): Nachrichten werden byteweise und in zufälligen Stücken (fester Startwert) gesendet und kommen vollständig und gleich an; eine Nachricht über der Grenze beendet den Leser mit einem Ergebniswert, und der Test belegt über die Zahl der gelesenen Bytes, dass nicht erst vollständig gepuffert wurde.
- [ ] Rückstau-Test mit einem langsamen Leser: Der Schreiber suspendiert, und die Zahl der im Speicher gehaltenen Bytes bleibt unter einer im Test genannten Grenze. Gemessen ist das am Kanal des Tests, nicht an 100 MiB wie im Spike; eine größere Aussage steht nicht in KDoc oder Notiz.
- [ ] Abgeschnittene, überlange und zufällig erzeugte Eingaben sowie ein abrupt geschlossener Kanal enden in einem Ergebniswert; der Testprozess bleibt am Leben ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Sensors nur, falls ein neues Target entsteht — geplant ist keines; `contracts/` bleibt unverändert).
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
| `gradle/libs.versions.toml`, `build.gradle.kts` | update | `kotlinx-coroutines-core` als `implementation`; Version nur in der Katalogdatei |
| `gradle.lockfile`, `gradle/verification-metadata.xml` | update | per `make deps-lock` (braucht Netz) neu geschrieben, nicht von Hand |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/` (Unterpackage, z. B. `channel`) <!-- d-check:ignore (geplant: neue Datei) --> | neu | Kanal-Schnittstelle, Adapter für `AsynchronousSocketChannel`, Speicher-Kanal, Nachrichtenleser und -schreiber; keine Imports aus `port`, `simulation`, `runtime` (SPEC-041) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Loopback, Zerstückelung mit festem Startwert, Grenze vor dem Puffern, Rückstau, abrupter Abbruch; `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: Pin zuerst (ein Test, der `runBlocking` aufruft, belegt, dass Lock und Prüfsumme greifen), dann Kanal, dann Leser und Schreiber.
- `make detekt` läuft auch über `detektTest`; Befunde zu Coroutinen (z. B. `GlobalCoroutineUsage`, `InjectDispatcher`) werden im Code behoben, nie unterdrückt oder in `config/detekt/detekt.yml` gelockert (`AGENTS.md` §3.2, §3.6).
- Die API des Paket-Codecs steht beim Schreiben dieses Plans nicht fest (`slice-codec-tds-paket` ist in Arbeit); der Implementer liest sie aus `done/` und passt §3 an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-transport-tls` ist eingetreten (`slice-codec-tds-paket` liegt in `done/`, `make gates` auf `main` grün) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder Leser und Schreiber sprengen den Rahmen. Schnittvorschlag: Slice A1 = Pin und Kanal, Slice A2 = Nachrichtenleser und -schreiber; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): `make deps-lock` scheitert oder die Abhängigkeit lässt sich nicht per Lock und Prüfsumme fixieren, sodass der Build nicht reproduzierbar ist.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Zeitpunkt des Pins: [ADR-0003](../../adr/0003-transportframework.md) sagt „sobald der erste Transport-Code entsteht“; `slice-codec-tds-paket` las das als „sobald Code mit I/O entsteht“ und nahm die Abhängigkeit nicht auf. Dieser Plan nimmt sie in den ersten Slice mit I/O. Entschieden (Nutzer, 2026-10-30): Pin in diesem Slice, Version 1.10.1. — **Ausgang:** offen bis zur Closure.
- Das Test-Gate (`make test`) kommt aus `slice-codec-tds-paket`; liegt es beim Start nicht in `done/`, ist der Start-Trigger nicht erfüllt, und dieser Slice liefert kein eigenes zweites Gate. — **Ausgang:** offen bis zur Closure.
- Loopback-Sockets im Docker-Build: Der Test braucht `127.0.0.1` im Build-Container. Das ist ohne Netzfreigabe zu erwarten, aber hier nicht gemessen; scheitert es, ist der Test auf den Speicher-Kanal zu begrenzen, und der Slice kann den Adapter nicht belegen. — **Ausgang:** offen bis zur Closure.
- Rückstau ist im Spike für 100 MiB gemessen und im Produktivcode „zu liefern“ ([ADR-0003](../../adr/0003-transportframework.md)); dieser Slice belegt eine kleinere Grenze am Test-Kanal, nicht den Produktivbetrieb unter Last. — **Ausgang:** offen bis zur Closure.
- Der Leser muss die Grenzprüfung vor dem Puffern auslösen, die der Paket-Codec als Funktion anbietet; weichen beide in der Auslegung ab (Summe der Paketlängen gegen Nachrichtenlänge), entsteht ein Doppelbefund. Gegenmittel: der Leser ruft die Prüfung des Codecs, er rechnet nicht selbst. — **Ausgang:** offen bis zur Closure.
- Strenge der statischen Analyse: Coroutinen-Code erzeugt Befunde zu Komplexität und Größe; die Abhilfe ist Zerlegung im Code. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `transport` ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage`. Betrifft diesen Slice als Implementierungs-Slice: jede Aussage trägt einen Test oder ist als Festlegung gekennzeichnet (§2, §6). Kein weiterer Folge-Slice; die Closure-Notiz hält fest, ob das Muster wieder auftrat.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 2×, offen. Betrifft den Slice nur mittelbar: er macht keine Aussage über einen echten SQL Server.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft dieses Dokument und die Closure-Notiz: `ADR-*` und `TDS-*` stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`; `tds-8-0-und-pooling-nicht-gemessen`, `anmeldepfad-sicherheit-nicht-gebaut` und `adapter-festlegungen-ohne-slice` berühren den Kanal nicht.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
