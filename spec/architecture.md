# Architektur — JVM TDS Server

**Status:** Aktiv (Entwurf). **Letzte Änderung:** 2026-10-29.  
**Rolle:** Abgeleitete Architektursicht zu `spec/lastenheft.md` und `spec/spezifikation.md`. Diese Datei enthält keine eigenständigen Anforderungen.

## 1. Komponenten-Übersicht

```mermaid
%%{init: {'theme': 'base', 'flowchart': {'curve': 'basis', 'nodeSpacing': 30, 'rankSpacing': 45}, 'themeVariables': {'lineColor': '#8a94a6', 'primaryColor': '#e8ecf8', 'primaryTextColor': '#1b2333', 'primaryBorderColor': '#5b6b9a', 'clusterBkg': 'transparent', 'clusterBorder': '#8a94a6', 'titleColor': '#8a94a6'}}}%%
flowchart TB
    subgraph Extern["Extern"]
        NET[".NET SqlClient"]
        Engine["Externe Query Engine"]
    end
    subgraph Protokoll["Protokollpfad"]
        Transport["TDS Transport / Framing"]
        Session["Handshake / Auth / Session"]
        Dispatch["SQL Batch / RPC Dispatch"]
        Stream["Response Sequencer / Cancel State"]
        Codec["Result / Error Encoder"]
    end
    subgraph Kern["Kern"]
        Backend["Query Backend Port"]
        Model["Domain / Types"]
        Sim["Simulations-Backend (optional)"]
    end
    subgraph Quer["Querschnitt"]
        Runtime["Runtime / Bootstrap"]
        Config["Config / Security"]
        Obs["Observability"]
    end
    NET <--> Transport
    Transport <--> Session
    Session --> Dispatch
    Session --> Config
    Dispatch --> Stream
    Stream --> Codec
    Codec --> Transport
    Dispatch --> Backend
    Backend <--> Engine
    Backend <--> Sim
    Session --> Model
    Dispatch --> Model
    Backend --> Model
    Sim --> Model
    Runtime --> Transport
    Runtime --> Config
    Runtime -.-> Sim
    Transport --> Obs
    Dispatch --> Obs
```

Pfeile zeigen Datenfluss und Aufrufrichtung zur Laufzeit, nicht die Import-Richtung; die Import-Regeln stehen in Abschnitt 2.

| ID | Komponente | Rolle |
|---|---|---|
| `ARC-001` | Domain / Types | Sitzungs-, Anfrage-, Ergebnis-, Fehler- und Typmodelle ohne Netzwerkabhängigkeit |
| `ARC-002` | Config / Security | Konfigurationsvalidierung, TLS- und Authentifizierungsadapter |
| `ARC-003` | TDS Transport / Framing | TCP, TDS-Header, Fragmentierung, Backpressure, I/O |
| `ARC-004` | Handshake / Session | PRELOGIN, LOGIN7, Verbindungs-Zustandsautomat, Session-Lebensdauer |
| `ARC-005` | SQL Batch / RPC Dispatch | TDS-Anfragen in interne QueryRequests übersetzen |
| `ARC-006` | Query Backend Port | Austauschbare, asynchrone Ausführungs- und Abbruchschnittstelle |
| `ARC-007` | Result / Error Encoder | COLMETADATA, ROW, DONE, ERROR und Login-Antworten |
| `ARC-008` | Runtime / Bootstrap | Verdrahtung, Listener-Start, Shutdown und Ressourcenverwaltung |
| `ARC-009` | Observability | Strukturierte Logs, Metriken und Traces ohne Geheimnisse |
| `ARC-015` | Response Sequencer / Cancel State | Hält den Anfragezustand; serialisiert Ergebnisereignisse, DONE-Familie und Abbruch; garantiert Antwortgrenzen |
| `ARC-016` | Simulations-Backend | Optionaler Backend-Adapter hinter `ARC-006`: beantwortet abgegrenzte Anweisungsformen aus beschriebenen Tabellen; keine SQL-Engine, keine Persistenz |

## 2. Schichten und Constraints

Jede Komponente liegt in einem Package unter `io.github.pt9912.tdswire` (Spalte „Package“).
Importiert wird nur, was die Spalte „Darf importieren“ nennt; die Spalte „Darf NICHT importieren“
nennt die wichtigsten Ausschlüsse. Im Kern (`domain`, `port`) gilt das auch für
Fremdbibliotheken: erlaubt sind nur die Standardbibliothek und im Port die Laufzeitbibliothek für
asynchrone Streams.

| Komponente(n) | Package | Schicht | Verantwortlichkeit | Darf importieren | Darf NICHT importieren |
|---|---|---|---|---|---|
| `ARC-001` | `domain` | Domain | reine Modelle und Verträge | Standardbibliothek | Transport, Runtime, externe Engine |
| `ARC-002` | `config` | Configuration | validierte Einstellungen und Security-Verträge | Domain | Transport-Implementierung, Dispatch |
| `ARC-006` | `port` | Backend Port | Backend-Abstraktion | Domain, Laufzeitbibliothek für asynchrone Streams | TDS-Codec, TCP-Transport |
| `ARC-004`, `ARC-005`, `ARC-007`, `ARC-015` | `protocol` | Protocol | TDS-Semantik, Session, Anfrage-/Antwortabbildung | Domain, Backend Port, Laufzeitbibliothek für asynchrone Streams, Security-Verträge, Observability-Schnittstellen, Transport-Schnittstellen (Antwortausgabe) | konkrete Backend-Engine, TCP-Transport-Implementierung, Bootstrap |
| `ARC-003` | `transport` | Transport | Netzwerk-I/O, Framing und TLS-Kapselung | Domain, Security-Verträge, Observability-Schnittstellen, Protocol-Schnittstellen | konkrete Backend-Engine |
| `ARC-009` | `observability` | Observability | Telemetrie-Adapter | Domain, Observability-Schnittstellen | fachliche Entscheidungen |
| `ARC-016` | `simulation` | Backend-Adapter | Simulations-Backend hinter dem Backend Port | Domain, Backend Port, Laufzeitbibliothek für asynchrone Streams | Protocol, Transport, Security-Verträge, Observability, Bootstrap, externe Engine |
| `ARC-008` | `runtime` | Runtime | Composition Root | alle benötigten Module | — |

**Constraints:** Kein Package außer Runtime importiert das Simulations-Backend; der Protokollkern
läuft ohne es. Protokollparser dürfen keine Query-Engine aufrufen. Der Backend-Port kennt keine TDS-Paketbytes. Die Typabbildung ist zentral und deterministisch. Jede Verbindung besitzt ihren eigenen Zustandsautomaten. Fehlerhafte Eingaben dürfen keine andere Session beeinflussen. TLS und Authentifizierung werden vor Freigabe von Query-Anfragen abgeschlossen. Backpressure und Cancel werden bis zum Backend durchgereicht.

## 3. Externe Abhängigkeiten

| ID | System | Rolle | Substituierbarkeit |
|---|---|---|---|
| `ARC-010` | .NET SqlClient | Externer Referenzclient für Kompatibilitätstests | Profilgebunden; anderer Client erfordert neue Validierung |
| `ARC-011` | Microsoft TDS / MS-TDS | Verbindlicher Protokollvertrag | Nicht austauschbar ohne Änderung des Produktziels |
| `ARC-012` | Query Engine | Externe Datenverarbeitung | Über `ARC-006` austauschbar |
| `ARC-013` | Netzwerk-/TLS-Laufzeit | TCP und Verschlüsselung | Austauschbar hinter Transport-/Security-Schnittstellen |
| `ARC-014` | Logging-/Tracing-Backend | Betriebsdaten | Über Observability-Schnittstellen austauschbar |

Konkrete Bibliotheks- und Versionsentscheidungen sind nicht Teil dieser Sicht.

## 4. Sequenz-Diagramme

### Use-Case: TDS-FA-01 / TDS-FA-02 — Verbindungsaufbau und Login

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'lineColor': '#7d8799', 'primaryColor': '#e8ecf8', 'primaryTextColor': '#1b2333', 'primaryBorderColor': '#5b6b9a', 'actorBkg': '#e8ecf8', 'actorBorder': '#5b6b9a', 'actorTextColor': '#1b2333', 'actorLineColor': '#7d8799', 'signalColor': '#7d8799', 'signalTextColor': '#7d8799', 'labelBoxBkgColor': '#e8ecf8', 'labelBoxBorderColor': '#5b6b9a', 'labelTextColor': '#1b2333', 'loopTextColor': '#7d8799', 'noteBkgColor': '#e8ecf8', 'noteTextColor': '#1b2333', 'noteBorderColor': '#5b6b9a'}}}%%
sequenceDiagram
    participant C as .NET SqlClient
    participant T as Transport
    participant S as Handshake/Session
    participant A as Auth Adapter
    C->>T: TCP + PRELOGIN
    T->>S: validierte PRELOGIN-Nachricht
    S-->>C: PRELOGIN-Antwort / TLS-Verhandlung
    C->>T: LOGIN7
    T->>S: LOGIN7
    S->>A: Credentials prüfen
    A-->>S: Principal / Ablehnung
    alt Erfolg
        S-->>C: Login-Antworttokens
    else Fehler
        S-->>C: Login-Fehler / Disconnect
    end
```

### Use-Case: TDS-FA-03 / TDS-FA-05 — SELECT als SQL_BATCH

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'lineColor': '#7d8799', 'primaryColor': '#e8ecf8', 'primaryTextColor': '#1b2333', 'primaryBorderColor': '#5b6b9a', 'actorBkg': '#e8ecf8', 'actorBorder': '#5b6b9a', 'actorTextColor': '#1b2333', 'actorLineColor': '#7d8799', 'signalColor': '#7d8799', 'signalTextColor': '#7d8799', 'labelBoxBkgColor': '#e8ecf8', 'labelBoxBorderColor': '#5b6b9a', 'labelTextColor': '#1b2333', 'loopTextColor': '#7d8799', 'noteBkgColor': '#e8ecf8', 'noteTextColor': '#1b2333', 'noteBorderColor': '#5b6b9a'}}}%%
sequenceDiagram
    participant C as .NET SqlClient
    participant T as Transport
    participant D as SQL Dispatch
    participant B as Backend Port
    participant Q as Response Sequencer
    participant E as Result Encoder
    C->>T: SQL_BATCH
    T->>D: SQL-Text + Session
    D->>B: execute(QueryRequest)
    B-->>D: Metadaten + Zeilenstream
    D->>Q: Ergebnisereignisse
    Q->>E: geordnetes Resultset
    E-->>T: COLMETADATA / ROW / DONE
    T-->>C: TDS-Tokenstrom
```

### Use-Case: TDS-FA-04 / TDS-FA-07 — Parametrisierter RPC und Abbruch

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'lineColor': '#7d8799', 'primaryColor': '#e8ecf8', 'primaryTextColor': '#1b2333', 'primaryBorderColor': '#5b6b9a', 'actorBkg': '#e8ecf8', 'actorBorder': '#5b6b9a', 'actorTextColor': '#1b2333', 'actorLineColor': '#7d8799', 'signalColor': '#7d8799', 'signalTextColor': '#7d8799', 'labelBoxBkgColor': '#e8ecf8', 'labelBoxBorderColor': '#5b6b9a', 'labelTextColor': '#1b2333', 'loopTextColor': '#7d8799', 'noteBkgColor': '#e8ecf8', 'noteTextColor': '#1b2333', 'noteBorderColor': '#5b6b9a'}}}%%
sequenceDiagram
    participant C as .NET SqlClient
    participant T as Transport
    participant D as RPC Dispatch
    participant B as Backend Port
    participant Q as Response Sequencer
    participant E as Result Encoder
    C->>T: RPC sp_executesql + Parameter
    T->>D: RPC-Nachricht + Session
    D->>B: execute(typisierte Parameter)
    opt Client bricht ab
        C->>T: ATTENTION
        T->>Q: cancel(sessionId, requestId)
        Q->>B: cancel(sessionId, requestId)
        Q->>E: Abbruchantwort nach Protokollzustand
    end
    B-->>D: Ergebnis oder Fehler
    D->>Q: Ergebnisereignisse
    Q->>E: Antwort ohne Vermischung mit Folgeanfrage
    E-->>T: Resultset / ERROR / DONE
    T-->>C: TDS-Tokenstrom
```

### Use-Case: TDS-FA-06 / TDS-FA-07 / TDS-FA-10 — Fehler während Streaming

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'lineColor': '#7d8799', 'primaryColor': '#e8ecf8', 'primaryTextColor': '#1b2333', 'primaryBorderColor': '#5b6b9a', 'actorBkg': '#e8ecf8', 'actorBorder': '#5b6b9a', 'actorTextColor': '#1b2333', 'actorLineColor': '#7d8799', 'signalColor': '#7d8799', 'signalTextColor': '#7d8799', 'labelBoxBkgColor': '#e8ecf8', 'labelBoxBorderColor': '#5b6b9a', 'labelTextColor': '#1b2333', 'loopTextColor': '#7d8799', 'noteBkgColor': '#e8ecf8', 'noteTextColor': '#1b2333', 'noteBorderColor': '#5b6b9a'}}}%%
sequenceDiagram
    participant C as .NET SqlClient
    participant T as Transport
    participant Q as Response Sequencer
    participant B as Backend Port
    B-->>Q: ResultStart + Row
    Q-->>T: COLMETADATA + ROW
    T-->>C: partielle Antwort
    alt Backend-Fehler, Strom synchron
        B-->>Q: Fehler
        Q-->>C: ERROR + DONE_ERROR
    else ATTENTION
        C->>T: ATTENTION
        T->>Q: cancel
        Q->>B: cancel / close
        Q-->>C: ATTENTION-Abschluss nach Profil
    else Synchronität nicht garantiert
        Q->>T: Verbindung schließen
        T-->>C: Disconnect
    end
```

## 5. Fehlermodelle und Resilienz

Die Logging-Spalte nennt die je Fehlerquelle wesentlichen Korrelationsfelder; zusätzlich gelten alle Pflicht-Logfelder der Spezifikation (Abschnitt Fehler-Codes und Logging-Felder).

| Fehlerquelle | Behandlung-Schicht | Logging |
|---|---|---|
| Ungültiger Paketheader / Fragmentierung | Transport | `connection_id`, `event`, `error_code`; keine Payload |
| PRELOGIN / TLS-Inkompatibilität | Handshake / Security | `connection_id`, `event`, `error_code`; keine Secrets |
| Ungültige Zugangsdaten | Session / Auth | `connection_id`, `event`, `error_code`; keine Passwörter |
| Unbekannter RPC / SQL-Fehler | Dispatch / Encoder | `session_id`, `request_id`, `error_code` |
| Teilantwort / fehlerhafte Ereignisfolge | Response Sequencer | `request_id`, `event`, `error_code`; keine Payload |
| Nicht serialisierbarer Typ | Result Encoder | `request_id`, `event`, `error_code` |
| Backend-Ausfall / Timeout | Backend Port / Dispatch | `request_id`, `duration_ms`, `error_code` |
| Client-Disconnect / ATTENTION | Transport / Session / Backend | `request_id`, `event`, `error_code` |

**Resilienzprinzipien:** Grenzen für Nachrichten- und Sitzungsspeicher; definierte Timeouts; kontrollierte Freigabe von Backendressourcen; keine ungefangenen Exceptions über Verbindungsgrenzen hinweg; geordneter Shutdown. Nicht unterstützte Protokollfunktionen werden explizit abgelehnt und nicht stillschweigend simuliert.

**Zustands- und Ownership-Grenzen:** Transport verantwortet Paketframing und TLS-Kapselung, Handshake die Login-Zustandsübergänge, Dispatch die fachliche Anfrageübersetzung, Response Sequencer die Reihenfolge der Antwortereignisse je aktiver Anfrage und die Cancel/DONE-Synchronität; ob mehrere Anfragen je Sitzung gleichzeitig aktiv sein dürfen (MARS), ist in `SPEC-004` offen. Backendoperationen und Ergebnisstreams sind sitzungsgebunden; bei Disconnect, Timeout und Shutdown werden sie geschlossen. Eine nach Cancel nicht eindeutig synchronisierbare Sitzung wird nicht wiederverwendet.
