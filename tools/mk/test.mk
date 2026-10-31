# tools/mk/test.mk — Unit-Tests in Docker (Dockerfile-Stage `test`, SPEC-024, TDS-QA-06).
# Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in harness/README.md.
.PHONY: test

test: ## Unit-Tests ausfuehren (Dockerfile-Stage `test`, kotlin.test)
	docker build $(BUILDER_FLAGS) --target test --progress=plain .

GATE_CHECKS += test
