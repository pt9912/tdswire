# ADR-0020: Logformat, Observability-Schnittstelle und Zugang zu Metriken und Spans

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0003](0003-transportframework.md), [ADR-0009](0009-authentifizierungsmodus.md), [ADR-0007](0007-fehler-und-attention.md), [ADR-0013](0013-backend-grenzen.md)

**Schärft:** [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) (Nr. 3: Ereignisse, Korrelations-IDs, Feld der Anmeldekennung), [SPEC-012](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) und die Pflicht-Logfelder in demselben Abschnitt, `SPEC-017` bis `SPEC-019` ([Metriken und Tracing-Felder](../../../spec/spezifikation.md#5-metriken-und-tracing-felder)), [ARC-009](../../../spec/architecture.md#1-komponenten-übersicht) und [ARC-014](../../../spec/architecture.md#3-externe-abhängigkeiten) (Ort der Schnittstelle, Austauschbarkeit). Kein `Supersedes`: [ADR-0009](0009-authentifizierungsmodus.md) und [ADR-0003](0003-transportframework.md) bleiben unverändert; [ADR-0009](0009-authentifizierungsmodus.md) überlässt das Feld der Anmeldekennung ausdrücklich der Spezifikation, und diese ADR liefert die Entscheidung dazu.

---

## Kontext

Das Lastenheft verlangt, dass Betreiber den Betriebszustand diagnostizieren können ([TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)), dass Zugangsdaten nie im Klartext im Log stehen ([TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), Messmethode Loginspektion) und dass Latenz je Schicht messbar ist ([TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance)). Die Spezifikation führt Pflicht-Logfelder (`connection_id`, `session_id`, `request_id`, `event`, `duration_ms`, `error_code`, `client_profile`) und drei Spans (`SPEC-017` bis `SPEC-019`) samt Metrik-Liste, legt aber weder Format noch Ort noch Zugang fest. Die Wahl trifft der Nutzer; dieser Text bietet je Frage Optionen und eine Empfehlung an und entscheidet nichts allein.

**Stand des Codes (aus dem Quelltext gelesen, nicht gemessen).**

- Das Log ist heute `ServerLog` im Package `runtime`: eine Funktion `line(text)`, die `tdswire: <Text>` auf die Standardausgabe schreibt. Der Handshake meldet über `LogEntry`, der Listener über `ListenerEvents`; beides sind Rückrufe der Schichten, keine gemeinsame Schnittstelle.
- Die Verbindungs-Kennung ist ein Zähler im Listener (`AtomicLong`); `session_id` und `request_id` erzeugen Handshake und Anfrageschleife heute mit `UUID.randomUUID()`.
- Das Feld `client_profile` hat im Code und in der Konfiguration keine Quelle; die Konfiguration kommt aus Umgebungsvariablen (`ConfigLoader`).
- Der Katalog führt als Laufzeitbibliothek nur die Coroutinen-Bibliothek (`gradle/libs.versions.toml`). Die Importregeln der Schichten (`config/detekt/detekt.yml`) lassen `domain` und `port` nur die Standardbibliothek importieren; `observability` darf nur `domain` importieren; `protocol` und `transport` dürfen heute `observability` importieren, weil Schnittstelle und Implementierung noch kein Package-Schnitt sind (Kommentar in der Konfiguration).

**Was nicht gemessen ist.** Die Entscheidung stützt sich auf keinen Spike: weder Größe oder Lock-Kosten einer Fremdbibliothek noch Verhalten eines vollen Ausgabekanals noch Lastkosten der Telemetrie sind gemessen. Aussagen über Aufwand in den Tabellen sind Einschätzungen aus dem Quelltext und den Importregeln. Die Messung der Lastkosten trägt der Performance-Nachweis (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`). **Alle Zahlen und Namen in dieser ADR sind Setzung (Voreinstellung, nicht gemessen).**

## Entscheidung

Der Nutzer entscheidet jede der acht Fragen; die Empfehlung ist jeweils fett. Die Fragen sind unabhängig, bis auf die Abhängigkeit Frage 3 und 4 von Frage 1 (Felder des Modells).

### Frage 1 — Format, Ziel und Bibliothek der Logzeilen

| Option | Pro | Contra |
|---|---|---|
| A — Text mit `key=value` auf Standardausgabe, eigener Schreiber | menschenlesbar; keine Abhängigkeit | Maschinenlesen braucht Parser; Werte mit Leerzeichen brauchen Regeln |
| **B — eine JSON-Zeile je Ereignis auf Standardausgabe, eigener Schreiber (empfohlen)** | strukturiert und maschinenlesbar; Container-Betrieb liest Standardausgabe; keine Fremdbibliothek im Produktivpfad; die Werte stammen aus einer geschlossenen Menge (Zahlen, UUID, Namen aus Aufzählungen), das Escaping bleibt klein | schwerer von Hand zu lesen; der Schreiber ist eigener Code und braucht Tests für das Escaping |
| C — JSON-Zeile in eine Datei mit Rotation durch den Server | Betreiber braucht keine Umleitung | Rotation, Rechte und Pfad sind Aufgaben des Betreibers (Welle und Lastenheft schließen sie aus); ein Pfad im Log-Umfeld ist selbst ein Redaktionsthema |
| D — Fremdbibliothek (SLF4J-Fassade mit Backend und JSON-Encoder) | etabliert, viele Ziele | neue Lieferkette (`make deps-lock`, Prüfsummen, Lizenzhinweise, `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`); `domain` und `port` dürfen sie nicht importieren; Freitext-API verleitet zu Werten im Text |
| E — nichts tun (`println` bleibt) | kein Aufwand | keine Pflichtfelder, keine Korrelations-IDs; [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) bleibt unerfüllt |

Empfehlung **B**. Felder je Zeile (Setzung): `ts` (UTC), `level`, `event` und die Pflichtfelder der Spezifikation, soweit vorhanden; `ts` und `level` ergänzt die Spezifikation beim Nachzug. Die Log-Ebene ist über die Konfiguration einstellbar (Schlüssel `LOG_LEVEL`, Werte `ERROR`, `WARN`, `INFO`, `DEBUG`, Voreinstellung `INFO`; ein anderer Wert bricht den Start ab). Ein Schalter für das Format entfällt, solange nur ein Format besteht.

### Frage 2 — Ort der Schnittstelle

| Option | Pro | Contra |
|---|---|---|
| **A — Schnittstelle und Ereignismodell im Package `domain`, Adapter in `observability` (empfohlen)** | `protocol` und `transport` importieren `domain` ohnehin; `observability` darf `domain` importieren; keine neue Importregel; `domain` führt laut [ARC-001](../../../spec/architecture.md#1-komponenten-übersicht) „Modelle und Verträge“ | `domain` wächst um die Typen; `domain` darf kein `java.util.logging` und keine Fremdbibliothek, die Typen müssen reine Werte sein |
| B — eigenes Package für die Schnittstelle (`observability` getrennt in Schnittstelle und Implementierung) | klare Trennung nach [ARC-009](../../../spec/architecture.md#1-komponenten-übersicht) („Observability-Schnittstellen“ gegenüber Telemetrie-Adapter) | neue Importregeln samt Selbsttest (`make detekt-selbsttest`) für `protocol`, `transport` und `observability`; mehr Bau für gleiche Wirkung |
| C — Schnittstelle in `observability`, `protocol` und `transport` importieren sie | keine neue Regel | `protocol` und `transport` hängen an der Implementierung (die Architektur verbietet das dem Kern); die Regel für `observability` („nur `domain`“) bliebe, aber die Trennung entfiele |

Empfehlung **A**; die Architektur-Sicht nennt „Observability-Schnittstellen“ als importierbar und legt den Ort nicht fest, A füllt die Lücke ohne Änderung der Sicht.

Zur Form des Modells (Empfehlung): eine geschlossene Menge von Ereignisarten (sealed Typen oder Aufzählung) mit typisierten Feldern, **kein Freitextfeld**. `error_code` ist ein `TDS_*`-Code aus der Spezifikation; eine Ausnahme erscheint nur als Typname ([SPEC-048](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) in der engeren Fassung der Zuordnung des Fehler-Mappings, [ADR-0013](0013-backend-grenzen.md) Entscheidung 7), nie mit Text und nie mit Stapelspur. Ein Modell ohne Freitext hat für Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext **kein Feld**; das ist die Redaktion durch Bauart. Sie gilt nur für Ereignisse, die über die Schnittstelle laufen; Ausgaben mit `println`, `System.out`, `System.err` oder `printStackTrace` außerhalb des Adapters erfasst sie nicht (siehe Fitness Function).

Entwurf der Ereignisarten (Setzung, der Bau legt die endgültige Menge fest): Start und Ende des Servers, Port gebunden, Bindefehler (Adresse und Port, wie [SPEC-043](../../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port) es verlangt), ungültige Konfiguration (Schlüssel und Art, kein Wert), Startprobe der TLS-Konfiguration fehlgeschlagen, Beginn und Ende des geordneten Beendens, Verbindung angenommen und beendet (mit der Art des Endes), Anmeldung angenommen und abgelehnt (mit Grund-Marke), Adapterfehler, Anfrage beendet (mit Art, Dauer, Ergebnis), Abbruch durch ATTENTION, aktive Abschwächung der Verschlüsselung ([SPEC-010](../../../spec/spezifikation.md#3-defaults-und-konstanten) verlangt sie „im Log sichtbar“).

### Frage 3 — Feld für die Anmeldekennung

| Option | Pro | Contra |
|---|---|---|
| A — nie im Log | keine Enumerationshilfe; kein Log-Eintrag mit versehentlich als Kennung getipptem Passwort | Rückverfolgung abgelehnter Anmeldungen nur über `connection_id`, Zeit und Grund-Marke; der Betreiber sieht nicht, welcher Benutzer scheitert |
| **B — Feld `principal` nur bei angenommener Anmeldung (empfohlen)** | der Betreiber sieht, wer angemeldet ist; die Kennung stammt aus der Benutzerliste des Betreibers, nicht aus Angreifer-Eingabe; abgelehnte Versuche tragen keine Kennung | abgelehnte Anmeldungen sind nur über `connection_id` und Zeit verfolgbar (wie A); die Kennung steht im Log (Datenschutz beim Betreiber) |
| C — Feld für alle Anmeldungen, auch abgelehnte | beste Rückverfolgung | Eingabe des Clients im Log: ein falsch ins Kennungsfeld getipptes Passwort steht im Klartext im Log (Verstoß gegen [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) im Betrieb); Steuerzeichen und Länge sind Eingabe des Angreifers; Zählung je Name erleichtert Enumeration durch jeden, der das Log liest |
| D — gesalzener Hash der Kennung bei abgelehnten Anmeldungen | gleiche Kennung ist korrelierbar, ohne Klartext | zusätzlicher Bau und Salz-Verwaltung; ein Hash einer kurzen oder erratenen Eingabe ist umkehrbar durch Probieren (nicht gemessen); Passwort im Kennungsfeld würde gehasht, aber der Hash eines Passworts ist selbst ein Geheimnisderivat |

Empfehlung **B**. Die Spezifikation führt das Feld erst nach Annahme dieser ADR (`TDS-FA-09.a` Nr. 3, „bisher keines“). SPEC-012 (feste Antwort ohne Kennung) bleibt unberührt: das betrifft die Antwort an den Client, nicht das Log.

### Frage 4 — Parameterwerte im Log

| Option | Pro | Contra |
|---|---|---|
| **A — kein Schalter; Parameterwerte erscheinen nie (empfohlen)** | das Modell hat kein Feld dafür; keine Abschwächung; kein Konfigurationsschlüssel, kein Test des Schalters; der Satz „standardmäßig nicht“ der Spezifikation bleibt wahr | die Diagnose eines Parameterfehlers geht nicht über das Log; Bedarf nicht belegt |
| B — Schalter, Voreinstellung aus; die Einschaltung steht als Ereignis im Log (wie `ENCRYPTION_POLICY`) | Diagnose im Einzelfall möglich | Abschwächung; Parameter können Zugangsdaten und personenbezogene Daten tragen; das Modell bekäme ein Feld für Werte, die Redaktionsgarantie „kein Feld“ entfiele für diese Ereignisart; Test für Standard und Schalter nötig |
| C — Schalter nur in einem Testlauf (nicht im Produktivartefakt) | Produktivbetrieb unberührt | zwei Bauformen; Artefakt-Unterschied widerspricht dem einen Laufzeit-Artefakt |

Empfehlung **A**; Re-Evaluierung, sobald ein Betreiber Werte für die Diagnose braucht (Trigger unten).

### Frage 5 — Wert von `client_profile` und Herkunft der IDs

`client_profile` (Pflichtfeld der Spezifikation) hat keine Quelle.

| Option | Pro | Contra |
|---|---|---|
| A — Name aus der Konfiguration (neuer Schlüssel) | der Betreiber legt den Namen fest | neuer Schlüssel mit Validierung; das Profil der Spezifikation ist ein versioniertes Dokument, kein Konfigurationsobjekt des Servers |
| B — Angaben des Clients aus LOGIN7 (Anwendungsname, Schnittstellenname) | zeigt den tatsächlichen Client | Eingabe des Clients: frei wählbar, im Log ein Einschleusungsweg, vor dem Login nicht vertrauenswürdig; Kardinalität offen |
| **C — aus dem ausgehandelten Protokoll abgeleitet: `tds-7.4` oder `tds-8.0` und der Verschlüsselungsmodus, vor der Aushandlung `unknown` (empfohlen)** | keine Eingabe des Clients, kein neuer Schlüssel; geschlossene Menge; deckt, was der Server wirklich weiß | sagt nichts über die Clientbibliothek; entspricht nur einer engen Lesart von „Profil“ |
| D — Feld streichen | kein Aufwand | Änderung einer Pflichtangabe der Spezifikation |

Empfehlung **C**. Die Lesart des Wortes „Profil“ in der Spezifikation ist nicht bestätigt (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`); der Nutzer sagt, ob C genügt oder A gemeint ist.

Herkunft der IDs: **Empfehlung: beibehalten, wie der Code sie heute bildet.** `connection_id` ist der Zähler des Listeners (ab 1 je Prozessstart), `session_id` und `request_id` sind zufällige UUID. Gesetzt, nicht gemessen: Der Zähler wiederholt sich nach einem Neustart; wer Logs über Neustarts zusammenführt, trennt sie über `ts`. Alternativen: alles Zähler (kompakt, aber nicht eindeutig über Neustarts), alles UUID (eindeutig, länger). Kosten der UUID je Anfrage sind nicht gemessen.

### Frage 6 — Telemetrie: Schnittstelle, Bibliothek und Zugang zu Metriken und Spans

| Option | Pro | Contra |
|---|---|---|
| **A — eigene kleine Schnittstelle in `domain` (Span beginnen und beenden mit den Pflicht-Attributen, Zähler, Verteilung), Sammler-Adapter für Tests, im Produkt Schnappschuss und Span-Ende als Logzeilen (empfohlen)** | keine Fremdbibliothek; kein neuer Port, keine neue Angriffsfläche; dieselbe Redaktion wie das Log (Attribute sind typisiert); die Dauer je Schicht ist für den Performance-Nachweis lesbar | Betreiber ohne Auswertung der Logzeilen sieht keine Kurven; spätere Telemetrie-Bibliothek braucht einen Adapter |
| B — Schnittstelle einer Telemetrie-Bibliothek (OpenTelemetry-API) direkt | Standard, viele Sammler | Fremdbibliothek im Kern nicht erlaubt (`protocol` und `transport` sollen sie nicht importieren); Größe der Abhängigkeiten nicht gemessen; Exporter und Collector sind Betreiberaufgabe |
| C — JMX | im JDK; kein neuer Netzport bei lokalem Zugriff | Fernzugriff öffnet einen Port und braucht Authentifizierung; ob das JMX-Modul im Laufzeitabbild des Artefakts liegt, ist nicht geprüft |
| D — Text-Endpunkt (HTTP) für Metriken | gängig für Abfrage durch Sammler | neuer Port mit Authentifizierung und TLS-Entscheidung; eine zweite Angriffsfläche neben TDS; Lastenheft fordert keine Verwaltungsoberfläche |
| E — nichts tun | kein Aufwand | `SPEC-017` bis `SPEC-019` und [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance) ungetragen |

Empfehlung **A**. Der Schnappschuss der Metriken erscheint als Logzeile (Ereignis `metrics`, Abstand `METRICS_INTERVAL_SECONDS`, Voreinstellung 60, 0 schaltet ihn aus; Setzung, nicht gemessen). Die Namen der Spans und ihre Pflicht-Attribute bleiben die der Spezifikation; Metriknamen ohne SQL-Text als Label, wie dort verlangt. B, C und D bleiben als Adapter hinter der Schnittstelle möglich ([ARC-014](../../../spec/architecture.md#3-externe-abhängigkeiten): „austauschbar“) und sind je eine eigene Entscheidung mit eigener Angriffsflächen-Prüfung.

### Frage 7 — Ausgabe bei vollem Kanal

| Option | Pro | Contra |
|---|---|---|
| A — synchron schreiben | einfachster Bau; keine Reihenfolgeprobleme | blockiert der Ausgabekanal (voller Pipe, träger Log-Treiber), hält er den Anfragepfad an; nicht gemessen |
| **B — begrenzte Warteschlange und ein Schreiber; bei Überlauf wird die neue Zeile verworfen und gezählt, die Zahl steht im Feld `dropped` der nächsten geschriebenen Zeile (empfohlen)** | der Anfragepfad blockiert nicht; Verlust ist sichtbar | Zeilen können fehlen, auch Sicherheitsereignisse; Warteschlangengröße ist Setzung (1024 Zeilen, nicht gemessen); mehr Bau und ein Nebenläufigkeitstest |
| C — wie B, aber Ereignisse der Ebenen `WARN` und `ERROR` blockieren statt zu fallen | Fehler gehen nicht verloren | der Anfragepfad kann doch anhalten, gerade im Fehlerfall |

Empfehlung **B**; die Wirkung unter Last misst der Performance-Nachweis, nicht dieser Text.

### Frage 8 — Vertragsabschnitt für die Observability-Schnittstelle

| Option | Pro | Contra |
|---|---|---|
| **A — die ADR und die Felder in der Spezifikation genügen; die Kotlin-Signatur ist Code mit KDoc (empfohlen)** | kein neues Dokument | ein externer Adapter-Autor findet die Signatur nur im Quelltext |
| B — eigener Vertragsabschnitt in der Spezifikation (wie für den Authentifizierungsadapter) | wie der Adaptervertrag auffindbar | zweite Fundstelle neben dem Code, driftet; kein externer Adapter-Autor ist bekannt |

Empfehlung **A**.

**Ablösung des minimalen Log-Anschlusses.** `ServerLog.line(text)`, `LogEntry` des Handshakes und die Rückrufe des Listeners rufen nach dem Bau die Schnittstelle; das beobachtbare Verhalten bleibt, die Zeilen ändern Form und Felder. Der Wegfall von `ServerLog` ist Teil des Baus; die Aufrufstellen sind aus dem Quelltext gelesen (Runtime, Handshake, Fehler-Mapping, Listener) und nicht vollständig inventarisiert.

## Verglichene Alternativen

Je Frage oben als Tabelle; „nichts tun“ ist bei Frage 1 (E) und Frage 6 (E) geführt, bei den Fragen 3, 4 und 8 ist die Option A der Verzicht auf mehr. Quer über alle Fragen gilt die Option mit dem geringsten Zusatzaufwand, die das Lastenheft trägt: eigener Schreiber, Schnittstelle in `domain`, kein neuer Port.

## Konsequenzen

- Positiv (aus der Bauart, nicht gemessen): kein Fremdbibliothek-Zuwachs, kein neuer Port, die Redaktion folgt aus dem Modell statt aus Disziplin; ein Sammler-Adapter macht die Ereignisse in Tests lesbar.
- Negativ: Eigener JSON-Schreiber und eigene Warteschlange sind Wartungscode; ohne Auswertung der Logzeilen gibt es keine Kurven; Zeilen können bei Überlauf fehlen (Frage 7); Parameterwerte stehen nie im Log (Frage 4 A); abgelehnte Anmeldungen sind nicht nach Kennung verfolgbar (Frage 3 B).
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation: Pflicht-Logfelder um `ts`, `level` und `principal` ergänzen, `client_profile` und die Herkunft der IDs festlegen, `TDS-FA-09.a` Nr. 3 („bisher keines“) auf das Feld der Entscheidung ziehen, Abschnitt „Metriken und Tracing“ um den Zugang und die Konfigurationsschlüssel (`LOG_LEVEL`, `METRICS_INTERVAL_SECONDS`) ergänzen; Träger: die Spezifikationsfortschreibung dieser Entscheidung. (2) Quelltext: Schnittstelle, Adapter, Sammler, Ablösung von `ServerLog`, Redaktionstests je Ereignisart, Inspektionstest über alle Zeilen des laufenden Prozesses; Träger: die Slices Log-Schnittstelle, Zustandsereignisse und Metriken/Tracing der Welle. (3) Messung des Overheads und des Rückstaus: Träger: der Performance-Nachweis.
- **Akzeptiertes Negativ (einmalig):** Die DoD des Entscheidungs-Slice verlangt Spikes mit Messwerten; diese ADR trägt keine. Grund: die Empfehlungen vermeiden die Bibliotheks- und Port-Optionen, deren Messung allein Aufwand gäbe; die Lastaussagen sind ohne Produktivcode nicht messbar und liegen beim Performance-Nachweis. Wählt der Nutzer D (Frage 1) oder B bis D (Frage 6), ist vor `Accepted` ein Spike im Scratchpad nachzuholen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Redaktionstest je Ereignisart: Eingaben mit Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext erscheinen in keiner Zeile, je mit verschiedenen Eingabewerten; Pflichtfelder vorhanden, soweit zutreffend | `make test` |
| Prozesstest (Kind-JVM, Standardausgabe gelesen) | Inspektion aller Zeilen eines Laufs mit Anmeldung angenommen und abgelehnt, TLS-Fehler, Startabbruch: weder Passwort noch Kennung einer abgelehnten Anmeldung noch Prüfwert | `make test` |
| detekt `ForbiddenMethodCall` (Vorschlag) | `println`, `System.out`, `System.err` und `printStackTrace` im Produktivcode außer im Ausgabe-Adapter; ob die bestehende Konfiguration das bereits sperrt, ist nicht geprüft | `make detekt` |

Noch kein bestandenes Gate; die Einrichtung folgt dem Bau.

## Offen

- Für den Nutzer: die Wahl je Frage 1 bis 8 (Empfehlungen oben); die Lesart von `client_profile` (Frage 5).
- Nicht entschieden: Format des Zeitstempels über UTC hinaus; ob das Ereignis `metrics` die Verteilung als Perzentile oder als Zähler je Klasse trägt (die Spezifikation nennt p50/p95/p99); die Quelladresse der Gegenstelle im Log (offen auch in [ADR-0016](0016-anmeldedrosselung.md), Datenschutz); Speicherkopien von Geheimnissen (nicht Gegenstand, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`); die Gesamtinspektion des Anmeldepfads (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`) ist hier nur als Fitness Function benannt, nicht gebaut.

## Re-Evaluierungs-Trigger

Ein Betreiber, der Parameterwerte oder die Kennung abgelehnter Anmeldungen für die Diagnose braucht; eine Messung, nach der Schreiben oder Warteschlange den Anfragepfad belastet; ein Betreiber, der Metriken ohne Auswertung von Logzeilen braucht (Adapter B, C oder D der Frage 6); eine Abhängigkeit, die ohnehin in den Katalog kommt und ein Logformat mitbringt; Änderung der Importregeln der Schichten.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Optionen und Empfehlungen zu Format, Ort, Kennungsfeld, Parameterwerten, `client_profile`, Telemetrie-Zugang, Rückstau und Vertragsabschnitt; alle Zahlen Setzung (Voreinstellung, nicht gemessen); die Wahl bestätigt der Nutzer mit der Annahme | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
