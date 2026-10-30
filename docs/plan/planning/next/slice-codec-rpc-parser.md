# Slice slice-codec-rpc-parser: RPC-Anfrage-Parser für den Katalog von ADR-0005 ohne Netzwerk

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — Folge-Slice von `welle-protokollcodec`; die funktionale Welle für SQL_BATCH und RPC ist noch nicht geplant und nimmt ihn auf, sobald sie eröffnet wird.

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)

**Berührte Spec-Stellen:** SPEC-003 · SPEC-024 · SPEC-027 · SPEC-028 · SPEC-030 · ARC-007

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

**Ziel:** Ein reiner Parser ohne Netzwerk liest die RPC-Anfrage (Pakettyp RPC) in die Formen, die der Katalog in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und `contracts/client-profile.md` (§RPC-Katalog) nennt: `sp_executesql` (ProcID 10), `sp_prepexec` (ProcID 13), `sp_execute` (ProcID 12) und RPC per Prozedurname; Parameter mit Namen, Statusflags (OUTPUT), TYPE_INFO, NULL-Sentinels und Kollation; Fehler sind Ergebniswerte, keine ungefangene Ausnahme.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ausführung, Prepare-Handle-Verwaltung je Sitzung und Fehlerantwort bei unbekanntem Handle — das ist Ablauf, nicht Parser; der Zustand gehört zum Zustandsautomaten ([ADR-0004](../../adr/0004-session-zustandsautomat.md), `Proposed`) und zum Backend-Vertrag ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), `Proposed`). Der Parser liefert die Handle-Zahl, entscheidet nichts.
- Unbekannte Katalogformen (`sp_prepare`, `sp_unprepare`, `sp_cursor*`, `SET FMTONLY`-Text, Transaktions-Pakettyp `0x0E`) — die Behandlung ist im Katalog offen; der Parser meldet eine unbekannte ProcID als eigenen Ergebniswert und legt keine Antwort fest ([ADR-0007](../../adr/0007-fehler-und-attention.md), `Proposed`).
- NBCROW, PLP und Typen außerhalb des Mindestkatalogs (Parameter-TYPE_INFO über die Typen von SPEC-003 hinaus) — dafür steht `slice-codec-datentypen-erweiterung`; dieser Slice lehnt solche Parameter als Ergebniswert ab.
- Netzwerk, Coroutinen, Kanäle — sie liegen in der Welle `welle-transport-tls`.
- Nachweis gegen den Referenzclient oder einen echten SQL Server — er gehört in das Kompatibilitäts- und Referenzabgleich-Gate (SPEC-032, SPEC-050); der Katalog ist nur am Testserver in TDS 7.4 ohne TLS und Pool gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **RPC-Kopf und Katalog-Erkennung** (SPEC-028, SPEC-030): ALL_HEADERS, Prozedur per ProcID oder Name (`NameLenProcID` als Zeichenzahl), Optionsflags; die vier Katalogformen werden erkannt, eine andere ProcID ist ein Ergebniswert.
- [ ] **Parameter-Leser** (SPEC-003, SPEC-030): Name, Statusbyte (OUTPUT), TYPE_INFO des Mindestkatalogs samt Kollation und Precision/Scale, Wert mit Längenpräfix und NULL-Sentinel; für `sp_executesql` bleiben SQL-Text und Deklarationsliste getrennt vom Wertetransport.
- [ ] **Fehlerverhalten** (SPEC-027, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): zerstückelte, überlange, abgeschnittene und zufällige Eingaben (fester Startwert) enden in einem Ergebniswert mit Kategorie nach SPEC-011; die Textdarstellung des Parse-Ergebnisses enthält keine Parameterwerte.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage des Parsers steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements); die Orakel sind Handvektoren, und die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | neu | RPC-Parser mit eigenem Ergebnismodell; nutzt `ByteReader` aus `protocol/wire`; keine Imports aus `transport`, `port`, `simulation`, `runtime` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | neu | Handvektoren je Katalogform, Negativfälle, Fuzz mit festem Startwert; `@spec`-Tag je Test |
| `spec/spezifikation.md` | update, falls nötig | `SPEC-030` ist „Entwurf“; ändert die Umsetzung eine Zeile des Katalogs, wird die Spec im selben Slice nachgezogen (Lehre aus `slice-codec-tds-paket`: eine berührte Spec-Stelle steht in dieser Tabelle) |

- Reihenfolge: Kopf und Erkennung, dann Parameter-Leser, dann Fehlerverhalten.
- Der Implementer liest die API von `protocol/wire` und `protocol/token` aus `done/` (`slice-codec-tds-paket`) und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-codec-tds-paket` liegt in `done/`, `make gates` ist auf `main` grün, [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) steht auf `Accepted`, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Slice A = Kopf und `sp_executesql`, Slice B = `sp_prepexec`, `sp_execute` und Prozedurname.
- `in-progress` → `open` (blockiert — Carveout?): `SPEC-030` ändert sich so, dass der Katalog neu entschieden werden muss.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage des Parsers sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Katalog ist nur am Testserver gemessen (Mindestkatalog; unter Pooling und für `sp_prepare`, `sp_unprepare`, `sp_cursor*` ungemessen): der Parser belegt Handvektoren, nicht die Annahme durch den Client. — **Ausgang:** offen bis zur Closure; Träger `slice-qa-kompatibilitaet`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.
- Parameterwerte sind Nutzerdaten (SQL-Parameter): eine automatisch erzeugte Textdarstellung brächte sie in ein Log. — **Ausgang:** offen bis zur Closure.
- Größe: drei Liefer-Punkte, vier Katalogformen. — **Ausgang:** offen bis zur Closure; Rückführung und Schnitt in §4.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); `protocol` ist kein eigener Pfad-Cluster mit eigener Strukturregel. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`), alle Sub-Area `*`. Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, Muster in KDoc; Gegenmittel in §2), `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×) und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (3×; Katalog nur am Testserver gemessen, §6). Ohne Treffer: die übrigen fünf.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
