# Verifikationsbericht: slice-auth-adapter-benutzerliste am Stand bd9eac0 — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst.

**Gegenstand:** [slice-auth-adapter-benutzerliste](../plan/planning/in-progress/slice-auth-adapter-benutzerliste.md) (Welle welle-handshake-login), Merge `bd9eac0`, Diff `7c9624c..bd9eac0` (15 Dateien, +1190 / −7). Bezug: [TDS-FA-02](../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [ADR-0011](../plan/adr/0011-pruefwertformat.md). Vorausgegangener Review: [2026-10-30-bd9eac0-slice-auth-adapter-benutzerliste](2026-10-30-bd9eac0-slice-auth-adapter-benutzerliste.md).

**Stand-Hinweis:** `main` steht auf `4bdef21`; seit `bd9eac0` änderten sich nur Berichte unter `docs/reviews/`. Alle Läufe unten betreffen `bd9eac0`. Läufe und Mutationen liefen in frischen Klonen im Scratchpad (`.../scratchpad/vauth/`), je Mutation ein eigener Klon. Der Arbeitsbaum des Repos blieb unverändert; `git status` zeigt dort nur `.claude/worktrees/` (untracked, nicht von diesem Lauf angelegt, der Anfangsstand war sauber).

**Behauptung ≠ Bestätigung:** Der Slice trägt keine Closure-Notiz, keine DoD-Häkchen und keine Messwerte (§7: „Wird bei der Closure gefüllt“). Die Behauptungen des Implementers (Tests grün, `make gates` grün, Orakel unabhängig, Mutations-Eigenschaften) liegen nur im Auftrag und im Review vor; jede wurde selbst gefahren.

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `docker build --target test` (`bd9eac0`, Klon) | Exit 0, **171 Tests PASSED, 0 FAILED** (Basis `7c9624c`: 143); Zuwachs **28** = 6 + 2 + 10 + 10 `@Test` in den vier Testklassen von `config/auth` |
| `make gates` (Klon) | Exit 0, 7,1 s (großteils aus dem Cache); `baseline-verify: v6.18.1 OK — 54 Dateien`, `d-check: 172 Datei(en) geprüft, 0 Befund(e)` |
| `docker build --no-cache-filter detekt --target detekt` | Exit 0, ohne Cache: `detekt`, `detektMain`, `detektTest` `BUILD SUCCESSFUL in 1m 19s`; `tools/suppression-check.sh src` lief davor |
| `make docs-check` (Klon) | 172 Dateien, 0 Befunde |
| `make spec-abdeckung` (Klon) | [SPEC-051](../../spec/spezifikation.md#6-externe-verträge): 28 Tests, „nachgewiesen“ (vorher „OHNE NACHWEIS“); SPEC-012: 1 Test, „nachgewiesen“. Die Datei `docs/plan/spec-abdeckung.md` im Repo ist dabei **nicht** aktuell (Finding L-4) |
| Suche `Suppress` in `src` | kein Treffer (Teil von `make detekt`) |
| `gradle.lockfile`, `settings-gradle.lockfile`, `gradle/`, `build.gradle.kts`, `Dockerfile`, `config/` | `git diff 7c9624c bd9eac0` leer: **keine neue Abhängigkeit** |

## 2. DoD Punkt für Punkt

### Liefer-Punkt 1 — Schnittstelle (`config`)

| Aussage | Urteil | Beleg |
|---|---|---|
| Package `config.auth`, Wertetypen nur wo mehrere Schichten sie brauchen im Package `domain` | **bestätigt** | `AuthAdapter.kt`, `PasswordChars.kt`, `UserList*.kt`, `SecretValueParser.kt`, `StoredSecret.kt` in `config/auth`; `domain/Principal.kt` neu, `domain/Placeholder.kt` gelöscht (Build trägt es) |
| Signatur `authenticate(AuthRequest): AuthResult`; Eingabe Kennung, Passwort als überschreibbares Zeichenfeld, Kontext, Merkmale, Verschlüsselungsmodus (als Entwurf gekennzeichnet) | **bestätigt** | `AuthRequest(userName, password: PasswordChars, context, encryption, features)`; `NegotiatedEncryption` trägt im KDoc „Festlegung ohne Messung (Entwurf des Vertrags)“ |
| Ausgabe `Accepted(principal)` oder `Rejected(reason)` | **bestätigt** | `sealed interface AuthResult`; `RejectReason` = `UNKNOWN_USER`, `WRONG_PASSWORD`, `INPUT_TOO_LONG` (Aufzählung ohne Text aus der Eingabe) |
| Code und `contracts/auth-adapter.md` konsistent (Abschnitt „Kotlin-Signatur“) | **bestätigt, mit Rest** | Paket, Typen, Felder, die drei Ablehnungsgründe, `UserList.parse`, `plainTestModeFrom`, 128-Grenze, „blockierend, nicht `suspend`“ stimmen mit dem Code überein. `ConnectionContext`, `AuthFeatures`, `NegotiatedEncryption` stehen nicht im Codeblock, wohl aber in den Aufzählungen. Rest: der Abschnitt „Pflichten“ sagt zum Scheinvergleich weiter „Weder gebaut noch gemessen“ (L-3) |
| Test belegt: keine Textdarstellung von Eingabe, Ausgabe, Benutzerliste enthält Passwort, Kennung oder Prüfwert ([TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)) | **bestätigt** | `AuthTextRepresentationTest`, 13 Fälle; Mutationen M11a bis M11e und M25 (Abschnitt 4) machen ihn rot, auch ein Leck in `Principal`, `Accepted`, `PasswordChars`, `AuthRequest`, `UserList` |
| Vertrag führt die festgelegte Signatur, Entwurfs-Hinweis nachgezogen | **bestätigt** | Diff `contracts/auth-adapter.md`: Einleitung nennt „Verschlüsselungsmodus bleibt ein Entwurf“, die Kotlin-Signatur steht im neuen Abschnitt |

### Liefer-Punkt 2 — Einstiegsimplementierung

| Aussage | Urteil | Beleg |
|---|---|---|
| bekannte Kennung, richtiges Passwort → `Accepted` mit Principal | **bestätigt** | Test „Richtiges Passwort wird angenommen“; M14 (Annahme vor Benutzerprüfung) rot |
| falsches Passwort, unbekannte Kennung → `Rejected` mit Grund ohne Passwort und Kennung | **bestätigt** | Test „Falsches Passwort und unbekannter Benutzer werden abgelehnt“ (Groß-/Kleinschreibung, um ein Zeichen abweichend, Leerzeichen) |
| leere Kennung, leeres Passwort | **bestätigt** | Test „Leere Kennung und leeres Passwort …“ (pbkdf2 und `plain`) |
| 128 UTF-16-Einheiten angenommen, 129 abgelehnt ohne Ausnahme und ohne Ableitung, Kennung wie Passwort | **bestätigt** | Test „Grenze von 128 Einheiten“; M13a (129 erlaubt), M13b (`>=`), M13c (Kennung ungeprüft) alle rot |
| Surrogatpaar | **bestätigt** | 64 Paare = 128 Einheiten angenommen (`paare`), Ableitung gegen das unabhängige Orakel mit `😀x` |
| Hochsurrogat-Kollision (`\uD800` wie `?`) | **bestätigt** | Adapter-Test „Dokumentierte Eigenschaften“ und Primitive-Test; Python-Gegenrechnung bestätigt `?` = `\uD800` (Abschnitt 5) |
| keine Normalisierung (NFC ≠ NFD) | **bestätigt** | Adapter-Test; M20 (NFC-Normalisierung eingebaut) rot; Python bestätigt NFC ≠ NFD |
| Scheinvergleich strukturell: gleiche Ableitung (Iterationen, Salzlänge, Ausgabelänge) und voller Vergleich | **bestätigt, mit Einschränkung** | Test „Unbekannter Benutzer führt Ableitung und Vergleich aus“ und `plain`-Test; M01, M02 rot. Einschränkung: bewiesen nur für eine Liste mit einheitlichen Parametern, in der der bekannte Benutzer selbst der erste Eintrag ist (M-1) |
| Vergleich bricht nicht beim ersten Unterschied ab, untersucht bis zur längeren Länge | **bestätigt** | Tests „Vergleich bricht nicht …“ und „unterscheidet Längen“ (`steps` = 32/32/32 bzw. 4); M03, M04, M05 rot |
| keine Zeitaussage | **bestätigt** | Suche nach „konstant“, „zeitgleich“, „unterscheidbar“: Treffer nur im Vertrag, dort als „Anforderung … weder gebaut noch gemessen“ (Zeile 29) und „nicht gemessen“; im Code nur „Bauart (Anforderung, strukturell belegt, nicht gemessen)“. Dieser Bericht trifft ebenfalls keine Zeitaussage über den Vergleich |

### Liefer-Punkt 3 — Validierung der Liste ([TDS-FA-09.a](../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose))

| Aussage (gegen [ADR-0011](../plan/adr/0011-pruefwertformat.md)) | Urteil | Beleg |
|---|---|---|
| Verfahrenskennung case-sensitiv klein; `{PBKDF2-SHA256}`, `{Plain}`, `{ plain}`, `{{plain}}`, `{}` abgewiesen; ohne Kennung eigene Fehlerart | **bestätigt** | Test „Verfahrenskennung fehlt oder ist unbekannt“; M15 (`lowercase()`) rot |
| Iterationen nur ASCII-Ziffern, 1 bis 8 Stellen, keine führende Null; Vorzeichen, arabisch-indische und vollbreite Ziffern abgewiesen | **bestätigt** | Test „Iterationen sind streng“ (12 ungültige Formen); M09d (führende Null) und M09e (9 Stellen) rot. M09f (`\d` statt `[0-9]`) überlebt, ist aber äquivalent (L-5) |
| Boden 210 000, Obergrenze 10 000 000 (Randwerte 209 999 / 210 000 / 10 000 000 / 10 000 001) | **bestätigt** | M09a (Boden 200 000), M09b (`<=`), M09c (`>=`) rot |
| Base64 mit Padding, kanonisch (Rück-Kodierung) | **bestätigt** | Test „Salz und Hash sind Base64 …“ (ohne Padding, Leerzeichen, nicht-kanonische Endbits, URL-Alphabet); M07 (Kanonizität entfernt) rot. M08 (Form-Prüfung entfernt) überlebt, ist äquivalent, weil die Rück-Kodierung dieselben Fälle fängt (L-5) |
| Salz ≥ 16 Byte, Hash genau 32 Byte | **bestätigt** | 15/16/40 Byte Salz, 31/32/33 Byte Hash im Test; M16 (`>=`), M17 (Salz 8) rot |
| CR irgendwo in der Zeile ist Fehler; leere Zeile ist Fehler; nur das Zeilenende am Dateiende erlaubt | **bestätigt** | Test „Zeilenform …“; M19 (CR erlaubt), M21 (leere Zeile erlaubt) rot |
| Duplikat | **bestätigt** | M12 rot |
| Zeilennummer 1-basiert, `null` bei Liste ohne Zeile | **bestätigt** | M18 (0-basiert) rot mit 7 Tests |
| 17 Fehlerarten | **bestätigt** | `enum UserListErrorKind` gezählt: 17. Zugeordnet im Test: `EMPTY_LIST`, `EMPTY_LINE`, `MISSING_TAB`, `CARRIAGE_RETURN`, `EMPTY_USER_NAME`, `DUPLICATE_USER_NAME`, `MISSING_SCHEME`, `INVALID_USER_NAME`, `UNKNOWN_SCHEME`, `PLAIN_NOT_ALLOWED`, `FIELD_COUNT`, `SALT_INVALID`, `SALT_TOO_SHORT`, `ITERATIONS_INVALID`, `…_BELOW_FLOOR`, `…_ABOVE_CEILING`, `HASH_INVALID` — alle 17 kommen in `UserListParserTest` vor |
| `plain` nur bei `AUTH_PLAIN_TESTMODE` genau `true` | **bestätigt** | `plainTestModeFrom` und `parsePlain`; M10a (Schalter ignoriert), M10b (`ignoreCase`), M10c (`!= null`) rot. Der Test prüft `TRUE`, `True`, `1`, `yes`, `" true"`, `"true "`, `false`, `null`, `""` |
| Fehlerwert nennt Art und Zeilennummer, nie Passwort oder Prüfwert | **bestätigt** | `UserListError(kind, line)` ohne Textfeld; Test „Fehlerwert nennt weder Prüfwert noch Namen“ (4 Eingaben) |
| Welche Fälle den Start abbrechen, als Festlegung im KDoc gekennzeichnet | **bestätigt** | KDoc von `UserList` („Festlegungen ohne Messung …“) und `Pbkdf2Limits`; der Vertrag führt dieselbe Liste |
| Die fünf offenen Punkte der ADR „vom Nutzer entschieden oder gemessen, sonst weiter offen“ | **nicht bestätigt** | Alle fünf sind im Code verbindlich gesetzt („vom Nutzer nicht entschieden“) und führen zum Startabbruch; weder entschieden noch gemessen noch als offen geführt. Das ist Review F-3 und bleibt eine Entscheidung des Nutzers oder des Architect. Der Code kennzeichnet es ehrlich als Festlegung; die DoD-Alternative „weiter offen mit Grund in der Closure“ ist nicht gewählt, weil die Closure fehlt |

### Nachweis-Pflichten

| Punkt | Urteil | Beleg |
|---|---|---|
| Jeder Test trägt im KDoc `@spec SPEC-<NNN>`; Tests in `make test` und grün | **bestätigt** | 28 `@Test`, 29 `@spec`-Zeilen (ein Test trägt SPEC-051 und SPEC-012), alle ohne Abweichung von `SPEC-<NNN>`; `make spec-abdeckung` führt sie als Beleg |
| Jede Aussage an einem Test oder als Festlegung (Zuordnung *Test \| Festlegung* in der Closure-Notiz) | **nicht bestätigt (offen)** | Closure-Notiz fehlt; im Code stehen die Festlegungen am KDoc. Aussagen über Beleg hinaus: L-1, L-3 |
| Überschreiben des Passwortfelds schützt nur dieses Feld; kein KDoc behauptet mehr | **teilweise** | `PasswordChars`-KDoc und Vertrag sind eng gefasst; zwei der vier Überschreib-Schritte sind ohne Test (M06c, M06d überleben) und der Schlüsselinhalt des JDK-Objekts wird nicht überschrieben (L-1) |
| `make gates` grün | **bestätigt** | Abschnitt 1 |
| Review durchgeführt, Report liegt vor | **bestätigt** | [Review-Report](2026-10-30-bd9eac0-slice-auth-adapter-benutzerliste.md); F-1 und F-3 (MEDIUM) sind im Baum unverändert offen |
| Doku-Update, falls öffentlicher Vertrag berührt | **bestätigt, mit Rest** | `contracts/auth-adapter.md` nachgezogen; Rest L-3, L-4 |
| Closure-Notiz mit Lerneintrag | **nicht bestätigt (offen)** | §7 „Wird bei der Closure gefüllt“ |
| Beobachtungs-Register fortgeschrieben (oder „keine Beobachtung“ in §7) | **nicht bestätigt (offen)** | unter `docs/plan/planning/observations/BEO-TDS/` keine Datei zu diesem Slice, §7 leer |
| Jedes Risiko aus §6 mit Ausgang | **nicht bestätigt (offen)** | alle sechs stehen auf „offen bis zur Closure“ |
| Drei Paarungen | **nicht bestätigt (offen)** | an die Closure gebunden |

Die offenen Punkte der Nachweis-Pflichten sind Closure-Arbeit, keine Verletzung: Der Slice liegt in `in-progress/` und darf noch nicht nach `done/`.

## 3. Schichten, Out-of-Scope, Abhängigkeiten

* **Schichten:** `config` und `domain` importieren zusammen genau `domain.Principal` und JDK-Typen (`java.util.Base64`, `java.nio.CharBuffer`, `javax.crypto.SecretKeyFactory`, `javax.crypto.spec.PBEKeySpec`). Importregel der Schicht und `make detekt` ohne Cache grün. Keine Verwendung von `transport`, `protocol`, `runtime`.
* **Out-of-Scope eingehalten:** Suche in `config/auth` nach `File`, `Files`, `Path`, `System.getenv`, `Thread`, `nanoTime`, `currentTimeMillis`: kein Treffer (das Wort „Thread“ steht nur in einem KDoc). Kein Lesen von `AUTH_USERS_FILE` (der Name steht nur im KDoc/Vertrag; `plainTestModeFrom(String?)` bekommt den Wert übergeben), kein LOGIN7, kein ERROR 18456, keine Drosselung, keine Zeitmessung.
* **Abhängigkeiten:** keine (Lockfiles, `gradle/`, `build.gradle.kts` unverändert). PBKDF2 über das JDK-Verfahren `PBKDF2WithHmacSHA256` wie in [ADR-0011](../plan/adr/0011-pruefwertformat.md) (B1).

## 4. Mutationen (selbst ausgeführt)

Je Mutation ein frischer Klon von `bd9eac0`, genau eine Textersetzung in `src/main`, danach `docker build --target test` (`make test`-Äquivalent). „rot“ = Exit 1 mit rotem Test (kein Compile-Fehler bei allen Läufen). **41 Mutationen, 34 rot, 7 überlebend** (Mutations-Skript und Logs liegen im Scratchpad `.../vauth/mut.py`, `.../vauth/mut/`).

| # | Gegenstand | Mutation | Ergebnis (rote Tests) |
|---|---|---|---|
| M01 | Scheinvergleich fehlt | `matches = entry != null && verify(entry.secret, …)` | rot (2): „Unbekannter Benutzer führt Ableitung und Vergleich aus“, `plain`-Scheinvergleich |
| M02 | Scheinvergleich andere Iterationen | Attrappe mit 1000 statt 210 000 Iterationen | rot (2) |
| M03 | Vergleich bricht früh ab | `if (difference != 0) return …` in der Schleife | rot (4) |
| M04 | Längenunterschied ignoriert | `difference = 0` | rot (1): „Vergleich unterscheidet Längen“ |
| M05 | Schrittzahl `minOf` | `steps = minOf(...)` | rot (2) |
| M06a | Kandidat nicht überschrieben | `candidate.fill(0)` entfernt | rot (1) |
| M06b | Zeichenkopie nicht überschrieben | `chars.fill('\u0000')` entfernt | rot (1) |
| M06c | `PBEKeySpec.clearPassword()` fehlt | entfernt | **überlebt** — kein Test beobachtet es (L-1) |
| M06d | UTF-8-Puffer (`plain`-Pfad) nicht überschrieben | `buffer.array().fill(0)` entfernt | **überlebt** — kein Test beobachtet es (L-1) |
| M07 | Base64-Kanonizität | Rück-Kodierungs-Prüfung entfernt | rot (1) |
| M08 | Base64-Form-/Längenprüfung | `shaped = true` | **überlebt, äquivalent**: die Rück-Kodierung fängt dieselben Eingaben (L-5) |
| M09a | Boden | 210 000 → 200 000 | rot (1) |
| M09b | Boden `<` → `<=` | | rot (12) |
| M09c | Obergrenze `>` → `>=` | | rot (1) |
| M09d | führende Null erlaubt | Regex `[0-9]{1,8}` | rot (1) |
| M09e | 9 Stellen erlaubt | Regex `{0,8}` | rot (1) |
| M09f | Unicode-Ziffern | `\d` statt `[0-9]` | **überlebt, äquivalent**: Javas `\d` ist ohne Flag ASCII (L-5) |
| M10a | plain-Testmodus ignoriert | `if (true)` | rot (2) |
| M10b | Schalter `ignoreCase` | | rot (1) |
| M10c | Schalter `!= null` | | rot (1) |
| M11a | `AuthRequest.toString` leckt Namen | | rot (1) |
| M11b | `Principal.toString` leckt Namen | | rot (1) |
| M11c | `Accepted.toString` ruft `principal` auf | | **überlebt, äquivalent**: `Principal` ist selbst verborgen; durch M25 ersetzt |
| M11d | `PasswordChars.toString` leckt Inhalt | | rot (1) |
| M11e | `UserList.toString` leckt Schlüssel | | rot (1) |
| M25 | `Accepted.toString` leckt `principal.name` | | rot (1) |
| M12 | Duplikaterkennung entfernt | | rot (1) |
| M13a | Grenze 129 | `MAX_INPUT_UNITS = 129` | rot (1) |
| M13b | Grenze `>=` | | rot (1) |
| M13c | nur Passwortlänge geprüft | | rot (1) |
| M14 | Annahme vor Benutzerprüfung | `when`-Reihenfolge getauscht | rot (3) |
| M15 | Verfahrenskennung case-insensitiv | `scheme.lowercase()` | rot (1) |
| M16 | Hash `>= 32` | | rot (1) |
| M17 | Salz-Minimum 8 | | rot (1) |
| M18 | Zeilennummer 0-basiert | | rot (7) |
| M19 | CR erlaubt | | rot (1) |
| M20 | NFC-Normalisierung des Passworts | | rot (1) |
| M21 | leere Zeile erlaubt | | rot (1) |
| M22 | Attrappe = letzter statt erster Eintrag | | **überlebt** — „erster Eintrag“ ist ungeprüft (M-1) |
| M23 | Surrogat im Namen erlaubt | | rot (1) |
| M24 | Vergleich bei kürzerer Länge `b = a` | | **überlebt, äquivalent**: das Ergebnis trägt weiter der Längen-XOR |

Ein Test-Sensor ohne Gegenprobe gilt nicht als bestanden; hier sind die roten Läufe die Gegenprobe. Die vier äquivalenten Überlebenden (M08, M09f, M11c, M24) sind keine Lücke des Tests, sondern Redundanz im Code.

## 5. Orakel: Unabhängigkeit und Gegenrechnung

**Frage:** Ist die Zweitimplementierung `oraclePbkdf2` (`AuthTestSupport.kt`) unabhängig von `SecretKeyFactory`?

* **Bauart:** `oraclePbkdf2` rechnet RFC 8018 §5.2 (nach Prüfung des RFC-Textes: Abschnitt 5.2 „PBKDF2“) mit eigener Schleife über `javax.crypto.Mac("HmacSHA256")`; es ruft weder `SecretKeyFactory` noch `PBEKeySpec`. Das Orakel teilt mit dem JDK-Pfad die HMAC-SHA-256-Primitive des Providers (das JDK-PBKDF2 benutzt intern ebenfalls HMAC); **unabhängig sind die Iterations- und XOR-Schleife, die Blockbildung und der Passwort-/Salz-Umgang, nicht die Hash- und HMAC-Primitive.** Ein Fehler im HMAC-SHA-256 des Providers würde beide gleich treffen; dagegen stehen die RFC-Vektoren.
* **Leeres Passwort:** `SecretKeySpec` verbietet leere Schlüssel, das Orakel nimmt `ByteArray(1)`. HMAC füllt den Schlüssel mit Nullbytes auf Blockgröße; ein Nullbyte und der leere Schlüssel ergeben dieselbe Schlüsselableitung. Die Python-Gegenrechnung unten bestätigt den Fall `""`.
* **RFC-7914-Vektoren:** Die Erwartungswerte im Test („Handvektoren des Implementers“) stimmen mit dem RFC-Text überein. Der RFC wurde in diesem Lauf abgerufen (<https://www.rfc-editor.org/rfc/rfc7914.txt>, Abschnitt 11 „Test Vectors for PBKDF2 with HMAC-SHA-256“); Passwort `passwd` / Salz `salt` / c = 1: `55 ac 04 6e … c2 0d ac bc`, Passwort `Password` / Salz `NaCl` / c = 80 000: `4d dc d8 f6 … 34 ab 56` (jeweils die ersten 32 Byte). Die Abschnittsnummer §11 im Test-KDoc ist damit belegt (Review F-7 geschlossen).
* **Eigene Gegenrechnung** in einem Python-Container (lokales Image, `python -I`), zwei Wege: `hashlib.pbkdf2_hmac` und eine selbstgeschriebene Schleife über `hmac`:

  | Eingabe | Ergebnis |
  |---|---|
  | `passwd`/`salt`/c = 1, 64 Byte | `hashlib` = RFC (erste 32 Byte `55ac046e … 20dacbc`); eigene Schleife = `hashlib` |
  | `Password`/`NaCl`/c = 80 000, 64 Byte | `hashlib` = RFC (`4ddcd8f6 … 34ab56`); eigene Schleife = `hashlib` |
  | Salz `01..10`, c = 1000, Passwort `""` | `hashlib` = eigene Schleife (`89cb58d0 …`) |
  | `?` gegen `\uD800` (UTF-8 mit Ersatz) | gleicher Hash |
  | NFC gegen NFD von `ä` | verschiedene Hashes |

* **Bewertung:** Orakel unabhängig im Sinn der Iterationslogik, bestätigt gegen RFC-Text und eine dritte Implementierung (Python). Nicht unabhängig bei der HMAC-Primitive (Hinweis, kein Befund). Im Test wird der zweite Vektor (c = 80 000) nur gegen das JDK gelaufen, nicht gegen `oraclePbkdf2`; das Orakel hat aber alle Adaptertests mit c = 210 000 gegen den JDK-Pfad abgeglichen, jede Annahme im Adaptertest ist eine Übereinstimmung beider.

## 6. Laufzeit von `make test`

Methode: `docker build --no-cache-filter test --target test --progress=plain .` in je einem Klon von `7c9624c` (vorher) und `bd9eac0` (nachher), abwechselnd, 3 Läufe je Seite; Zeiten aus dem Gradle-Protokoll (`BUILD SUCCESSFUL in …`, Zeitstempel des Beginns und des letzten Tests der Task `:test`). Ein Host (20 logische CPUs), kein CPU-Limit, keine Leistungszusage.

| | Lauf 1 | Lauf 2 | Lauf 3 |
|---|---|---|---|
| Gradle gesamt, vorher (143 Tests) | 43 s | 33 s | 32 s |
| Gradle gesamt, nachher (171 Tests) | 31 s | 35 s | 47 s |
| Task `:test` (erster bis letzter Test), vorher | 2,1 s | 1,7 s | 1,9 s |
| Task `:test`, nachher | 2,8 s | 3,2 s | 2,8 s |

* **Test-Ausführung:** Zuwachs etwa **+0,9 bis +1,1 s** (Mittel 1,9 s → 2,9 s) für 28 Tests; die Auth-Tests liegen in einem Fenster von etwa 0,9 s (44,53 s bis 45,43 s im dritten Lauf).
* **Gesamtdauer des Docker-Builds** ist von dieser Messung nicht zu trennen: die Streuung der Läufe (32 bis 47 s) liegt über dem Zuwachs. Mehr Kompilierzeit durch +1190 Zeilen ist darin enthalten, aber nicht getrennt ausgewiesen.
* Aus dem Cache dauert `make test` rund 1 bis 2 s. Der Implementer hat diesen Zuwachs nicht gemessen (Auftrag); die Zahl steht hiermit im Bericht.

## 7. `@spec`-Tags

Alle 28 Tests tragen im KDoc über der Testfunktion einen Block-Tag `@spec SPEC-051`, einer zusätzlich `@spec SPEC-012` („Falsches Passwort und unbekannter Benutzer werden abgelehnt“). `make spec-abdeckung` im Klon wertet sie aus: [SPEC-051](../../spec/spezifikation.md#6-externe-verträge) 28 Tests, `SPEC-012` 1 Test, beide „nachgewiesen“. Hilfsfunktionen (`AuthTestSupport.kt`) sind keine Tests und tragen keinen Tag.

## 8. Aussagen über Messung hinaus (`AGENTS.md` §3.8)

Geprüft: KDoc in `AuthAdapter.kt`, `PasswordChars.kt`, `UserList.kt`, `UserListAuthAdapter.kt`, `StoredSecret.kt`, Test-KDoc und der neue Teil des Vertrags. Die Festlegungen tragen „Festlegung ohne Messung“; Hash-/Salzlänge, Kommentare, Zeilenenden, Iterationsboden im Testmodus, Ziffernlänge sind als Setzungen markiert; kein Text behauptet „zeitgleich“, „konstante Zeit“ als Eigenschaft oder „nicht unterscheidbar“. RFC-Abschnittsnummern (§11 von RFC 7914, §5.2 von RFC 8018) sind in diesem Lauf an den RFC-Texten belegt. Rest: L-1, L-3.

## 9. Findings

**HIGH:** keine.

**MEDIUM**

* **M-1 · Der Scheinvergleich trägt „gleiche Struktur“ nur für eine Liste mit einheitlichen Parametern; „erster Eintrag“ ist ungeprüft.** Beleg: M22 (Attrappe = letzter Eintrag) überlebt, weil alle `pbkdf2`-Einträge der Testliste dieselbe Form haben und der bekannte Benutzer selbst der erste Eintrag ist. Aus dem Code folgt (nicht gefahren): Ist der erste Eintrag `plain` oder hat er andere Iterationen als der Eintrag des bekannten Benutzers, läuft bei unbekannter Kennung eine andere Folge von Rechenschritten (z. B. keine Ableitung) als bei bekannter Kennung mit falschem Passwort; der Test „Scheinvergleich gegen einen plain-Eintrag“ zeigt das für eine Liste aus einem `plain`-Eintrag. Eine Zeitaussage macht dieser Befund nicht. Der Vertrag nennt die Grenze nicht. Entspricht Review F-1/F-2, im Baum unverändert; M22 liefert den Mutationsbeleg. Bezug: [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md) (§Offen).
* **M-2 · Die fünf offenen Punkte aus [ADR-0011](../plan/adr/0011-pruefwertformat.md) sind vom Implementer gesetzt, nicht entschieden und nicht offen geführt (DoD-Punkt 3).** Siehe Abschnitt 2; Review F-3, im Baum unverändert. Die Setzungen sind ehrlich gekennzeichnet (KDoc, Vertrag), laufen aber bis zur Entscheidung des Nutzers als Startabbruch (CRLF, Hash ≠ 32 Byte, Salz < 16 Byte). Closure-blockierend bis zur Entscheidung oder bis jeder Punkt in der Closure als offen mit Grund steht.

**LOW**

* **L-1 · Die Überschreib-Aussage trägt weiter als die Tests.** Der Vertrag sagt: „der Adapter überschreibt seine eigenen Kopien“. Beobachtet werden nur das an die Ableitung übergebene Zeichenfeld, die abgeleiteten Bytes und der `plain`-Kandidat (M06a, M06b rot). `PBEKeySpec.clearPassword()` (M06c) und die Nullung des UTF-8-Puffers im `plain`-Pfad (M06d) haben keinen Test; der vom JDK gehaltene Schlüsselinhalt des `SecretKey`-Objekts aus `generateSecret` wird nicht überschrieben (`encoded` ist eine Kopie, die der Adapter nullt; das Schlüsselobjekt selbst bleibt der Müllsammlung überlassen; aus dem Code gelesen, nicht gemessen). Der Vorbehalt „nicht jede Kopie der JVM“ steht im Vertrag; die Aussage „seine eigenen“ ist breiter als der Beleg (`AGENTS.md` §3.8).
* **L-2 · Tippfehler „dokumiert“** in `AuthPrimitivesTest.kt` (Review F-6, im Baum unverändert).
* **L-3 · Vertrag widerspricht sich (Review F-4, im Baum unverändert).** `contracts/auth-adapter.md` §Pflichten: „Weder gebaut noch gemessen; ein Test muss es belegen“; der Abschnitt „Kotlin-Signatur“ beschreibt die Bauart als gebaut und strukturell belegt.
* **L-4 · `docs/plan/spec-abdeckung.md` ist nach dem Slice nicht regeneriert.** Im Baum steht für SPEC-051 und SPEC-012 „OHNE NACHWEIS“; `make spec-abdeckung` ergibt im Klon „nachgewiesen“ (28 bzw. 1 Test). Das Dokument ist ein erzeugter Stand (kein Gate); der Stand im Repo gibt dem Leser das Gegenteil des Belegs an.
* **L-5 · Drei äquivalente Überlebende deuten auf redundanten Code:** `shaped`-Prüfung in `decodeBase64` (M08) neben der Rück-Kodierung; die Regex ist ASCII-geschützt auch ohne ausdrückliches `[0-9]`-Muster (M09f, nur Beobachtung). Kein Fehler.
* **L-6 · Die Testzahl im Review stimmt nicht.** Der Review nennt 26 Tests in `config/auth`; gezählt und gelaufen sind 28 (6 + 2 + 10 + 10, Zuwachs 143 → 171). Die Aussagen des Reviews zu den Tests bleiben davon unberührt.

**INFO**

* **I-1** Review F-7 (RFC-Abschnittsnummern ohne Beleg) ist in diesem Lauf geschlossen: beide Nummern sind am RFC-Text belegt (Abschnitt 5). Eine Quellenangabe im Repo (URL) fehlt weiterhin.
* **I-2** `make gates` kam im Klon großteils aus dem Cache; belegt wurde `detekt` zusätzlich mit `--no-cache-filter` und `make test` mit `--no-cache-filter test` (Abschnitte 1 und 6).
* **I-3** Im Arbeitsbaum des Repos liegt ein untracked `.claude/worktrees/` (drei Unterverzeichnisse), das dieser Lauf nicht angelegt hat.

## 10. Nicht gefahren

Zeitmessung des Vergleichs oder der Ableitung (ausdrücklich außerhalb: keine Zeitaussage); ein Test mit gemischter Liste (erster Eintrag `plain`, danach `pbkdf2`) als Belegversuch zu M-1 (Befund steht aus dem Code und aus M22); ein Speicherabzug zur Prüfung, ob das JDK-Schlüsselobjekt Reste des Passworts hält; Dateirechte und Lesen der Datei (liegen bei einem anderen Slice).

## 11. Urteil

Liefer-Punkte 1 und 2: **bestätigt** (mit M-1 als Einschränkung beim Scheinvergleich). Liefer-Punkt 3: **bestätigt** bis auf die fünf Setzungen, die weder entschieden noch offen geführt sind (M-2). Nachweis-Pflichten: Tests, `@spec`, Gates und Review **bestätigt**; Closure-Notiz, Register, Risiko-Ausgänge und Paarungen **offen**. Kein HIGH. Der Slice darf nicht nach `done/`, bis M-1 und M-2 einen Ausgang haben und die Closure-Pflichten erfüllt sind.
