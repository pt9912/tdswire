# tools/mk/stempel.mk - Selbsttests des Stempel-Mechanismus (tools/stempel.sh, tools/stempel/*.eingaenge; ADR-0024,
# ADR-0023, TDS-QA-06). Repo-eigen, eingebunden von repo.mk; jedes Target hat seine Zeile in harness/README.md.
# Beide laufen auf dem Host mit bash und git (die Host-Abhaengigkeiten des Repos), nicht in Docker, und lesen den
# Arbeitsbaum; sie beruehren weder refs/notes/gates noch die Konfiguration des Repos. Zuordnung: Klasse `werkzeug`
# (ADR-0024 Entscheidung 3), sie pruefen Werkzeug und kein Verhalten des Produkts.
.PHONY: stempel-selbsttest stempel-eingaenge

stempel-selbsttest: ## Jeder Fehlerpfad von tools/stempel.sh in Wegwerf-Repos: Klon, Hash, Sperre, Amend, Rebase, Squash (bash, git)
	bash tools/stempel-selbsttest.sh

stempel-eingaenge: ## Jede COPY-Quelle der Klassen werkzeug und tests steht in tools/stempel/*.eingaenge; Selbsttest mit Mutationen (bash, git)
	bash tools/stempel-eingaenge-pruefung.sh
	bash tools/stempel-eingaenge-selbsttest.sh

GATE_CHECKS += stempel-selbsttest stempel-eingaenge
