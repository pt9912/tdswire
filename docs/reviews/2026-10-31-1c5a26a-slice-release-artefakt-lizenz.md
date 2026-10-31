# Review-Report: slice-release-artefakt-lizenz — 2026-10-31

**Review-Art:** Code (Diff gegen Plan, Entscheidungen und Hard Rules). Gegen die DoD wird nicht geprüft; das ist die Frage des Verifiers.

**Gegenstand:** Diff `ab4b000..1c5a26a` (Merge-Commit 1c5a26a, Umsetzungs-Commit e6ddd9f). Code: `Dockerfile`, `build.gradle.kts`, `tools/laufzeittest.sh`, `tools/mk/laufzeittest.mk`, `LaufzeitClient.kt`, `LizenzTest.kt`, `RuntimeSupport.kt`; Doku: Zeile in `harness/README.md`, Verweis-Nachzug in Slice- und Wellen-Dateien. Der Rest des Diffs (Archivierung von welle-backend-streaming, Reviews) gehört nicht zum Slice und ist nicht geprüft.

**Skill:** `.harness/skills/reviewer.md` @ 1c5a26a
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-31

**Eingangs-Kontext:**

- Slice-Plan slice-release-artefakt-lizenz (Abschnitte 1 bis 3, 6 und 8)
- [ADR-0013](../plan/adr/0013-backend-grenzen.md) (Betreiberschalter `-XX:+ExitOnOutOfMemoryError`), [ADR-0011](../plan/adr/0011-pruefwertformat.md) (Benutzerliste, Dateirechte offen)
- [SPEC-023](../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023), `SPEC-042` (Zeile der Spezifikation-Tabelle ohne eigenen Anker)
- [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)
- [AGENTS.md](../../AGENTS.md) §3.1 bis §3.8

**Eigene Läufe (Worktree, ein Docker-Build gleichzeitig, nichts gemountet):**

| Lauf | Ergebnis |
|---|---|
| `make laufzeittest` einmal | grün, alle zehn Prüfzeilen ok, Exit-Code des Servers 143, Laufzeit 88 s einschließlich beider Builds (Implementer: 31 s warm, 171 s kalt, je ein Lauf); danach kein Container, kein Image `tdswire-laufzeit*`, kein Netzwerk `tdswire*` übrig |
| Nachbau der Inhaltsprüfungen `app_nur_artefakt` und `ohne_geheimnis` auf synthetischen Dateilisten (nur die Befehlszeilen, kein Image) | leere Liste: beide grün; `app/config.properties`, `app/secrets/a.txt` neben erlaubten Dateien: rot; Namen `benutzer.txt`, `passwords.csv`, `db.properties`: nicht erkannt; `users`, `app/users.txt`: erkannt |
| Fremde Aufrufer der Jar | `grep` über `Makefile`, `tools/`, `harness/mk`, `.github`, `Dockerfile`: keine Stelle nennt einen Jar-Namen außer den neuen; `ServerProcess` startet die Kind-JVM über die Klasse `MainKt`, nicht über das Jar |
| `make gates` | siehe Summary |

Nicht ausgeführt: Mutationen am Dockerfile (Class-Path, Entrypoint), Abbruch des Skripts mit SIGINT, zwei parallele Läufe von `make laufzeittest`.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Einordnung als Werkzeug nennt eine Messung, aber keinen Maßstab, ab wann Laufzeit ein Gate ausschließt; die Zeile gibt je einen Lauf auf einem Rechner an, der eigene Lauf maß 88 s. Folge: Das Jar (Manifest mit `Class-Path`, Dateiname), die Stage `runtime` und der Entrypoint laufen in keinem Gate und nicht in der CI (`make gates`); ein defekter `Class-Path` oder Entrypoint bliebe grün, weil `make smoke` die Kind-JVM über die Klasse `MainKt` startet, nicht über das Jar. | [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), Slice-DoD Punkt 2 | `harness/README.md` · „Werkzeug, Laufzeit 31 s warm, 171 s kalt (2026-10-31, je ein Lauf)“ | ja — Mutation am `Class-Path`, danach `make gates` (nicht ausgeführt) | Gate-Ausschluss ohne Maßstab; Artefaktpfad ohne Gate |
| F-2 | MEDIUM | Der Slice-Ziel-Satz sagt, Laufzeittest und Lizenzprüfung seien „Teil der Gates“; geliefert ist nur die Lizenzprüfung (Test und `cmp` in `build-jar`) als Gate, der Laufzeittest nicht. `tools/mk/laufzeittest.mk` verweist für die Begründung auf die Zeile in `harness/README.md`, die keine nennt (siehe F-1). | Maintainability (Zeiger ohne Ziel), Slice §1 | `tools/mk/laufzeittest.mk` · „die Begruendung steht in der Zeile in harness/README.md“ | ja — Lesen beider Stellen | Verweis auf nicht vorhandene Begründung |
| F-3 | MEDIUM | Die Inhaltsprüfung des Images kann leer grün sein: `docker export \| tar -t 2>/dev/null` schreibt `inhalt`, ohne dass geprüft wird, dass die Liste nicht leer ist; bei einer leeren Liste sind `app_nur_artefakt` und `ohne_geheimnis` beide wahr (nachgebaut, siehe Läufe). | [TDS-RB-01](../../spec/lastenheft.md#tds-rb-01--implementierungssprache), Negativtest-Pflicht (Skill MEDIUM) | `tools/laufzeittest.sh` · `docker export "$lager" \| tar -t >"$SCRATCH/inhalt" 2>/dev/null` | ja — Nachbau auf leerer Datei | Prüfung ohne Nachweis der Eingabe |
| F-4 | MEDIUM | Die Formulierung „kein Geheimnis“ ist breiter als der Beleg: Der Test prüft Dateinamen per Muster (nicht erkannt: `benutzer.txt`, `passwords.csv`, `db.properties`; `etc/` ist ausgenommen), Umgebungsnamen (`PASSWORD`, `SECRET`, `AUTH_USERS`, `KEYSTORE`) und die Dateinamen unter `/app`; Inhalte der Jars und der Basis-Schichten werden nicht gelesen. Belegt ist es durch die Bauart (drei `COPY`-Zeilen), nicht durch einen Inhaltsscan; der Dockerfile-Kommentar sagt das nicht. | AGENTS.md §3.8, Slice-DoD Punkt 1 | `Dockerfile` · „keine Konfiguration, keine Benutzerliste, kein Geheimnis“ | ja — Nachbau mit Dateinamen | Aussage breiter als Beleg (Namenstest als Inhaltsaussage) |
| F-5 | MEDIUM | `LizenzTest` trägt `@spec SPEC-042`, die Zeile `SPEC-042` lautet „Build-Gate (`make build`): Compile und Jar ohne Tests, Basis-Images per Digest gepinnt, Lock und Prüfsummen“. Der Test liest nur die Datei `LICENSE`; nach `make spec-abdeckung` würde die Matrix `SPEC-042` als durch einen bestandenen Test gedeckt zeigen (heute 0, weil die Matrix nicht neu erzeugt ist). | AGENTS.md §3.8, `SPEC-042` | `src/test/kotlin/io/github/pt9912/tdswire/runtime/LizenzTest.kt` · „@spec SPEC-042“ | ja — `make spec-abdeckung` (nicht ausgeführt) | Spec-Tag breiter als Testgegenstand |
| F-6 | LOW | Der Container `$lager` aus `docker create "$SERVER_IMAGE"` steht nicht in der Aufräum-Funktion; scheitert `docker cp` oder `docker export` hart oder bricht das Skript zwischen `create` und `rm` ab, bleibt er mit dem Image-Layer zurück (`rmi -f` entfernt nur den Tag). Der Client-Container ist nur durch `--rm` gedeckt. | Maintainability (Aufräumen bei Abbruch, Slice §3 „Kollisionen“) | `tools/laufzeittest.sh` · `lager="$(docker create "$SERVER_IMAGE")"` | nein — Abbruch nicht ausgeführt | Aufräumlücke im Abbruchpfad |
| F-7 | INFO | Exit-Code 143 ist das Standardverhalten der JVM bei SIGTERM (128 + 15), kein Zeichen eines Signal-Handlers des Produkts. Dass der Shutdown-Hook lief, belegt die Zeile `tdswire: gestoppt`; dass er innerhalb der Stop-Frist endete, belegt 143 statt 137 (SIGKILL). Die Prüfung `test "$ende" = "143"` hängt am JVM-Standard; ein später eingebauter Handler mit Exit 0 macht sie rot. Die Zusage im Skript („Stop beendet den Prozess“) ist so eng, wie sie gemessen ist. | Maintainability | `tools/laufzeittest.sh` · „143 = 128 + SIGTERM“ | ja — Lauf | Erwartungswert am JVM-Standard |
| F-8 | INFO | Der Laufzeittest belegt, dass `JAVA_TOOL_OPTIONS` die JVM erreicht und die Option erkannt wird (die Zeile `Picked up …`; eine unbekannte Option bräche den Start), nicht die Wirkung von `-XX:+ExitOnOutOfMemoryError`. Alle Läufe (Start, Login, Stop) laufen mit gesetztem Schalter; das Verhalten ohne ihn ([ADR-0013](../plan/adr/0013-backend-grenzen.md): nicht voreingestellt) hat der Test nicht als eigenen Lauf, das Image setzt keine `ENV`. | [ADR-0013](../plan/adr/0013-backend-grenzen.md) | `tools/laufzeittest.sh` · „JAVA_TOOL_OPTIONS erreicht die JVM“ | ja — Lauf | Schalter-Ankunft statt Wirkung |
| F-9 | INFO | Der `build`-Stage-Artefakt-Image (`COPY --from=build-jar … /app/`) trägt jetzt `tdswire.jar` mit einem `Class-Path` auf `lib/`, das Verzeichnis kopiert er nicht; der Jar-Name verliert die Version (`0.1.0-SNAPSHOT`). Der Kommentar „nur dem fertigen Jar“ gilt weiter, das Jar läuft dort aber nicht allein. §6 des Slice führt „zwei Definitionen desselben Artefakts“ als offen. | Slice §6 | `Dockerfile` · `COPY --from=build-jar /src/build/libs/*.jar /app/` | ja — Stage bauen | Zweites Artefakt neben `runtime` |
| F-10 | INFO | Der Test legt die Benutzerliste per `docker cp` als root-eigene, weltlesbare Datei ab; dass der Nutzer 65532 sie liest, belegt nur diesen Fall. Dateirechte und Übergabe ([ADR-0011](../plan/adr/0011-pruefwertformat.md) §Offen Nr. 5, Slice §6) bleiben offen; der Header der Skripts behauptet dazu nichts. | [ADR-0011](../plan/adr/0011-pruefwertformat.md) | `tools/laufzeittest.sh` · „Die Benutzerliste kommt per `docker cp` vor dem Start“ | nein | Betriebsübergabe ungemessen |
| F-11 | INFO | `LizenzTest` prüft Anfang, Rechteinhaber `Copyright (c) 2026 pt9912`, Beginn der Rechteeinräumung und Haftungsausschluss; ein geänderter Mittelteil bliebe grün, ein Jahreswechsel im Rechteinhaber-Text machte den Test rot. Die Gleichheit Jar/Image mit `LICENSE` prüfen `cmp` im Build und im Laufzeittest, nicht der Test. | [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) | `LizenzTest.kt` · „Copyright (c) 2026 pt9912“ | ja — Mutation (nicht ausgeführt) | Textprüfung nach Fragmenten |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| (a) Einordnung Werkzeug, nicht `GATE_CHECKS`: AGENTS.md §3.6 | geprüft, keine Gate-Lockerung: `make gates` hat kein Target verloren, `laufzeittest` ist neu und steht nicht in `GATE_CHECKS`; Schwellen und `config/detekt/detekt.yml` unverändert. Die Begründung selbst: F-1, F-2 |
| (b) Exit-Code 143 | siehe F-7; kein Befund gegen die Zusage |
| (c) Server-Log ohne Benutzer und Passwort | geprüft, ohne Befund: der Server schreibt `Login: …` ins Log, die Prüfung auf `alice` und `secret` ist daher nicht leer |
| (d) Manifest, Jar-Name, Stage `build-jar`, `smoke`, `test`, `spec-abdeckung` | geprüft, ohne Befund: `LICENSE` ist per `COPY` in `test`, `spec-abdeckung`, `build-jar`; `.dockerignore` nimmt sie nicht aus; kein anderer Aufrufer nennt den alten Jar-Namen; `Class-Path` kommt aus `runtimeClasspath` (kotlin-stdlib, coroutines, annotations), der Lauf im Container lief. Die Kommentare in `build.gradle.kts` sind Zusage und Kopplung |
| (e) Netz `--internal`, Namen mit `$$`, Image-Tags je Lauf, `trap … EXIT` | geprüft, ohne Befund außer F-6: nach dem Lauf blieb nichts zurück; parallele Läufe haben getrennte Namen (nicht ausgeführt); `set -u` ohne `-e` mit einzelnen `exit 1` an den Stellen vor dem Netz |
| (f) Digest-Pin | geprüft, ohne Befund: der Digest `7e37784d…` ist laut `docker buildx imagetools inspect` der Index-Digest des Tags `nonroot` am 2026-10-31; der Kommentar sagt „Messung … am Tag `nonroot`“ und nennt den Digest eine bewusste Änderung der Datei |
| (g) `-Djdk.tls.server.newSessionTicketCount=0` im Entrypoint | geprüft, ohne Befund: die Startprüfung bricht sonst ab, der Server meldete „gestartet“; `JAVA_TOOL_OPTIONS` hat keine Voreinstellung (F-8). Ob eine Betreiber-Eigenschaft dasselbe Flag überstimmt, ist nicht gemessen |
| (h) Lizenzprüfung im Jar | geprüft, ohne Befund außer F-5, F-11: `jar tf … grep -qx` und `unzip -p … \| cmp - LICENSE` brechen `build-jar`; ohne `pipefail` entscheidet der letzte Befehl, und eine leere Ausgabe von `unzip` ließe `cmp` rot werden |
| (i) „nichts mounten“, Netz | geprüft, ohne Befund: kein `-v`, kein `--mount`; Datei per `docker cp`; Server und Client im Netz `--internal` |
| AGENTS.md §3.1 (Docker-only) | geprüft, ohne Befund: der Host braucht `bash` und `docker`, kein JDK und kein Gradle |
| AGENTS.md §3.2 (Suppression) | geprüft, ohne Befund: kein `@Suppress` im Diff |
| AGENTS.md §3.7 (Kommentare) in `Dockerfile`, `tools/laufzeittest.sh`, `build.gradle.kts`, `tools/mk/laufzeittest.mk` | geprüft, ohne Befund außer F-2: keine verworfene Alternative, kein abwesender Text |
| `harness/README.md` (Zeile `make laufzeittest`) | geprüft, ohne Befund außer F-1; Zeile, Bindung und Link vorhanden |
| `LaufzeitClient.kt`, `RuntimeSupport.kt` | geprüft, ohne Befund: Standardwert `LOOPBACK` erhält das Verhalten der bestehenden Tests; Exit-Code 1 bei jedem Fehlausgang |
| Verweis-Nachzug in `slice-release-doku-prozess`, `slice-qa-kompatibilitaet`, `welle-referenzabgleich-release`, Roadmap | geprüft, ohne Befund |

## Summary

0 HIGH, 5 MEDIUM, 1 LOW, 5 INFO. Kein ADR-Verstoß, keine Gate-Lockerung. Das Gewicht liegt auf der Gate-Frage (F-1, F-2): der Weg vom Jar-Manifest bis zum Container liegt außerhalb jedes Gates. Die Inhaltsprüfung des Images (F-3, F-4) ist ein Namenstest und kann bei leerer Eingabe leer grün sein.
