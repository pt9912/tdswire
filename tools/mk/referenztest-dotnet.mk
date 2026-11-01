# tools/mk/referenztest-dotnet.mk — .NET-Referenztests in Docker (referenztest-dotnet/Dockerfile,
# ADR-0008, TDS-QA-01, TDS-QA-06). Repo-eigen, eingebunden von repo.mk; jedes Target hat seine Zeile
# in harness/README.md.
#
# Eigener Build-Kontext: das Verzeichnis referenztest-dotnet/. Gemountet wird nichts; die Lock-Datei
# kommt per --output heraus, wie bei `make deps-lock`.
#
# Der Lauf erfolgt nur, wenn sich der Inhalt der Eingaenge geaendert hat. .harness/state/referenztest-dotnet.hash
# (nicht versioniert) haelt den Hash (tools/eingangs-hash.sh) des Stands, der gruen lief; mtimes zaehlen nicht,
# ein checkout, merge oder rebase ohne Inhaltsaenderung loest nichts aus. Eingaenge: alles unter referenztest-dotnet/
# (Dockerfile mit den Basis-Image-Digests, csproj, Lock, Tests) ausser bin/ und obj/, dazu diese Datei. Ein
# frischer Klon und ein frischer CI-Runner haben keine Hash-Datei und fuehren den Lauf aus.
# Setzung: `make referenztest-dotnet FORCE=1` erzwingt den Lauf.
# Setzung: der Hash wird vor dem Lauf berechnet, die Datei vor dem Lauf entfernt und nach gruenem Lauf mit dem
# vorab berechneten Hash geschrieben. Ein Abbruch oder roter Lauf hinterlaesst keine Datei; eine Aenderung
# waehrend des Laufs weicht vom gespeicherten Hash ab und loest den naechsten aus. Beides irrt zur sicheren Seite.
.PHONY: referenztest-dotnet referenztest-dotnet-lock referenztest-dotnet-audit

REFERENZTEST_DOTNET_DIR = referenztest-dotnet
REFERENZTEST_DOTNET_HASH = .harness/state/referenztest-dotnet.hash

referenztest-dotnet: ## .NET-Referenztests (Smoke ohne Server) ausfuehren; laeuft nur bei geaendertem Eingangs-Inhalt, FORCE=1 erzwingt
	@h=$$(bash tools/eingangs-hash.sh $(REFERENZTEST_DOTNET_DIR) tools/mk/referenztest-dotnet.mk) || exit 1; \
	if [ -z "$(FORCE)" ] && [ "$$(cat $(REFERENZTEST_DOTNET_HASH) 2>/dev/null)" = "$$h" ]; then \
		echo "referenztest-dotnet: Eingaenge unveraendert ($$h), uebersprungen; FORCE=1 erzwingt den Lauf"; exit 0; \
	fi; \
	rm -f $(REFERENZTEST_DOTNET_HASH); \
	docker build $(BUILDER_FLAGS) --target test --progress=plain $(REFERENZTEST_DOTNET_DIR) || exit 1; \
	mkdir -p $(dir $(REFERENZTEST_DOTNET_HASH)); echo "$$h" > $(REFERENZTEST_DOTNET_HASH)

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
