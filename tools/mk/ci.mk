# tools/mk/ci.mk — Pruefwerkzeuge fuer die CI-Datei und den Clean-Build (TDS-QA-06).
# Repo-eigen, eingebunden von repo.mk. Beide Targets sind Werkzeuge, keine Gates; ihre Zeilen
# stehen in harness/README.md.

# actionlint 1.7.7, per Digest festgelegt; ein neuer Digest ist eine Aenderung dieser Datei.
ACTIONLINT_IMAGE ?= rhysd/actionlint:1.7.7@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9
WORKFLOWS := $(wildcard .github/workflows/*.yml)

.PHONY: workflow-lint clean-build

# Jede Workflow-Datei geht ueber stdin in den Container, nichts wird gemountet.
workflow-lint: ## Workflow-Dateien unter .github/workflows/ mit actionlint pruefen (Docker, kein Gate)
	@test -n "$(WORKFLOWS)" || { echo "workflow-lint: keine Datei unter .github/workflows/"; exit 1; }
	@for f in $(WORKFLOWS); do \
	  echo "workflow-lint: $$f"; \
	  docker run --rm -i $(ACTIONLINT_IMAGE) -no-color - < "$$f" || exit 1; \
	done

# Die Test-Stage wird nach einem grünen Lauf gecacht: `make test` und `make gates` zeigen bei unveränderten Eingängen
# einen Cache-Treffer und führen die Tests nicht erneut aus. Eine Stabilitätsaussage über Tests mit Wanduhr oder Last
# (Fristen, Ticker) trägt deshalb nur ein Lauf dieses Targets oder `docker build --target test --no-cache-filter test .`.
# --no-cache verwirft den Layer-Cache der Stages der drei Aufrufe (Gradle-Stages, ohne den
# .NET-Restore); die Basis-Images bleiben, wie sie lokal liegen. Ein frischer Runner zieht sie ohnehin neu, der Layer-Cache fehlt dort von selbst.
clean-build: ## Build, Tests und detekt ohne Layer-Cache (Docker, kein Gate) · Frischbeleg für zeitabhängige Tests · seit slice-handshake-login
	docker build $(BUILDER_FLAGS) $(BUILDER_LOAD) --no-cache -t $(IMAGE):$(TAG) .
	docker build $(BUILDER_FLAGS) --no-cache --target test --progress=plain .
	docker build $(BUILDER_FLAGS) --no-cache --target detekt --progress=plain .
