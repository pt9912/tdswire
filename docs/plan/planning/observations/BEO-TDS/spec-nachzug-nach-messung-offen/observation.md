# Messungen am Referenzclient sind geliefert, die Spezifikation nennt sie noch offen

**Sub-Area:** `*` (Spezifikation, Kompatibilitätstests)

Nach den Messungen am Referenzclient stehen in `spec/spezifikation.md` Aussagen, die den Stand davor beschreiben: `SPEC-025`, `SPEC-032` und `SPEC-050` tragen „Lauf des E2E-Durchgangs, noch einzurichten“; `SPEC-004` führt Pooling, `RESETCONNECTION`, Transaktionen und `SET`-Text als offene Freigabepunkte; die Folgepflichten (2) und (3) von [ADR-0019](../../../../adr/0019-kompatibilitaetstests-zuschnitt.md) (Container-Modell in `SPEC-025`/`SPEC-032`, Matrix B nicht in `make gates`) stehen aus. Spezifikationsänderungen sind ein eigener Schritt; die ADRs bleiben nach `Accepted` unverändert (`AGENTS.md` §3.5), eine Fortschreibung von [ADR-0001](../../../../adr/0001-tds-version-und-clientprofil.md) bräuchte eine Folge-ADR.
