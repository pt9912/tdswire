# tools/mk/simulationstest.mk — Integrationslauf des Simulations-Backends mit dem .NET-Referenzclient (SPEC-040,
# ADR-0012, ADR-0008). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Gate: Setzung (Massstab wie bei `laufzeittest`, nicht gemessen vorgegeben): ein Pruefschritt gehoert in
# `make gates`, wenn sein Warmlauf (Images im Docker-Cache) unter der Laufzeit der Stage `test` ohne Cache liegt
# (89 bis 102 s, gemessen am 2026-10-31); der Kaltlauf wird nicht verlangt. Gemessen am 2026-10-31, je ein Lauf:
# Warmlauf dieses Targets 25 s (Bau 9 s, Tests 16 s), Kaltlauf 131 s (Bau 113 s, Tests 18 s). Die Laufzeit der CI
# mit dem Gate ist nicht gemessen.
.PHONY: simulations-integration

simulations-integration: ## Referenzclient gegen den Serverprozess mit Simulations-Backend: Auswahl, leeres Ergebnis, Zeilenquelle ueber dem Heap, Fehler der Zeilenquelle (Dockerfile-Stage `integrationsserver`, referenztest-dotnet-Stage `integration`)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/simulationstest.sh

GATE_CHECKS += simulations-integration
