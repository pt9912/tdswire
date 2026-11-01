# ADR-0028: Referenzinstanz für TDS 8.0: zweite Instanz `2025-latest` neben `2022-latest` (Nachtrag zu ADR-0008)

**Status:** Proposed

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0002](0002-tls-und-verschluesselung.md)

**Schärft:** [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [SPEC-050](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)

**Supersedes:** keines. [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleibt in Kraft; diese ADR ersetzt zwei seiner Aussagen durch Messungen und legt die Folge fest. Betroffen sind (a) „SQL Server 2022 biete TDS 8.0 nicht an“ und (b) „der Server-Image-Tag für TDS 8.0 wurde nicht ermittelt“.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0008](0008-dotnet-kompatibilitaetstests.md) misst, dass `Encrypt=Strict` gegen `mssql/server:2022-latest` mit `Class=20, Number=0` scheitert, folgert daraus, SQL Server 2022 biete TDS 8.0 nicht an, und nennt den Tag für TDS 8.0 als nicht ermittelt. [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) lässt den Abgleich des TDS-8.0-Pfads deshalb entfallen.

Der Vergleichslauf D (`make vergleichslauf-d`, Messungen vom 2026-11-01, Einzelmessungen auf einem Host; Belege und Digests in `docs/plan/referenzabgleich-ausnahmen.md`) misst dazu:

- `2022-latest` (Digest `sha256:cf8f0ab2…`): `Encrypt=Strict` scheitert auch mit einem selbst erzeugten, im Client vertrauten Zertifikat (vier Varianten). Das Log des Servers nennt „A valid TLS certificate is not configured to accept strict (TDS 8.0 and above) connections“: der Server erkennt den Versuch, er wird nicht als unbekanntes Protokoll behandelt. Die Folgerung „bietet TDS 8.0 nicht an“ trifft für dieses Image in diesem Aufbau zu, der Grund der Ablehnung (Zertifikatskonfiguration) ist nicht geklärt. `mssql-conf list` führt im Image keine Einstellung `network.forcestrict`.
- `2025-latest` (Digest `sha256:245f6b1c…`): `Encrypt=Strict` gelingt ohne Zusatzeinstellung, der TLS-Handschlag mit der Sonde endet in TLS 1.3, das Log nennt TLS 1.0 bis 1.3. Mit `network.forcestrict = 1` (laut Hersteller ab SQL Server 2025) lehnt der Server `Encrypt=Mandatory` ab.

Die Aussage von [ADR-0008](0008-dotnet-kompatibilitaetstests.md), die Referenzinstanz sei bei TDS 8.0 und TLS 1.3 kein Maßstab, gilt damit für `2022-latest`, nicht für `2025-latest`.

## Entscheidung

Wir wählen **Option B**: Der Vergleichslauf D führt zwei Referenzinstanzen. `2022-latest` bleibt die Instanz der Messung D von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) für alle Schritte außer TDS 8.0; `2025-latest` (Digest im Dockerfile des Referenztests) ist die Referenzinstanz für den TDS-8.0-Pfad. Der Abgleich des TDS-8.0-Pfads entfällt damit nicht mehr; [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) und die Slices des Live-Laufs sind nachzuziehen, sobald die ADR `Accepted` ist.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — nur `2022-latest`, TDS 8.0 bleibt ohne Abgleich | kein zweites Image (2,4 GB Platte, rund 636 MB Netz) | der TDS-8.0-Pfad hat keine Referenz; die Aussage „bietet nicht an“ bleibt für 2025 gemessen falsch |
| **B — beide Instanzen, 2025 für TDS 8.0** | TDS 8.0 und TLS 1.3 haben eine Referenz; die Messung D von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleibt vergleichbar | doppelter Bedarf an Platte und Netz; zwei Digests zu pflegen; das Schema muss auf beiden Versionen laufen |
| C — nur `2025-latest` | ein Image, eine Referenz für alles | die Messreihe D und alle bisherigen Aussagen beziehen sich auf 2022; ein Wechsel macht sie nicht mehr vergleichbar, ohne sie neu zu messen |

## Konsequenzen

- Positiv: der TDS-8.0-Pfad ist abgleichbar; der Befund „Class 20“ ist eingeordnet.
- Negativ: zweite Instanz im Lauf D (Bedarf in `tools/mk/vergleichslauf-d.mk`); die Aussage über 2022 bleibt auf das Image und den Aufbau beschränkt (Zertifikatsvarianten, keine Quelle für eine Konfiguration, die `Strict` auf 2022 erlaubt).
- Folgepflicht: der Plan des Live-Laufs im Referenzabgleich führt „die Referenzinstanz 2022 biete den TDS-8.0-Pfad nicht an, der Abgleich entfällt“; das trägt die Messung für 2025 nicht mehr (Planner). `SPEC-045` nennt dieselbe Grenze („der Abgleich beider Pfade entfällt dort“) und ist nach Annahme dieser ADR nachzuziehen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Vergleichslauf D, Lauf `2025-tds8` | `Encrypt=Strict` gelingt gegen den gepinnten Digest, sonst rot | `make vergleichslauf-d` (kein Gate, nur lokal) |

## Re-Evaluierungs-Trigger

Ein neuer Digest von `2025-latest` oder `2022-latest` mit anderem Verhalten bei `Encrypt=Strict`; eine belegte Konfiguration, mit der `2022-latest` `Strict` annimmt; eine Rücknahme der EULA-Entscheidung des Nutzers vom 2026-11-01 (dann entfällt der Lauf D).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Proposed | Vergleichslauf D (Messung TDS-8.0-Tag) |
