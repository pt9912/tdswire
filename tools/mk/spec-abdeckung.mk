# tools/mk/spec-abdeckung.mk — Abdeckungsmatrix der Festlegungen der Spezifikation (TDS-QA-05,
# TDS-QA-06). Repo-eigen, eingebunden von repo.mk; beide Targets haben ihre Zeile in harness/README.md.
#
# KEIN GATE: der Erzeuger berichtet nur (Entscheidung des Nutzers vom 2026-10-30: Option D). Nichts
# aus diesem Fragment steht in GATE_CHECKS. SPEC_ABDECKUNG_STRENG=1 ist ein optionaler Schalter und
# faerbt den Lauf rot, sobald die Matrix eine Festlegung ohne Nachweis zeigt: der Erzeuger endet mit
# Exit 1, `make` meldet dann Exit 2. Die Matrix wird vorher geschrieben, und eine Gate-Festlegung ohne
# Test zaehlt nicht als Waise. Eine leere oder fehlende Waisenzahl bricht den Lauf auch ohne STRENG.
# Zwei Builds (Setzung: der Bericht der C#-Tests wird nie aus dem Layer-Cache gelesen): erst die Stage
# `test-bericht-export` von referenztest-dotnet/Dockerfile mit `--no-cache-filter test-bericht`, die die C#-Tests der Stage
# `test` bei jedem Aufruf neu ausfuehrt (der Restore bleibt im Cache) und den TRX-Bericht auch bei roten Tests herausgibt;
# dann die Wurzel-Stage `spec-abdeckung-export` mit diesem Bericht als benanntem Build-Kontext `dotnet-bericht`. Der Gradle-Teil
# haengt am Layer-Cache seiner Quellen (`BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`): bei unveraenderten Quellen kommt der
# Testlauf der Gradle-Stage aus dem Cache, auch wenn er einen roten Test (Flake) enthielt, und `make spec-abdeckung` meldet dann
# „unveraendert“, ohne ihn zu pruefen. Frischlauf der Gradle-Stage ohne Cache:
# `docker build --target spec-abdeckung-export --no-cache-filter spec-abdeckung --build-context dotnet-bericht=<Ablage> ...`
# (die Ablage mit dem TRX-Bericht entsteht im ersten Build des Rezepts unten). Die Stage fuehrt die Tests aus
# und gibt Matrix und Waisenzahl per --output in ein
# temporaeres Verzeichnis ausserhalb des Repos (`mktemp -d`, Modus 700); das Doku-Gate liest es nie, und
# der trap raeumt es bei Erfolg, Fehler und Abbruch weg. Gemountet wird nichts. Die zwei Tests von `LAUF=mitschnitt`
# laufen nur in `make vergleichslauf-d` und stehen in der Matrix als „nicht gelaufen“.
.PHONY: spec-abdeckung spec-abdeckung-selbsttest

SPEC_ABDECKUNG_ZIEL ?= docs/plan/spec-abdeckung.md
SPEC_ABDECKUNG_STRENG ?= 0

spec-abdeckung: ## die Abdeckungsmatrix der SPEC-Festlegungen aus @spec-Tags und Testberichten erzeugen (SPEC_ABDECKUNG_STRENG=1: Waisen sind rot, Exit 2) — KEIN Gate
	@set -eu; \
	ablage=$$(mktemp -d); \
	trap 'rm -rf "$$ablage"' EXIT INT TERM; \
	docker build $(BUILDER_FLAGS) --target test-bericht-export --no-cache-filter test-bericht --progress=plain --output type=local,dest="$$ablage/dotnet" referenztest-dotnet; \
	[ -s "$$ablage/dotnet/bericht.trx" ] || { echo "spec-abdeckung: der Lauf der C#-Tests liefert keinen Bericht" >&2; exit 1; }; \
	docker build $(BUILDER_FLAGS) --target spec-abdeckung-export --build-context dotnet-bericht="$$ablage/dotnet" --progress=plain --output type=local,dest="$$ablage/matrix" .; \
	[ -s "$$ablage/matrix/spec-abdeckung.md" ] || { echo "spec-abdeckung: der Export liefert keine Matrix" >&2; exit 1; }; \
	[ -s "$$ablage/matrix/waisen" ] || { echo "spec-abdeckung: der Export liefert keine Waisenzahl" >&2; exit 1; }; \
	waisen=$$(cat "$$ablage/matrix/waisen"); \
	case "$$waisen" in ''|*[!0-9]*) echo "spec-abdeckung: ungueltige Waisenzahl '$$waisen'" >&2; exit 1;; esac; \
	if cmp -s "$$ablage/matrix/spec-abdeckung.md" "$(SPEC_ABDECKUNG_ZIEL)"; then \
	  echo "spec-abdeckung: $(SPEC_ABDECKUNG_ZIEL) unveraendert"; \
	else \
	  cp "$$ablage/matrix/spec-abdeckung.md" "$(SPEC_ABDECKUNG_ZIEL)"; \
	  echo "spec-abdeckung: $(SPEC_ABDECKUNG_ZIEL) geschrieben"; \
	fi; \
	echo "spec-abdeckung: $$waisen Festlegung(en) ohne Nachweis"; \
	if [ "$(SPEC_ABDECKUNG_STRENG)" = 1 ] && [ "$$waisen" -gt 0 ]; then \
	  echo "spec-abdeckung: SPEC_ABDECKUNG_STRENG=1 und Festlegungen ohne Nachweis" >&2; exit 1; \
	fi

spec-abdeckung-selbsttest: ## den Erzeuger der Abdeckungsmatrix gegen einen erzeugten Mini-Bestand pruefen (Stage `spec-abdeckung-selbsttest`) — KEIN Gate
	docker build $(BUILDER_FLAGS) --target spec-abdeckung-selbsttest --progress=plain .
