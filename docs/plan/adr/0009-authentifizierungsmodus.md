# ADR-0009: Authentifizierungsmodus und Authentifizierungsadapter

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)

**Schärft:** [SPEC-004](../../../spec/spezifikation.md#spec-004--testprofil), [SPEC-012](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-051](../../../spec/spezifikation.md#6-externe-verträge), [TDS-FA-02.a](../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung), [TDS-FA-09.a](../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

---

## Kontext

Das Lastenheft verlangt, den initial unterstützten Authentifizierungsmodus vor der Implementierung der Anmeldung festzulegen. Die Wahl bestimmt den Inhalt von LOGIN7, die Schnittstelle zum Authentifizierungsadapter und die Sicherheitsaussagen. Windows Integrated Authentication, Kerberos und Entra-ID sind im Lastenheft für die erste Version ausgeschlossen.

Gemessen mit `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 gegen einen Testserver in C# (Zertifikat `CN=localhost`, `TrustServerCertificate=true`; Passwörter sind Dummy-Werte):

- **LOGIN7:** Bei SQL-Authentifizierung ist die Nachricht 509 Byte lang (`TDSVersion=0x74000004`, `PacketSize=8000`), mit und ohne TLS-Tunnel dieselbe Struktur. Kennung und Passwort stehen als UTF-16LE mit Länge in UTF-16-Einheiten; Datenbank, Anwendungsname, Rechnername, Intent und Paketgröße stehen in eigenen Feldern.
- **Passwort:** Je Byte XOR `0xA5`, danach Nibble-Tausch; die Entschleierung (erst XOR, dann Tausch) ergab in allen Fällen den Eingabewert, auch bei Unicode, Surrogatpaar und Sonderzeichen. Das ist reversibel und kein Schutz: Im unverschlüsselten Testmodus liegt das Passwort lesbar auf der Leitung.
- **Grenzen:** Kennung und Passwort sind clientseitig auf 128 Zeichen begrenzt (längere Werte werfen vor dem Verbindungsaufbau); beide dürfen leer sein. Die Grenze 128/129 ist nur mit ASCII gemessen; ein Passwort mit CJK, Euro, Umlaut und Surrogatpaar hat 15 UTF-16-Einheiten, die Länge zählt Einheiten.
- **Annahme und Ablehnung:** Annahme mit LOGINACK und DONE genügt dem Client, FEATUREEXTACK verlangt er nicht. Die Ablehnung braucht genau ein ERROR (Nummer 18456, State, Class 14) und DONE mit Fehlerstatus; der Client macht daraus eine `SqlException` mit denselben Werten und wiederholt nicht. Weitere Tokens verändern `Message` und `Errors`; fehlt das DONE oder schließt der Server ohne Antwort, meldet der Client Verbindungsfehler der Klasse 20.
- **Gleiche Ablehnung:** Mit fester Meldung `Login failed.` sind die Antwortbytes für unbekannte Kennung und falsches Passwort identisch (SHA-256 gleich); mit dem Namen der Kennung unterscheiden sie sich nur im Namen. Ein unterschiedlicher State wird bis zur Anwendung durchgereicht.
- **Nicht unterstützte Modi:** `Integrated Security` sendet LOGIN7 mit `fIntSecurity` und SSPI-Block (GSS-API-Token), nur wenn der Client sein Token erzeugen konnte; gemessen nur mit einem MIT-Kerberos-Token, nicht mit Windows-SSPI (NTLM); `Authentication=ActiveDirectory…` sendet LOGIN7 mit leerer Kennung und leerem Passwort und FeatureExt `0x02`. Beide sind am LOGIN7 erkennbar, eine Ablehnung mit 18456 zeigt der Client wie eine falsche Anmeldung.
- **Passwortwechsel:** `ChangePassword` setzt `fChangePassword` und trägt das neue Passwort verschleiert im eigenen Feld, das alte steht weiter im Passwortfeld.
- **Nicht gemessen:** der Zeitunterschied zwischen unbekannter Kennung und falschem Passwort; Windows-SSPI (NTLM) und die Erkennung von `fIntSecurity` mit einem anderen Token als MIT-Kerberos; der Verschlüsselungsmodus als Eingabe des Adapters (er stammt aus PRELOGIN, nicht aus LOGIN7); TDS 8.0 mit SQL-Authentifizierung; LOGIN7 über mehrere Pakete; Pooling mit `ResetConnection`; die Server-SSPI-Antwort und das Entra-ID-Token; andere Clients; State und Meldung eines echten SQL Servers.

**Messmatrix** (Auszug, soweit sie die Entscheidung trägt; Testserver im Client-Container, ohne Mount; Spike-Code nicht im Repo):

| Fall | Beobachtung |
|---|---|
| Annahme: LOGINACK und DONE (66 Byte) | `Open` gelingt; kein FEATUREEXTACK verlangt |
| Ablehnung: ein ERROR (18456, Class 14) und DONE mit Fehlerstatus | sofortige `SqlException` mit denselben Werten, kein zweiter Versuch; im TLS-Tunnel identisch |
| zwei ERROR oder INFO vor ERROR | `Errors=2`, `Message` verkettet |
| nur ERROR ohne DONE, Server schließt | Fehler 18456 plus Verbindungsfehler Klasse 20 |
| Server schließt nach LOGIN7 ohne Antwort | Klasse 20 |
| Meldung `Login failed for user '<Name>'.` für unbekannte Kennung und falsches Passwort, gleicher State | Antwortbytes unterscheiden sich nur im Namen |
| feste Meldung `Login failed.` für beide Fälle | Antwortbytes identisch (SHA-256 gleich) |
| State 5 bzw. 8 | der Client reicht den State durch, die Anwendung unterscheidet beide Fälle |
| Passwort 128 Zeichen (ASCII) | gesendet, LOGIN7 745 Byte |
| Passwort 129 oder 1000 Zeichen, Kennung 129 Zeichen | `ArgumentException` vor dem Verbindungsaufbau |
| leere Kennung, leeres Passwort | `cch=0`, `Open` gelingt |
| `Integrated Security` mit MIT-Kerberos-Token | `fIntSecurity`, SSPI-Block 770 Byte (GSS-API-Token); Ablehnung 18456 erscheint wie eine falsche Anmeldung |
| `Authentication=ActiveDirectory…` | leere Kennung und leeres Passwort, FeatureExt `0x02` |
| `ChangePassword` | `fChangePassword`, neues Passwort verschleiert im eigenen Feld, altes im Passwortfeld |

## Entscheidung

Wir wählen **Option A**: SQL-Authentifizierung (Kennung und Passwort im LOGIN7) über eine austauschbare Adapter-Schnittstelle. Die Einstiegsimplementierung ist die einfachste Variante, die feste Benutzerliste aus der Konfiguration (Option B der Alternativen); Option C ist nicht gewählt.

- **Schnittstelle:** Der Anmeldepfad entschleiert LOGIN7 und ruft den Adapter mit Kennung, Passwort (beide UTF-16, 0 bis 128 Einheiten, auch leer), Verbindungskontext und Authentifizierungsmerkmalen. Der Adapter antwortet mit angenommen (samt Principal) oder abgelehnt (mit einem Grund nur für das Log). Die Eingaben sind aus der Messung abgeleitet; Eingaben und Ausgaben in `contracts/auth-adapter.md` sind ein **Entwurf** und nicht gemessen, die Kotlin-Signatur ist noch festzulegen. Der Verschlüsselungsmodus als Eingabe kommt aus PRELOGIN und ist nicht gemessen.
- **Antwort an den Client:** Ein festes Muster des Anmeldepfads, nicht des Adapters: genau ein ERROR (18456, Class 14, einheitlicher State, feste Meldung ohne Kennung), danach DONE mit Fehlerstatus. Damit sind die Antwortbytes für unbekannte Kennung und falsches Passwort gleich (`SPEC-012`).
- **Nicht unterstützte Modi:** SSPI/Kerberos und Entra-ID erkennt der Anmeldepfad an `fIntSecurity` und FeatureExt `0x02` und lehnt sie mit demselben Muster ab, ohne den Adapter zu rufen. Ein Passwortwechsel wird in der ersten Version abgelehnt.
- **Einstiegsimplementierung:** Feste Benutzerliste, beim Start validiert; Konfigurationsweg und Format der Prüfwerte sind noch festzulegen.
- **Auswahlgrund:** Die Messung entscheidet zwischen A, B und C nicht: LOGIN7 trägt für alle drei dieselben Felder, und ob ein Backend die Zugangsdaten sieht (C), ist am Client nicht abzulesen. Gewählt ist A wegen der Betreiberanforderung (Entscheidung des Nutzers, 2026-10-30): „Betreiber sollen später eine eigene Benutzerverwaltung über den Adapter anbinden können; bis dahin gilt die feste Benutzerliste aus der Konfiguration.“ Grenze: Das Lastenheft nennt den Betreiber der .NET-Anwendung als Verantwortlichen der Festlegung, ohne dass eine Festlegung durch ihn vorliegt; diese Anforderung ist daher nicht abnahmebindend. Die Messung stützt nur, dass die Schnittstelle schmal sein kann.
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
- **Geheimnisschutz:** Entwurf: Der Anmeldepfad überschreibt das entschleierte Passwortfeld nach dem Adapteraufruf. Nicht erfasst sind der Paket- und Lesepuffer, der TLS-Puffer und Kopien im Adapter oder in der JVM; die Aussage schützt nicht gegen ein Speicherabbild. Das Passwort erscheint in keinem Log und keiner Fehlermeldung, auch nicht gekürzt oder verschleiert; die Kennung erscheint in keiner Antwort an den Client und im Log nur in einem Feld, das die Spezifikation festlegt (bisher keines); `Rejected(reason)` trägt den Grund ohne Passwort und Kennung (`TDS-FA-09.a`, `SPEC-012`, [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)). Anforderung an den Produktivcode der Einstiegsimplementierung: Der Vergleich läuft in konstanter Zeit, bei unbekannter Kennung mit Scheinvergleich; weder gebaut noch gemessen. Die Verschleierung im LOGIN7 schützt nicht; der Schutz auf der Leitung ist TLS ([ADR-0002](0002-tls-und-verschluesselung.md)), im unverschlüsselten Testmodus liegt das Passwort lesbar vor und das Log nennt den Modus.
- **Grenze der Gleichheit:** Gleich sind die Antwortbytes (gemessen am Spike-Testserver; das Produkt existiert noch nicht, seine Gleichheit ist eine Anforderung). Zeitgleichheit von unbekannter Kennung und falschem Passwort ist nicht gemessen; der konstante Vergleich ist eine Anforderung an den Produktivcode, die ein Test belegen muss. Ein Adapter mit eigener Benutzerverwaltung kann die Zeitgleichheit verletzen; der Vertrag nennt die Anforderung, kann sie aber nicht erzwingen.
- **Folgepflicht:** `SPEC-004` und `SPEC-012` auf den Modus festlegen (geschehen), Schnittstelle in `contracts/auth-adapter.md` und `SPEC-051` führen (Entwurf), `TDS-FA-02.a` und `TDS-FA-09.a` nachziehen (geschehen). Die Architektur-Sicht benennt den Authentifizierungsadapter bereits und bleibt ohne ADR-Bezug unverändert.
- **Offen:** Konfigurationsweg und Prüfwertformat der Benutzerliste; Kotlin-Signatur; Zeitgleichheit; Schutz gegen wiederholte Anmeldeversuche (Drosselung, Sperre): weder entschieden noch gebaut, bei gleicher Ablehnung bleibt Raten ungebremst ([TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)); TDS 8.0 mit SQL-Authentifizierung und LOGIN7 über mehrere Pakete; Verhalten bei Pooling mit `ResetConnection`; State und Meldung eines echten SQL Servers (`SPEC-045`).

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                                | Make-Target                    |
| ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| JVM-/Protokolltests und .NET-Referenzclient | Gültige Zugangsdaten führen zu einer nutzbaren Sitzung, ungültige zu einem definierten Anmeldefehler ohne Sitzung; die Antwortbytes für unbekannte Kennung und falsches Passwort sind gleich; Integrated Security und Entra-ID werden abgelehnt; Passwörter erscheinen in keinem Log. Noch kein bestandenes Gate. | — (Gate noch einzurichten)     |

## Re-Evaluierungs-Trigger

Ein neuer unterstützter Authentifizierungsmodus im Clientprofil oder eine neue Sicherheitsrichtlinie des Betreibers.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-30 | Accepted – Option A mit fester Benutzerliste als Einstiegsimplementierung; Messung mit .NET 10 und `Microsoft.Data.SqlClient` 7.1.1; Schnittstelle als Entwurf | [SPEC-051](../../../spec/spezifikation.md#6-externe-verträge) |
| 2026-10-30 | Zurück auf Proposed – Review fordert Korrekturen vor erneutem Accepted | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0009.md) |
| 2026-10-30 | Korrektur nach Review (F-1 bis F-10): Aussagen auf das Gemessene beschränkt, Messmatrix in die ADR, Betreiberanforderung benannt, Log-Pflicht vereinheitlicht, Drosselung als offen. Hard Rule 3.5 bindet nur `Accepted`; die ADR wurde per Nutzerentscheidung auf Proposed zurückgestuft und ist inhaltlich änderbar, ohne dass die Regel verletzt wird; Accepted folgt erst nach Verifikation | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0009.md) |
