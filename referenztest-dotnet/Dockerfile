# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e
# ---------------------------------------------------------------------------
# tdswire - .NET-Referenztests (ADR-0008, TDS-QA-01, TDS-QA-06)
#
# Eigener Build-Kontext (dieses Verzeichnis), kein Layer-Cache mit dem Gradle-Dockerfile an der
# Wurzel. Auf dem Host laufen nur Docker und make, nichts wird gemountet:
#   make referenztest-dotnet        -> docker build --target test --progress=plain referenztest-dotnet
#   make referenztest-dotnet-lock   -> Stage `lock-export` schreibt packages.lock.json per --output
# Frontend und Basis-Image sind per Digest gepinnt; der Digest des Basis-Images ist der
# Index-Digest des Tags 10.0.403. Ein neuer Digest ist eine Aenderung dieser Datei.
# ---------------------------------------------------------------------------

FROM mcr.microsoft.com/dotnet/sdk:10.0.403@sha256:de4fe90a98845d75910b9769662465c84de1a934665c431279d1382b2bddc02b AS basis
ENV DOTNET_NOLOGO=1 \
    DOTNET_CLI_TELEMETRY_OPTOUT=1
WORKDIR /src

# --- Restore: nur die drei Dateien, die den Restore bestimmen; eine Aenderung am Quelltext laesst
# diese Stage im Cache. Der Restore laeuft gesperrt (RestoreLockedMode in der csproj). ---------
FROM basis AS restore
COPY nuget.config TdsWire.Referenztests.csproj packages.lock.json ./
RUN dotnet restore

# --- Test: Build und Tests ohne Netz. Ein roter Test bricht den Build. -------------------------
FROM restore AS test
COPY Smoke Smoke
RUN --network=none dotnet test --no-restore --nologo

# --- Audit: meldet bekannte Schwachstellen der Pakete aus der Datenbank der Quelle am Tag des Laufs.
# Das Ergebnis haengt am Abfragetag und ist nicht reproduzierbar; die Stage ist kein Gate (CO-001). -
FROM restore AS audit
RUN dotnet list package --vulnerable --include-transitive

# --- Lock: loest ohne Sperre auf und schreibt packages.lock.json; ein vorhandenes Lock wird nicht
# kopiert. `lock-export` gibt die Datei per --output heraus. ------------------------------------
FROM basis AS lock
COPY nuget.config TdsWire.Referenztests.csproj ./
RUN dotnet restore -p:LockErzeugen=true

FROM scratch AS lock-export
COPY --from=lock /src/packages.lock.json /
