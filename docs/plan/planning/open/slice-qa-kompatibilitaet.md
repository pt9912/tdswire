# Slice slice-qa-kompatibilitaet: Kompatibilität: Ende-zu-Ende-Szenarien mit echtem .NET-Client

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-025 · SPEC-032 · SPEC-050

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die freigegebenen Ende-zu-Ende-Szenarien (Anmeldung, SELECT, parametrisierte Abfrage, NULL, Fehler, Abbruch) laufen automatisiert mit einem echten .NET-Client gegen den Server und sind ein Gate. Aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen gehören dazu die Anmeldemessungen, die dort fehlen: Windows-SSPI (NTLM), TDS 8.0 mit SQL-Authentifizierung, mehrpaketiges LOGIN7, Pooling mit `ResetConnection`. Das Testprojekt samt Restore-Sperre und Make-Target liefert [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md); dieser Slice schreibt die Szenarien dazu. Aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) §Offen kommen `sp_unprepare` unter Pooling, die `SET`-Text-Szenarien (`SchemaOnly`, `KeyInfo`), Transaktionen (Pakettyp `0x0E`) und TDS 8.0 für RPC hinzu. Dieser Slice ist zugleich der Träger der Beobachtung `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (Ausgang aus `welle-protokollcodec`): Der Codec ist für TDS 8.0 und Pooling bisher nur nach MS-TDS festgelegt, nicht am Server dieses Repos gemessen (Closure-Notiz [welle-protokollcodec-results](../done/welle-protokollcodec-results.md)). State und Meldung eines echten SQL Servers gehören nicht hierher, sondern in den Vergleichslauf D von [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Referenzabgleich gegen eine SQL-Server-Instanz (`SPEC-045`, `SPEC-050`) — [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) hat ihn als Vergleichslauf D entschieden; Träger ist [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md), dieser Slice führt keinen Referenzabgleich.
- Lastmessung — gehört zu `slice-qa-performance`, weil sie eine andere Messmethode und ein anderes Gate braucht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Szenarien nach `SPEC-025`/`SPEC-032` (Liste in [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) §3, Träger-Spalte) laufen im Testprojekt des Gerüsts grün gegen den Server; das Make-Target des Gerüsts bleibt in `make gates`.
- [ ] Anmeldemessungen und RPC-Messungen aus dem Ziel (Windows-SSPI, TDS 8.0, mehrpaketiges LOGIN7, Pooling mit `ResetConnection` und `sp_unprepare`, `SET`-Text-Szenarien, Transaktionen) am Server dieses Repos gemessen und in den ADRs bzw. im Clientprofil nachgetragen, oder je Punkt mit Begründung als nicht lieferbar benannt; für TDS 8.0 und Pooling auch die Aussage des Codecs (KDoc und Closure-Notiz von `welle-protokollcodec`) von *Festlegung* auf *gemessen* gehoben oder eingeschränkt.
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
| Szenarien im Testprojekt des Gerüsts ([slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md)) | neu | Szenarien Anmeldung, SELECT, RPC, NULL, Fehler, Abbruch nach [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) |
| Make-Fragment des Gerüsts | update | Szenarien in den Testlauf; `GATE_CHECKS +=` je nach Entscheidung im Gerüst-Slice |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Accepted und ein lauffähiger Server beantwortet die Anmeldung, und [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) liegt in `done/` (Gerüst: Testprojekt, Restore-Sperre, Make-Target).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Das Clientprofil (`SPEC-004`) ist nicht festgelegt.

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

- Die Pflicht-Matrix B ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), Status `Proposed`, wird `Accepted`) ist nicht gemessen; ihr Umfang kann sich bei der Verifikation ändern. — **Ausgang:** offen bis zur Closure.
- Die TLS-Schleife (`slice-transport-tls-schleife`) ist nur gegen eine JDK-`SSLEngine` im selben Prozess gemessen; am Referenzclient stehen TLS 1.2, andere Suiten als `TLS_AES_256_GCM_SHA384` und TDS 8.0 mit der eigenen Schleife aus. Der Lauf gegen den Referenzclient misst beide Pfade mit der Schleife dieses Repos und trägt nach, was er findet, oder benennt es als nicht lieferbar. — **Ausgang:** offen bis zur Closure.

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
