# tools/mk/flaeche.mk - Sensor der oeffentlichen Flaeche (Dockerfile-Stage `flaeche`, TDS-QA-05, TDS-RB-04, SPEC-059).
# Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md. Die Stage erbt von `basis`, braucht
# kein Gradle und kopiert die Quellen von Hauptmodul, Einbettungstest und Testserver, die Spezifikation und die Skripte
# tools/flaeche-pruefung.sh und tools/flaeche-selbsttest.sh. Klasse `immer`: ihr Eingang (Spezifikation und Quellen) steht nicht
# vollstaendig in der Eingangsliste einer Klasse mit Stempel; der Lauf kostet bei unveraendertem Eingang nur den Layer-Cache.
.PHONY: flaeche

flaeche: ## Typnamen der Liste in SPEC-059 gegen die oeffentlichen Typdeklarationen und ihre Verwendung pruefen; Selbsttest mit Mutationen (Stage `flaeche`)
	docker build $(BUILDER_FLAGS) --target flaeche --progress=plain .

GATE_CHECKS += flaeche
GATE_KLASSE_immer += flaeche
