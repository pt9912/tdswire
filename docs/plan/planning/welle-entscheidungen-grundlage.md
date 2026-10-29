# Welle welle-entscheidungen-grundlage: Offene Architekturentscheidungen treffen

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

Die neun offenen ADRs ([ADR-0001](../adr/0001-tds-version-und-clientprofil.md) bis [ADR-0009](../adr/0009-authentifizierungsmodus.md)) sind mit je einem Nachweis entschieden und `Accepted`, sodass die funktionalen Slices (`TDS-FA-*`) auf festen Entscheidungen aufsetzen.

## 2. Trigger (Welle startet)


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün (Gerüst mit Docker, detekt und Build steht).
- Modus-Deklaration in `harness/conventions.md` ausgefüllt (Greenfield).

## 3. Closure-Trigger (Welle schließt)


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- Alle neun ADRs stehen auf `Accepted` (oder sind durch eine neue ADR ersetzt) und der ADR-Index trägt den Stand.
- `make gates` grün.
- Closure-Notiz in `welle-entscheidungen-grundlage-results.md`.

## 4. Slices in dieser Welle


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-adr-clientprofil | Entscheidung: TDS-Version und .NET-Kompatibilitätsprofil | [ADR-0001](../adr/0001-tds-version-und-clientprofil.md) |
| slice-adr-tls | Entscheidung: TLS-Strategie und Verschlüsselungsmodi | [ADR-0002](../adr/0002-tls-und-verschluesselung.md) |
| slice-adr-transport | Entscheidung: Transportframework und Netzwerk-Laufzeit | [ADR-0003](../adr/0003-transportframework.md) |
| slice-adr-session-zustand | Entscheidung: Session-Zustandsautomat und Protokollverarbeitung | [ADR-0004](../adr/0004-session-zustandsautomat.md) |
| slice-adr-rpc | Entscheidung: RPC-Unterstützung und Prepared Statements | [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) |
| slice-adr-backend-streaming | Entscheidung: Backend-Schnittstelle und Resultset-Streaming | [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |
| slice-adr-fehler-attention | Entscheidung: Fehlerbehandlung und ATTENTION-Semantik | [ADR-0007](../adr/0007-fehler-und-attention.md) |
| slice-adr-kompatibilitaetstests | Entscheidung: Referenzclient und .NET-Kompatibilitätstests | [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md) |
| slice-adr-authentifizierung | Entscheidung: Authentifizierungsmodus und Authentifizierungsadapter | [ADR-0009](../adr/0009-authentifizierungsmodus.md) |

## 5. Abhängigkeiten


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: die Slices der Welle [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md), die auf [ADR-0007](../adr/0007-fehler-und-attention.md), [ADR-0008](../adr/0008-dotnet-kompatibilitaetstests.md) und [ADR-0009](../adr/0009-authentifizierungsmodus.md) warten, sowie die funktionalen Wellen.
- Wird blockiert von: keiner Welle.

## 6. Out-of-Scope für diese Welle


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Produktionscode der gewählten Optionen — er entsteht in den funktionalen Wellen.
- [ADR-0010](../adr/0010-detekt-pre-release.md) — die Annahme hängt am Motiv des Autors, nicht an einem Spike.
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-entscheidungen-grundlage-results.md`
Zähler: `observations/`
