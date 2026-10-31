# ADR-0017: Grenzwert der Prepared-Handles je Sitzung und Verhalten bei Überschreitung

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [ADR-0004](0004-session-zustandsautomat.md), [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0014](0014-fehlerfolge-rpc-antwort.md), [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Anzahl der Handles je Sitzung ist zu begrenzen), [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (Zugriff unter dem Sitzungsschutz), [TDS-FA-04.a](../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), [ARC-005](../../../spec/architecture.md#1-komponenten-übersicht). Verhältnis zu den angenommenen ADRs: [ADR-0005](0005-rpc-und-prepared-statements.md) verlangt, dass der Produktivcode die Handles begrenzt, und nennt keine Zahl und keinen Fehler; [ADR-0004](0004-session-zustandsautomat.md) legt den Sitzungsschutz fest, unter dem die Tabelle liegt. Beide werden **geschärft**, kein Satz wird überstimmt; kein `Supersedes`.

---

## Kontext

[ADR-0005](0005-rpc-und-prepared-statements.md) („Negativ / Risiko“) hält fest: Der Server hält Prepare-Handles je Sitzung; da der Client in den gemessenen Szenarien (ohne Pool) kein `sp_unprepare` sendet, wachsen sie bis zum Sitzungsende, wenn der Produktivcode sie nicht begrenzt; unter Pooling ist das nicht gemessen. [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) verlangt, die Anzahl je Sitzung zu begrenzen (Festlegung), und beantwortet `sp_unprepare` mit einem definierten Fehler (der Katalog kennt es nicht). Handles werden deshalb vor dem Sitzungsende **nie** freigegeben.

Der Slice für Prepared-Handles nennt den Grenzwert „Festlegung des Nutzers, nicht gemessen“ und startet erst, wenn er entschieden ist; die Closure des Slice für mehrteilige Antworten und Ausgabewerte findet für den Grenzwert keine Zahl im Repo (Entscheidungsblatt [Punkt 1](../entscheidungsblatt.md)). Eine Messung am echten Server gibt es nicht: weder wie viele Handles ein SQL Server je Sitzung hält noch wie er ein Überschreiten beantwortet. Der Speicher je Handle (Text, Deklaration) ist nicht gemessen; `MAX_CONNECTIONS` ist 1000 (`SPEC-034`, vorläufig).

## Entscheidung

Wir wählen **Option A für den Grenzwert** und **Option a für die Überschreitung**.

1. **Grenzwert (Setzung, Voreinstellung, nicht gemessen):** Eine Sitzung hält höchstens **1000** Prepared-Handles gleichzeitig. Die Zahl ist durch keine Messung begründet; sie ist eine runde Obergrenze, die einen Betrieb mit gewöhnlichem Prepare-Gebrauch nicht berührt (nicht gemessen) und einer Sitzung, die dauernd neu vorbereitet, eine Schranke setzt. Sie wird in der Spezifikation als Setzung gekennzeichnet. Gezählt werden die gleichzeitig gehaltenen Handles; weil `sp_unprepare` nicht unterstützt wird, heißt das heute: die in der Sitzung bisher erfolgreich vergebenen.
2. **Überschreitung (Empfehlung des Architect; Optionen unten):** Der `sp_prepexec`, der das 1001. Handle vergäbe, wird mit einem **definierten Fehler abgelehnt**: ERROR (eigener Code der Anfragefehler-Gruppe, Class 16) und DONEPROC mit Fehlerstatus nach [ADR-0014](0014-fehlerfolge-rpc-antwort.md); die **Verbindung bleibt offen**, das Backend wird nicht gerufen, die vorhandenen Handles bleiben gültig. Der Code und seine Nummer legt das Fehler-Mapping fest (Nachzug in der Spezifikation); diese ADR bestimmt nur Gruppe, Class und Verbindungsfolge (Setzung, Voreinstellung, nicht gemessen, gleiche Gruppe wie `TDS_UNSUPPORTED_RPC`).
3. **Zugriff.** Die Handle-Tabelle liegt je Sitzung unter dem Sitzungsschutz von [ADR-0004](0004-session-zustandsautomat.md); zwei Sitzungen sehen die Handles der jeweils anderen nicht (Anforderung aus dem Slice-Plan, Test dort).
4. **Konfigurierbarkeit.** Die Zahl ist fest; sie ist nicht per Umgebung einstellbar (Option c nicht gewählt, siehe Alternativen). Wird sie änderbar, ist das eine neue Entscheidung (Trigger).

## Verglichene Alternativen

**Grenzwert.**

| Option | Pro | Contra |
|---|---|---|
| **A — 1000 fest, Setzung (gewählt)** | startet den Slice ohne Messung; eine Zahl, ein Test; kein Konfigurationspfad | Zahl nicht begründbar außer als Setzung; Speicher im ungünstigsten Fall bis zu 1000 Handles × `MAX_CONNECTIONS` (1000), je Handle-Größe nicht gemessen |
| B — andere Zahl | je nach Zahl enger oder weiter | derselbe Mangel an Begründung; keine Messung wäre besser als 1000 |
| C — konfigurierbar mit Voreinstellung 1000 | Betreiber kann anpassen | neuer Schalter, Validierung, Doku; es gibt keinen benannten Betreiber, der die Zahl ändern will (abgeleitet) |

**Überschreitung.**

| Option | Pro | Contra |
|---|---|---|
| **a — definierter Fehler, Verbindung offen (empfohlen)** | folgt dem Muster der Anfragefehler (Class 16); der Client kann weiterarbeiten (unbekanntes Handle und `sp_unprepare` sind ebenso beantwortet); keine Zustandsänderung | der Client hat womöglich keinen Weg, Handles freizugeben (`sp_unprepare` ist abgelehnt): jede weitere Vorbereitung auf dieser Verbindung scheitert, bis sie endet |
| b — Verbindung schließen | Sitzung wird sicher beendet; Speicher frei | trifft auch gültige Handles und laufende Arbeit; Class 20 passt nicht zu einem Anfragefehler; ein Pool würde die Verbindung ersetzen müssen (nicht gemessen) |
| c — ältestes Handle verdrängen | Client arbeitet weiter | ein später ausgeführtes `sp_execute` auf das verdrängte Handle schlägt mit „unbekanntes Handle“ fehl: falsche Ursache für den Client, schwer zu finden |
| d — nichts begrenzen | kein Code | widerspricht [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) und dem Risiko aus [ADR-0005](0005-rpc-und-prepared-statements.md); Speicher wächst mit der Sitzungsdauer |

## Konsequenzen

- Positiv: Der Start-Trigger des Slice für Prepared-Handles (Grenzwert entschieden) ist mit der Annahme erfüllt; Ressourcenverbrauch je Sitzung ist nach oben begrenzt ([TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität)).
- Negativ: Ein gepoolter Client auf langlebiger Verbindung, der laufend vorbereitet und nie `sp_unprepare` senden kann, erreicht die Grenze und bekommt ab dann Fehler. Ob ein echter gepoolter Client das tut, ist nicht gemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation: `SPEC-030` nennt die Zahl und kennzeichnet sie als Setzung (Voreinstellung, nicht gemessen); der Fehlercode kommt in die Zuordnungstabelle des Fehler-Mappings. Das Blatt nennt `SPEC-021` als Stelle der Kennzeichnung; `SPEC-021` ist das Clientprofil, die Zeile der Handles steht in `SPEC-030`; die Stelle legt der Spec-Nachzug fest. (2) Quelltext und Tests: Handle-Tabelle mit Grenze, Test an der Grenze (1000 gehalten, 1001. abgelehnt, Verbindung offen, Folge-RPC beantwortet). Träger: der Slice für Prepared-Handles, dessen Planner den Verweis auf diese ADR setzt. (3) Abhängigkeit: das Abschlusstoken des Fehlers hängt an [ADR-0014](0014-fehlerfolge-rpc-antwort.md); ist sie bis zum Bau nicht angenommen, gilt die Form des gebauten Standes (DONE) und wird mit ihr nachgezogen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Eine Sitzung hält bis zu 1000 Handles; die Anfrage, die das 1001. vergäbe, ergibt den definierten Fehler bei offener Verbindung und lässt die Handles unberührt; zwei Sitzungen zählen getrennt. Noch kein bestandenes Gate. | — (Träger: der Slice für Prepared-Handles) |

## Offen

- Für den Nutzer: der Fehlercode ist nicht gewählt (Nummer und Text legt das Fehler-Mapping fest); ob Option b (schließen) statt a gewünscht ist.
- Nicht entschieden: Vergabe der Handle-Werte (Festlegung im Slice); Verhalten der Tabelle bei `RESETCONNECTION` (offen in [ADR-0004](0004-session-zustandsautomat.md) und [ADR-0007](0007-fehler-und-attention.md)); `sp_unprepare` unter Pooling.

## Re-Evaluierungs-Trigger

Eine Messung des Handle-Verhaltens eines echten Servers; ein gepoolter Referenzclient, der die Grenze im Normalbetrieb erreicht oder `sp_unprepare` voraussetzt; ein Betreiber, der die Zahl ändern will (dann Konfigurierbarkeit); eine Messung des Speichers je Handle, die 1000 widerlegt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Grenzwert 1000 als Setzung (Voreinstellung, nicht gemessen) und Überschreitung als definierter Fehler bei offener Verbindung (Empfehlung) zu Punkt 1 des Entscheidungsblatts (Nutzer: „ok deine empfehlungen“); Status bleibt Proposed bis Review und Annahme durch den Nutzer | [Entscheidungsblatt](../entscheidungsblatt.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
