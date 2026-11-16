# tools/mk/release-dateien.mk — Release-Dateien in Docker bauen und herausgeben (TDS-RB-05, TDS-QA-06, ADR-0050; Dockerfile-Stages
# `release-dateien` und `release-dateien-export`). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Kein Gate und nicht Teil von `make e2e`: `release-dateien` steht in keiner Liste GATE_CHECKS, GATE_KLASSE_* oder GATE_STAGE
# (tools/release-selbsttest.sh liest die Make-Dateien darauf). Ohne Wirkung nach aussen: kein Token, kein gh, kein Netz ausser dem Bau.
# Der Export folgt `deps-lock`: --output type=local, nichts gemountet, auf dem Host entsteht kein build/ und kein .gradle/.
# Die Version wird vor `docker build` geprueft (tools/release-dateien.sh --nur-version, Host-sh): -SNAPSHOT bricht, ausser mit
# VORSCHAU=1; dann heisst das ZIP tdswire-<Version>-vorschau.zip. Nur der Dispatch-Lauf der CI nutzt VORSCHAU=1, ein Release nie.
# RELEASE_AUSGABE: Verzeichnis der Ausgabe (Voreinstellung release-dateien/, ignoriert von git und docker); es darf nicht oder nur leer bestehen.
.PHONY: release-dateien

RELEASE_AUSGABE ?= release-dateien
VORSCHAU ?=

release-dateien: ## ZIP tdswire-<Version>.zip (Jar, lib/, LICENSE, Lizenzdateien), einzelnes Jar und SHA256SUMS bauen, am entpackten ZIP pruefen, nach $(RELEASE_AUSGABE)/ exportieren; VORSCHAU=1 erlaubt -SNAPSHOT (ZIP ...-vorschau.zip); kein Token, nichts nach aussen (Stage `release-dateien`, kein Gate)
	@sh tools/release-dateien.sh $(if $(VORSCHAU),--vorschau) --nur-version build.gradle.kts
	@if [ -e "$(RELEASE_AUSGABE)" ] && [ -n "$$(ls -A "$(RELEASE_AUSGABE)" 2>/dev/null)" ]; then echo "release-dateien: $(RELEASE_AUSGABE) besteht und ist nicht leer" >&2; exit 1; fi
	docker build $(BUILDER_FLAGS) --target release-dateien-export --progress=plain $(if $(VORSCHAU),--build-arg RELEASE_VORSCHAU=--vorschau) --output type=local,dest=$(RELEASE_AUSGABE) .
	@ls -l $(RELEASE_AUSGABE)
