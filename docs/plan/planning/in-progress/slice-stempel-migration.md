# Slice slice-stempel-migration: Stempel verdrahten: Gruppenschritte werkzeug und tests, Migration von detekt-selbsttest und referenztest-dotnet, Nachmessung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (Begründung in [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md) §1)

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)

**Berührte Spec-Stellen:** —

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `make gates` fährt die Gates in zwei Gruppen, `werkzeug` und `tests` ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 1 bis 3): je Gruppe `pruefe`, bei Treffer überspringen, sonst alle Gates der Klasse laufen lassen und danach `schreibe`. Die zwei Altmechanismen (`.harness/state/detekt-selbsttest.ok`, `.harness/state/referenztest-dotnet.hash`) entfallen, `detekt-selbsttest` gehört zu `werkzeug`, `referenztest-dotnet` zu `tests`. Die Laufzeiten sind danach warm und kalt gemessen. Der Slice braucht Skript und Listen aus [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Skript, Eingangslisten und deren Selbsttests — [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md); hier werden sie benutzt.
- Die CI-Seite ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Entscheidung 7, dritter Schritt der Reihenfolge in [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 9): kein Slice angelegt, Begründung und Bedingung stehen in [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md) §1. Die CI führt weiter `make gates` ohne Note, also voll; das ändert dieser Slice nicht.
- Notes zu pushen oder zu holen — nicht gewollt (Entscheidung des Nutzers, 2026-11-01).
- E2E-Läufe (`make e2e`, `laufzeittest`, `simulations-integration`, `kompatibilitaet`) und Lasttest-Gates — stehen in keinem Stempel-Umfang ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 6: Gates mit verstecktem Eingang laufen immer). `make e2e` (sieben Stufen) ruft `referenztest-dotnet` nicht; `make referenztest-dotnet` allein läuft immer; `FORCE=1` wirkt nur auf `make gates`.
- Aufteilung in weitere Stempel — nur per neuer Entscheidung ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Re-Evaluierungs-Trigger).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Gruppenschritte und Zuordnung** ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 1, 3, 5, 8): Jedes Target in `GATE_CHECKS` gehört zu genau einer Klasse: `werkzeug` (`baseline-verify`, `detekt-selbsttest`, `skript-selbsttest`, `kopplung-zahlen`), `tests` (`test`, `detekt`, `build`, `referenztest-dotnet`), `stempel` (die vier Selbsttests der Stempel, [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)) und `immer` ohne Stempel (`docs-check`, `gate-zuordnung`, `stempel-eingaenge-pruefung`, [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md)); eine Doku-Änderung löst keine Klasse mit Stempel aus, `docs-check` läuft aber immer. Die Zuordnung steht im Make-Fragment des Gates und in `harness/README.md` §Sensors (Bindung: [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)); ein Sensor prüft, dass kein Target in `GATE_CHECKS` ohne Klasse oder in zwei Klassen steht. Je Klasse ein Gruppenschritt: `bash tools/stempel.sh pruefe <klasse>` (Exit 0 überspringt die Klasse, jeder andere Ausgang lässt sie laufen), sonst Lauf **aller** Gates der Klasse, dann `schreibe` mit dem vorab berechneten Hash; `make test` allein schreibt nichts; `FORCE=1` im Aufruf überspringt `pruefe`; `make clean-build` bleibt der Frischbeleg ohne Stempel ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)). `make gates` ist grün, wenn jede Klasse grün lief oder mit Beleg übersprungen wurde; `record-gates` und der Stop-Hook behalten Gesamtbaum-Hash und HEAD unverändert. `einrichten` (`notes.rewriteRef`) wird vom Setup-Target des Repos und von `schreibe` aufgerufen, falls der Schlüssel fehlt (welcher der beiden Aufrufer es trägt, entscheidet der Bau). Tests am Zusammenspiel: ein Lauf schreibt beide Stempel; derselbe Eingang ein zweites Mal überspringt beide Klassen; eine Änderung unter `docs/` löst keine Klasse mit Stempel aus (`docs-check` läuft in `immer`); eine Änderung unter `src/` löst `tests`, nicht `werkzeug` aus; eine Änderung am `Dockerfile` löst beide aus; ein roter Test lässt `make gates` rot und schreibt keinen Stempel.
- [x] **Migration der Altmechanismen** ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 7, [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Folgepflicht 4): `.harness/state/detekt-selbsttest.ok` und `.harness/state/referenztest-dotnet.hash` entfallen; die Skip-Logik in `tools/mk/detekt-selbsttest.mk` (mtime-Stempel) und `tools/mk/referenztest-dotnet.mk` (Inhalts-Hash) entfällt zugunsten des Gruppenschritts, `detekt-selbsttest` geht in `werkzeug`, `referenztest-dotnet` in `tests` auf. `tools/eingangs-hash.sh` und sein Selbsttest bleiben, solange ein Hash über Dateien nötig ist, sonst entfallen sie mit Begründung in der Closure-Notiz. Zu belegen: `bin/` und `obj/` unter `referenztest-dotnet/` zählten im alten Hash nicht mit, die Sauberkeitsprüfung zählt auch Ignoriertes; die Klasse `tests` darf davon nicht dauerhaft „nicht sauber“ und damit immer Lauf sein (auf dem Host baut nichts; der Selbsttest belegt es). Die Aufnahmebedingung (6) wird für beide migrierten Gates geprüft: `referenztest-dotnet` läuft mit per Digest gepinntem Image und festem Szenario-Satz (Smoke ohne Server) und hängt nicht von der Uhr ab; `detekt-selbsttest` ebenso. Die Kommentare der Fragmente und die Zeilen in `harness/README.md` (u. a. `make detekt-selbsttest`, `make referenztest-dotnet`, `FORCE=1`) sagen den neuen Mechanismus.
- [x] **Nachmessung der Laufzeit** ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Folgepflicht 4): Laufzeit von `make gates` warm und kalt für die Fälle ohne Beleg, beide Klassen mit Beleg, nur Doku geändert, nur `src/` geändert, `Dockerfile` geändert und Amend ohne Inhaltsänderung (mit `notes.rewriteRef`); dazu die Laufzeit der Selbsttests aus [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md). Die Werte stehen als Einzelmessungen mit Datum im Kopf des Fragments `tools/mk/stempel.mk` (keine Streuung behauptet); ohne Messung trägt keine Laufzeitaussage. Prüft den Re-Evaluierungs-Trigger der ADR, ob `referenztest-dotnet` in `tests` spürbar mitläuft.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `harness/README.md` (Sensors-Tabelle: zwei Klassen, Bindung auf [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) und [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md); Zeilen der migrierten Gates; Hinweis zu `FORCE=1`), die Köpfe der Fragmente. Eine Spec-Stelle wird nicht berührt.
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
| `tools/mk/stempel.mk` | update | Gruppenschritte `werkzeug` und `tests`, Zuordnung der Gates, Messwerte im Kopf (das Fragment entsteht in [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md)) |
| `tools/mk/detekt-selbsttest.mk` | update | mtime-Stempel und Eingangsliste entfallen; Gate in `werkzeug` |
| `tools/mk/referenztest-dotnet.mk` | update | Hash-Datei und Skip-Logik entfallen; Gate in `tests`; `FORCE=1` bleibt als Schalter des Gruppenschritts |
| `tools/mk/test.mk`, `tools/mk/build.mk`, `tools/mk/skript-selbsttest.mk`, `tools/mk/kopplung-zahlen.mk`, `harness/mk/doc-gate.mk`, `harness/mk/baseline.mk` | update | Zuordnung zur Klasse im Fragment ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 3); `harness/mk/` gehört dem Werkzeug: ob die Zuordnung dort oder in `repo.mk` steht, entscheidet der Bau (Risiko in §6) |
| `Makefile`, `repo.mk` | update | `GATE_CHECKS` und Gruppenschritte; Setup-Target ruft `einrichten` |
| `tools/eingangs-hash.sh`, `tools/eingangs-hash-selbsttest.sh` | update oder entfernen | bleiben, solange ein Hash über Dateien nötig ist |
| `tools/e2e.sh` | prüfen | keine der sieben Stufen ruft `referenztest-dotnet`; nichts zu ändern, solange das so bleibt |
| `harness/README.md` | update | Sensors-Tabelle, Bindung |
| Test der Zuordnung (Skript oder Teil des Selbsttests) | neu | jedes Target in `GATE_CHECKS` in genau einer Klasse ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Fitness Function) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md) liegt in `done/` (Skript und Listen stehen mit grünen Selbsttests auf `main`) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder die Migration von `referenztest-dotnet` (Sauberkeit von `bin/` und `obj/`, Aufnahmebedingung) sprengt den Rahmen; Schnittvorschlag: Migration der zwei Altmechanismen als eigener Slice hinter den Gruppenschritten. Auslöser, am Slice-Plan beobachtbar: das Häkchen von Punkt 1 ist gesetzt und für Punkt 2 gibt es noch keinen Test.
- `in-progress` → `open` (blockiert — Carveout?): Der Selbsttest der Gruppenschritte zeigt, dass ein Gate der Klasse einen versteckten Eingang hat, den die Liste nicht erfasst (falsch grün); dann ist das der Re-Evaluierungs-Fall von [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) (Aufnahmebedingung 6), keine Anpassung des Tests.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`) ohne und mit Beleg, die Altmechanismen sind aus dem Baum entfernt, die Messwerte sind eingetragen, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Das Gate wird auf Grundlage eines Belegs übersprungen** (falsch grün, [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Negativ 3): ein Beleg, der nicht zum Stand passt, ergibt grün ohne Lauf. Begrenzt durch Schreiben nur nach grünem Lauf aller Gates der Klasse, die Listen mit ihrem Selbsttest und den Vollauf der CI. — **Ausgang:** weiter offen: → `BEO-TDS/test-stage-cache-verdeckt-wiederholung`
- `harness/mk/` gehört dem Werkzeug und wird bei einem erneuten Bootstrap überschrieben; die Zuordnung `docs-check`, `baseline-verify` zur Klasse `werkzeug` darf dort nicht stehen. — **Ausgang:** entfallen: die Zuordnung steht in `tools/mk/stempel.mk` (Repo-eigen), nicht unter `harness/mk/`; `harness/mk/doc-gate.mk` und `baseline.mk` bleiben unverändert
- Slices anderer Wellen, die ein Gate anhängen (zum Beispiel aus `welle-referenzabgleich-release`), brauchen eine Klasse; ohne den Sensor der Zuordnung entstünde ein dritter Mechanismus. — **Ausgang:** entfallen: `make gate-zuordnung` (Gate der Klasse `immer`) wird rot, wenn ein Gate in `GATE_CHECKS` ohne Klasse oder in zwei Klassen steht; ein Gate ohne Klasse läuft bis dahin
- Squash-Merges auf `main` und Rebase lassen die Note am alten Commit zurück; ein Klon ohne `einrichten` läuft nach Amend voll (Kosten: ein Lauf, irrt zur sicheren Seite). — **Ausgang:** entfallen: von [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) als Negativ 1 geführt, kein Risiko des Slice
- Mehrere Agenten schreiben Notes aus verschiedenen Arbeitsbäumen desselben Klons (parallele Läufe): im Selbsttest von [slice-stempel-mechanismus](../done/slice-stempel-mechanismus.md) geprüft, im Betrieb nicht gemessen (die Verifikation prüfte `make -j4 gates` mit der Sperre und einen zweiten `git worktree`, der die Notes des ersten nutzt). — **Ausgang:** weiter offen: im Betrieb mit mehreren Agenten nicht gemessen
- Die Laufzeitwerte sind Einzelmessungen auf einem Rechner und gelten nicht für die CI, die weiter voll läuft. — **Ausgang:** weiter offen: Einzelmessungen mit Last, kalt nicht gemessen; stehen im Kopf von `tools/mk/stempel.mk`
- Die CI führt nur `make gates` und ruft die Selbsttests der Stempel (`stempel-selbsttest`, `stempel-eingaenge`, `stempel-abbild`, bisher kein Gate) nicht; ob sie einen eigenen Schritt bekommen und ob die Selbsttests als `werkzeug`-Gates in `GATE_CHECKS` oder mit engerem Stempel laufen (Verifikation V-4 von slice-stempel-mechanismus), entscheidet der Nutzer vor der Aufnahme. — **Ausgang:** entfallen: die vier Selbsttests stehen als Gates der Klasse `stempel` in `GATE_CHECKS` ([ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)); die CI führt `make gates` ohne Note, also auch sie voll. Ob die CI Stempel bekommt ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Entscheidung 7), steht als Nutzerpunkt in §7

- Abweichung von den ADRs: `docs-check`, `gate-zuordnung` und `stempel-eingaenge-pruefung` stehen in der Klasse `immer` (kein Stempel, laufen in jedem `make gates`), nicht in `werkzeug`, wie [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 3 und [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) Entscheidung 2 es sagen; ihr Eingang (jede Markdown-Datei, alle Make-Fragmente) steht nicht in einer Liste, ein Stempel liesse sie bei einer Datei ausserhalb der Liste aus (Review 54cc013, HIGH-1). Die Klasse `immer` trägt [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md) (Accepted, Option A). — **Ausgang:** entfallen: gelöst durch [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md); die Pflichtmenge der Klasse `immer` in `gate-zuordnung` (Frage (b) der ADR) ist nicht beantwortet und nicht gebaut.

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

**Gegenstand:** geliefert. `make gates` fährt vier Klassen: `werkzeug`, `tests` und `stempel` mit je einem Stempel (Gruppenschritte `gruppe-werkzeug`, `gruppe-tests`, `gruppe-stempel`; `tools/stempel-gruppe.sh`) und `immer` ohne Stempel (`docs-check`, `gate-zuordnung`, `stempel-eingaenge-pruefung`). Die Altmechanismen (`.harness/state/detekt-selbsttest.ok`, `.harness/state/referenztest-dotnet.hash`, `tools/eingangs-hash.sh` samt Selbsttest und Dockerfile-Stufe) sind aus dem Baum entfernt; `detekt-selbsttest` steht in `werkzeug`, `referenztest-dotnet` in `tests`. Die Klasse `stempel` und die vier Selbsttests als Gates tragen [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md), die Klasse `immer` [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md) (Accepted, Option A). Die DoD-Häkchen stehen, soweit der Verifikationsbericht sie für Stand 38aea39 bestätigt; DoD-Punkt 1 trägt den Wortlaut mit den vier Klassen.

**Review und Verifikation:** Review 54cc013 (HIGH-1 Doku-Gate übersprungen trotz Datei außerhalb der Liste, HIGH-2 Selbsttests nicht im Gate, HIGH-3 Kommentar; MEDIUM-1 neues `tests`-Gate lief nicht, MEDIUM-2 `make -t` schrieb Stempel; alle behoben), Review 62e9937 (HIGH-1 `make gates FORCE=1` immer rot, `FORCE` leckte in `stempel-gruppe`; MEDIUM-1 `make -j gates` rötete `stempel-abbild` zufällig; MEDIUM-2 Klasse `immer` ohne ADR; behoben bzw. durch [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md) getragen), Verifikation 38aea39 (keine HIGH, keine Falsch-grün-Lücke gefunden; MEDIUM-1 = Klasse `immer` ohne ADR, **inzwischen erledigt durch [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md) Accepted**; MEDIUM-2 Grenze der Eingangsprüfung; LOW-1 bis LOW-3, INFO-1, INFO-2). Berichte: [Review 54cc013](../../../reviews/2026-11-01-54cc013-slice-stempel-migration.md), [Review 62e9937](../../../reviews/2026-11-01-62e9937-slice-stempel-migration.md), [Verifikation 38aea39](../../../reviews/2026-11-01-38aea39-verifikation-slice-stempel-migration.md).

**Nicht unabhängig verifiziert (Delta nach der Verifikation):** Die Verifikation galt Stand 38aea39. Danach kamen die Nachführung von [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md) (Kopf von `tools/mk/stempel.mk`, §6 und DoD-Wortlaut dieses Slice, die Grenze `assume-unchanged`/`skip-worktree` in `tools/stempel.sh`) und der Merge von `main` (Slice `slice-resetconnection-zuruecksetzen`, unter anderem `src/`, `tools/e2e.sh`, `tools/kompatibilitaetstest.sh`, Spec). Das Delta ist kein Reviewer- oder Verifier-Gegenstand gewesen. Belege des Implementers für den Stand danach: `make gates` 24,7 s (Lastmittel 4,3), `make -j4 gates` 3,7 s, `make -j4 gates FORCE=1` grün.

**Gemessen** (Einzelmessungen am 2026-11-01 auf einem Host, Docker-Layer-Cache warm, Lastmittel 9 bis 15 durch andere Agenten, keine Streuung behauptet; Quelle Verifikation 38aea39, Werte im Kopf von `tools/mk/stempel.mk`):

- Referenz `main` ohne Stempel, `make gates` warm: 8,7 und 11,8 s.
- Mit Beleg (alle Klassen übersprungen): 3,7 bis 4,4 s; mit Beleg nach einer Änderung an `harness/README.md` (nur `werkzeug` läuft) 5,2 s; nach einer Änderung an `README.md` 4,0 s (alle skippen).
- Ohne Beleg (frischer Klon ohne Notes): 15,0 bis 20,6 s, also **langsamer als `main`**; die Klasse `stempel` mit den vier Selbsttests kostet etwa 10 s bei jeder Änderung unter `tools/` und am `Makefile`. Dockerfile geändert: 20,6 bis 32,6 s, alle Klassen laufen. Nur `src/` geändert: Gradle-Neubau, 254 s (Lastmittel 4, Kopf von `stempel.mk`); 339 s im Review bei Lastmittel 15.
- `make gates FORCE=1`: 25,3 s. `make stempel-gruppe FORCE=1`: 64 Fälle, rund 6,6 s bei Lastmittel 12 (der Kopf nennt 2,3 s bei Lastmittel 3 bis 5).
- Kalt (leerer Cache) nicht gemessen. Die CI führt `make gates` ohne Note, also voll; für sie gilt keiner dieser Werte. „Performanter“ gilt nur mit Beleg.

**Handvektor:** keiner; keine Aussage dieses Slice hängt an einem Standard-Vektor.

**Selbstkonsistenz:** `gate-zuordnung` hält jedes Gate in genau einer Klasse und die Stage-Paare gegen die Stage-Liste der Klasse; `stempel-eingaenge-pruefung` hält die Eingangslisten gegen `COPY`-Quellen, Link-Ziele und Make-Dateien; `stempel-abbild` zeigt je Listeneintrag, dass er den Hash seiner Klasse ändert. Die Verifikation fand mit Mutationen an Zuordnung, Kette, Listen und Stempel-Logik keine Falsch-grün-Lücke; nicht erkannte Fälle sind äquivalente Mutanten (`FORCE='$(FORCE)'`) oder sichere Richtung (Gate in `immer` statt Klasse mit Stempel).

**Festlegung (Setzungen):** (1) Die Klasse `immer` ohne Stempel für die drei Gates mit Eingang außerhalb jeder Liste ([ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md)). (2) Die Zuordnung der Gates steht in `tools/mk/stempel.mk` (Repo-eigen), nicht unter `harness/mk/`. (3) `make referenztest-dotnet` allein läuft immer; `FORCE=1` wirkt nur auf `make gates`. (4) Die Selbsttests des Mechanismus sind Gates der Klasse `stempel` ([ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)). (5) Die vier Stempel-Gruppenschritte laufen unter `make -j` nebeneinander; die Sperre des Skripts serialisiert das Schreiben der Notes.

**Abweichungen:** Die Klasse `immer` weicht vom Wortlaut von [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 1 und 3 und [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) Entscheidung 2 ab (dort `werkzeug`); die Abweichung trägt [ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md) mit `Supersedes (teilweise)`. Der DoD-Wortlaut „Selbsttests als kein Gate“ aus dem Vorgänger-Slice ist mit der Klasse `stempel` aufgelöst.

**Nicht gemessen:** kalte Laufzeit; das Delta nach 38aea39 durch einen unabhängigen Lauf; Betrieb mit mehreren Agenten auf einem Klon über `-j4` und einen zweiten Worktree hinaus; der Fall „nur `src/` geändert“ nur grob (Gradle-Neubau).

**Offene Punkte für den Nutzer:**

1. Pflichtmenge der Klasse `immer` in `gate-zuordnung` ([ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md) Frage (b)): nicht beantwortet und nicht gebaut. Ohne sie kann ein Gate der Klasse `immer` oder (außer `stempel`) einer anderen Klasse nach `immer` wandern, ohne dass `gate-zuordnung` rot wird; die Kette selbst prüft die Klasse `immer` bereits (Verifikation LOW-2).
2. Grenze `assume-unchanged`/`skip-worktree`: eine so markierte Listen-Datei macht den Sauberkeitstest blind (Verifikation LOW-1, bewusste Git-Manipulation); im Kopf von `tools/stempel.sh` benannt, kein Sensor.
3. Host-Gates ohne `GATE_STAGE`-Paar (`x=-`) in `werkzeug`, `tests` oder `stempel`: liest ein solches Gate eine Datei außerhalb der Liste, sieht es der Sensor nicht (Verifikation MEDIUM-2, Grenze im Kopf von `tools/stempel-eingaenge-pruefung.sh`; heute betrifft es nur `baseline-verify`). Bei jedem neuen Host-Gate im Review gezielt prüfen.
4. CI-Seite ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Entscheidung 7): kein Slice angelegt; die CI läuft ohne Note voll, die Stempel wirken dort nicht.

**Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Gelernt: (1) Die Zuordnungsregel „sonst zu `werkzeug`“ aus [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) war der unsichere Default: ein Gate, dessen Eingang keine Liste vollständig deckt, wurde mit Beleg übersprungen (Review 54cc013 HIGH-1, mit einer Probedatei belegt); die sichere Seite ist „läuft immer“, und eine ADR trägt sie erst nach dem Befund ([ADR-0031](../../adr/0031-gates-ohne-stempel-klasse-immer.md)). Vorschlag, nicht gesetzt: Die Frage „deckt die Liste den Eingang vollständig?“ gehört in den Entscheidungstext einer Stempel-ADR, bevor ein Gate einer Klasse zugeordnet wird. (2) Ein Selbsttest, der ein Skript mit der Umgebung des Aufrufers startet, wird unter einem dokumentierten Schalter rot, den er selbst nie setzt (`FORCE=1` leckte in `stempel-gruppe`, Review 62e9937 HIGH-1; unter `make gates` ohne Schalter fiel es nicht auf). Beide Punkte sind Beobachtungen im Register, keine deklarierten Regeln.

**Register:** neue Evidenz unter [BEO-TDS/eingangsliste-je-stage-von-hand](../observations/BEO-TDS/eingangsliste-je-stage-von-hand/observation.md) (`evidence/slice-stempel-migration.md`) und [BEO-TDS/zusage-ohne-sensor-in-der-ci-kette](../observations/BEO-TDS/zusage-ohne-sensor-in-der-ci-kette/observation.md) (`evidence/slice-stempel-migration.md`). Zitiert ohne neue Evidenz: [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md) (verkörpert; Stempel und Layer-Cache verdecken die Wiederholung, `make clean-build` und `FORCE=1` bleiben der Frischbeleg). Kein Zähler gesetzt. Nach den Dateien stehen `eingangsliste-je-stage-von-hand` bei 3× und `zusage-ohne-sensor-in-der-ci-kette` bei 6× unter `evidence/`; beide sind über der Schwelle und ohne Ausgang, die Zuordnung eines Ausgangs ist Sache der Welle-Closure oder des Nutzers, hier nur gemeldet. Die Beobachtungen `mutation-haengt-an-ungesteuerter-reihenfolge` und `lesart-einer-nutzerentscheidung-unbestaetigt` sind nicht einschlägig.

**Ausgang der Risiken in §6:** in §6 je Zeile eingetragen: Falsch grün durch Beleg: weiter offen (Register); `harness/mk/`-Zuordnung: entfallen; Gates anderer Wellen: entfallen; Squash/Rebase: entfallen; mehrere Agenten: weiter offen; Laufzeitwerte: weiter offen; CI-Selbsttests: entfallen (CI-Seite als Nutzerpunkt 4); Abweichung Klasse `immer`: entfallen.

**Folge-Slice:** keiner angelegt; die offenen Punkte oben sind Nutzerentscheidungen.

**Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die nächste Welle-Closure. Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: getragen (die zwei Pfade stehen oben). Folge-Slice: keiner.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Pfade (`tools/mk`, `harness/mk`, `Makefile`, `repo.mk`, `harness/README.md`) sind Werkzeug-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des gemergten `main` am 2026-11-01); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 5×, Stand *verkörpert* (`make clean-build`). Treffer: Stempel und Layer-Cache verdecken die Wiederholung grüner Läufe; `make clean-build` und `FORCE=1` bleiben der Frischbeleg (Punkt 1, §6).
- `BEO-TDS/eingangsliste-je-stage-von-hand` — 1×, offen, unter der Schwelle. Treffer: die Listen sind nach {S_STM} ein geprüftes Artefakt; die Zuordnung in Punkt 1 hängt daran.
- Ohne Treffer: die übrigen Einträge des Registers.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
