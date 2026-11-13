# Welle welle-betrieb-diagnose — Betrieb und Diagnose: strukturiertes Log, Zustandsereignisse, Startdiagnose, Metriken und Spans — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-betrieb-diagnose
**Abschluss:** 2026-11-13
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

Alle zwölf Slices mit `Welle: welle-betrieb-diagnose` liegen in `done/` (am Dateisystem geprüft: kein Slice der Welle in `open/`, `next/` oder `in-progress/`; jeder Slice aus §4 der Welle-Datei besteht dort). Das *Mehr* gegenüber den Slice-DoDs, Closure-Trigger 3 der Welle-Datei, trägt `slice-betrieb-prozesslauf-logzeilen-inspektion`: ein Rohbytes-Lauf über den Testserver-Prozess und ein Inspektionstest über alle Zeilen dieses Laufs. Die Aussage der Welle steht damit am Prozess und an der Senke der Bibliothek, nicht am Referenzclient und nicht unter Last ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)).

- **Entscheidung und Spezifikation:** `slice-adr-observability` ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), Ereignismodell, Senke, Zugang zu Metriken und Spans; das Format ist ein Adapter im Testserver) und `slice-spec-nachzug-telemetrie` (Abschnitt 4 und 5 der Spezifikation: Pflichtfelder `ts`, `level`, `principal`, Eingänge `LOG_LEVEL` und `METRICS_INTERVAL_SECONDS`). Aus dem Review der Annahme entstand [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) (Parameter von `TdsServer.start`).
- **Bibliothek:** `slice-betrieb-log-schnittstelle` (Modell `TelemetryEvent`, Senke `TelemetrySink`, Parameter `telemetry`, Ablösung von `ServerLog`, Ausgabe- und Importsperren, Redaktionstests je Ereignisart, Einbettungstest), `slice-betrieb-telemetrie-verdrahtung-belegen` (`ACCEPT_FAILED` und `CONNECTION_FAILED` am Server, Rot-Fall der Ausgabe-Sperre je Package, `Logger.log` gesperrt), `slice-listener-annahmeschleife-bei-erschoepften-dateien` (Annahmeschleife übersteht das erschöpfte Limit offener Dateien, [ADR-0043](../../adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) Weg A), `slice-betrieb-zustandsereignisse` (vier neue Arten, `SERVER_STARTED` mit Adresse, Startdiagnose am Prozess, [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md)), `slice-betrieb-metriken-tracing` (Spans `tds.connection`, `tds.query`, `backend.execute`, Metriken mit Schnappschuss, `SPEC-017` bis `SPEC-019`).
- **Testserver:** `slice-betrieb-log-adapter-testserver` (JSON-Adapter, `LOG_LEVEL`, Warteschlange, Regel zum Prozessende, Inspektion am Prozess, Skript-Muster mit Selbsttest).
- **Fläche und Form der Einstellungen:** `slice-oeffentliche-flaeche-verkleinern-und-belegen` (83 auf 61 öffentliche Typnamen, `make flaeche`), `slice-einstellungen-in-typen-buendeln` (`RequestSettings` in `RequestTimeouts`, `RequestLimits`, `ObservationSettings`; inkompatible Änderung vor der ersten Veröffentlichung, [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md)), `slice-request-failed-mit-request-id` (`requestId` im Ereignis `REQUEST_FAILED`).
- **Das Mehr:** `slice-betrieb-prozesslauf-logzeilen-inspektion` (`TestserverLaufInspektionTest`: Start, Login abgelehnt und angenommen, Massenladen, SQL_BATCH, parametrisierte Anfrage, Fehler, Cancel, SIGTERM; Pflichtfelder je Art, Menge der erlaubten Felder, weder Passwort noch Prüfwert noch Parameterwert noch SQL-Text in einer Zeile, drei Spans mit Pflicht-Attributen). Kein Produktcode. Reihenfolge am Ende der Welle: `slice-request-failed-mit-request-id` (dessen `request_id` im Fehlerereignis der Lauf prüft) vor `slice-betrieb-prozesslauf-logzeilen-inspektion`, beide am 2026-11-13 geschlossen.

### Die Aussagen der Welle: gemessen und Festlegung

Maßstab für beide Spalten: [AGENTS.md](../../../../AGENTS.md) §3.8 (eine Aussage trägt so weit wie ihr Beleg). *Gemessen* heißt: ein Test, eine Mutation oder ein Lauf, der in den §7 der Slices oder im Verifikationsbericht steht; Einzelmessungen gelten an ihrem Host und ihrer Last. *Festlegung* heißt: Setzung oder Zuschnitt, durch Text (und oft durch einen Test auf die Abbildung) gehalten, aber nicht gemessen.

| Gegenstand | gemessen | Festlegung (nicht gemessen) |
|---|---|---|
| Lauf am Serverprozess | Ein Lauf über `main` des Testservers (Login abgelehnt und angenommen, Massenladen, SQL_BATCH, parametrisierte Anfrage, Fehler, Cancel mit `DONE[ATTN]`, SIGTERM); 19 Anfragen, `requests_total` 19, `backend_active` 1 bei offener Anfrage; elf Mutationen, zehn rot an der benannten Behauptung, (e) grün wie gefordert (`TestserverLaufInspektionTest`, Verifikation `4de389a1`) | Datenmenge gleich vier mal die Summe der Puffergrenzen, Grenze 128 MiB, Heap 512 MiB, `QUERY_TIMEOUT` 3600 als Vorsorge; „14 Ladeschritte“ ist ein Wert dieses Hosts |
| Redaktion | Kein Feld für Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad, Ausnahmetext im Modell (`TelemetryRedactionTest`, Mutation V-M2 rot); die Zeilen des Prozesslaufs enthalten keinen bekannten Wert in den Formen Ganzes, Marke, Salz und Hash als Base64, Base64URL, Hex (Mutationen (f), (g), (k) rot); wirksame Konfiguration aus der Bauart des Wertobjekts ([ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md)) | Kürzung auf 256 Zeichen (Setzung der ADR); Grenze der Suche: Teilstücke, auf mehrere Felder verteilte Werte und Iterationen bleiben unsichtbar |
| Pflichtfelder und Felder je Art | `LogInspektion` rot bei fehlendem oder unbekanntem Feld (Mutationen (a), (b), (d)) | Menge der Pflichtfelder je Art, Zuordnung Feld zu Art und `outcome`-Werte aus Spezifikation Abschnitt 4 und 5 und `TelemetryJson`; die Kopie in `LogInspektion.kt` hat keinen Sensor gegen `TelemetryJson.PROPERTIES` |
| Spans und Metriken | Ein Span endet genau einmal (Cancel, Disconnect, Frist, Beenden, Ausnahme; Mutationen M1, M4, M6, M8, M10 rot); `duration_ms` in `span_ended` aller drei Spans am Prozess (Mutation (a) rot); Zählung `aborts_total` und `protocol_errors_total` | Menge der `outcome`-Werte, Klassengrenzen der Latenz (1 bis 60000 ms), Perzentil als Obergrenze der Klasse (Nearest-Rank), Bereich 0 bis 86400 s und Voreinstellung 60 des Abstands, `request_kind` `sql_batch` für ein Bulk-Load-Paket |
| Zustandsereignisse und Startdiagnose | Folge Start bis Ende, Marken `HANDLER_RETURNED`, `SHUTDOWN`, `REFUSED_AT_LIMIT`; Startdiagnose am Prozess (belegter Port, unbrauchbarer Speicher, unsichere Konfiguration); die Sperre von `stop` (Mutation „ohne Sperre“ rot) | Ebenen je Art, Zuordnung Ende der Verbindung zu Marke; vier Marken nur als Abbildung geprüft, am Server nicht erzeugt; `MAX_TDS_PACKET_BYTES` als Protokollkonstante 65535 |
| Verdrahtung und Ausfall | `ACCEPT_FAILED` am Limit offener Dateien einer Kind-JVM mit Wiederannehmen (128 Dateien; M1 bis M3 rot); `CONNECTION_FAILED` bei einem `Error` aus der Senke; Ausgabe-Sperre mit 132 Aufruf-Fällen | der Auslöser `Error` als Grenze (Nutzerentscheid 2026-11-06); 128 offene Dateien als Setzung des Tests; Verhalten unter erschöpftem Limit nur für gehaltene, idle Verbindungen |
| Adapter des Testservers | 28 Tests in fünf Dateien (Escaping, Filter nach `LOG_LEVEL`, Überlauf mit `dropped`, `close` leert, hängender Kanal, `writeLast`), Prozesstests in einer Kind-JVM, Skript-Muster mit acht Mutationen | 1024 Zeilen Warteschlange, 2 s Frist von `close`, 250 ms für die letzte Zeile, Schreibweise der Felder, der Name `log_dropped` |
| Form der Fläche | `make flaeche`: 61 Typnamen von `SPEC-059`, Selbsttest mit 14 Fällen; Gruppen der Einstellungen nicht vertauschbar (Compiler, `RequestSettingsTest`) | Mitglieder und ihre Reihenfolge (kein Sensor), Namen `EventKind` und `issueKind`, Felder je Art nur durch Test, Instanztrennung als benannte Grenze statt Feld |
| `requestId` im Fehlerereignis | `REQUEST_FAILED` trägt die Kennung für alle getesteten Auslöser, gleich der im Ende von `tds.query` und `backend.execute`; drei Mutationen rot | Zustandsfeld statt Parameter; der Freigabepfad und der Satz „das Fehler-Mapping läuft nur für die Antwort einer Anfrage“ ruhen auf Code-Lesung |

**Nicht gemessen, aus keiner Closure der Welle:** Rückstau und Kosten des Ereignisbaus unter Last (Träger `slice-qa-performance`); Speicherkopien von Geheimnissen (`BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, kein Träger als Datei); der Prozesslauf auf einem Host mit anderen Puffergrenzen (`BEO-TDS/annahme-ueber-puffer-der-umgebung`); die Reihenfolge von `CONFIG_EFFECTIVE`, `SERVER_STARTED` und einer frühen Verbindung bei festem Port; mehrere Server in einer JVM mit gemeinsamer Senke; `stop` aus der Senke (gemessen, nicht getestet); der Betrieb aus einem Jar.

### Anschluss an `slice-qa-sicherheit` und `slice-qa-performance`

Spalten wie oben: *gemessen* ist, was ein Test oder Lauf trägt, *Festlegung* ist Text ohne Messung.

| Anschluss | gemessen | Festlegung / offen |
|---|---|---|
| `slice-qa-sicherheit` (Sammler, Loginspektion über das Gesamtsystem) | `slice-qa-sicherheit` führte die Inspektion über das Gesamtsystem als „weiter offen“ mit Träger `slice-betrieb-log-schnittstelle`. Der Sammler besteht je Source-Set (`CapturedEvents`, Sammler im Einbettungstest); am Prozess belegen `TestserverLogProcessTest` (kein verbotenes Wort in einer Zeile, Mutation m5 rot) und `TestserverLaufInspektionTest` (ein Lauf über alle Zeilen) die Gesamtinspektion für bekannte Werte und Formen ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)) | Grenze: Teilstücke, verteilte Werte, Iterationen; Speicherkopien (`BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`) und `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (geplant, Träger laut Eintrag `slice-qa-sicherheit`, `slice-qa-anmeldedrosselung`, `slice-betrieb-log-adapter-testserver`, `slice-release-artefakt-lizenz`) unverändert |
| `slice-qa-performance` (Telemetrie als Dauer je Schicht, [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance)) | `duration_ms` ist Pflichtfeld in `span_ended` von `tds.connection`, `tds.query` und `backend.execute`; am Prozess geprüft (Mutation (a) rot); Start-Trigger des Slice („`slice-qa-kompatibilitaet` done und die Telemetrie liefert Dauer je Schicht“) ist erfüllt, der Start ist Sache des Nutzers | Latenzverteilung in Klassen ist Setzung, nicht gemessen; Overhead und Rückstau ungemessen (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, geplant, Träger `slice-qa-performance`); die 3 bis 5 s des Prozesslauf-Tests sind keine Messung |

### Offene Punkte von [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (a) bis (h) und ihre Träger

Geprüft in dieser Closure: (c) bis (g) Wort für Wort gegen §7 des Träger-Slice (und gegen dessen DoD, wo §7 den Punkt nicht nennt); (a), (b), (h) gegen die genannten Dateien.

| Punkt | Träger | Befund |
|---|---|---|
| (a) Lesart von [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) Happy Path | `slice-spec-nachzug-telemetrie` | §7 „Offen (a) der ADR ist überholt“: Lastenheft 0.6.1 bestätigt die Lesart „keine Senke, kein Log“. Erledigt |
| (b) Satz zum Log in [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) | [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) | `Accepted` (Annahme durch den Nutzer, Historie der ADR); `slice-adr-observability` führte die Annahme noch als offenen Nutzerpunkt. Erledigt |
| (c) Format des Zeitstempels über UTC hinaus | `slice-spec-nachzug-telemetrie` | §7 trägt die Festlegung „`ts` UTC mit Format ISO-8601 als Voreinstellung des Adapters“, wie die ADR sie als Voreinstellung nennt. „Über UTC hinaus“ steht in §7 weder entschieden noch als offen; die Voreinstellung trägt, die Erweiterung hat keinen Auftrag |
| (d) `metrics` als Perzentile oder Zähler je Klasse | `slice-betrieb-metriken-tracing` | Die DoD-Zeile nennt Offen (d) und überlässt dem Bau die Wahl „mit Begründung“. §7 hält die Wahl fest (Perzentile p50, p95, p99 als Obergrenze der Latenzklasse, Klassengrenzen Setzung); eine Begründung gegen „Zähler je Klasse“ steht dort nicht ausdrücklich. Die Spezifikation führt `request_latency_p50_ms`, `request_latency_p95_ms`, `request_latency_p99_ms` |
| (e) Quelladresse der Gegenstelle im Ereignis | Anmeldedrosselung (`slice-qa-anmeldedrosselung`, [ADR-0016](../../adr/0016-anmeldedrosselung.md)) | **Kein Träger trägt den Punkt Wort für Wort.** [ADR-0016](../../adr/0016-anmeldedrosselung.md) führt „ob die Drosselung im Log erscheint und mit welcher Adressangabe“ unter „Nicht entschieden“; §7 von `slice-qa-anmeldedrosselung` nennt „die Log-Ausgabe der Drosselung (sie schreibt nichts)“ als nicht gemessen. Das Modell trägt keine Adresse der Gegenstelle (`address` gehört dem Bindefehler). Der Punkt steht jetzt als `BEO-TDS/quelladresse-im-ereignis-ohne-traeger` im Register (1×); die Entscheidung bleibt beim Nutzer |
| (f) Instanztrennung bei mehreren Servern | `slice-betrieb-log-schnittstelle` | §7: „benannte Grenze statt Feld im Ereignis“, mit Re-Evaluierung („ein Embedder mit mehreren Servern, der die Hülle nicht bauen will“). Die ADR sagt, das Ereignis „braucht ein Feld … oder einen Instanzzähler“; §7 nennt die Wahl Planner-Lesart nach Verifikation, die ADR bleibt unverändert. Eine Bestätigung durch den Nutzer steht nicht in §7 |
| (g) Namen und Form von Senke, Parameter, Sammler | `slice-betrieb-log-schnittstelle`, `slice-oeffentliche-flaeche-verkleinern-und-belegen` | §7: Arbeitsnamen `TelemetrySink`, `TelemetryEvent`, `domain.telemetry`, Parameter `telemetry` bleiben; Sammler je Source-Set, kein öffentlicher Typ. `SPEC-059` trägt die Typnamen; `EventKind` und `issueKind` sind Setzung, die Nutzerpunkte (1) bis (3) von `slice-oeffentliche-flaeche-verkleinern-und-belegen` (`SECURITY_WEAKENED` mit `reason`, die Namen, Felder je Art nur durch Test) stehen als offen in der Roadmap |
| (h) Speicherkopien; Gesamtinspektion des Anmeldepfads | `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`; `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`; Gesamtinspektion `slice-betrieb-log-adapter-testserver` | Gesamtinspektion am Prozess gebaut (`TestserverLogProcessTest`, `TestserverLaufInspektionTest`); Speicherkopien bleiben ohne Träger als Datei (§6 der Welle-Datei) |

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- **Mutation je Zusage durch Review und Verifikation.** Sie fand Teile ohne Träger (F-2 der Annahmeschleife: doppelt gebautes `ListenerEvent.AcceptFailed`), äquivalente Mutanten, die Lücke von `Logger.log` und die Bedingung, unter der `ACCEPT_FAILED` überhaupt entsteht (Mutation „ohne `preloadClasses()`“).
- **Redaktion aus der Bauart.** [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) Option D: ein Feld mit Text im Wertobjekt bricht die Übersetzung, nicht erst einen Test. Dazu der Reflexionstest über die Felder.
- **Signale statt Zeit.** Der Prozesslauf wartet auf Zeilen und Bytes; Zeit kommt nur als Schranke vor. Die Wartestellen und ihre Schranken stehen in §7 von `slice-betrieb-prozesslauf-logzeilen-inspektion`.
- **Frischbelege statt Cache.** Die Verifikationen fuhren `make clean-build` und `make e2e` auf dem End-Stand; `make gates` aus dem Layer-Cache gilt als Beleg der Eingänge, nicht als frischer Testlauf (`BEO-TDS/test-stage-cache-verdeckt-wiederholung`).
- **Der Rückschnitt der Aussagen bei der Closure.** Der Verifier des letzten Slice nahm DoD-Zeile, Schlüsselname und Vorprüfung auf ihren Beleg zurück (V-1, V-2, V-4), bevor die Closure sie als geliefert führte.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile möglichst mit der Konsequenz,
die daraus schon gezogen wurde.

- **Das Mehr war nach den Slice-DoDs nicht belegt.** Die getrennten Prozesstests der Slices hatten keinen Lauf mit Cancel, Parameterwert und Fehlerpfad der Zeilen. Entscheid des Nutzers 2026-11-13, Weg B: Code angleichen, Wortlaut des Closure-Triggers bleibt; Folge `slice-betrieb-prozesslauf-logzeilen-inspektion` (Roadmap, Historische Trigger-Verschiebungen).
- **Der Zuschnitt der Welle änderte sich sechsmal** (Drift-Log der Roadmap: 2026-11-01 dreimal, 2026-11-06, 2026-11-07, 2026-11-13): Neuzuschnitt nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), Spec-Nachzug als eigener Slice, Verdrahtungsbeleg, Annahmeschleife, Einstellungen in Typen und `requestId`, der Prozesslauf.
- **Nachbesserung ohne zweiten Review.** Sieben Slices der Welle tragen den Vermerk oder die benannte Grenze (sechs davon führt das Register als Evidence-Datei, `slice-listener-annahmeschleife-bei-erschoepften-dateien` nicht): `slice-betrieb-log-adapter-testserver` (`c30f627e`, `98b65613`, `db98080c`), `slice-betrieb-telemetrie-verdrahtung-belegen` (`77c9a718`, `c7082ca4`, `33b0e7e1`), `slice-listener-annahmeschleife-bei-erschoepften-dateien` (`07cf015b`), `slice-betrieb-zustandsereignisse` (`b7bd6203` bis `b0ac40c5`), `slice-betrieb-metriken-tracing` (`33f67126` bis `85a7c00b` und spätere), `slice-request-failed-mit-request-id` (`50c80578`), `slice-betrieb-prozesslauf-logzeilen-inspektion` (`4de389a1`); `slice-einstellungen-in-typen-buendeln` änderte nach dem Review nur Plan, Spezifikation, Nutzer-Doku und ein KDoc. Die Beobachtung `BEO-TDS/nachbesserung-ohne-zweiten-review` ist verkörpert (Nutzerentscheid 2026-11-07); ihr Ausgang bleibt, wie er ist.
- **Die zweite Fundstelle blieb in jedem Slice nach Schritt 20c stehen** (Retirement-Check unten).
- **`slice-betrieb-log-schnittstelle` steht mit drei offenen DoD-Haken in `done/`:** zwei Liefer-Punkte (Modell mit `request_id`, Redaktionstest je Art mit `AcceptFailed` und `ConnectionFailed`) trugen benannte Abweichungen, die `slice-request-failed-mit-request-id` und `slice-betrieb-telemetrie-verdrahtung-belegen` später lieferten; der dritte Haken („die drei Paarungen“) ist mit der Zeile „Drei Paarungen“ unten erledigt. Die Datei bleibt unverändert (Zeitdokument).

## Trigger-Audit der Welle (Schritt 2)

Regeln dieser Sektion: `close-welle` Schritt 2 — je Klasse eine belegte Feststellung.

- **Carveouts:** 0 offen. `docs/plan/carveouts/` führt nur `done/` ([CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) war schon vor der Welle aufgelöst); kein Slice der Welle hat einen Carveout eröffnet oder verlängert. Kein Gate steht rot in `done/`.
- **Bootstrap-aware Gates:** 0 offen. Die Sensors-Tabelle in `harness/README.md` führt kein Gate mit Reifestufe; die Zeilen mit `bootstrap-aware` und `bootstrap via` sind Vorlagenreste. Neu oder geändert in der Welle: `make flaeche` (Klasse `immer`), die Selbsttests von `make detekt-selbsttest` und `make skript-selbsttest`; keines mit Schwelle oder Stufe.
- **Hard Rules:** keine Hard Rule in `AGENTS.md` §3 trägt einen Auflösungs-Trigger.
- **Entscheidungen (Re-Evaluierungs-Trigger der ADRs, die die Slices der Welle tragen).** Maßstab: Kenntnisstand dieser Closure aus den Closure-Notizen und dem Verifikationsbericht; kein Trigger wurde eigens neu gemessen.

| ADR | Trigger | Befund |
|---|---|---|
| [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) | Betreiber braucht Parameterwerte oder Kennung abgelehnter Anmeldungen; Messung zeigt Belastung des Anfragepfads; Senke hält den Anfragepfad an; Metriken ohne Auswertung von Ereignissen; fertiges Format der Bibliothek; E2E-Befund jenseits des Modells; Änderung der Importregeln oder des Schnitts von Bibliothek und Testserver | nicht eingetreten, mit zwei Lesarten des Planners (widerlegbar): die zwei Importsperren `ForbiddenImport/protocol` und `transport` entstanden mit dem Bau (`slice-betrieb-log-schnittstelle`, Abweichung vom Wortlaut „eine Regelzeile“ dort benannt); [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) machte den Testserver zum Gradle-Modul, die Zuständigkeiten (Modell und Senke in der Bibliothek, Format im Adapter des Testservers) blieben, was `TestserverLaufInspektionTest` am Prozess belegt. Offene Punkte (e), (f), (g) siehe oben |
| [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) | Änderung von [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 1 Punkt 3 | nicht eingetreten; der Parameter `telemetry` steht in `SPEC-059` |
| [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) | Feld mit Textwert; Embedder verlangt Typ statt Abbildung; zweiter Schreiber von `CONFIG_EFFECTIVE`; weiterer Fall Text auf Text im Modell | nicht eingetreten; `slice-einstellungen-in-typen-buendeln` ändert die Form von `RequestSettings`, die zwölf Schlüssel von `EffectiveSettings` blieben (Test `RequestSettingsTest`) |
| [ADR-0043](../../adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) | Test der Kind-JVM rot trotz Weg A; Betrieb aus dem Jar zeigt das Hängen | nicht eingetreten; der Betrieb aus dem Jar ist nicht gemessen (Grenze in Spezifikation Abschnitt 4), der Trigger ist deshalb nicht widerlegt, nur nicht ausgelöst |
| [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) | Veröffentlichung; Konflikt mit Jackson; erste Version `1.0`; Einstieg aus Java; Embedder mit Log- oder Telemetrie-Adapter; Bruch der Fläche, den kein Gate zeigte | nicht eingetreten; der Wunsch nach einem Telemetrie-Parameter ist durch [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) beantwortet; die inkompatible Änderung von `RequestSettings` ist benannt, vor `1.0` zulässig und kein unbemerkter Bruch |
| [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) | `make e2e` warm unter einem Zehntel der Stage `test` | nicht eingetreten: `make e2e` 330 s, `make clean-build` 563 s (Verifikation `4de389a1`) |
| [ADR-0016](../../adr/0016-anmeldedrosselung.md) | Betreiber hinter gemeinsamer Adresse; Angriff über mehrere Adressen; neuer Adapter mit Sperrlogik; Modus ohne Passwort | nicht eingetreten; die Frage der Adresse im Ereignis steht unter (e) oben |
| [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) (`Accepted`, nicht aus dieser Welle) mit [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) | (Nachzug zu [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), nicht Gegenstand der Welle) | **offen, Entscheidung des Nutzers:** die Zeilen von [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) (um Zeile 83) tragen die alte Lesart und stehen nicht in der `Supersedes`-Liste; behebbar nur über eine Folge-ADR (Roadmap, `slice-referenzabgleich-adr-0028-nachzug`). Diese Closure schreibt keine |

Keine Entscheidung steht mit eingetretener, unbearbeiteter Bedingung da, außer der Zeile zu [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), die nicht zu dieser Welle gehört.

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

**Lese-Schritt des Registers.** `docs/plan/planning/observations/BEO-TDS/` führt 171 Verzeichnisse; 44 haben mindestens 3 Dateien in `evidence/` (der Zähler ist die Zahl der Dateien; die `Stand`-Zeilen mancher Einträge zählen anders). Davon tragen 31 einen Ausgang (24 `verkörpert`, davon 7 mit dieser Closure, 2 `geplant`, 2 `Träger entschieden`, je 1 `gestrichen`, `gebaut`, `geliefert`); 13 stehen `offen` (12) oder `teilweise gemessen` (1) ohne Beitrag aus dieser Welle (Liste unten). Mit Slices dieser Welle erreichten vier Einträge die Schwelle (nach dem Add-Commit der dritten Evidence-Datei): `wanduhr-tests-flackern-unter-last` (mit `slice-betrieb-zustandsereignisse`), `flake-fix-ohne-reproduzierten-fehler` (mit `slice-betrieb-telemetrie-verdrahtung-belegen`), `test-naht-im-produktivcode` (mit `slice-listener-annahmeschleife-bei-erschoepften-dateien`) und `redaktion-sucht-nur-den-bekannten-wert` (mit `slice-betrieb-prozesslauf-logzeilen-inspektion`). Drei weitere standen über der Schwelle und nennen diese Closure als ihren Lese-Schritt: `zahl-an-mehreren-orten-ohne-sensor` (7 Dateien), `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (10) und `oeffentliche-flaeche-mitglieder-ohne-sensor` (3). Diese Closure verkörpert **sieben** Einträge (Entscheid des Nutzers 2026-11-13, Empfehlung des Architects); alle sieben als Prosa, ohne ADR und ohne neuen Slice.

- **Reviewer-Prüfung** ergänzt: der Redaktions- oder Inspektionstest füllt jedes redaktionspflichtige Feld (Mutation „Feld entfernen“ röten), und die Ersetzung nennt die Kennungen der Umgebung (Servername, Hostname, Container-ID), nicht nur Kennwort und Prüfwert; Grenze: Teilstücke und auf mehrere Felder verteilte Werte sind akzeptiertes Negativ — liegt in `.harness/skills/reviewer.md`.
  Auslöser: `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert` (`slice-qa-vergleichslauf-d-messungen`, `slice-referenzabgleich-mitschnitt`, `slice-betrieb-prozesslauf-logzeilen-inspektion` — 3×). Eng an den drei Belegen gefasst ([AGENTS.md](../../../../AGENTS.md) §3.8). Der Skill führt keine Gruppe „Test belegt nicht, was er behauptet“; der Punkt steht in der MEDIUM-Liste.
- **Reviewer-Prüfung** ergänzt: steht eine Zahl, Feldmenge oder Zuordnung an einem zweiten Ort (Kopie, Skript, Doku, Test), liest ein Test oder Gate beide Orte oder ein Kopplungskommentar steht auf beiden Seiten, sonst MEDIUM — liegt in `.harness/skills/reviewer.md`.
  Auslöser: `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (7 Dateien, nach Dateien gezählt; der frühere Stand-Text zählte 1×). Kein neuer Sensor. Offene Möglichkeit, **nicht angelegt:** ein Test, der die Felder je Art in `LogInspektion.kt` gegen `TelemetryJson.covered` liest (ob die Modulgrenze es zulässt, ist nicht geprüft).
- **Implementer-Anweisung** (Schritt 22a) geschärft: ein roter Lauf eines Wanduhr-Tests außerhalb des Diffs wird unter natürlicher Last wiederholt, bevor er als Flake gilt (keine künstliche Last), der rote Testfall steht mit Namen im Bericht (kein `-q`); ein Fix eines Flakes bringt einen reproduzierten Fehler mit, sonst steht in §7 und im KDoc „aus der Reihenfolge begründet, Wirkung nicht gemessen“ und der Haken „behoben“ entfällt; `docs/plan/spec-abdeckung.md` wird nur aus einem Lauf ohne roten Test eingecheckt — liegt in `.claude/commands/implement-slice.md`.
  Auslöser: `BEO-TDS/wanduhr-tests-flackern-unter-last` (12 Dateien, dritte mit `slice-betrieb-zustandsereignisse`; der frühere Stand-Text zählte 8×) und `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` (`slice-backend-sequenzierer`, `slice-transport-listener-lebenszyklus`, `slice-betrieb-telemetrie-verdrahtung-belegen` — 3×). Ein gemeinsamer Absatz für beide Klassen. Kein Sensor: die Last des Hosts ist keine Eingabe eines Gates, und ein Fehler, der nicht reproduziert wird, lässt sich nicht prüfen. Der Ausgang „behoben“ für die Wanduhr-Klasse ist **nicht** gesetzt (Ursache von `TelemetryJsonTest … principal` bleibt Hypothese).
- **Reviewer-Prüfung** ergänzt, gleicher Inhalt wie Schritt 22a (Wiederholung unter natürlicher Last, Testfall mit Namen, reproduzierter Fehler oder der Satz „Wirkung nicht gemessen“, Matrix nur aus einem Lauf ohne roten Test) — liegt in `.harness/skills/reviewer.md`.
  Auslöser: dieselben zwei Beobachtungen, `BEO-TDS/wanduhr-tests-flackern-unter-last` und `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`.
- **Reviewer-Prüfung** ergänzt, eng gefasst: ein Parameter nur für einen Test ist `internal`, das KDoc nennt ihn Naht und sagt, dass sie die Reaktion der Klasse auf den Ausgang belegt, nicht dass ein echtes Ereignis ihn auslöst — liegt in `.harness/skills/reviewer.md`.
  Auslöser: `BEO-TDS/test-naht-im-produktivcode` (`slice-oeffentliche-flaeche-verkleinern-und-belegen`, `slice-sql-batch-dispatch`, `slice-listener-annahmeschleife-bei-erschoepften-dateien` — 3×). Gedeckt sind die zwei ersten Fälle; der dritte (Vorbereitung mit Produktnutzen, `internal`, verifiziert) ist konform und nicht erweitert.
- **Reviewer-Prüfung** ergänzt, ohne Sensor: ein Slice, der Mitglieder an einem Typ der Liste in `SPEC-059` ergänzt, führt sie im Änderungsprotokoll der Spezifikation — liegt in `.harness/skills/reviewer.md`.
  Auslöser: `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` (`slice-betrieb-metriken-tracing`, `slice-betrieb-zustandsereignisse`, `slice-merge-antwort-an-messung-angleichen` — 3×). Begründung ohne Sensor: ein Vergleich der Mitglieder bräuchte einen Parser der Signaturen und änderte die Zusage von `SPEC-059` und [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit); ob `make flaeche` wächst, bleibt beim Nutzer.
- **Reviewer-Prüfung** ergänzt (LOW, MEDIUM bei Wiederkehr im selben Slice): Test-KDoc und Slice-Plan stehen im Indikativ über den geltenden Zustand, kein Konjunktiv über den abwesenden Zustand, kein `Proposed` nach der Annahme der ADR — liegt in `.harness/skills/reviewer.md`.
  Auslöser: `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (10 Dateien; der frühere Stand-Text zählte 5×). [AGENTS.md](../../../../AGENTS.md) §3.7 und Schritt 20 von `implement-slice` bleiben unverändert. Der HIGH-Punkt „Kommentar trägt keine der Kommentar-Klassen“ im Skill gilt für Code, Konfiguration und Skript weiter; dass beide nebeneinander stehen, ist eine Setzung dieses Entscheids.

*Retirement-Check:* jede dieser sieben Regeln wird nicht entfernt oder gelockert, ohne dass die Herkunft konsultiert wird („seit welle-betrieb-diagnose — ist die Beobachtung wieder aufgetreten?“).

**Wiederkehrende, schon verkörperte Klassen (Retirement-Check und Fortzählung).**

- `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (21 Dateien, davon 7 aus dieser Welle): Regel bleibt, die Klasse ist nach der Verkörperung wiedergekehrt. **Kein Sensor, begründet:** ein Abgleich der Tabelle gegen `git diff --name-only` meldete erzeugte und umbenannte Dateien (Review-Reports, Abdeckungsmatrix, Links der Gruppe `slice-mv`, Roadmap) als fehlend oder müsste raten, welche in die Tabelle gehören. Der Entscheid über einen Sensor bleibt beim Nutzer (so steht er im Stand).
- `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (42 Dateien, davon 8 aus dieser Welle): Retirement-Check beantwortet je Slice vor und nach Schritt 20c (Commit `bdfd86f8`, 2026-11-13 06:09; Stand der Umsetzung nach der Abstammung der Umsetzungs-Commits). Vor 20c fünf von neun Slices mit Evidence-Datei dieser Klasse, vier ohne. Nach 20c, mit 20c im Stand der Umsetzung (`bdfd86f8` ist Vorfahr von `7582e1ef`, `09c3e34c`, `fd49abd2`): drei von drei Slices mit Evidence-Datei (`slice-einstellungen-in-typen-buendeln`, `slice-request-failed-mit-request-id`, `slice-betrieb-prozesslauf-logzeilen-inspektion`), jedes Mal von Reviewer oder Verifier gefangen, nicht vom Implementer. Die Klasse ist trotz Schritt 20c wiedergekehrt; drei Slices tragen keine Aussage über seine Wirkung. **Schritt 20c bleibt, nicht gelockert.** Die Prosa-Form ist damit ausgeschöpft im Sinne von Modul 6 (vierte und weitere Schwelle): einen mechanischen Sensor benennt diese Closure nicht, weil die Eingabe der Prüfung die Kernaussage des Slice ist, nicht der Diff; ein Wächter, der sie ableitet, ist nicht bekannt. Ob ein Sensor oder eine Änderung der Rollenfolge nötig ist, entscheidet der Nutzer.
- `BEO-TDS/aussagen-ueber-messung-hinaus` (82 Dateien, 7 aus dieser Welle), `BEO-TDS/nachbesserung-ohne-zweiten-review` (13, 6 aus dieser Welle; Ausgang unverändert), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (25, 4), `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (11, 1): weiter gezählt, keine weitere Schärfung; die Begründung der vorigen Closure (`welle-testserver-manuell-results.md`, Abschnitt „Gezählt, nicht verkörpert“) gilt unverändert.
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (12, 3), `BEO-TDS/dod-wortlaut-ueber-der-bauart` (8, 2), `BEO-TDS/haken-vor-verifikation-gesetzt` (6, 1), `BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr` (4, 1), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (7, 1; gestrichen, das Gate deckt es): weiter gezählt, keine Schärfung; die Regeln stehen in `AGENTS.md`, Reviewer-Skill und den Commands (nicht neu gelesen für diese Closure).
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11, 3 aus dieser Welle) und `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (7): unverändert; Träger von `rueckstau-im-produktivbetrieb-nicht-gemessen` ist `slice-qa-performance`, die Träger von `anmeldepfad-sicherheit-nicht-gebaut` nennt sein Stand (`slice-qa-sicherheit`, `slice-qa-anmeldedrosselung`, `slice-betrieb-log-adapter-testserver`, `slice-release-artefakt-lizenz`); die Gesamtinspektion ist gebaut, siehe Anschluss oben.

**Gelesen, ohne neuen Beleg aus dieser Welle, Stand unverändert (13 Einträge ab 3× ohne Ausgang).** Die Zählung wurde am Register nachgeprüft: 44 Einträge mit mindestens 3 Dateien, 31 mit Ausgang, 13 ohne. Namen: `lesart-einer-nutzerentscheidung-unbestaetigt` (16 Dateien; der Stand-Text nennt 10×), `zusage-ohne-sensor-in-der-ci-kette`, `eingangsliste-je-stage-von-hand` (3), `dod-haengt-an-offenem-slice-ohne-rueckfuehrung`, `geheimnis-kopien-im-speicher-nicht-gemessen` (4; der Stand-Text nennt 3×), `festlegung-gilt-nur-fuer-das-clientprofil`, `spec-einzelfaelle-ohne-lastenheft-deckung`, `schranken-test-mit-ungeprueftem-zaehler`, `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`, `parametertyp-des-clients-passt-nicht-zur-spalte` (3), `tds-8-0-und-pooling-nicht-gemessen`, `messung-am-echten-sql-server-fehlt` (teilweise gemessen), `katalog-elemente-grenzen-der-angleichung`. Diese Closure ändert weder Regel noch Gate dafür; die Ausgänge setzt der Nutzer (Regel, Sensor, Träger-Slice oder Streichung).

**Stand-Zahlen nachgezogen** (Zahl der Dateien in `evidence/`): `wanduhr-tests-flackern-unter-last` 12, `eingangsliste-je-stage-von-hand` 3, `parametertyp-des-clients-passt-nicht-zur-spalte` 3, dazu beim Verkörpern `zahl-an-mehreren-orten-ohne-sensor` 7 und `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` 10. `parametertyp-des-clients-passt-nicht-zur-spalte` nennt Träger, aber keinen der drei Ausgänge (beide genannten Träger-Slices sind geschlossen, `geplant` ist deshalb nicht eindeutig): **als offen gelistet, nichts zugewiesen.** Ungleich bleiben außerdem die Stand-Texte von `lesart-einer-nutzerentscheidung-unbestaetigt` (10× gegen 16 Dateien) und `geheimnis-kopien-im-speicher-nicht-gemessen` (3× gegen 4); sie sind nicht geändert, weil ihr Stand unverändert bleibt.

**Nachtrag zu §1 der Welle-Datei.** Dort steht in der Register-Sichtung der Eröffnung: „keine Beobachtung erreicht mit ihr 3×, die Welle schließt daher keine Beobachtung ein“. Das stimmt nicht mehr. Vier Beobachtungen erreichten mit Slices dieser Welle die Schwelle: `redaktion-sucht-nur-den-bekannten-wert`, `flake-fix-ohne-reproduzierten-fehler` und `test-naht-im-produktivcode` (die drei, die der Entscheid des Nutzers nennt) sowie `wanduhr-tests-flackern-unter-last` (dritte Datei mit `slice-betrieb-zustandsereignisse`, dort in §7 so geführt). §1 bleibt unverändert; der Satz galt zur Eröffnung.

**Benannte Spec-Lücke:** keine neue. Die Lücke `TDS-FA-09.a` Nr. 3 nennt die Zustandsereignisse nicht (`slice-betrieb-zustandsereignisse` V-9, ein Halbsatz in der Spezifikation) bleibt dort als Nachzug benannt und ist hier nicht entschieden.

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat, steht oben unter
*Steering-Loop-Einträge*. Fortgeschrieben bei dieser Closure: sieben Einträge auf `verkörpert`, zwei Retirement-Nachträge (`BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`), Stand-Zahlen von `BEO-TDS/eingangsliste-je-stage-von-hand` und `BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte`; neu angelegt: `BEO-TDS/quelladresse-im-ereignis-ohne-traeger` (Punkt (e) der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), 1×).

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren.

- `slice-qa-performance` (Last, Durchsatz, Dauerbetrieb, Telemetrie als Dauer je Schicht; Datei in `open/`; Start-Trigger erfüllt, der Start ist Sache des Nutzers) — Träger von Overhead und Rückstau, des Anschlusses oben und der Out-of-Scope-Grenze „Last“ dieser Welle.
- `slice-qa-sicherheit` besteht in `done/` (Anschluss oben); kein neuer Slice aus dieser Welle.
- **Nicht angelegt:** ein Mini-Slice, der `LogInspektion` gegen `TelemetryJson.covered` liest (Instanz von `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor`); er steht oben als offene Möglichkeit.

## Ausgeschlossene Gegenstände (§6 der Welle-Datei) und ihr Träger

| Gegenstand | Träger | Stand |
|---|---|---|
| Startprobe der TLS-Konfiguration, belegter Port, Listener | `welle-transport-tls` (`slice-transport-listener-lebenszyklus`) | unverändert; die Meldung läuft über die Log-Schnittstelle |
| Verhalten des geordneten Beendens, Ressourcenfreigabe | `slice-abbruch-ressourcen-shutdown` | unverändert; hier nur Beginn und Ende als Ereignis |
| `QUERY_TIMEOUT`, `CANCEL_TIMEOUT`, Fehler-Mapping | `slice-attention-cancel-timeout`, `slice-fehler-mapping` | unverändert |
| Zeitgleichheit des Passwortvergleichs, Drosselung | `slice-qa-sicherheit`, `slice-qa-anmeldedrosselung` | unverändert; Gesamtinspektion jetzt am Prozess (Anschluss oben) |
| Lastmessung, Overhead der Telemetrie | `slice-qa-performance` (`open/`) | offen |
| Speicherkopien von Geheimnissen | kein Träger als Datei | offen (`BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`) |
| Rotation, Aggregation, Versand, Collector, Dashboards, Alarmierung, Verwaltungsoberfläche, Neuladen der Konfiguration | Betreiber ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), Out-of-Scope) | unverändert ausgeschlossen |
| Adapter für JMX, Text-Endpunkt, Telemetrie-Bibliothek, fertiges Format der Bibliothek | Re-Evaluierungs-Trigger der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) | nicht eingetreten |

## Offene Fragen an den Nutzer

Diese Closure trifft keine davon; sie ändert für sie weder Regel noch Gate.

1. **Folge-ADR zu [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md).** Beide Zeilen tragen die alte Lesart der Referenzinstanz für TDS 8.0, [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) ist `Accepted`; die Zeilen stehen nicht in der `Supersedes`-Liste. Behebbar nur durch eine Folge-ADR; Entscheidung beim Nutzer.
2. **Quelladresse im Ereignis** ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (e)): Slice, Entscheidung in einer Folge-ADR oder Streichung? Das Register führt den Punkt jetzt (`BEO-TDS/quelladresse-im-ereignis-ohne-traeger`).
3. **Instanztrennung und Namen** ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (f), (g)): bestätigt der Nutzer „benannte Grenze statt Feld“ und die Namen `EventKind`, `issueKind` samt `SECURITY_WEAKENED` mit `reason`? Offene Nutzerpunkte (1) bis (3) von `slice-oeffentliche-flaeche-verkleinern-und-belegen`.
4. **Sensor für Mitglieder der Fläche.** Soll `make flaeche` um Mitglieder wachsen (Frage 2 der Closure von `welle-testserver-manuell`)? Der Reviewer-Punkt ist verkörpert, der Sensor nicht.
5. **Ein Sensor für zweite Fundstellen.** `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` ist nach Schritt 20c in drei von drei Slices wiedergekehrt; ob eine Änderung über die Prosa hinaus nötig ist, entscheidet der Nutzer (Modul 6, vierte Schwelle).
6. **Die 13 Einträge ohne Ausgang** (Liste oben) und `BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte`: je Regel, Sensor, Träger-Slice oder Streichung.
7. **Sechs Release-Entscheidungen** (`slice-release-entscheidungen`), Kompatibilitätsmatrix (die drei Slices `slice-kompatibilitaetsmatrix-*`), Passwortvergleich (`slice-qa-passwortvergleich-gemischte-liste`) und der Ausgang von `BEO-TDS/nachbesserung-ohne-zweiten-review`: nicht Gegenstand dieser Closure, unverändert beim Nutzer.
8. **Archivierung (Schritt 4).** Siehe „Archivierung und Roadmap“ unten.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle Slices der Welle in `done/`: 12 Dateien mit `Welle: welle-betrieb-diagnose` in `done/`, 0 in `open/`, `next/` und `in-progress/` (am Dateisystem, 2026-11-13). Die Vorschau des Archivierungs-Werkzeugs zählt dieselben 12 als Mitglieder.
- Closure-Trigger 3 der Welle-Datei („Das Mehr“): Verifikationsbericht `2026-11-13-4de389a1-verifikation-slice-betrieb-prozesslauf-logzeilen-inspektion` (unter `docs/reviews/`), Abschnitt „Welle-Beleg“ (Punkte (i) bis (iii) erfüllt, jede Teilaussage von „Das Mehr“ belegt; Grenzen V-1, V-4, V-7). Punkt (iv) des Berichts — was für diese Notiz zu liefern war (zwei Spalten, Anschluss an `slice-qa-sicherheit` und `slice-qa-performance`, Zuordnung der Punkte (a) bis (h)) — steht oben.
- Frischbelege des Codes auf `4de389a1` (Verifier, Last 5 bis 6, ein Gradle-Lauf zugleich, keine künstliche Last): `make clean-build` Exit 0 (563 s, 1402 `PASSED`, 0 `FAILED`), `make e2e` Exit 0 (330 s, acht Stufen), elf Mutationen. Seit `4de389a1` änderte sich kein Code in `src/` und `testserver/`; die Commits danach sind Dokumente, Register und zwei Command- und Skill-Dateien.
- `make gates` Exit 0 in dieser Closure (12 s: Gruppen `werkzeug`, `tests`, `stempel` bei passendem Stempel übersprungen, `d-check` 1437 Dateien 0 Befunde, `gate-zuordnung` 16 Gates und 33 Fälle, `stempel-eingaenge`, `flaeche`); der Lauf belegt für `tests` den Stempel, keine frische Gradle-Arbeit. Frischbeleg der Tests ist `make clean-build` des Verifiers.
- Doku-Gates dieser Closure: `make docs-check`, `make flaeche` (Exit 0), `make kopplung-zahlen` (Exit 0); Werte im Abschnitt „Archivierung und Roadmap“.

## Drei Paarungen (Schritt 3, zum Schluss geprüft)

- *Anker:* sieben Einträge mit `liegt in`, Zielorte `.harness/skills/reviewer.md` und `.claude/commands/implement-slice.md` bestehen ab Repo-Wurzel; beide tragen `seit welle-betrieb-diagnose` (der Skill sechsmal, der Command einmal an Schritt 22a). Geprüft am Dateisystem mit `grep` über beide Dateien.
- *Folge-Slice:* `slice-qa-performance` besteht als Datei in `open/`; `slice-qa-sicherheit` in `done/`.
- *Register:* jede genannte Kennung `BEO-TDS/<slug>` besteht als Verzeichnis mit nicht leerem `evidence/` (Lauf in dieser Closure über alle in dieser Notiz genannten Kennungen). Die drei Paarungen, die `slice-betrieb-log-schnittstelle` der Welle-Closure überließ, sind damit geprüft; die übrigen Slices der Welle verwiesen auf dieselbe Prüfung.

## Archivierung und Roadmap

Regeln dieser Sektion: `close-welle` Schritte 4 und 6.

**Archivierung (Schritt 4): nicht ausgeführt, Bedingung nicht eingetreten.**

- `make archive-welle WELLE=welle-betrieb-diagnose` meldet in diesem Arbeitsbaum: „der Traeger liegt nicht (.harness/state/bin/ai-harness-init) — dieses Repo archiviert seine Wellen nicht“; der Träger (gitignoriert) liegt im Haupt-Arbeitsbaum.
- Die Vorschau dieses Trägers (`ai-harness-init archive-welle --vorschau welle-betrieb-diagnose`, aus dem Haupt-Arbeitsbaum gegen diesen Arbeitsbaum gefahren, nichts geschrieben, Exit 3) nennt nach Ergebnisnotiz und Move des Plans: 12 Mitglieder (Feld `Welle:`), 0 wellenlos seit der letzten Closure, 2 „bleibt liegen (nach der Grenze)“ (wellenlose Slices; die Vorschau führte sie vor dem Schreiben der Notiz als 2 wellenlos, die Einstufung wechselte mit der Notiz, der Grund ist nicht weiter geprüft), 74 fremd, 25 Review-Reports (ohne Stub), 128 Dateien mit Verweisen auf Bewegtes (309 Präfix-, 28 geschwister-relative Fundstellen).
- **Sperre `[haenger]`** (eine von eins; vor der Ergebnisnotiz standen zusätzlich `[ergebnisnotiz]` und `[kein-plan]`, beide mit dem Schreiben der Notiz und dem Move des Plans aufgelöst): „ein Review-Report soll verschwinden, auf den noch verwiesen wird“, Hinweis „erst den Verweis aufloesen (oder die Referenz im Doku-Gate ausnehmen, mit einer ADR — Gates werden nicht ohne ADR gelockert)“. Zählung: 86 Verweise aus 58 Dateien auf 16 Review-Reports; alle 58 Dateien liegen im Beobachtungs-Register (`evidence/`, `state.md`, `observation.md` unter `observations/BEO-TDS/`).
- Nichts erzwungen, nichts von Hand ins Archiv bewegt, keine Referenz im Doku-Gate ausgenommen. Das Auflösen der Sperre (die Verweise des Registers auf Review-Reports lösen oder ausnehmen) ist Entscheidung des Nutzers; bis dahin gilt die Welle als inhaltlich geschlossen, die Auditierbarkeit nach Schritt 4 steht aus. Dasselbe Bild nannte die Closure von `welle-testserver-manuell` (dort 112 Verweise auf Review-Reports); auch `welle-fehler-abbruch` und `welle-produktschnitt` stehen ohne Archiv.

**Gates dieser Closure (Pflicht für Doku und Spec nach `harness/README.md` §Minimal agent workflow):** `make docs-check` Exit 0 (1441 Dateien, 0 Befunde), `make flaeche` Exit 0, `make kopplung-zahlen` Exit 0 (Docker-Cache), `make gates` Exit 0 in 12 s (Gruppen `werkzeug`, `tests`, `stempel` mit Beleg übersprungen). Geändert wurden nur Dokumente, Register, ein Reviewer-Skill und ein Command; `src/` und `testserver/` blieben unberührt.

**Roadmap (Schritt 6):** die Welle verließ *Offene Wellen* (samt dem Block „In Arbeit“), *Abgeschlossene Wellen* trägt ihre Zeile mit Zeiger auf diese Notiz. Meilenstein: keiner (die Welle hat „kein Meilenstein-Bezug“). Keine Umplanung, daher kein Eintrag in *Historische Trigger-Verschiebungen*. Der Welle-Plan liegt per reinem Move in `done/`; die Verweise darauf sind nachgezogen.
