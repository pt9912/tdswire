# Slice slice-qa-test-stage-run-kopplung: `RUN`-Zeile der Gate-Stage `test` an die Basis `test-quellen` koppeln (Gleichlauf mit `test-bericht`)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)

**Berührte Spec-Stellen:** SPEC-045 (Abdeckungsmatrix liest C#-Tests; Bericht aus der Stage `test-bericht`)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Gate-Stage `test` und die Schwesterstage `test-bericht` in `referenztest-dotnet/Dockerfile` führen den Aufruf von `dotnet test` aus **einer** Zeile der Basis `test-quellen` aus, sodass ein Flag, das nur die Gate-Stage verengt (etwa `--filter`), nicht mehr unbemerkt auseinanderläuft und die Abdeckungsmatrix keine Tests als bestanden zählt, die das Gate nicht ausführt.

**Herkunft.** Nutzerentscheid vom 2026-11-06 („deine Empfehlungen“) zum Vorschlag der Closure von [slice-spec-abdeckung-liest-csharp](../done/slice-spec-abdeckung-liest-csharp.md) und zur Beobachtung `BEO-TDS/schwesterstage-ohne-gleichlauf-sensor`: Mutation 1 des Verifiers (V-2, ein `--filter` nur in der `RUN`-Zeile von `test`) ließ die Matrix weiterzählen und nichts wurde rot; die Basis trägt heute `COPY` und `DOTNET_TEST_ARGS`, die `RUN`-Zeile steht je Stage für sich.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Gleichlauf-Sensor als eigenes Gate — der Vorschlag ist die Kopplung in der Bauart; ein Sensor wäre die Alternative und braucht eine eigene Entscheidung (Klassen und Stempel, `harness/README.md` §Sensors).
- Die Stages `integration`, `eigenes-backend`, `kompatibilitaet` und `vergleich` — sie bauen ohne `dotnet test` und teilen das Rezept nicht.
- Der Erzeuger `tools/spec-abdeckung.sh` über den Kommentar hinaus — seine Logik bleibt; nur der Satz „weitere Flags in der RUN-Zeile von `test` sind nicht gekoppelt“ wird nachgezogen.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [ ] **Kopplung in der Bauart** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): Der Aufruf `dotnet test` samt `$DOTNET_TEST_ARGS` steht an einer Stelle der Basis `test-quellen` (Variable oder Skript), `test` ruft ihn unverändert, `test-bericht` fügt nur den TRX-Logger und das Ergebnisverzeichnis hinzu; rote Tests brechen `test` weiter und lassen `test-bericht` den Bericht behalten. Beleg: `make test`, `make spec-abdeckung`, `make gates`.
- [ ] **Mutationsbeleg und Kommentare** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): Zwei Mutationen laufen und stehen im Report: ein Flag (`--filter`) an der gemeinsamen Stelle wirkt auf beide Stages (die Matrix folgt dem Gate), und ein Flag, das nur `test` verengen soll, ist am gemeinsamen Aufruf nicht mehr möglich; der Kommentar im `Dockerfile` und der Satz im Kopf von `tools/spec-abdeckung.sh` („nicht gekoppelt“) sind auf den neuen Stand nachgezogen (`AGENTS.md` §3.7, §3.8).

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
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
| `referenztest-dotnet/Dockerfile` | update | gemeinsamer Aufruf in der Basis `test-quellen`, Stages `test` und `test-bericht` rufen ihn ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)) |
| `tools/spec-abdeckung.sh` | update | Kommentar im Kopf auf die Kopplung nachziehen; keine Änderung der Logik |
| `docs/plan/spec-abdeckung.md` | update | nur falls `make spec-abdeckung` den Text des Erzeugers ändert (Matrix von Hand nachziehen, wenn der Erzeuger sie schreibt) |
| `tools/stempel/*.eingaenge` | prüfen | Eingänge der Klasse `tests`; ein neues Skript im `COPY` braucht einen Eintrag (`make stempel-eingaenge-pruefung`) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Nutzer hat den Vorschlag am 2026-11-06 bestätigt (erfüllt); ein Verantwortlicher ist eingetragen. Keine Wartebedingung auf andere Slices.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Kopplung zwingt `test-bericht` zu einer anderen Form des Aufrufs (Logger lässt sich nicht als zusätzliche Variable anfügen); Schnittvorschlag: Sensor statt Bauart, mit eigener Entscheidung.
- `in-progress` → `open` (blockiert): `--network=none` oder der Cache der Stage hängt an der Zeile und die Kopplung ändert die Stempel-Eingänge in einer Weise, die eine ADR braucht (Gates dürfen nicht ohne ADR gelockert werden, `AGENTS.md` §3.6).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beide Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, die Mutationen stehen im Report, `BEO-TDS/schwesterstage-ohne-gleichlauf-sensor` trägt den Ausgang, und die Closure-Notiz hat den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Der Bericht verträgt keine rote Gate-Logik.** `test-bericht` behält den Bericht bei roten Tests (`|| echo`), `test` bricht ab; die gemeinsame Zeile darf diesen Unterschied nicht verwischen. — **Ausgang:** offen bis zur Closure.
- **Stempel-Eingänge.** Ein neues Skript oder eine geänderte `COPY`-Zeile wirkt auf den Hash der Klasse `tests`. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt die Docker-Stages des Referenztests und den Kopfkommentar des Erzeugers der Abdeckungsmatrix, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/schwesterstage-ohne-gleichlauf-sensor` (Gegenstand, 1×, Vorschlag bestätigt), `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
