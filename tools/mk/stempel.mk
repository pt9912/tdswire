# tools/mk/stempel.mk - Gruppenschritte der Gate-Klassen `werkzeug`, `tests` und `stempel` und Selbsttests des Stempel-Mechanismus
# (tools/stempel.sh, tools/stempel-gruppe.sh, tools/stempel/*.eingaenge; ADR-0024, ADR-0030, ADR-0023, TDS-QA-06). Repo-eigen,
# eingebunden von repo.mk; jedes Target hat seine Zeile in harness/README.md.
#
# Zuordnung: Jedes Gate haengt sich mit `GATE_CHECKS += x` an die Kette und steht in genau einer Klasse seines Fragments:
# `GATE_KLASSE_werkzeug += x`, `GATE_KLASSE_tests += x`, `GATE_KLASSE_stempel += x` oder `GATE_KLASSE_immer += x`. Die Klassen
# werkzeug, tests und stempel tragen je einen Stempel; ein Gate steht nur dort, wenn die Eingangsliste der Klasse seinen Eingang
# vollstaendig deckt. Die Klasse stempel (ADR-0030) fasst die vier Selbsttests des Mechanismus: stempel-selbsttest,
# stempel-eingaenge (nur sein Selbsttest mit Mutationen), stempel-abbild und stempel-gruppe; `make gate-zuordnung` verlangt sie
# in der Kette und in dieser Klasse. Die Klasse immer traegt keinen Stempel und laeuft in jedem `make gates`: docs-check liest
# jede Markdown-Datei des Repos, stempel-eingaenge-pruefung alle Make-Fragmente und das Dockerfile, gate-zuordnung die
# Make-Fragmente; ihr Eingang steht nicht in einer Liste. Diese Zuordnung weicht von ADR-0024 Entscheidung 3 und ADR-0030
# Entscheidung 2 ab, die docs-check, gate-zuordnung und stempel-eingaenge-pruefung der Klasse werkzeug zuordnen; sie ist als
# offener Punkt in slice-stempel-migration §6 gefuehrt und gilt bis zur Entscheidung des Architects. Baut ein Gate der Klassen
# werkzeug oder tests eine Docker-Stage, nennt es sie mit `GATE_STAGE += x=<stage>` (`x=-` ohne Stage; die Klasse stempel baut
# keine); `make gate-zuordnung` prueft sie gegen die Stage-Liste der Klasse. Die Zuordnung von `docs-check` und `baseline-verify`,
# deren Fragmente unter harness/mk/ dem Werkzeug gehoeren, steht hier.
# repo.mk ersetzt GATE_CHECKS nach dem Einbinden aller Fragmente durch die Gruppenschritte gruppe-werkzeug, gruppe-tests und
# gruppe-stempel, die Gates der Klasse immer und jedes Gate ohne Klasse; ein Gate ohne Klasse laeuft, und `make gate-zuordnung`
# meldet es rot. Nach Lauf aller Gates einer Klasse schreibt der Gruppenschritt den Stempel (tools/stempel-gruppe.sh); `make test`
# und jedes andere Gate allein schreiben nichts. `make gates FORCE=1` ueberspringt die Pruefung der Stempel; `make clean-build`
# fragt keinen. Die Selbsttests und die Skripte der Gruppenschritte laufen auf dem Host mit bash und git (die Host-Abhaengigkeiten
# des Repos), nicht in Docker, und lesen den Arbeitsbaum; sie beruehren weder refs/notes/gates noch die Konfiguration des Repos.
# Die Eingaenge der Klasse stempel stehen mit ihrer Herleitung in tools/stempel/stempel.eingaenge; die CI sieht keine Noten
# und fuehrt alle Klassen aus.
#
# Laufzeit von `make gates` (Einzelmessungen 2026-11-01 auf einem Host, Docker-Layer-Cache warm, Lastmittel in Klammern, keine
# Streuung behauptet; kalt, mit leerem Cache, nicht gemessen; gilt nicht fuer die CI):
#   Alle drei Klassen mit Beleg: 5,8 / 8,6 s (Lastmittel 20), 4,6 / 4,2 s nach einer Doku-Aenderung (20, alle drei Klassen
#   uebersprungen); harness/README.md geaendert 9,9 s (16, werkzeug laeuft); tools/gate-zuordnung.sh geaendert 21,2 s (18,
#   werkzeug und stempel laufen); Dockerfile geaendert 32,6 s (18, alle drei laufen). Die drei ungestempelten Gates (Klasse immer)
#   machen davon rund 1,7 s aus. Nur src/ geaendert 254 s (4), fast alles der Gradle-Neubau der Stages test, detekt und build.
#   Einzeln, warm, ohne Stempel: docs-check 1,2 s, baseline-verify 0,03 s, detekt-selbsttest 0,9 s, skript-selbsttest 0,9 s,
#   kopplung-zahlen 0,9 s, gate-zuordnung 0,13 s, stempel-eingaenge-pruefung 0,3 s, test 1,1 s, detekt 1,0 s, build 1,1 s,
#   referenztest-dotnet 1,1 s, stempel-selbsttest 1,3 s, stempel-eingaenge (Selbsttest) rund 3 s, stempel-abbild 3,0 s,
#   stempel-gruppe 2,3 s (die vier letzten bei Lastmittel 3 bis 5).
.PHONY: gruppe-werkzeug gruppe-tests gruppe-stempel gate-zuordnung stempel-eingaenge-pruefung stempel-selbsttest stempel-eingaenge stempel-abbild stempel-gruppe

GATE_KLASSE_werkzeug += baseline-verify
GATE_KLASSE_immer += docs-check gate-zuordnung stempel-eingaenge-pruefung
GATE_KLASSE_stempel += stempel-selbsttest stempel-eingaenge stempel-abbild stempel-gruppe
GATE_STAGE += baseline-verify=- stempel-selbsttest=- stempel-eingaenge=- stempel-abbild=- stempel-gruppe=-

# Flags von make, unter denen ein Gruppenschritt keine Gate-Laeufe belegen kann: Rezepte mit $(MAKE) laufen auch unter -n, -t und
# -q, ohne dass ein Gate laeuft (STEMPEL_TROCKEN=1: keine Pruefung, kein Lauf, kein Stempel); unter -i meldet das Unter-Make
# rote Gates als gruen (STEMPEL_KEIN=1: Lauf ohne Stempel). Gelesen wird das erste Wort von MAKEFLAGS, die gebuendelten Kurzflags.
STEMPEL_FLAGS := $(filter-out -%,$(firstword $(MAKEFLAGS)))
STEMPEL_TROCKEN := $(if $(or $(findstring n,$(STEMPEL_FLAGS)),$(findstring t,$(STEMPEL_FLAGS)),$(findstring q,$(STEMPEL_FLAGS))),1)
STEMPEL_KEIN := $(if $(findstring i,$(STEMPEL_FLAGS)),1)

# GATE_GRUPPE_<klasse>: die Gates der Klasse, die in GATE_CHECKS stehen (von repo.mk gesetzt).
gruppe-werkzeug: ## Gates der Klasse werkzeug, uebersprungen bei passendem Stempel (FORCE=1 erzwingt); Teil von make gates
	@MAKE='$(MAKE) --no-print-directory' FORCE='$(FORCE)' TROCKEN='$(STEMPEL_TROCKEN)' KEIN_STEMPEL='$(STEMPEL_KEIN)' bash tools/stempel-gruppe.sh werkzeug $(GATE_GRUPPE_werkzeug)

gruppe-tests: ## Gates der Klasse tests, uebersprungen bei passendem Stempel (FORCE=1 erzwingt); Teil von make gates
	@MAKE='$(MAKE) --no-print-directory' FORCE='$(FORCE)' TROCKEN='$(STEMPEL_TROCKEN)' KEIN_STEMPEL='$(STEMPEL_KEIN)' bash tools/stempel-gruppe.sh tests $(GATE_GRUPPE_tests)

gruppe-stempel: ## Selbsttests des Stempel-Mechanismus, uebersprungen bei passendem Stempel (FORCE=1 erzwingt); Teil von make gates
	@MAKE='$(MAKE) --no-print-directory' FORCE='$(FORCE)' TROCKEN='$(STEMPEL_TROCKEN)' KEIN_STEMPEL='$(STEMPEL_KEIN)' bash tools/stempel-gruppe.sh stempel $(GATE_GRUPPE_stempel)

gate-zuordnung: ## Jedes Target in GATE_CHECKS gehoert zu genau einer Klasse, seine Stage zur Klasse, die vier Selbsttests zu stempel; mit Selbsttest (bash)
	@GZ_ENDKETTE='$(GATE_CHECKS)' GZ_GRUPPE_W='$(GATE_GRUPPE_werkzeug)' GZ_GRUPPE_T='$(GATE_GRUPPE_tests)' GZ_GRUPPE_S='$(GATE_GRUPPE_stempel)' \
		bash tools/gate-zuordnung.sh '$(GATE_ROH)' '$(GATE_KLASSE_werkzeug)' '$(GATE_KLASSE_tests)' '$(GATE_KLASSE_stempel)' '$(GATE_KLASSE_immer)' '$(GATE_STAGE)'
	@bash tools/gate-zuordnung-selbsttest.sh

stempel-eingaenge-pruefung: ## COPY-Quellen, Link-Ziele und Make-Dateien stehen in tools/stempel/*.eingaenge (ohne Selbsttest; bash, git)
	@bash tools/stempel-eingaenge-pruefung.sh

GATE_CHECKS += gate-zuordnung stempel-eingaenge-pruefung stempel-selbsttest stempel-eingaenge stempel-abbild stempel-gruppe

stempel-selbsttest: ## Jeder Fehlerpfad von tools/stempel.sh in Wegwerf-Repos: Klon, Hash, Sperre, Amend, Rebase, Squash (bash, git); Klasse stempel
	bash tools/stempel-selbsttest.sh

stempel-eingaenge: ## Selbsttest der Pruefung von tools/stempel/*.eingaenge mit Mutationen am Dockerfile, an Listen und Links (bash, git); Klasse stempel
	bash tools/stempel-eingaenge-selbsttest.sh

stempel-abbild: ## Jeder Eintrag der echten Listen aendert den Hash seiner Klasse, an einem Abbild des Arbeitsbaums (bash, git); Klasse stempel
	bash tools/stempel-abbild-selbsttest.sh

stempel-gruppe: ## Zusammenspiel der Gruppenschritte und Flags von make mit den echten Listen an einem Abbild des Arbeitsbaums (bash, git); Klasse stempel
	bash tools/stempel-gruppe-selbsttest.sh
