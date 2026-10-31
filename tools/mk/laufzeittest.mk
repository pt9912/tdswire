# tools/mk/laufzeittest.mk — Laufzeittest des Container-Artefakts (TDS-RB-01, Dockerfile-Stagen `runtime`
# und `laufzeit-client`). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Kein Gate: der Lauf gehoert zum E2E-Durchgang `make e2e` (Stufe 2, ADR-0022) und steht nicht in `GATE_CHECKS`.
# Gemessen am 2026-10-31, je ein Lauf: Warmlauf dieses Targets 24 bis 31 s, Kaltlauf 88 bis 171 s. Den Weg Manifest,
# Class-Path und Jar-Name prueft im Gate allein tools/manifest-pruefung.sh im Bau; Stage `runtime` und Entrypoint
# laufen nur hier, weil `make smoke` die Klasse `MainKt` startet, nicht das Jar.
.PHONY: laufzeittest

laufzeittest: ## Container starten, Rohbytes-Login aus zweitem Container, Stop; Image-Inhalt pruefen (Dockerfile-Stagen `runtime`, `laufzeit-client`)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/laufzeittest.sh
