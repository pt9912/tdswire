# Slice slice-release-doku-prozess: Nutzer- und Maintainer-Doku, Release-Schritte und Wächter für den detekt-Stabil-Release und die vendored Baseline

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../../adr/0010-detekt-pre-release.md)

**Berührte Spec-Stellen:** SPEC-041 · SPEC-042

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

**Ziel:** Die Nutzer-Doku in `docs/user/` beschreibt den Ist-Zustand von Betrieb und Backend-Anbindung, `docs/maintainer/releasing.md` beschreibt die Release-Schritte; der Re-Evaluierungs-Trigger von [ADR-0010](../../adr/0010-detekt-pre-release.md) (Stabil-Release von detekt 2.x) und der Freshness-Audit der vendored Baseline haben je einen benannten Wächter.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Kompatibilitätsmatrix der Nachrichten und Datentypen — [slice-qa-wartbarkeit](../done/slice-qa-wartbarkeit.md) führt sie unter `docs/plan/` (`AGENTS.md` §5, Regel 6); die Nutzer-Doku verlinkt sie und kopiert sie nicht, weil Kopien driften.
- Chronik, Forensik und Planung in der Nutzer-Doku — `AGENTS.md` §5, Regel 5: Was war, steht in `git`; was kommt, in `docs/plan/planning/`.
- Ein Sensor, der die Release-Liste des Kurs-Repos abruft — `harness/conventions.md` führt ihn als *kommt nicht mit*, weil er Netzzugriff über `bash`, `git` und `docker` hinaus bräuchte; der Audit bleibt eine Handlung im Release-Ablauf.
- Der Wechsel von detekt auf eine 2.x-Stabilversion — ein eigener Slice nach Eintritt des Triggers, mit neuer ADR (`Supersedes` [ADR-0010](../../adr/0010-detekt-pre-release.md)); [ADR-0010](../../adr/0010-detekt-pre-release.md) ist `Accepted` und bleibt unverändert (`AGENTS.md` §3.5). Eine Lockerung der Gates bräuchte eine ADR (`AGENTS.md` §3.6).
- Versionsschema, Tag, Veröffentlichung in einer Registry, Signierung, Software-Stückliste, `make e2e` im Release und die Form des Wächters (Werkzeug-Target) als Entscheidung — Nutzerentscheidungen, geliefert von [slice-release-entscheidungen](../open/slice-release-entscheidungen.md); dieser Slice dokumentiert sie als offen und erfindet nichts.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Nutzer-Doku** (Ist-Zustand): Einbettung der Bibliothek über `TdsServer.start`; der Testserver-Container dient nur der Prüfung und ist kein Betriebsweg ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)), Konfigurationsschlüssel (die Tabelle aus [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md)), Logfelder und Startdiagnose, Metriken und Spans, Anbindung eines Backends über den Port (`spec/spezifikation.md` `SPEC-022`) und Nutzung des Simulations-Backends (Beschreibung der Tabellen, erkannte Formen als Verweis auf SPEC-036, nicht als Kopie), der Betriebstext aus [ADR-0013](../../adr/0013-backend-grenzen.md) (Betreiberschalter `-XX:+ExitOnOutOfMemoryError` mit Supervisor-Neustart und Pflichttext für Integratoren: Backend und Zeilenquelle suspendierend, blockweise Aufrufe empfohlen; Zahlen als Spike-Messung gekennzeichnet), Grenzen mit Verweis auf die Kompatibilitätsmatrix; die Hinweise in `AGENTS.md` und `harness/README.md`, die `docs/user` und `docs/maintainer` als „falls vorhanden“ führen (mit `d-check:ignore`), werden auf Links umgestellt, soweit das Verzeichnis besteht.
- [x] **Maintainer-Doku** `docs/maintainer/releasing.md`: die Schritte eines Releases, soweit entschieden und gebaut (Gates, Artefakt, Lizenz), der Freshness-Audit der vendored Baseline als Schritt (die Release-Liste des Kurs-Repos gegen den gepinnten Tag halten, Ergebnis im Ablauf vermerken), die Entscheidungen des Nutzers (Versionsschema, Tag, Registry, Signierung, Stückliste, `make e2e` im Release) als **offen** geführt, nicht gesetzt, und der Pfad im Rang 6 der Quellen in `harness/README.md`. Die Entscheidungen selbst liefert [slice-release-entscheidungen](../open/slice-release-entscheidungen.md).
- [x] **Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md)** als Schritt mit Abfrage in `releasing.md`: Der Trigger „Stabil-Release von detekt 2.x erscheint“ hat einen benannten Träger (den Schritt); die Grenze der Deklaration steht in der Closure. Ein zusätzliches Werkzeug-Target außerhalb von `make gates` ist Entscheidung des Nutzers und liegt in [slice-release-entscheidungen](../open/slice-release-entscheidungen.md); der Ausgang beim Eintreten des Triggers ist ein Slice mit neuer ADR.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün (Closure-Lauf auf dem Stand vor dem Move, Exit 0; die Klassen `werkzeug`, `tests` und `stempel` übersprungen bei passendem Stempel, der Diff berührt nur Doku; `make docs-check` lief).
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
| `docs/user/` | neu | Ist-Zustand für Betreiber und Integratoren |
| `docs/maintainer/releasing.md` | neu | Release-Schritte, Freshness-Audit, Wächter |
| `harness/README.md`, `AGENTS.md` | update | Hinweise „falls vorhanden“ auf Links umstellen; Target des Wächters in §Sensors, falls eines entsteht |
| `tools/mk/` | update | nur falls der Nutzer ein Werkzeug-Target für den Wächter wählt (kein Gate) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [welle-betrieb-diagnose](../welle-betrieb-diagnose.md), [welle-simulations-backend](../done/welle-simulations-backend/welle-simulations-backend.md) und [slice-release-artefakt-lizenz](../done/slice-release-artefakt-lizenz.md) liegen in `done/` (Betrieb, Backend-Anbindung und die Einbettung der Bibliothek existieren, die beschrieben werden), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Nutzer-Doku und Maintainer-Doku als zwei Slices.
- `in-progress` → `open` (blockiert — Carveout?): entfällt; die Entscheidungen des Nutzers zu Versionsschema und Veröffentlichung blockieren diesen Slice nicht, sie tragen [slice-release-entscheidungen](../open/slice-release-entscheidungen.md).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make docs-check`), der Review-Report liegt vor, und die Closure-Notiz trägt die Form des Wächters, das Ergebnis eines ersten Freshness-Audits und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Nutzer-Doku sagt nur, was gebaut und belegt ist (`AGENTS.md` §3.8); Setzungen stehen als solche, und die Doku entsteht nach den Wellen, die sie beschreiben, nicht davor. — **Ausgang:** Eingetreten, behoben (Review F-2): Die Doku trägt je Aussage einen Beleg oder kennzeichnet die Setzung (Review: 11 Schlüssel, Ereignisarten und Nicht-Gebautes gegen den Code gleich; Verifikation bestätigt). Die Bedingung `success` ohne Kennzeichnung ist als Setzung der Anleitung gekennzeichnet; die Verifikation fand keine Aussage über dem Beleg.
- Die dokumentierten Konfigurationsschlüssel können vom Code abweichen, ohne dass ein Sensor es meldet (verwandt mit `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`); ob ein Test die dokumentierte Schlüsselliste gegen die Konfiguration hält, ist offen (Frage in §6). — **Ausgang:** Weiter offen. Die Tabelle und `ServerConfig` halten sich nur durch Lesen; die Doku nennt die Grenze selbst, ob ein Test die Liste gegen die Konfiguration hält, entscheidet der Nutzer (Review F-8, Register `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`).
- Der Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md) braucht Netz, wenn er die Versionsliste abfragt; ein Gate bleibt netzlos, das Target ist ein Werkzeug ohne Gate-Wirkung. Ohne Werkzeug ist der Wächter ein Schritt, der vergessen werden kann — die Grenze der Deklaration steht in der Closure. — **Ausgang:** Weiter offen, als Grenze benannt: Der Wächter ist der Schritt 4 samt Abschnitt in `releasing.md`; er hat keinen Auslöser und kein Gate, wer ihn vergisst, bemerkt den Trigger nur zufällig. Ein Werkzeug-Target ist Nutzerentscheidung in [slice-release-entscheidungen](../open/slice-release-entscheidungen.md).
- Der Freshness-Audit der vendored Baseline ist eine geschuldete Handlung ohne Sensor (`harness/conventions.md`); der Schritt in `releasing.md` ist sein Träger, nicht seine Durchsetzung. — **Ausgang:** Weiter offen, als Grenze benannt: Der Schritt in `releasing.md` ist Träger, nicht Durchsetzung; wer ihn auslässt, wird von keinem Gate erwischt. Ein Sensor kommt nicht mit (`harness/conventions.md`).
- Neue Verzeichnisse unter `docs/` laufen durch das Doku-Gate; `ADR-*`- und `TDS-*`-Kennungen müssen als Link stehen (`BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten`). — **Ausgang:** Entfallen: `make docs-check` ist grün (773 Dateien, 0 Befunde, Verifikation und Closure-Lauf); die Review-Fundstelle zu Kennungen betraf nur die Marker (F-5), nicht Links. Das Register zählt hier keine Instanz.
- Lizenzhinweise zu Abhängigkeiten im Release sind eine offene Rechtsfrage ([slice-release-artefakt-lizenz](../done/slice-release-artefakt-lizenz.md), §6); die Doku behauptet dazu nichts; offen geführt in [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen (`Proposed`), Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`. Bei einer Hinweisdatei im Artefakt trägt dieser Slice sie. — **Ausgang:** Weiter offen, die Doku behauptet dazu nichts (Nutzer-Doku „Grenzen und nicht Belegtes“, `releasing.md` §Offene Entscheidungen); Träger: [slice-release-entscheidungen](../open/slice-release-entscheidungen.md) bzw. [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen. Eine Hinweisdatei im Artefakt entstand nicht.
- Dateirechte und Übergabe der Benutzerliste im Betrieb ([ADR-0011](../../adr/0011-pruefwertformat.md) §Offen Nr. 5, offen auch in [ADR-0018](../../adr/0018-laufzeit-artefakt.md) §Offen (`Proposed`); der Träger [slice-release-artefakt-lizenz](../done/slice-release-artefakt-lizenz.md) schließt ohne Entscheidung, Register `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen`): Die Nutzer-Doku nennt Mount, Secret und `AUTH_USERS_FILE`, behauptet aber keine Rechte und keinen Besitzer, solange der Nutzer nicht entschieden hat; belegt ist nur die root-eigene, weltlesbare Datei per `docker cp`. — **Ausgang:** Weiter offen, die Doku behauptet keine Rechte und keinen Besitzer (gleiche Stellen wie oben); Register `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen`.
- Betriebstext aus [ADR-0013](../../adr/0013-backend-grenzen.md): der Betreiberschalter `-XX:+ExitOnOutOfMemoryError` (Prozessende beim ersten `OutOfMemoryError` statt Fangen; im Spike Exit-Code 3 nach rund 2 s, Neustart über einen Supervisor) und der Pflichttext für Integratoren (Backend und Zeilenquelle suspendierend, blockweise Aufrufe empfohlen) gehören in die Nutzer-Doku; ein Betriebs-Container ist kein Produkt ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)). Der Text ist Teil von Liefer-Punkt 1; diese Zeile hält das Risiko, dass er Spike-Zahlen (Exit-Code 3 nach rund 2 s) als Verhalten des Produkts darstellt. — **Ausgang:** Entfallen: Der Betriebstext steht in der Nutzer-Doku, die Zahlen sind als Spike-Messung mit Bedingungen gekennzeichnet (Schalter, Pflicht für Integratoren, Exit-Code und Dauer als Spike); Review und Verifikation fanden keine Darstellung als Produktverhalten.
- Zwei offene Nutzerentscheidungen aus [slice-explicit-api-umbau](../done/slice-explicit-api-umbau.md) §6 und §7, die die Doku der öffentlichen Fläche (`SPEC-059`) betreffen, nichts entschieden: (1) ein Sensor für die Menge der öffentlichen Klassen gegen die Liste, oder die Grenze bleibt benannt (kein Gate hält sie; Mutation „Nicht-Listen-Member `public`“ bleibt grün); (2) `Principal` steht in der Liste, ist aber nur über den nicht öffentlichen Authentifizierungsadapter erreichbar. Die Nutzer-Doku sagt über die Fläche nicht mehr, als `SPEC-059` belegt. — **Ausgang:** Weiter offen beim Nutzer: Die Doku sagt über die Fläche nicht mehr, als `SPEC-059` belegt; ein Sensor für die Menge der öffentlichen Klassen und der Stand von `Principal` sind nicht entschieden (Doku: „Dass nur die Namen der Liste öffentlich sind, hält kein Gate“).

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

- **Belege:** Review [2026-11-06-04cf0fec-slice-release-doku-prozess](../../../reviews/2026-11-06-04cf0fec-slice-release-doku-prozess.md) (0 HIGH, 1 MEDIUM, 5 LOW, 3 INFO), Verifikation [2026-11-06-d3d2e63-verifikation-slice-release-doku-prozess](../../../reviews/2026-11-06-d3d2e63-verifikation-slice-release-doku-prozess.md) (DoD-konform nach gekürztem Wortlaut; V-1 und V-2 INFO). Bezug: [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0010](../../adr/0010-detekt-pre-release.md).
- **Was hat funktioniert:** Der Review las die 11 Konfigurationsschlüssel, Voreinstellungen und Bereiche gegen `ServerConfig.kt` und `ConfigValidation.kt` und maß Freshness-Audit und detekt-Abfrage nach; das Nicht-Gebaute (Metriken, Spans, `request_id`, `MAX_CONNECTIONS`) steht in der Doku als nicht gebaut. Die Kürzung der DoD (Commit `3258e26e`) machte den Slice schließbar, ohne eine Entscheidung des Nutzers zu setzen.
- **Was ging anders als geplant:** (1) DoD 2 und 3 nannten Entscheidungen des Nutzers als Inhalt der Lieferung (F-1); gekürzt auf das Lieferbare, die Entscheidungen tragen [slice-release-entscheidungen](../open/slice-release-entscheidungen.md). (2) Der Implementer setzte Haken vor der Verifikation und schrieb Rollen-Text in den DoD (F-6); zurückgenommen. (3) Fünf LOW/INFO-Funde der Nutzer- und Maintainer-Doku (F-2 bis F-5, F-7) sind in der Fix-Runde behoben.
- **Frischer Lauf der Closure:** `make docs-check` und `make gates` im Worktree, Exit 0 (siehe Commit-Reihe); `tests`, `werkzeug` und `stempel` übersprungen bei passendem Stempel, weil der Diff weder Quelltext noch Gate noch Konfiguration berührt (Verifikation V-1); `make gates FORCE=1` nicht gefahren.
- **Zuordnung, Stand der Closure:**

  | Aussage | Zuordnung | Wert und Grenze |
  |---|---|---|
  | 11 Schlüssel, Feldzuordnung, Voreinstellungen und Bereiche der Nutzer-Doku | gemessen (Lesen von Code und Spezifikation, Review und Verifikation unabhängig) | kein Test hält die Tabelle gegen `ServerConfig` (V-2, F-8) |
  | Einbettung über `TdsServer.start` | gemessen, eng | Einbettungstest `EmbeddingTest` im Stempel der Klasse `tests`; in der Closure nicht neu ausgeführt |
  | Zahlen zu Backend-Grenzen (173/454 Byte, 1,54 Mio Zeilen, 3099 ms/77 Threads u. a.) | gemessen im Spike | stehen so in [ADR-0013](../../adr/0013-backend-grenzen.md), in der Doku als Spike-Messung mit Bedingungen gekennzeichnet; nicht für das Produkt wiederholt |
  | Freshness-Audit: Latest der Release-Liste des Kurs-Repos ist `v6.18.1`, gepinnt ist `v6.18.1` | gemessen, ein Abruf | Review, 2026-11-06, `gh release list -R pt9912/ai-harness-course`; vom Verifier nicht wiederholt; bestätigt, dass der Pin am Tag des Abrufs aktuell ist |
  | detekt-Beobachtung: neueste Version `2.0.0-alpha.6`, keine Stabilversion 2.x | gemessen, ein Abruf | Review, 2026-11-06, Maven-Metadaten von `dev.detekt:detekt-cli`; die Abfrage ohne Ausgabe stimmt mit der Anleitung; dass sie bei `2.0.0` greift, ist unbelegt (der Review wiederholte die Prüfung an einer Kopie nicht) |
  | Bedingung „Lauf auf dem Release-Stand von `main` ist `success`“ | Festlegung | Setzung der Anleitung, nicht entschieden |
  | Versionsschema, Tag, Registry, Signierung, Stückliste, `make e2e` im Release, Form des Wächters | nicht entschieden | Nutzerentscheidungen, Träger [slice-release-entscheidungen](../open/slice-release-entscheidungen.md) |
  | Menge der öffentlichen Klassen gleich `SPEC-059`-Liste | nicht gemessen | `explicitApi` belegt nur die ausdrückliche Sichtbarkeit; kein Gate hält die Liste |

- **Form des Wächters (§5):** Der Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md) ist der Schritt 4 in `docs/maintainer/releasing.md` samt Abschnitt mit Abfrage und Ausgang „Slice mit neuer ADR (`Supersedes`)“. **Grenze der Deklaration:** Der Schritt hat keinen Auslöser und kein Gate; wer ihn vergisst, wird nicht erwischt. Gleiches gilt für den Freshness-Audit der Baseline. Ein Werkzeug-Target ist Entscheidung des Nutzers.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Gelernt: (1) **Plan-Defekt, neue Klasse:** Eine DoD, die eine Entscheidung des Nutzers als Inhalt der Lieferung nennt, ist für den Implementer nicht schließbar; die Kürzung auf das Lieferbare plus ein Folge-Slice für die Entscheidung löste es ([BEO-TDS/dod-nennt-nutzerentscheidung-als-lieferinhalt](../observations/BEO-TDS/dod-nennt-nutzerentscheidung-als-lieferinhalt/observation.md), 1×; verwandt mit `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung`, dort Abhängigkeit von einem Slice, hier von einer Entscheidung). (2) Zwei neue Klassen, je 1×: Planung und Register-Zeiger in der Nutzer-Doku ([BEO-TDS/planung-und-register-zeiger-in-nutzer-doku](../observations/BEO-TDS/planung-und-register-zeiger-in-nutzer-doku/observation.md)), Haken vor der Verifikation ([BEO-TDS/haken-vor-verifikation-gesetzt](../observations/BEO-TDS/haken-vor-verifikation-gesetzt/observation.md)). (3) Wiederauftreten: `BEO-TDS/aussagen-ueber-messung-hinaus` (F-2, bereits verkörpert in `AGENTS.md` §3.8), `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (F-4, F-5; jetzt 4×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer), `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (F-8, andere Datei, gleiche Klasse). **Benannte Spec-Lücke:** keine. Kein neuer Sensor.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-release-doku-prozess.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`; neu, je `offen`, je eine Evidence-Datei: die drei oben genannten. Die Zähler folgen aus den Dateien. F-7 (Rückgabetyp), F-9 (Host-Werkzeug `curl`/`gh` in einer Handlung) trugen keine Klasse.
- **Folge-Slices:** keine neuen. Träger: [slice-release-entscheidungen](../open/slice-release-entscheidungen.md) (Entscheidungen, Wächter-Form; trägt auch das Risiko, dass das Release-Artefakt im Bau nur geprüft, nicht abgelegt wird).
- **Risiken aus §6:** je ein Ausgang, siehe §6 (neun Risiken: eines eingetreten und behoben, zwei entfallen, sechs weiter offen mit Träger oder benannter Grenze).
- **Offene Entscheidungen beim Nutzer:** Entscheidungen von [slice-release-entscheidungen](../open/slice-release-entscheidungen.md); Sensor Doku-Schlüsselliste gegen `ServerConfig`; Sensor für die Menge der öffentlichen Klassen und Stand von `Principal`; Werkzeug-Target für den Wächter; Lizenzhinweise und Dateirechte.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-referenzabgleich-release](../welle-referenzabgleich-release.md). Anker ↔ Folge-Slice: kein Anker gesetzt; der Folge-Slice existiert als Datei in `open/`. Register ↔ Closure-Notiz: getragen, alle sechs genannten Kennungen existieren als Verzeichnis mit nicht leerem `evidence/`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt Testwerkzeug, Docker-Stages und Dokumentation; keine dieser Strukturen trägt eine eigene Strukturregel (Achse 1) oder eine eigene Inventur-Linie (Achse 2) neben dem Repo insgesamt: eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` — 1×, offen. Verwandt: die Schlüsselliste der Doku kann hinter dem Code zurückbleiben, ohne dass ein Sensor es meldet (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft alle neuen Dokumente dieses Slice: Kennungen als Link, `make docs-check` ist das Gate.
- `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` — 1×, offen. Betrifft den Wächter: der detekt-Pin steht im Katalog und im Lock, ohne Gegenprüfung durch ein Gate.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage der Doku über das Verhalten des Servers trägt einen Beleg oder ist als Festlegung gekennzeichnet.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md)). Betrifft die Beschreibung von Fehlertexten und -nummern: gemessen ist nur, was der Abgleich zeigt.
- Ohne Treffer: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
