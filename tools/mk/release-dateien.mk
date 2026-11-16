# tools/mk/release-dateien.mk — Release-Dateien in Docker bauen und herausgeben (TDS-RB-05, TDS-QA-06, ADR-0050; Dockerfile-Stages
# `release-dateien` und `release-dateien-export`). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Kein Gate und nicht Teil von `make e2e`: `release-dateien` steht in keiner Liste GATE_CHECKS, GATE_KLASSE_* oder GATE_STAGE
# (tools/release-selbsttest.sh liest die Make-Dateien darauf). Ohne Wirkung nach aussen: kein Token, kein gh, kein Netz ausser dem Bau.
# Der Export folgt `deps-lock`: --output type=local, nichts gemountet, auf dem Host entsteht kein build/ und kein .gradle/.
# RELEASE_AUSGABE: Verzeichnis der Ausgabe (Voreinstellung release-dateien/, ignoriert von git und docker); es darf nicht oder nur leer bestehen.
.PHONY: release-dateien

RELEASE_AUSGABE ?= release-dateien

release-dateien: ## ZIP tdswire-<Version>.zip (Jar, lib/, LICENSE, Lizenzdateien), einzelnes Jar und SHA256SUMS bauen, am entpackten ZIP pruefen und nach $(RELEASE_AUSGABE)/ exportieren; kein Token, nichts nach aussen (Dockerfile-Stage `release-dateien`, kein Gate)
	@if [ -e "$(RELEASE_AUSGABE)" ] && [ -n "$$(ls -A "$(RELEASE_AUSGABE)" 2>/dev/null)" ]; then echo "release-dateien: $(RELEASE_AUSGABE) besteht und ist nicht leer" >&2; exit 1; fi
	docker build $(BUILDER_FLAGS) --target release-dateien-export --progress=plain --output type=local,dest=$(RELEASE_AUSGABE) .
	@ls -l $(RELEASE_AUSGABE)
