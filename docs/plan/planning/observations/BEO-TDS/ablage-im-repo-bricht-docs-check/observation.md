# Ein Werkzeug, das im Repo ablegt, bricht das Doku-Gate beim Abbruch

**Sub-Area:** `*` (Make-Rezepte, die Ausgaben von Docker-Stages einsammeln)

Legt ein Make-Rezept eine Ausgabe in einem Verzeichnis unter dem Repo ab (hier Modus 700) und räumt sie erst am Rezeptende, bleibt das Verzeichnis nach einem Abbruch liegen, und `make docs-check` bricht fail-closed mit `permission denied`. Behoben wird durch eine Ablage außerhalb des Repos (`mktemp -d`) mit `trap`.
