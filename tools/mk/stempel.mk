# tools/mk/stempel.mk - Selbsttests des Stempel-Mechanismus (tools/stempel.sh, tools/stempel/*.eingaenge; ADR-0024,
# ADR-0023, TDS-QA-06). Repo-eigen, eingebunden von repo.mk; jedes Target hat seine Zeile in harness/README.md.
# Beide laufen auf dem Host mit bash und git (die Host-Abhaengigkeiten des Repos), nicht in Docker, und lesen den
# Arbeitsbaum; sie beruehren weder refs/notes/gates noch die Konfiguration des Repos. Zuordnung: Klasse `werkzeug`
# (ADR-0024 Entscheidung 3), sie pruefen Werkzeug und kein Verhalten des Produkts.
# Kein Gate: nichts hieraus steht in `GATE_CHECKS`, `make gates` und die CI (gates.yml fuehrt nur `make gates`) rufen sie nicht;
# Aufnahme in `GATE_CHECKS` erst mit der
# Migration, wenn der werkzeug-Stempel sie ueberspringt. Ein Fehler in tools/stempel*, den Listen oder dem Dockerfile macht
# deshalb kein Gate rot: wer sie aendert, fuehrt `make stempel-selbsttest stempel-eingaenge stempel-abbild` selbst aus.
# Laufzeit (Einzelmessungen 2026-11-01 bei Lastmittel 7 bis 10 durch andere Agenten, kein Beleg fuer andere Hosts; kalt, also
# mit leerem Dateicache, nicht gemessen): stempel-selbsttest 1,2 s, stempel-eingaenge 2,6 s (davon die Pruefung 0,3 s),
# stempel-abbild 2,6 s.
.PHONY: stempel-selbsttest stempel-eingaenge stempel-abbild

stempel-selbsttest: ## Jeder Fehlerpfad von tools/stempel.sh in Wegwerf-Repos: Klon, Hash, Sperre, Amend, Rebase, Squash; KEIN Gate (bash, git)
	bash tools/stempel-selbsttest.sh

stempel-eingaenge: ## COPY-Quellen, Link-Ziele und Make-Dateien stehen in tools/stempel/*.eingaenge; Selbsttest mit Mutationen; KEIN Gate (bash, git)
	bash tools/stempel-eingaenge-pruefung.sh
	bash tools/stempel-eingaenge-selbsttest.sh

stempel-abbild: ## Jeder Eintrag der echten Listen aendert den Hash seiner Klasse, an einem Abbild des Arbeitsbaums; KEIN Gate (bash, git)
	bash tools/stempel-abbild-selbsttest.sh
