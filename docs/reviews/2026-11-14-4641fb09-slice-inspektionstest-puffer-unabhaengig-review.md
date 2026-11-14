# Review-Report: slice-inspektionstest-puffer-unabhaengig — 2026-11-14

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD)

**Gegenstand:** `slice-inspektionstest-puffer-unabhaengig`, Diff `4dafdba4..4641fb09` (ein Commit, eine Datei: `src/test/kotlin/io/github/pt9912/tdswire/runtime/TestserverLaufInspektionTest.kt`, +13 −16). `git diff --stat` bestätigt: kein Produktcode, kein Plan, kein Register im Diff.

**Skill:** `.harness/skills/reviewer.md` @ 4641fb09
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-14

**Eingangs-Kontext:**

- Plan `slice-inspektionstest-puffer-unabhaengig` (§1 Befund und Abgrenzung, §2 DoD mit M1 bis M3, §3 Weg und Zuordnung, §5, §6), gelesen im Hauptbaum
- [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md)
- [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)
- `AGENTS.md` §3.7 und §3.8; Vorgänger `slice-betrieb-prozesslauf-logzeilen-inspektion` (§3 Punkt 2, Vorprüfung); `ServerProcess.kt`, `RuntimeSupport.kt`, `LaufClient.kt`
- Bericht und Messungen des Implementers (Container mit `--sysctl`, M1, M2) liegen dem Review nicht als Artefakt vor; sie sind unten als „vom Implementer berichtet" gekennzeichnet und nicht von diesem Review wiederholt.

**Sensoren dieses Laufs:** kein Gradle-Lauf (der Diff ist ein Testcode-Diff mit konstanter Menge; ein Lauf auf diesem Host ergäbe weder eine Aussage über den Runner noch über H1; ein zweiter Gradle-Agent läuft parallel). Statische Prüfung: `git diff`, Lesen der Hilfsklassen, `grep` (Treffer unten). Gesamtlaufzeit des Reviews: wenige Minuten, größter Posten Lesen von Plan und Skill.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die KDoc von `VOLUME` nennt als Grenze „die Anfrage bleibt offen, solange die Puffer die Menge nicht fassen"; das ist die Zusage selbst, keine Bedingung. Die tragende Annahme H1 (der Sendepuffer des Servers wächst bei Empfangsfenster 4096 nicht in die Nähe der Menge) steht nicht im KDoc und ist an keinem Wert des Sendepuffers gemessen, nur am Ausgang „Anfrage blieb offen". Die zweite Umgebung („Container mit Obergrenzen von 144 MiB") teilt den Kernel des lokalen Hosts (6.8.0-147, nur `tcp_wmem`/`tcp_rmem` je Namensraum verändert, so der Plan §3); der Runner hat einen anderen Kernel. Gemessen ist damit: zwei Obergrenzen-Einstellungen eines Kernels, nicht zwei Kernel; die Obergrenze 144 MiB ist nicht der tatsächlich gewachsene Puffer. Szenario: der Autotuning-Verlauf des Runner-Kernels lässt den Sendepuffer über 40 MiB wachsen, der Test ist wieder rot („keine offene Anfrage"), obwohl KDoc und Plan die Umgebungsunabhängigkeit zusagen. | `AGENTS.md` §3.8; Reviewer-Skill „Aussage breiter als ihr Beleg", „Zusage ohne ihre gemessene Bedingung im selben Satz" | `TestserverLaufInspektionTest.kt` · „gemessen am lokalen Host (Obergrenzen 4 und 6 MiB) und in einem Container mit" | ja — der CI-Lauf `gates` auf dem Zweig oder auf `main` (Workflow läuft bei `push`); H1 selbst nur durch Auslesen des wirksamen Sendepuffers, nicht durch ein Gate | Beleg an Obergrenze statt an wirksamem Puffer; Umgebung nicht unabhängig (gleicher Kernel) |
| F-2 | MEDIUM | `RECEIVE_BUFFER` wird weiter an `LaufClient` übergeben (Zeile `LaufClient(port, RECEIVE_BUFFER)`), obwohl M2 grün blieb (vom Implementer berichtet: mit und ohne Parameter bleibt die Anfrage in beiden Umgebungen offen). Die Zeile ist Eigencode, dessen Entfernung keinen Test rot macht; die Konstante `RECEIVE_BUFFER` trägt selbst keinen Kommentar, die Einschränkung steht nur im KDoc von `VOLUME`. Die Ablehnung eines roten M2 ist im Plan §2 vorgesehen (Aussage einschränken); das ist hier für den Test geschehen, nicht für die Fundstellen unten. | Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht"; `AGENTS.md` §3.8 | `TestserverLaufInspektionTest.kt` · `private const val RECEIVE_BUFFER = 4_096` | ja — Mutation M2 in der engsten Testklasse (grün erwartet) | Parameter ohne gemessene Wirkung bleibt im Test |
| F-3 | LOW | Zweite Fundstelle der alten Aussage „der Client verkleinert sein Fenster, die Menge hält die Anfrage offen" bleibt ohne Vermerk: Plan `slice-inspektionstest-puffer-unabhaengig` §1 Absatz „Die Sendeseite des Servers" und §3 Weg Punkt 1 (H1 formuliert „Bei `SO_RCVBUF` 4096 des Clients bleibt …") liest den Parameter als Träger; der Vorgänger `slice-betrieb-prozesslauf-logzeilen-inspektion` §3 Punkt 2 („damit das Fenster klein ist") und §7 Vorprüfung Punkt 1 („Empfangspuffer des Clients 4096 … bestätigt"). Die KDoc von `RawClient` in `RuntimeSupport.kt` („setzt der Client den Empfangspuffer vor dem Verbinden auf diesen Wert") und von `LaufClient` sagen nur, was gesetzt wird, nicht eine Wirkung; sie sind nicht zu breit. Die Änderungsliste des Diffs (eine Datei) nennt die Fundstellen nicht; die Closure-Notiz §7 ist die Stelle dafür. | Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen" | `docs/plan/planning/in-progress/slice-inspektionstest-puffer-unabhaengig.md` · „bleibt die Antwort am Server so weit unter der Obergrenze"; `docs/plan/planning/done/slice-betrieb-prozesslauf-logzeilen-inspektion.md` · „Empfangspuffer des Clients 4096" | nein — Grep (`SO_RCVBUF`, `Empfangspuffer`, `Empfangsfenster`) findet die Stellen, urteilt aber nicht | Zweite Fundstelle der Parameter-Aussage |
| F-4 | LOW | Die KDoc von `VOLUME` erzählt eine Messchronik („blieb sie in beiden Umgebungen offen") statt des geltenden Zustands, und zählt Umgebungswerte auf (4 und 6 MiB, 144 MiB), die im Plan, in der Beobachtung und im Vorgänger-Slice ein zweites Mal stehen, ohne Sensor oder Kopplungskommentar. Die 40 MiB selbst stehen nur im Test (der Plan nennt „Kandidat 40 MiB", der Vorgänger 41 943 040 Byte). | `AGENTS.md` §3.7 (Zustand statt Chronik); Reviewer-Skill „Zahl an einem zweiten Ort ohne Sensor" (Zahlen sind Messwerte von Hosts, keine Konfiguration; deshalb LOW) | `TestserverLaufInspektionTest.kt` · „Mit und ohne `RECEIVE_BUFFER` blieb sie in beiden" | nein | Messchronik im KDoc |
| F-5 | INFO | Die Closure-Bedingung (CI `success` auf `main`, `head_sha` gleich Merge-Commit) ist im Plan §2 und §5 vollständig; der Workflow `gates` läuft bei `push` auf jedem Zweig und bei `pull_request` (`.github/workflows/gates.yml`). Der Plan nennt nur den Lauf nach dem Merge; ein roter Lauf nach dem Merge hat in §4 keine Rückführung, §6 lässt das Runner-Risiko „offen bis zur Closure". Zuständig: Planner. | Plan §4, §5, §6 | `docs/plan/planning/in-progress/slice-inspektionstest-puffer-unabhaengig.md` · „Beleg am CI-Lauf auf `main`" | ja — `gh run list --workflow gates` | Closure-Bedingung ohne Rückführung bei Rot |

## Zu den fünf Prüfpunkten des Auftrags

1. **Trägt `VOLUME` = 40 MiB die Zusage auf dem Runner?** Die Rechnung (a) trägt: Summe der Obergrenzen Runner 36 MiB (aus 144 MiB und Faktor 4 im CI-Log, Plan §1), Deckel 40 MiB darüber. Sie trägt nur, wenn der Sendepuffer überhaupt bis zur Obergrenze wachsen *müsste*, um die Menge zu fassen, nicht zusätzlich der Empfangspuffer in voller Höhe; die Aussage „40 MiB > Summe der Obergrenzen" belegt „Puffer fassen die Menge nicht", unabhängig von H1, solange beide Puffer höchstens ihre Obergrenze erreichen. Das ist der belastbare Teil: die Anfrage kann auf dem Runner nicht vollständig in den Puffern liegen, denn 36 MiB < 40 MiB (Rechnung, Rest im Heap des Servers und in Zwischenpuffern der JVM nicht gerechnet). H1 ist damit für den Runner nicht mehr notwendig, sondern nur noch für Hosts mit Summe über 40 MiB; der Plan §3 nennt H1 aber als Bedingung (b), und die KDoc nennt die Rechnung (a) nicht. Der Befund F-1 betrifft deshalb die Darstellung, nicht den Runner: die KDoc nennt weder die Rechnung (a) noch H1 als Bedingung. Nicht bewertet ist, ob Puffer der Bibliothek des Servers (Punkt 1 des Vorgängers) die Rechnung um etwas Weiteres überschreiten; der Vorgänger belegt ~1,2 MB gelesen, nicht die Gesamtgröße der Puffer. Verbleibendes Risiko: ein Runner, dessen Summe 40 MiB überschreitet (ein Kernel mit größeren Obergrenzen) — benannt im Plan §6, ohne Sensor.
2. **M2 grün, tote Parameter-Aussage.** Die Einschränkung im KDoc von `VOLUME` ist ehrlich („eine Wirkung des Parameters auf diese Hosts ist nicht gemessen"); genauer wäre „keine Wirkung gemessen". Fundstellen der alten Aussage: siehe F-2 und F-3.
3. **Meldungen.** Beide Meldungen tragen das Fragezeichen („… fassen die Menge $volume Byte?", „(Puffer größer als die Menge?)") und nennen eine Vermutung, keine Tatsache; die zweite steht in einer Zusicherung, die nur nach der ersten erreicht wird, und ist in der Reihenfolge des Tests ein zweiter Netzboden. Wartezeit bei Rot: `WAIT_SECONDS = 60` in `ServerProcess.kt` (Schranke, laut KDoc keine Erwartung der Dauer, keine neue Zeit-Annahme); die 61 s bei Rot sind diese Schranke plus Start und Laden und liegen im Rahmen; der grüne Pfad bekommt keine zusätzliche Wartezeit.
4. **Closure-Bedingung und Umfang.** Der Diff ändert nur den Test; der Plan §3 nennt die Datei; Register und Plan sind nicht im Diff (kein Haken `- [x]` gesetzt, keine Doku-Änderung). Closure-Bedingung siehe F-5.
5. **Grep (selbst gefahren, Worktree `4641fb09`):** `grep -rnE "proc/sys|tcp_wmem|tcp_rmem|SO_SNDBUF|SO_RCVBUF" src testserver` — ohne Treffer. `BUFFER_FACTOR`, `MAX_VOLUME`, `upperLimit` — ohne Treffer in `src`. Treffer für `RECEIVE_BUFFER`/`receiveBuffer`: `RuntimeSupport.kt`, `LaufClient.kt`, der Test (nur Testcode).

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main`, `testserver` | geprüft (Diff und Grep), ohne Befund; nicht berührt |
| `TestserverLaufInspektionTest.kt` Wartestellen und Inspektion | geprüft, ohne Befund; unverändert |
| `RuntimeSupport.kt`, `LaufClient.kt` | geprüft, ohne Befund; unverändert, KDoc sagt nur, was gesetzt wird |
| Test-Naht im Produktivcode | geprüft, ohne Befund; kein Parameter, kein Hook |
| Suppression, Schichtregel, Host statt Docker | geprüft, ohne Befund |
| `docs/`, `spec/`, `harness/` | nicht im Diff; Plan nur gelesen (F-3, F-5) |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 2 |
| INFO | 1 |

**Finding-Klassen dieses Laufs:** Beleg an Obergrenze statt an wirksamem Puffer · Parameter ohne gemessene Wirkung bleibt im Test · Zweite Fundstelle der Parameter-Aussage · Messchronik im KDoc · Closure-Bedingung ohne Rückführung bei Rot

## Verdikt

**Merge-blockierend:** nein — die zwei MEDIUM-Befunde betreffen die Darstellung der Grenze (KDoc) und einen wirkungslosen, aber harmlosen Parameter; die Zusage auf dem Runner trägt die Rechnung 36 MiB < 40 MiB. Der Beleg am CI-Lauf bleibt Closure-Bedingung. Die Befunde gehen an den Implementer (F-1, F-2) und in die Closure-Notiz §7 (F-3, Zweitfundstellen).

**Übergabe:** Findings an den Implementer; Finding-Klassen in die Slice-Closure §7. Dieser Report ersetzt keine Verifikation.
