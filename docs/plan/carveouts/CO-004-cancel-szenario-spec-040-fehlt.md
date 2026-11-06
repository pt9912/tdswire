# CO-004: Cancel-Szenario des Integrationslaufs nach SPEC-040 fehlt

**Status:** Aktiv.

**Datum angelegt:** 2026-10-31. **Letzte Prüfung:** 2026-10-31.

**Betroffener Lauf:** `make simulations-integration` (Integrationslauf nach `SPEC-040`, Stufe 3 des E2E-Durchgangs `make e2e`, kein Gate nach [ADR-0022](../adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Der Carveout nimmt keine Schwelle und keine Regel aus: Der Lauf ist grün mit vier Szenarien (Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle oberhalb der Speichergrenze, Fehler der Zeilenquelle); das fünfte, Cancel mit Folgeabfrage, hat keinen Test.

**Geltungsbereich:** Cancel-Teil von `SPEC-040` in [`spec/spezifikation.md`](../../../spec/spezifikation.md) und DoD-Punkt 3 von [slice-sim-aenderung-integration](../planning/done/welle-simulations-backend/slice-sim-aenderung-integration.md); Tests unter `referenztest-dotnet/Integration/`, Skript `tools/simulationstest.sh`.

**Folge-Slice:** [`slice-cancel-referenzclient-szenario.md`](../planning/in-progress/slice-cancel-referenzclient-szenario.md); sein Abbruchpfad im Server steht in [`slice-attention-cancel-timeout.md`](../planning/done/slice-attention-cancel-timeout.md) (Zerlegung vom 2026-11-01)

**Verantwortlich:** pt9912 (Autor des Slice-Plans) bis zur Priorisierung von `slice-cancel-referenzclient-szenario`.

**Entscheidung des Nutzers 2026-10-31:** Der Nutzer hat „Option A“ bestätigt: die Führung als befristeter Carveout mit dem Folge-Slice `slice-attention-cancel-timeout`; die Alternativen B und C sind nicht gewählt.

Regeln: Baseline-Regelwerk `modul-07-carveouts.md` §Ziel-Form: Carveout — ein
Carveout braucht immer einen Auflösungs-Trigger **und** einen Folge-Slice.

---

## Begründung

Der Server beantwortet ein ATTENTION-Paket während einer Anfrage nicht: Der `RequestLoop` liest während der Anfrage nicht vom Transport, und [`SPEC-001`](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) nennt den Pfad Zielbild. Ein Cancel-Test mit Folgeabfrage am Referenzclient kann gegen den heutigen Server nicht bestehen; der Pfad gehört zu [slice-attention-cancel-timeout](../planning/done/slice-attention-cancel-timeout.md) (Welle welle-fehler-abbruch). Dieser Slice schließt den Referenzclient aus, und sein Start-Trigger verlangt Nutzerentscheidungen zum Inhalt der Cancel-Antwort, zu Cancel und Timeout als Fehler und zu ATTENTION im Handshake. Bis dahin ist der Gegenstand nicht baubar und kein Fehler von [slice-sim-aenderung-integration](../planning/done/welle-simulations-backend/slice-sim-aenderung-integration.md). Befund: Review F-1 (Report `2026-10-31-a7e6afa-slice-sim-aenderung-integration`, mit der Archivierung von welle-simulations-backend aus `docs/reviews/` entfernt, im Archiv der Welle); Einordnung als L-4: Verifikation (Report `2026-10-31-accd7c7-verifikation-slice-sim-aenderung-integration`, mit der Archivierung von welle-simulations-backend aus `docs/reviews/` entfernt, im Archiv der Welle).

## Auflösungs-Trigger

Der Carveout ist aufgelöst, wenn beides zutrifft: (a) `slice-attention-cancel-timeout` (Abbruchpfad) und `slice-cancel-referenzclient-szenario` (Szenario) liegen in `done/`, und (b) ein Cancel-Szenario mit Folgeabfrage von `SPEC-040` läuft am Referenzclient gegen das Simulations-Backend in `make simulations-integration` (E2E-Stufe 3) und besteht.

## Geltungs-Konfiguration

Stellen, die die Lücke nennen (keine Gate-Konfiguration):

| Datei | Zeile/Section | Wert |
|---|---|---|
| `spec/spezifikation.md` | `SPEC-040` | „Cancel mit Folgeabfrage gehört nicht zum Lauf“ |
| `referenztest-dotnet/Integration/SimulationBackendTests.cs` | KDoc | nennt die Lücke |
| `tools/simulationstest.sh` | Kopf | nennt die Lücke |
| `harness/README.md` | Sensor-Zeile `make simulations-integration` | nennt die vier laufenden Szenarien und diesen Carveout als Bindung |

## Verifikation (nach Auflösung)

- [ ] Das Cancel-Szenario läuft in `make simulations-integration` und besteht; `SPEC-040` nennt es als Teil des Laufs.
- [ ] `make gates` und `make e2e` grün ohne Ausnahme.
- [ ] Datei wird nach `docs/plan/carveouts/done/` bewegt (reiner `git mv`). <!-- d-check:ignore (done/ entsteht erst bei erster Carveout-Auflösung) -->
- [ ] Folge-Slice geschlossen oder explizit dokumentiert.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Angelegt (Voreinstellung des Koordinators) | [slice-sim-aenderung-integration](../planning/done/welle-simulations-backend/slice-sim-aenderung-integration.md) |
| 2026-10-31 | Option A vom Nutzer bestätigt | Sitzung |
| 2026-11-01 | Folge-Slice geteilt: Das Szenario am Referenzclient liegt in [slice-cancel-referenzclient-szenario](../planning/in-progress/slice-cancel-referenzclient-szenario.md), der Abbruchpfad bleibt in [slice-attention-cancel-timeout](../planning/done/slice-attention-cancel-timeout.md); die Start-Bedingung der Nutzerentscheidungen ist mit [ADR-0027](../adr/0027-abbruch-und-zuruecksetzen.md) erfüllt | [slice-attention-cancel-timeout](../planning/done/slice-attention-cancel-timeout.md) |
| 2026-11-06 | Das Cancel-Szenario besteht in `make simulations-integration` (Stufe 3), der Cancel-Teil von [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) in Stufe 7; die vier Stellen der Geltungs-Konfiguration sind berichtigt. Der Carveout bleibt `Aktiv`, bis der Folge-Slice in `done/` liegt (Auflösungs-Trigger a) | [slice-cancel-referenzclient-szenario](../planning/in-progress/slice-cancel-referenzclient-szenario.md) |
