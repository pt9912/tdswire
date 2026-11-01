# tools/mk/kompatibilitaet.mk — Kompatibilitaetslauf: .NET-Referenzclient gegen den Serverprozess mit Simulations-Backend
# (ADR-0019, ADR-0008, TDS-QA-01). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in
# harness/README.md. Kein Gate: der Lauf gehoert zum E2E-Durchgang `make e2e` (Stufen 5 bis 8, ADR-0022) und steht
# nicht in `GATE_CHECKS`.
.PHONY: kompatibilitaet

KATEGORIE ?= Szenario

kompatibilitaet: ## Referenzclient gegen den Serverprozess (zwei Container): KATEGORIE=Szenario|Anmeldung|Luecken|Synchronitaet — KEIN Gate
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/kompatibilitaetstest.sh "$(KATEGORIE)"
