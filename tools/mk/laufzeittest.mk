# tools/mk/laufzeittest.mk — Laufzeittest des Container-Artefakts (TDS-RB-01, Dockerfile-Stagen `runtime`
# und `laufzeit-client`). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Gate: Setzung (Massstab nicht gemessen vorgegeben): ein Pruefschritt gehoert in `make gates`, wenn sein
# Warmlauf (Images im Docker-Cache) unter der Laufzeit der Stage `test` ohne Cache liegt; der Kaltlauf wird
# nicht verlangt. Gemessen am 2026-10-31, je ein Lauf: Stage `test` ohne Cache 89 bis 102 s (Gradle-Teil 93 s);
# Warmlauf dieses Targets 24 bis 31 s, `make gates` warm 26 s; Kaltlauf dieses Targets 88 bis 171 s, der
# hoehere Wert liegt ueber dem der Stage `test`; CI-Lauf mit dem Gate 8 min 58 s gegen rund 7 min 43 s davor, rund 75 s mehr.
# Ohne Gate liefe der Weg Manifest, Class-Path, Stage `runtime` und Entrypoint in keinem Gate, weil
# `make smoke` die Klasse `MainKt` startet, nicht das Jar.
.PHONY: laufzeittest

laufzeittest: ## Container starten, Rohbytes-Login aus zweitem Container, Stop; Image-Inhalt pruefen (Dockerfile-Stagen `runtime`, `laufzeit-client`)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/laufzeittest.sh

GATE_CHECKS += laufzeittest
