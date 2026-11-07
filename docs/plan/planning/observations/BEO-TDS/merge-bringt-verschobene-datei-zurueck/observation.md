# Ein Merge löst „geändert gegen gelöscht“ zugunsten der Änderung und bringt eine verschobene Datei zurück

**Sub-Area:** `*` (Planung, Lifecycle)

`main` verschob einen Slice per `git mv` nach `done/`, der Zweig hatte die Datei in `open/` geändert (Verweiskorrekturen); die Auflösung des Konflikts behielt die Änderung, und der Slice lag in zwei Zuständen. `make docs-check` meldet nichts. Prüfpunkt beim Merge: `git diff --name-status <main> HEAD -- docs/plan/planning` auf `A` in einem Lifecycle-Verzeichnis, dessen Datei anderswo liegt.
