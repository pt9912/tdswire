# ADR-0028: Referenzinstanz für TDS 8.0: zweite Instanz `2025-latest` neben `2022-latest` (Nachtrag zu ADR-0008)

**Status:** Accepted

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0002](0002-tls-und-verschluesselung.md)

**Schärft:** [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [SPEC-050](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)

**Supersedes (teilweise), jeweils nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Datei von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0008](0008-dotnet-kompatibilitaetstests.md), Messung D, Abweichung von den Annahmen: „SQL Server 2022 bietet TDS 8.0 nicht an; der TDS-8.0-Pfad ist an dieser Referenzinstanz also nicht abgleichbar.“ Ersetzt durch die gemessene Wirkung im Kontext unten: Der Server 2022 erkennt den Strict-Versuch und lehnt ihn mit einer Meldung zur Zertifikatskonfiguration ab (Einzelmessung 2026-11-01, ein Host, vier selbst erzeugte Zertifikatsvarianten); „bietet nicht an“ ist unter dieser Bedingung nicht belegt.
- [ADR-0008](0008-dotnet-kompatibilitaetstests.md), derselbe Punkt: „Der Server-Image-Tag für TDS 8.0 (etwa eine neuere SQL-Server-Version) wurde nicht ermittelt.“ Ersetzt durch: Für `2025-latest` (Digest unten) gelingt `Encrypt=Strict` in der Messung vom 2026-11-01.
- [ADR-0008](0008-dotnet-kompatibilitaetstests.md), Messung D, letzter Punkt: „Gegenüber SPEC-023 (Mindestversion 1.2, TLS 1.3 bevorzugt) ist die Referenzinstanz bei TLS 1.3 also kein Maßstab.“ Eingeschränkt auf `2022-latest`; für `2025-latest` zeigt das Log TLS 1.0 bis 1.3 als erlaubt.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0008](0008-dotnet-kompatibilitaetstests.md) misst, dass `Encrypt=Strict` gegen `mssql/server:2022-latest` mit `Class=20, Number=0` scheitert. [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) lässt den Abgleich des TDS-8.0-Pfads deshalb entfallen.

Der Vergleichslauf D (`make vergleichslauf-d`) misst dazu. **Bedingung für alle Zeilen:** Einzelmessungen vom 2026-11-01 auf einem Host, `Microsoft.Data.SqlClient` 7.1.1, selbst erzeugtes Zertifikat, das im Server über `tlscert` und `tlskey` eingetragen und beim Client Vertrauensanker ist; Belege und Digests in `docs/messungen/referenzabgleich.md`.

- `2022-latest` (Digest `sha256:cf8f0ab2…`): Unter vier getesteten Zertifikatsvarianten und in diesem Aufbau (Einzelmessung 2026-11-01, ein Host, vier selbst erzeugte Zertifikatsvarianten, keine Herstellerquelle für eine Konfiguration unter Linux) gelingt `Encrypt=Strict` nicht. Das Log des Servers nennt „A valid TLS certificate is not configured to accept strict (TDS 8.0 and above) connections“: der Server erkennt den Versuch und lehnt ihn mit einer Meldung zur Zertifikatskonfiguration ab. Ob eine andere Konfiguration unter Linux `Strict` auf 2022 erlaubt, ist **nicht geklärt**. `mssql-conf list` führt im Image keine Einstellung `network.forcestrict`.
- `2025-latest` (Digest `sha256:245f6b1c…`): Mit selbst erzeugtem Zertifikat und `tlscert`-Eintrag (Einzelmessung 2026-11-01, ein Host) gelingt `Encrypt=Strict` ohne Zusatzeinstellung, die Sonde (OpenSSL) schließt den TLS-Handschlag mit TLS 1.3 ab, das Log nennt TLS 1.0 bis 1.3. Mit `network.forcestrict = 1` (laut Hersteller ab SQL Server 2025) lehnt der Server `Encrypt=Mandatory` ab. Nicht gemessen: Verhalten mit dem Standardzertifikat des Servers ohne `tlscert`.

## Empfehlung

**Empfehlung: Option B.** Der Vergleichslauf D führt zwei Referenzinstanzen. `2022-latest` bleibt die Instanz der Messung D von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) für alle Schritte außer TDS 8.0; `2025-latest` (Digest im Dockerfile des Referenztests) wäre die Referenzinstanz für den TDS-8.0-Pfad. Entschieden ist das erst mit der Annahme dieser ADR durch den Nutzer; bis dahin ist die Stufe `referenzinstanz-2025` im Dockerfile ein Messwerkzeug des Vergleichslaufs, keine angenommene Entscheidung. [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) und der Plan des Live-Laufs sind nach der Annahme nachzuziehen.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — nur `2022-latest`, TDS 8.0 bleibt ohne Abgleich | kein zweites Image (2,4 GB Platte, rund 636 MB Netz) | der TDS-8.0-Pfad hat keine Referenz, obwohl `2025-latest` unter den genannten Bedingungen `Strict` annimmt |
| **B — beide Instanzen, 2025 für TDS 8.0 (Empfehlung)** | TDS 8.0 und TLS 1.3 haben eine Referenz; die Messung D von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleibt vergleichbar | doppelter Bedarf an Platte und Netz; zwei Digests zu pflegen; das Schema muss auf beiden Versionen laufen |
| C — nur `2025-latest` | ein Image, eine Referenz für alles | die Messreihe D und alle bisherigen Aussagen beziehen sich auf 2022; ein Wechsel macht sie nicht mehr vergleichbar, ohne sie neu zu messen |

## Konsequenzen

- Positiv: der TDS-8.0-Pfad wäre abgleichbar; der Befund „Class 20“ ist eingegrenzt.
- Negativ: zweite Instanz im Lauf D (Bedarf in `tools/mk/vergleichslauf-d.mk`); die Aussage über 2022 bleibt auf Image, Aufbau und die vier Zertifikatsvarianten beschränkt.
- Folgepflicht: Der Plan des Live-Laufs im Referenzabgleich nennt den Abgleich des TDS-8.0-Pfads an der Referenzinstanz 2022 als „gefolgert, nicht gemessen“ und lässt ihn entfallen; das ist nach Annahme dieser ADR anzupassen (Planner). `SPEC-045` nennt dieselbe Grenze („der Abgleich beider Pfade entfällt dort“) und ist nach Annahme nachzuziehen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Vergleichslauf D, Lauf `2025-tds8` | `Encrypt=Strict` gelingt gegen den gepinnten Digest, sonst rot | `make vergleichslauf-d` (kein Gate, nur lokal) |

## Re-Evaluierungs-Trigger

Ein neuer Digest von `2025-latest` oder `2022-latest` mit anderem Verhalten bei `Encrypt=Strict`; eine belegte Konfiguration, mit der `2022-latest` `Strict` annimmt; eine Rücknahme der EULA-Entscheidung des Nutzers vom 2026-11-01 (dann entfällt der Lauf D).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Proposed (Empfehlung Option B, Entscheidung beim Nutzer) | Vergleichslauf D (Messung TDS-8.0-Tag) |
| 2026-11-13 | Accepted (Entscheidung des Nutzers, Option B) | [slice-referenzabgleich-adr-0028-nachzug](../planning/open/slice-referenzabgleich-adr-0028-nachzug.md) |
