# Authentifizierungsadapter

Vertrag zwischen Anmeldepfad und Benutzerverwaltung (`TDS-FA-02.a`, `SPEC-012`, `SPEC-051`). Der Anmeldepfad entschleiert LOGIN7 und ruft den Adapter; der Adapter entscheidet allein über Annahme oder Ablehnung. Gemessen sind die Eingaben des Referenzclients (`Microsoft.Data.SqlClient` 7.1.1, .NET 10), nicht der Kotlin-Vertrag selbst; der Verschlüsselungsmodus als Eingabe bleibt ein Entwurf. Die Kotlin-Signatur steht im Abschnitt „Kotlin-Signatur“.

## Eingabe

| Feld | Inhalt | Grenzen (gemessen) |
|---|---|---|
| Benutzername (in der Spezifikation und in der Messmatrix „Kennung“) | UTF-16, nach LOGIN7 `UserName` | 0 bis 128 UTF-16-Einheiten, auch leer; der Client wirft vor dem Verbindungsaufbau, wenn sie länger ist (128 und 129 nur mit ASCII gemessen) |
| Passwort | UTF-16, entschleiert (`Password`, je Byte XOR `0xA5`, dann Nibble-Tausch) | 0 bis 128 UTF-16-Einheiten, auch leer (128 und 129 nur mit ASCII gemessen; Unicode, Sonderzeichen und Surrogatpaar nur mit kürzeren Passwörtern, das mit Nicht-BMP-Zeichen hat 15 Einheiten). Entwurf: ein Zeichenfeld, das der Anmeldepfad nach dem Aufruf überschreibt; erfasst ist nur dieses Feld, nicht der Paket- oder TLS-Puffer |
| Verbindungskontext | Datenbank, Anwendungsname, Rechnername, Intent | Werte aus LOGIN7; der Rechnername ist vom Client frei gesetzt und kein Beleg einer Herkunft |
| Verschlüsselungsmodus | Entwurf: der ausgehandelte Modus aus PRELOGIN, nicht aus LOGIN7 | nicht gemessen |
| Authentifizierungsmerkmale | `fIntSecurity` (SSPI-Block), FeatureExt-Option `0x02`, `fChangePassword` mit neuem Passwort | am LOGIN7 erkennbar |

Nicht unterstützte Modi (SSPI/Kerberos, Entra-ID-Token) erkennt der Anmeldepfad an den Merkmalen und lehnt sie ab, ohne den Adapter zu rufen. Ein Passwortwechsel ist in der ersten Version nicht Teil des Adaptervertrags; er wird abgelehnt.

## Ausgabe

| Wert | Bedeutung |
|---|---|
| `Accepted(principal)` | Anmeldung gelungen; der Principal geht in die Session |
| `Rejected(reason)` | abgelehnt; `reason` dient nur dem Server-Log |

Der Adapter liefert keinen Text und keine Nummer für den Client. Die Antwort an den Client ist ein festes Muster des Anmeldepfads (ERROR 18456, einheitlicher State, Class 14, feste Meldung ohne Benutzernamen, danach DONE mit Fehlerstatus; `SPEC-012`). Danach schließt der Server die Verbindung; ein weiteres LOGIN7 wird nicht angenommen (Setzung, nicht gemessen: am echten SQL Server wurde nur die Fehlerantwort einmal beobachtet). Ein Ausnahmefall im Adapter wird wie `Rejected` beantwortet und als interner Fehler geloggt (`SPEC-048`).

## Pflichten

- Das Passwort erscheint in keinem Log und in keiner Fehlermeldung, auch nicht gekürzt oder verschleiert. Der Benutzername erscheint in keiner Antwort an den Client und im Log nur in einem Feld, das die Spezifikation festlegt (bisher keines; `TDS-FA-09.a`). `Rejected(reason)` trägt den Grund ohne Passwort und ohne Benutzernamen.
- Anforderung an den Produktivcode der Einstiegsimplementierung: Der Passwortvergleich läuft in konstanter Zeit über die Länge hinaus; bei unbekannter Kennung läuft ein Scheinvergleich. Weder gebaut noch gemessen; ein Test muss es belegen. Ein Adapter mit eigener Benutzerverwaltung unterliegt derselben Anforderung, der Vertrag kann sie nicht erzwingen.
- Die Antwort an den Client ist für unbekannten Benutzernamen und falsches Passwort in den Antwortbytes gleich (gemessen am Spike-Testserver, nicht am Produkt); das ist Sache des festen Antwortmusters des Anmeldepfads, nicht des Adapters.
- Ein Adapter blockiert den Anmeldepfad nicht länger als `LOGIN_TIMEOUT` (`SPEC-008`).

## Einstiegsimplementierung

Feste Benutzerliste aus der Konfiguration (Benutzername, Passwortprüfwert). Die Liste wird beim Start validiert (`TDS-FA-09.a`); eine fehlerhafte Liste bricht den Start ab. Für Test und kleine Betriebe gedacht.

Prüfwertformat und Konfigurationsweg (vom Nutzer entschieden, 2026-10-30; was unter „Offen“ steht, ist nicht entschieden):

- **Verfahrenskennung:** Jeder Eintrag trägt vor dem Prüfwert eine Verfahrenskennung, `{kennung}Wert`. Ein Eintrag ohne Verfahrenskennung und eine unbekannte Verfahrenskennung brechen den Start ab (Setzung, nicht gemessen). Die Verfahrenskennung ist case-sensitiv und klein: `plain`, `pbkdf2-sha256`; `{PBKDF2-SHA256}` wird abgewiesen.
- **Verfahren:** `plain` nur bei ausdrücklich eingeschaltetem Testmodus, sonst Startabbruch vor dem Binden des Ports; ein leerer `plain`-Wert ist zulässig (nur im Testmodus). `pbkdf2-sha256` (`PBKDF2WithHmacSHA256` des JDK, keine Fremdbibliothek), Wert `Salz$Iterationen$Hash`; Salz und Hash sind Standard-Base64 mit verlangtem Padding.
- **Iterationszahl (Setzung):** Boden 210 000, Standard 600 000, Obergrenze 10 000 000; der Betreiber unterschreitet den Boden nicht. Gemessen (nur in einem Spike-Image auf einem Host, kein Produktivwert) ist die Dauer je Ableitung: 54 ms bei 210 000, 125 ms bei 600 000, 2,1 s bei 10 000 000. Die Werte für Boden, Standard und Obergrenze sind gesetzt, nicht gemessen. Die Obergrenze schützt nicht vor gleichzeitiger Last: 64 Threads mit je 3 Ableitungen (192 Ableitungen) mit 600 000 und `--cpus=1` erreichten im Spike als längste Dauer einer Ableitung 23,6 s; das ist die Dauer der letzten Ableitung unter dieser Last, nicht die Wartezeit einer einzelnen Anmeldung. Die Iterationen bestehen nur aus ASCII-Ziffern (keine Vorzeichen, keine anderen Ziffernschriften).
- **Passwort-Randfälle:** Die Ableitung nimmt die UTF-8-Bytes des vom Client gelieferten Passworts (UTF-16 nach UTF-8 wie im JDK). Ein alleinstehendes Hochsurrogat ergibt denselben Hash wie `?` (gemessen); das ist eine bekannte Eigenschaft. Es gibt keine Unicode-Normalisierung (NFC und NFD ergeben verschiedene Hashes, gemessen). Ein leeres Passwort ist zulässig und wird abgeleitet.
- **Konfigurationsweg:** eigene Datei nur für die Benutzerliste, eigenes Zeilenformat, eigener Parser. Der Pfad wird in der Umgebungsvariable `AUTH_USERS_FILE` übergeben, wie der Schalter `AUTH_PLAIN_TESTMODE` (Setzung (Voreinstellung 2026-10-30, nicht gemessen)); gemessen ist nur die Übergabe eines Werts per Umgebungsvariable, nicht die eines Pfades. Eine Zeile je Eintrag, `Benutzername<TAB>Prüfwert`, UTF-8, kein Escaping, Aufteilung am ersten TAB. Benutzernamen mit TAB, LF, CR oder alleinstehendem Hochsurrogat sind nicht darstellbar und ein Ladefehler. Ein doppelter Benutzername bricht den Start ab.
- **Testmodus-Schalter:** `AUTH_PLAIN_TESTMODE`, gilt nur bei genau `true`; eigener Schalter nur für `plain`; der Start-Log nennt den Modus.
- **Fehlerwerte:** je Abweichung genau eine Fehlerart mit Listenplatz, ohne Prüfwert und ohne Passwort. Der Listenplatz ist die Zeilennummer der Datei, 1-basiert (Setzung (Voreinstellung 2026-10-30, nicht gemessen)); gemessen ist im Spike die Position nach Sortierung, nicht die Zeilennummer.
- **Offen** (vom Nutzer nicht entschieden, nicht gemessen): niedrigerer Iterationsboden im Testmodus, Kommentare und Leerzeilen, Zeilenenden (CRLF), Hashlänge, Mindestlänge des Salzes, Dateirechte im Betrieb, Länge der Ziffernfolge der Iterationen. Die Implementierung setzt dazu vorläufig die Festlegungen ohne Messung aus dem Abschnitt „Kotlin-Signatur“; Dateirechte prüft sie nicht.

## Kotlin-Signatur

Package `io.github.pt9912.tdswire.config.auth` (Schicht `config`); der Principal liegt in `io.github.pt9912.tdswire.domain`. Die Signatur ist eine Festlegung ohne Messung am Referenzclient oder an einem echten SQL Server:

```kotlin
interface AuthAdapter { fun authenticate(request: AuthRequest): AuthResult }

class AuthRequest(
    val userName: String, val password: PasswordChars, val context: ConnectionContext,
    val encryption: NegotiatedEncryption, val features: AuthFeatures,
)
sealed interface AuthResult {
    class Accepted(val principal: Principal) : AuthResult
    data class Rejected(val reason: RejectReason) : AuthResult   // UNKNOWN_USER, WRONG_PASSWORD, INPUT_TOO_LONG
}
```

- Der Aufruf **blockiert** und ist nicht `suspend`. Der Aufrufer hält den Transport-Thread frei und `LOGIN_TIMEOUT` ein (`SPEC-008`); eine Messung zur Wirkung einer blockierenden Ableitung auf den Transport liegt nicht vor.
- `PasswordChars` hält die UTF-16-Einheiten als Kopie; `reveal()` gibt eine Kopie, `wipe()` überschreibt. Das schützt nur dieses Feld, nicht den Paket-, Lese- oder TLS-Puffer und nicht jede Kopie der JVM. Der Anmeldepfad überschreibt sein Feld nach dem Aufruf; der Adapter überschreibt seine eigenen Kopien.
- Keine Textdarstellung von `AuthRequest`, `PasswordChars`, `ConnectionContext`, `Principal`, `Accepted`, der Benutzerliste und ihrer Einträge nennt Passwort, Benutzernamen oder Prüfwert.
- Eingaben über 128 UTF-16-Einheiten (Benutzername oder Passwort) ergeben `Rejected(INPUT_TOO_LONG)`, ohne Ausnahme und ohne Ableitung.
- `AuthFeatures` wertet die Einstiegsimplementierung nicht aus; der Anmeldepfad lehnt die nicht unterstützten Modi vor dem Adapter ab.

Validierung der Liste: `UserList.parse(text, plainTestMode)` liefert `Valid` oder `Invalid(UserListError(kind, line))`; `line` ist die 1-basierte Zeilennummer der Datei, bei einer Liste ohne Zeile `null`. Das Lesen der Datei, `AUTH_USERS_FILE` und das Auswerten von `AUTH_PLAIN_TESTMODE` (nur der Wert `true` schaltet ein, `UserList.plainTestModeFrom`) gehören der Konfiguration. Die Fehlerarten sind `UserListErrorKind`.

Festlegungen ohne Messung zu den offenen Punkten oben (einfachste strenge Variante, jeweils im KDoc von `UserList` und `Pbkdf2Limits` gekennzeichnet, vom Nutzer nicht entschieden):

- Der Iterationsboden 210 000 gilt auch im Testmodus.
- Keine Kommentare: eine Zeile mit `#` ist ein gewöhnlicher Eintrag. Eine leere Zeile ist ein Fehler; nur das Zeilenende am Dateiende ist erlaubt.
- Zeilenende ist LF; ein CR in einer Zeile (auch CRLF) ist ein Fehler.
- Der Hash hat genau 32 Byte, das Salz mindestens 16 Byte.
- Die Iterationen sind eine Ziffernfolge von 1 bis 8 Stellen ohne führende Null.
- Der erste Fehler in Dateireihenfolge bricht die Prüfung ab.
- Benutzernamen werden zeichengenau verglichen (keine Groß-/Kleinschreibung-Faltung, keine Normalisierung).
- Dateirechte und Übergabe der Datei sind weder geprüft noch entschieden.

Bauart des Vergleichs (Anforderung, strukturell belegt, nicht gemessen): Der Vergleich untersucht jede Position bis zur längeren Länge und bricht nicht beim ersten Unterschied ab. Bei unbekanntem Benutzernamen läuft dieselbe Ableitung beziehungsweise derselbe Vergleich wie für den ersten Eintrag der Liste gegen dessen Prüfwert (Scheinvergleich); das Ergebnis wird verworfen. Eine Aussage über Zeit ist daraus nicht abzuleiten.

## Gemessen

Siehe die Messmatrix in [ADR-0009](../docs/plan/adr/0009-authentifizierungsmodus.md): LOGIN7-Felder, Entschleierung, Ablehnungsbytes, erkennbare Modi. Die Messmatrix zu Prüfwert und Konfigurationsweg steht in [ADR-0011](../docs/plan/adr/0011-pruefwertformat.md): gemessen mit JDK 21 (Eclipse Adoptium 21.0.12.1) in einem Spike-Image auf einem Host (Intel Core i9-13900H): Ableitungsdauer je Iterationszahl, Kodierung des Passworts (wie UTF-8), Parser-Fälle, Sichtbarkeit des Prüfwerts je Weg; die Werte gelten nur für dieses Image und diese Hardware. Nicht gemessen: Dateirechte im Betrieb, Wirkung einer blockierenden Ableitung auf den Transport, Zeitgleichheit von unbekannter Kennung und falschem Passwort, Windows-SSPI (NTLM), Verschlüsselungsmodus als Adaptereingabe, TDS 8.0 mit SQL-Authentifizierung, LOGIN7 über mehrere Pakete, Pooling mit `ResetConnection`, andere Clients.
