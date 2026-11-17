# tools/mk/lasttest.mk — Lasttest des Referenz-Workloads (TDS-QA-04, ADR-0006): Aufbauten A bis C, Messzeilen mit Median, Minimum und
# Maximum. Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md. Kein Gate (Laufzeit, Last des Hosts,
# ADR-0022): der Lauf steht nicht in `GATE_CHECKS`; `make e2e` fuehrt ihn als Stufe 9 in Probengroesse aus (`LASTTEST_ZEILEN` und
# `LASTTEST_WDH` setzt tools/e2e.sh). Parameter und Vorgaben: Kopf von tools/lasttest.sh.
.PHONY: lasttest lasttest-tabellen

lasttest: ## Lasttest des Referenz-Workloads in Docker (--cpus, --memory, fester Seed, Wiederholungen mit Median, Minimum, Maximum): Aufbau A Backend allein, B eigenes Backend, C Simulations-Backend, je mit und ohne Senke — KEIN Gate
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/lasttest.sh

lasttest-tabellen: ## Tabellen eines Messprotokolls aus einer Rohzeilen-Datei des Lasttests setzen: ROHZEILEN=<Datei> [STUFEN="stufe1b stufe8"] — KEIN Gate
	@test -n "$(ROHZEILEN)" || { echo "lasttest-tabellen: ROHZEILEN=<Datei> fehlt" >&2; exit 2; }
	bash tools/lasttest-tabellen.sh "$(ROHZEILEN)" $(STUFEN)
