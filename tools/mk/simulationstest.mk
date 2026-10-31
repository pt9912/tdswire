# tools/mk/simulationstest.mk — Integrationslauf des Simulations-Backends mit dem .NET-Referenzclient (SPEC-040,
# ADR-0012, ADR-0008). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Kein Gate: der Lauf gehoert zum E2E-Durchgang `make e2e` (Stufe 3, ADR-0022) und steht nicht in `GATE_CHECKS`.
# Gemessen am 2026-10-31, je ein Lauf: Warmlauf dieses Targets 25 s (Bau 9 s, Tests 16 s), Kaltlauf 131 s (Bau 113 s,
# Tests 18 s); ein spaeterer Warmlauf am selben Tag brauchte 19 s (Bau 3 s, Tests 15 s). Einzelmessungen, keine Streuung.
.PHONY: simulations-integration

simulations-integration: ## Referenzclient gegen den Serverprozess mit Simulations-Backend: Auswahl, leeres Ergebnis, Zeilenquelle ueber dem Heap, Fehler der Zeilenquelle (Dockerfile-Stage `integrationsserver`, referenztest-dotnet-Stage `integration`)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/simulationstest.sh
