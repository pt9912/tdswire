# Lastenheft — JVM TDS Server

**Version:** 0.2.0  
**Status:** Draft  
**Autor:** pt9912
**Datum:** 2026-10-29

---

## 1. Zweck und Geltungsbereich

Das Projekt stellt einen eigenständigen Serverendpunkt bereit, über den .NET-Anwendungen mit einem Microsoft-SQL-Server-kompatiblen Clienttreiber mittels Tabular Data Stream (TDS) auf eine kundenseitig bereitgestellte Abfrage- und Datenverarbeitung zugreifen können. Der Server nimmt Verbindungen entgegen, authentifiziert Clients, übergibt unterstützte Anfragen an ein angebundenes Backend und liefert Resultate und Fehler im erwarteten Protokollformat zurück. Ziel ist Interoperabilität für einen klar abgegrenzten Funktionsumfang, nicht die vollständige Nachbildung von Microsoft SQL Server.

Primärer Client ist eine .NET-Anwendung. Die konkrete Treiberversion, erforderliche Verbindungsoptionen und der tatsächlich genutzte SQL-/RPC-Funktionsumfang werden vor Abnahme in einem Client-Kompatibilitätsprofil festgelegt. Die interne Abfrage-Engine und Datenhaltung sind nicht Bestandteil des TDS-Servers; ihre Anbindung gehört zur Systemgrenze. Die konkrete Implementierung ist nicht Gegenstand dieses Dokuments.

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

**Beschreibung:** Der Server verarbeitet Verbindungsabbrüche und die vom Referenzclient genutzten Abbruchmechanismen, einschließlich ATTENTION, sofern dieser gesendet wird.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine laufende Abfrage, when der Client sie abbricht, then wird der Abbruch erkannt und die Sitzung erreicht einen definierten Zustand.
- **Boundary:** Given einen Abbruch unmittelbar nach Abschluss, when er eintrifft, then entstehen keine doppelten Antworten oder Ressourcenlecks.
- **Negative:** Given einen abrupten TCP-Abbruch, when die Verbindung endet, then werden zugehörige Sitzungsressourcen freigegeben.

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

**Out-of-Scope:** Entwicklung einer SQL-Engine, eines Query-Optimizers oder einer Persistenzschicht.

### TDS-FA-09 — Konfiguration und Diagnose

**Beschreibung:** Betreiber können Netzwerkendpunkt, Sicherheitsparameter, Zeitlimits und Protokollierung konfigurieren und den Betriebszustand diagnostizieren.

**Akzeptanzkriterien:**
- **Happy Path:** Given eine gültige Konfiguration, when der Server startet, then ist der konfigurierte Endpunkt erreichbar und relevante Zustandsereignisse werden protokolliert.
- **Boundary:** Given ein belegter Port, when der Server startet, then schlägt der Start mit einer verständlichen Diagnose fehl.
- **Negative:** Given eine ungültige sicherheitsrelevante Konfiguration, when der Server startet, then wird sie abgewiesen, ohne unsichere Defaults stillschweigend zu aktivieren.

**Out-of-Scope:** Grafische Administrationsoberfläche.

## 4. Nichtfunktionale Anforderungen und Randbedingungen

### TDS-QA-01 — Kompatibilität
- **Anforderung:** Alle freigegebenen Ende-zu-Ende-Szenarien laufen mit der festgelegten .NET-Runtime, SqlClient-Version und Connection String-Konfiguration erfolgreich.
- **Messmethode:** Automatisierte Integrationstests mit echtem .NET-Client gegen den Server, inklusive Anmeldung, SELECT, parametrisierter Abfrage (RPC-Form gemäß Clientprofil, `TDS-FA-04`), NULL, Fehler und Abbruch.

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
- **Anforderung:** Build, Tests und ausführbarer Server lassen sich in einer dokumentierten Docker-basierten Umgebung reproduzieren.
- **Messmethode:** Clean-Build und Integrationstests in CI ohne lokale JVM-Voraussetzungen auf dem Host.

### TDS-QA-07 — Protokoll-Synchronität
- **Anforderung:** Nach Fehler, Timeout oder Abbruch darf keine Antwort einer früheren Anfrage einer späteren Anfrage zugeordnet werden; wenn Synchronität nicht sicher wiederherstellbar ist, muss die Verbindung geschlossen werden.
- **Messmethode:** Ende-zu-Ende-Tests mit Fehler nach bereits gesendeten Zeilen, ATTENTION während Streaming sowie unmittelbar nachfolgender Abfrage.

### TDS-RB-01 — Implementierungssprache
- **Art:** technisch
- **Vorgabe:** Der Server muss auf der JVM betrieben werden können und eine Kotlin-basierte Entwicklung erlauben.
- **Nachweis:** Reproduzierbarer JVM-Build und Laufzeittest.

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

### Offene Entscheidungen

Jede offene Festlegung hat eine verantwortliche Rolle und einen Trigger; ohne die Festlegung gilt die jeweilige Anforderung als nicht abnehmbar.

| Festlegung                                       | Betrifft                 | Verantwortlich               | Trigger                                    |
| ------------------------------------------------ | ------------------------ | ---------------------------- | ------------------------------------------ |
| Authentifizierungsmodus                          | `TDS-FA-02`              | Betreiber der .NET-Anwendung | vor Implementierung der Anmeldung          |
| Clientprofil (Paket, Version, .NET-Version, Connection String, Encrypt/TrustServerCertificate, unterstützte RPC-Formen, MARS, Transaktionen, Pooling) | `TDS-RB-02`, `TDS-QA-01` | Betreiber der .NET-Anwendung | vor Implementierung des Verbindungsaufbaus |
| TLS-Modus für den Produktivbetrieb               | `TDS-QA-03`              | Betrieb / DevOps             | vor Implementierung des Verbindungsaufbaus |
| Protokollversion und Funktionsmenge              | `TDS-RB-03`              | Serverentwicklung            | vor Implementierung des Verbindungsaufbaus |
| Grenzwerte für Durchsatz, p95-Latenz, Sitzungen und Lastprofil | `TDS-QA-04`              | Betreiber der .NET-Anwendung | vor Abnahme                                |

## 5. Globale Out-of-Scope-Punkte

- Kein vollständiger Ersatz für Microsoft SQL Server.
- Keine vollständige T-SQL-Kompatibilität, keine SQL-Server-Systemkataloge oder Administrationsfunktionen.
- Keine Unterstützung für Entity Framework Core, SSMS, Power BI oder DBeaver als eigenständiges Abnahmeziel, sofern nicht später vereinbart.
- Keine Hochverfügbarkeit, Replikation, Clustering oder verteilte Transaktionen in der ersten Version.
- Keine integrierte SQL-Engine oder persistente Datenbank.
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

## 7. Historie

| Version | Datum      | Änderung                                                                | Verweis |
| ------- | ---------- | ----------------------------------------------------------------------- | ------- |
| 0.2.0   | 2026-10-29 | Entwurf überarbeitet: mehrteilige Antworten und Synchronität präzisiert | —       |
