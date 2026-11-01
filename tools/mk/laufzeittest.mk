# tools/mk/laufzeittest.mk — Lauf des Testserver-Containers (TDS-RB-01, TDS-QA-06, ADR-0025; Dockerfile-Stagen
# `testserver` und `laufzeit-client`). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Kein Gate: der Lauf gehoert zum E2E-Durchgang `make e2e` (Stufe 2, ADR-0022) und steht nicht in `GATE_CHECKS`.
# Laufzeiten: harness/README.md, Zeile `make laufzeittest`. Den Weg Manifest, Class-Path und Jar-Name des Bibliotheks-Jars
# prueft im Gate allein tools/manifest-pruefung.sh im Bau; dieser Lauf startet nur den Testserver als Container, nicht das Jar.
.PHONY: laufzeittest

laufzeittest: ## Testserver-Container starten, Rohbytes-Login aus zweitem Container, Stop mit Exit-Code 143 (Dockerfile-Stagen `testserver`, `laufzeit-client`)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/laufzeittest.sh
