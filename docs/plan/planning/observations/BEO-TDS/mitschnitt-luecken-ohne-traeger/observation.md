# Abweichungen des Servers aus dem Mitschnitt-Lauf stehen als Lücke, ohne dass eine Datei sie baut

**Sub-Area:** `*` (Protokollantworten, Referenzabgleich)

Der Mitschnitt-Lauf (`make vergleichslauf-d LAUF=mitschnitt`) führt Abweichungen des Servers von der Referenzinstanz als Art *Lücke* (Messprotokoll `docs/messungen/referenzabgleich.md`, A-10 `curCmd` der DONE-Tokens ist 0, A-11 Länge im TYPE_INFO von `DECIMALN`, A-15 `WAITFOR` und der Abbruch eines wartenden Befehls, A-18 SESSIONSTATE bei Sitzungswiederaufnahme). Keine Datei im Planning-Lifecycle führt den Bau in DoD oder Risiko; A-11 und A-18 haben keine untersuchte Ursache. Ohne Träger bleibt die Lücke eine Zeile der Ausnahmeliste.
