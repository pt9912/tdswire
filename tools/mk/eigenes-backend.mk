# tools/mk/eigenes-backend.mk — Lauf mit eigenem In-Memory-Backend: .NET-Referenzclient gegen den Serverprozess, dessen
# Backend eine eigene `QueryBackend`-Implementierung ist (TDS-FA-08, TDS-RB-04). Repo-eigen, eingebunden von repo.mk; das
# Target hat seine Zeile in harness/README.md. Kein Gate: der Lauf gehoert zum E2E-Durchgang `make e2e` (Stufe 8, ADR-0022)
# und steht nicht in `GATE_CHECKS`.
.PHONY: eigenes-backend

eigenes-backend: ## Referenzclient gegen den Serverprozess mit eigenem Backend (nicht die Simulation): gefuelltes und leeres Ergebnis, Fehlerpfade, Server bleibt verfuegbar (Dockerfile-Stage `eigenesbackendserver`, referenztest-dotnet-Stage `eigenes-backend`) — KEIN Gate
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/eigenes-backend-test.sh
