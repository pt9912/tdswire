# Lastenheft — JVM TDS Server

**Version:** 0.7.0  
**Status:** Draft  
**Autor:** pt9912
**Datum:** 2026-10-29

---

## 1. Zweck und Geltungsbereich

Das Projekt stellt eine JVM-Bibliothek bereit, mit der eine Anwendung einen TDS-Serverendpunkt einbettet, über den .NET-Anwendungen mit einem Microsoft-SQL-Server-kompatiblen Clienttreiber mittels Tabular Data Stream (TDS) auf eine kundenseitig bereitgestellte Abfrage- und Datenverarbeitung zugreifen können. Der eingebettete Serverendpunkt (im Folgenden „der Server“) nimmt Verbindungen entgegen, authentifiziert Clients, übergibt unterstützte Anfragen an ein angebundenes Backend und liefert Resultate und Fehler im erwarteten Protokollformat zurück. Ziel ist Interoperabilität für einen klar abgegrenzten Funktionsumfang, nicht die vollständige Nachbildung von Microsoft SQL Server. Ein eigenständig ausführbarer Server ist nicht Produkt dieses Projekts; ein Testserver dient nur der Prüfung der Bibliothek.

Primärer Client ist eine .NET-Anwendung. Die konkrete Treiberversion, erforderliche Verbindungsoptionen und der tatsächlich genutzte SQL-/RPC-Funktionsumfang werden vor Abnahme in einem Client-Kompatibilitätsprofil festgelegt. Die interne Abfrage-Engine und Datenhaltung sind nicht Bestandteil des TDS-Servers; ihre Anbindung gehört zur Systemgrenze. Mitgeliefert wird ein optionales Simulations-Backend (`TDS-FA-11`), das einen abgegrenzten Satz einfacher Anweisungsformen aus deklarativ beschriebenen Tabellen beantwortet; es ist keine SQL-Engine. Die konkrete Implementierung ist nicht Gegenstand dieses Dokuments.

## 2. Stakeholder

| Stakeholder                  | Rolle                     | Erwartung                                                             |
| ---------------------------- | ------------------------- | --------------------------------------------------------------------- |
| Betreiber der .NET-Anwendung | Auftraggeber / Integrator | Verbindung mit bestehendem SQL-Client ohne proprietären Clientadapter |
| Backend-Entwicklung          | Integrationspartner       | Eindeutiger Vertrag für Abfragen, Parameter, Ergebnisse und Fehler    |
| Serverentwicklung            | Umsetzung                 | Testbarer, abgegrenzter Protokollumfang                               |
| Betrieb / DevOps             | Betrieb                   | Reproduzierbare Bereitstellung, Diagnose und sichere Konfiguration    |
| Qualitätssicherung           | Abnahme                   | Automatisierte Ende-zu-Ende-Kompatibilitätstests                      |

## 3. Funktionale Anforderungen

### TDS-FA-01 — Verbindungsaufbau und Protokollverhandlung

**Beschreibung:** Der Server nimmt TCP-Verbindungen entgegen und verarbeitet die für das vereinbarte .NET-Clientprofil erforderliche TDS-Verbindungsinitialisierung einschließlich PRELOGIN und Verschlüsselungsverhandlung.

**Akzeptanzkriterien:**
- **Happy Path:** Given ein erreichbarer Server und ein freigegebenes Clientprofil, when der .NET-Client eine Verbindung öffnet, then wird die Protokollverhandlung erfolgreich abgeschlossen.
- **Boundary:** Given eine unterstützte, aber abweichende Clientoption, when die Verbindung aufgebaut wird, then wird diese gemäß dokumentiertem Kompatibilitätsprofil behandelt.
- **Negative:** Given eine unzulässige oder beschädigte PRELOGIN-Nachricht, when sie empfangen wird, then wird die Verbindung kontrolliert abgewiesen, ohne den Serverprozess zu beeinträchtigen.

**Out-of-Scope:** Beliebige historische TDS-Versionen und undokumentierte Clientvarianten.

### TDS-FA-02 — Anmeldung und Sitzungsverwaltung

**Beschreibung:** Der Server verarbeitet LOGIN7 und verwaltet voneinander getrennte Clientsitzungen. Der initial unterstützte Authentifizierungsmodus wird vor Implementierung verbindlich festgelegt.

**Akzeptanzkriterien:**
- **Happy Path:** Given gültige Zugangsdaten im freigegebenen Authentifizierungsmodus, when LOGIN7 verarbeitet wird, then erhält der Client eine erfolgreiche Anmeldung und eine nutzbare Sitzung.
- **Boundary:** Given zwei gleichzeitige Sitzungen, when sie Anfragen ausführen, then bleiben Sitzungszustände getrennt.
- **Negative:** Given ungültige Zugangsdaten, when eine Anmeldung versucht wird, then erhält der Client einen definierten Anmeldefehler und keine nutzbare Sitzung.

**Out-of-Scope:** Windows Integrated Authentication, Kerberos und Entra-ID-Authentifizierung in der ersten Version.

### TDS-FA-03 — Einfache SQL-Batches

**Beschreibung:** Der Server nimmt SQL_BATCH-Anfragen entgegen und reicht den SQL-Text an das angebundene Backend weiter.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine angemeldete Sitzung und eine unterstützte SELECT-Abfrage, when ein SQL_BATCH empfangen wird, then erhält das Backend die Abfrage und der Client deren Ergebnis.
- **Boundary:** Given eine Abfrage mit leerem Resultset, when sie ausgeführt wird, then erhält der Client gültige Spaltenmetadaten und einen ordnungsgemäßen Abschluss.
- **Negative:** Given nicht unterstütztes SQL, when es ausgeführt wird, then erhält der Client einen definierten SQL-Fehler und die Sitzung bleibt, soweit zulässig, nutzbar.

**Out-of-Scope:** Vollständige T-SQL-Sprachkompatibilität.

### TDS-FA-04 — Parametrisierte Abfragen über RPC

**Beschreibung:** Der Server unterstützt die im vereinbarten .NET-Clientprofil tatsächlich verwendeten RPC-Aufrufe für parametrisierte Abfragen; mindestens ist die Unterstützung von `sp_executesql` zu prüfen und, falls vom Client benötigt, bereitzustellen.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine parametrisierte SELECT-Abfrage des Referenzclients, when sie als RPC gesendet wird, then werden Parameter typgerecht an das Backend übergeben und das korrekte Ergebnis geliefert.
- **Boundary:** Given ein NULL-Parameter oder ein Parameter an einer dokumentierten Typgrenze, when der RPC ausgeführt wird, then bleibt seine Bedeutung erhalten.
- **Negative:** Given ein unbekannter RPC oder ungültige Parameterkodierung, when der Aufruf eintrifft, then wird ein definierter Fehler zurückgegeben.

**Out-of-Scope:** Alle nicht im Clientprofil freigegebenen Stored Procedures und RPC-Varianten.

### TDS-FA-05 — Resultsets und Datentypen

**Beschreibung:** Der Server überträgt für den freigegebenen Typkatalog korrekte Spaltenmetadaten, Datenzeilen, NULL-Werte und Abschlussinformationen. Der Typkatalog umfasst für die erste Abnahme mindestens Integer, Unicode-Text, Boolean/Bit, Dezimalzahlen und Datum/Zeit, soweit das Backend diese bereitstellt.

**Akzeptanzkriterien:**
- **Happy Path:** Given ein Resultset mit allen freigegebenen Typen, when der Client es liest, then stimmen Spaltennamen, Werte, Typen und NULL-Semantik mit dem Backend überein.
- **Boundary:** Given ein leeres Resultset oder maximal zulässige Feldlängen, when es gelesen wird, then bleibt die Kodierung gültig.
- **Negative:** Given ein nicht abbildbarer Backendtyp, when das Ergebnis serialisiert werden soll, then wird ein definierter Fehler geliefert statt eines fehlerhaften TDS-Pakets.

**Out-of-Scope:** Vollständiger SQL-Server-Typkatalog, XML, Spatial und benutzerdefinierte SQL-Server-Typen.

### TDS-FA-06 — Fehler und Abschlusszustände

**Beschreibung:** Der Server meldet Ausführungsfehler, Protokollfehler und erfolgreiche Abschlüsse so, dass der Referenzclient sie eindeutig verarbeiten kann.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine erfolgreiche Abfrage, when die Übertragung endet, then erkennt der Client den vollständigen Abschluss.
- **Boundary:** Given eine Abfrage ohne Ergebniszeilen, when sie abgeschlossen wird, then wird ein gültiger Abschlussstatus übertragen.
- **Negative:** Given einen Backendfehler, when die Abfrage fehlschlägt, then erhält der Client eine definierte Fehlermeldung statt eines Hängers oder stillen Verbindungsabbruchs.

**Out-of-Scope:** Vollständige Nachbildung aller SQL-Server-Fehlernummern.

### TDS-FA-07 — Abbruch und Ressourcenfreigabe

**Beschreibung:** Der Server verarbeitet Verbindungsabbrüche und die vom Referenzclient genutzten Abbruchmechanismen, einschließlich ATTENTION, sofern dieser gesendet wird. Beim Beenden des Servers lässt er laufende Anfragen innerhalb einer konfigurierbaren Frist abschließen, bricht danach die übrigen ab und beantwortet Anfragen nach Beginn des Beendens mit einem definierten Fehler.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine laufende Abfrage, when der Client sie abbricht, then wird der Abbruch erkannt und die Sitzung erreicht einen definierten Zustand.
- **Boundary:** Given einen Abbruch unmittelbar nach Abschluss, when er eintrifft, then entstehen keine doppelten Antworten oder Ressourcenlecks.
- **Negative:** Given einen abrupten TCP-Abbruch, when die Verbindung endet, then werden zugehörige Sitzungsressourcen freigegeben.
- **Beenden:** Given eine laufende Anfrage, when der Server beendet wird, then schließt sie innerhalb der Frist regulär ab oder wird danach abgebrochen, und das Beenden kehrt nach spätestens Frist plus Abbruchfrist zurück.

**Out-of-Scope:** Garantierte Unterbrechbarkeit beliebiger externer Backendoperationen.

### TDS-FA-10 — Mehrteilige Antworten und Ausgabewerte

**Beschreibung:** Der Server stellt die vom freigegebenen .NET-Clientprofil tatsächlich genutzten Antwortformen bereit: mehrere Ergebnisse, Update-Zähler, Rückgabestatus und OUTPUT-Parameter, sofern die zugehörigen Operationen Bestandteil des Profils sind. Nicht benötigte Varianten werden ausdrücklich dokumentiert.

**Akzeptanzkriterien:**
- **Happy Path:** Given ein im Clientprofil freigegebener RPC mit OUTPUT-Parameter oder mehreren Ergebnissen, when der Client die Antwort liest, then erhält er alle Ergebnisse in korrekter Reihenfolge einschließlich Abschluss- und Rückgabewerten.
- **Boundary:** Given ein Ergebnis ohne Zeilen zwischen zwei Ergebnissen, when der Client `NextResult()` verwendet, then werden die Grenzen eindeutig erkannt.
- **Negative:** Given eine nicht unterstützte Antwortform, when das Backend sie liefert, then wird ein definierter Fehler gemeldet, ohne einen unvollständigen Erfolgsstream zu erzeugen.

**Out-of-Scope:** Beliebige SQL-Server-Prozeduren außerhalb des freigegebenen Profils.

### TDS-FA-08 — Backend-Anbindung

**Beschreibung:** Der Server bietet eine dokumentierte Integrationsschnittstelle für Abfrageausführung, Parameter, Resultsets, Fehler und Abbruchsignale.

**Akzeptanzkriterien:**
- **Happy Path:** Given ein angebundenes Testbackend, when eine unterstützte Anfrage eingeht, then wird sie ohne TDS-spezifische Abhängigkeit im Backend verarbeitet.
- **Boundary:** Given ein leeres Resultset, when das Backend antwortet, then kann der Server dieses korrekt übertragen.
- **Negative:** Given eine nicht erreichbare oder fehlerhafte Backendimplementierung, when eine Anfrage ausgeführt wird, then erhält der Client einen definierten Fehler und der Server bleibt verfügbar.

**Out-of-Scope:** Entwicklung einer SQL-Engine, eines Query-Optimizers oder einer Persistenzschicht. Das mitgelieferte Simulations-Backend (`TDS-FA-11`) ist keine SQL-Engine im Sinne dieses Ausschlusses; sein Bestand im Arbeitsspeicher (`TDS-FA-12`) ist keine Persistenzschicht.

### TDS-FA-09 — Konfiguration und Diagnose

**Beschreibung:** Integratoren und Betreiber können Netzwerkendpunkt, Sicherheitsparameter, Zeitlimits und Protokollierung konfigurieren und den Betriebszustand diagnostizieren.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine gültige Konfiguration und eine vom Integrator übergebene Diagnoseausgabe, when der Serverendpunkt gestartet wird, then ist der konfigurierte Endpunkt erreichbar und relevante Zustandsereignisse werden an die Diagnoseausgabe gegeben; ohne übergebene Diagnoseausgabe schreibt die Bibliothek kein Log.
- **Boundary:** Given ein belegter Port, when der Serverendpunkt gestartet wird, then schlägt der Start mit einer verständlichen Diagnose fehl.
- **Negative:** Given eine ungültige sicherheitsrelevante Konfiguration, when der Serverendpunkt gestartet wird, then wird sie abgewiesen, ohne unsichere Defaults stillschweigend zu aktivieren.

**Out-of-Scope:** Grafische Administrationsoberfläche.

### TDS-FA-11 — Mitgeliefertes Simulations-Backend

**Beschreibung:** Das Projekt liefert ein optionales Backend mit, das den Backend-Vertrag (`TDS-FA-08`) implementiert und Anfragen aus deklarativ beschriebenen Tabellen beantwortet (Spalten, Typen, Schlüssel; Zeilen aus einer vom Integrator bereitgestellten Zeilenquelle). Es erkennt eine abgegrenzte Menge einfacher Anweisungsformen anhand ihres Textes: Auswahl von Spalten einer Tabelle mit einfachen Gleichheits- und Mengenbedingungen sowie der Prüfung auf NULL (`IS NULL`, `IS NOT NULL`), verknüpft durch UND; einfache Einfüge-, Änderungs- und Löschanweisungen, die an den Integrator weitergegeben werden; sowie die Abfragen, die der Referenzclient laut Clientprofil beim Verbindungsaufbau oder für seine Metadaten stellt. Es wertet kein SQL aus und speichert im Standard keine Daten: Was gespeichert wird, entscheidet der Integrator; nur auf ausdrückliche Konfiguration des Integrators hält es einen veränderlichen Bestand im Arbeitsspeicher (`TDS-FA-12`). Der Protokollkern ist vom Simulations-Backend unabhängig und läuft ohne es.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine beschriebene Tabelle mit Zeilen und eine erkannte Auswahl mit Bedingung, when der Referenzclient sie ausführt, then erhält er genau die passenden Zeilen mit den beschriebenen Spaltentypen und NULL-Werten.
- **Boundary:** Given eine Zeilenquelle, deren Zeilen nicht gleichzeitig in den Speicher des Servers passen, when der Client das Resultset liest, then überträgt der Server es schrittweise, ohne es vollständig zu halten; Given eine Bedingung, die keine Zeile trifft, then erhält der Client gültige Spaltenmetadaten und einen ordnungsgemäßen Abschluss.
- **Negative:** Given eine Anweisung oder Bedingung außerhalb der erkannten Formen (Verbund, Aggregat, Ausdruck, Unterabfrage), when sie ausgeführt wird, then erhält der Client einen definierten SQL-Fehler statt eines stillen Erfolgs oder eines falschen Ergebnisses; Given ein Fehler beim Lesen der Zeilenquelle, then erhält der Client einen definierten Fehler und die Sitzung bleibt, soweit zulässig, nutzbar.

**Out-of-Scope:** Auswertung beliebigen SQLs (Verbunde, Aggregate, Ausdrücke, Unterabfragen, Sortierung), Persistenz, Transaktionssemantik über die Weitergabe an den Integrator hinaus und SQL-Server-Systemkataloge, soweit sie der Referenzclient nach Clientprofil nicht selbst abfragt. Ausgenommen ist, was `TDS-FA-12` bei eingeschaltetem Bestand verlangt.

### TDS-FA-12 — Simulations-Backend mit veränderlichem Bestand und Katalog

**Beschreibung:** Auf ausdrückliche Konfiguration des Integrators hält das Simulations-Backend (`TDS-FA-11`) einen veränderlichen Bestand seiner Tabellen im Arbeitsspeicher, nimmt Schreibzugriffe und Änderungen der Tabellenstruktur an und beantwortet die Katalogabfragen, mit denen Clientwerkzeuge die Struktur dieser Tabellen lesen. Ohne diese Konfiguration verhält es sich wie in `TDS-FA-11` beschrieben (Standard; Setzung dieses Entwurfs, vom Nutzer nicht bestätigt). Als zweiter, nicht verbindlicher Prüfclient neben dem Referenzclient dient das Werkzeug d-migrate (Version und Treiber legt die Spezifikation fest) für Verbindung, Strukturlesen und Strukturvergleich, Strukturänderung mit Ausführung, Datenexport, Datenimport und Datenübertragung. Der Testserver (Glossar) ist nur der Prozess, der die Bibliothek bindet und die Funktion für diese Läufe bereitstellt; er hat keinen eigenen Funktionsumfang. Der Bestand lebt nur im Arbeitsspeicher des Prozesses und ist nach dessen Neustart wieder der Ausgangsbestand. Der verbindliche Client bleibt der des `TDS-RB-02`.

**Akzeptanzkriterien:**
- **Happy Path:** Given ein Simulations-Backend mit eingeschaltetem Bestand und beschriebenen Tabellen (über den Testserver betrieben), when d-migrate Verbindung, Strukturlesen (`schema reverse`), Strukturvergleich (`schema compare`), Datenexport (`data export`) und Strukturänderung mit Ausführung (`schema migrate --execute`) ausführt, then endet jeder Lauf mit Exit 0, der Export enthält die Zeilen mit den beschriebenen Typen und NULL-Werten, und ein erneutes Strukturlesen zeigt die geänderte Struktur; Given ein Datenimport und eine Datenübertragung (`data import`, `data transfer`) in eine Tabelle des Simulations-Backends, jeweils im Standardpfad des Werkzeugs und mit `--on-conflict skip`, then endet der Lauf mit Exit 0 und ein anschließender Export enthält die übertragenen Zeilen.
- **Boundary:** Given die Verbindung mit dem Standard des Treibers (verschlüsselt, `encrypt=true`) und ein Server mit TLS, when d-migrate sich anmeldet und eine Abfrage stellt, then gelingt sie; Given ein Neustart des Prozesses, then enthält der Bestand wieder genau den Ausgangsbestand; Given ein Simulations-Backend ohne ausdrückliche Konfiguration des Bestands, then verhält es sich wie in `TDS-FA-11` (Schreibzugriffe gehen an den Integrator oder werden abgewiesen).
- **Negative:** Given eine Anweisung außerhalb der erkannten Formen (zum Beispiel die Aggregate und Ausdrücke von `data profile`), when sie ausgeführt wird, then erhält der Client einen definierten SQL-Fehler statt eines stillen Erfolgs oder eines falschen Ergebnisses; Given ein Schreibzugriff, der gegen die Struktur der Tabelle verstößt (unbekannte Spalte, Verletzung des Schlüssels), then wird er mit einem definierten Fehler abgewiesen und der Bestand bleibt unverändert.

**Out-of-Scope:** `data profile` von d-migrate, Dauerhaftigkeit über den Prozess hinaus, Transaktionssemantik mit Rücknahme über mehrere Anweisungen, Zusage für andere Clients oder Versionen von d-migrate und Treiber als die in der Spezifikation festgelegten, ein Bestand als Produktfunktion außerhalb der Prüfung der Bibliothek.

## 4. Nichtfunktionale Anforderungen und Randbedingungen

### TDS-QA-01 — Kompatibilität
- **Anforderung:** Alle freigegebenen Ende-zu-Ende-Szenarien laufen mit der festgelegten .NET-Runtime, SqlClient-Version und Connection String-Konfiguration erfolgreich.
- **Messmethode:** Automatisierte Integrationstests mit echtem .NET-Client gegen den Server, inklusive Anmeldung, SELECT, parametrisierter Abfrage (RPC-Form gemäß Clientprofil, `TDS-FA-04`), NULL, Fehler und Abbruch. Ergänzend kann das Verhalten des Servers an einer echten SQL-Server-Instanz als Referenz gemessen werden (Referenzabgleich); ob und in welcher Form, steht mit der Entscheidung über die Kompatibilitätstests aus, bis dahin ist der Abgleich vorläufig.

### TDS-QA-02 — Stabilität
- **Anforderung:** Ungültige, unvollständige oder unerwartet getrennte Clientverbindungen dürfen den Gesamtprozess nicht zum Absturz bringen.
- **Messmethode:** Negativtests, Fragmentierungs- und Protokoll-Fuzztests sowie parallele Verbindungen.

### TDS-QA-03 — Sicherheit
- **Anforderung:** Zugangsdaten dürfen nicht im Klartext protokolliert werden. Für produktiven Betrieb muss ein freigegebener TLS-Modus vorhanden sein; unsichere Testmodi dürfen nicht unbemerkt produktiv aktiv werden.
- **Messmethode:** Loginspektion, TLS-Handshake-Tests, Konfigurationsnegativtests und Security-Review.

### TDS-QA-04 — Performance
- **Anforderung:** Protokollbedingte Latenz und Ressourcenverbrauch müssen messbar sein. Konkrete Grenzwerte für Durchsatz, p95-Latenz und gleichzeitige Sitzungen werden vor Abnahme anhand eines Referenz-Workloads festgelegt.
- **Messmethode:** Reproduzierbarer Lasttest mit getrennten Messungen für Protokollschicht und Backend.

### TDS-QA-05 — Wartbarkeit
- **Anforderung:** Protokollverarbeitung und Backendintegration sind getrennt testbar; unterstützte Nachrichten und Datentypen sind dokumentiert.
- **Messmethode:** Review der Modulgrenzen, automatisierte Tests und dokumentierte Kompatibilitätsmatrix.

### TDS-QA-06 — Reproduzierbarkeit
- **Anforderung:** Build, Tests und Testserver lassen sich in einer dokumentierten Docker-basierten Umgebung reproduzieren.
- **Messmethode:** Clean-Build und Integrationstests in CI ohne lokale JVM-Voraussetzungen auf dem Host.

### TDS-QA-07 — Protokoll-Synchronität
- **Anforderung:** Nach Fehler, Timeout oder Abbruch darf keine Antwort einer früheren Anfrage einer späteren Anfrage zugeordnet werden; wenn Synchronität nicht sicher wiederherstellbar ist, muss die Verbindung geschlossen werden.
- **Messmethode:** Ende-zu-Ende-Tests mit Fehler nach bereits gesendeten Zeilen, ATTENTION während Streaming sowie unmittelbar nachfolgender Abfrage.

### TDS-RB-01 — Implementierungssprache
- **Art:** technisch
- **Vorgabe:** Die Bibliothek muss auf der JVM betrieben werden können und eine Kotlin-basierte Entwicklung erlauben.
- **Nachweis:** Reproduzierbarer JVM-Build und Einbettungstest.

### TDS-RB-02 — Externer Client
- **Art:** technisch
- **Vorgabe:** Der verbindliche Client ist eine .NET-Anwendung mit einem vor Abnahme festgelegten SqlClient-Paket und Version; dessen tatsächliches Verbindungs- und Abfrageverhalten ist maßgeblich.
- **Nachweis:** Versioniertes Client-Testprojekt und dokumentiertes Kompatibilitätsprofil.

### TDS-RB-03 — Protokollstandard
- **Art:** technisch
- **Vorgabe:** Die Kommunikation orientiert sich an der Microsoft-MS-TDS-Spezifikation; die konkret unterstützte Protokollversion und Funktionsmenge werden vor Implementierung des Verbindungsaufbaus festgelegt und dokumentiert.
- **Nachweis:** Protokolltests und dokumentierte Abweichungen.

### TDS-RB-04 — Externes Backend
- **Art:** technisch
- **Vorgabe:** Das Backend ist austauschbar und darf keine TDS-Paketkodierung implementieren müssen.
- **Nachweis:** Integration mit einem In-Memory-Testbackend.

### TDS-RB-05 — Lizenz
- **Art:** rechtlich
- **Vorgabe:** Das Projekt steht unter der MIT-Lizenz.
- **Nachweis:** `LICENSE` im Wurzelverzeichnis des Repositories.

### Offene Entscheidungen

Jede offene Festlegung hat eine verantwortliche Rolle und einen Trigger; ohne die Festlegung gilt die jeweilige Anforderung als nicht abnehmbar.

| Festlegung                                       | Betrifft                 | Verantwortlich               | Trigger                                    |
| ------------------------------------------------ | ------------------------ | ---------------------------- | ------------------------------------------ |
| Authentifizierungsmodus                          | `TDS-FA-02`              | Betreiber der .NET-Anwendung | vor Implementierung der Anmeldung          |
| Clientprofil (Paket, Version, .NET-Version, Connection String, Encrypt/TrustServerCertificate, unterstützte RPC-Formen, MARS, Transaktionen, Pooling) | `TDS-RB-02`, `TDS-QA-01` | Betreiber der .NET-Anwendung | vor Implementierung des Verbindungsaufbaus |
| TLS-Modus für den Produktivbetrieb               | `TDS-QA-03`              | Betrieb / DevOps             | vor Implementierung des Verbindungsaufbaus |
| Protokollversion und Funktionsmenge              | `TDS-RB-03`              | Serverentwicklung            | vor Implementierung des Verbindungsaufbaus |
| Grenzwerte für Durchsatz, p95-Latenz, Sitzungen und Lastprofil | `TDS-QA-04`              | Betreiber der .NET-Anwendung | vor Abnahme                                |
| Erkannte Anweisungsformen, Bedingungen und Typen des Simulations-Backends | `TDS-FA-11`              | Serverentwicklung            | vor Implementierung des Simulations-Backends |

## 5. Globale Out-of-Scope-Punkte

- Kein vollständiger Ersatz für Microsoft SQL Server.
- Keine vollständige T-SQL-Kompatibilität, keine SQL-Server-Systemkataloge oder Administrationsfunktionen, außer den Abfragen des Referenzclients, die das Clientprofil festlegt (`TDS-FA-11`), und den Katalogabfragen von d-migrate für die Tabellen des Simulations-Backends bei eingeschaltetem Bestand (`TDS-FA-12`).
- Keine Unterstützung für Entity Framework Core, SSMS, Power BI oder DBeaver als eigenständiges Abnahmeziel, sofern nicht später vereinbart.
- Keine Hochverfügbarkeit, Replikation, Clustering oder verteilte Transaktionen in der ersten Version.
- Keine integrierte SQL-Engine oder persistente Datenbank; das mitgelieferte Simulations-Backend (`TDS-FA-11`) erkennt nur abgegrenzte Anweisungsformen und speichert nichts. Ausgenommen ist der Bestand im Arbeitsspeicher, den das Simulations-Backend auf ausdrückliche Konfiguration des Integrators hält (`TDS-FA-12`); er besteht nie über den Prozess hinaus.
- Keine Zusage für beliebige TDS-/SqlClient-Versionen.
- Keine Windows-Authentifizierung in der ersten Version.

## 6. Glossar

| Begriff        | Bedeutung im Lastenheft                                                                          |
| -------------- | ------------------------------------------------------------------------------------------------ |
| TDS            | Tabular Data Stream, Kommunikationsprotokoll von SQL Server                                      |
| PRELOGIN       | TDS-Phase zur Aushandlung von Verbindungsoptionen                                                |
| LOGIN7         | TDS-Anmeldenachricht                                                                             |
| SQL_BATCH      | TDS-Nachricht zur Übertragung eines SQL-Textes                                                   |
| RPC            | Remote Procedure Call, u. a. für parametrisierte SQL-Ausführung                                  |
| SqlClient      | .NET-Treiberfamilie für SQL-Server-Verbindungen                                                  |
| Backend        | Vom TDS-Server getrennte Abfrage- und Datenverarbeitung                                          |
| Clientprofil   | Festgelegte Kombination aus .NET-/Treiber-Version, Connection String und unterstützten Szenarien |
| Referenzclient | Testanwendung, die das verbindliche Clientprofil ausführt                                        |
| Simulations-Backend | Mitgeliefertes optionales Backend, das einfache Anweisungsformen aus deklarativ beschriebenen Tabellen beantwortet; keine SQL-Engine |
| Server         | Der von der Bibliothek bereitgestellte, in eine Anwendung eingebettete TDS-Serverendpunkt; kein eigenständiges Produkt |
| Testserver     | Ausführbarer Serverprozess, der nur der Prüfung der Bibliothek dient (E2E- und Kompatibilitätstests); kein Produkt |
| Bestand | Die veränderlichen Tabellen und Zeilen im Arbeitsspeicher des Simulations-Backends bei ausdrücklicher Konfiguration (`TDS-FA-12`); nach einem Neustart wieder der Ausgangsbestand |
| Zeilenquelle   | Vom Integrator bereitgestellte Quelle der Zeilen einer Tabelle, die schrittweise gelesen wird    |

## 7. Historie

| Version | Datum      | Änderung                                                                | Verweis |
| ------- | ---------- | ----------------------------------------------------------------------- | ------- |
| 0.2.0   | 2026-10-29 | Entwurf überarbeitet: mehrteilige Antworten und Synchronität präzisiert | —       |
| 0.3.0   | 2026-10-29 | Simulations-Backend als Anforderung aufgenommen; Ausschluss der SQL-Engine abgegrenzt; Lizenz als Randbedingung | `TDS-FA-11`, `TDS-RB-05` |
| 0.4.0   | 2026-10-29 | Anker für geordnetes Beenden und Referenzabgleich ergänzt               | `TDS-FA-07`, `TDS-QA-01` |
| 0.5.0   | 2026-10-30 | Prüfung auf NULL (`IS NULL`, `IS NOT NULL`) als erkannte Bedingung des Simulations-Backends aufgenommen | `TDS-FA-11` |
| 0.6.0   | 2026-11-01 | Produkt ist die JVM-Bibliothek; ein eigenständig ausführbarer Server ist kein Produkt, der Testserver dient nur der Prüfung | `TDS-RB-01`, `TDS-QA-06`, `TDS-FA-09` |
| 0.6.1   | 2026-11-01 | Happy Path von `TDS-FA-09` präzisiert: Zustandsereignisse gehen an die vom Integrator übergebene Diagnoseausgabe; ohne sie schreibt die Bibliothek kein Log | `TDS-FA-09` |
| 0.7.0   | 2026-11-05 | Simulations-Backend mit veränderlichem Bestand im Arbeitsspeicher (auf Konfiguration, Standard unverändert), Katalogabfragen und d-migrate als zweitem Prüfclient aufgenommen; der Testserver bindet nur die Bibliothek; Ausnahmen in den Ausschlüssen von `TDS-FA-08`, `TDS-FA-11` und §5 | `TDS-FA-12`, `TDS-FA-11`, `TDS-FA-08` |
