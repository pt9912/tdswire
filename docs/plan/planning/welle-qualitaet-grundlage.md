# Welle welle-qualitaet-grundlage: Qualitätsanforderungen belegen

**Lifecycle:** Diese Datei entsteht bei der **Eröffnung** der Welle und liegt
flach unter `docs/plan/planning/`; bei Closure wandert sie per `git mv` nach
`done/` (neben ihre `welle-<Kennung>-results.md`). Der Zustand ist die
Verzeichnis-Position — kein Status-Feld. **Geplante Wellen bekommen noch keine
Datei:** Sie stehen in der Roadmap unter *Nächste Wellen* und nirgends sonst —
zwei Positionen, nicht drei.

**Zielmeilenstein:** kein Meilenstein-Bezug.

**Verantwortlich:** pt9912. **Datum:** 2026-10-29.

---

## 1. Welle-Ziel


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht.

Jede der sieben Qualitätsanforderungen (`TDS-QA-01` bis `TDS-QA-07`) hat eine laufende, belegte Messung, und jede Festlegung der Spezifikation (`SPEC-*`) ist durch Tests belegt; `make doc-complete` meldet keine Waise mehr.

## 2. Trigger (Welle startet)


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün (Gerüst mit Docker, detekt und Build steht).

## 3. Closure-Trigger (Welle schließt)


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` und `make doc-complete` grün; die Messungen der sieben Anforderungen laufen im Aggregator.
- Closure-Notiz in `welle-qualitaet-grundlage-results.md`.

## 4. Slices in dieser Welle


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-qa-dotnet-referenztest-geruest | .NET-Referenztest-Gerüst in Docker ohne Server (Restore-Sperre, Smoke-Test) | [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| slice-tds8-verdrahtung | TDS-8.0-Pfad ab dem ersten Byte im Serverprozess verdrahten (Voraussetzung der TDS-8.0-Messung von `slice-qa-kompatibilitaet`) | [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) |
| slice-qa-kompatibilitaet | Kompatibilität: Ende-zu-Ende-Szenarien mit echtem .NET-Client | [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität) |
| slice-qa-stabilitaet | Stabilität: ungültige und getrennte Verbindungen | [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität) |
| slice-qa-sicherheit | Sicherheit: Zugangsdaten im Log und TLS-Modus | [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) |
| slice-qa-anmeldedrosselung | Drosselung wiederholter Anmeldeversuche entscheiden (Folge-Slice von [CO-003](../carveouts/done/CO-003-anmeldedrosselung-fehlt.md)) | [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) |
| slice-qa-performance | Performance: Referenz-Workload und Messung | [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance) |
| slice-qa-wartbarkeit | Wartbarkeit: Modulgrenzen und Kompatibilitätsmatrix | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| slice-qa-reproduzierbarkeit | Reproduzierbarkeit: Clean-Build und Tests in CI | [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| slice-qa-vergleichslauf-d | Vergleichslauf D: Referenzabgleich gegen einen echten SQL Server | [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität) |
| slice-qa-vergleichslauf-d-messungen | Vergleichslauf D, Messreihe am Wire: Antwort-Tokens, Fehler in `sp_executesql`, Zurücksetzen und Abbruch am echten SQL Server (Zerlegung von `slice-qa-vergleichslauf-d`) | [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) |
| slice-qa-dotnet-lieferkette-haerten | .NET-Restore: Signaturprüfung und Schwachstellenprüfung entscheiden | [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| slice-qa-synchronitaet | Protokoll-Synchronität nach Fehler und Abbruch | [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) |
| slice-e2e-geruest | Produkt-E2E-Skript bündelt die vorhandenen Läufe; `make e2e-abdeckung` erzeugt die Sicht | [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| slice-e2e-backend-ausserhalb-modul | E2E-Stufe für ein eigenes Backend und seinen Fehlpfad gegen den Serverprozess (Folge-Slice von `slice-e2e-geruest`) | [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../spec/lastenheft.md#tds-rb-04--externes-backend) |
| slice-qa-spec-abdeckung | Spezifikations-Abdeckungsmatrix: jede Festlegung durch Tests belegt | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| slice-spec-abdeckung-liest-csharp | `make spec-abdeckung` liest `@spec`-Tags und den Bericht der C#-Tests des Referenzclients | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md) |
| slice-kompatibilitaetsmatrix-erzeugen | Kompatibilitätsmatrix als erzeugte Sicht auf die Spezifikation (Erzeuger, E2E-Ergebnisdatei) | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| slice-kompatibilitaetsmatrix-migration | Spec-Nachzug, Tags und Umstellung auf die erzeugte Sicht | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| slice-kompatibilitaetsmatrix-gate | Erzeugte Kompatibilitätsmatrix als Gate der Klasse tests | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| slice-qa-passwortvergleich-gemischte-liste | Passwortvergleich in konstanter Zeit bei gemischten Benutzerlisten angleichen und messen | [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung) |
| slice-qa-test-stage-run-kopplung | `RUN`-Zeile der Gate-Stage `test` an die Basis `test-quellen` koppeln (Gleichlauf mit `test-bericht`) | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |

## 5. Abhängigkeiten


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: keine weitere Welle.
- Wird blockiert von: keiner Welle; die Entscheidungen, auf die einzelne Slices warteten ([ADR-0007](../adr/0007-fehler-und-attention.md), [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0009](../adr/0009-authentifizierungsmodus.md)), stehen auf `Accepted` und waren kein Welle-Inhalt. [ADR-0010](../adr/0010-detekt-pre-release.md) (`Accepted`) ist Bezug von `slice-qa-wartbarkeit` und `slice-qa-reproduzierbarkeit` und gehört damit zu dieser Welle; sie hat keinen eigenen Entscheidungs-Slice.

- Startbarkeit nach `slice-qa-sicherheit`: Der Start-Trigger von [slice-qa-anmeldedrosselung](done/slice-qa-anmeldedrosselung.md) ist erfüllt, offen ist allein der Verantwortliche im Kopf; die Welle wartet auf ihn, weil [CO-003](../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) erst mit ihm und einer `Accepted`-ADR zur Drosselung oder zum Verzicht aufgelöst ist. Die Welle schließt außerdem erst, wenn die übrigen Slices der Tabelle in `done/` liegen.

## 6. Out-of-Scope für diese Welle


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Funktionale Anforderungen (`TDS-FA-*`) — sie haben eigene Slices in einer späteren Welle; diese Welle belegt nur die Qualitätsseite.
- Optimierung bei verfehlten Grenzwerten — ein Slice je Befund, nach der Messung.

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-qualitaet-grundlage-results.md`
Zähler: `observations/`
