# Welle welle-betrieb-diagnose: Betrieb und Diagnose: strukturiertes Log, Zustandsereignisse, Startdiagnose, Metriken und Spans

**Lifecycle:** Diese Datei entsteht bei der **Eröffnung** der Welle und liegt
flach unter `docs/plan/planning/`; bei Closure wandert sie per `git mv` nach
`done/` (neben ihre `welle-<Kennung>-results.md`). Der Zustand ist die
Verzeichnis-Position — kein Status-Feld. **Geplante Wellen bekommen noch keine
Datei:** Sie stehen in der Roadmap unter *Nächste Wellen* und nirgends sonst —
zwei Positionen, nicht drei.

**Zielmeilenstein:** kein Meilenstein-Bezug.

**Verantwortlich:** pt9912. **Datum:** 2026-10-30.

---

## 1. Welle-Ziel

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht.

Ein Betreiber konfiguriert den Server, beobachtet ihn über strukturierte Logzeilen mit Korrelations-IDs und diagnostiziert seinen Zustand über Metriken und Spans; Passwörter, Prüfwerte, Parameterwerte und SQL-Text erscheinen in keiner Zeile, und ein Startfehler nennt die Ursache verständlich ([TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). Die Messpunkte für Protokollschicht und Backend sind der Eingang für [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance). Die Aussage steht am Serverprozess mit Rohbytes-Client und Sammler-Adapter, nicht am Referenzclient und nicht unter Last.

**Grenzen zu den Nachbarn (nicht doppeln).** Die Startprobe (SPEC-043) und die Diagnose für einen belegten Port sind Code von [welle-transport-tls](welle-transport-tls.md); diese Welle leitet ihre Meldung über die Log-Schnittstelle, ohne sie neu zu formulieren. Das geordnete Beenden (SPEC-044) ist Verhalten von [welle-fehler-abbruch](welle-fehler-abbruch.md); hier erscheinen nur Beginn und Ende als Ereignis. Die Loginspektion über das Gesamtsystem bleibt bei [slice-qa-sicherheit](open/slice-qa-sicherheit.md); diese Welle liefert Sammler und Redaktionstests je Ereignisart.

**Register-Sichtung (Eröffnung, Schritt 2).** `observations/` durchgegangen: Treffer führen die Slices in §8. `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (3×, geplant bei [slice-qa-sicherheit](open/slice-qa-sicherheit.md)) erreicht keine neue Zahl mit dieser Welle; keine Beobachtung erreicht mit ihr 3×, die Welle schließt daher keine Beobachtung ein.

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- Die Wellen [welle-transport-tls](welle-transport-tls.md) und [welle-handshake-login](welle-handshake-login.md) liegen in `done/`: Listener, Startprobe und der minimale Log-Anschluss der Anmeldung, den die Welle ablöst, stehen auf `main`.
- Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (für [slice-betrieb-metriken-tracing](open/slice-betrieb-metriken-tracing.md) die Wellen [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md), [welle-backend-streaming](welle-backend-streaming.md) und [welle-fehler-abbruch](welle-fehler-abbruch.md)): sie verzögern einen Slice, nicht den Start der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich `make test` und des Prozesstests aus [slice-betrieb-zustandsereignisse](open/slice-betrieb-zustandsereignisse.md).
- Das Mehr gegenüber den Slice-DoDs: Ein Rohbytes-Lauf über den Serverprozess (Start, Login angenommen und abgelehnt, SQL_BATCH, Fehler, Cancel, Beenden) erzeugt Logzeilen und Telemetrie; ein Inspektionstest über alle erfassten Zeilen findet weder Passwort noch Prüfwert noch Parameterwert noch SQL-Text, jede Zeile trägt die Pflichtfelder, soweit vorhanden, und die drei Spans liegen mit ihren Pflicht-Attributen vor. Einzelne Slices belegen nur ihren Teil.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* und *Festlegung*, nennt für [slice-qa-sicherheit](open/slice-qa-sicherheit.md) (Sammler) und [slice-qa-performance](open/slice-qa-performance.md) (Telemetrie als Dauer je Schicht) den Anschluss, und ordnet jeden offenen Punkt der ADR aus [slice-adr-observability](open/slice-adr-observability.md) einem Träger zu; die Nutzerentscheidungen (Logformat, Ort der Schnittstelle, Kennungsfeld, Schalter für Parameterwerte, `client_profile`, Zugang zu Metriken) sind getroffen und in den Slice-Notizen festgehalten.
- Closure-Notiz in `welle-betrieb-diagnose-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-adr-observability | Entscheidung: Logformat, Observability-Schnittstelle, Zugang zu Metriken und Spans; neue ADR, Fortschreibung Spezifikation §4 und §5 | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0003](../adr/0003-transportframework.md), [ADR-0009](../adr/0009-authentifizierungsmodus.md) |
| slice-betrieb-log-schnittstelle | Log-Schnittstelle mit Pflichtfeldern, Adapter, Konfiguration, Ablösung der minimalen Log-Anschlüsse, Redaktionstests | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0009](../adr/0009-authentifizierungsmodus.md) |
| slice-betrieb-zustandsereignisse | Zustandsereignisse, Startdiagnose am Prozess, Konfigurationsabdeckung der Limits | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../adr/0002-tls-und-verschluesselung.md) |
| slice-betrieb-metriken-tracing | Spans tds.connection, tds.query, backend.execute und Metriken nach Spezifikation §5 | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance) |

Reihenfolge: Die Entscheidung steht vor dem Code (Format, Schnittstelle und Kennungsfeld sind offen). Die Log-Schnittstelle steht vor den Zustandsereignissen (sie rufen sie) und vor den Metriken (die Spans teilen IDs und Kontext). Die Zustandsereignisse brauchen Listener und Beenden, die Metriken schließen die Welle, weil sie Anfrage, Backend und Abbruch voraussetzen.

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: [slice-qa-performance](open/slice-qa-performance.md) (Start-Trigger: Telemetrie liefert Dauer je Schicht, SPEC-017 bis SPEC-019) und damit die Closure von [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md); [slice-qa-sicherheit](open/slice-qa-sicherheit.md) (Loginspektion nutzt den Sammler-Adapter und die Redaktionstests).
- Wird blockiert von: [welle-transport-tls](welle-transport-tls.md) und [welle-handshake-login](welle-handshake-login.md) (Start-Trigger); [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md), [welle-backend-streaming](welle-backend-streaming.md) und [welle-fehler-abbruch](welle-fehler-abbruch.md) (nur [slice-betrieb-metriken-tracing](open/slice-betrieb-metriken-tracing.md)); [slice-abbruch-ressourcen-shutdown](open/slice-abbruch-ressourcen-shutdown.md) (nur [slice-betrieb-zustandsereignisse](open/slice-betrieb-zustandsereignisse.md)).
- Berührt, blockiert aber nicht: [welle-simulations-backend](welle-simulations-backend.md) — das Simulations-Backend ruft keine Observability (Importregel `ForbiddenImport/simulation`); die Telemetrie des Backend-Aufrufs sitzt im Backend-Adapter des Protokollkerns.

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Startprobe der TLS-Konfiguration, Diagnose für einen belegten Port als Code, Listener: [welle-transport-tls](welle-transport-tls.md) ([slice-transport-listener-lebenszyklus](in-progress/slice-transport-listener-lebenszyklus.md)).
- Verhalten des geordneten Beendens (SPEC-044, `SHUTDOWN_GRACE`, `TDS_SHUTDOWN`) und Ressourcenfreigabe: [slice-abbruch-ressourcen-shutdown](open/slice-abbruch-ressourcen-shutdown.md) ([welle-fehler-abbruch](welle-fehler-abbruch.md)).
- Semantik und Durchsetzung von `QUERY_TIMEOUT` und `CANCEL_TIMEOUT`, Zuordnung der Fehlercodes: [slice-attention-cancel-timeout](open/slice-attention-cancel-timeout.md), [slice-adr-backend-grenzen](open/slice-adr-backend-grenzen.md), [slice-fehler-mapping](in-progress/slice-fehler-mapping.md).
- Loginspektion über das Gesamtsystem, Zeitgleichheit des Passwortvergleichs, Drosselung wiederholter Anmeldeversuche: [slice-qa-sicherheit](open/slice-qa-sicherheit.md).
- Lastmessung, Referenz-Workload, Overhead der Telemetrie und Grenzwerte: [slice-qa-performance](open/slice-qa-performance.md).
- Speicherkopien von Geheimnissen in der JVM: `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (kein Träger als Datei).
- Rotation, Aggregation, Versand der Logs, Collector, Dashboards, Alarmierung (Betreiber, ARC-014), Verwaltungsoberfläche ([TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), Out-of-Scope) und das Neuladen der Konfiguration zur Laufzeit.
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-betrieb-diagnose-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
