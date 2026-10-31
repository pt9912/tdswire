# tools/mk/laufzeittest.mk — Laufzeittest des Container-Artefakts (TDS-RB-01, Dockerfile-Stagen `runtime`
# und `laufzeit-client`). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Gate: Setzung (Massstab nicht gemessen vorgegeben): ein Pruefschritt gehoert in `make gates`, wenn sein
# Warmlauf unter der Laufzeit der Stage `test` liegt (gemessen 93 s, Gradle-Teil); der Warmlauf dieses
# Targets maß 31 s, der Kaltlauf 88 bis 171 s. Ohne Gate liefe der Weg Manifest, Class-Path, Stage `runtime`
# und Entrypoint in keinem Gate, weil `make smoke` die Klasse `MainKt` startet, nicht das Jar.
.PHONY: laufzeittest

laufzeittest: ## Container starten, Rohbytes-Login aus zweitem Container, Stop; Image-Inhalt pruefen (Dockerfile-Stagen `runtime`, `laufzeit-client`)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/laufzeittest.sh

GATE_CHECKS += laufzeittest
