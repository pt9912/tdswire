# tools/mk/referenztest-dotnet.mk — .NET-Referenztests in Docker (referenztest-dotnet/Dockerfile,
# ADR-0008, TDS-QA-01, TDS-QA-06). Repo-eigen, eingebunden von repo.mk; jedes Target hat seine Zeile
# in harness/README.md.
#
# Eigener Build-Kontext: das Verzeichnis referenztest-dotnet/. Gemountet wird nichts; die Lock-Datei
# kommt per --output heraus, wie bei `make deps-lock`.
#
# Klasse `tests` (ADR-0024 Entscheidung 2 und 3): in `make gates` laeuft `referenztest-dotnet` im Gruppenschritt
# gruppe-tests und wird uebersprungen, wenn der Stempel der Klasse zum Eingang passt (tools/mk/stempel.mk); `make gates
# FORCE=1` erzwingt den Lauf. Aufgerufen als `make referenztest-dotnet` laeuft es immer, der Layer-Cache uebergeht die Stage,
# wenn nichts davon geaendert ist, was sie kopiert. Eingaenge: referenztest-dotnet/ ganz; auf dem Host baut nichts dorthin,
# ein bin/ oder obj/ machte die Klasse nicht sauber (Lauf ohne Stempel, tools/stempel-gruppe-selbsttest.sh), dazu diese Datei
# (tools/stempel/tests.eingaenge).
# Aufnahmebedingung (ADR-0024 Entscheidung 6): Basis-Image per Digest, Wiederherstellung der Pakete aus packages.lock.json,
# Stage `test` ohne Netz und ohne Server (Smoke). Eine Uhr gibt es in KeinServerSmokeTests: die Dauer des Verbindungsfehlers
# gegen eine Grenze von 5 s (Setzung dort, Messwert 702 ms); sie kann einen Lauf rot machen, nicht einen roten Stand gruen.
.PHONY: referenztest-dotnet referenztest-dotnet-lock referenztest-dotnet-audit

REFERENZTEST_DOTNET_DIR = referenztest-dotnet

referenztest-dotnet: ## .NET-Referenztests (Smoke ohne Server) ausfuehren (Stage `test`); in make gates im Gruppenschritt tests
	docker build $(BUILDER_FLAGS) --target test --progress=plain $(REFERENZTEST_DOTNET_DIR)

# Nach einer Versionsaenderung in der csproj. Das erste Lock vertraut nuget.org beim Schreiben
# (Erstschreiben ohne Gegenpruefung); die Signaturpruefung nach nuget.config (require, trustedSigners) laeuft auch hier.
referenztest-dotnet-lock: ## Nach einer Versionsaenderung in der csproj: referenztest-dotnet/packages.lock.json neu schreiben (braucht Netz)
	docker build $(BUILDER_FLAGS) --target lock-export --progress=plain --output type=local,dest=$(REFERENZTEST_DOTNET_DIR) $(REFERENZTEST_DOTNET_DIR)

# Die Stage `audit` fragt die Schwachstellen-Datenbank der Quelle am Tag des Laufs ab; das Ergebnis
# haengt am Abfragetag. --no-cache-filter erzwingt den Lauf der Stage, sonst zeigt ein gecachter
# Layer den Stand eines frueheren Tages (ADR-0035).
referenztest-dotnet-audit: ## Bekannte Schwachstellen der Pakete melden; haengt am Abfragetag, nicht reproduzierbar, KEIN Gate
	docker build $(BUILDER_FLAGS) --target audit --no-cache-filter audit --progress=plain $(REFERENZTEST_DOTNET_DIR)

GATE_CHECKS += referenztest-dotnet
GATE_KLASSE_tests += referenztest-dotnet
GATE_STAGE += referenztest-dotnet=dotnet:test
