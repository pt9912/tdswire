# Verifikationsbericht: slice-release-artefakt-lizenz — 2026-10-31

**Rolle:** Verifier („Bauen wir es richtig?" gegen Plan und DoD). Der Bericht repariert nichts und schließt nichts.

**Gegenstand:** [slice-release-artefakt-lizenz](../plan/planning/in-progress/slice-release-artefakt-lizenz.md) nach Umsetzung `e6ddd9f` und Fix-Runde `71d4f4f`, Stand `main` `175d1e4`. Eingang: Review [2026-10-31-1c5a26a-slice-release-artefakt-lizenz.md](2026-10-31-1c5a26a-slice-release-artefakt-lizenz.md) (F-1 bis F-11).

**Bezug:** [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0013](../plan/adr/0013-backend-grenzen.md), [ADR-0011](../plan/adr/0011-pruefwertformat.md), [SPEC-023](../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023), `SPEC-042` (Zeile der Spezifikation-Tabelle ohne eigenen Anker).

**Modell:** claude-sonnet-5-5 · frischer Kontext, ein Docker-Build gleichzeitig.

## 1. Eigene Läufe (Belege, nicht Behauptungen)

| Lauf | Ergebnis |
|---|---|
| `docker build --no-cache-filter test --target test .` | Exit 0, 102 s; `LizenzTest > LICENSE im Wurzelverzeichnis ist die MIT-Lizenz() PASSED`, `BUILD SUCCESSFUL` |
| `make laufzeittest` (unmutiert, warm) | grün, elf Prüfzeilen ok (Nutzer 65532, Lizenz gleich, Dateiliste gelesen, Namenstest, `/app` nur Artefakt, gestartet, `JAVA_TOOL_OPTIONS` erreicht JVM, Login, Exit 143, `gestoppt`, kein Benutzer im Log); Laufzeit **24 s** wandgemessen (Skript: 22 s) |
| `make gates` (warm, mit dem neuen Gate) | Exit 0, **26 s** gesamt; darin `laufzeittest` mit 16 s (Skript), `laufzeittest: gruen`; `make -n gates` nennt `laufzeittest` |
| Mutation M1: `docker export … \| tar -t` durch `: >"$SCRATCH/inhalt"` ersetzt (leere Dateiliste) | **rot**, Exit 2: `FEHLER Dateiliste des Images ist gelesen`; die beiden anderen Inhaltsprüfungen blieben (wie in F-3 beschrieben) grün, den Ausschlag gibt allein der neue Wächter |
| Mutation M2: `Class-Path` in `build.gradle.kts` auf `xlib/…` | **rot**, Exit 2 (160 s, Neubau): Server meldet nicht „gestartet", Login, Exit-Code (1 statt 143) und `gestoppt` rot |
| Mutation M3: `-Djdk.tls.server.newSessionTicketCount=0` aus dem `ENTRYPOINT` entfernt | **rot**, Exit 2: Start bricht ab (nicht „gestartet", Exit-Code 1) — die Startprüfung aus [SPEC-023](../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023) greift im Container |
| Nachbau des Namensmusters auf synthetischen Listen | erkannt: `app/benutzer.txt`, `app/passwords.csv`, `app/db.properties`, `app/users.txt`, `tmp/x/secret.json`; nicht erkannt (außerhalb des Musters; unter `/app` deckt `app_nur_artefakt` ab): `app/credentials.yml`, `app/Passwort.txt`, `opt/app.yaml` |
| Rückstände | nach allen Läufen kein Container `tdswire*`, kein Image `tdswire-laufzeit*`; Worktree sauber (Mutationen per `git checkout` zurückgenommen) |

Grenze der Belege: Die Mutationen M2 und M3 habe ich am Target `make laufzeittest` gefahren, nicht über ein volles `make gates`. Dass das Target in der Kette liegt, belegen `GATE_CHECKS += laufzeittest` in `tools/mk/laufzeittest.mk`, `make -n gates` und der unmutierte `make gates`-Lauf. Der CI-Lauf `41193283726` (Push `175d1e4`, erster mit dem neuen Gate) war bei Abschluss noch `in_progress` (7 m 48 s; der Vorgänger lief 7 m 43 s); sein Ausgang ist unbelegt.

## 2. DoD Punkt für Punkt

| DoD-Punkt | Urteil | Beleg |
|---|---|---|
| Laufzeit-Image: Stage mit JRE-Basis per Digest (`SPEC-042`) | erfüllt | `Dockerfile` Stage `runtime`: `gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d…`; die Digest-Messung des Reviews (Negativbefund f) habe ich nicht erneut gegen die Registry gemessen |
| Nutzer ohne Root-Rechte | erfüllt | `USER 65532:65532`; Prüfzeile „Image-Nutzer ohne Root-Rechte" ok |
| Jar mit Startklasse | erfüllt | `Main-Class` im Manifest; Container startet über `-jar`; M2 zeigt: ein defekter `Class-Path` wird erkannt |
| Konfiguration zur Laufzeit; kein Geheimnis, keine Benutzerliste im Image (Test über den Inhalt) | erfüllt, **eng belegt** | Umgebung und `docker cp`; der Test prüft Dateinamen unter `app\|home\|tmp\|srv\|opt\|root\|data\|config`, Namen der Umgebung und `/app` gegen eine Positivliste — kein Inhaltsscan. `Dockerfile`-Kommentar und Skriptkopf sagen das jetzt. „Inhalt" der DoD ist so der Image-Inhalt (Dateiliste) |
| Bestehendes `build`-Image bleibt | erfüllt | Stage `build` besteht; das Jar heißt jetzt `tdswire.jar` und läuft dort nicht allein (F-9 des Reviews, in §6 des Slice benannt) |
| `-Djdk.tls.server.newSessionTicketCount=0` ([SPEC-023](../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023)); Startprüfung bleibt | erfüllt | `ENTRYPOINT` trägt das Flag; M3 rot |
| JVM-Optionen des Betreibers ohne Neubau, `-XX:+ExitOnOutOfMemoryError` nicht voreingestellt ([ADR-0013](../plan/adr/0013-backend-grenzen.md)); Test startet mit Schalter und prüft Ankunft | erfüllt, **Ankunft, nicht Wirkung** | `JAVA_TOOL_OPTIONS` (Festlegung im `Dockerfile`-Kommentar); Prüfzeile „erreicht die JVM" (`Picked up JAVA_TOOL_OPTIONS: …`); das Image setzt kein `ENV`. Die DoD verlangt nur „an der JVM ankommt" |
| Laufzeittest: Container startet, Rohbytes-Login, Stop; `make`-Target in `make gates` oder begründet außerhalb; Eintrag in `harness/README.md` §Sensors | erfüllt | Target in `GATE_CHECKS`; Zeile in der Gate-Tabelle von `harness/README.md` (nicht mehr unter Werkzeuge) mit Bindung [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache); Laufzeit genannt (Abschnitt 1) |
| Lizenz: `LICENSE` ist MIT (Textprüfung); Artefakt führt sie mit; Test bricht, wenn sie fehlt | erfüllt | `LizenzTest` (PASSED im frischen Lauf); `META-INF/LICENSE` im Jar mit `cmp` in `build-jar` (bricht den Build), `/app/LICENSE` im Image mit `cmp` im Laufzeittest ([TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz)) |
| Nachweis: jeder Test trägt `@spec SPEC-<NNN>` | **teilweise** | `LizenzTest` trägt seit der Fix-Runde keinen Tag mehr (V-1) |
| Nachweis: Zuordnung *Test \| Festlegung* in der Closure-Notiz; Review-Report liegt vor | Review erfüllt, Notiz offen | §7 „Wird bei der Closure gefüllt" — Closure ist ausdrücklich nicht Teil dieses Laufs |
| `make gates` grün | erfüllt | Lauf oben |
| Closure-Notiz, Register, Risiko-Ausgänge (§6), Paarungen | offen | erwartungsgemäß: alle sechs Risiken in §6 stehen „offen bis zur Closure"; DoD-Häkchen nicht gesetzt |

## 3. Review-Findings F-1 bis F-6: wirklich behoben?

| ID | Urteil | Beleg |
|---|---|---|
| F-1 (Jar/Manifest/Class-Path/`runtime`/Entrypoint ohne Gate) | **behoben** | `laufzeittest` in `GATE_CHECKS`; M2 und M3 rot am Target, das die Kette ruft. Der Maßstab steht im Kopf von `tools/mk/laufzeittest.mk` (Setzung: Warmlauf unter der Laufzeit der Stage `test`, Gradle-Teil 93 s). Restpunkt: V-2 |
| F-2 (Verweis auf nicht vorhandene Begründung) | **behoben** | Das Target ist ein Gate; der Verweis zeigt auf den Kopf der `.mk`-Datei mit Maßstab und Messung |
| F-3 (leere Dateiliste) | **behoben** | Wächter `inhalt_gelesen` (`app/tdswire.jar` in der Liste); M1 rot. Er macht die beiden anderen Prüfungen nicht selbst nicht-leer, schützt sie aber über die Eingabe |
| F-4 (Namensmuster, Formulierung) | **behoben** | Muster um `benutzer*`, `passwords*`, `secrets*`, `*.properties` erweitert und auf Betreiber-Verzeichnisse begrenzt; Beschreibung in Skriptkopf, Prüfzeile und `Dockerfile` sagt „Dateinamen und Namen der Umgebung … kein Inhaltsscan". Nachbau bestätigt |
| F-5 (Spec-Tag `SPEC-042` am `LizenzTest`) | **behoben**, mit DoD-Folge V-1 | Tag entfernt, KDoc begründet („keine Zeile der Spezifikation fordert die Lizenz"); `SPEC-042` wird nicht mehr fälschlich gedeckt |
| F-6 (Container `$lager` nicht im `trap`) | **behoben** | `lager` in `aufraeumen()`, nach `docker rm` geleert; kein Rückstand nach Läufen. Einen Abbruch per SIGINT zwischen `create` und `rm` habe ich nicht ausgelöst |

F-7 bis F-11 (INFO) sind unverändert und bleiben zutreffend eingeordnet.

## 4. Entscheidungs-Konformität

- [ADR-0013](../plan/adr/0013-backend-grenzen.md): Betreiberschalter über `JAVA_TOOL_OPTIONS`, nicht voreingestellt, Ankunft belegt; die Wirkung (JVM-Ende bei OOM) ist nicht belegt und nicht gefordert.
- [ADR-0011](../plan/adr/0011-pruefwertformat.md): Das Image enthält keine Benutzerliste; Rechte und Übergabe bleiben offen und stehen so in §6 (kein Befund).
- [SPEC-023](../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023): Flag im Entrypoint, Mutation rot.
- `SPEC-042`: Digest-Pin der neuen Basis erfüllt; kein Test beansprucht die Zeile mehr.
- AGENTS.md §3.6: keine Lockerung, ein Gate kam hinzu; `make gates` verlor kein Target; die README-Zeile liegt jetzt in der Gate-Tabelle.
- AGENTS.md §3.7: Kommentare in `Dockerfile`, `tools/laufzeittest.sh`, `tools/mk/laufzeittest.mk` tragen Zusage, Kopplung oder Grenze; der Zusatz „Stand der Umsetzung" in §6 nennt einen Zustand, keine Chronik. Der Skriptkopf hat eine überlange Zeile (Form, kein Befund).
- AGENTS.md §3.8: Die Aussage „kein Geheimnis" ist auf Dateinamen und Umgebungsnamen zurückgenommen; Exit 143 ist als gemessen gekennzeichnet; Laufzeitangaben nennen Datum und Läufe.

## 5. Plan-vs-Code-Diff

| Plan §3 | Code | Abweichung |
|---|---|---|
| `Dockerfile` Laufzeit-Stage, JRE per Digest | Stagen `build-jar`, `runtime`, `laufzeit-client`; `build` bleibt | zusätzlich `laufzeit-client` (Rohbytes-Client als zweiter Container), vom Plan gedeckt („Tests des Laufzeittests") |
| `build.gradle.kts` Manifest, Lizenz im Jar | `Main-Class`, `Class-Path`, `META-INF/LICENSE`, Tasks `laufzeitBibliotheken`, `laufzeitClient` | keine |
| `tools/mk/` Target, `GATE_CHECKS +=` nach Entscheidung | `laufzeittest.mk`, `tools/laufzeittest.sh`; Entscheidung Gate | nach dem Review von Werkzeug auf Gate umgestellt |
| Tests Start/Login/Stop, fehlende Lizenz, Secret im Image | Skript-Prüfungen plus `LizenzTest`; kein Kotlin-Test für Start/Login | der Laufzeittest ist ein Shell-Gate (die DoD verlangt ein `make`-Target) |
| nicht geplant | `LaufzeitClient.kt`, `RuntimeSupport.kt` (Standard `LOOPBACK` erhält das Verhalten) | Hilfscode des Laufzeittests |

## Findings

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | LOW | Die Nachweis-Pflicht „Jeder Test trägt `@spec SPEC-<NNN>`" gilt nach der Fix-Runde für `LizenzTest` nicht mehr; die Fix-Runde löst F-5 durch Entfernen. Das ist vertretbar (keine passende Zeile; die Forderung steht im Lastenheft, [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz)), aber der Slice-Text bleibt unverändert. Entscheidung für den Planner: DoD-Zeile mit Ausnahme versehen oder eine `SPEC-*`-Zeile für das Artefakt schaffen. | `src/test/kotlin/io/github/pt9912/tdswire/runtime/LizenzTest.kt`; Slice §2 |
| V-2 | LOW | Der Gate-Maßstab ist eine Setzung („Warmlauf unter der Laufzeit der Stage `test`") und vergleicht warm mit dem Gradle-Teil. Gemessen heute: Warmlauf 24 s, `make gates` warm 26 s, Stage `test` ohne Cache 102 s. Die Kaltlaufzeit des Gates (README: 88 bis 171 s, je ein Lauf) liegt über dem Maßstab, der nicht sagt, ob warm oder kalt gilt; der CI-Lauf `41193283726` mit dem neuen Gate ist unbelegt (bei Abschluss `in_progress`). Nachzusehen, sobald er endet. | `tools/mk/laufzeittest.mk`; `harness/README.md` Zeile `make laufzeittest` |
| V-3 | INFO | Die Bindung `TDS-RB-01` (Lastenheft-ID) steht in einer Spalte, deren Zusatzklassen `harness/conventions.md` nur als Platzhalter führt; Präzedenz hat die Zeile `make workflow-lint` mit `TDS-QA-06`. Keine Zusatzklasse deklariert. | `harness/conventions.md` §Zusatzklassen |
| V-4 | INFO | Mutationen M2/M3 am Target, nicht an `make gates`; der Gate-Anschluss ist durch `make -n gates` und den grünen Lauf belegt. Der Wächter für die leere Dateiliste prüft das Vorkommen des Jars, nicht die Vollständigkeit der Liste. | Abschnitt 1 |
| V-5 | INFO | F-7 bis F-11 sind unverändert (Exit 143 am JVM-Standard; Ankunft statt Wirkung des Schalters; zweites Artefakt `build`; Benutzerliste nur als weltlesbare Datei belegt; Lizenztext nach Fragmenten). Die Closure-Notiz führt sie in der Zuordnung *Test \| Festlegung*. | [Review](2026-10-31-1c5a26a-slice-release-artefakt-lizenz.md) |

## Summary

0 HIGH, 0 MEDIUM, 2 LOW, 3 INFO. F-1 bis F-6 sind behoben; die Wächter für Dateiliste, `Class-Path` und Entrypoint schlagen in den Mutationen rot an. Frischer Testlauf, `make laufzeittest` (24 s warm) und `make gates` (26 s warm) grün. Offen für die Closure, nicht für diesen Bericht: Closure-Notiz, Risiko-Ausgänge, DoD-Häkchen, V-1 (Planner-Entscheidung) und der Ausgang des CI-Laufs.
