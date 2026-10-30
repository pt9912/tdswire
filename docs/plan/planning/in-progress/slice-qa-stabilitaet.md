# Slice slice-qa-stabilitaet: Stabilität: ungültige und getrennte Verbindungen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-006 · SPEC-007 · SPEC-027 · SPEC-034 · SPEC-046 · SPEC-048

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ungültige, unvollständige oder unerwartet getrennte Clientverbindungen bringen den Prozess nicht zum Absturz; das belegen Negativ-, Fragmentierungs- und Fuzz-Tests sowie parallele Verbindungen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Protokoll-Synchronität nach Fehler und Abbruch — das ist `slice-qa-synchronitaet`, weil dort die Antwortzuordnung geprüft wird, nicht das Überleben des Prozesses.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Negativ- und Fragmentierungstests nach `SPEC-027` laufen grün — für Paket-Codec, Leser und Anmeldepfad (Listener, Handshake-Treiber, Sockets am Loopback); der Anfragepfad nach dem Login ist nicht belegt, siehe §6.
- [x] Fuzz-/Property-Tests: defekte Paketlängen, Offsets und Typwerte verursachen keinen ungefangenen Absturz — für Paketköpfe, Nachrichtengrenzen und Zerstückelung (Referenz `FramingOracle`) und für PRELOGIN/LOGIN7 durch den Treiber (überschriebene, eingefügte und herausgeschnittene Bytes, darunter Offsets und Längen innerhalb der Nachricht); RPC- und SQL_BATCH-Nachrichten belegen nur die Fuzz-Tests ihrer Parser ohne Transport.
- [ ] Test mit parallelen Verbindungen bis `MAX_CONNECTIONS` (`SPEC-034`) — 105 parallele Verbindungen mit fehlerhaften und getrennten Clients sind belegt, die Grenze `MAX_CONNECTIONS` gibt es im Code nicht; offen bis `slice-abbruch-ressourcen-shutdown` sie liefert, siehe §6.
- [x] `make gates` grün.
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
| Testquellen des Protokollpfads | neu | Negativ-, Fragmentierungs-, Fuzz-Tests nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) |
| `src/test/kotlin/io/github/pt9912/tdswire/stability/` | neu | Referenz der Nachrichtengrenzen (`FramingOracle`), Erzeuger beschädigter Ströme (`WireGenerator`), Fuzz des Paket-Codecs und des Anmeldepfads, Abbruch an jeder Byteposition, Verbindungssturm gegen den Listener mit dem Handshake-Treiber |
| `tools/mk/` Make-Fragment | entfällt | `make test` (`tools/mk/test.mk`, `SPEC-024`) führt die neuen Tests bereits aus; ein zweites Target wäre eine Kopie |

Ist-Zustand gegen den Plan gemessen: Es gibt keinen verdrahteten Serverprozess, keinen SQL_BATCH-Pfad und keine Begrenzung auf `MAX_CONNECTIONS` im Code (`grep` über `src/` findet weder `MAX_CONNECTIONS` noch `maxConnections`). Die Tests laufen deshalb gegen den Listener mit dem Handshake-Treiber und dem Paket-Codec im Prozess, mit JDK-Sockets am Loopback als Gegenstelle. Was dadurch nicht belegt ist, steht in §6 mit Träger.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Transport und Protokollpfad nehmen Verbindungen an ([ADR-0003](../../adr/0003-transportframework.md) Accepted).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Fuzz-Läufe sind nicht reproduzierbar (Seed).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Das Transportframework ist nicht festgelegt, die Testart hängt daran. — **Ausgang:** offen bis zur Closure.
- `MAX_CONNECTIONS` (`SPEC-034`) ist im Code nicht vorhanden: der Listener nimmt unbegrenzt an, keine Verbindung wird wegen des Limits abgewiesen. Der Test belegt 105 gleichzeitige Verbindungen (21 Fälle, 5 Läufe je Fall) und zusätzlich 21 in der Aufwärmrunde, aber keine Grenze und keine Abweisung über der Grenze. Träger der Grenze ist `slice-abbruch-ressourcen-shutdown` (führt `SPEC-034`); der Test „bis `MAX_CONNECTIONS`“ bleibt in diesem Slice offen, bis die Grenze existiert. — **Ausgang:** offen bis zur Closure.
- Kein SQL_BATCH- und kein RPC-Pfad in der Sitzung, keine Anbindung des Sequenzierers: Nach dem Login verwirft der Handler der Tests jede Nachricht. Fuzz mit defekten SQL_BATCH-, RPC- und Attention-Nachrichten in einer angemeldeten Sitzung, Abbruch während eines Ergebnisstroms und Rücksetzung mitten in einer Antwort sind nicht belegt; die Parser (`SqlBatchParser`, `RpcParser`) haben eigene Fuzz-Tests ohne Transport. Träger: `slice-sql-batch-dispatch`, `slice-backend-sequenzierer`, `slice-attention-cancel-timeout`. — **Ausgang:** offen bis zur Closure.
- Die Gegenstelle ist ein JDK-Socket, kein Referenzclient: wie `Microsoft.Data.SqlClient` trennt, zerstückelt und zurücksetzt, ist nicht gemessen. Träger: `slice-qa-kompatibilitaet`. — **Ausgang:** offen bis zur Closure.
- TLS und Anmeldung sind nicht zusammen geprüft: der Verbindungssturm nutzt den unverschlüsselten Testmodus (`TEST_UNENCRYPTED`), der Anmelde-Fuzz ersetzt den TLS-Handschlag durch eine Zusage; defekte TLS-Records stehen in den Tests der TLS-Schleife, beschädigte LOGIN7-Nachrichten hinter echtem TLS nicht. Kein Träger beschlossen; Vorschlag `slice-qa-sicherheit`. — **Ausgang:** offen bis zur Closure.
- Die Ressourcenfreigabe ist an Dateideskriptoren (`UnixOperatingSystemMXBean`, Linux im Container) und an die Threads des Listeners gemessen, nicht am Heap und nicht an den gemeinsamen Pools von `Dispatchers.Default` und `Dispatchers.IO`. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` steht noch als Vorlage; der Slice berührt das ganze Repo (`*`), eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
