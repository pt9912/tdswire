# tools/mk/kopplung-zahlen.mk - Kopplung der Zahlen des Integrationslaufs an allen Orten (Dockerfile-Stage
# `kopplung-zahlen`, TDS-FA-11, TDS-QA-06, ADR-0023). Repo-eigen, eingebunden von repo.mk; das Target hat seine
# Zeile in harness/README.md. Die Stage erbt von `basis` und kopiert nur die fuenf Dateien mit den Zahlen und die
# Skripte: eine Aenderung an anderen Dateien der Spezifikation oder des Quelltexts loest sie nicht aus.
.PHONY: kopplung-zahlen

kopplung-zahlen: ## Heap und Text von `gross` des Integrationslaufs in Gradle, Kotlin, .NET, Spezifikation und README gleich pruefen; Selbsttest mit Mutationen (Stage `kopplung-zahlen`)
	docker build $(BUILDER_FLAGS) --target kopplung-zahlen --progress=plain .

GATE_CHECKS += kopplung-zahlen
GATE_KLASSE_werkzeug += kopplung-zahlen
GATE_STAGE += kopplung-zahlen=kopplung-zahlen
