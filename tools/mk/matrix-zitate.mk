# tools/mk/matrix-zitate.mk — Zitat-Pruefung der Kompatibilitaetsmatrix (TDS-QA-03). Repo-eigen, eingebunden von
# repo.mk; das Target hat seine Zeile in harness/README.md. KEIN GATE: nichts hieraus steht in GATE_CHECKS.
# Der Lauf baut die Stage `test` ohne Cache-Treffer (--no-cache-filter test), haelt den Bericht in einer Datei
# ausserhalb des Repos (mktemp, trap raeumt) und liest mit tools/matrix-zitate.sh jedes `Klasse > Testfall` der
# Matrix gegen die PASSED-Zeilen. MATRIX_ZITATE_BERICHT=<Datei> nimmt einen vorhandenen Bericht statt des Builds.
.PHONY: matrix-zitate

MATRIX_ZITATE_DATEI ?= docs/plan/kompatibilitaetsmatrix.md
MATRIX_ZITATE_BERICHT ?=

matrix-zitate: ## jedes Zitat `Klasse > Testfall` der Kompatibilitaetsmatrix gegen frischen Testbericht pruefen — KEIN Gate
	@set -eu; \
	tmp=$$(mktemp -d); \
	trap 'rm -rf "$$tmp"' EXIT INT TERM; \
	bericht="$(MATRIX_ZITATE_BERICHT)"; \
	if [ -z "$$bericht" ]; then \
	  bericht="$$tmp/test.log"; \
	  docker build $(BUILDER_FLAGS) --target test --no-cache-filter test --progress=plain . > "$$bericht" 2>&1 \
	    || { tail -n 40 "$$bericht" >&2; echo "matrix-zitate: Stage test rot" >&2; exit 1; }; \
	fi; \
	sh tools/matrix-zitate.sh "$(MATRIX_ZITATE_DATEI)" "$$bericht"
