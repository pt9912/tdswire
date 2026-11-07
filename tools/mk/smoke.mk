# tools/mk/smoke.mk — Prozesstests des Serverprozesses in Docker (Dockerfile-Stage `smoke`: ServerSmokeTest und
# ZustandsereignisseProzessTest, TDS-FA-09, TDS-FA-01). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Kein eigener Eintrag in GATE_CHECKS: beide Testklassen laufen auch in der Stage `test` und damit in `make gates`.
.PHONY: smoke

smoke: ## Prozesstests: Serverprozess ueber main starten, Login-Pfad mit Rohbytes-Client, Startdiagnose und Zustandsereignisse (Dockerfile-Stage `smoke`)
	docker build $(BUILDER_FLAGS) --target smoke --progress=plain .
