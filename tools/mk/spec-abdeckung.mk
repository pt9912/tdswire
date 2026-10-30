# tools/mk/spec-abdeckung.mk — Abdeckungsmatrix der Festlegungen der Spezifikation (TDS-QA-05,
# TDS-QA-06). Repo-eigen, eingebunden von repo.mk; beide Targets haben ihre Zeile in harness/README.md.
#
# KEIN GATE: ob Festlegungen ohne Nachweis `make gates` brechen, entscheidet der Nutzer, nicht
# dieses Fragment. Der Erzeuger berichtet nur. SPEC_ABDECKUNG_STRENG=1 färbt den Lauf rot, sobald die
# Matrix eine Festlegung ohne Nachweis zeigt; die Matrix wird vorher geschrieben, und eine
# Gate-Festlegung ohne Test zählt nicht als Waise.
# Die Dockerfile-Stage führt die Tests aus und gibt Matrix und Waisenzahl per --output nach
# SPEC_ABDECKUNG_ABLAGE (nicht versioniert, Modus 700 und damit fuer das Doku-Gate unlesbar: das
# Target raeumt sie nach dem Lesen weg); gemountet wird nichts.
.PHONY: spec-abdeckung spec-abdeckung-selbsttest

SPEC_ABDECKUNG_ZIEL ?= docs/plan/spec-abdeckung.md
SPEC_ABDECKUNG_STRENG ?= 0
SPEC_ABDECKUNG_ABLAGE = .harness/state/spec-abdeckung

spec-abdeckung: ## die Abdeckungsmatrix der SPEC-Festlegungen aus @spec-Tags und Testberichten erzeugen (SPEC_ABDECKUNG_STRENG=1: Waisen sind rot) — KEIN Gate
	rm -rf $(SPEC_ABDECKUNG_ABLAGE)
	docker build --target spec-abdeckung-export --progress=plain --output type=local,dest=$(SPEC_ABDECKUNG_ABLAGE) .
	@if cmp -s $(SPEC_ABDECKUNG_ABLAGE)/spec-abdeckung.md $(SPEC_ABDECKUNG_ZIEL); then \
	  echo "spec-abdeckung: $(SPEC_ABDECKUNG_ZIEL) unveraendert"; \
	else \
	  cp $(SPEC_ABDECKUNG_ABLAGE)/spec-abdeckung.md $(SPEC_ABDECKUNG_ZIEL); \
	  echo "spec-abdeckung: $(SPEC_ABDECKUNG_ZIEL) geschrieben"; \
	fi
	@waisen=$$(cat $(SPEC_ABDECKUNG_ABLAGE)/waisen); \
	rm -rf $(SPEC_ABDECKUNG_ABLAGE); \
	echo "spec-abdeckung: $$waisen Festlegung(en) ohne Nachweis"; \
	if [ "$(SPEC_ABDECKUNG_STRENG)" = 1 ] && [ "$$waisen" -gt 0 ]; then \
	  echo "spec-abdeckung: SPEC_ABDECKUNG_STRENG=1 und Festlegungen ohne Nachweis" >&2; exit 1; \
	fi

spec-abdeckung-selbsttest: ## den Erzeuger der Abdeckungsmatrix gegen einen erzeugten Mini-Bestand pruefen (Stage `spec-abdeckung-selbsttest`) — KEIN Gate
	docker build --target spec-abdeckung-selbsttest --progress=plain .
