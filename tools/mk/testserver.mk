# tools/mk/testserver.mk - Testserver von Hand starten und stoppen (TDS-RB-01, TDS-QA-06, ADR-0025; Dockerfile-Stage
# `testserver`). Repo-eigen, eingebunden von repo.mk; jedes Target hat seine Zeile in harness/README.md.
# Kein Gate: ein dauerhaft laufender Container gehoert in keinen Durchgang (ADR-0022). Variablen und Voreinstellungen:
# Kopf von tools/testserver.sh.
.PHONY: testserver-up testserver-down testserver-logs testserver-status testserver-selbsttest

testserver-up: ## Testserver als Container im Hintergrund starten, Port auf 127.0.0.1:1433 (TESTSERVER_PORT=, TESTSERVER_HOST=, TESTSERVER_BESCHREIBUNG=, TESTSERVER_BENUTZER_DATEI=), Image tdswire-testserver:lokal
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/testserver.sh up

testserver-down: ## Den Testserver-Container stoppen und entfernen (ohne Container: Erfolg ohne Wirkung)
	bash tools/testserver.sh down

testserver-logs: ## Log des Testserver-Containers (TESTSERVER_FOLGEN=1 verfolgt es)
	bash tools/testserver.sh logs

testserver-status: ## Laeuft der Testserver-Container? Exit 0 ja, 1 nein
	bash tools/testserver.sh status

testserver-selbsttest: ## Selbsttest der Targets: up, Rohbytes-Login vom Host-Netz, down, Port frei; Mutationen je Zweig (kein Gate)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/testserver-selbsttest.sh
