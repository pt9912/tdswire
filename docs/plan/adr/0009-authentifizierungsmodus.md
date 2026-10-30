# ADR-0009: Authentifizierungsmodus und Authentifizierungsadapter

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)

**Schärft:** [SPEC-004](../../../spec/spezifikation.md#spec-004--testprofil), [SPEC-012](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-051](../../../spec/spezifikation.md#6-externe-verträge), [ARC-002](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-004](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-02.a](../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung)

---

## Kontext

Das Lastenheft verlangt, den initial unterstützten Authentifizierungsmodus vor der Implementierung der Anmeldung festzulegen. Die Wahl bestimmt den Inhalt von LOGIN7, die Schnittstelle zum Authentifizierungsadapter und die Sicherheitsaussagen. Windows Integrated Authentication, Kerberos und Entra-ID sind im Lastenheft für die erste Version ausgeschlossen.

Gemessen mit `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 gegen einen Testserver in C# (Zertifikat `CN=localhost`, `TrustServerCertificate=true`; Passwörter sind Dummy-Werte):

- **LOGIN7:** Bei SQL-Authentifizierung ist die Nachricht 509 Byte lang (`TDSVersion=0x74000004`, `PacketSize=8000`), mit und ohne TLS-Tunnel dieselbe Struktur. Kennung und Passwort stehen als UTF-16LE mit Länge in UTF-16-Einheiten; Datenbank, Anwendungsname, Rechnername, Intent und Paketgröße stehen in eigenen Feldern.
- **Passwort:** Je Byte XOR `0xA5`, danach Nibble-Tausch; die Entschleierung (erst XOR, dann Tausch) ergab in allen Fällen den Eingabewert, auch bei Unicode, Surrogatpaar und Sonderzeichen. Das ist reversibel und kein Schutz: Im unverschlüsselten Testmodus liegt das Passwort lesbar auf der Leitung.
- **Grenzen:** Kennung und Passwort sind clientseitig auf 128 Zeichen begrenzt (längere Werte werfen vor dem Verbindungsaufbau); beide dürfen leer sein.
- **Annahme und Ablehnung:** Annahme mit LOGINACK und DONE genügt dem Client, FEATUREEXTACK verlangt er nicht. Die Ablehnung braucht genau ein ERROR (Nummer 18456, State, Class 14) und DONE mit Fehlerstatus; der Client macht daraus eine `SqlException` mit denselben Werten und wiederholt nicht. Weitere Tokens verändern `Message` und `Errors`; fehlt das DONE oder schließt der Server ohne Antwort, meldet der Client Verbindungsfehler der Klasse 20.
- **Gleiche Ablehnung:** Mit fester Meldung `Login failed.` sind die Antwortbytes für unbekannte Kennung und falsches Passwort identisch (SHA-256 gleich); mit dem Namen der Kennung unterscheiden sie sich nur im Namen. Ein unterschiedlicher State wird bis zur Anwendung durchgereicht.
- **Nicht unterstützte Modi:** `Integrated Security` sendet LOGIN7 mit `fIntSecurity` und SSPI-Block (GSS-API-Token), nur wenn der Client sein Token erzeugen konnte; `Authentication=ActiveDirectory…` sendet LOGIN7 mit leerer Kennung und leerem Passwort und FeatureExt `0x02`. Beide sind am LOGIN7 erkennbar, eine Ablehnung mit 18456 zeigt der Client wie eine falsche Anmeldung.
- **Passwortwechsel:** `ChangePassword` setzt `fChangePassword` und trägt das neue Passwort verschleiert im eigenen Feld, das alte steht weiter im Passwortfeld.
- **Nicht gemessen:** der Zeitunterschied zwischen unbekannter Kennung und falschem Passwort; TDS 8.0 mit SQL-Authentifizierung; LOGIN7 über mehrere Pakete; Pooling mit `ResetConnection`; die Server-SSPI-Antwort und das Entra-ID-Token; andere Clients; State und Meldung eines echten SQL Servers.

## Entscheidung

Wir wählen **Option A**: SQL-Authentifizierung (Kennung und Passwort im LOGIN7) über eine austauschbare Adapter-Schnittstelle. Die Einstiegsimplementierung ist die einfachste Variante, die feste Benutzerliste aus der Konfiguration (Option B der Alternativen); Option C ist nicht gewählt.

- **Schnittstelle:** Der Anmeldepfad entschleiert LOGIN7 und ruft den Adapter mit Kennung, Passwort (beide UTF-16, 0 bis 128 Einheiten, auch leer), Verbindungskontext und Authentifizierungsmerkmalen. Der Adapter antwortet mit angenommen (samt Principal) oder abgelehnt (mit einem Grund nur für das Log). Die Eingaben sind aus der Messung abgeleitet; die Kotlin-Signatur in `contracts/auth-adapter.md` ist ein **Entwurf** und nicht gemessen.
- **Antwort an den Client:** Ein festes Muster des Anmeldepfads, nicht des Adapters: genau ein ERROR (18456, Class 14, einheitlicher State, feste Meldung ohne Kennung), danach DONE mit Fehlerstatus. Damit sind die Antwortbytes für unbekannte Kennung und falsches Passwort gleich (`SPEC-012`).
- **Nicht unterstützte Modi:** SSPI/Kerberos und Entra-ID erkennt der Anmeldepfad an `fIntSecurity` und FeatureExt `0x02` und lehnt sie mit demselben Muster ab, ohne den Adapter zu rufen. Ein Passwortwechsel wird in der ersten Version abgelehnt.
- **Einstiegsimplementierung:** Feste Benutzerliste, beim Start validiert; Konfigurationsweg und Format der Prüfwerte sind noch festzulegen.
- **Auswahlgrund:** Die Messung entscheidet zwischen A, B und C nicht: LOGIN7 trägt für alle drei dieselben Felder, und ob ein Backend die Zugangsdaten sieht (C), ist am Client nicht abzulesen. Gewählt ist A wegen der Betreiberanforderung (Entscheidung des Autors), später eine eigene Benutzerverwaltung anzubinden, ohne den Anmeldepfad zu ändern. Die Messung stützt nur, dass die Schnittstelle schmal sein kann.
- **Nachweis:** Ein Spike mit einem Testserver und dem Referenzclient hat LOGIN7, Entschleierung, Annahme- und Ablehnungsantworten und die nicht unterstützten Modi gemessen (siehe Kontext). Der Spike-Code liegt nicht im Repo.

## Verglichene Alternativen

| Option                                                  | Pro                                          | Contra                                                  |
| ------------------------------------------------------- | -------------------------------------------- | ------------------------------------------------------- |
| **A — SQL-Authentifizierung über austauschbaren Adapter** | Betreiber bindet eigene Benutzerverwaltung an; Anmeldepfad bleibt unverändert | Adapter-Vertrag und Geheimnisbehandlung zu definieren und zu pflegen |
| B — fest konfigurierte Benutzerliste (als Einstiegsimplementierung von A) | Einfach und ohne externe Abhängigkeit         | Allein nur für Test und kleine Betriebe geeignet; ohne Schnittstelle keine Anbindung |
| C — Weiterreichen der Zugangsdaten an das Backend       | Backend entscheidet allein                    | Backend sieht Geheimnisse; Vertrag nicht mehr TDS-frei; nicht gewählt |
| 0 — keine Festlegung vor dem ersten LOGIN7-Mitschnitt   | Keine Vorfestlegung                           | Blockiert `SPEC-012` und die Anmeldung nach `TDS-FA-02`; der Mitschnitt liegt jetzt vor |

## Konsequenzen

- **Positiv:** Der Anmeldepfad, seine Fehlerfälle und die Schnittstelle zur Benutzerverwaltung sind festgelegt und prüfbar. Eine eigene Benutzerverwaltung ersetzt nur den Adapter.
- **Negativ / Risiko:** Der Modus begrenzt die Clientkonfigurationen auf SQL-Authentifizierung; Clients mit Integrated Security oder Entra-ID werden abgelehnt. Die Schnittstelle ist ein Entwurf und kann sich beim Bau ändern.
- **Geheimnisschutz:** Das Passwort steht nur im Speicher des Anmeldepfads und wird nach dem Adapteraufruf überschrieben; es erscheint in keinem Log und keiner Fehlermeldung (`TDS-FA-09.a`). Der Vergleich in der Einstiegsimplementierung läuft in konstanter Zeit, bei unbekannter Kennung mit Scheinvergleich. Die Verschleierung im LOGIN7 schützt nicht; der Schutz auf der Leitung ist TLS ([ADR-0002](0002-tls-und-verschluesselung.md)), im unverschlüsselten Testmodus liegt das Passwort lesbar vor und das Log nennt den Modus.
- **Grenze der Gleichheit:** Gleich sind die Antwortbytes (gemessen). Zeitgleichheit von unbekannter Kennung und falschem Passwort ist nicht gemessen; die konstante Zeit ist eine Anforderung an den Produktivcode, die ein Test belegen muss. Ein Adapter mit eigener Benutzerverwaltung kann die Zeitgleichheit verletzen; der Vertrag nennt sie als Pflicht, kann sie aber nicht erzwingen.
- **Folgepflicht:** `SPEC-004` und `SPEC-012` auf den Modus festlegen (geschehen), Schnittstelle in `contracts/auth-adapter.md` und `SPEC-051` führen (Entwurf). Die Beschreibung der Schnittstelle in der Architektur-Sicht (`ARC-002`) folgt, sobald der Adapter gebaut ist; die Sicht bleibt ohne ADR-Bezug.
- **Offen:** Konfigurationsweg und Prüfwertformat der Benutzerliste; Kotlin-Signatur; Zeitgleichheit; TDS 8.0 mit SQL-Authentifizierung und LOGIN7 über mehrere Pakete; Verhalten bei Pooling mit `ResetConnection`; State und Meldung eines echten SQL Servers (`SPEC-045`).

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                                | Make-Target                    |
| ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| JVM-/Protokolltests und .NET-Referenzclient | Gültige Zugangsdaten führen zu einer nutzbaren Sitzung, ungültige zu einem definierten Anmeldefehler ohne Sitzung; die Antwortbytes für unbekannte Kennung und falsches Passwort sind gleich; Integrated Security und Entra-ID werden abgelehnt; Zugangsdaten erscheinen in keinem Log. Noch kein bestandenes Gate. | — (Gate noch einzurichten)     |

## Re-Evaluierungs-Trigger

Ein neuer unterstützter Authentifizierungsmodus im Clientprofil oder eine neue Sicherheitsrichtlinie des Betreibers.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-30 | Accepted – Option A mit fester Benutzerliste als Einstiegsimplementierung; Messung mit .NET 10 und `Microsoft.Data.SqlClient` 7.1.1; Schnittstelle als Entwurf | [SPEC-051](../../../spec/spezifikation.md#6-externe-verträge) |
| 2026-10-30 | Zurück auf Proposed – Review fordert Korrekturen vor erneutem Accepted | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0009.md) |
