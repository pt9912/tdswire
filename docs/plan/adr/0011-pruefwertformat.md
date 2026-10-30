# ADR-0011: Prüfwertformat und Konfigurationsweg der festen Benutzerliste

**Status:** Proposed

**Datum:** 2026-10-30

**Autor:** pt9912

**Bezug:** [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](0009-authentifizierungsmodus.md)

**Schärft:** [SPEC-051](../../../spec/spezifikation.md#6-externe-verträge)

---

## Kontext

[ADR-0009](0009-authentifizierungsmodus.md) (`Accepted`) wählt SQL-Authentifizierung über einen Adapter und als Einstiegsimplementierung eine feste Benutzerliste aus der Konfiguration. Sie nennt „Konfigurationsweg und Format der Prüfwerte“ in §Entscheidung als „noch festzulegen“ und in §Offen als offen; sie entscheidet beides nicht. Diese ADR füllt die benannte Lücke. Sie widerspricht keiner Aussage von ADR-0009 und ändert keine; deshalb steht hier `Bezug:` und kein `Supersedes`. ADR-0009 bleibt wörtlich stehen, ihr §Offen bleibt der Stand zum Zeitpunkt ihrer Annahme.

**Richtung des Nutzers** (Eingabe vor der Messung): konfigurierbares Format, vor dem Prüfwert eine Verfahrenskennung; `{plain}…` nur bei ausdrücklich eingeschaltetem Testmodus, sonst Startabbruch; `{pbkdf2-sha256}Salz$Iterationen$Hash` mit dem JDK-Verfahren `PBKDF2WithHmacSHA256` ohne Fremdbibliothek.

**Messobjekt und Umgebung.** Drei Spike-Images mit eigenem Dockerfile, ohne Mount, Basis-Image `gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06`, JDK Eclipse Adoptium 21.0.12.1, Kotlin 2.4.10 (Spike-Build mit Netz im Image-Build, ohne Lock und Prüfsumme). Host: Intel Core i9-13900H, 20 logische CPUs, 31 GiB RAM, Docker ohne CPU-Limit; wo angegeben `--cpus=1`. Der Prozess lief als `root`. **Alle Werte gelten nur für dieses Image und diese Hardware**; sie sind keine Leistungszusage ([TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance) ist nicht berührt) und sagen nichts über den Produktivbetrieb. „Abgeleitet“ ist gerechnet oder gefolgert, nicht gemessen. Der Spike-Code liegt nicht im Repo. Die Grenzen, die der Spike-Parser setzte (Untergrenze 100 000, Obergrenze 10 000 000, Hash 32 Byte, Salz nicht leer), dienten nur der Messung und sind keine Festlegung.

## Messmatrix

Auszug, soweit sie die Entscheidung trägt.

| Nr. | Messung | Ergebnis |
|---|---|---|
| M1-1 | `SecretKeyFactory`-Algorithmen im Image | 30, Provider `SunJCE`, darunter `PBKDF2WithHmac` mit `SHA1`, `SHA224`, `SHA256`, `SHA384`, `SHA512`, `SHA512/224`, `SHA512/256` |
| M1-2 | `scrypt`, `Argon2`, `bcrypt` als `SecretKeyFactory` | alle `NoSuchAlgorithmException`; Dienste vom Typ `KDF`: keine |
| M1-3 | Bekannte PBKDF2-HMAC-SHA256-Vektoren | beide stimmen mit den Erwartungswerten überein |
| M1-4 | Dauer je Ableitung (warm, Median, 256 Bit, Salz 16 Byte) | c = 10 000: 3,1 ms · 100 000: 23,9 · 210 000: 54,4 · 600 000: 124,5 · 1 000 000: 206,1 · 10 000 000: 2 086,6 · 30 000 000: 6 387,0 |
| M1-5 | Dasselbe mit `--cpus=1` | c = 600 000: 120,6 ms · 1 000 000: 199,9 · 10 000 000: 2 169,6 |
| M1-6 | Erste Ableitung im frischen JVM (kalt, ein Lauf) | c = 10 000: 118,5 ms · 600 000: 325,3 · 10 000 000: 2 616,8 |
| M1-7 | Gleichzeitige Ableitungen, c = 600 000, Median / Maximum | 20 CPUs, 64 Threads: 700,4 / 1 705,4 ms. `--cpus=1`: 16 Threads 2 856,5 / 3 796,6 · 64 Threads 10 478,8 / 23 598,0 ms |
| M1-8 | Andere HMAC-Variante, c = 600 000, warm | `SHA1` 270,8 · `SHA256` 150,7 · `SHA512` 563,1 ms (`SHA256` streute über Prozesse zwischen 120,6 und 150,7 ms) |
| M1-9 | Kodierung des Passworts vor der Ableitung (11 Fälle) | `char[]` gleich der UTF-8-Berechnung in allen 11 Fällen; UTF-16 stimmt nur bei wenigen Fällen überein und belegt keine UTF-16-Kodierung |
| M1-10 | Randfälle der Eingabe | leeres Passwort wird abgeleitet; das alleinstehende Hochsurrogat (`U+D800`) ergibt **denselben Hash wie `?`**; NFC und NFD ergeben verschiedene Hashes |
| M1-11 | Parameter der `PBEKeySpec` | Salz leer, Iterationen 0 oder −1, Länge 0 Bit: `IllegalArgumentException`; die Spec hält eine Kopie des Passwort-`char[]`; Zwischenpuffer im JDK nicht untersucht |
| M1-12 | Kodierung von Salz (16 Byte) und Hash (32 Byte) | Base64 24 und 44 Zeichen mit `=` (ohne Padding 22 und 43), Hex 32 und 64; über 100 000 Zufalls-Hashes kam `$`, `{`, `}`, `:` in keiner Kodierung vor, `=` nur in Base64; `Base64.getDecoder()` nimmt Eingaben ohne Padding an und lehnt Leerzeichen ab |
| M2-1 | Schema `{kennung}Rest`, 44 Fälle | Jeder Fall ergibt gültig oder genau eine Fehlerart mit Listenplatz; `}` und `$` im `plain`-Wert gehen durch; `{{plain}}`, `{ plain}`, `{PBKDF2-SHA256}` ergeben „unbekannte Verfahrenskennung“; ohne Verfahrenskennung ist im Spike ein Fehler (ohne Standardverfahren gebaut) |
| M2-2 | Fehlerwerte frei von Prüfwert und Passwort | in allen 44 Fällen enthält die Textform des Fehlerwerts den Prüfwert nicht; eine Kotlin-`data class` ohne eigenes `toString` druckt ihn (gemessen) |
| M2-3 | Meldungen der Aufrufe unter dem Parser | `Integer.parseInt` und `String.toInt` nennen die Eingabe in der Meldung; Base64-Decoder und `PBEKeySpec` nennen sie nicht |
| M2-4 | Iterationszahl: `toIntOrNull` gegen `Regex("[0-9]{1,10}")` | `toIntOrNull` nimmt `+600000`, `−5`, arabisch-indische und vollbreite Ziffern an; die Regex nur ASCII-Ziffern; führende Nullen ergeben 600 000 |
| M2-6 | Groß-/Kleinschreibung der Verfahrenskennung | Gleichheit mit der Kleinschreibung weist `{PBKDF2-SHA256}` ab; ein Vergleich nach `lowercase()` nimmt es an |
| M2-7 | Salz-Dekodierung | `Base64.getDecoder()` nimmt Salz ohne Padding an; URL-Alphabet und Leerzeichen lehnt es ab |
| M2-8, M2-9 | Alternativen A2 (eigenes Feld) und A3 (`$kennung$…`) | A2: Art „Salz ungültig“, wenn die Verfahrenskennung doch im Wert steht; A3: `$` trennt Kennung und Felder zugleich |
| M2-10 | Parser-Bibliothek | nicht gemessen |
| M3-1 | Schreiben und Lesen durch das Format selbst (`Properties`) | 23 von 23 Benutzernamen unverändert |
| M3-2 | Betreiber schreibt die Zeile von Hand, 23 Benutzernamen am Rand der Eingabe | `Properties`: 11 von 23 falsch gelesen (u. a. Leerzeichen, `:`, `=`, `\`, TAB, LF, CR). Eigenes Zeilenformat `Benutzername<TAB>Prüfwert`: 4 von 23 nicht darstellbar (TAB, LF, CR; das alleinstehende Hochsurrogat wird zu `?`); Leerzeichen, `:`, `=`, `#`, `\` kommen richtig zurück |
| M3-3 | `Properties`: Duplikate und Reihenfolge | doppelter Benutzername ohne Fehler (letzter Wert gewinnt); Durchlauf nicht in Dateireihenfolge; keine Zeilennummer. Mit eigener Zeilenlesung wird das Duplikat erkannt |
| M3-4 | `Properties`: Zeichensatz und Wertseite | `load(InputStream)` liest ISO-8859-1; ein `\` im Wert wird verschluckt, `\n` wird Zeilenumbruch; `$`, `}`, `#`, `=` bleiben erhalten |
| M3-5 | Sichtbarkeit des Werts je Weg (Container als `root`, `docker`-Befehle vom Host) | **Datei im Image:** `docker inspect`, `docker logs`, `/proc/1/environ`, `/proc/1/cmdline` ohne Treffer; `docker exec cat`, `docker cp`, `docker run --entrypoint cat`: lesbar. **Umgebungsvariable:** `docker inspect` (`Config.Env`) und `/proc/1/environ` zeigen den Wert. `docker top`, `docker save`: nicht ermittelt |
| M3-6 | Start mit `plain` ohne Testmodus (laden, validieren, dann erst Port binden) | Exit 1, Meldung `PLAIN_NOT_ALLOWED` mit Listenplatz, Port nicht gebunden, Prüfwert nirgends in stdout oder stderr. Mit Schalter `true`: Exit 0. `TRUE` wurde abgewiesen (der Spike liest genau `true`). Listenplatz im Spike = Position nach Sortierung, nicht Dateizeile |
| M3-7 | Größe von Umgebungsvariablen | bis 131 060 Zeichen je Variable und 1 500 000 gesamt angekommen; `--env-file`-Zeile über 64 KiB scheiterte im Docker-Client |

**Nicht gemessen:** Zeitunterschied zwischen unbekanntem Benutzernamen und falschem Passwort; Wirkung einer blockierenden Ableitung auf den Coroutinen-Transport ([ADR-0003](0003-transportframework.md)); Dateirechte und Übergabe im Betrieb; mehrzeilige Werte in Umgebungsvariablen; Parser-Bibliotheken; Kommentare, Leerzeilen und Zeilenenden (CRLF) im Zeilenformat; andere Hardware oder anderes Image; Last durch gleichzeitige Anmeldungen im Produkt.

**Abgeleitet, nicht gemessen:** Bei linearem Verlauf von M1-4 läge eine Ableitung von 15 s (`LOGIN_TIMEOUT`, [SPEC-008](../../../spec/spezifikation.md#3-defaults-und-konstanten)) bei rund 70 000 000 Iterationen; die Frist umfasst zusätzlich PRELOGIN und TLS-Handschlag, deren Dauer hier nicht gemessen ist. M1-7 zeigt für diesen Spike-Prozess bei `--cpus=1` und 64 gleichzeitigen Ableitungen ein Maximum über 15 s; für den Transport des Produkts gilt das nicht.

## Entscheidung

Die Optionen A1, B1, C2, D1 mit F2 und E2 sowie die unten benannten Zahlen, Passwort-Randfälle, Parserregeln und das Zeilenformat hat der Nutzer entschieden (2026-10-30). Was er nicht entschieden hat, steht als **offen**. Zwei Begriffe: **Verfahrenskennung** ist die Angabe `plain` oder `pbkdf2-sha256` in geschweiften Klammern vor dem Prüfwert; **Benutzername** ist der Schlüssel eines Eintrags (die Anmeldekennung des Clients, in der Messmatrix und im Spike „Kennung“ genannt; M2-Zeilen meinen die Verfahrenskennung, M3-Zeilen den Benutzernamen). Jede Aussage trägt ihre Art: Setzung (Wahl des Nutzers), gemessen (mit Messzeile) oder abgeleitet.

**Entschieden (Nutzer, 2026-10-30):**

- **A1 — Schema der Verfahrenskennung `{kennung}Wert`.** Jeder Eintrag trägt vor dem Prüfwert eine Verfahrenskennung in geschweiften Klammern; der Wert beginnt hinter der ersten `}` (M2-1). Ein Eintrag ohne Verfahrenskennung führt zum Startabbruch, eine unbekannte Verfahrenskennung ebenso. *Setzung, nicht gemessen:* M2-1 zeigt nur, dass der Spike-Parser ohne Standardverfahren „ohne Verfahrenskennung“ als Fehlerart ausweist, nicht, dass der Startabbruch die richtige Regel ist.
- **B1 — Verfahren `plain` und `pbkdf2-sha256`.** `plain` ist nur im Testmodus zulässig, sonst Startabbruch vor dem Binden des Ports (M3-6). `pbkdf2-sha256` ist das JDK-Verfahren `PBKDF2WithHmacSHA256` ohne Fremdbibliothek (M1-1, M1-3). Weitere Verfahren kommen als zusätzliche Verfahrenskennung hinzu. Ein Verfahren ohne Fremdbibliothek bleibt Bedingung für die erste Version; scrypt, Argon2 und bcrypt gibt es im JDK-Image nicht (M1-2).
- **C2 — Iterationszahl mit Standardwert und hartem Boden; Zahlen (Setzung).** Boden **210 000**, Standard **600 000**, Obergrenze **10 000 000**. Der Betreiber kann die Zahl nicht unter den Boden senken und nicht über die Obergrenze heben; M1-11: das JDK weist 0 und negative Werte ab. Belege für die Dauer je Ableitung (nur dieses Image und diese Hardware, warm, Median): 54,4 ms bei 210 000, 124,5 ms bei 600 000, 2 086,6 ms bei 10 000 000 (M1-4); mit `--cpus=1` 120,6 ms bei 600 000 und 2 169,6 ms bei 10 000 000 (M1-5). Die Obergrenze schützt vor einem einzelnen Eintrag mit sehr langer Ableitung (abgeleitet aus M1-4), **nicht** vor gleichzeitiger Last: 64 gleichzeitige Ableitungen mit 600 000 und `--cpus=1` erreichten ein Maximum von 23 598,0 ms (M1-7). Die Untergrenze gilt je Verfahren, weil gleiche Iterationszahl nicht gleiche Dauer heißt (M1-8). Ob im Testmodus ein niedrigerer Boden gilt, ist **nicht entschieden** (siehe Offen).
- **D1 und F2 — eigene Datei nur für die Benutzerliste mit eigenem Zeilenformat und eigenem Parser.** Duplikate sind erkennbar und die Reihenfolge ist erhaltbar (M3-3); die Datei ist von der übrigen Konfiguration getrennt, sodass diese keinen Prüfwert enthält (abgeleitet).
- **E2 — eigener Testmodus-Schalter nur für `plain`.** Er ist vom Schalter für die Abschwächung der Verschlüsselung ([SPEC-010](../../../spec/spezifikation.md#3-defaults-und-konstanten)) unabhängig (abgeleitet). Der Start-Log nennt den Modus (analog SPEC-010); `plain` ohne Schalter bricht den Start ab (M3-6).
- **Zeilenformat (Setzung):** eine Zeile je Eintrag, `Benutzername<TAB>Prüfwert`, Aufteilung am ersten TAB, kein Beschneiden von Leerzeichen, kein Escaping, Kodierung UTF-8 (Spike-Format in M3-2). Folge ohne Escaping: Benutzernamen mit TAB, LF, CR oder alleinstehendem Hochsurrogat sind nicht darstellbar (M3-2: TAB, LF, CR gemessen nicht darstellbar, das Hochsurrogat wird zu `?`); ein solcher Benutzername ist ein Fehler beim Laden (Setzung). Leerzeichen, `:`, `=`, `#`, `\` im Benutzernamen sind zulässig (M3-2). **Ein doppelter Benutzername** ist ein Startabbruch (Erkennbarkeit: M3-3). **Listenplatz** in Fehlerwerten: was er im Produkt ist (Dateizeile oder Position nach Sortierung), ist offen (Offen Nr. 7); gemessen ist im Spike die Position nach Sortierung, nicht die Dateizeile (M3-6).
- **Wertseite `pbkdf2-sha256` (Setzung):** `Salz$Iterationen$Hash`, genau drei Felder (M2-1); Salz und Hash als Standard-Base64 **mit Padding verlangt** (Zeichenvorrat ohne `$`, M1-12; ohne Padding nimmt `Base64.getDecoder()` die Eingabe an, M2-7, der Parser lehnt sie dennoch ab).
- **Parser streng (Setzung):** Die Iterationen bestehen nur aus ASCII-Ziffern, geprüft regex-artig; Vorzeichen und andere Ziffernschriften werden abgewiesen (M2-4: `toIntOrNull` nähme `+600000`, `−5`, arabisch-indische und vollbreite Ziffern an). Die Verfahrenskennung ist case-sensitiv und klein: `plain` und `pbkdf2-sha256`; `{PBKDF2-SHA256}` wird abgewiesen (M2-6: der Gleichheitsvergleich weist es ab). Ein leerer `plain`-Wert ist zulässig, nur im Testmodus (ein leeres Passwort ist als Client-Eingabe zulässig, M1-10, [ADR-0009](0009-authentifizierungsmodus.md)). Je Abweichung gibt es genau eine Fehlerart mit Listenplatz (M2-1). Fehlerwerte enthalten weder Prüfwert noch Passwort (M2-2); Meldungen von Aufrufen unter dem Parser werden nicht weitergereicht, weil `parseInt` die Eingabe nennt (M2-3).
- **Passwort-Randfälle (Setzung, dokumentierte Eigenschaft):** Das Passwort wird aus den UTF-8-Bytes des vom Client gelieferten Zeichenfelds abgeleitet (UTF-16 nach UTF-8 wie im JDK; das `char[]` ergibt in allen 11 Fällen dieselbe Ableitung wie die UTF-8-Berechnung, M1-9). **Bekannte Eigenschaft:** Ein alleinstehendes Hochsurrogat ergibt denselben Hash wie `?` (M1-10); zwei verschiedene Passwörter können dadurch denselben Prüfwert erfüllen. Es gibt **keine Unicode-Normalisierung**: NFC und NFD ergeben verschiedene Hashes (M1-10), das Passwort wird so abgeleitet, wie der Client es liefert. Ein leeres Passwort ist zulässig und wird abgeleitet (M1-10).
- **Schlüsselnamen (Setzung):** `AUTH_USERS_FILE` für den Pfad der Datei, `AUTH_PLAIN_TESTMODE` für den Schalter, der nur beim Wert `true` einschaltet (im Spike wurde `TRUE` abgewiesen, M3-6). Ob der Pfad per Umgebungsvariable oder per Argument übergeben wird, ist **offen** (Offen Nr. 7); `AUTH_USERS_FILE` ist der Schlüsselname, nicht der Übergabeweg. Gemessen ist nur die Übergabe eines Werts per Umgebungsvariable (M3-5), nicht die des Pfades.
- **Verantwortung** (Planannahme, nicht gemessen): Die Adapter-Seite hält den Prüfwert als Wert (Verfahren, Parameter, Salz, Hash), parst und validiert ihn, vergleicht und kennt den Testmodus als Eingabewert. Die Konfigurationsseite besitzt das Konfigurationsmodell, liest die Datei, reicht die Einträge als Text an die Validierung weiter, führt den Testmodus-Schlüssel und bricht bei einem Fehlerwert den Start ab.

**Sichtbarkeit des Werts (gemessen, M3-5):** Eine Datei im Image zeigt den Wert nicht in `docker inspect`, `docker logs`, `/proc/1/environ` und `/proc/1/cmdline`, ist aber für `docker exec`, `docker cp` und `docker run --entrypoint cat` lesbar (als `root` gemessen). Dateirechte und Übergabe im Betrieb sind weder gemessen noch entschieden; die Datei ist ein Geheimnis des Betreibers, und `plain`-Werte stehen darin im Klartext.

**Offen** (nicht entschieden; Entscheidung oder Messung im Auth-Slice, als solche geführt):

1. Niedrigerer Iterationsboden im Testmodus: ja oder nein, und welcher Wert.
2. Kommentare und Leerzeilen im Zeilenformat (nicht gemessen).
3. Zeilenenden, insbesondere CRLF (nicht gemessen).
4. Hashlänge und Mindestlänge des Salzes (der Spike setzte 32 Byte und „nicht leer“ nur zur Messung).
5. Dateirechte und Übergabe der Datei im Betrieb (nicht gemessen).
6. Zulässige Länge der Ziffernfolge der Iterationen (führende Nullen ergaben im Spike 600 000, M2-4).
7. Übergabeweg des Pfades `AUTH_USERS_FILE` (Umgebungsvariable oder Argument) und die Bedeutung des Listenplatzes in Fehlerwerten (Dateizeile oder Position nach Sortierung).

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A1 — Präfix `{kennung}`** (gewählt) | Der Rest hinter der ersten `}` bleibt unverändert, `}` und `$` im `plain`-Wert gehen durch (M2-1) | Drei zusätzliche Fehlerarten für die Verfahrenskennung (M2-1); ein Standardverfahren für Einträge ohne Kennung ist nicht gebaut |
| A2 — eigenes Feld `verfahren` | Wert und Verfahren getrennt (M2-8) | zwei Felder je Eintrag; Verfahrenskennung im Wert ergibt „Salz ungültig“ (M2-8) |
| A3 — `$kennung$…` | `plain` mit `$` im Wert geht durch (M2-9) | `$` trennt Verfahrenskennung und Felder zugleich; bildet die Richtung des Nutzers nicht ab (M2-9) |
| **B1 — `plain` und `pbkdf2-sha256`** (gewählt) | Startabbruch von `plain` ohne Testmodus messbar (M3-6); ein Test mit bekanntem Passwort braucht keine Ableitung (abgeleitet) | zweiter Prüfpfad; `plain`-Wert im Klartext in der Datei (M3-5) |
| B2 — nur `pbkdf2-sha256` | ein Prüfpfad; bekannte Vektoren stimmen (M1-3) | Erzeugung eines Prüfwerts zu bekanntem Passwort im Test nicht gemessen |
| B3 — plus weiteres JDK-Verfahren | andere `PBKDF2WithHmac`-Varianten vorhanden (M1-1) | scrypt, Argon2, bcrypt nicht im JDK-Image (M1-2); Dauer je Variante verschieden (M1-8) |
| C1 — feste Untergrenze | kein Konfigurationsfall | an dieser Hardware gemessen (M1-4, M1-5) |
| **C2 — Standard mit hartem Boden** (gewählt) | ein Test kann mit niedriger Zahl laufen, soweit der Boden es zulässt (M1-4) | weitere Festlegung und ein Konfigurationsschlüssel |
| C3 — keine Untergrenze | das JDK nimmt c = 1 (M1-3) | schwacher Prüfwert zulässig; Sicherheitswirkung nicht gemessen |
| D2 — Umgebungsvariablen | keine Datei; Größe kein Hindernis (M3-7) | Wert in `docker inspect` und `/proc/1/environ` (M3-5) |
| **D1 mit eigener Datei nur für die Benutzerliste** (gewählt) | Wert nicht in `docker inspect` und `/proc/1/environ` (M3-5) | per `docker exec` und `docker cp` lesbar (M3-5); Dateirechte nicht gemessen; ein Pfad mehr |
| F1 — `java.util.Properties` | JDK, keine Abhängigkeit (M3-1) | von Hand geschrieben 11 von 23 Benutzernamen falsch (M3-2); Duplikat ohne Fehler, Reihenfolge verloren (M3-3); `\` verschluckt (M3-4) |
| **F2 — eigenes Zeilenformat** (gewählt) | Duplikat erkennbar, Reihenfolge erhaltbar (M3-3); 19 von 23 Benutzernamen ohne Escaping (M3-2) | 4 von 23 nicht darstellbar (M3-2); eigener Code und eigene Regeln |
| F3 — Format mit Parser-Bibliothek | — | nicht gemessen (M2-10); neue Abhängigkeit mit Lock und Prüfsumme |
| E1 — gemeinsamer Testmodus | ein Schalter (M3-6) | mit anderen Abschwächungen geteilt, `plain` bliebe eher unbemerkt (abgeleitet) |
| **E2 — eigener Schalter** (gewählt) | unabhängig von anderen Abschwächungen (abgeleitet); Start wie bei E1 (M3-6) | ein weiterer Konfigurationsschlüssel ([TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)) |
| 0 — nichts festlegen | keine Vorfestlegung | die Startvalidierung der Benutzerliste nach ADR-0009 bliebe ohne Format |

## Konsequenzen

- **Positiv:** Verfahren, Prüfwertform und Konfigurationsweg der Benutzerliste sind festgelegt, soweit nicht unter Offen geführt; `plain` ist nur mit ausdrücklichem Schalter möglich; das Verfahren braucht keine Fremdbibliothek.
- **Negativ / Risiko:** Ein eigener Parser mit eigenen Regeln ist zu pflegen. Benutzernamen mit TAB, LF, CR oder alleinstehendem Hochsurrogat sind im Format nicht darstellbar (M3-2). Zwei Passwörter, die sich nur durch ein alleinstehendes Hochsurrogat gegenüber `?` unterscheiden, haben denselben Hash (M1-10); NFC- und NFD-Eingabe desselben Texts ergeben verschiedene Hashes (M1-10), ein Nutzer mit anderer Normalform kann abgewiesen werden (abgeleitet). Die Obergrenze der Iterationen schützt nicht vor gleichzeitiger Last (M1-7). Die Dauer einer Ableitung ist an Image und Hardware des Spikes gemessen und gilt weder für den Produktivbetrieb noch für gleichzeitige Anmeldungen (M1-7); die Wirkung einer blockierenden Ableitung auf den Coroutinen-Transport ist nicht gemessen. Der `plain`-Wert liegt im Klartext in der Datei.
- **Grenze:** Zeitgleichheit von unbekanntem Benutzernamen und falschem Passwort, Drosselung wiederholter Versuche und der Scheinvergleich bei unbekanntem Benutzernamen sind weder entschieden noch gemessen ([ADR-0009](0009-authentifizierungsmodus.md) §Offen, [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)); diese ADR legt dazu nichts fest.
- **Folgepflicht:** `SPEC-051` und `contracts/auth-adapter.md` führen das Format bereits (Stand `Proposed`); bei Ablehnung dieser ADR sind sie zurückzunehmen. Die Zahlen stehen oben. Die Punkte unter Offen werden im Auth-Slice entschieden oder gemessen. Bis zur Annahme bleibt die ADR `Proposed`.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Unit-Tests des Parsers | Jeder Fall aus M2-1 ergibt gültig oder genau eine Fehlerart mit Listenplatz; die Textform des Fehlerwerts enthält weder Prüfwert noch Passwort; `plain` ohne Schalter bricht den Start vor dem Binden des Ports ab. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Ein weiteres Verfahren neben `plain` und `pbkdf2-sha256`; ein anderes Dateiformat oder eine Parser-Bibliothek für die Benutzerliste; eine Messung auf dem Laufzeit-Image des Produkts, die die Dauer je Iterationszahl wesentlich anders zeigt als M1-4; eine Betreiberanforderung an die Übergabe der Datei.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-30 | Proposed – Optionen A1, B1, C2, D1 mit F2 und E2 gewählt (Entscheidung des Nutzers, 2026-10-30) nach Messung M1-1 bis M3-7; Iterationszahlen, Regelfragen und Zeilenformat-Einzelheiten offen; ADR-0009 bleibt unberührt | [ADR-0009](0009-authentifizierungsmodus.md) |
| 2026-10-30 | Proposed – Iterationszahlen (Boden 210 000, Standard 600 000, Obergrenze 10 000 000), Passwort-Randfälle (Kollision dokumentiert, keine Normalisierung), strenger Parser, Zeilenformat und Schlüsselnamen vom Nutzer entschieden; Testmodus-Boden, Kommentare, Leerzeilen, CRLF, Hashlänge, Salzlänge, Dateirechte offen | [ADR-0009](0009-authentifizierungsmodus.md) |
| 2026-10-30 | Nach Re-Review nachgeführt (Status weiter Proposed): Begriffe Verfahrenskennung und Benutzername getrennt, Übergabeweg des Pfades `AUTH_USERS_FILE` und Bedeutung des Listenplatzes als offen geführt, Folgepflicht im Ist-Zustand | [ADR-0009](0009-authentifizierungsmodus.md) |
