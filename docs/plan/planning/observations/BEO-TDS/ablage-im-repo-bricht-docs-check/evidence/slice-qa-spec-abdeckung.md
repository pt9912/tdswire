**Vorgang:** slice-qa-spec-abdeckung
**Fund:** Verifikation F-7 fand die Ablage `.harness/state/spec-abdeckung` (Modus 700) nach einem Abbruch des Rezepts liegen und `make docs-check` rot; das Rezept legt jetzt nach `mktemp -d` außerhalb des Repos ab und räumt per `trap` bei Erfolg, Fehler und Abbruch.
