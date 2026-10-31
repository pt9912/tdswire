# CO-003: Keine Drosselung wiederholter Anmeldeversuche

**Status:** Aktiv.

**Datum angelegt:** 2026-10-31. **Letzte Prüfung:** 2026-10-31.

**Betroffenes Gate:** keines. Die Aussage ist ein offener Punkt der Entscheidung ([ADR-0009](../adr/0009-authentifizierungsmodus.md) §Offen) und hat kein Gate; `make test` belegt nur die Messungen unter *Geltungs-Konfiguration*.

**Geltungsbereich:** Anmeldepfad des Serverprozesses (`src/main/kotlin/io/github/pt9912/tdswire/protocol/session/`, Aufruf des Adapters über `AuthPool`)

**Folge-Slice:** [`slice-qa-anmeldedrosselung.md`](../planning/open/slice-qa-anmeldedrosselung.md)

Regeln: Baseline-Regelwerk `modul-07-carveouts.md` §Ziel-Form: Carveout — ein
Carveout braucht immer einen Auflösungs-Trigger **und** einen Folge-Slice.

---

## Begründung

[ADR-0009](../adr/0009-authentifizierungsmodus.md) §Offen nennt den Schutz gegen wiederholte Anmeldeversuche (Drosselung, Sperre) weder entschieden noch gebaut: Bei gleicher Ablehnung für jeden Grund bleibt Raten ungebremst ([TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) verlangt keine Enumerationsfestigkeit). Die Wahl der Art (je Benutzername, je Quelladresse, Verzögerung oder Sperre) und der Grenzwerte ist eine Entscheidung des Nutzers, die eine neue ADR braucht; der Implementer entscheidet sie nicht. [slice-qa-sicherheit](../planning/in-progress/slice-qa-sicherheit.md) misst die Grundlagen der Entscheidung: Eine Anmeldung mit 210000 Iterationen braucht auf dem Host des Laufs rund 57 ms, und vier Aufrufe belegen alle Plätze des `AuthPool` für die Dauer einer Ableitung; wie viele Versuche je Sekunde ein Client dem Server abverlangen kann, ist nicht gemessen.

## Auflösungs-Trigger

Der Carveout ist aufgelöst, wenn der Folge-Slice `slice-qa-anmeldedrosselung` in `done/` liegt und eines davon zutrifft: (a) eine ADR mit Status `Accepted` legt die Drosselung fest und der Anmeldepfad setzt sie mit Tests um, oder (b) eine ADR mit Status `Accepted` hält den Verzicht auf eine Drosselung fest.

## Geltungs-Konfiguration

| Datei | Zeile/Section | Wert |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/AuthExecutor.kt` | KDoc von `AuthExecutor` | nennt `CO-003`: die Begrenzung der Plätze ist keine Drosselung |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/AuthPoolOccupationTest.kt` | KDoc der Platzbelegung | misst die Wartezeit hinter belegten Plätzen, baut keine Abhilfe |

## Verifikation (nach Auflösung)

- [ ] Die Drosselung ist per ADR entschieden und umgesetzt, oder der Verzicht ist per ADR festgehalten.
- [ ] `make gates` grün ohne Ausnahme.
- [ ] Datei wird nach `docs/plan/carveouts/done/` bewegt (reiner `git mv`). <!-- d-check:ignore (done/ entsteht erst bei erster Carveout-Auflösung) -->
- [ ] Folge-Slice geschlossen oder explizit dokumentiert.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Angelegt | [slice-qa-sicherheit](../planning/in-progress/slice-qa-sicherheit.md) |
