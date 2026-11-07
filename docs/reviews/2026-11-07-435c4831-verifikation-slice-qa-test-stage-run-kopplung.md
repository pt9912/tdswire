# Verifikationsbericht: slice-qa-test-stage-run-kopplung (HEAD 435c4831)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-qa-test-stage-run-kopplung` (`in-progress/`, Zweig `worktree-agent-a679b3097d69efc71`, HEAD `435c4831`, enthält `main` bis `43511f37`) gegen die DoD §2, die Plan-Tabelle §3, [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0023](../plan/adr/0023-gate-klassen-und-stempel.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) und `AGENTS.md` §3.1 bis §3.8.
* Maßstab des Nutzers: „Spec ist Zielbild“. Verifiziert wird die Zusage (DoD, Plan), nicht der Diff; Belege sind eigene Läufe. Nichts am Produktivcode geändert; die Mutationen liefen an Kopien im Scratchpad.
* Die Nachbesserung `435c4831` (Selbsttest, Exit 3, Einhängen, Eingangsliste) hatte keinen zweiten Review; sie ist hier wie ein Review bewertet (Abschnitt „Review der Nachbesserung“). Das ersetzt keinen Re-Review durch einen eigenen Kontext.
* Der Review-Report des Vorgängers liegt als `2026-11-07-3f4568e-review-slice-qa-test-stage-run-kopplung`; seine Mutationen M1 bis M5 sind hier nachgefahren.

## Eigene Läufe (Belege)

Host: Lastmittel 25 bis 28 durch andere Läufe (kein ruhiger Host; Einzelmessungen). Alle Läufe am Arbeitsbaum bei HEAD `435c4831`.

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0, 32 s. Die Stage `test` des .NET-Gerüsts kam aus dem Layer-Cache (89 `CACHED` im Log): der Lauf belegt die Tests **nicht** (Cache-Falle G1), nur die übrigen Gates und Stempel. `d-check: 1189 Datei(en) geprüft, 0 Befund(e)`; `gate-zuordnung: 16 Gates … jedes in genau einer Klasse`, `gate-zuordnung-selbsttest: 33 Faelle`; `stempel-selbsttest: 118`, `stempel-eingaenge-selbsttest: 95`, `stempel-abbild: 101`, `stempel-gruppe: 67 Faelle wie erwartet`; `stempel-eingaenge: ok` |
| `make skript-selbsttest` ungecacht (`BUILDER_FLAGS="--no-cache-filter skript-selbsttest"`) | Exit 0, 8,7 s; Schritt lief mit Ausgabe: `test-lauf-selbsttest: ok - Faelle und 8 Mutationen wie erwartet` |
| `make clean-build` (Gradle ohne Cache) | Exit 0, 8 min 10 s; Stages `build-jar`, `test`, `detekt` ohne Cache. Er berührt das .NET-Gerüst nicht (G1: kein Beleg für Punkt 1 der DoD) |
| `make referenztest-dotnet BUILDER_FLAGS="--no-cache-filter test"` (frisch, echtes `dotnet`) | Exit 0; `Passed! - Failed: 0, Passed: 126, Skipped: 0, Total: 126` |
| `make spec-abdeckung` von Hand zerlegt (kein Schreiben ins Repo): `test-bericht-export` mit `--no-cache-filter test-bericht`, dann `spec-abdeckung-export` mit `--no-cache-filter spec-abdeckung` und `--build-context dotnet-bericht` | Exit 0 beider Builds, Gradle-Teil frisch (1156 `PASSED`-Zeilen im Log, nicht gecacht); `waisen` = 9; die erzeugte Matrix ist byte-gleich zu `docs/plan/spec-abdeckung.md` (`cmp`), also stimmt der gedruckte Satz mit dem Erzeuger überein |
| `make stempel-eingaenge-pruefung`, `make stempel-eingaenge`, `make stempel-gruppe`, `make stempel-abbild`, `make stempel-selbsttest`, `make gate-zuordnung` | in `make gates FORCE=1` gelaufen und grün (Zahlen oben) |
| `make docs-check` | vor diesem Bericht 0 Befunde (im Gate-Lauf); nach dem Bericht siehe Schluss |
| Mutationen M1 bis M5 und Zusatzmessungen | siehe Abschnitt „Mutationen“ |

## Gesamturteil

**Closure-fähig: ja, unter den Bedingungen 1 bis 4 unten** (alle liegen beim Planner oder sind Dokumentation; kein Befund verlangt einen Rückbau). Beide Liefer-Punkte der DoD sind im Code und an eigenen Läufen belegt: Der Aufruf von `dotnet test` samt `$DOTNET_TEST_ARGS` steht in `test-lauf.sh` (Basis `test-quellen`), beide Stages rufen es, ein Flag am Skriptaufruf bricht mit Exit 2, ein Filter in `DOTNET_TEST_ARGS` trifft beide Stages. Eine Verletzung von Plan, ADR oder Hard Rule habe ich nicht gefunden. Offen sind eine Grenze der Exit-3-Verschärfung (V-1, Locale), zwei Lücken in Dokumentation und Plan-Tabelle (V-2, V-3) und die Closure-Handgriffe des Planners.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Kopplung in der Bauart: ein Aufruf an einer Stelle der Basis, `test` ruft ihn unverändert, `test-bericht` fügt nur Logger und Ergebnisverzeichnis hinzu; rot bricht `test`, `test-bericht` behält den Bericht; Beleg `make test`, `make spec-abdeckung`, `make gates` | erfüllt, mit Zusatz | `referenztest-dotnet/test-lauf.sh`, `COPY` in `test-quellen`. Frisch: 126 Tests grün (Stage `test`). Roter Test (zusätzliche `Smoke/Rot.cs`, `Assert.Equal(1, 2)`): Stage `test` `Failed: 1, Passed: 126, Total: 127`, `exit code: 1`; Stage `test-bericht` baut durch, meldet „der Testlauf ist rot“, TRX enthält die roten Ergebnisse (2 `Failed`-Ergebnisse, wie im Review M4). „`test` ruft ihn **unverändert**“ stimmt für den Modus `test` nicht mehr wörtlich: er hat Exit 3 dazubekommen (Verschärfung, siehe V-1, V-4) |
| 2 | Mutationsbeleg und Kommentare: zwei Mutationen im Report; Kommentar im `Dockerfile` und Satz im Kopf von `tools/spec-abdeckung.sh` nachgezogen | erfüllt | Mutationen im Review (M1, M2a) und hier nachgefahren; Kommentare in `referenztest-dotnet/Dockerfile`, `tools/spec-abdeckung.sh`, gedruckter Satz und `docs/plan/spec-abdeckung.md` einheitlich („gekoppelt über test-lauf.sh und DOTNET_TEST_ARGS; eine RUN-Zeile ohne test-lauf.sh bleibt ungekoppelt“) |
| N1 | `make gates` grün | erfüllt, mit Cache-Vorbehalt | Exit 0; Tests frisch belegt durch den ungecachten Lauf oben |
| N2 | Review durchgeführt, Report liegt vor | erfüllt, Nachbesserung ohne zweiten Review | `2026-11-07-3f4568e-review-…`; `435c4831` hier bewertet |
| N3 | Haken setzt der Planner | **keiner gesetzt, richtig** | `grep -c '^- \[x\]'` auf dem Slice: 0 |
| N4 | Doku-Update bei Vertrag | erfüllt | `harness/README.md` (Zeile `make skript-selbsttest`, 195 Zeichen; vorher 200), `make help`-Text; `spec/` unverändert, wie der Kopf sagt (`git diff 43511f37..HEAD -- spec` leer). Die Exit-3-Verschärfung steht in keiner Sensors-Zeile (V-3) |
| N5 | Closure-Notiz mit Lerneintrag | offen, erwartet | §7 „Wird bei der Closure gefüllt“ |
| N6 | Register fortgeschrieben | offen, erwartet | `schwesterstage-ohne-gleichlauf-sensor/state.md` trägt nur den nachgezogenen Link, kein Ausgang, keine neue `evidence/`-Datei |
| N7 | Jedes Risiko aus §6 trägt einen Ausgang | offen, erwartet | alle vier „offen bis zur Closure“; Vorschlag unten |
| N8 | Drei Paarungen | offen bis Welle-Closure | Anker nur, wenn die Closure etwas verkörpert |

## Plan-vs-Code-Diff (§3)

| Zeile des Plans | Diff | Urteil |
|---|---|---|
| `referenztest-dotnet/Dockerfile` | ja: `COPY test-lauf.sh ./` in `test-quellen`, beide Stages `sh test-lauf.sh <Modus>`; Kommentare | ja |
| `referenztest-dotnet/test-lauf.sh` (neu) | ja | ja; Modus `test` hat zusätzlich Exit 3 (Abweichung, siehe §6-Zeile und V-1) |
| `tools/spec-abdeckung.sh` | ja: Kopfkommentar und gedruckter Satz, Logik unverändert | ja |
| `docs/plan/spec-abdeckung.md` | ja, byte-gleich zum frischen Erzeuger-Lauf | ja |
| `tools/stempel/*.eingaenge` | ja: `werkzeug.eingaenge` bekommt `referenztest-dotnet/test-lauf.sh` plus Kopfzeile; `tests.eingaenge` unverändert (`referenztest-dotnet` ganz) | ja |
| `tools/test-lauf-selbsttest.sh` (neu) | ja | ja; im Plan als Abweichung ausgewiesen (V-2: §1 sagt dazu das Gegenteil) |
| `Dockerfile` (Stage `skript-selbsttest`), `tools/mk/skript-selbsttest.mk`, `harness/README.md` | ja | ja |
| Roadmap, Register-`state.md`, Slice-Datei | ja (Link `in-progress/`; Roadmap-Zeile in Arbeit, Zeile „Nichts in Arbeit.“ entfernt) | ja; siehe Merge unten |
| nicht im Plan | `docs/reviews/…3f4568e…` (Reviewer) | begründet |
| Kopf „Berührte Spec-Stellen: keine“ | `spec/` ohne Diff; `SPEC-045` bleibt über die erzeugte Matrix berührt (Kopf nennt sie) | stimmt |

## Mutationen

Alle an Kopien des Kontexts `referenztest-dotnet/` im Scratchpad, mit echtem `dotnet` (SDK-Image 10.0.403), `--no-cache-filter test`.

| Mutation | Wirkung gemessen |
|---|---|
| M1: `sh test-lauf.sh test --filter X` in der RUN-Zeile von `test` | rot: `test-lauf.sh: genau ein Argument`, `exit code: 2` (bestätigt) |
| Filter ohne Treffer in `DOTNET_TEST_ARGS` (`--filter Name~GibtEsNicht`), Image-Standard | rot: `No test matches the given testcase filter`, `test-lauf.sh: dotnet test hat keinen Test ausgefuehrt`, `exit code: 3` (bestätigt, am echten `dotnet`) |
| derselbe Filter mit `DOTNET_CLI_UICULTURE=de-DE` oder `=fr-FR` | weiter englische Meldung, Exit 3: diese Variable ändert die Meldung nicht |
| derselbe Filter mit `ENV LANG=de_DE.UTF-8` oder `ENV LC_ALL=de_DE.UTF-8` | **Meldung deutsch** („Kein Test entspricht dem angegebenen Testfallfilter …“), die Treffer-Prüfung greift nicht, `rc=0`, **Stage `test` grün ohne ausgeführten Test** (V-1) |
| M5: Argument-Zählung gestrichen | im Selbsttest rot: `Mutation 'Argumentzaehlung gestrichen'` färbt Fälle rot (Teil der 8 Mutationen, `ok` im ungecachten Lauf) |
| M3: RUN-Zeile von `test` ohne `test-lauf.sh` | kein Sensor, wie benannt (im Review gemessen); nicht neu gefahren, die Zusage ist im Plan als Restrisiko ausgewiesen |

Zu M3 und §3.8: Die Aussage lautet „gekoppelt über test-lauf.sh und DOTNET_TEST_ARGS; eine RUN-Zeile ohne test-lauf.sh bleibt ungekoppelt (benanntes Restrisiko, kein Sensor)“. Sie trägt genau so weit wie der Beleg: die Kopplung ist an M1 und am Filter in `DOTNET_TEST_ARGS` gemessen, die Lücke ist als Lücke benannt, nichts behauptet mehr. §3.8-konform.

## Review der Nachbesserung 435c4831

| ID | Kategorie | Befund | Quelle | Pfad |
|---|---|---|---|---|
| V-1 | MEDIUM | Die Exit-3-Prüfung hängt an der englischen Meldung von `dotnet test`. Mit `LANG`/`LC_ALL` auf `de_DE.UTF-8` schreibt `dotnet` „Kein Test entspricht …“; Stage `test` endet mit Exit 0 ohne einen Test (gemessen). `DOTNET_CLI_UICULTURE` ändert die Meldung nicht. Der Image-Standard (kein `LANG`) ist englisch, daher heute nicht eingetreten; eine `ENV`-Zeile oder eine Basis mit Locale machte das Gate wieder still. Der Selbsttest sieht das nicht (Stub). Die Grenze steht im Kopf von `test-lauf.sh` und `test-lauf-selbsttest.sh` („Meldung … englisch“), nicht in Slice §6, nicht in `harness/README.md` | `AGENTS.md` §3.8 (Aussage „ein Gate ohne Tests ist rot“ breiter als der Beleg), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) | `referenztest-dotnet/test-lauf.sh` · `grep -Eq 'No test matches\|No test is available'` |
| V-2 | LOW | Selbsttest und Exit 3 stehen weder in den zwei Liefer-Punkten der DoD noch im Plan-Wortlaut von §3 ursprünglich; §1 schließt den Selbsttest als "nicht berührt" vom Ausschluss des Gleichlauf-Sensors aus, §6 und §3 weisen ihn als Abweichung aus. Die Abweichung ist sichtbar geführt und gedeckt; die DoD-Zeilen und §5 (Closure-Trigger) nennen sie nicht, die Abnahme des Selbsttests hängt nur an `make gates` | Modul 5 (Plan-Wortlaut gegen Bau) | Slice §1, §2, §5, §6 |
| V-3 | LOW | Die Verschärfung (Exit 3) steht nur im Kopf von `test-lauf.sh` und im Slice, nicht in `harness/README.md` (Zeile `make referenztest-dotnet`: „roter Test macht `make gates` rot“) und nicht im Kopf von `tools/mk/referenztest-dotnet.mk`. Wer ein grünes Gate erwartet, findet den Grund nicht an der Stelle, an der er sucht | `AGENTS.md` §3.7, `harness/README.md` §Sensors | `harness/README.md` · Zeile `make referenztest-dotnet` |
| V-4 | INFO | Der Modus `test` puffert die Ausgabe in eine Datei und druckt sie erst nach Ende (`cat`); der Fortschritt des Testlaufs erscheint im Build-Log nicht mehr zeilenweise. Die Suche `No test matches\|No test is available` läuft über die gesamte Ausgabe, auch über Text eines Tests; ein Test, der diese Wörter druckt, machte das Gate rot (nicht eingetreten) | Maintainability | `referenztest-dotnet/test-lauf.sh` Modus `test` |
| V-5 | INFO | Legitim leere Läufe (0 Tests) gibt es hier nicht: 126 Tests, und ein Gate ohne Tests soll rot sein. Die Verschärfung macht kein heutiges grünes Gate rot (frischer Lauf grün). Zulässig nach `AGENTS.md` §3.6 (nur strenger, keine Senkung) | `AGENTS.md` §3.6 | `referenztest-dotnet/test-lauf.sh` |
| V-6 | INFO | Die Zelle `make skript-selbsttest` in `harness/README.md` wurde von 200 auf 195 Zeichen umformuliert und verlor dabei Detail (u. a. "Abbruch", "Fehler von"); inhaltlich verlustarm, Gate unberührt | `AGENTS.md` §3.7 | `harness/README.md` |

Negativbefunde der Nachbesserung: Der Selbsttest läuft in `sh` (dash) und besteht ungecacht; seine acht Mutationen färben je mindestens einen Fall rot (Ausgabe `ok`). Die Mutationsliste enthält Argumentzählung, Modus-Exit, Treffer-Prüfung, Exit-Verschlucken, Logger, `|| echo`, Ergebnisverzeichnis und Variable. `$DOTNET_TEST_ARGS` ist absichtlich ungequotet und kommentiert; der Stub prüft die Zerlegung (Aufruf `test --no-restore --nologo`). Exit bei rotem Test: echte Messung 1. Eingangslisten: `tools/test-lauf-selbsttest.sh` liegt unter `tools` (Klasse `werkzeug` ganz), `referenztest-dotnet/test-lauf.sh` steht jetzt in `werkzeug.eingaenge` und über `referenztest-dotnet` auch in `tests.eingaenge`: eine Änderung am Skript macht beide Stempel ungültig. Das ist gewollt (beide Stages lesen die Datei) und kostet einen .NET-Lauf plus den Selbsttest, beide klein; `stempel-eingaenge-pruefung`, `stempel-abbild` und `stempel-gruppe` sind grün, keine Doppelzählung im Sinn eines Fehlers. `gate-zuordnung`: 33 Fälle, jedes Gate in genau einer Klasse.

## Stempel- und Gate-Mechanik

Kein Gate gelockert, kein neues Gate, kein neues Target: `AGENTS.md` §3.6 nicht berührt (die Verschärfung ist erlaubt, der Selbsttest hängt an der bestehenden Stage `skript-selbsttest`, Klasse `werkzeug`). `harness/README.md` Zelle: 195 Zeichen, unter der Grenze von 200 (der benachbarte Bericht zu `slice-betrieb-zustandsereignisse` nennt 200 als Grenze der Zelle `make smoke`).

## Merge mit main (nur benannt)

`git merge-tree --write-tree HEAD main` (main `46607765`) meldet **keine Konflikte**. Zu prüfen nach dem Merge: die Roadmap trägt dann die Zeile „in `in-progress/`: slice-qa-test-stage-run-kopplung“ in der Welle `welle-qualitaet-grundlage` neben den Zeilen der Nachbarwellen mit „keiner“; der Zweig hat „Nichts in Arbeit.“ gestrichen, und die Zusammenführung ließ die Zeile stehen, die main nach dem Move der Schwesterslices setzt (geprüft im Ergebnisbaum: Zeilen 26, 30, 34).

## Risiken §6 — Vorschlag der Ausgänge

| Risiko | Vorschlag |
|---|---|
| Der Bericht verträgt keine rote Gate-Logik | entfallen: M4 und die Messung oben zeigen, dass `test` bricht und `test-bericht` den Bericht behält |
| Abweichung vom Plan-Wortlaut (Selbsttest, Exit 3) | eingetreten, gedeckt; V-1 als Grenze der Verschärfung mitführen |
| Ungekoppelte RUN-Zeile | weiter offen (benanntes Restrisiko, eigene Entscheidung für einen Sensor) |
| Stempel-Eingänge | eingetreten und gelöst: `test-lauf.sh` in `werkzeug.eingaenge` (Prüfung grün) und über `referenztest-dotnet` in `tests.eingaenge` |

## Bedingungen vor der Closure

1. V-1 entscheiden und festhalten: entweder die Grenze („nur die englische Meldung; mit `LANG`/`LC_ALL` auf Deutsch ohne Wirkung, gemessen am 2026-11-07“) in §6/§7 des Slice und im Kopf von `test-lauf.sh` festschreiben, oder die Prüfung vom Wortlaut lösen (z. B. an die Testzahl im Bericht oder an die Image-Locale binden). Eine Verbreiterung der Aussage „ein Gate ohne Tests ist rot“ über die englische Meldung hinaus ist ohne Beleg nicht zulässig (`AGENTS.md` §3.8).
2. V-3: die Verschärfung in die Zeile `make referenztest-dotnet` und den Kopf von `tools/mk/referenztest-dotnet.mk` nachtragen (Zellgrenze beachten).
3. Closure-Notiz mit Steering-Loop-Lerneintrag, Ausgängen für alle vier Risiken und Register-Ausgang (`schwesterstage-ohne-gleichlauf-sensor`: Kopplung verkörpert, Ungekoppeltes als Restrisiko); Haken setzt der Planner nach diesem Bericht.
4. Der Review der Nachbesserung `435c4831` ersetzt kein zweites Review: ist ein Re-Review gewollt, ist er ein eigener Lauf. Vorschlag für das Register: Beobachtung „Nachbesserung ohne zweiten Review“ (Klasse bereits im Register geführt).

## Offene Punkte

* Locale-Messung nur mit `de_DE.UTF-8` (`LANG` und `LC_ALL`) sowie `DOTNET_CLI_UICULTURE` `de-DE`/`fr-FR`; weitere Locales nicht gemessen. Die Messlogs liegen nicht im Repo.
* `make docs-check` nach diesem Bericht: im Folgeschritt gefahren, siehe Commit.
