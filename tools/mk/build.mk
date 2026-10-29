# tools/mk/build.mk — Build und statische Analyse in Docker (Dockerfile, TDS-QA-06).
# Repo-eigen, eingebunden von repo.mk. Jedes Target hat seine Zeile in harness/README.md.
IMAGE ?= tdswire
TAG   ?= latest

.PHONY: build detekt deps-lock

build: ## Jar bauen (Dockerfile-Stage `build`, ohne Tests)
	docker build -t $(IMAGE):$(TAG) .

detekt: ## Suppression-Verbot und statische Analyse samt Importregeln der Schichten (Dockerfile-Stage `detekt`)
	docker build --target detekt --progress=plain .

# Alle Gradle-Tasks, die das Dockerfile ausfuehrt, plus `dependencies` (das Dockerfile fuehrt es
# nicht aus; es steht hier, damit Lock und Pruefsummen auch die Konfigurationen erfassen, die es
# aufloest). Manche Konfigurationen loest Gradle erst waehrend der Ausfuehrung auf; Lock und
# Pruefsummen brauchen darum jede dieser Tasks.
DEPS_TASKS = dependencies help classes detekt detektMain detektTest detektSelbsttest detektSelbsttestAufrufe assemble

# Die Stage `lock` im Dockerfile schreibt die Dateien, `lock-export` gibt sie per --output heraus:
# nichts wird gemountet, auf dem Host entsteht kein build/ und kein .gradle/.
deps-lock: ## Nach einer Versionsaenderung in gradle/libs.versions.toml: gradle.lockfile und gradle/verification-metadata.xml neu schreiben
	docker build --target lock-export --build-arg DEPS_TASKS="$(DEPS_TASKS)" --output type=local,dest=. .

GATE_CHECKS += detekt build
