# tools/mk/build.mk — Build und statische Analyse in Docker (Dockerfile, TDS-QA-06).
# Repo-eigen, eingebunden von repo.mk. Jedes Target hat seine Zeile in harness/README.md.
IMAGE ?= tdswire
TAG   ?= latest

.PHONY: build detekt deps-lock

build: ## Jar bauen (Dockerfile-Stage `build`, ohne Tests)
	docker build -t $(IMAGE):$(TAG) .

detekt: ## Suppression-Verbot und statische Analyse samt Importregeln der Schichten (Dockerfile-Stage `detekt`)
	docker build --target detekt --progress=plain .

# Alle Gradle-Tasks, die das Dockerfile ausfuehrt. Manche Konfigurationen loest Gradle erst
# waehrend der Ausfuehrung auf; Lock und Pruefsummen brauchen darum jede dieser Tasks.
DEPS_TASKS = dependencies help classes detekt detektSelbsttest assemble

# Das Gradle-Image des Locks ist das der Stage `deps` im Dockerfile: ein Digest, eine Stelle.
GRADLE_IMAGE = $(shell sed -n 's/^FROM \(gradle[^ ]*\) AS deps$$/\1/p' Dockerfile)

deps-lock: ## Nach einer Versionsaenderung in gradle/libs.versions.toml: gradle.lockfile und gradle/verification-metadata.xml neu schreiben
	rm -f gradle/verification-metadata.xml
	docker run --rm -v "$(CURDIR)":/home/gradle/project -w /home/gradle/project $(GRADLE_IMAGE) \
		gradle --no-daemon -q --write-locks --write-verification-metadata sha256 $(DEPS_TASKS) >/dev/null

GATE_CHECKS += detekt build
