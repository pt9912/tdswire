# Slice slice-codec-datentypen-erweiterung: NBCROW, PLP und Typen außerhalb des Mindestkatalogs im Codec

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — Folge-Slice von `welle-protokollcodec`; ob und wann er nötig wird, entscheidet die Messung des Referenzclients (Risiko in §6), nicht die Reihenfolge der Wellen.

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-003 · SPEC-014 · SPEC-024 · SPEC-027 · SPEC-028 · ARC-007

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

**Ziel:** Der Token-Encoder und der RPC-Parser (`slice-codec-rpc-parser`) beherrschen über den Mindestkatalog von SPEC-003 hinaus die Formen, die ein Client für längere Werte und weitere Typen braucht: PLP (MAX-Typen) in ROW und RETURNVALUE, NBCROW und, soweit der Referenzclient sie sendet, `text`, `ntext`, `image`, `sql_variant`; was der Codec nicht kann, bleibt ein Ergebniswert mit Kategorie `TDS_UNSUPPORTED_TYPE` (SPEC-014).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- UDT, `json` und Table-Valued-Parameter — das Clientprofil nennt sie nicht als gemessen; ohne Messung gäbe es kein Orakel, und ein Handvektor allein wäre ein weiteres Vorkommen von `BEO-TDS/aussagen-ueber-messung-hinaus`.
- Abbildung zwischen Backendtypen und TDS-Typen — das ist der Backend-Vertrag ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), `Proposed`); der Codec nimmt TDS-Typen entgegen und kennt `SqlType` nicht.
- Netzwerk, Coroutinen, Kanäle und Streaming mit Rückstau — sie liegen in der Welle `welle-transport-tls`; hier arbeitet der Codec auf Bytes im Speicher.
- Nachweis gegen den Referenzclient und gegen einen echten SQL Server — er gehört in das Kompatibilitäts- und Referenzabgleich-Gate (SPEC-032, SPEC-050).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **PLP** (SPEC-003, SPEC-028): MAX-Typen (`nvarchar(max)`, `varchar(max)`, `varbinary(max)`) in COLMETADATA, ROW und RETURNVALUE als Stück-Folge mit Terminator; Längenprüfung vor dem Puffern. **Auch als RPC-Parameter** im Parser aus `slice-codec-rpc-parser`, samt SQL-Text und Deklarationsliste von `sp_executesql` als MAX: das ist die Abweichung von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und SPEC-003, die jener Slice benennt (er lehnt heute die ganze Nachricht als `INVALID_TYPE` ab); der Test `PLP lehnt die ganze Nachricht ab, auch als SQL-Text und Deklaration` wechselt mit diesem Punkt von „abgelehnt“ zu „gelesen“.
- [ ] **NBCROW** (SPEC-028): Null-Bitmap-Zeile neben ROW; Encoder wählt nicht selbst, sondern schreibt, was man ihm gibt.
- [ ] **Weitere Typen** (SPEC-003, SPEC-014): `text`, `ntext`, `image`, `sql_variant` nur, soweit `spec/spezifikation.md` `SPEC-021` sie als gesendet führt; alles andere bleibt `TDS_UNSUPPORTED_TYPE`.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements); die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket`.
- [ ] Zerstückelte, überlange und zufällige Eingaben enden in einem Ergebniswert (SPEC-027, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (die Zeilen zu den Typen in `SPEC-003`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/` | update | `TdsType`, `TypeInfoWriter`, `ValueWriter`, `TokenWriters` um PLP, NBCROW und weitere Typen; Zerlegung statt großer `when`-Blöcke |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | update | Parameter-Leser für PLP (RPC-Parameter, SQL-Text und Deklaration als MAX); `slice-codec-rpc-parser` liegt in `done/` und lehnt PLP bis dahin ab |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/token/`, `.../testsupport/` | update | Handvektoren, Test-Decoder (`TokenDecoder`) und Fuzz erweitern |
| `spec/spezifikation.md` | update, falls nötig | Zeilen zu den Typen in `SPEC-003` (Lehre aus `slice-codec-tds-paket`: eine berührte Spec-Stelle steht in dieser Tabelle) |

Der Implementer liest den Stand des Token-Packages aus `done/` (`slice-codec-tds-paket`) und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-codec-tds-paket` liegt in `done/`, `make gates` ist auf `main` grün, und `spec/spezifikation.md` `SPEC-021` oder der Referenzabgleich nennt mindestens einen der Gegenstände als gesendet oder verlangt; ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: PLP und NBCROW in einem Slice, die weiteren Typen in einem zweiten.
- `in-progress` → `open` (blockiert — Carveout?): Es gibt keinen Beleg, dass der Referenzclient die Typen sendet oder erwartet; der Slice bleibt dann `open`, und `TDS_UNSUPPORTED_TYPE` genügt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Offene Punkte aus [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Simulations-Backend), die diese Datei als Träger nennt: (1) der Typkatalog von `SqlType` und [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) umfasst `smallint`, `char`, `float`, GUID, binär, Zonenzeit und `xml` nicht, die das neutrale Schema von d-migrate kennt, und das Simulations-Backend weist solche Spalten beim Laden ab; (2) `UnicodeText` trägt keine Länge, das Backend meldet `NVARCHAR(4000)` statt `NVARCHAR(n)`. Das Ziel dieses Slice nennt bisher nur Codec-Formen (PLP, NBCROW), nicht den Typkatalog des Backends: ob er den Katalog und die Länge aufnimmt oder eine eigene Datei entsteht, entscheidet der Nutzer. Ohne diese Entscheidung bleibt der Punkt hier als offen geführt. — **Ausgang:** offen bis zur Closure.
- Es ist offen, ob der Referenzclient diese Formen in den gemessenen Szenarien sendet oder erwartet: das Clientprofil nennt sie als nicht gesendet oder ungemessen. Ohne Beleg ist der Slice Vorrat. — **Ausgang:** offen bis zur Closure; Träger `slice-qa-kompatibilitaet`.
- Handvektoren für PLP und NBCROW sind eine Lesart von MS-TDS, keine Messung (`BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.
- Strenge der statischen Analyse: weitere Typen vergrößern die `when`-Blöcke des Encoders; die Abhilfe ist Zerlegung im Code. — **Ausgang:** offen bis zur Closure.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung und Schnitt in §4.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`), alle Sub-Area `*`. Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×), beide in §6. Ohne Treffer: die übrigen sechs.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
