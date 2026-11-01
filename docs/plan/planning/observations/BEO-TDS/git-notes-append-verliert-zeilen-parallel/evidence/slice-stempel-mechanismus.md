**Vorgang:** slice-stempel-mechanismus
**Fund:** Selbsttest `tools/stempel-selbsttest.sh` (Kopf, Messung zu gleichzeitigem Schreiben) und Verifikation d00ead2, Schwerpunkt 7: 60 `git notes append` aus zwei Arbeitsbäumen, 60-mal Exit 0, 39 Zeilen blieben (Implementer: 36 bis 47 von 60 in mehreren Läufen). Mit der Sperre des Skripts 80 gesetzt, 0 ungesetzt. Einzelmessungen, Git 2.43.0, ein Host.
