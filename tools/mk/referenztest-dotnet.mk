# tools/mk/referenztest-dotnet.mk — .NET-Referenztests in Docker (referenztest-dotnet/Dockerfile,
# ADR-0008, TDS-QA-01, TDS-QA-06). Repo-eigen, eingebunden von repo.mk; jedes Target hat seine Zeile
# in harness/README.md.
#
# Eigener Build-Kontext: das Verzeichnis referenztest-dotnet/. Gemountet wird nichts; die Lock-Datei
# kommt per --output heraus, wie bei `make deps-lock`.
.PHONY: referenztest-dotnet referenztest-dotnet-lock referenztest-dotnet-audit

REFERENZTEST_DOTNET_DIR = referenztest-dotnet

referenztest-dotnet: ## .NET-Referenztests (Smoke ohne Server) bauen und ausfuehren (referenztest-dotnet/Dockerfile, Stage `test`)
	docker build $(BUILDER_FLAGS) --target test --progress=plain $(REFERENZTEST_DOTNET_DIR)

# Nach einer Versionsaenderung in der csproj. Das erste Lock vertraut nuget.org beim Schreiben
# (CO-002); die Signaturpruefung der Pakete ist aus.
referenztest-dotnet-lock: ## Nach einer Versionsaenderung in der csproj: referenztest-dotnet/packages.lock.json neu schreiben (braucht Netz)
	docker build $(BUILDER_FLAGS) --target lock-export --progress=plain --output type=local,dest=$(REFERENZTEST_DOTNET_DIR) $(REFERENZTEST_DOTNET_DIR)

# Die Stage `audit` fragt die Schwachstellen-Datenbank der Quelle am Tag des Laufs ab; das Ergebnis
# haengt am Abfragetag. --no-cache-filter erzwingt den Lauf der Stage, sonst zeigt ein gecachter
# Layer den Stand eines frueheren Tages (CO-001).
referenztest-dotnet-audit: ## Bekannte Schwachstellen der Pakete melden; haengt am Abfragetag, nicht reproduzierbar, KEIN Gate
	docker build $(BUILDER_FLAGS) --target audit --no-cache-filter audit --progress=plain $(REFERENZTEST_DOTNET_DIR)

GATE_CHECKS += referenztest-dotnet
