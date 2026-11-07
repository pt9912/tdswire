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

Ein Betreiber konfiguriert den Server, beobachtet ihn über strukturierte Logzeilen mit Korrelations-IDs und diagnostiziert seinen Zustand über Metriken und Spans; Passwörter, Prüfwerte, Parameterwerte und SQL-Text erscheinen in keiner Zeile, und ein Startfehler nennt die Ursache verständlich ([TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). Die Messpunkte für Protokollschicht und Backend sind der Eingang für [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance). Die Aussage steht am Testserver-Prozess (der Server als Prozess besteht nur als Testserver, [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md)) mit Rohbytes-Client und den JSON-Zeilen seines Adapters und an der Senke der Bibliothek mit dem Sammler, nicht am Referenzclient und nicht unter Last. Nach [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, 2026-11-01) legt die Bibliothek Ereignismodell, Senke und einen öffentlichen Parameter von `TdsServer.start` fest; das Format (JSON-Zeile), die Ebene (`LOG_LEVEL`) und die Warteschlange sind Sache des Testservers.

**Grenzen zu den Nachbarn (nicht doppeln).** Die Startprobe (SPEC-043) und die Diagnose für einen belegten Port sind Code von [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md); diese Welle leitet ihre Meldung über die Log-Schnittstelle, ohne sie neu zu formulieren. Das geordnete Beenden (SPEC-044) ist Verhalten von [welle-fehler-abbruch](done/welle-fehler-abbruch.md); hier erscheinen nur Beginn und Ende als Ereignis. Die Redaktion im Modell trägt [slice-betrieb-log-schnittstelle](done/slice-betrieb-log-schnittstelle.md), die Loginspektion über das Gesamtsystem am laufenden Prozess [slice-betrieb-log-adapter-testserver](done/slice-betrieb-log-adapter-testserver.md); [slice-qa-sicherheit](done/slice-qa-sicherheit.md) prüft das minimale Log von heute. Diese Welle liefert Sammler, Redaktionstests je Ereignisart und den Inspektionstest. Die Umbauten des Produktschnitts (Testserver, Jar ohne `Main-Class`) liegen in [welle-produktschnitt](welle-produktschnitt.md); von ihnen hängt hier nur der Anfang der Log-Slices ab (§5).

**Register-Sichtung (Eröffnung, Schritt 2).** `observations/` durchgegangen: Treffer führen die Slices in §8. `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (3×, geplant bei [slice-qa-sicherheit](done/slice-qa-sicherheit.md)) erreicht keine neue Zahl mit dieser Welle; keine Beobachtung erreicht mit ihr 3×, die Welle schließt daher keine Beobachtung ein.

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- Die Wellen [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md) und [welle-handshake-login](done/welle-handshake-login/welle-handshake-login.md) liegen in `done/`: Listener, Startprobe und der minimale Log-Anschluss der Anmeldung, den die Welle ablöst, stehen auf `main`.
- Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (für [slice-betrieb-log-schnittstelle](done/slice-betrieb-log-schnittstelle.md) und [slice-betrieb-log-adapter-testserver](done/slice-betrieb-log-adapter-testserver.md) der Slice `slice-testserver-herausloesen` aus [welle-produktschnitt](welle-produktschnitt.md), für [slice-betrieb-metriken-tracing](open/slice-betrieb-metriken-tracing.md) die Wellen [welle-anfrageverarbeitung](done/welle-anfrageverarbeitung.md), [welle-backend-streaming](done/welle-backend-streaming/welle-backend-streaming.md) und [welle-fehler-abbruch](done/welle-fehler-abbruch.md)): sie verzögern einen Slice, nicht den Start der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich `make test` und des Prozesstests aus [slice-betrieb-zustandsereignisse](in-progress/slice-betrieb-zustandsereignisse.md).
- Das Mehr gegenüber den Slice-DoDs: Ein Rohbytes-Lauf über den Testserver-Prozess (Start, Login angenommen und abgelehnt, SQL_BATCH, Fehler, Cancel, Beenden) erzeugt Logzeilen (JSON-Zeilen des Adapters) und Telemetrie (Ereignisse `metrics` und Span-Ende); ein Inspektionstest über alle erfassten Zeilen findet weder Passwort noch Prüfwert noch Parameterwert noch SQL-Text, jede Zeile trägt die Pflichtfelder, soweit vorhanden, und die drei Spans liegen mit ihren Pflicht-Attributen vor. Einzelne Slices belegen nur ihren Teil.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* und *Festlegung*, nennt für [slice-qa-sicherheit](done/slice-qa-sicherheit.md) (Sammler) und [slice-qa-performance](open/slice-qa-performance.md) (Telemetrie als Dauer je Schicht) den Anschluss, und ordnet jeden offenen Punkt der ADR aus [slice-adr-observability](done/slice-adr-observability.md) ((a) bis (h) der [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md)) einem Träger zu; die Nutzerentscheidungen (Logformat als Adapter, Ort der Schnittstelle, Kennungsfeld, Schalter für Parameterwerte, `client_profile`, Zugang zu Metriken) sind mit der Annahme der ADR am 2026-11-01 getroffen und in den Slice-Notizen festgehalten.
- Closure-Notiz in `welle-betrieb-diagnose-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-adr-observability | Entscheidung: Ereignismodell, Log-Schnittstelle, Zugang zu Metriken und Spans; neue ADR mit acht Fragen (Spezifikation §4 und §5: [slice-spec-nachzug-telemetrie](done/slice-spec-nachzug-telemetrie.md)) | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0003](../adr/0003-transportframework.md), [ADR-0009](../adr/0009-authentifizierungsmodus.md) |
| [slice-spec-nachzug-telemetrie](done/slice-spec-nachzug-telemetrie.md) | Spezifikation nachziehen: Pflicht-Logfelder, Zugang zu Metriken und Spans, Eingänge des Testservers (Folgepflicht 1 der [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md); zweiter Spec-Nachzug neben dem des Produktschnitts) | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) |
| slice-betrieb-log-schnittstelle | Ereignismodell, Senke, Parameter von `start` und Sammler in der Bibliothek, Ablösung von `ServerLog`, Sperren für `println` und `simulation`, Redaktionstests, Einbettungstest | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0009](../adr/0009-authentifizierungsmodus.md), [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) |
| [slice-betrieb-log-adapter-testserver](done/slice-betrieb-log-adapter-testserver.md) | JSON-Adapter, `LOG_LEVEL`, Warteschlange und Regel zum Prozessende im Testserver, Inspektion am laufenden Prozess, Umstellung der Skript-Muster | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0025](../adr/0025-produktschnitt-bibliothek-und-testserver.md) |
| [slice-betrieb-telemetrie-verdrahtung-belegen](done/slice-betrieb-telemetrie-verdrahtung-belegen.md) | Verdrahtungstest für `ACCEPT_FAILED` und `CONNECTION_FAILED`, Rot-Fälle der Ausgabe-Sperre je Package, Lücke `Logger.log` | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) |
| [slice-listener-annahmeschleife-bei-erschoepften-dateien](open/slice-listener-annahmeschleife-bei-erschoepften-dateien.md) | Annahmeschleife läuft nach erschöpftem Limit offener Dateien weiter oder meldet (Befund aus dem Review von slice-betrieb-telemetrie-verdrahtung-belegen) | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) |
| slice-betrieb-zustandsereignisse | Zustandsereignisse, Startdiagnose am Prozess, Konfigurationsabdeckung der Limits | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../adr/0002-tls-und-verschluesselung.md) |
| slice-betrieb-metriken-tracing | Spans tds.connection, tds.query, backend.execute und Metriken nach Spezifikation §5 | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance) |
| [slice-oeffentliche-flaeche-verkleinern-und-belegen](done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) | Öffentliche Fläche auf das Nötige verkleinern (Nutzerentscheidung 2026-11-05), jeden zugesagten Namen der `SPEC-059` über die öffentliche Fläche belegen, Sensor | [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-RB-04](../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0021](../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) |

Reihenfolge: Die Entscheidung ist mit der Annahme der [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md) getroffen (der Slice `slice-adr-observability` schließt davon unabhängig). Dann folgt die Spezifikation (`slice-spec-nachzug-telemetrie`, nach `slice-spec-nachzug-produktschnitt` aus [welle-produktschnitt](welle-produktschnitt.md)), dann der Code: `slice-testserver-herausloesen` (aus [welle-produktschnitt](welle-produktschnitt.md)) → `slice-betrieb-log-schnittstelle` (Bibliothek) → `slice-betrieb-log-adapter-testserver` (Testserver, braucht Senke und Testserver) → `slice-betrieb-zustandsereignisse` (braucht Senke, Adapter, Listener und Beenden) und `slice-betrieb-metriken-tracing` (braucht Senke und Adapter, schließt die Welle, weil es Anfrage, Backend und Abbruch voraussetzt). `slice-betrieb-telemetrie-verdrahtung-belegen` → `slice-listener-annahmeschleife-bei-erschoepften-dateien` (der Test der Kind-JVM muss auf `main` stehen). Jede Kante steht als Start-Trigger in §4 des Folge-Slice. Die Log-Schnittstelle steht vor den Zustandsereignissen (sie rufen sie) und vor den Metriken (die Spans teilen IDs und Kontext).

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: [slice-qa-performance](open/slice-qa-performance.md) (Start-Trigger: Telemetrie liefert Dauer je Schicht, SPEC-017 bis SPEC-019) und damit die Closure von [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md).
- Wird blockiert von: [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md) und [welle-handshake-login](done/welle-handshake-login/welle-handshake-login.md) (Start-Trigger); [welle-produktschnitt](welle-produktschnitt.md) mit den Slices `slice-spec-nachzug-produktschnitt` (vor `slice-spec-nachzug-telemetrie`) und `slice-testserver-herausloesen` (vor `slice-betrieb-log-schnittstelle` und `slice-betrieb-log-adapter-testserver`); damit hängt der Anfang der Code-Slices dieser Welle an der Nennung des Starts von [welle-produktschnitt](welle-produktschnitt.md) durch den Nutzer (Nutzerentscheidung 2026-11-01); [welle-anfrageverarbeitung](done/welle-anfrageverarbeitung.md), [welle-backend-streaming](done/welle-backend-streaming/welle-backend-streaming.md) und [welle-fehler-abbruch](done/welle-fehler-abbruch.md) (nur [slice-betrieb-metriken-tracing](open/slice-betrieb-metriken-tracing.md)); [slice-abbruch-ressourcen-shutdown](done/slice-abbruch-ressourcen-shutdown.md) (nur [slice-betrieb-zustandsereignisse](in-progress/slice-betrieb-zustandsereignisse.md)).
- Berührt, blockiert aber nicht: [welle-simulations-backend](done/welle-simulations-backend/welle-simulations-backend.md) — das Simulations-Backend ruft keine Observability (Importregel `ForbiddenImport/simulation`); die Telemetrie des Backend-Aufrufs sitzt im Backend-Adapter des Protokollkerns.

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Startprobe der TLS-Konfiguration, Diagnose für einen belegten Port als Code, Listener: [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md) ([slice-transport-listener-lebenszyklus](done/welle-transport-tls/slice-transport-listener-lebenszyklus.md)).
- Verhalten des geordneten Beendens (SPEC-044, `SHUTDOWN_GRACE`, `TDS_SHUTDOWN`) und Ressourcenfreigabe: [slice-abbruch-ressourcen-shutdown](done/slice-abbruch-ressourcen-shutdown.md) ([welle-fehler-abbruch](done/welle-fehler-abbruch.md)).
- Semantik und Durchsetzung von `QUERY_TIMEOUT` und `CANCEL_TIMEOUT`, Zuordnung der Fehlercodes: [slice-attention-cancel-timeout](done/slice-attention-cancel-timeout.md), [slice-adr-backend-grenzen](done/welle-backend-streaming/slice-adr-backend-grenzen.md), [slice-fehler-mapping](done/slice-fehler-mapping.md).
- Zeitgleichheit des Passwortvergleichs und Loginspektion des minimalen Logs: [slice-qa-sicherheit](done/slice-qa-sicherheit.md); Drosselung wiederholter Anmeldeversuche: [slice-qa-anmeldedrosselung](done/slice-qa-anmeldedrosselung.md) ([CO-003](../carveouts/done/CO-003-anmeldedrosselung-fehlt.md)).
- Lastmessung, Referenz-Workload, Overhead der Telemetrie und Grenzwerte: [slice-qa-performance](open/slice-qa-performance.md).
- Speicherkopien von Geheimnissen in der JVM: `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (kein Träger als Datei).
- Rotation, Aggregation, Versand der Logs, Collector, Dashboards, Alarmierung (Betreiber, ARC-014), Verwaltungsoberfläche ([TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), Out-of-Scope) und das Neuladen der Konfiguration zur Laufzeit.
- Spec-Folgepflichten und Umsetzung der Entscheidungen aus [ADR-0027](../adr/0027-abbruch-und-zuruecksetzen.md) (Cancel-Antwort, Cancel und Timeout als Fehler, ATTENTION im Handshake, Schließen nach `TDS_SHUTDOWN`, `RESETCONNECTION`): Abbruch- und Reset-Slices in [welle-fehler-abbruch](done/welle-fehler-abbruch.md), die ein anderer Planner anlegt; die Zähler „Abbrüche“ und „Protokollfehler“ folgen deren Begriffen.
- Adapter für JMX, einen Text-Endpunkt oder eine Telemetrie-Bibliothek, ein fertiges Format der Bibliothek und eine Warteschlange in der Bibliothek: Re-Evaluierungs-Trigger der [ADR-0020](../adr/0020-logformat-und-observability-schnittstelle.md), je eigene Entscheidung.
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-betrieb-diagnose-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
