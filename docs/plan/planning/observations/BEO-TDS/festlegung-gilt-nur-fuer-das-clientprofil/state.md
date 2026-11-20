**Stand:** offen — 3× (Evidence: [slice-prelogin-serverversion](../../../done/slice-prelogin-serverversion.md), [slice-testserver-manuell-doku](../../../done/slice-testserver-manuell-doku.md), [slice-d-migrate-verbindung-und-export](../../../done/slice-d-migrate-verbindung-und-export.md)), Schwelle erreicht; die Klasse (Festlegung wirkt nur für Clients des Profils) ist weder verkörpert noch geplant; der Lese-Schritt gehört in die Closure von [welle-testserver-manuell](../../../done/welle-testserver-manuell.md). Offene Frage dahinter: ob JDBC oder d-migrate ins Clientprofil gehören ([ADR-0001](../../../../adr/0001-tds-version-und-clientprofil.md)), Entscheidung beim Nutzer.


**Lese-Schritt welle-fehler-abbruch (2026-11-06):** 3× gelesen, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung), siehe `welle-fehler-abbruch-results.md` §Steering-Loop-Einträge und §Offene Fragen. Die Closure ändert weder Regel noch Gate. Der Stand `offen` bleibt.

**Lese-Schritt welle-testserver-manuell (2026-11-13):** 3× gelesen, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer, ob d-migrate 1.12.0 mit mssql-jdbc 13.4.0 ins Clientprofil gehört (Frage 1 in `welle-testserver-manuell-results.md`, Empfehlung dort). Die Closure ändert weder Regel noch Gate. Der Stand `offen` bleibt.

**Wiederauftreten (slice-antwortform-nbcrow, 2026-11-17):** 4×. Die Wahl zwischen ROW und NBCROW ist am Referenzclient gemessen; mssql-jdbc ist gegen diesen Server nicht gemessen (Verifikation V-1). Die Klasse ist weder verkörpert noch geplant; Folge-Messauftrag und Entscheidung liegen beim Nutzer.

**Lese-Schritt welle-referenzabgleich-release (2026-11-17):** 4× gelesen, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung), siehe `welle-referenzabgleich-release-results.md` §Steering-Loop-Einträge. Die Closure ändert weder Regel noch Gate. Der Stand `offen` bleibt.

**Wiederauftreten (slice-cancel-antwort-zwei-nachrichten-angleichen):** 5×: die Zwei-Nachrichten-Form ist am Referenzclient gemessen und belegt, für mssql-jdbc nicht. Die Klasse ist weder verkörpert noch geplant; Folge-Messauftrag und Entscheidung liegen beim Nutzer.
