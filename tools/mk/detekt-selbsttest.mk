# tools/mk/detekt-selbsttest.mk — Selbsttest der Importregeln in config/detekt/detekt.yml und des
# Suppression-Verbots (Dockerfile-Stage `detekt-selbsttest`, tools/detekt-selbsttest.sh).
# Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
#
# Der Selbsttest laeuft nur, wenn sich ein Eingang geaendert hat. Ein Stempel in build/ haelt
# fest, dass er fuer diese Eingaenge gruen war; sonst bleibt auch der docker-Aufruf aus. Die
# Verzeichnisse stehen mit in der Liste, damit eine geloeschte Testdatei ihn ausloest. Ein
# frischer Klon hat keinen Stempel und fuehrt ihn einmal aus.
.PHONY: detekt-selbsttest

SELBSTTEST_STEMPEL = build/detekt-selbsttest.ok
SELBSTTEST_EINGAENGE = Dockerfile settings.gradle.kts build.gradle.kts gradle.properties \
	gradle/libs.versions.toml gradle.lockfile settings-gradle.lockfile \
	gradle/verification-metadata.xml config/detekt/detekt.yml \
	tools/detekt-selbsttest.sh tools/suppression-check.sh $(shell find config/detekt/selbsttest)

detekt-selbsttest: $(SELBSTTEST_STEMPEL) ## Importregeln der Schichten und Suppression-Verbot gegen Testdaten pruefen; laeuft nur bei geaenderten Eingaengen

# Der Stempel entsteht vor dem Lauf: eine Aenderung waehrend des Laufs ist dann juenger als er und
# loest den naechsten aus. Scheitert der Lauf, verschwindet er.
$(SELBSTTEST_STEMPEL): $(SELBSTTEST_EINGAENGE)
	@mkdir -p $(@D)
	@touch $@
	docker build --target detekt-selbsttest --progress=plain . || { rm -f $@; exit 1; }

GATE_CHECKS += detekt-selbsttest
