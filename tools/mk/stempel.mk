# tools/mk/stempel.mk - Gruppenschritte der zwei Gate-Klassen `werkzeug` und `tests` und Selbsttests des Stempel-Mechanismus
# (tools/stempel.sh, tools/stempel-gruppe.sh, tools/stempel/*.eingaenge; ADR-0024, ADR-0023, TDS-QA-06). Repo-eigen,
# eingebunden von repo.mk; jedes Target hat seine Zeile in harness/README.md.
#
# Zuordnung: jedes Gate haengt sich mit `GATE_CHECKS += x` an die Kette und nennt seine Klasse mit
# `GATE_KLASSE_werkzeug += x` oder `GATE_KLASSE_tests += x` in seinem Fragment (Regel: ADR-0024 Entscheidung 3); baut es eine
# Docker-Stage, nennt es sie mit `GATE_STAGE += x=<stage>`, und `make gate-zuordnung` prueft sie gegen die Stage-Liste der Klasse. Die
# Zuordnung von `docs-check` und `baseline-verify`, deren Fragmente unter harness/mk/ dem Werkzeug gehoeren, steht hier.
# repo.mk ersetzt GATE_CHECKS nach dem Einbinden aller Fragmente durch die zwei Gruppenschritte gruppe-werkzeug und gruppe-tests
# und jedes Gate ohne Klasse: das laeuft weiter immer, und `make gate-zuordnung` meldet es rot. Nach Lauf aller Gates einer Klasse
# schreibt der Gruppenschritt den Stempel (tools/stempel-gruppe.sh); `make test` und jedes andere Gate allein schreiben nichts.
# `make gates FORCE=1` ueberspringt die Pruefung der Stempel; `make clean-build` fragt keinen Stempel.
# Die Selbsttests und die Skripte der Gruppenschritte laufen auf dem Host mit bash und git (die Host-Abhaengigkeiten des Repos), nicht in Docker, und
# lesen den Arbeitsbaum; sie beruehren weder refs/notes/gates noch die Konfiguration des Repos.
#
# In `GATE_CHECKS` stehen von den Selbsttests nur gate-zuordnung (mit seinem Selbsttest) und stempel-eingaenge-pruefung; die
# uebrigen vier (stempel-selbsttest, stempel-eingaenge, stempel-abbild, stempel-gruppe) sind kein Gate, weil sie `make gates`
# merklich verlaengerten (Laufzeiten unten). Ein Fehler in tools/stempel*, den Listen oder dem Dockerfile-Bezug macht darum nur
# die Pruefung der Listen rot: wer tools/stempel* aendert, fuehrt `make stempel-selbsttest stempel-eingaenge stempel-abbild
# stempel-gruppe` selbst aus. Die CI ruft sie nicht.
#
# Laufzeit von `make gates` (Einzelmessungen 2026-11-01 auf einem Host, Docker-Layer-Cache warm, Lastmittel 3 bis 5 durch andere
# Agenten, keine Streuung behauptet; kalt, mit leerem Cache, nicht gemessen; gilt nicht fuer die CI, die ohne Note voll laeuft):
#   vorher (Altmechanismen, Stand 0db1db4): warm 9,2 / 8,2 / 8,3 s; Dockerfile geaendert 14,2 s.
#   nachher: beide Klassen mit Beleg 2,3 / 2,2 s; Amend ohne Inhaltsaenderung 2,0 s (notes.rewriteRef gesetzt); nur Doku
#   geaendert 6,8 s (tests uebersprungen); Dockerfile geaendert 15,0 s (beide laufen); erster Lauf ohne Beleg 11,1 s (Dockerfile
#   neu); nur src/ geaendert 254 s, davon fast alles der Gradle-Neubau der Stages test, detekt und build, nicht gegen den
#   Altmechanismus gemessen (dessen Cache bricht an derselben Stelle).
#   Gates einzeln, warm: docs-check 1,2 s, baseline-verify 0,03 s, detekt-selbsttest 0,9 s, skript-selbsttest 0,9 s,
#   kopplung-zahlen 0,9 s, gate-zuordnung 0,13 s, stempel-eingaenge-pruefung 0,3 s, test 1,1 s, detekt 1,0 s, build 1,1 s,
#   referenztest-dotnet 1,1 s. Die Selbsttests, die kein Gate sind: stempel-selbsttest 1,3 s, stempel-eingaenge 3,5 s,
#   stempel-abbild 3,0 s, stempel-gruppe 2,3 s; als Gates der Klasse werkzeug liefen sie bei jeder Aenderung unter docs/ mit
#   (gerechnet, nicht gemessen: 6,8 s plus 10,1 s ergaeben 16,9 s).
.PHONY: gruppe-werkzeug gruppe-tests gate-zuordnung stempel-eingaenge-pruefung stempel-selbsttest stempel-eingaenge stempel-abbild stempel-gruppe

GATE_KLASSE_werkzeug += docs-check baseline-verify gate-zuordnung stempel-eingaenge-pruefung

# Trockenlauf (`make -n`): Rezepte mit $(MAKE) laufen auch dann; STEMPEL_TROCKEN=1 sagt dem Gruppenschritt, dass er weder
# Gates ruft noch einen Stempel schreibt.
STEMPEL_TROCKEN := $(if $(findstring n,$(filter-out -%,$(firstword $(MAKEFLAGS)))),1)

# GATE_GRUPPE_<klasse>: die Gates der Klasse, die in GATE_CHECKS stehen (von repo.mk gesetzt).
gruppe-werkzeug: ## Gates der Klasse werkzeug, uebersprungen bei passendem Stempel (FORCE=1 erzwingt); Teil von make gates
	@MAKE='$(MAKE) --no-print-directory' FORCE='$(FORCE)' TROCKEN='$(STEMPEL_TROCKEN)' bash tools/stempel-gruppe.sh werkzeug $(GATE_GRUPPE_werkzeug)

gruppe-tests: ## Gates der Klasse tests, uebersprungen bei passendem Stempel (FORCE=1 erzwingt); Teil von make gates
	@MAKE='$(MAKE) --no-print-directory' FORCE='$(FORCE)' TROCKEN='$(STEMPEL_TROCKEN)' bash tools/stempel-gruppe.sh tests $(GATE_GRUPPE_tests)

gate-zuordnung: ## Jedes Target in GATE_CHECKS gehoert zu genau einer Klasse werkzeug oder tests; mit Selbsttest (bash)
	@bash tools/gate-zuordnung.sh '$(GATE_ROH)' '$(GATE_KLASSE_werkzeug)' '$(GATE_KLASSE_tests)' '$(GATE_STAGE)'
	@bash tools/gate-zuordnung-selbsttest.sh

stempel-eingaenge-pruefung: ## COPY-Quellen, Link-Ziele und Make-Dateien stehen in tools/stempel/*.eingaenge (ohne Selbsttest; bash, git)
	@bash tools/stempel-eingaenge-pruefung.sh

GATE_CHECKS += gate-zuordnung stempel-eingaenge-pruefung

stempel-selbsttest: ## Jeder Fehlerpfad von tools/stempel.sh in Wegwerf-Repos: Klon, Hash, Sperre, Amend, Rebase, Squash; KEIN Gate (bash, git)
	bash tools/stempel-selbsttest.sh

stempel-eingaenge: ## COPY-Quellen, Link-Ziele und Make-Dateien stehen in tools/stempel/*.eingaenge; Selbsttest mit Mutationen; KEIN Gate (bash, git)
	bash tools/stempel-eingaenge-pruefung.sh
	bash tools/stempel-eingaenge-selbsttest.sh

stempel-abbild: ## Jeder Eintrag der echten Listen aendert den Hash seiner Klasse, an einem Abbild des Arbeitsbaums; KEIN Gate (bash, git)
	bash tools/stempel-abbild-selbsttest.sh

stempel-gruppe: ## Zusammenspiel der Gruppenschritte mit den echten Listen an einem Abbild des Arbeitsbaums; KEIN Gate (bash, git)
	bash tools/stempel-gruppe-selbsttest.sh
