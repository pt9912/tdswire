# tools/mk/publish.mk — Bibliothek als Maven-Paket vorbereiten und veroeffentlichen (TDS-RB-05, TDS-QA-06, ADR-0047;
# Dockerfile-Stage `publish`). Repo-eigen, eingebunden von repo.mk; jedes Target hat seine Zeile in harness/README.md.
# Kein Gate und nicht Teil von `make e2e`: `make publish` schreibt nach aussen und ist nicht wiederholbar (ADR-0047
# Entscheidung 6). Beide Targets sind absichtlich in keiner Liste GATE_CHECKS, GATE_KLASSE_* oder GATE_STAGE;
# tools/publish-selbsttest.sh liest die Make-Dateien darauf.
# Das Token kommt nur aus der Umgebung des Aufrufs (GITHUB_ACTOR, GITHUB_TOKEN) und steht in keiner Rezeptzeile.
.PHONY: publish-vorschau publish

publish-vorschau: ## Jar und POM im Container nach ~/.m2 des Containers bauen (publishToMavenLocal) und die POM ausgeben; kein Token, nichts nach aussen (Dockerfile-Stage `publish`, kein Gate)
	docker build $(BUILDER_FLAGS) --target publish -t tdswire-publish-vorschau:lokal --progress=plain .
	docker run --rm tdswire-publish-vorschau:lokal sh -c 'gradle --no-daemon --offline -q publishToMavenLocal && ls ~/.m2/repository/io/github/pt9912/tdswire/*/ && cat ~/.m2/repository/io/github/pt9912/tdswire/*/*.pom'

publish: ## Veroeffentlichen in GitHub Packages (GITHUB_ACTOR und GITHUB_TOKEN aus der Umgebung); bricht vor Docker bei -SNAPSHOT, fehlendem Tag auf HEAD, unsauberem Baum, leerem Token; kein Gate
	BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/publish.sh
