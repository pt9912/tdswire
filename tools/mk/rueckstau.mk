# tools/mk/rueckstau.mk — Rueckstau-Messung (TDS-QA-04, ADR-0006): langsamer Leser mit mehreren Verbindungen, blockierendes Backend,
# Simulations-Backend. Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md. Kein Gate (Laufzeit, Last
# des Hosts, ADR-0022): der Lauf steht nicht in `GATE_CHECKS` und nicht in `make e2e`. Parameter und Zellen: Kopf von tools/rueckstau.sh.
.PHONY: rueckstau rueckstau-tabellen

rueckstau: ## Rueckstau-Messung in Docker (Zellen waehlbar mit RUECKSTAU_ZELLEN): langsamer Leser (Socket, 1 bis 256 Verbindungen), blockierendes Backend, Abbruch, Massenladen, Eigenes und Simulations-Backend — KEIN Gate
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/rueckstau.sh

rueckstau-tabellen: ## Tabellen eines Messprotokolls aus einer Rohzeilen-Datei der Rueckstau-Messung setzen: ROHZEILEN=<Datei> — KEIN Gate
	@test -n "$(ROHZEILEN)" || { echo "rueckstau-tabellen: ROHZEILEN=<Datei> fehlt" >&2; exit 2; }
	bash tools/rueckstau-tabellen.sh "$(ROHZEILEN)"
