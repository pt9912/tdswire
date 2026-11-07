# tools/mk/skript-selbsttest.mk — Selbsttest der Skripte tools/e2e.sh und tools/manifest-pruefung.sh
# (Dockerfile-Stage `skript-selbsttest`, TDS-QA-06, ADR-0022), von tools/vergleichslauf-d.sh und der Log-Muster der Laufskripte
# (tools/log-muster.sh, TDS-FA-09). Repo-eigen, eingebunden von repo.mk; das Target
# hat seine Zeile in harness/README.md. Die Stage erbt von `basis` und kopiert Skripte, Dockerfile, `tools/mk`, `harness/mk`, `Makefile`, `repo.mk` und die Workflows:
# eine Quelltext-Aenderung loest sie nicht aus, eine Aenderung an diesen Dateien schon; ein Lauf kostet Sekunden.
.PHONY: skript-selbsttest

skript-selbsttest: ## Abbruch von tools/e2e.sh, Fehlerfaelle von tools/manifest-pruefung.sh gegen synthetische Jars und Log-Muster der Laufskripte pruefen (Stage `skript-selbsttest`)
	docker build $(BUILDER_FLAGS) --target skript-selbsttest --progress=plain .

GATE_CHECKS += skript-selbsttest
GATE_KLASSE_werkzeug += skript-selbsttest
GATE_STAGE += skript-selbsttest=skript-selbsttest
