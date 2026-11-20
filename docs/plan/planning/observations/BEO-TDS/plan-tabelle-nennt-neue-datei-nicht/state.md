**Stand:** verkörpert — `.harness/skills/reviewer.md` (MEDIUM, Prüfpunkt „Plan-Tabelle nennt die berührten Dateien nicht“), Anker `seit slice-e2e-backend-ausserhalb-modul`; Nachtrag in `slice-e2e-backend-ausserhalb-modul.md` §7. Ob ein Sensor den Abgleich Tabelle gegen Diff übernimmt, entscheidet der Nutzer.

**Lese-Schritt welle-fehler-abbruch (2026-11-06):** 4× gelesen, über der Schwelle; inzwischen verkörpert, siehe Stand; Einzelheiten in `welle-fehler-abbruch-results.md` §Steering-Loop-Einträge.

**Lese-Schritt welle-betrieb-diagnose (2026-11-13):** 21 Dateien, davon 7 aus Slices dieser Welle (`slice-betrieb-telemetrie-verdrahtung-belegen`, `slice-betrieb-log-adapter-testserver`, `slice-betrieb-zustandsereignisse`, `slice-betrieb-metriken-tracing`, `slice-einstellungen-in-typen-buendeln`, `slice-request-failed-mit-request-id`, `slice-betrieb-prozesslauf-logzeilen-inspektion`); Reviewer oder Verifier fingen jedes Auftreten, der Implementer hatte die Tabelle nicht gegen den Diff gelesen. Retirement-Check: die Klasse ist nach der Verkörperung wiedergekehrt, die Regel bleibt. Kein Sensor (Begründung des Planners, der Entscheid bleibt beim Nutzer): ein Abgleich der Tabelle gegen `git diff --name-only` meldete erzeugte und umbenannte Dateien (Review-Reports, Abdeckungsmatrix, Links der Gruppe `slice-mv`, Roadmap) als fehlend oder müsste raten, welche in die Tabelle gehören.

**Wiederauftreten (slice-release-lizenzen-lib-belegen):** Review F-4 und Verifikation F-1, beide LOW; Reviewer und Verifier fingen die Abweichung der Tabelle §3 gegen den Diff. Retirement-Check: die Klasse ist nach der Verkörperung wiedergekehrt, die Regel bleibt.


**Wiederauftreten (slice-qa-performance-durchsatz-sitzungen):** Review F-9 und Re-Review 2 R-2: die Tabelle §3 gegen den Diff, zweimal in einem Slice; der Reviewer fing beide. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.


**Wiederauftreten (slice-qa-performance-rueckstau):** Review F-7 und Re-Review N-2: die Tabelle §3 gegen den Diff, dann ein Widerspruch zweier Zeilen; der Reviewer fing beide. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.

**Lese-Schritt welle-referenzabgleich-release (2026-11-17):** 28 Dateien, davon Beiträge aus Slices dieser Welle (Liste in `welle-referenzabgleich-release-results.md` §Steering-Loop-Einträge). Retirement-Check: die Klasse ist in Slices dieser Welle aufgetreten, die Regel bleibt, keine Lockerung; die Closure macht keine Aussage über die Wirkung der Regel. Ob ein Sensor über die Prosa hinaus entsteht, entscheidet der Nutzer.

**Wiederauftreten (slice-cancel-antwort-zwei-nachrichten-angleichen):** Review F-5 (16 Dateien und zwei Spec-Stellen fehlten, eine genannte war nicht berührt) und Verifikation F-3 (die Folge-ADR fehlte in Kopf, §3 und DoD); der Reviewer und der Verifier fingen es. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.
