# tools/mk/detekt-selbsttest.mk — Selbsttest der Importregeln in config/detekt/detekt.yml und des
# Suppression-Verbots (Dockerfile-Stage `detekt-selbsttest`, tools/detekt-selbsttest.sh).
# Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
#
# Klasse `werkzeug` (ADR-0024 Entscheidung 3): in `make gates` laeuft das Target im Gruppenschritt
# gruppe-werkzeug und wird uebersprungen, wenn der Stempel der Klasse zum Eingang passt (tools/mk/stempel.mk).
# Aufgerufen als `make detekt-selbsttest` laeuft es immer; der Layer-Cache der Stage uebergeht es dann, wenn nichts
# von dem geaendert ist, was die Stage kopiert.
# Aufnahmebedingung (ADR-0024 Entscheidung 6): digest-gepinntes Image, feste Testdaten unter config/detekt/selbsttest*,
# keine Uhr; ihre Eingaenge stehen in tools/stempel/werkzeug.eingaenge (Dockerfile, Gradle-Dateien, Lockfiles, config).
.PHONY: detekt-selbsttest

detekt-selbsttest: ## Importregeln der Schichten und Suppression-Verbot gegen Testdaten pruefen (Stage `detekt-selbsttest`); in make gates im Gruppenschritt werkzeug
	docker build $(BUILDER_FLAGS) --target detekt-selbsttest --progress=plain .

GATE_CHECKS += detekt-selbsttest
GATE_KLASSE_werkzeug += detekt-selbsttest
GATE_STAGE += detekt-selbsttest=detekt-selbsttest
