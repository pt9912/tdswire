# tools/mk/spec-abdeckung.mk — Abdeckungsmatrix der Festlegungen der Spezifikation (TDS-QA-05,
# TDS-QA-06). Repo-eigen, eingebunden von repo.mk; beide Targets haben ihre Zeile in harness/README.md.
#
# KEIN GATE: der Erzeuger berichtet nur (Entscheidung des Nutzers vom 2026-10-30: Option D). Nichts
# aus diesem Fragment steht in GATE_CHECKS. SPEC_ABDECKUNG_STRENG=1 ist ein optionaler Schalter und
# faerbt den Lauf rot, sobald die Matrix eine Festlegung ohne Nachweis zeigt: der Erzeuger endet mit
# Exit 1, `make` meldet dann Exit 2. Die Matrix wird vorher geschrieben, und eine Gate-Festlegung ohne
# Test zaehlt nicht als Waise. Eine leere oder fehlende Waisenzahl bricht den Lauf auch ohne STRENG.
# Die Dockerfile-Stage fuehrt die Tests aus und gibt Matrix und Waisenzahl per --output in ein
# temporaeres Verzeichnis ausserhalb des Repos (`mktemp -d`, Modus 700); das Doku-Gate liest es nie, und
# der trap raeumt es bei Erfolg, Fehler und Abbruch weg. Gemountet wird nichts.
.PHONY: spec-abdeckung spec-abdeckung-selbsttest

SPEC_ABDECKUNG_ZIEL ?= docs/plan/spec-abdeckung.md
SPEC_ABDECKUNG_STRENG ?= 0

spec-abdeckung: ## die Abdeckungsmatrix der SPEC-Festlegungen aus @spec-Tags und Testberichten erzeugen (SPEC_ABDECKUNG_STRENG=1: Waisen sind rot, Exit 2) — KEIN Gate
	@set -eu; \
	ablage=$$(mktemp -d); \
	trap 'rm -rf "$$ablage"' EXIT INT TERM; \
	docker build $(BUILDER_FLAGS) --target spec-abdeckung-export --progress=plain --output type=local,dest="$$ablage" .; \
	[ -s "$$ablage/spec-abdeckung.md" ] || { echo "spec-abdeckung: der Export liefert keine Matrix" >&2; exit 1; }; \
	[ -s "$$ablage/waisen" ] || { echo "spec-abdeckung: der Export liefert keine Waisenzahl" >&2; exit 1; }; \
	waisen=$$(cat "$$ablage/waisen"); \
	case "$$waisen" in ''|*[!0-9]*) echo "spec-abdeckung: ungueltige Waisenzahl '$$waisen'" >&2; exit 1;; esac; \
	if cmp -s "$$ablage/spec-abdeckung.md" "$(SPEC_ABDECKUNG_ZIEL)"; then \
	  echo "spec-abdeckung: $(SPEC_ABDECKUNG_ZIEL) unveraendert"; \
	else \
	  cp "$$ablage/spec-abdeckung.md" "$(SPEC_ABDECKUNG_ZIEL)"; \
	  echo "spec-abdeckung: $(SPEC_ABDECKUNG_ZIEL) geschrieben"; \
	fi; \
	echo "spec-abdeckung: $$waisen Festlegung(en) ohne Nachweis"; \
	if [ "$(SPEC_ABDECKUNG_STRENG)" = 1 ] && [ "$$waisen" -gt 0 ]; then \
	  echo "spec-abdeckung: SPEC_ABDECKUNG_STRENG=1 und Festlegungen ohne Nachweis" >&2; exit 1; \
	fi

spec-abdeckung-selbsttest: ## den Erzeuger der Abdeckungsmatrix gegen einen erzeugten Mini-Bestand pruefen (Stage `spec-abdeckung-selbsttest`) — KEIN Gate
	docker build $(BUILDER_FLAGS) --target spec-abdeckung-selbsttest --progress=plain .
