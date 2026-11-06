# tools/mk/treiber-handles.mk - Beleg am Treiber zu den Prepared-Handles (TDS-FA-04, TDS-QA-02, ADR-0036; Skript
# tools/treiber-handles.sh). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
# KEIN Gate (ADR-0022): der Lauf braucht Docker, baut das Image des Testservers und zieht, falls es lokal fehlt, das
# Image von d-migrate per Digest.
.PHONY: messung-treiber-handles

messung-treiber-handles: ## Beleg am Treiber: mssql-jdbc 13.4.0 gegen den gebauten Testserver (Handle je Anweisungsobjekt, Freigabe als Batch, keine Abweisung an der Grenze), kein Gate
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/treiber-handles.sh
