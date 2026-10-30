# Authentifizierungsadapter

Vertrag zwischen Anmeldepfad und Benutzerverwaltung (`TDS-FA-02.a`, `SPEC-012`, `SPEC-051`). Der Anmeldepfad entschleiert LOGIN7 und ruft den Adapter; der Adapter entscheidet allein über Annahme oder Ablehnung. Die Schnittstelle ist ein **Entwurf**: Gemessen sind die Eingaben des Referenzclients (`Microsoft.Data.SqlClient` 7.1.1, .NET 10), nicht der Kotlin-Vertrag selbst; eine Kotlin-Signatur ist noch festzulegen.

## Eingabe

| Feld | Inhalt | Grenzen (gemessen) |
|---|---|---|
| Kennung | UTF-16, nach LOGIN7 `UserName` | 0 bis 128 UTF-16-Einheiten, auch leer; der Client wirft vor dem Verbindungsaufbau, wenn sie länger ist (128 und 129 nur mit ASCII gemessen) |
| Passwort | UTF-16, entschleiert (`Password`, je Byte XOR `0xA5`, dann Nibble-Tausch) | 0 bis 128 UTF-16-Einheiten, auch leer (128 und 129 nur mit ASCII gemessen; Unicode, Sonderzeichen und Surrogatpaar nur mit kürzeren Passwörtern, das mit Nicht-BMP-Zeichen hat 15 Einheiten). Entwurf: ein Zeichenfeld, das der Anmeldepfad nach dem Aufruf überschreibt; erfasst ist nur dieses Feld, nicht der Paket- oder TLS-Puffer |
| Verbindungskontext | Datenbank, Anwendungsname, Rechnername, Intent | Werte aus LOGIN7; der Rechnername ist vom Client frei gesetzt und kein Beleg einer Herkunft |
| Verschlüsselungsmodus | Entwurf: der ausgehandelte Modus aus PRELOGIN, nicht aus LOGIN7; nicht gemessen |
| Authentifizierungsmerkmale | `fIntSecurity` (SSPI-Block), FeatureExt-Option `0x02`, `fChangePassword` mit neuem Passwort | am LOGIN7 erkennbar |

Nicht unterstützte Modi (SSPI/Kerberos, Entra-ID-Token) erkennt der Anmeldepfad an den Merkmalen und lehnt sie ab, ohne den Adapter zu rufen. Ein Passwortwechsel ist in der ersten Version nicht Teil des Adaptervertrags; er wird abgelehnt.

## Ausgabe

| Wert | Bedeutung |
|---|---|
| `Accepted(principal)` | Anmeldung gelungen; der Principal geht in die Session |
| `Rejected(reason)` | abgelehnt; `reason` dient nur dem Server-Log |

Der Adapter liefert keinen Text und keine Nummer für den Client. Die Antwort an den Client ist ein festes Muster des Anmeldepfads (ERROR 18456, einheitlicher State, Class 14, feste Meldung ohne Kennung, danach DONE mit Fehlerstatus; `SPEC-012`). Ein Ausnahmefall im Adapter wird wie `Rejected` beantwortet und als interner Fehler geloggt (`SPEC-048`).

## Pflichten

- Das Passwort erscheint in keinem Log und in keiner Fehlermeldung, auch nicht gekürzt oder verschleiert. Die Kennung erscheint in keiner Antwort an den Client und im Log nur in einem Feld, das die Spezifikation festlegt (bisher keines; `TDS-FA-09.a`). `Rejected(reason)` trägt den Grund ohne Passwort und ohne Kennung.
- Anforderung an den Produktivcode der Einstiegsimplementierung: Der Passwortvergleich läuft in konstanter Zeit über die Länge hinaus; bei unbekannter Kennung läuft ein Scheinvergleich. Weder gebaut noch gemessen; ein Test muss es belegen. Ein Adapter mit eigener Benutzerverwaltung unterliegt derselben Anforderung, der Vertrag kann sie nicht erzwingen.
- Die Antwort an den Client ist für unbekannte Kennung und falsches Passwort in den Antwortbytes gleich (gemessen); das ist Sache des festen Antwortmusters des Anmeldepfads, nicht des Adapters.
- Ein Adapter blockiert den Anmeldepfad nicht länger als `LOGIN_TIMEOUT` (`SPEC-008`).

## Einstiegsimplementierung

Feste Benutzerliste aus der Konfiguration (Kennung, Passwortprüfwert). Die Liste wird beim Start validiert (`TDS-FA-09.a`); eine fehlerhafte Liste bricht den Start ab. Der Konfigurationsweg und das Format der Prüfwerte sind noch festzulegen. Für Test und kleine Betriebe gedacht.

## Gemessen

Siehe die Messmatrix in [ADR-0009](../docs/plan/adr/0009-authentifizierungsmodus.md): LOGIN7-Felder, Entschleierung, Ablehnungsbytes, erkennbare Modi. Nicht gemessen: Zeitgleichheit von unbekannter Kennung und falschem Passwort, Windows-SSPI (NTLM), Verschlüsselungsmodus als Adaptereingabe, TDS 8.0 mit SQL-Authentifizierung, LOGIN7 über mehrere Pakete, Pooling mit `ResetConnection`, andere Clients.
