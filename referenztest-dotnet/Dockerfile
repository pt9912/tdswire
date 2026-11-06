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

# --- Integration: der Referenzclient gegen den Serverprozess mit Simulations-Backend (`make simulations-integration`,
# SPEC-040). Gebaut wird ohne Netz; der Lauf des Containers braucht den Server im selben Netzwerk (TDSWIRE_ZIEL).
# Die Stage `test` kopiert nur Smoke und laeuft ohne Server. ---
FROM restore AS integration
COPY Integration Integration
RUN --network=none dotnet build --no-restore --nologo
CMD ["dotnet", "test", "--no-build", "--nologo", "--logger", "console;verbosity=detailed"]

# --- Eigenes Backend: der Referenzclient gegen den Serverprozess mit einem eigenen In-Memory-Backend (`make eigenes-backend`,
# TDS-FA-08, TDS-RB-04). Gebaut wird ohne Netz; der Lauf des Containers braucht den Server im selben Netzwerk (TDSWIRE_ZIEL). ---
FROM restore AS eigenes-backend
COPY EigenesBackend EigenesBackend
RUN --network=none dotnet build --no-restore --nologo
CMD ["dotnet", "test", "--no-build", "--nologo", "--logger", "console;verbosity=detailed"]

# --- Kompatibilitaet: die Szenarien und Messungen des Referenzclients gegen den Serverprozess (`make kompatibilitaet`,
# ADR-0019). Gebaut wird ohne Netz; der Lauf braucht den Server im selben Netzwerk (TDSWIRE_ZIEL) und sein Zertifikat
# (SSL_CERT_FILE) als Vertrauensanker. Der Lauf waehlt die Tests je Stufe mit `--filter Kategorie=...`. ---
FROM restore AS kompatibilitaet
COPY Kompatibilitaet Kompatibilitaet
RUN --network=none dotnet build --no-restore --nologo
CMD ["dotnet", "test", "--no-build", "--nologo", "--logger", "console;verbosity=detailed"]

# --- Vergleich: der Referenzclient gegen einen echten SQL Server (`make vergleichslauf-d`, ADR-0008; kein Gate).
# Gebaut wird ohne Netz; der Lauf braucht den Server im selben Netzwerk (TDSWIRE_ZIEL), sein Zertifikat
# (SSL_CERT_FILE), das Kennwort von `sa` und die Erwartung fuer Encrypt=Strict (Umgebung, siehe Vergleich/ReferenzZiel.cs). ---
FROM restore AS vergleich
COPY Vergleich Vergleich
RUN --network=none dotnet build --no-restore --nologo
CMD ["dotnet", "test", "--no-build", "--nologo", "--logger", "console;verbosity=detailed"]

# --- Referenzinstanzen des Vergleichslaufs D: echte SQL Server (Developer-Edition, `MSSQL_PID=Developer` im Lauf).
# Index-Digests der Tags, ermittelt am 2026-11-01 mit `docker buildx imagetools inspect`; ein neuer Digest ist eine
# Aenderung dieser Datei. Die Stage tut nichts ausser den Digest zu halten; ACCEPT_EULA setzt allein das Make-Target
# `vergleichslauf-d` (Entscheidung des Nutzers, ADR-0008 und tools/mk/vergleichslauf-d.mk). ---
# 2022-latest: SQL Server 2022 (RTM-CU27-GDR) 16.0.4305.5, die Referenzinstanz der Messung D in ADR-0008.
FROM mcr.microsoft.com/mssql/server:2022-latest@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718 AS referenzinstanz-2022
# 2025-latest: SQL Server 2025, fuehrt `network.forcestrict` in `mssql-conf list` (das 2022-Image nicht).
FROM mcr.microsoft.com/mssql/server:2025-latest@sha256:245f6b1c8903edae52cb20fa31419cefcde1a49938bb8c3447099011fc871ca3 AS referenzinstanz-2025

# --- Audit: meldet bekannte Schwachstellen der Pakete aus der Datenbank der Quelle am Tag des Laufs.
# Das Ergebnis haengt am Abfragetag und ist nicht reproduzierbar; die Stage ist kein Gate (ADR-0035). -
FROM restore AS audit
RUN dotnet list package --vulnerable --include-transitive

# --- Lock: loest ohne Sperre auf und schreibt packages.lock.json; ein vorhandenes Lock wird nicht
# kopiert. `lock-export` gibt die Datei per --output heraus. ------------------------------------
FROM basis AS lock
COPY nuget.config TdsWire.Referenztests.csproj ./
RUN dotnet restore -p:LockErzeugen=true

FROM scratch AS lock-export
COPY --from=lock /src/packages.lock.json /
