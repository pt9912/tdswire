# tools/mk/heap-abschluss.mk — Heap- und Abschluss-Messung (TDS-QA-04, ADR-0006): Live-Heap beim Strom von 10 Mio Zeilen und Verzoegerung des
# zurueckgehaltenen Abschlusses. Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md. Kein Gate (Laufzeit, Last
# des Hosts, ADR-0022): der Lauf steht nicht in `GATE_CHECKS` und nicht in `make e2e`. Parameter und Zellen: Kopf von tools/heap-abschluss.sh.
.PHONY: heap-abschluss heap-abschluss-tabellen

heap-abschluss: ## Heap- und Abschluss-Messung in Docker (Zellen waehlbar mit HEAP_ZELLEN): Live-Heap-Stichprobe beim Strom von 10 Mio Zeilen (1, 8, 16 Sitzungen, Aufbau B und C), Verzoegerung des zurueckgehaltenen Abschlusses (Rohbytes-Client) — KEIN Gate
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/heap-abschluss.sh

heap-abschluss-tabellen: ## Tabellen eines Messprotokolls aus einer Rohzeilen-Datei der Heap- und Abschluss-Messung setzen: ROHZEILEN=<Datei> — KEIN Gate
	@test -n "$(ROHZEILEN)" || { echo "heap-abschluss-tabellen: ROHZEILEN=<Datei> fehlt" >&2; exit 2; }
	bash tools/heap-abschluss-tabellen.sh "$(ROHZEILEN)"
