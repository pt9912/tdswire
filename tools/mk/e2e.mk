# tools/mk/e2e.mk — Produkt-E2E-Durchgang (TDS-QA-01, TDS-QA-06, ADR-0022). Repo-eigen, eingebunden von repo.mk;
# das Target hat seine Zeile in harness/README.md. Kein Gate: es steht nicht in GATE_CHECKS, und der Workflow
# .github/workflows/e2e.yml ruft es beim Push.
.PHONY: e2e

e2e: ## E2E-Durchgang: Smoke, Container-Laufzeittest, Referenzclient gegen Simulations-Backend, Referenztest-Gerüst nacheinander — KEIN Gate
	bash tools/e2e.sh
