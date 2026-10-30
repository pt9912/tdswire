# Slice slice-adr-pruefwertformat: Prüfwertformat der festen Benutzerliste und Konfigurationsweg entscheiden

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-handshake-login

**Bezug:** [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) · SPEC-010 · [TDS-FA-02.a](../../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung) · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Das Prüfwertformat der festen Benutzerliste und der Konfigurationsweg der Liste sind vom Nutzer anhand gemessener Optionen entschieden und stehen als Fortschreibung von [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) und `contracts/auth-adapter.md` oder als neue ADR fest; die offene Frage aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen lautet: Prüfwertformat und Konfigurationsweg.

**Richtung des Nutzers (Eingabe, keine Festlegung):** Das Format ist konfigurierbar. Jeder Eintrag trägt vor dem Prüfwert eine Verfahrenskennung. Beispiele: `{plain}…` ist nur bei ausdrücklich eingeschaltetem Testmodus zulässig und führt sonst zum Startabbruch; `{pbkdf2-sha256}Salz$Iterationen$Hash` nutzt das JDK-Verfahren `PBKDF2WithHmacSHA256` ohne Fremdbibliothek; spätere Verfahren kommen als zusätzliche Kennung hinzu. Der Slice prüft diese Richtung mit Spikes und legt dem Nutzer Optionen vor (§3); er entscheidet nicht selbst, und kein Detail der Beispiele gilt vor der Wahl als gesetzt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode (Parser, Ableitung, Vergleich, Laden der Datei) — der Slice entscheidet und baut nicht; der Code entsteht in `slice-auth-adapter-benutzerliste` (Prüfwert, Verfahren, Validierung, Vergleich) und `slice-bootstrap-konfiguration` (Laden, Konfigurationsmodell).
- Zeitgleichheit des Vergleichs und Drosselung wiederholter Versuche — `slice-qa-sicherheit` ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen); die Spikes messen die Dauer einer Ableitung, nicht den Zeitunterschied zwischen unbekannter Kennung und falschem Passwort.
- Der Authentifizierungsmodus und die Adapter-Schnittstelle selbst — von [ADR-0009](../../adr/0009-authentifizierungsmodus.md) entschieden und `Accepted`; dieser Slice schreibt sie nicht um (Hard Rule `AGENTS.md` §3.5).
- Betrieb der Prüfwert-Datei (Dateirechte, Übergabe in den Container, Geheimnisverwaltung des Betreibers) — Betriebsfrage; der Slice misst nur, wo ein Wert je Weg sichtbar wird, und nennt die Rechte als Anforderung an den Betreiber, falls der Nutzer die Option wählt.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Spikes durchgeführt (S1 bis S3 aus §3); Messwerte, Optionen und Befund stehen als Tabelle in diesem Slice und in der Entscheidung. Der Spike-Code liegt im Scratchpad, ist temporär, nie im Repo und nach der Entscheidung gelöscht.
- [ ] Der Nutzer hat gewählt: Kennungsschema, Verfahrensumfang, Untergrenze der Iterationszahl, Konfigurationsweg und -format, Testmodus-Schalter (Optionen A bis E in §3). Die Wahl ist festgehalten als Fortschreibung von [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) und `contracts/auth-adapter.md` **oder** als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted`, Index und Geschichte nachgezogen), mit der Begründung, warum [ADR-0009](../../adr/0009-authentifizierungsmodus.md) (`Accepted`) nicht überschrieben wird (§3).
- [ ] Anschlussstellen nachgezogen: Spec-Stellen aus dem `Schärft:`-Feld bzw. SPEC-051; die Slices `slice-auth-adapter-benutzerliste` und `slice-bootstrap-konfiguration` (Start-Trigger, Risiken, Aufteilung der Verantwortung für Prüfwert und Konfigurationsmodell) und `slice-qa-sicherheit` (Prüfwertformat-Punkt, nur soweit die Entscheidung ihn auflöst).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Spikes im Scratchpad (Dockerfile je Spike, ohne Mount, Basis-Images per Digest) | neu, danach löschen | nie im Repo; Ergebnisse gehen als Tabelle in diesen Slice und in die Entscheidung |
| `spec/spezifikation.md` (SPEC-051, ggf. Konfigurationsschlüssel in §3, Historie) | update | Fortschreibung, falls der Nutzer sie statt einer ADR wählt, sonst nur Stellen aus dem `Schärft:`-Feld |
| `contracts/auth-adapter.md` (§Einstiegsimplementierung: „Konfigurationsweg und Format sind noch festzulegen“, §Gemessen) | update | festgelegtes Format und Weg; Entwurfs-Hinweise bleiben, wo nichts gemessen ist |
| `docs/plan/adr/<nächste Nummer>-….md` und `docs/plan/adr/README.md` | neu / update, nur bei ADR-Weg | Entscheidung mit Alternativen, Fitness Function, Geschichte; Index |
| `docs/plan/planning/next/slice-auth-adapter-benutzerliste.md`, `slice-bootstrap-konfiguration.md` | update | Verweis auf die Entscheidung, Aufteilung (siehe unten) |
| `docs/plan/planning/open/slice-qa-sicherheit.md` | update, nur falls nötig | Prüfwertformat-Punkt in §2; Begründung in §7 dieses Slice |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

### Spikes (Scratchpad, Docker-only, nichts mounten)

Jeder Spike ist ein Dockerfile mit einem JDK-Basis-Image per Digest; Eingaben werden per `COPY` ins Image gelegt, es gibt keinen Mount und keine Installation auf dem Host (`AGENTS.md` §3.1). Messwerte gelten für das gebaute Image und die Hardware des Spike-Laufs; die Hardware wird in der Messtabelle genannt. Ein Spike belegt, was er misst, und nicht mehr.

- **S1 — Ableitung mit dem JDK.** Welche `SecretKeyFactory`-Algorithmen das JDK-Image anbietet (Liste; ob dort außer PBKDF2-Varianten weitere Verfahren stehen, ist Messgegenstand, nicht Annahme). `PBKDF2WithHmacSHA256` mit Iterationszahlen aus einer Staffel (zum Beispiel 10 000, 100 000, 600 000, 1 000 000; die Staffel legt der Spike fest und nennt sie): Dauer je Ableitung, wiederholt und mit Aufwärmlauf getrennt ausgewiesen, im Verhältnis zu `LOGIN_TIMEOUT` (15 s, SPEC-008). Eingaben an den Rändern der Adapter-Eingabe: leeres Passwort, 128 Einheiten, Surrogatpaar, Umlaut; welche Byte-Kodierung des Passworts vor der Ableitung (UTF-8, UTF-16) das JDK trägt und wie sie in den Prüfwert eingeht. Ausgabelänge und Kodierung von Salz und Hash (Base64 oder Hex) mit den Folgen für die Zeichen im Prüfwert (Trennzeichen `$`). Keine Zeitgleichheitsaussage.
- **S2 — Kennungsschema und Parser.** Die Kennungsschemata aus Option A werden mit Mitteln des JDK und der Kotlin-Standardbibliothek geparst: gültige Einträge, fehlende oder unbekannte Kennung, leerer Rest, zu wenige oder zu viele Felder, nicht numerische oder negative Iterationszahl, Iterationszahl unter der Untergrenze und über einer Obergrenze, Groß-/Kleinschreibung der Kennung. Je Fall: ergibt der Parser einen Fehlerwert mit Art und Listenplatz, und enthält er weder Prüfwert noch Passwort? Ein Format-Kandidat mit Parser-Bibliothek (Option D) wird nur gemessen, soweit sie sich per Lock und Prüfsumme fixieren lässt (`make deps-lock` braucht Netz, im Spike also nur als Befund „fixierbar ja/nein“), und trägt die Kosten einer neuen Abhängigkeit in die Optionstabelle.
- **S3 — Konfigurationsweg.** Für die Wege aus Option D: Kennungen am Rand der Eingabe (0 bis 128 Einheiten, leer, Doppelpunkt, Gleichheitszeichen, Leerzeichen, Unicode) werden in das Dateiformat geschrieben und gelesen; welche Zeichen das Format maskieren muss. Sichtbarkeit der Prüfwerte je Weg im Spike-Container (Datei im Image, Umgebungsvariable): ob `docker inspect` und der Prozess-Umgebungsblock des Containers den Wert zeigen. Ergebnis ist eine Matrix „Weg × wo der Wert lesbar ist“, keine Aussage über Dateirechte im Betrieb. Wie `plain` ohne Testmodus abgewiesen wird: Startabbruch vor dem Binden des Ports, Meldung ohne Prüfwert.

### Messergebnisse der Spikes S1 bis S3

**Messobjekt und Umgebung.** Drei Spike-Images mit je eigenem Dockerfile, ohne Mount, Basis-Image `gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06` (das des Repo-Dockerfiles), JDK Eclipse Adoptium 21.0.12.1, Kotlin 2.4.10 (Version aus `gradle/libs.versions.toml`, Spike-Build mit Netz im Image-Build, ohne Lock und Prüfsumme). Host: Intel Core i9-13900H, 20 logische CPUs, 31 GiB RAM, Docker ohne CPU-Limit; wo angegeben `--cpus=1`. Der Prozess im Spike-Image läuft als `root` (Vorgabe des Gradle-Images), nicht als Laufzeit-Image des Produkts. **Alle Werte gelten nur für dieses Image und diese Hardware**; sie sind keine Leistungszusage ([TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) ist nicht berührt) und keine Aussage über den Produktivbetrieb. Was als „abgeleitet“ steht, ist gerechnet oder gefolgert und **nicht gemessen**. Der Spike-Code ist im Scratchpad und wird nach der Entscheidung gelöscht.

**S1 — Ableitung mit dem JDK** (`PBKDF2WithHmacSHA256`, 256 Bit Ausgabe, Salz 16 Byte, Passwort mit 28 Zeichen; „warm“ = nach 20 verworfenen Ableitungen mit c = 100 000 im selben Prozess)

| Nr. | Messung | Ergebnis |
|---|---|---|
| M1-1 | `SecretKeyFactory`-Algorithmen im Image | 30, alle Provider `SunJCE`: `DES`, `DESede`, 14 × `PBEWithHmac…AndAES_*`, 7 ältere `PBEWith…` (MD5/SHA1 mit DES, TripleDES, RC2, RC4) und 7 × `PBKDF2WithHmac` mit `SHA1`, `SHA224`, `SHA256`, `SHA384`, `SHA512`, `SHA512/224`, `SHA512/256` |
| M1-2 | `scrypt`, `SCRYPT`, `Argon2`, `argon2id`, `bcrypt`, `BCrypt` als `SecretKeyFactory` | alle `NoSuchAlgorithmException`; die Liste der Dienste vom Typ `KDF` ist leer |
| M1-3 | Bekannte Antworten (PBKDF2-HMAC-SHA256: `passwd`/`salt`/c = 1/64 Byte; `Password`/`NaCl`/c = 80 000/64 Byte) | beide stimmen mit den Erwartungswerten überein |
| M1-4 | Dauer je Ableitung, warm, Median (min–max) in ms; Anzahl Läufe 15 / 5 / 3 | c = 10 000: 3,1 (3,0–3,4) · 100 000: 23,9 (20,4–28,1) · 210 000: 54,4 (44,6–59,8) · 600 000: 124,5 (122,0–150,3) · 1 000 000: 206,1 (200,0–256,8) · 10 000 000: 2 086,6 (2 017,5–2 345,9) · 30 000 000: 6 387,0 (6 240,5–6 549,6) |
| M1-5 | Dasselbe mit `--cpus=1` | c = 600 000: 120,6 (118,1–137,5) · 1 000 000: 199,9 (197,4–226,6) · 10 000 000: 2 169,6 (2 053,8–2 397,2) |
| M1-6 | Erste Ableitung im frischen JVM (kalt, je ein Prozess, ein Lauf) | c = 10 000: 118,5 ms · 100 000: 158,4 · 600 000: 325,3 · 1 000 000: 408,6 · 10 000 000: 2 616,8 |
| M1-7 | Gleichzeitige Ableitungen, c = 600 000, je Thread 3 Ableitungen; Median / Maximum je Ableitung in ms | 20 CPUs: 1 Thread 128,8 / 141,6 · 8 Threads 143,5 / 158,0 · 16 Threads 180,7 / 264,4 · 32 Threads 311,1 / 680,0 · 64 Threads 700,4 / 1 705,4. `--cpus=1`: 1 Thread 119,5 / 122,8 · 4 Threads 588,5 / 735,5 · 16 Threads 2 856,5 / 3 796,6 · 32 Threads 5 400,9 / 10 001,8 · 64 Threads 10 478,8 / 23 598,0 |
| M1-8 | Andere HMAC-Variante, c = 600 000, warm, Median (15 Läufe, eigener Prozess; derselbe Wert für `SHA256` lag in M1-4 bei 124,5) | `SHA1` 270,8 · `SHA256` 150,7 · `SHA512` 563,1 ms. Die Läufe von `SHA256` bei c = 600 000 streuen zwischen 120,6 und 150,7 ms Median über Prozesse |
| M1-9 | Kodierung des Passworts vor der Ableitung: `char[]` gegen eine eigene PBKDF2-Berechnung über `Mac`, die UTF-8-, UTF-16LE- und UTF-16BE-Bytes nimmt (11 Fälle: leer, ASCII, Umlaut NFC und NFD, Surrogatpaar, 128 Einheiten ASCII, 128 Einheiten als 64 Paare = 256 UTF-8-Byte, alleinstehendes Hochsurrogat, `?`, NUL im Passwort, Leerzeichen am Rand) | In allen 11 Fällen gleich der UTF-8-Berechnung. UTF-16LE stimmt nur beim leeren Passwort und bei `?` überein (abgeleitet, nicht gemessen: HMAC füllt den Schlüssel mit Nullbytes auf, ein nachgestelltes Nullbyte ändert ihn nicht); das belegt keine UTF-16-Kodierung. UTF-16BE stimmt nur beim leeren Passwort überein |
| M1-10 | Randfälle der Eingabe | Leeres Passwort: das JDK leitet ab, keine Ausnahme. Das alleinstehende Hochsurrogat (`U+D800`) ergibt **denselben Hash wie `?`**. NFC (`U+00FC`) und NFD (`U+0075 U+0308`) ergeben verschiedene Hashes. 128 Einheiten ASCII und 64 Surrogatpaare werden abgeleitet |
| M1-11 | Parameter der `PBEKeySpec` | Salz leer: `IllegalArgumentException`; Iterationen 0 oder −1: `IllegalArgumentException`; Länge 0 Bit: `IllegalArgumentException`; Länge 100 Bit liefert 12 Byte; Länge 512 Bit liefert 64 Byte; Salz mit 1 Byte wird abgeleitet. Die `PBEKeySpec` hält eine Kopie des Passwort-`char[]` (Überschreiben des Quellfelds ändert die Spec nicht); nach `clearPassword()` wirft `getPassword()` eine `IllegalStateException`. Was das JDK intern an Zwischenpuffern hält, ist nicht untersucht |
| M1-12 | Kodierung von Salz (16 Byte) und Hash (32 Byte) | Base64: 24 und 44 Zeichen mit `=`, ohne Padding 22 und 43; Hex: 32 und 64. Zeichenvorrat über 100 000 Zufalls-Hashes: Base64 `A–Z a–z 0–9 + / =`, URL-Variante `- _` statt `+ /`, Hex `0–9 a–f`; in keinem kam `$`, `{`, `}` oder `:` vor, `=` nur in Base64. `Base64.getDecoder()` akzeptiert `AAAA`, `AAA` (ohne Padding) und `AA==`, lehnt ein Leerzeichen ab |

**S2 — Kennungsschema und Parser.** Parser in Kotlin mit JDK und Standardbibliothek, 87 Zeilen (`Parser.kt`) einschließlich Wertklassen; Grenzen im Spike nur zur Messung gesetzt, keine Festlegung: Untergrenze 100 000, Obergrenze 10 000 000, Hash 32 Byte, Salz nicht leer. Der Fehlerwert trägt nur Art und Listenplatz.

| Nr. | Messung | Ergebnis |
|---|---|---|
| M2-1 | A1 `{kennung}Rest`, 44 Fälle | Gültig: `pbkdf2-sha256` mit Salz, Iterationen, Hash; `plain` bei eingeschaltetem Testmodus (auch mit `}` und `$` im Wert). Fehlerarten: `plain` bei ausgeschaltetem Testmodus; `plain` mit leerem Wert; ohne Kennung (auch mit führendem Leerzeichen); leere Kennung `{}`; nicht geschlossene Kennung; unbekannte Kennung (auch `{{plain}}`, `{ plain}`, `{PBKDF2-SHA256}`); leerer Rest; 1, 2 oder 4 Felder statt 3; Iterationen nicht numerisch, unter der Untergrenze oder über der Obergrenze; Salz mit ungültigem Zeichen, im URL-Alphabet oder mit Leerzeichen; leeres Salz; Hash mit ungültigem Zeichen, 31 Byte oder leer. Jeder Fall ergibt genau eine Art mit Listenplatz |
| M2-2 | Fehlerwerte frei von Prüfwert und Passwort | In allen 44 A1-Fällen enthält die Textform des Fehlerwerts den eingebetteten Prüfwert (`ZQXJKV…`) nicht. Die Wertklassen des Spikes (`Plain`, `Pbkdf2`) drucken ihn ebenfalls nicht, weil sie ein eigenes `toString` haben; eine Kotlin-`data class` ohne eigenes `toString` druckt den Wert (gemessen) |
| M2-3 | Meldungen der Aufrufe unter dem Parser | `Integer.parseInt` und `String.toInt` nennen die Eingabe in der Meldung (`For input string: "…"`); die Base64-Decoder-Meldungen (`Illegal base64 character 21`, `…wrong 4-byte ending unit`) und die der `PBEKeySpec` nennen sie nicht. Der Parser im Spike leitet keine Meldung eines Aufrufs weiter |
| M2-4 | Iterationszahl: `toIntOrNull` gegen `Regex("[0-9]{1,10}")` | `toIntOrNull` und `Integer.parseInt` nehmen `+600000`, `−5`, arabisch-indische (`U+0660…`) und vollbreite Ziffern (`U+FF10…`) als Zahl an (ergibt 600 000 bzw. −5); die Regex nimmt nur ASCII-Ziffern an. Führende Nullen (`0600000`) ergeben 600 000. Elf oder mehr Ziffern scheitern an der Regex (Art „nicht numerisch“) |
| M2-5 | Grenzen der Iterationen | 0 und 99 999: unter der Untergrenze; 100 000 und 10 000 000: gültig; 10 000 001 und 2 147 483 647: über der Obergrenze |
| M2-6 | Groß-/Kleinschreibung | Gleichheit mit der Kleinschreibung weist `{PBKDF2-SHA256}` ab (unbekannte Kennung); ein Vergleich nach `lowercase()` nimmt es an |
| M2-7 | Salz-Dekodierung | `Base64.getDecoder()` nimmt Salz ohne Padding (22 Zeichen) an; das URL-Alphabet (`-`, `_`) und Leerzeichen lehnt es ab |
| M2-8 | A2 (Verfahren als eigenes Feld), 6 Fälle | Gültig; Feld fehlt, leer, unbekannt, `plain` ohne Testmodus: je eine eigene Art. Steht die Kennung zusätzlich im Wert (`{pbkdf2-sha256}Salz…`), lautet die Art „Salz ungültig“ |
| M2-9 | A3 (`$kennung$Rest`), 7 Fälle | Gültig, auch `plain` mit `$` im Wert. Ohne Kennung, nicht abgeschlossen, leere Kennung, 4 Felder im Rest: je eine Art. Ein Eintrag `$abc$…` ergibt „unbekannte Kennung“ |
| M2-10 | Parser-Bibliothek (F3) | Nicht gemessen: keine Bibliothek ausprobiert, „fixierbar ja/nein“ nicht ermittelt (kein `make deps-lock` im Spike). `gradle.lockfile` des Repos wurde nicht auf Parser-Bibliotheken untersucht |

**S3 — Konfigurationsweg.** Gegenstand: F1 `java.util.Properties`, F2 eigenes Zeilenformat (`Kennung<TAB>Prüfwert`, eine Zeile je Eintrag, Aufteilung am ersten TAB, ohne Beschneiden), UTF-8; je 23 Kennungen am Rand der Eingabe (0 bis 128 Einheiten, Leerzeichen, `:`, `=`, `#`, `!`, `\`, Umlaut, Surrogatpaar, alleinstehendes Hochsurrogat, TAB, LF, CR, `U+2028`, `{plain}x`, `$`, NUL).

| Nr. | Messung | Ergebnis |
|---|---|---|
| M3-1 | Schreiben und Lesen durch das Format selbst (F1 `store` und `load` mit `Writer`/`Reader` in UTF-8) | 23 von 23 Kennungen kommen unverändert zurück |
| M3-2 | Betreiber schreibt die Zeile von Hand ohne Maskierung (`Kennung=Wert`) | F1: 11 von 23 Kennungen werden falsch gelesen: Leerzeichen (innen, vorn, hinten), `:`, `=`, `#` und `!` am Anfang, `\`, TAB, LF, CR. Richtig gelesen: leer, `a`, 128 Einheiten, Umlaut, Surrogatpaar, `U+2028`, `{plain}x`, `$`, `#` innen, NUL. F2: 4 von 23 gehen nicht (alleinstehendes Hochsurrogat wird zu `?`, TAB, LF, CR); Leerzeichen, `:`, `=`, `#`, `\` kommen richtig zurück |
| M3-3 | F1 Eigenschaften der Tabelle | Doppelte Kennung: kein Fehler, der letzte Wert gewinnt (2 Einträge, `alice=C`). Reihenfolge: bei 12 Einträgen `user01…user12` in Dateireihenfolge liefert der Durchlauf `user11, user10, user04, …`, also nicht die Dateireihenfolge. Mit einer `LinkedHashMap` und eigener Zeilenlesung erkennt der Spike das Duplikat (1 erkannt). Die Zeilennummer eines Eintrags liefert `Properties.load` nicht |
| M3-4 | F1 Zeichensatz und Wertseite | `load(InputStream)` liest ISO-8859-1: ein UTF-8-`ü` wird zu `U+00C3 U+00BC`; `load(Reader)` in UTF-8 liest es richtig; `store(OutputStream)` schreibt `ü`. Wertseite roh geschrieben: `$`, `}`, `#`, `=` (Padding) und Leerzeichen am Ende bleiben erhalten; ein `\` im Wert wird verschluckt (`pa\ss` → `pass`), `\n` wird zum Zeilenumbruch, `\u00zz` ergibt `IllegalArgumentException: Malformed \uxxxx encoding.` (Meldung ohne den Wert), ein `\` am Zeilenende hängt die nächste Zeile an (`passz=1`) |
| M3-5 | Sichtbarkeit des Werts `CANARY…` je Weg, Container als `root`, `docker`-Befehle vom Host (Datei im Image, Pfad per Umgebungsvariable; Wert in der Umgebung) | **Datei im Image:** `docker inspect` 0 Treffer; `docker logs` 0; `/proc/1/environ` und `/proc/1/cmdline` 0; `docker exec cat` Datei: lesbar; `docker cp` aus dem laufenden Container: lesbar; `docker run --entrypoint cat` auf das Image: lesbar; `docker history`: die Zeile `COPY data /spike/data` ohne Inhalt. **Wert in der Umgebung:** `docker inspect` (`Config.Env`) **zeigt den Wert**; `/proc/1/environ` im Container **zeigt ihn**; `docker logs` 0; `/proc/1/cmdline` 0. `docker top` lief nicht (Fehler des `ps` im Image), nicht ermittelt. `docker save` ergab 0 Treffer, aber die Layer sind verschachtelte Archive, die der Befehl nicht entpackt: **nicht ermittelt** |
| M3-6 | Start mit `plain` ohne Testmodus (Spike-Start: laden, validieren, erst dann einen Port binden; Testmodus = Umgebungsvariable `TDS_TESTMODE=true`) | Datei mit `plain`, Testmodus aus: Exit 1, Log `Testmodus=aus`, stderr `Konfigurationsfehler: Eintrag 1: PLAIN_NOT_ALLOWED` und `Start abgebrochen: 1 Fehler`, die Zeile „Port gebunden“ erscheint **nicht**. Mit `TDS_TESTMODE=true`: Exit 0, Log `Testmodus=an (plain zugelassen)`, Port gebunden. Datei ohne `plain`: Exit 0. `TDS_TESTMODE=TRUE`: Exit 1 (der Spike liest genau `true`). `plain` über `TDS_USERS` in der Umgebung ohne Testmodus: Exit 1. Keine Konfiguration: Exit 2. In keinem Lauf enthalten stdout und stderr den Prüfwert. Der Listenplatz im Spike ist die Position nach Sortierung der Schlüssel, nicht die Dateizeile (siehe M3-3) |
| M3-7 | Größe von Umgebungsvariablen (`docker run -e`) | Eine Variable mit 60 000, 100 000, 131 000 und 131 060 Zeichen kommt im Container an; bei 131 071 scheiterte schon der Aufruf von `docker` auf dem Host („Argumentliste zu lang“), nicht gemessen im Container; 5 Variablen mit je 100 000 Zeichen (500 000 gesamt) und 15 Variablen (1 500 000 gesamt) kamen an. `docker run --env-file` mit einer Zeile über 64 KiB scheiterte im Docker-Client (`bufio.Scanner: token too long`) |

**Nicht gemessen:** Zeitunterschied zwischen unbekannter Kennung und falschem Passwort ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen, `slice-qa-sicherheit`); Wirkung einer blockierenden Ableitung auf den Coroutinen-Transport ([ADR-0003](../../adr/0003-transportframework.md)); Dateirechte und Übergabe im Betrieb; mehrzeilige Werte in Umgebungsvariablen; `ENV` im Dockerfile; Parser-Bibliotheken; Läufe auf anderer Hardware oder anderem Image; Last durch gleichzeitige Anmeldungen im Produkt.

**Abgeleitet, nicht gemessen:** Bei linearem Verlauf von M1-4 (30 000 000 Iterationen in rund 6,4 s) würde eine Ableitung von 15 s (`LOGIN_TIMEOUT`, SPEC-008) bei rund 70 000 000 Iterationen liegen; die Frist umfasst zusätzlich PRELOGIN und TLS-Handschlag, deren Dauer hier nicht gemessen ist. Die Gleichzeitigkeit in M1-7 mit `--cpus=1` zeigt, dass bei c = 600 000 die Dauer je Ableitung mit der Zahl gleichzeitiger Ableitungen wächst (64 Threads: Maximum 23,6 s, über 15 s); das gilt für diesen Spike-Prozess, nicht für den Transport des Produkts.

### Optionen für die Nutzerentscheidung

Die Optionen sind Vorschläge zur Wahl; der Nutzer wählt je Gruppe, und eine Gruppe darf auch eine andere als die genannten Antworten haben. Der Slice legt sie mit den Messwerten aus S1 bis S3 vor.

| Gruppe | Frage | Optionen |
|---|---|---|
| A Kennungsschema | Wie nennt der Eintrag sein Verfahren? | A1 Präfix `{kennung}` vor dem Prüfwert (Richtung des Nutzers); A2 eigenes Feld im Eintrag (Schlüssel `verfahren`); A3 `$kennung$…`-Stil. Gemeinsam zu klären: Eintrag ohne Kennung ist Startabbruch oder hat ein Standardverfahren; eine unbekannte Kennung ist Startabbruch. |
| B Verfahrensumfang | Welche Verfahren in der ersten Version? | B1 `plain` (nur Testmodus) und `pbkdf2-sha256`; B2 nur `pbkdf2-sha256` (Testbetrieb mit einem Prüfwert zu bekanntem Passwort); B3 B1 oder B2 plus ein weiteres JDK-Verfahren, falls S1 eines zeigt. Gemeinsam zu klären: wie spätere Verfahren ergänzt werden (eine Kennung je Verfahren, im Code als geschlossene Menge) und ob ein Verfahren ohne Fremdbibliothek Bedingung bleibt. |
| C Iterationszahl | Untergrenze und Obergrenze? | C1 feste Untergrenze aus S1, nicht konfigurierbar; C2 Untergrenze mit Standardwert, vom Betreiber nicht unter einen harten Boden senkbar; C3 keine Untergrenze (nur Testmodus). Die Obergrenze schützt vor einem Eintrag, dessen Ableitung die Anmeldung über `LOGIN_TIMEOUT` hinaus blockiert; sie wird aus S1 begründet. Der Scheinvergleich bei unbekannter Kennung rechnet mit denselben Parametern; wie, regelt der Auth-Slice. |
| D Konfigurationsweg und -format | Woher kommt die Liste, in welcher Form? | Weg: D1 Datei, Pfad per Umgebungsvariable oder Argument; D2 Umgebungsvariablen; D3 eigene Datei nur für die Benutzerliste, getrennt von der übrigen Konfiguration. Format: F1 `java.util.Properties` (JDK); F2 eigenes Zeilenformat; F3 ein Format mit Parser-Bibliothek (neue Abhängigkeit mit Lock und Prüfsumme). Gemeinsam zu klären: Zeichenkodierung der Datei, Maskierung von Sonderzeichen in der Kennung (S3). |
| E Testmodus-Schalter | Was schaltet `plain` frei? | E1 derselbe ausdrückliche Testmodus wie die Abschwächung der Verschlüsselung (SPEC-010); E2 ein eigener Schalter nur für `plain`. In beiden Fällen nennt der Start-Log den Modus (analog SPEC-010), und `plain` ohne Schalter bricht den Start ab. |

### Pro und Contra der Optionen aus der Messung

Jede Zeile nennt den Messbeleg (M1-… bis M3-…, Tabellen oben). „Abgeleitet“ ist nicht gemessen. Eine Option ohne Beleg zu einem Punkt ist dort nicht bewertet. Die Auswahl trifft der Nutzer.

| Option | Pro (Beleg) | Contra (Beleg) |
|---|---|---|
| A1 Präfix `{kennung}` | Der Rest hinter der ersten `}` bleibt unverändert, `}` und `$` im `plain`-Wert gehen durch (M2-1); steht in einer Properties-Datei im Wert, wo `{` und `$` unkritisch sind (M3-4). | Drei zusätzliche Fehlerarten für die Kennung: fehlend, leer, nicht geschlossen (M2-1); `{{plain}}` und `{ plain}` ergeben „unbekannte Kennung“ (M2-1). Ein Standardverfahren für Einträge ohne Kennung ist im Spike nicht gebaut; abgeleitet: ein Prüfwert, der mit `{` beginnt, ließe sich dann nicht von einer Kennung trennen (M2-1 zeigt nur „unbekannte Kennung“ ohne Standardverfahren). |
| A2 eigenes Feld `verfahren` | Wert und Verfahren getrennt: der Wertparser braucht keine Kennungs-Zerlegung (M2-8). | Braucht ein Format mit zwei Feldern je Eintrag; `Properties` kennt nur Schlüssel und Wert (M3-1 bis M3-4), also ein zusätzlicher Schlüsselname (abgeleitet). Steht die Kennung doch im Wert, lautet die Art „Salz ungültig“, nicht „Kennung doppelt“ (M2-8). |
| A3 `$kennung$…` | `plain` mit `$` im Wert geht durch (M2-9). | Das Trennzeichen `$` trennt Kennung und Felder von `pbkdf2-sha256` zugleich (M2-9); die Richtung des Nutzers (geschweifte Klammern) wird nicht abgebildet; `$abc$…` ergibt „unbekannte Kennung“ (M2-9). |
| B1 `plain` und `pbkdf2-sha256` | Startabbruch von `plain` ohne Testmodus ist messbar und liefert Exit 1 vor dem Binden des Ports (M3-6); ein Test mit bekanntem Passwort braucht keine Ableitung (abgeleitet). | Ein zweiter Prüfpfad; der `plain`-Wert steht im Klartext in der Datei und ist je nach Weg lesbar (M3-5); leerer `plain`-Wert ist im Spike ein Fehler, ein leeres Passwort ist als Client-Eingabe zulässig (M1-10, Vertragsdatei) — Festlegung offen. |
| B2 nur `pbkdf2-sha256` | Ein Prüfpfad; das JDK ableitet bekannte Vektoren richtig (M1-3). | Der Testbetrieb braucht einen Prüfwert zu bekanntem Passwort; wie er erzeugt wird, ist nicht gemessen. |
| B3 plus weiteres JDK-Verfahren | Das JDK bietet `PBKDF2WithHmacSHA1`, `SHA224`, `SHA384`, `SHA512`, `SHA512/224`, `SHA512/256` (M1-1). | scrypt, Argon2 und bcrypt gibt es im JDK-Image nicht (M1-2) und bräuchten eine Fremdbibliothek; gleiche Iterationszahl heißt nicht gleiche Dauer: bei c = 600 000 `SHA1` 270,8, `SHA256` 150,7, `SHA512` 563,1 ms (M1-8), eine Untergrenze gilt also je Verfahren. |
| C1 feste Untergrenze | Kein Konfigurationsfall; die Staffel zeigt: 600 000 → 124,5 ms, 1 000 000 → 206,1 ms (M1-4). | Der Wert ist an dieser Hardware gemessen (M1-4, M1-5); ein Betrieb mit anderer Hardware ist nicht gemessen. |
| C2 Standard mit hartem Boden | Zwei Zahlen: Standardwert und Boden; ein Test kann mit niedriger Zahl laufen (c = 10 000: 3,1 ms, M1-4). | Eine weitere Festlegung (der Boden) und ein Konfigurationsschlüssel; der JDK-Wert 0 und negative Werte scheitern ohnehin (M1-11). |
| C3 keine Untergrenze (nur Testmodus) | Das JDK nimmt c = 1 (M1-3). | Ohne Grenze ist ein schwacher Prüfwert zulässig; der Spike misst keine Sicherheitswirkung. Der Schutz vor Raten liegt außerhalb dieses Slice ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), `slice-qa-sicherheit`). |
| Obergrenze (zu C) | 10 000 000 → 2 086,6 ms, 30 000 000 → 6 387,0 ms (M1-4); 15 s bei rund 70 000 000 (abgeleitet). | Eine Obergrenze schützt nicht vor Last: `--cpus=1`, 64 gleichzeitige Ableitungen mit c = 600 000: Median 10,5 s, Maximum 23,6 s (M1-7). Kalt im frischen JVM sind es bei 10 000 000 Iterationen 2 616,8 ms (M1-6). |
| D1 Datei, Pfad per Umgebung oder Argument | Der Wert steht nicht in `docker inspect`, `docker logs`, `/proc/1/environ` und `/proc/1/cmdline` (M3-5). | Die Datei ist per `docker exec`, `docker cp` und `docker run --entrypoint cat` lesbar, im Spike als `root` (M3-5); Dateirechte im Betrieb nicht gemessen. |
| D2 Umgebungsvariablen | Keine Datei zu pflegen; Größe kein Hindernis bis 131 060 Zeichen je Variable und 1 500 000 gesamt (M3-7). | Der Wert steht in `docker inspect` (`Config.Env`) und in `/proc/1/environ` (M3-5); der Docker-Client lehnt `--env-file`-Zeilen über 64 KiB ab (M3-7); mehrzeilige Werte nicht gemessen. |
| D3 eigene Datei nur für die Benutzerliste | Sichtbarkeit wie D1 (M3-5); die übrige Konfiguration enthält keinen Prüfwert (abgeleitet). | Eine Datei und ein Pfad mehr; Dateirechte im Betrieb nicht gemessen. |
| F1 `Properties` | JDK, keine Abhängigkeit; Schreiben und Lesen durch das Format selbst: 23 von 23 Kennungen (M3-1). | Von Hand geschrieben 11 von 23 Kennungen falsch gelesen (M3-2); doppelte Kennung ohne Fehler, Reihenfolge nicht erhalten, keine Zeilennummer (M3-3); `\` im Wert wird verschluckt, `load(InputStream)` liest ISO-8859-1 (M3-4). |
| F2 eigenes Zeilenformat | Duplikat erkennbar und Reihenfolge erhaltbar (M3-3); Leerzeichen, `:`, `=`, `#`, `\` in der Kennung unkritisch (M3-2). | Von Hand geschrieben 4 von 23 Kennungen nicht darstellbar: TAB, LF, CR, alleinstehendes Hochsurrogat (M3-2); eigener Code und eigene Regeln, die das Repo pflegt. |
| F3 Format mit Parser-Bibliothek | — | Nicht gemessen (M2-10); trägt die Kosten einer neuen Abhängigkeit mit Lock und Prüfsumme. |
| E1 gemeinsamer Testmodus | Ein Schalter; `plain` ohne Schalter bricht vor dem Binden des Ports ab, der Log nennt den Modus (M3-6). | Der Schalter ist mit anderen Abschwächungen geteilt (Risiko in §6); im Spike schaltet nur der Wert `true`, `TRUE` nicht (M3-6). |
| E2 eigener Schalter | Unabhängig von anderen Abschwächungen (abgeleitet); Verhalten beim Start wie E1 (M3-6). | Ein weiterer Konfigurationsschlüssel ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). |

**Befund zur Richtung des Nutzers** (Setzungen geprüft, keine Festlegung): `PBKDF2WithHmacSHA256` ist im Image vorhanden (M1-1), ableitet bekannte Vektoren richtig (M1-3) und braucht keine Fremdbibliothek. Das Beispiel `Salz$Iterationen$Hash` ist mit Base64 und Hex verträglich, weil `$` im Zeichenvorrat beider Kodierungen nicht vorkommt (M1-12). `{plain}` ohne Testmodus lässt sich vor dem Binden des Ports abweisen, ohne den Wert zu nennen (M3-6, M2-2). Offene Punkte, die die Messung aufwirft und die der Nutzer mitentscheidet: Umgang mit dem alleinstehenden Hochsurrogat im Passwort (M1-10), Unicode-Normalform (M1-10), Strenge der Zahlenerkennung (M2-4), Padding von Salz und Hash (M2-7), leerer `plain`-Wert (M2-1).

### Entscheidungsform und Verhältnis zur Authentifizierungs-ADR

[ADR-0009](../../adr/0009-authentifizierungsmodus.md) ist `Accepted` und wird nicht überschrieben (`AGENTS.md` §3.5). Sie wählt SQL-Authentifizierung über einen Adapter mit fester Benutzerliste und nennt Konfigurationsweg und Prüfwertformat als „noch festzulegen“ (§Entscheidung) und als offen (§Offen); sie entscheidet beides nicht. Das Festlegen füllt daher eine benannte Lücke und widerspricht ihrer Entscheidung nicht. Zwei Formen stehen zur Wahl, der Nutzer entscheidet:

- **Fortschreibung** von [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) (technisch fortschreibbar) und `contracts/auth-adapter.md`: genügt, wenn die Wahl nur Format und Defaults festlegt. Begründung für die Unversehrtheit der ADR: Die Spezifikation darf geschärft werden, die ADR bleibt wörtlich stehen, und ihr §Offen bleibt als Stand zum Zeitpunkt ihrer Annahme lesbar.
- **Neue ADR** mit Begründung, Alternativen (Optionen A bis E), Messmatrix und Geschichte, `Schärft:` SPEC-051 und `Bezug:` auf [ADR-0009](../../adr/0009-authentifizierungsmodus.md) ohne `Supersedes`, weil sie nichts entscheidet, das hier geändert würde. Der Planner neigt zur ADR, wenn der Nutzer einen Testmodus-Schalter mit Sicherheitswirkung (Option E) oder eine neue Abhängigkeit (Option D, F3) wählt: Beides ist dauerhaft und betrifft zwei Slices; das ist eine Neigung, keine Entscheidung. Würde die Wahl [ADR-0009](../../adr/0009-authentifizierungsmodus.md) widersprechen, wäre das eine `Supersedes`-ADR; die Spikes zeigen derzeit keinen solchen Fall, das ist vor der Wahl zu prüfen.

### Verantwortung für Prüfwert und Konfigurationsmodell

Die Grenze zwischen den beiden Anschluss-Slices ist Teil der Entscheidung und wird dort festgehalten. Der Plan nimmt an, ohne es zu setzen: `slice-auth-adapter-benutzerliste` hält den Prüfwert als Wert (Verfahren, Parameter, Salz, Hash), parst und validiert ihn, vergleicht und kennt den Testmodus als Eingabewert (`plain` ohne Testmodus ergibt einen Fehlerwert); `slice-bootstrap-konfiguration` besitzt das Konfigurationsmodell, liest Datei oder Umgebung nach dem entschiedenen Weg, reicht die Einträge als Text an die Validierung des Auth-Slice weiter, führt den Testmodus-Schlüssel und bricht bei einem Fehlerwert den Start ab. Weicht die Entscheidung davon ab, werden beide Slices in §3/§4 angepasst.

**Entscheidungsvermerk (2026-10-30):** Die Wahl des Nutzers (A1, B1, C2, D1 mit F2, E2) steht als [ADR-0011](../../adr/0011-pruefwertformat.md) im Status `Proposed`, `Bezug:` [ADR-0009](../../adr/0009-authentifizierungsmodus.md) ohne `Supersedes`, `Schärft:` [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge). Offen bleiben die Zahlen der Iterationszahl und neun Regelfragen ([ADR-0011](../../adr/0011-pruefwertformat.md) §Entscheidung, Offen). Anschlussstellen (Slices) sind noch nicht nachgezogen; das geschieht nach `Accepted`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-handshake-login` ist eingetreten, ein Verantwortlicher ist im Kopf eingetragen und der Nutzer steht für die Wahl der Optionen A bis E bereit. Der Slice braucht den Codec nicht; er kann vor `slice-auth-adapter-benutzerliste` und `slice-bootstrap-konfiguration` laufen und ist ihre Voraussetzung.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Nutzer entscheidet nicht, oder die gewählte Option braucht eine Abhängigkeit, die sich nicht per Lock und Prüfsumme fixieren lässt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben. Jede Aussage der Entscheidung trägt eine Messzeile (S1 bis S3) oder ist als Setzung des Nutzers gekennzeichnet (`BEO-TDS/aussagen-ueber-messung-hinaus`).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Richtung des Nutzers wird als gesetzt gelesen, bevor die Spikes sie geprüft haben (zum Beispiel Beispielformat `Salz$Iterationen$Hash` oder Kennung in geschweiften Klammern). — **Ausgang:** offen bis zur Closure.
- Überschneidung mit `slice-qa-sicherheit`: Dessen Liefer-Punkt nennt das Prüfwertformat ebenfalls („entschieden oder als Carveout geführt“). Dieser Slice entscheidet es vorgezogen; `slice-qa-sicherheit` bleibt beim Anlegen dieses Slice unverändert, weil seine Änderung eine Entscheidung vorwegnähme, die noch nicht gefallen ist. Mit der Closure wird der Punkt dort auf die verbleibenden Gegenstände (Zeitgleichheit, Drosselung) gekürzt und der Verweis auf die Entscheidung gesetzt, die Änderung ist in §7 begründet. — **Ausgang:** offen bis zur Closure.
- Die Dauer einer Ableitung ist an Image und Hardware des Spike-Laufs gemessen; sie gilt weder für den Produktivbetrieb noch für die parallele Last mehrerer Anmeldungen. Eine Untergrenze der Iterationszahl ist damit eine Festlegung mit Messwert, keine Leistungszusage; die Wirkung auf den Coroutinen-Transport ([ADR-0003](../../adr/0003-transportframework.md)) bei blockierender Ableitung ist nicht gemessen. — **Ausgang:** offen bis zur Closure.
- Ein Testmodus-Schalter kann den Betrieb mit `plain` unbemerkt machen, wenn er mit anderen Schaltern geteilt wird (Option E1). — **Ausgang:** offen bis zur Closure.
- Prüfwerte in Datei oder Umgebung: die Sichtbarkeit je Weg wird gemessen, die Dateirechte und die Übergabe im Betrieb nicht. — **Ausgang:** offen bis zur Closure.
- Eine Parser-Bibliothek (Option F3) wäre eine neue Abhängigkeit mit Lock und Prüfsumme; ohne Netz im Spike ist nur „fixierbar ja/nein“ zu befunden. — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Wird bei der Closure geschrieben. Die Ruheort-Regel gilt für jeden Pfad: Verweise werden so geschrieben, wie sie vom Ruheort `done/` auflösen.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec, Vertragsdatei und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/`) durchgegangen. Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice` (1×, offen; Konfigurationsweg des Adapters — dieser Slice trägt ihn), `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (1×, offen; Sicherheitsaussagen zum Prüfwert) und `BEO-TDS/aussagen-ueber-messung-hinaus` (2×; Grund für die Messzeilen-Pflicht in §5).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
