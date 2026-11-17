**Stand:** offen


**Lese-Schritt welle-fehler-abbruch (2026-11-06):** 6× gelesen, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung), siehe `welle-fehler-abbruch-results.md` §Steering-Loop-Einträge und §Offene Fragen. Die Closure ändert weder Regel noch Gate. Der Stand `offen` bleibt.

**Lese-Schritt welle-produktschnitt (2026-11-07):** 6× gelesen (Belege dieser Welle: `slice-testserver-herausloesen`, `slice-jar-ohne-main-und-image`, `slice-explicit-api-umbau`), über der Schwelle, nicht verkörpert; das Register trägt keine Empfehlung, die Frage steht als Frage 1 in `welle-produktschnitt-results.md` §Offene Fragen. Der Stand `offen` bleibt.

**Wiederauftreten (slice-release-lizenzen-lib-belegen):** Verifikation F-2: die Zusage „das ZIP führt alle Lizenzdateien“ hat bis zum Bau in `slice-release-dateien-bauen` keinen Sensor; der Plan dort führt jetzt die Sollmenge der sieben Dateien und die Mutation „NOTICE entfernt“ (Träger, nicht gebaut). Stand `offen` bleibt; 7× über der Schwelle, Entscheidung beim Nutzer.

**Wiederauftreten (slice-release-dateien-bauen):** Re-Review N-1 und N-6, Verifikation F-4: die Zusage „ein Release nutzt den Vorschau-Modus nie“ hat nur die Abweisung eines Vorschau-ZIP als Sensor; die `&&`-Kette der Selbsttests im `Dockerfile` maskiert nach dem ersten roten Selbsttest die folgenden (Diagnose, nicht Gate). Der Sensor für die Sollmenge der Lizenzdateien (siehe Eintrag davor) ist mit diesem Slice gebaut: NOTICE entfernt ist am echten ZIP rot (Verifikation b2). Stand `offen` bleibt; 8× über der Schwelle, Entscheidung beim Nutzer.

**Wiederauftreten (slice-release-job-im-workflow-bauen):** Pins gegen die echte Action, bedingte Ausführung und Reihenfolge zur Laufzeit trägt kein Sensor; der Selbsttest liest den Workflow als Text (Rechte, `needs`, Token-Ort, Bedingung, Form der Pins, 21 Gegenproben). Die Zusage „ein Release nutzt den Vorschau-Modus nie“ ist mit dem Job `release` (ohne `VORSCHAU=1`) und der Abweisung eines `*-vorschau.zip` im Eingangsskript am Stub gedeckt, nicht in der CI. Stand `offen` bleibt; 9× über der Schwelle, Entscheidung beim Nutzer.

**Lese-Schritt welle-referenzabgleich-release (2026-11-17):** 9× gelesen, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung), siehe `welle-referenzabgleich-release-results.md` §Steering-Loop-Einträge. Die Closure ändert weder Regel noch Gate. Der Stand `offen` bleibt.
