# tools/mk/laufzeittest.mk — Laufzeittest des Container-Artefakts (TDS-RB-01, Dockerfile-Stagen `runtime`
# und `laufzeit-client`). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# Werkzeug, kein Gate: kein Eintrag in GATE_CHECKS; die Begruendung steht in der Zeile in harness/README.md.
.PHONY: laufzeittest

laufzeittest: ## Container starten, Rohbytes-Login aus zweitem Container, Stop; Image-Inhalt pruefen (Werkzeug, kein Gate)
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/laufzeittest.sh
