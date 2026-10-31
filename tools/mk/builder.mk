# tools/mk/builder.mk — optionaler buildx-Builder mit CPU-/RAM-Limits fuer lokale Docker-Builds
# (TDS-QA-06). Repo-eigen, eingebunden von repo.mk. Beide Targets sind Werkzeuge, keine Gates; ihre
# Zeilen stehen in harness/README.md.
#
# BUILDER leer (Voreinstellung): alle docker-build-Aufrufe laufen wie ohne diese Datei, ueber den
# aktiven Builder des Hosts; die CI auf GitHub setzt BUILDER nicht.
# BUILDER=<name>: build.mk, test.mk, smoke.mk, ci.mk, spec-abdeckung.mk, detekt-selbsttest.mk und
# referenztest-dotnet.mk rufen `docker build --builder <name>` (BUILDER_FLAGS). Wo das Ergebnis das
# Image $(IMAGE):$(TAG) ist, kommt --load dazu (BUILDER_LOAD): der docker-container-Treiber legt
# das Image sonst nicht in den lokalen Image-Speicher. Targets mit --output type=local lesen das
# Ergebnis per --output, nichts wird gemountet.
#
# Grenze: der Builder hat einen eigenen BuildKit-Cache und zieht Basis-Images selbst aus der
# Registry. Der erste Lauf ueber einen frischen Builder ist ein Kaltstart (kein Layer-Cache des
# Host-Builders, Basis-Images per Digest neu geladen); `make builder-rm` verwirft den Cache.
#
# Belegt am Host mit buildx v0.38.1 (`docker inspect` des Builder-Containers): driver-opt
# cpuset-cpus, memory, cpu-quota, cpu-period, cpu-shares und cpuset-mems werden angenommen und
# am Container gesetzt; memory-swap allein und unbekannte Namen lehnt buildx ab. Genutzt werden
# cpuset-cpus und memory.
# Die Limits gelten fuer den Builder-Container: RUN-Schritte laufen in BuildKit innerhalb dieses
# Containers und teilen sich dessen cgroup.
BUILDER ?=

# Setzung (Voreinstellung, nicht gemessen): 8 Kerne und 24 GiB RAM fuer den Builder.
BUILDER_CPUS   ?= 8
BUILDER_MEMORY ?= 24g
# Setzung (Voreinstellung, nicht gemessen): cpuset-cpus pinnt auf die Kerne 0 bis N-1, N = BUILDER_CPUS.
BUILDER_CPUSET ?= 0-$(shell expr $(BUILDER_CPUS) - 1)

BUILDER_FLAGS = $(if $(BUILDER),--builder $(BUILDER))
BUILDER_LOAD  = $(if $(BUILDER),--load)

.PHONY: builder-create builder-rm

# Idempotent: ein vorhandener Builder gleichen Namens bleibt unveraendert; ein Limit-Wechsel
# braucht erst builder-rm. Die driver-opt-Namen cpuset-cpus und memory sind in harness/README.md
# belegt.
builder-create: ## buildx-Builder BUILDER mit CPU-/RAM-Limits anlegen (BUILDER_CPUS, BUILDER_MEMORY; Werkzeug, kein Gate)
	@test -n "$(BUILDER)" || { echo "builder-create: BUILDER=<name> setzen"; exit 1; }
	@if docker buildx inspect "$(BUILDER)" >/dev/null 2>&1; then \
	  echo "builder-create: $(BUILDER) existiert bereits"; \
	else \
	  docker buildx create --name "$(BUILDER)" --driver docker-container \
	    --driver-opt cpuset-cpus=$(BUILDER_CPUSET) --driver-opt memory=$(BUILDER_MEMORY) && \
	  docker buildx inspect "$(BUILDER)" --bootstrap >/dev/null; \
	fi

builder-rm: ## buildx-Builder BUILDER samt eigenem Cache entfernen (Werkzeug, kein Gate)
	@test -n "$(BUILDER)" || { echo "builder-rm: BUILDER=<name> setzen"; exit 1; }
	docker buildx rm --force "$(BUILDER)"
