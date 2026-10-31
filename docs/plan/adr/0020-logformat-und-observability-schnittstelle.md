# ADR-0020: Logformat, Observability-Schnittstelle und Zugang zu Metriken und Spans

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0003](0003-transportframework.md), [ADR-0009](0009-authentifizierungsmodus.md), [ADR-0007](0007-fehler-und-attention.md), [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), [ADR-0013](0013-backend-grenzen.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md)

**Schärft:** [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) (Nr. 3: Ereignisse, Korrelations-IDs, Feld der Anmeldekennung), [SPEC-012](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) und die Pflicht-Logfelder in demselben Abschnitt, `SPEC-017` bis `SPEC-019` ([Metriken und Tracing-Felder](../../../spec/spezifikation.md#5-metriken-und-tracing-felder)), [ARC-009](../../../spec/architecture.md#1-komponenten-übersicht) und [ARC-014](../../../spec/architecture.md#3-externe-abhängigkeiten) (Ort der Schnittstelle, Austauschbarkeit). Kein `Supersedes`: [ADR-0009](0009-authentifizierungsmodus.md) und [ADR-0003](0003-transportframework.md) bleiben unverändert; [ADR-0009](0009-authentifizierungsmodus.md) überlässt das Feld der Anmeldekennung ausdrücklich der Spezifikation, und diese ADR liefert die Entscheidung dazu.

---

## Kontext

Das Lastenheft verlangt, dass Betreiber den Betriebszustand diagnostizieren können ([TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)), dass Zugangsdaten nie im Klartext im Log stehen ([TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), Messmethode Loginspektion) und dass Latenz je Schicht messbar ist ([TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance)). Die Spezifikation führt Pflicht-Logfelder (`connection_id`, `session_id`, `request_id`, `event`, `duration_ms`, `error_code`, `client_profile`) und drei Spans (`SPEC-017` bis `SPEC-019`) samt Metrik-Liste, legt aber weder Format noch Ort noch Zugang fest. Die Wahl trifft der Nutzer; dieser Text bietet je Frage Optionen und eine Empfehlung an und entscheidet nichts allein.

**Stand des Codes (aus dem Quelltext gelesen, nicht gemessen).**

- Das Log ist heute `ServerLog` im Package `runtime`: eine Funktion `line(text)`, die `tdswire: <Text>` auf die Standardausgabe schreibt. Der Handshake meldet über `LogEntry`, der Listener über `ListenerEvents`; beides sind Rückrufe der Schichten, keine gemeinsame Schnittstelle.
- Die Verbindungs-Kennung ist ein Zähler im Listener (`AtomicLong`, je Listener); `session_id` und `request_id` erzeugen Handshake und Anfrageschleife heute mit `UUID.randomUUID()`.
- Das Feld `client_profile` hat im Code und in der Konfiguration keine Quelle; die Konfiguration kommt aus Umgebungsvariablen (`ConfigLoader`).
- Der Katalog führt als Laufzeitbibliotheken die Coroutinen-Bibliothek und `jackson-dataformat-yaml` (`gradle/libs.versions.toml`); im Lock steht `jackson-core` 2.21.7 auf dem Laufzeit-Klassenpfad (`gradle.lockfile`), mit dem Streaming-Generator. Jackson liest heute die Simulationsdatei im Adapter in `runtime`; `simulation` darf es nicht importieren ([ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), Regel `ForbiddenImport/simulation`).
- Die Importregeln der Schichten (`config/detekt/detekt.yml`) lassen `domain` und `port` nur die Standardbibliothek (`port` zusätzlich die Coroutinen-Bibliothek) importieren; `observability` darf nach der Sicht nur `domain` importieren, die Regel sperrt die anderen Packages, nicht Fremdbibliotheken; `protocol` und `transport` dürfen heute `observability` importieren, weil Schnittstelle und Implementierung noch kein Package-Schnitt sind (Kommentar in der Konfiguration). `ForbiddenImport/simulation` sperrt das Package `observability`, nicht `domain`.

**Messungen (drei Spikes im Scratchpad, ohne Produktivcode, Einzelmessungen am 2026-10-31).**

| Nr. | Frage | Bedingungen | Messwert |
|---|---|---|---|
| i | Blockiert die Standardausgabe den Schreiber, wenn der Leser stockt? | Container `gradle:8.14-jdk21` (Digest des Dockerfile), JDK-Einzeldatei-Programm, 20 000 Zeilen zu je 153 Byte (rund 3 MB) über `System.out.println`, Standardausgabe in eine Pipe, deren Leser 5 s schläft und dann liest | synchron (Option A der Frage 7): ein einzelner Aufruf hielt den Aufrufer 4 586 ms an, der Aufrufer war nach 4 616 ms fertig. Mit begrenzter Warteschlange (1024 Zeilen, ein Schreiber-Thread, `offer` ohne Warten): der Aufrufer war nach 5 ms fertig, 18 869 von 20 000 Zeilen wurden verworfen |
| ii | Liegt das JMX-Modul im Laufzeit-Image? | `gcr.io/distroless/java21-debian12:nonroot` (Digest des Dockerfile, Java 21.0.10), `java --list-modules` | `java.management`, `java.management.rmi`, `jdk.management`, `jdk.management.agent` und `java.logging` sind enthalten (53 Module insgesamt) |
| iii | Verliert die Warteschlange Zeilen beim sofortigen `System.exit`? | wie i, Standardausgabe nicht gestaut; drei Zeilen in die Warteschlange, sofort `System.exit(1)`; je fünf Läufe ohne und mit Warten auf das Leeren | in allen zehn Läufen kamen alle drei Zeilen an: der Verlust trat nicht auf. Das belegt nicht, dass er nicht auftreten kann (Wettlauf zwischen Schreiber und Prozessende); die Regel in Frage 7 gilt unabhängig davon |

Die Messungen gelten für die Bedingungen der Zeile: ein Pipe-Leser in einem Container, nicht der Log-Treiber einer Container-Laufzeit, nicht das Produkt, nicht Last mit mehreren Verbindungen. Messung ii sagt, dass das Modul vorhanden ist; ob JMX sicher zu betreiben ist, sagt sie nicht (Frage 6). Nicht gemessen sind Größe oder Lock-Kosten einer Fremdbibliothek (SLF4J, OpenTelemetry), Zeit und Speicher des Jackson-Generators und die Lastkosten der Telemetrie; Aussagen darüber in den Tabellen sind Einschätzungen aus dem Quelltext, dem Lock und den Importregeln. Die Messung der Lastkosten trägt der Performance-Nachweis (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`). **Alle Zahlen und Namen außer denen der Messtabelle sind Setzung (Voreinstellung, nicht gemessen).**

## Entscheidung

Der Nutzer entscheidet jede der acht Fragen; die Empfehlung ist jeweils fett. Die Fragen sind unabhängig, bis auf die Abhängigkeit Frage 3 und 4 von Frage 1 (Felder des Modells).

### Frage 1 — Format, Ziel und Bibliothek der Logzeilen

| Option | Pro | Contra |
|---|---|---|
| A — Text mit `key=value` auf Standardausgabe, eigener Schreiber | menschenlesbar; keine Abhängigkeit | Maschinenlesen braucht Parser; Werte mit Leerzeichen brauchen Regeln |
| B — eine JSON-Zeile je Ereignis auf Standardausgabe, eigener Schreiber | strukturiert und maschinenlesbar; Container-Betrieb liest Standardausgabe; keine zusätzliche Abhängigkeit | schwerer von Hand zu lesen; der Schreiber ist eigener Code: das Escaping muss vollständig sein (Anführungszeichen, Steuerzeichen, Unicode), weil Zeichenketten-Felder bleiben (Frage 2), und braucht Tests |
| C — JSON-Zeile in eine Datei mit Rotation durch den Server | Betreiber braucht keine Umleitung | Rotation, Rechte und Pfad wären Aufgaben des Servers; der Plan der Welle schließt eine Logdatei aus, das Lastenheft nennt weder Logdatei noch Rotation (die Abwertung ist Setzung, nicht Vertrag); ein Pfad im Log-Umfeld ist selbst ein Redaktionsthema |
| D — Fremdbibliothek (SLF4J-Fassade mit Backend und JSON-Encoder) | etabliert, viele Ziele | neue Lieferkette (`make deps-lock`, Prüfsummen, Lizenzhinweise, `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`); `domain` und `port` dürfen sie nicht importieren; Freitext-API verleitet zu Werten im Text |
| E — nichts tun (`println` bleibt) | kein Aufwand | keine Pflichtfelder, keine Korrelations-IDs; [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) bleibt unerfüllt |
| **F — eine JSON-Zeile je Ereignis auf Standardausgabe, geschrieben mit dem Streaming-Generator der bereits ausgelieferten `jackson-core` (empfohlen)** | keine neue Lieferkette: die Bibliothek liegt im Lock auf dem Laufzeit-Klassenpfad (ob `make deps-lock` Lock und Prüfsummen unverändert lässt, ist nicht ausgeführt); das Escaping ist Bibliothekscode, nicht Eigenbau; der Generator schreibt nur Felder, kein Freitext-Aufruf | Kopplung des Logs an die Jackson-Version; `jackson-core` ist im Katalog nicht direkt geführt (nur `jackson-dataformat-yaml`), direkter Gebrauch verlangt eine Katalogzeile (Einschätzung); der Schreiber darf nur im Adapter-Package `observability` liegen, nicht in `domain`, `port` oder `simulation` (Importregeln, siehe unten); `protocol` und `transport` hindert keine Regel am Import, das bleibt Konvention; schließt die Aufteilung in Module ohne Jackson aus (Option b2 in [ADR-0021](0021-oeffentlicher-integrations-einstieg.md)); Zeit und Speicher nicht gemessen |
| G — JDK-Logging (`System.Logger` oder `java.util.logging`) | im JDK, keine Abhängigkeit (das Modul `java.logging` liegt im Laufzeit-Image, Messung ii) | Freitext-API (`log(Ebene, Text)`), die das Modell ohne Freitext umgeht; ein JSON-Format braucht trotzdem einen eigenen Formatter oder Handler und damit das Escaping aus B; globale Konfiguration des `LogManager`; `System.Logger` leitet nach der JDK-Dokumentation an `java.util.logging` weiter, wenn das Modul vorhanden ist (Dokumentation, nicht gemessen); `domain` darf es nicht importieren |

Empfehlung **F**; B ist der Rückfall, wenn der Nutzer Jackson im Logpfad nicht will. Grund: die frühere Begründung für B („Werte aus einer geschlossenen Menge, das Escaping bleibt klein“) trägt nicht, weil `principal`, Adresse und Ausnahme-Typname Zeichenketten bleiben (Frage 2); ein vollständiges Escaping ist dann Pflicht, und eine ausgelieferte, geprüfte Bibliothek ist der kleinere Aufwand als eigener Code mit eigenen Tests. G und D scheiden aus, weil ihre Freitext-API das Modell unterläuft. Für F gilt zur Verteilung auf die Schichten: Die Schnittstelle liegt in `domain` und kennt Jackson nicht; der Schreiber (Adapter, der Jackson importiert) liegt in `observability`; `simulation` bleibt ohne Jackson ([ADR-0012](0012-simulations-backend-beschreibung-und-formen.md)); die Datei-Leseschicht der Simulation in `runtime` und der Log-Schreiber teilen nur die Bibliothek, nicht den Code.

Felder je Zeile (Setzung): `ts` (UTC), `level`, `event` und die Pflichtfelder der Spezifikation, soweit vorhanden; `ts` und `level` ergänzt die Spezifikation beim Nachzug. Die Log-Ebene ist über die Konfiguration einstellbar (Schlüssel `LOG_LEVEL`, Werte `ERROR`, `WARN`, `INFO`, `DEBUG`, Voreinstellung `INFO`; ein anderer Wert bricht den Start ab). Ein Schalter für das Format entfällt, solange nur ein Format besteht.

### Frage 2 — Ort der Schnittstelle

| Option | Pro | Contra |
|---|---|---|
| **A — Schnittstelle und Ereignismodell im Package `domain` (Unterpackage, Arbeitsname `domain.telemetry`), Adapter in `observability` (empfohlen)** | `protocol` und `transport` importieren `domain` ohnehin; `observability` darf `domain` importieren; `domain` führt laut [ARC-001](../../../spec/architecture.md#1-komponenten-übersicht) „Modelle und Verträge“ | `domain` wächst um die Typen; `domain` darf kein `java.util.logging` und keine Fremdbibliothek, die Typen müssen reine Werte sein; das Verbot der Sicht, dass `simulation` Observability nicht importiert, ist für die Schnittstelle in `domain` ohne eigene Regel unwirksam (siehe unten) |
| B — eigenes Package für die Schnittstelle (`observability` getrennt in Schnittstelle und Implementierung) | klare Trennung nach [ARC-009](../../../spec/architecture.md#1-komponenten-übersicht) („Observability-Schnittstellen“ gegenüber Telemetrie-Adapter) | neue Importregeln samt Selbsttest (`make detekt-selbsttest`) für `protocol`, `transport` und `observability`; mehr Bau für gleiche Wirkung |
| C — Schnittstelle in `observability`, `protocol` und `transport` importieren sie | keine neue Regel | `protocol` und `transport` hängen an der Implementierung (die Architektur verbietet das dem Kern); die Regel für `observability` („nur `domain`“) bliebe, aber die Trennung entfiele |

Empfehlung **A**; die Architektur-Sicht nennt „Observability-Schnittstellen“ als importierbar und legt den Ort nicht fest, A füllt die Lücke ohne Änderung der Sicht.

**Folge für das Verbot an `simulation` (ehrlich benannt).** Die Sicht verbietet `simulation` den Import von Observability. `ForbiddenImport/simulation` sperrt das Package `observability`, nicht `domain`; mit A bleibt ein Aufruf aus `simulation` gegen die Schnittstelle grün, das Verbot wäre nur Konvention. Zwei Wege: (1) **Empfehlung:** die Schnittstelle liegt in einem Unterpackage von `domain`, und `ForbiddenImport/simulation` bekommt einen Eintrag für dieses Unterpackage, samt Rot-Fall im Selbsttest (`make detekt-selbsttest`); A braucht dann **eine** neue Regelzeile, nicht „keine neue Importregel“ (ob die Regel `domain.*` der Domain-Regel das Unterpackage zulässt, belegt der Bau, gemessen ist das nicht). (2) Verzicht auf die technische Sperre: das Verbot gilt als Review-Konvention, und die Sicht bleibt sachlich unverändert. Der Nutzer wählt; gebaut wird (1), wenn er nichts anderes sagt.

Zur Form des Modells (Empfehlung): eine geschlossene Menge von Ereignisarten (sealed Typen oder Aufzählung) mit typisierten Feldern, **kein Freitextfeld**. `error_code` ist ein `TDS_*`-Code aus der Spezifikation; eine Ausnahme erscheint nur als Typname ([SPEC-048](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) in der engeren Fassung der Zuordnung des Fehler-Mappings, [ADR-0013](0013-backend-grenzen.md) Entscheidung 7), nie mit Text und nie mit Stapelspur.

**Was die Bauart trägt und was nicht.** Für Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext hat das Modell **kein Feld**; nur für diese Werte gilt „Redaktion durch Bauart“. Zeichenketten bleiben in vier Feldern: `principal` (Kennung aus dem Authentifizierungsadapter), die Adresse beim Bindefehler (Konfigurationswert), der Typname der Ausnahme (auch aus Integrator-Code) und der Schlüsselname der Konfiguration. Das ist keine geschlossene Menge. Mindestzusage für diese Felder: (a) der Schreiber escaped jede Zeichenkette vollständig; (b) die Länge ist begrenzt (Setzung: 256 Zeichen, darüber abgeschnitten); (c) `principal` erscheint nur bei angenommener Anmeldung (Frage 3). Nicht zugesagt: dass die Kennung eines **eigenen** Adapters frei von Geheimnissen ist. Belegt ist nur die mitgelieferte Benutzerliste (`Principal(entry.name)` aus der Liste des Betreibers); [ADR-0009](0009-authentifizierungsmodus.md) lässt eigene Adapter zu, und ein Adapter, der die getippte Zeichenkette als Kennung zurückgibt, brächte ein dort getipptes Passwort in die Zeile. Das kann der Server nicht erkennen; der Betreiber eines eigenen Adapters verantwortet seine Kennung. Prüfung: ein Redaktionstest mit einem Adapter, der die Eingabe als Kennung zurückgibt, belegt das Escaping und die Längengrenze (Steuerzeichen und Anführungszeichen im Namen), nicht die Abwesenheit des Geheimnisses. Die Zusage gilt außerdem nur für Ereignisse, die über die Schnittstelle laufen; Ausgaben mit `println`, `System.out`, `System.err` oder `printStackTrace` außerhalb des Adapters erfasst sie nicht (siehe Fitness Function).

Entwurf der Ereignisarten (Setzung, der Bau legt die endgültige Menge fest): Start und Ende des Servers, Port gebunden, Bindefehler (Adresse und Port, wie [SPEC-043](../../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port) es verlangt), ungültige Konfiguration (Schlüssel und Art, kein Wert), Startprobe der TLS-Konfiguration fehlgeschlagen, Beginn und Ende des geordneten Beendens, Verbindung angenommen und beendet (mit der Art des Endes), Anmeldung angenommen und abgelehnt (mit Grund-Marke), Adapterfehler, Anfrage beendet (mit Art, Dauer, Ergebnis), Abbruch durch ATTENTION, aktive Abschwächung der Verschlüsselung ([SPEC-010](../../../spec/spezifikation.md#3-defaults-und-konstanten) verlangt sie „im Log sichtbar“).

### Frage 3 — Feld für die Anmeldekennung

| Option | Pro | Contra |
|---|---|---|
| A — nie im Log | keine Enumerationshilfe; kein Log-Eintrag mit versehentlich als Kennung getipptem Passwort, auch nicht über einen eigenen Adapter | Rückverfolgung abgelehnter Anmeldungen nur über `connection_id`, Zeit und Grund-Marke; der Betreiber sieht nicht, welcher Benutzer scheitert |
| **B — Feld `principal` nur bei angenommener Anmeldung (empfohlen)** | der Betreiber sieht, wer angemeldet ist; bei der mitgelieferten Benutzerliste stammt die Kennung aus der Liste des Betreibers, nicht aus Angreifer-Eingabe; abgelehnte Versuche tragen keine Kennung | abgelehnte Anmeldungen sind nur über `connection_id` und Zeit verfolgbar (wie A); die Kennung steht im Log (Datenschutz beim Betreiber); bei einem eigenen Adapter liefert dieser die Kennung, und ein Adapter, der Eingabe zurückgibt, bringt sie ins Log (Frage 2, Mindestzusage) |
| C — Feld für alle Anmeldungen, auch abgelehnte | beste Rückverfolgung | Eingabe des Clients im Log: ein falsch ins Kennungsfeld getipptes Passwort steht im Klartext im Log (Verstoß gegen [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit) im Betrieb); Steuerzeichen und Länge sind Eingabe des Angreifers; Zählung je Name erleichtert Enumeration durch jeden, der das Log liest |
| D — gesalzener Hash der Kennung bei abgelehnten Anmeldungen | gleiche Kennung ist korrelierbar, ohne Klartext | zusätzlicher Bau und Salz-Verwaltung; ein Hash einer kurzen oder erratenen Eingabe ist umkehrbar durch Probieren (nicht gemessen); Passwort im Kennungsfeld würde gehasht, aber der Hash eines Passworts ist selbst ein Geheimnisderivat |

Empfehlung **B**; wer eigene Adapter ohne diese Vorbehalte absichern will, wählt A. Die Spezifikation führt das Feld erst nach Annahme dieser ADR (`TDS-FA-09.a` Nr. 3, „bisher keines“). SPEC-012 (feste Antwort ohne Kennung) bleibt unberührt: das betrifft die Antwort an den Client, nicht das Log.

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

Herkunft der IDs: **Empfehlung: beibehalten, wie der Code sie heute bildet.** `connection_id` ist der Zähler des Listeners (ab 1 je Prozessstart), `session_id` und `request_id` sind zufällige UUID. Gesetzt, nicht gemessen: Der Zähler wiederholt sich nach einem Neustart; wer Logs über Neustarts zusammenführt, trennt sie über `ts`. Alternativen: alles Zähler (kompakt, aber nicht eindeutig über Neustarts), alles UUID (eindeutig, länger). Kosten der UUID je Anfrage sind nicht gemessen. Mehrere Server in einer JVM (der Einstieg aus [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) erlaubt das) schreiben auf dieselbe Standardausgabe; der Zähler gilt je Listener, die Zeile trägt kein Feld, das die Instanz trennt (siehe „Offen“).

### Frage 6 — Telemetrie: Schnittstelle, Bibliothek und Zugang zu Metriken und Spans

| Option | Pro | Contra |
|---|---|---|
| **A — eigene kleine Schnittstelle in `domain` (Span beginnen und beenden mit den Pflicht-Attributen, Zähler, Verteilung), Sammler-Adapter für Tests, im Produkt Schnappschuss und Span-Ende als Logzeilen (empfohlen)** | keine Fremdbibliothek; kein neuer Port, keine neue Angriffsfläche; dieselbe Redaktion wie das Log (Attribute sind typisiert); die Dauer je Schicht ist für den Performance-Nachweis lesbar | Betreiber ohne Auswertung der Logzeilen sieht keine Kurven; spätere Telemetrie-Bibliothek braucht einen Adapter |
| B — Schnittstelle einer Telemetrie-Bibliothek (OpenTelemetry-API) direkt | Standard, viele Sammler | Fremdbibliothek im Kern nicht erlaubt (`protocol` und `transport` sollen sie nicht importieren); Größe der Abhängigkeiten nicht gemessen; Exporter und Collector sind Betreiberaufgabe |
| C — JMX | im JDK und im Laufzeit-Image vorhanden (Messung ii: `java.management`, `jdk.management.agent`); kein neuer Netzport bei lokalem Zugriff | Fernzugriff öffnet einen Port und braucht Authentifizierung und Transportsicherung (nicht untersucht); Betrieb im Container ohne lokalen Zugriff ist praktisch Fernzugriff |
| D — Text-Endpunkt (HTTP) für Metriken | gängig für Abfrage durch Sammler | neuer Port mit Authentifizierung und TLS-Entscheidung; eine zweite Angriffsfläche neben TDS; Lastenheft fordert keine Verwaltungsoberfläche |
| E — nichts tun | kein Aufwand | `SPEC-017` bis `SPEC-019` und [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance) ungetragen |

Empfehlung **A**. Der Schnappschuss der Metriken erscheint als Logzeile (Ereignis `metrics`, Abstand `METRICS_INTERVAL_SECONDS`, Voreinstellung 60, 0 schaltet ihn aus; Setzung, nicht gemessen). Die Namen der Spans und ihre Pflicht-Attribute bleiben die der Spezifikation; Metriknamen ohne SQL-Text als Label, wie dort verlangt. B, C und D bleiben als Adapter hinter der Schnittstelle möglich ([ARC-014](../../../spec/architecture.md#3-externe-abhängigkeiten): „austauschbar“) und sind je eine eigene Entscheidung mit eigener Angriffsflächen-Prüfung. Austauschbar heißt im Code: Adapter hinter der Schnittstelle; ob ein Embedder (ADR-0021) Adapter einsetzen kann, entscheidet [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) (dort nicht öffentlich, siehe „Offen“).

### Frage 7 — Ausgabe bei vollem Kanal und beim Prozessende

| Option | Pro | Contra |
|---|---|---|
| A — synchron schreiben | einfachster Bau; keine Reihenfolgeprobleme; beim Prozessende ist nichts im Flug | blockiert der Ausgabekanal, hält er den Anfragepfad an: gemessen 4 586 ms für einen Aufruf bei einem Leser, der 5 s stockt (Messung i, Pipe-Leser, kein Log-Treiber) |
| **B — begrenzte Warteschlange und ein Schreiber; bei Überlauf wird die neue Zeile verworfen und gezählt, die Zahl steht im Feld `dropped` der nächsten geschriebenen Zeile (empfohlen)** | der Anfragepfad blockiert nicht (Messung i: 5 ms für 20 000 Aufrufe bei demselben stockenden Leser); Verlust ist sichtbar | Zeilen können fehlen, auch Sicherheitsereignisse (Messung i: 18 869 von 20 000 verworfen, unter einem 5 s stockenden Leser und einem Stoß ohne Pause, das ist keine Last des Produkts); Warteschlangengröße ist Setzung (1024 Zeilen); mehr Bau, ein Nebenläufigkeitstest und eine Regel zum Prozessende (unten) |
| C — wie B, aber Ereignisse der Ebenen `WARN` und `ERROR` blockieren statt zu fallen | Fehler gehen nicht verloren | der Anfragepfad kann doch anhalten, gerade im Fehlerfall |

Empfehlung **B**; die Wirkung unter Last misst der Performance-Nachweis, nicht dieser Text.

**Regel zum Prozessende (gilt für B und C).** `main` beendet den Prozess nach einem abgebrochenen Start sofort mit `exitProcess(1)`, und der Shutdown-Hook schreibt „gestoppt“ zuletzt; die Diagnose von [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) („belegter Port“, „ungültige Konfiguration“) darf nicht in der Warteschlange hängen bleiben. Festlegung (Setzung): (1) **Startabbruch-Diagnose synchron:** Ereignisse, die vor dem erfolgreichen Start entstehen (ungültige Konfiguration, Bindefehler, fehlgeschlagene TLS-Probe), schreibt der Adapter synchron und ohne Warteschlange, solange der Server nicht läuft; der Schreiber-Thread startet erst mit dem laufenden Server. (2) **Leeren mit Frist:** der Adapter bietet `close` (oder `flush`) mit Frist (Setzung: 2 s); `main` und der Shutdown-Hook rufen es vor dem Prozessende, auch vor `exitProcess`. (3) **Rest nach Fristablauf:** was nach der Frist in der Warteschlange bleibt, geht verloren und wird mit der Zahl in einer letzten, synchron geschriebenen Zeile (`dropped`) gemeldet, soweit der Kanal noch annimmt; hängt der Kanal, endet der Prozess trotzdem nach der Frist (die Diagnose geht dann nur verloren, wenn der Kanal selbst nicht liest). Prüfung: ein Prozesstest mit Startabbruch (belegter Port, ungültige Konfiguration) liest die Diagnosezeile; ein Test mit gestautem Kanal prüft, dass der Prozess nach der Frist endet.

### Frage 8 — Vertragsabschnitt für die Observability-Schnittstelle

| Option | Pro | Contra |
|---|---|---|
| **A — die ADR und die Felder in der Spezifikation genügen; die Kotlin-Signatur ist Code mit KDoc (empfohlen)** | kein neues Dokument | ein externer Adapter-Autor findet die Signatur nur im Quelltext |
| B — eigener Vertragsabschnitt in der Spezifikation (wie für den Authentifizierungsadapter) | wie der Adaptervertrag auffindbar | zweite Fundstelle neben dem Code, driftet; kein externer Adapter-Autor ist bekannt |

Empfehlung **A**.

**Ablösung des minimalen Log-Anschlusses.** `ServerLog.line(text)`, `LogEntry` des Handshakes und die Rückrufe des Listeners rufen nach dem Bau die Schnittstelle; das beobachtbare Verhalten bleibt, die Zeilen ändern Form und Felder. Der Wegfall von `ServerLog` ist Teil des Baus; die Aufrufstellen sind aus dem Quelltext gelesen (Runtime, Handshake, Fehler-Mapping, Listener) und nicht vollständig inventarisiert.

## Verglichene Alternativen

Je Frage oben als Tabelle; „nichts tun“ ist bei Frage 1 (E) und Frage 6 (E) geführt, bei den Fragen 3, 4 und 8 ist die Option A der Verzicht auf mehr. Quer über alle Fragen gilt die Option mit dem geringsten Zusatzaufwand, die das Lastenheft trägt: die ausgelieferte Bibliothek für das Schreiben (Frage 1 F), Schnittstelle in `domain`, kein neuer Port.

## Konsequenzen

- Positiv (aus der Bauart, nicht gemessen): kein neuer Bibliothekseintrag in Lock und Prüfsummen erwartet (bei F; nicht ausgeführt), kein neuer Port; für Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext folgt die Redaktion aus dem Modell statt aus Disziplin (die Zeichenketten-Felder unter Frage 2 sind davon ausgenommen); ein Sammler-Adapter macht die Ereignisse in Tests lesbar.
- Negativ: Der Log-Schreiber hängt an der Jackson-Version; die eigene Warteschlange ist Wartungscode; ohne Auswertung der Logzeilen gibt es keine Kurven; Zeilen können bei Überlauf fehlen (Frage 7); Parameterwerte stehen nie im Log (Frage 4 A); abgelehnte Anmeldungen sind nicht nach Kennung verfolgbar (Frage 3 B); ein eigener Authentifizierungsadapter kann ein Geheimnis als Kennung ins Log bringen (Frage 2).
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation: Pflicht-Logfelder um `ts`, `level` und `principal` ergänzen, `client_profile` und die Herkunft der IDs festlegen, `TDS-FA-09.a` Nr. 3 („bisher keines“) auf das Feld der Entscheidung ziehen, Abschnitt „Metriken und Tracing“ um den Zugang und die Konfigurationsschlüssel (`LOG_LEVEL`, `METRICS_INTERVAL_SECONDS`) ergänzen; Träger: die Spezifikationsfortschreibung dieser Entscheidung. (2) Quelltext: Schnittstelle, Adapter, Sammler, Ablösung von `ServerLog`, Regel zum Prozessende, Redaktionstests je Ereignisart, Inspektionstest über alle Zeilen des laufenden Prozesses; Träger: die Slices Log-Schnittstelle, Zustandsereignisse und Metriken/Tracing der Welle Betrieb und Diagnose. (3) Messung des Overheads und des Rückstaus: Träger: der Performance-Nachweis. (4) Der Plan des Entscheidungs-Slice führt sechs Fragen, diese ADR acht (Telemetrie-Zugang und Rückstau sind neu, der Vertragsabschnitt ist Frage 8): die Anschlussstellen im Plan werden bei der Closure auf die Nummern dieser ADR gezogen.
- **Akzeptiertes Negativ (einmalig):** Zu den Optionen D (Frage 1) und B (Frage 6) liegt kein Spike vor (Größe und Lock-Kosten einer Fremdbibliothek), ebenso nicht zu Zeit und Speicher des Jackson-Generators. Grund: die Empfehlungen wählen weder D noch B; F nutzt eine bereits ausgelieferte Bibliothek, deren Lock-Eintrag existiert. Wählt der Nutzer D (Frage 1) oder B (Frage 6), ist vor `Accepted` ein Spike im Scratchpad nachzuholen. Die Messungen i bis iii stehen im Kontext.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Redaktionstest je Ereignisart: Eingaben mit Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext erscheinen in keiner Zeile, je mit verschiedenen Eingabewerten; Pflichtfelder vorhanden, soweit zutreffend | `make test` |
| JVM-Tests (`kotlin.test`) | Test für `principal`: ein Adapter, der die Eingabe als Kennung zurückgibt, mit Steuerzeichen, Anführungszeichen und über der Längengrenze; die Zeile bleibt eine gültige Zeile (Escaping, Kürzung) | `make test` |
| Prozesstest (Kind-JVM über `main`, Standardausgabe gelesen), in der Form von `ServerSmokeTest` | Inspektion aller Zeilen eines Laufs mit Anmeldung angenommen und abgelehnt, TLS-Fehler, Startabbruch: weder Passwort noch Kennung einer abgelehnten Anmeldung noch Prüfwert; die Diagnosezeile des Startabbruchs (belegter Port, ungültige Konfiguration) ist vorhanden. Einordnung: ein Kindprozess ist nach [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 1 ein E2E-Lauf; Entscheidung 4 hält `make smoke` in der Stage `test`, und dieser Test hängt an derselben Form (gleiche Klasse von Tests, gleicher Ort); gäbe der Nutzer das nicht her, läuft die Inspektion im selben Prozess mit umgeleiteter Standardausgabe (ohne Prozessgrenze, schwächerer Beleg) | `make test` |
| detekt `ForbiddenMethodCall` (Vorschlag, Verschärfung nach `AGENTS.md` §3.6) | `kotlin.io.println`, `kotlin.io.print`, `java.io.PrintStream.println`, `java.io.PrintStream.print` und `java.lang.Throwable.printStackTrace` im Produktivcode außer im Ausgabe-Adapter. `System.out` und `System.err` sind Feldzugriffe, die Regel sperrt den Aufruf auf `PrintStream`, nicht das Feld. Heute sperrt die Konfiguration **keinen** dieser Aufrufe: `ForbiddenMethodCall` hat `includes` nur für `domain` und `port`, und `runtime/ServerLog.kt` ruft `println`. Der Vorschlag dehnt `includes` auf die übrigen Packages außer dem Adapter aus (ob Testcode damit rot wird, ist zu prüfen und ggf. auszunehmen); jeder Eintrag braucht nach dem Kommentar in der Konfiguration einen Rot-Fall unter `config/detekt/selbsttest-aufrufe/`, den `make detekt-selbsttest` fährt. Ob die Regel Aufrufe auf `PrintStream` über `System.out` trifft, ist nicht gemessen; die Aktivierung folgt der Ablösung von `ServerLog` | `make detekt`, `make detekt-selbsttest` |
| detekt `ForbiddenImport/simulation` | Eintrag für das Unterpackage der Schnittstelle in `domain` (Frage 2) samt Rot-Fall im Selbsttest | `make detekt-selbsttest` |

Noch kein bestandenes Gate; die Einrichtung folgt dem Bau.

## Offen

- Für den Nutzer: die Wahl je Frage 1 bis 8 (Empfehlungen oben); die Lesart von `client_profile` (Frage 5); ob die Sperre für `simulation` gebaut wird oder als Konvention bleibt (Frage 2).
- Nicht entschieden, mit Träger: (a) Format des Zeitstempels über UTC hinaus; Träger: die Spezifikationsfortschreibung (Folgepflicht 1), Voreinstellung bis dahin ISO-8601 in UTC. (b) ob das Ereignis `metrics` die Verteilung als Perzentile trägt (die Spezifikation nennt p50, p95, p99, das bleibt die Voreinstellung) oder als Zähler je Klasse; Träger: der Slice Metriken und Tracing der Welle Betrieb und Diagnose. (c) die Quelladresse der Gegenstelle im Log (offen auch in [ADR-0016](0016-anmeldedrosselung.md), Datenschutz); Träger: dieselbe Entscheidung zur Anmeldedrosselung. (d) Instanztrennung bei mehreren Servern in einer JVM: `connection_id` zählt je Listener, die Zeile braucht ein Feld (zum Beispiel den gebundenen Port) oder einen Instanzzähler; Träger: der Slice Log-Schnittstelle. (e) ob ein Embedder Log- und Telemetrie-Adapter einsetzen kann: [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) macht das Wiring nicht öffentlich; solange das gilt, ist die Austauschbarkeit ([ARC-014](../../../spec/architecture.md#3-externe-abhängigkeiten)) im Prozess gegeben, für Embedder nicht. (f) Speicherkopien von Geheimnissen (nicht Gegenstand, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`); die Gesamtinspektion des Anmeldepfads (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`) ist hier nur als Fitness Function benannt, nicht gebaut.

## Re-Evaluierungs-Trigger

Ein Betreiber, der Parameterwerte oder die Kennung abgelehnter Anmeldungen für die Diagnose braucht; eine Messung, nach der Schreiben oder Warteschlange den Anfragepfad belastet; ein Betreiber, der Metriken ohne Auswertung von Logzeilen braucht (Adapter B, C oder D der Frage 6); ein Embedder, der Log- oder Telemetrie-Adapter einsetzen will; die Aufteilung in Module ohne Jackson (dann F neu); Änderung der Importregeln der Schichten.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Optionen und Empfehlungen zu Format, Ort, Kennungsfeld, Parameterwerten, `client_profile`, Telemetrie-Zugang, Rückstau und Vertragsabschnitt; alle Zahlen Setzung (Voreinstellung, nicht gemessen); die Wahl bestätigt der Nutzer mit der Annahme | [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) |
| 2026-10-31 | Proposed, Korrekturrunde nach Review: Ist-Zustand des Katalogs berichtigt, Optionen F (Jackson-Generator) und G (JDK-Logging) ergänzt, Empfehlung zu Frage 1 auf F, drei Spikes mit Messwerten, Regel zum Prozessende, Zusage zur Redaktion auf die Bauart eingeschränkt, Folge für das Verbot an `simulation` benannt, Offen-Punkte mit Träger | [Review-Report](../../reviews/2026-10-31-33ffaab-adr-0020-und-0021.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
