# ADR-0016: Drosselung wiederholter Anmeldeversuche

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [ADR-0009](0009-authentifizierungsmodus.md), [ADR-0011](0011-pruefwertformat.md), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [CO-003](../carveouts/CO-003-anmeldedrosselung-fehlt.md)

**Schärft:** [SPEC-012](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (Ablehnung und Verbindungsende nach abgelehntem Login), [SPEC-008](../../../spec/spezifikation.md#3-defaults-und-konstanten) (`LOGIN_TIMEOUT`), [SPEC-051](../../../spec/spezifikation.md#6-externe-verträge) (Adaptervertrag). Löst den offenen Punkt „Schutz gegen wiederholte Anmeldeversuche“ aus [ADR-0009](0009-authentifizierungsmodus.md) §Offen (dort bleibt der Text unverändert; kein `Supersedes`, weil kein Satz überstimmt wird).

---

## Kontext

[ADR-0009](0009-authentifizierungsmodus.md) führt den Schutz gegen wiederholte Anmeldeversuche als weder entschieden noch gebaut; [CO-003](../carveouts/CO-003-anmeldedrosselung-fehlt.md) trägt den Punkt mit dem Folge-Slice für die Anmeldedrosselung und verlangt zur Auflösung eine `Accepted`-ADR für Drosselung oder Verzicht (Entscheidungsblatt [Punkt 2](../entscheidungsblatt.md)). Die Wahl der Art und der Grenzwerte trifft der Nutzer; dieser Text bietet Optionen und eine Empfehlung an.

**Was gemessen ist.** Im Slice für die Sicherheitstests: eine Anmeldung mit 210000 Iterationen braucht auf dem Host des Laufs rund 57 ms (CO-003; die Messtests streuen je nach Last zwischen rund 43 und 64 ms je Aufruf), und mit 4 Plätzen des `AuthPool` warten reguläre Aufrufe hinter belegenden (`AuthPoolOccupationTest`). Abgeleitet, nicht gemessen: bei 4 Plätzen und 57 ms ergeben sich höchstens rund 70 Ableitungen je Sekunde, ohne Netz- und Handshakezeit. Wie viele Versuche je Sekunde ein Client dem Server tatsächlich abverlangen kann, ist nicht gemessen.

**Was feststeht.** Die Ablehnung ist für unbekannte Kennung und falsches Passwort gleich (`SPEC-012`), der Server schließt nach einem abgelehnten Login ([ADR-0004](0004-session-zustandsautomat.md), Setzung); jeder Wiederholungsversuch ist deshalb eine **neue Verbindung**. Der Server liest die Gegenstelle der Verbindung im Transport; ob der Anmeldepfad sie heute erhält, ist **nicht geprüft** (der Verbindungskontext des Adapters nennt Datenbank, Anwendungsname, Rechnername, Intent; der Rechnername ist vom Client frei gesetzt und kein Herkunftsbeleg, `SPEC-051`). Der Plan nennt zwei Risiken: eine Drosselung je Benutzername lässt sich zur Sperre eines regulären Benutzers missbrauchen, eine je Quelladresse trifft Clients hinter gemeinsamen Adressen; beides ist nicht gemessen.

## Entscheidung

Wir empfehlen **Option B**: eine **Verzögerung** (keine Sperre) wiederholter Fehlversuche je **Quelladresse**; der Benutzername geht nicht in den Schlüssel ein.

Die Empfehlung hängt an drei Gründen, die keine Messung sind: (i) Der Schlüssel „Adresse“ enthält keine Eingabe, die der Angreifer wählt, um Zustand je Name anzulegen oder abzufragen; es entsteht kein Kanal, der die Existenz einer Kennung verrät, und `SPEC-012` (gleiche Ablehnung) bleibt unberührt. (ii) Eine Verzögerung mit Deckel sperrt niemanden aus; die Fremdsperre eines Benutzers (Risiko 1 des Plans) entfällt. (iii) Der Aufwand ist klein: eine begrenzte Tabelle im Anmeldepfad, keine Persistenz.

**Mechanik (Setzung, Voreinstellung, nicht gemessen):**

1. Jeder Fehlversuch einer Adresse zählt; ein Zähler fällt 60 s nach dem letzten Fehlversuch dieser Adresse auf null (Zeitfenster, nicht durch Erfolg zurückgesetzt, damit ein gültiges Konto keinen Zähler fremder Versuche löscht).
2. Die ersten 5 Fehlversuche je Fenster bleiben unverzögert. Ab dem 6. wartet jede weitere Anmeldung dieser Adresse **vor** der Prüfung der Zugangsdaten (die Wartezeit hängt nur von der Adresse ab, nie von Kennung oder Passwort, damit sie keinen Zeitkanal öffnet): 250 ms, verdoppelt je weiterem Fehlversuch, Deckel 5 s.
3. Die Wartezeit ist ein Anhalten der Coroutine (kein Thread, kein Platz des `AuthPool`) und liegt innerhalb von `LOGIN_TIMEOUT` (15 s, `SPEC-008`; der Deckel 5 s lässt dafür Raum).
4. Die Tabelle ist begrenzt (10 000 Adressen; bei Überlauf wird die Adresse mit dem ältesten Fehlversuch verdrängt) und wächst nie mit Eingaben des Angreifers über diese Grenze.
5. Ein laufender Versuch zählt ab seinem Beginn und wird bei Erfolg zurückgenommen, damit eine Welle gleichzeitiger Verbindungen nicht alle denselben (niedrigen) Zählerstand sieht.

**Alle Zahlen sind Setzung (Voreinstellung, nicht gemessen):** 5 freie Versuche, 60 s Fenster, 250 ms Anfang, Deckel 5 s, 10 000 Adressen. Sie sind nicht aus einer Messung abgeleitet; abgeleitet ist nur die Folgerung, dass ein Client mit dem Deckel höchstens rund 12 Versuche je Minute (60 s / 5 s) hinter einer Adresse erreicht, solange er eine Verbindung nach der anderen nutzt. Gleichzeitige Verbindungen, andere Adressen und der Zählerstand bei Welle sind **nicht gemessen** und der erste Messauftrag des Folge-Slice.

**Gilt nicht:** Eine verteilte Suche über viele Adressen bleibt ungebremst; ein Konto, das aus vielen Adressen angegriffen wird, bekommt von dieser Drosselung keinen Schutz. Das ist die Grenze der Wahl, kein Fehler der Umsetzung.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — Verzicht, Netzebene des Betreibers (Firewall, Rate-Limit vor dem Server) | kein Code; löst [CO-003](../carveouts/CO-003-anmeldedrosselung-fehlt.md) mit Verzichts-ADR | Raten bleibt auf dem Server ungebremst ([TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)); die Plätze des `AuthPool` bleiben durch Fehlversuche belegbar (gemessen: wartende Aufrufe) |
| **B — Verzögerung je Quelladresse, Deckel (empfohlen)** | kein Zustand je Name, kein Enumerationskanal, keine Fremdsperre; kleiner Bau; sperrt nicht dauerhaft | Clients hinter gemeinsamer Adresse (NAT, Proxy, Anwendungsserver) teilen den Zähler und die Verzögerung; verteilte Suche ungebremst; die Adresse muss den Anmeldepfad erreichen (nicht geprüft) |
| C — Verzögerung oder Sperre je Benutzername | bremst die Suche gegen ein Konto auch aus vielen Adressen | Fremdsperre: ein Dritter bremst oder sperrt den regulären Benutzer mit falschen Versuchen; Zähler je Name legt Zustand nach Angreifer-Eingabe an (Speicherbegrenzung nötig) und kann Existenz verraten, wenn nur bekannte Namen gezählt werden |
| D — harte Sperre je Quelladresse nach N Fehlversuchen | stärkste Bremse für eine Adresse | sperrt Anwendungsserver hinter gemeinsamer Adresse vollständig; Sperrdauer und Aufhebung werden zum Betreiberproblem; Fremdsperre durch gefälschte Absender nur bei TCP nicht trivial (nicht geprüft) |
| E — Kombination B und C | deckt beide Angriffsformen | beide Nachteile, doppelte Zahl an Grenzwerten, mehr Bau; kein Beleg, dass beides heute gebraucht wird |

## Konsequenzen

- Positiv: Ein Angreifer, der Kennungen von einer Adresse rät, wird nach 5 Fehlversuchen auf rund 12 je Minute gebremst (abgeleitet aus der Setzung); die Wartezeit belegt keinen `AuthPool`-Platz. [CO-003](../carveouts/CO-003-anmeldedrosselung-fehlt.md) hat bei Annahme und Bau einen Auflösungsweg.
- Negativ: Eine fehlerhaft konfigurierte Anwendung hinter einer Adresse (falsches Passwort nach Rotation) bremst alle Clients dieser Adresse, bis das Fenster abläuft. Der Deckel 5 s begrenzt den Schaden, aber nicht auf null.
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation: neue Konstanten (Zahl freier Versuche, Fenster, Anfang, Deckel, Tabellengröße) in den Defaults und die Zeile im Anmeldevertrag; Träger: der Slice für die Anmeldedrosselung. (2) Quelltext: Weitergabe der Gegenstelle an den Anmeldepfad, falls nicht vorhanden, Tabelle, Wartezeit; Tests, die zeigen, dass wiederholte Versuche gebremst werden und eine reguläre Anmeldung nach Ablauf des Fensters nicht blockiert ist (Messung der Wirkung wie `AuthPoolOccupationTest` für die Plätze). Träger: derselbe Slice. (3) [CO-003](../carveouts/CO-003-anmeldedrosselung-fehlt.md) wird bei Closure des Slice aufgelöst.
- **Akzeptiertes Negativ (einmalig):** Die Grenzwerte sind Voreinstellung ohne Messung und ohne Konfigurierbarkeit; ob sie konfigurierbar sein sollen, ist nicht entschieden und kein Träger angelegt: erst ein Betreiber, der sie ändern will, macht daraus einen Auftrag (Trigger unten).

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) mit gesteuerter Uhr | Nach 5 Fehlversuchen einer Adresse wartet der nächste Versuch gemäß Staffel bis zum Deckel; die Wartezeit hängt nicht von der Kennung ab (Antwortbytes und Wartezeit für bekannte und unbekannte Kennung gleich); nach Ablauf des Fensters ist die Anmeldung unverzögert; die Tabelle überschreitet ihre Grenze nicht. Noch kein bestandenes Gate. | — (Gate noch einzurichten; Träger: der Slice für die Anmeldedrosselung) |

## Offen

- Für den Nutzer: Art (Option B empfohlen, A bis E zur Wahl) und Grenzwerte; ob die Grenzwerte konfigurierbar sind.
- Nicht entschieden: Ob die Gegenstelle im Anmeldepfad verfügbar ist; Umgang mit IPv6 (eine Adresse oder ein Präfix als Schlüssel); ob die Drosselung im Log erscheint und mit welcher Adressangabe (Datenschutz, [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)); Verhalten hinter einem Proxy, der die Quelladresse verdeckt; die Begrenzung gleichzeitiger Anmeldungen je Adresse (Welle, siehe Entscheidung 5).

## Re-Evaluierungs-Trigger

Ein Betreiber hinter gemeinsamer Adresse, dessen reguläre Anmeldungen durch den Zähler gebremst werden; eine Messung, nach der der Angriff über mehrere Adressen oder gegen ein einzelnes Konto den Server oder die Plätze des `AuthPool` stärker belastet als der Schlüssel Adresse abdeckt; ein neuer Authentifizierungsadapter mit eigener Sperrlogik; ein Anmeldemodus ohne Passwort (Änderung von [ADR-0009](0009-authentifizierungsmodus.md)).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Optionen A bis E mit Empfehlung B zu Punkt 2 des Entscheidungsblatts (Nutzer: „ok deine empfehlungen“); Grenzwerte als Setzung (Voreinstellung, nicht gemessen); die Wahl der Art bestätigt der Nutzer mit der Annahme | [Entscheidungsblatt](../entscheidungsblatt.md), [CO-003](../carveouts/CO-003-anmeldedrosselung-fehlt.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
