# tools/mk/vergleichslauf-d.mk — Vergleichslauf D: der .NET-Referenzclient gegen einen echten SQL Server im Container
# (ADR-0008 Option D, SPEC-045, TDS-QA-01). Repo-eigen, eingebunden von repo.mk; das Target hat seine Zeile in
# harness/README.md. KEIN Gate und keine Stufe von `make e2e`: das Target steht nicht in `GATE_CHECKS`, und
# tools/e2e.sh und die Workflows unter .github/ nennen es nicht (gehalten von `make skript-selbsttest`). Der Lauf
# spricht den Server dieses Repos nicht an; ein Lauf in der CI oder in `make gates` braucht eine neue Entscheidung des
# Nutzers.
#
# EULA: VERGLEICH_ACCEPT_EULA=Y ist die Zustimmung des Nutzers pt9912 (2026-11-01) fuer das Developer-Image von
# SQL Server (`MSSQL_PID=Developer`), lokal und nur in diesem Target. Sie ist eine Entscheidung des Nutzers, keine
# Rechtspruefung und keine Rechtsaussage des Repos; verbindlich ist der Text des Herstellers zum Zeitpunkt des Pulls.
# Quelle der Entscheidung: slice-qa-vergleichslauf-d §1 und ADR-0008 (Abschnitt EULA, 2026-10-30). Die Variable steht
# nirgends sonst; tools/vergleichslauf-d.sh bricht ohne `Y` und in der CI (CI, GITHUB_ACTIONS) mit Exit 2 ab.
#
# Referenzinstanzen (ADR-0028): `2022-latest` fuer alle Schritte ausser TDS 8.0, die Dockerfile-Stage `referenzinstanz-2025` (`2025-latest`) fuer den TDS-8.0-Pfad;
# gemessen als Einzelmessung am 2026-11-01 auf einem Host, Grenzen in docs/messungen/referenzabgleich.md.
#
# Bedarf: Einzelmessungen am 2026-11-01 auf einem Host (20 CPUs, 31 GiB Speicher, Docker 29.9.2, x86_64) unter Last
# durch andere Laeufe (Lastmittel 6 bis 11); gilt fuer diesen Host, nicht fuer CI-Runner. Laufzeit: `make
# vergleichslauf-d` (fuenf Laeufe) 113 s und 96 s in zwei Durchgaengen, ein Einzellauf 16 bis 27 s einschliesslich Bau und
# Start des Servers (12 bis 24 s). Netz: Summe der komprimierten Layer laut Manifest der gepinnten Digests 627 MB (2022)
# und 636 MB (2025); der Pull von 2025-latest dauerte 20 s, mit teils vorhandenen Layern, nicht kalt. Platte: 2346 MB (2022)
# und 2426 MB (2025) laut `docker image inspect`, zusammen rund 4,8 GB (Layer teilweise geteilt, nicht gemessen); die Images
# bleiben nach dem Lauf liegen; der freie Platz wird vor dem Lauf nicht geprueft. Speicher des Servers 671 bis 965 MiB (`docker stats`, ein Messpunkt nach dem Start).
# Die Messzeilen jedes Laufs stehen in seiner Ausgabe (`vergleichslauf-d: messung:`).
# Mitschnitt-Lauf (`LAUF=aufnahme`, `LAUF=mitschnitt`; SPEC-045): die Aufnahme braucht die Referenzinstanz (EULA wie oben), das Abspielen nur den Server dieses Repos
# und das Client-Image; Einzelmessungen am 2026-11-06: Aufnahme beider Instanzen 90 s, Abspielen je Teillauf 14 bis 17 s.
# Messreihe am Draht (`LAUF=wire`, `wire-2022`, `wire-2025`; SPEC-045): TLS-beendender Proxy im Client-Container, 22 Szenarien je Instanz; Einzelmessung am 2026-11-06:
# beide Instanzen zusammen 70 s einschliesslich Bau und Start; Ablage in referenztest-dotnet/Mitschnitt/messung/; `MESSUNG_NUR=<Szenarien>` fuer eine Teilmenge ohne Ablage.
# Messreihe zu Massenladen, MERGE, Katalog und Strukturaenderung (`LAUF=bulk`, `bulk-2022`, `bulk-2025`; SPEC-045, Kategorie MessungBulk): wie `wire`, 22 Szenarien je Instanz;
# Einzelmessung am 2026-11-07: Laufzeit gesamt `bulk-2022` 56 s, `bulk-2025` 68 s (mit Cache); Ablage in referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/.
# `MESSUNG_NUR=<Szenarien>` fuer eine Teilmenge ohne Ablage; mit `MESSUNG_WIEDERHOLEN=<N>` faehrt jedes genannte Szenario N-mal ueber einen frischen Bestand und gibt die Rate
# roter Laeufe aus (`MESSUNG_DIREKT=1`: der Client verbindet sich ohne Proxy).
# d-migrate gegen die Referenzinstanz (`LAUF=jdbc`, `jdbc-2022`, `jdbc-2025`; `tools/jdbc-messung/Aufnahme.java`, Funktion `jdbclauf` in `tools/vergleichslauf-d.sh`):
# 19 Befehle (`data import` und `data transfer` mit und ohne `--on-conflict`, `data export`; Funktion `jdbclauf`) ueber den mitschneidenden Durchlauf; Einzelmessungen am 2026-11-08:
# 120 s (Instanz 2022) und 148 s (Instanz 2025) einschliesslich Bau, Lastmittel 20 bis 50; Ablage in referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/ (`<stufe>.aufnahme.txt`);
# `MESSUNG_NUR=<Schritte>` fuer eine Teilmenge ohne Ablage, `MESSUNG_DIREKT=1` ohne Durchlauf, `AUFNAHME_PAKETE=1` mit den Paketen des Durchlaufs.
# d-migrate `schema migrate`, `schema compare` und `schema reverse` gegen die Referenzinstanz (`LAUF=schema`, `schema-2022`, `schema-2025`; Funktion `schemalauf` in `tools/vergleichslauf-d.sh`):
# 16 Schritte (Soll mit allen Elementarten anlegen, vergleichen, zweiter Lauf mit demselben Soll, je Elementart eine Aenderung, Index mit `unique: true`) ueber den mitschneidenden Durchlauf;
# Einzelmessungen am 2026-11-11 (Lastmittel 4 bis 8): 71 s (Instanz 2022) und 85 s (Instanz 2025) einschliesslich Bau; Ablage `<stufe>.schema.aufnahme.txt` neben den Dateien von `jdbc`.
# Live-Lauf (`LAUF=live-2022`, `live-2025`, `live` fuer beide) und Gegenmessungen (`LAUF=messung-2022`, `messung-2025`) laufen
# mit derselben EULA-Entscheidung und denselben Images (gepinnte Digests); der Live-Lauf baut zusaetzlich die Dockerfile-Stage
# `kompatibilitaetsserver` (JVM). Einzelmessungen am 2026-11-06 auf demselben Host (Lastmittel 8 bis 9): ein Teillauf des
# Live-Laufs 35 bis 46 s einschliesslich Bau und Start der Referenzinstanz (13 bis 22 s, einmal 55 s), `live` zusammen 77 bis
# 98 s; ein Lauf `messung-*` 22 und 23 s. Die Images und der Speicher des Servers sind die der anderen Laeufe.
.PHONY: vergleichslauf-d

VERGLEICH_ACCEPT_EULA = Y
LAUF ?= alle

vergleichslauf-d: ## Vergleichslauf D: Referenzclient gegen echten SQL Server (Developer-Image, EULA-Entscheidung des Nutzers), LAUF=alle|live|aufnahme|mitschnitt|wire|wire-2022|wire-2025|bulk|bulk-2022|bulk-2025|jdbc|jdbc-2022|jdbc-2025|schema|schema-2022|schema-2025|aufnahme-2022|aufnahme-2025|mitschnitt-2022|mitschnitt-2025|messung-2022|messung-2025|live-2022|live-2025|2022-anmeldung|2022-tds8|2025-anmeldung|2025-tds8|2025-tds8-forcestrict — KEIN Gate, nur lokal
	VERGLEICH_ACCEPT_EULA="$(VERGLEICH_ACCEPT_EULA)" BUILDER_FLAGS="$(BUILDER_FLAGS)" bash tools/vergleichslauf-d.sh "$(LAUF)"
