# tools/mk/smoke.mk — Rohbytes-Smoke des Serverprozesses in Docker (Dockerfile-Stage `smoke`, TDS-FA-09,
# TDS-FA-01). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Kein eigener Eintrag in GATE_CHECKS: ServerSmokeTest laeuft auch in der Stage `test` und damit in `make gates`.
.PHONY: smoke

smoke: ## Rohbytes-Smoke: Serverprozess ueber main starten, Login-Pfad mit Rohbytes-Client (Dockerfile-Stage `smoke`)
	docker build --target smoke --progress=plain .
