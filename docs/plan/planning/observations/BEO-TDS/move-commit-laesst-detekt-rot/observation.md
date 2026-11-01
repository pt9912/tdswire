# Ein reiner Move-Commit lässt das Repo kurz rot, weil Paketdeklaration und Verzeichnis auseinanderliegen

**Sub-Area:** `*` (Workflow, `AGENTS.md` §3.3)

`AGENTS.md` §3.3 verlangt für Verschieben und Umschreiben zwei Commits. Wird eine Kotlin-Datei in ein anderes Verzeichnis verschoben, steht ihre Paketdeklaration im Move-Commit noch im alten Paket, und detekt ist rot, bis der Inhalts-Commit folgt. Beide Commits gehören in denselben Push, sonst wird der rote Zwischenstand der geprüfte Stand.
