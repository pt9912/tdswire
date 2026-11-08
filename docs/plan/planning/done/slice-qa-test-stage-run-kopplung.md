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

**Berührte Spec-Stellen:** keine (`spec/` bleibt unverändert); berührt sind der Erzeuger `tools/spec-abdeckung.sh` und die erzeugte Matrix `docs/plan/spec-abdeckung.md`, die SPEC-045 abdeckt

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-07.

**Autor:** pt9912. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Gate-Stage `test` und die Schwesterstage `test-bericht` in `referenztest-dotnet/Dockerfile` führen den Aufruf von `dotnet test` aus **einer** Zeile der Basis `test-quellen` aus, sodass ein Flag am Skriptaufruf oder in `DOTNET_TEST_ARGS` beide Stages gleich trifft oder abgewiesen wird. Gekoppelt ist die Änderung des Aufrufs über `test-lauf.sh` und `DOTNET_TEST_ARGS`; eine RUN-Zeile von `test` ganz ohne `test-lauf.sh` bleibt ungekoppelt (benanntes Restrisiko, kein Sensor, §6).

**Herkunft.** Nutzerentscheid vom 2026-11-06 („deine Empfehlungen“) zum Vorschlag der Closure von [slice-spec-abdeckung-liest-csharp](../done/slice-spec-abdeckung-liest-csharp.md) und zur Beobachtung `BEO-TDS/schwesterstage-ohne-gleichlauf-sensor`: Mutation 1 des Verifiers (V-2, ein `--filter` nur in der `RUN`-Zeile von `test`) ließ die Matrix weiterzählen und nichts wurde rot; die Basis trägt heute `COPY` und `DOTNET_TEST_ARGS`, die `RUN`-Zeile steht je Stage für sich.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Gleichlauf-Sensor als eigenes Gate (ein Selbsttest des Skripts `test-lauf.sh` ist davon nicht berührt und gehört zum Slice, siehe §6) — der Vorschlag ist die Kopplung in der Bauart; ein Sensor wäre die Alternative und braucht eine eigene Entscheidung (Klassen und Stempel, `harness/README.md` §Sensors).
- Die Stages `integration`, `eigenes-backend`, `kompatibilitaet` und `vergleich` — sie bauen ohne `dotnet test` und teilen das Rezept nicht.
- Der Erzeuger `tools/spec-abdeckung.sh` über den Kommentar hinaus — seine Logik bleibt; nur der Satz „weitere Flags in der RUN-Zeile von `test` sind nicht gekoppelt“ wird nachgezogen.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei; Abweichung vom Plan-Wortlaut: Selbsttest von `test-lauf.sh` und Exit 3 ohne ausgeführten Test gehören dazu, siehe §6):

- [x] **Kopplung in der Bauart** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): Der Aufruf `dotnet test` samt `$DOTNET_TEST_ARGS` steht an einer Stelle der Basis `test-quellen` (Variable oder Skript), `test` ruft ihn unverändert, `test-bericht` fügt nur den TRX-Logger und das Ergebnisverzeichnis hinzu; rote Tests brechen `test` weiter und lassen `test-bericht` den Bericht behalten. Beleg: `make test`, `make spec-abdeckung`, `make gates`.
- [x] **Mutationsbeleg und Kommentare** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): Zwei Mutationen laufen und stehen im Report: ein Flag (`--filter`) an der gemeinsamen Stelle wirkt auf beide Stages (die Matrix folgt dem Gate), und ein Flag, das nur `test` verengen soll, ist am gemeinsamen Aufruf nicht mehr möglich; der Kommentar im `Dockerfile` und der Satz im Kopf von `tools/spec-abdeckung.sh` („nicht gekoppelt“) sind auf den neuen Stand nachgezogen (`AGENTS.md` §3.7, §3.8).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor — Nachbesserung ohne zweiten Review (Vermerk in §7: `435c4831` vom Verifier wie ein Review bewertet, `fc5cd675` nur durch Handmessung und Selbsttest)
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen (geprüft: kein `liegt in`-Feld, kein Folge-Slice, alle genannten `BEO-TDS/…` mit nicht leerem `evidence/`) — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Dockerfile` | update | gemeinsamer Aufruf in der Basis `test-quellen`, Stages `test` und `test-bericht` rufen ihn ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)) |
| `referenztest-dotnet/test-lauf.sh` | neu | der eine Aufruf von `dotnet test` (Modi `test`, `bericht`; jedes andere Argument Exit 2); per `COPY` in der Basis `test-quellen`, im Stempel der Klasse `tests` durch `referenztest-dotnet` ganz gedeckt |
| `tools/spec-abdeckung.sh` | update | Kommentar im Kopf und der gedruckte Satz der Matrix (Zeile `Der Bericht der C#-Tests …`) auf die Kopplung nachziehen; keine Änderung der Logik |
| `docs/plan/spec-abdeckung.md` | update | `make spec-abdeckung` schreibt sie neu, weil der Erzeuger den Satz druckt |
| `tools/stempel/*.eingaenge` | update | Klasse `tests`: `referenztest-dotnet` ist ganz gelistet, kein Eintrag nötig; Klasse `werkzeug`: Eintrag `referenztest-dotnet/test-lauf.sh` für die Stage `skript-selbsttest` (`make stempel-eingaenge-pruefung`, `make stempel-abbild`) |
| `tools/test-lauf-selbsttest.sh` | neu | Selbsttest von `test-lauf.sh` gegen einen `dotnet`-Stub (inklusive fester Locale), 9 Mutationen; Abweichung vom Plan-Wortlaut, siehe §6 |
| `Dockerfile` (Stage `skript-selbsttest`), `tools/mk/skript-selbsttest.mk`, `harness/README.md` | update | Selbsttest eingehängt; Hilfetext und Zeile der Sensors-Tabelle nachgezogen |
| `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/observations/BEO-TDS/schwesterstage-ohne-gleichlauf-sensor/state.md`, die Slice-Datei | update | Lifecycle-Dateien: Slice-Move (`slice-mv`), Roadmap-Zeile, Verweis im Register |

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

- **Der Bericht verträgt keine rote Gate-Logik.** `test-bericht` behält den Bericht bei roten Tests (`|| echo`), `test` bricht ab; die gemeinsame Zeile darf diesen Unterschied nicht verwischen. — **Ausgang:** entfallen. Gemessen im [Review](../../../reviews/2026-11-07-3f4568e-review-slice-qa-test-stage-run-kopplung.md) (M4) und in der [Verifikation](../../../reviews/2026-11-07-435c4831-verifikation-slice-qa-test-stage-run-kopplung.md): mit einem roten Test (`Assert.Equal(1, 2)`) bricht `test` mit `Failed: 1`, Exit 1; `test-bericht` baut durch und das TRX enthält die roten Ergebnisse.
- **Abweichung vom Plan-Wortlaut (Review F-2, F-3).** Die Abweisung in `test-lauf.sh` bekam einen Selbsttest (`tools/test-lauf-selbsttest.sh`, Stage `skript-selbsttest`, Klasse `werkzeug`); der Plan nannte nur Hand-Mutationen. Der Modus `test` bricht zudem mit Exit 3, wenn `dotnet test` keinen Test ausführt (Filter ohne Treffer); das verschärft das Gate (`AGENTS.md` §3.6 erlaubt es). Beides ist kein Gleichlauf-Sensor. Die Treffer-Prüfung liest die englische Meldung; `test-lauf.sh` setzt deshalb `LC_ALL=C.UTF-8`, `LANG=C.UTF-8`, `LANGUAGE=en` selbst (Handmessung 2026-11-07 mit `ENV LC_ALL=de_DE.UTF-8` im Dockerfile und Filter ohne Treffer: Exit 3; ohne die feste Locale im Skript meldet `dotnet` „Kein Test entspricht …“ und die Stage endet mit Exit 0). — **Ausgang:** eingetreten und gedeckt (Nachbesserung `435c4831` und `fc5cd675`, siehe §7). Die Grenze der Verschärfung steht im nächsten Punkt.
- **Ungekoppelte RUN-Zeile.** Eine RUN-Zeile von `test` ganz ohne `test-lauf.sh` (Review M3) wird von keinem Gate bemerkt; sie bleibt benanntes Restrisiko, ein Sensor wäre eine eigene Entscheidung. — **Ausgang:** weiter offen als akzeptiertes Negativ. Träger: Register `BEO-TDS/schwesterstage-ohne-gleichlauf-sensor` (`state.md`, mit Re-Evaluierungs-Trigger); der Satz „bleibt ungekoppelt“ steht im `Dockerfile`, im Kopf von `test-lauf.sh` und `tools/spec-abdeckung.sh` und in der gedruckten Matrix.
- **Grenze der Treffer-Prüfung von Exit 3 (Verifikation V-1, V-4).** Die Prüfung liest die englische Meldung `No test matches` und `No test is available`. Gemessen: nur `de_DE.UTF-8` (`LANG` und `LC_ALL`) schreibt am echten `dotnet` die deutsche Meldung („Kein Test entspricht …“), `DOTNET_CLI_UICULTURE` mit `de-DE` und `fr-FR` ändert sie nicht; weitere Locales sind nicht gemessen. `test-lauf.sh` setzt die Locale selbst fest (`fc5cd675`); dass die feste Locale am echten `dotnet` greift, ist eine einmalige Handmessung (2026-11-07, `ENV LC_ALL=de_DE.UTF-8` im Dockerfile, Filter ohne Treffer: Exit 3), kein Gate; der Selbsttest läuft gegen einen Stub und belegt die Umgebung, nicht den Wortlaut des echten `dotnet`. Ein Image-Update (SDK-Pin) kann die Meldung ändern; dann endet ein Filter ohne Treffer wieder mit Exit 0. Die Suche läuft zudem über die gesamte Ausgabe: ein Test, der diese Wörter druckt, machte das Gate rot (nicht eingetreten). — **Ausgang:** weiter offen als benannte Grenze. Träger: Register `BEO-TDS/gate-liest-wortlaut-einer-werkzeugmeldung-ohne-locale`; Trigger zum Nachmessen: jede Änderung des SDK-Pins oder der Basis von `referenztest-dotnet/Dockerfile`.
- **Stempel-Eingänge.** Ein neues Skript oder eine geänderte `COPY`-Zeile wirkt auf den Hash der Klasse `tests`. — **Ausgang:** eingetreten und gelöst. `referenztest-dotnet/test-lauf.sh` steht in `tools/stempel/werkzeug.eingaenge` und über `referenztest-dotnet` in `tools/stempel/tests.eingaenge`; `make stempel-eingaenge-pruefung`, `make stempel-abbild`, `make stempel-gruppe` und `make gate-zuordnung` sind grün (Verifikation).

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

**Gegenstand.** Die Stages `test` und `test-bericht` führen `dotnet test $DOTNET_TEST_ARGS` über **ein** Skript aus, `referenztest-dotnet/test-lauf.sh` (Modi `test` und `bericht`, `COPY` in der Basis `test-quellen`). Ein Flag am Skriptaufruf wird mit Exit 2 abgewiesen, ein Filter in `DOTNET_TEST_ARGS` trifft beide Stages gleich. Zusätzlich (Abweichung vom Plan-Wortlaut, §6): Selbsttest `tools/test-lauf-selbsttest.sh` (Stage `skript-selbsttest`, Klasse `werkzeug`, 9 Mutationen) und Exit 3 des Modus `test`, wenn `dotnet test` keinen Test ausführt, bei fester Locale im Skript.

**Berichte.** [Review](../../../reviews/2026-11-07-3f4568e-review-slice-qa-test-stage-run-kopplung.md) F-1 bis F-7 (0 HIGH, 2 MEDIUM) und [Verifikation](../../../reviews/2026-11-07-435c4831-verifikation-slice-qa-test-stage-run-kopplung.md) V-1 bis V-6 mit vier Bedingungen. Umgang mit den Befunden:

| Befund | Behandlung |
|---|---|
| F-1 (Zielsatz breiter als die Kopplung) | Zielsatz in §1 und Roadmap auf den belegten Stand eingeschränkt (`435c4831`): gekoppelt über `test-lauf.sh` und `DOTNET_TEST_ARGS`, eine RUN-Zeile ohne das Skript bleibt ungekoppelt |
| F-2 (Abweisung ohne Test) | Selbsttest `tools/test-lauf-selbsttest.sh` (`435c4831`) |
| F-3 (Filter ohne Treffer, Gate grün) | Exit 3 im Modus `test` (`435c4831`), Grenze: Locale, siehe V-1 |
| F-4, F-5 (Plan-Kopf, Roadmap-Zeile) | Plan-Kopf und Roadmap-Zeile nachgezogen; die Roadmap nennt hier den Stand mit Beleg |
| F-6, F-7 (INFO) | keine Handlung; Stempel-Lauf und Cache-Verhalten im Verifikationsbericht belegt |
| V-1 (Treffer-Prüfung hängt am englischen Wortlaut) | `fc5cd675`: feste Locale im Skript (`LC_ALL=C.UTF-8`, `LANG=C.UTF-8`, `LANGUAGE=en`), Selbsttest und Mutation „feste Locale gestrichen“; Grenze in §6 mit Träger |
| V-2 (Selbsttest und Exit 3 nicht in der DoD) | in §2 und §6 als Abweichung ausgewiesen; hier festgehalten, die Liefer-Punkte bleiben zwei |
| V-3 (Exit 3 in keiner Sensors-Zeile) | `fc5cd675`: Zeile `make referenztest-dotnet` in `harness/README.md` (182 Zeichen) und Kopf von `tools/mk/referenztest-dotnet.mk` |
| V-4 bis V-6 (INFO) | V-4 als Grenze in §6 und im Register; V-5, V-6 keine Handlung |

**Nachbesserung ohne zweiten Review** (`BEO-TDS/nachbesserung-ohne-zweiten-review`, seit `0bc03b4d` auf `main` verkörpert). Betroffen sind die Commits `435c4831` (Selbsttest `tools/test-lauf-selbsttest.sh`, Exit 3 in `test-lauf.sh`, Einhängen in `Dockerfile` und `tools/mk/skript-selbsttest.mk`, Eingangsliste `tools/stempel/werkzeug.eingaenge`) und `fc5cd675` (feste Locale in `test-lauf.sh`, neuer Fall und neue Mutation im Selbsttest, Zeile in `harness/README.md`, Kopf von `tools/mk/referenztest-dotnet.mk`). Grund: beide ändern Skripte und Build-Dateien; ein Re-Review in frischem Kontext lief nicht. Stattdessen geprüft: `435c4831` hat der Verifier wie einen Reviewer bewertet (Abschnitt „Review der Nachbesserung“ des Verifikationsberichts, Befunde V-1 bis V-6, eigene Läufe und Mutationen am echten `dotnet`). `fc5cd675` kam nach der Verifikation und ist **nur** durch eine Handmessung mit echtem `dotnet` (`ENV LC_ALL=de_DE.UTF-8` im Dockerfile, Filter ohne Treffer: Exit 3, ohne die feste Locale im Skript Exit 0) und durch den Selbsttest (9 Mutationen) geprüft; weder gereviewt noch erneut verifiziert. Der Verifier hat ausdrücklich festgehalten, dass seine Bewertung keinen eigenen Re-Review ersetzt. Der Haken der Review-Zeile in §2 trägt diesen Vermerk.

**Zuordnung Test | Festlegung.**

| Aussage | Träger | Art |
|---|---|---|
| Ein Argument am Skriptaufruf wird abgewiesen (Exit 2) | Selbsttest, Fälle mit Stub und Mutationen „Argumentzaehlung gestrichen“, „unbekannter Modus Exit 0“; Review M1, M5 (Hand, echter Build) | Test (Stub) und Messung |
| Rote Tests brechen `test`, `test-bericht` behält den Bericht | Selbsttest (Mutationen „Exit des Modus test verschluckt“, „Modus bericht bricht bei Rot ab“); Review M4 und Verifikation mit echtem `dotnet` (`Failed: 1`, Exit 1, TRX mit roten Ergebnissen) | Test (Stub) und Messung |
| Logger, Ergebnisverzeichnis, Variable nur dort, wo sie hingehören | Selbsttest, Mutationen „Logger im Modus test“, „Ergebnisverzeichnis gestrichen“, „Argumente der Variable gestrichen“ | Test (Stub) |
| Filter in `DOTNET_TEST_ARGS` trifft beide Stages gleich | Review M2a (ein Test in beiden Stages), keine dauerhafte Prüfung | Messung, Festlegung der Bauart |
| Filter ohne Treffer: Exit 3 | Selbsttest, Mutation „Treffer-Pruefung gestrichen“ (Stub); am echten `dotnet` Review M2b und Verifikation (Image-Standard, Exit 3) | Test (Stub) und Messung |
| Die Prüfung greift bei deutscher Locale der Umgebung | Selbsttest (Stub mit gesetzter Locale), Mutation „feste Locale gestrichen“; am echten `dotnet` **nur** die Handmessung vom 2026-11-07 mit `de_DE.UTF-8` | Test (Stub) und einmalige Handmessung, kein Gate |
| Eine RUN-Zeile von `test` ohne `test-lauf.sh` bleibt ungekoppelt | Review M3 (kein Sensor wird rot) | Festlegung (akzeptiertes Negativ) |
| 126 Tests, Matrix byte-gleich zum Erzeuger | Verifikation (frischer Lauf der Stage `test`, `make spec-abdeckung` zerlegt, `cmp`) | Messung |

**Gelernt (Lerneintrag).** Vier Klassen, drei davon aus dem Slice, eine aus dem Betrieb:

1. *Eine Prüfung, die den Wortlaut einer Werkzeugmeldung liest, ohne die Locale festzulegen.* Das Gate „ein Lauf ohne Test ist rot“ hing an der englischen Meldung von `dotnet test`; mit `de_DE.UTF-8` in der Umgebung blieb die Stage grün ohne einen Test (V-1). Behoben durch feste Locale im Skript; die Grenze (nur `de_DE.UTF-8` gemessen, Wortlaut des echten `dotnet` nur durch eine Handmessung, ein Image-Update kann die Meldung ändern) trägt das Register: neu `BEO-TDS/gate-liest-wortlaut-einer-werkzeugmeldung-ohne-locale` (1×).
2. *Ein Selbsttest mit Stub belegt das Skript, nicht den Wortlaut des echten Werkzeugs.* Der Stub gibt den Satz aus, den das Skript sucht; stimmt der Satz nicht mit dem echten `dotnet` überein, bleibt der Selbsttest grün. Dieselbe Klasse wie `BEO-TDS/test-belegt-nur-den-fake` (jetzt 8×, bereits verkörpert im Reviewer-Skill); Beleg als Datei in dessen `evidence/`.
3. *Eine Ausnahme („gekoppelt“) breiter als ihr Beleg.* Der Zielsatz sagte „nicht mehr unbemerkt“, gemessen war eine Form der Änderung (F-1). `BEO-TDS/aussagen-ueber-messung-hinaus` (jetzt 77×, bereits verkörpert in `AGENTS.md` §3.8); Beleg als Datei in dessen `evidence/`.
4. *Betriebsbeobachtung: ein Vorgänger-Lauf blieb ohne Bericht an den Aufrufer stehen* (Angabe des Auftrags dieser Closure, nicht selbst beobachtet). Register `BEO-TDS/agent-bleibt-ohne-bericht-stehen` (1×); Folge für diesen Slice: keine, der Stand ist aus den Commits und den Berichten rekonstruiert.

- **Steering-Loop-Eintrag:** keine neue Regel verkörpert. Die Regeln zu 2 und 3 gelten bereits (Reviewer-Skill, `AGENTS.md` §3.8); Klasse 1 und 4 stehen mit je 1× unter der Schwelle. Der Lese-Schritt des Registers nennt dazu: *kein Eintrag über der Schwelle, der nicht schon einen Ausgang trägt* (`BEO-TDS/nachbesserung-ohne-zweiten-review` 8×, verkörpert; `BEO-TDS/test-belegt-nur-den-fake` 8×, verkörpert; `BEO-TDS/aussagen-ueber-messung-hinaus` 77×, verkörpert). Eine benannte Spec-Lücke entstand nicht (`spec/` unverändert).
- **Beobachtungs-Register (`../observations/`):** neu `BEO-TDS/gate-liest-wortlaut-einer-werkzeugmeldung-ohne-locale` und `BEO-TDS/agent-bleibt-ohne-bericht-stehen` (je 1×); weitere Dateien in `evidence/` von `BEO-TDS/nachbesserung-ohne-zweiten-review`, `BEO-TDS/test-belegt-nur-den-fake`, `BEO-TDS/aussagen-ueber-messung-hinaus` und `BEO-TDS/schwesterstage-ohne-gleichlauf-sensor` (jetzt 2×).
- **Ausgang von `BEO-TDS/schwesterstage-ohne-gleichlauf-sensor`:** Die Kopplung ist in der Bauart verkörpert (`referenztest-dotnet/test-lauf.sh`, Kommentare in `Dockerfile`, `tools/spec-abdeckung.sh`, gedruckte Matrix). Restrisiko: eine RUN-Zeile von `test` ohne `test-lauf.sh` bleibt ohne Sensor (Review M3). Grenze entschieden (akzeptiertes Negativ, Nutzerentscheid 2026-11-06 „deine Empfehlungen“ zu Bauart statt Sensor); Re-Evaluierungs-Trigger: die Beobachtung erreicht 3× oder eine weitere Schwesterstage mit eigener `RUN`-Zeile tritt hinzu. Die Beobachtung bleibt dann offen unter der Schwelle (kein `gestrichen`: die Ursache entfällt nicht ganz).
- **Ausgang von `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`:** unverändert verkörpert als Handlung (`implement-slice` Schritt 24). In diesem Slice trat kein Veralten auf: die Verifikation verglich die erzeugte Matrix byte-gleich mit der committeten; keine neue `evidence/`-Datei, der Beleg steht in `state.md`.
- **Folge-Slices:** keine. Der Sensor für die ungekoppelte RUN-Zeile wäre eine eigene Entscheidung (Trigger oben).
- **Risiken aus §6:** alle fünf tragen einen Ausgang (§6): zwei entfallen oder gelöst, eins eingetreten und gedeckt, zwei weiter offen mit Träger im Register.
- **Gate-Läufe.** `make gates FORCE=1` Exit 0 (46 s) vor dem Move, `make docs-check` 0 Befunde; die Stage `test` des .NET-Gerüsts kam dabei aus dem Layer-Cache und ist **kein** Beleg für die Tests. Frischer Beleg der Tests ist der ungecachte Lauf der Verifikation (126 Tests, `--no-cache-filter test`, am Stand `435c4831`); für `fc5cd675` (feste Locale) gibt es keinen frischen Lauf der Stage `test`, sondern die Handmessung und den ungecachten Selbsttest (9 Mutationen).

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
