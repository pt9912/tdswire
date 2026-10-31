# Verifikation slice-qa-sicherheit (Modul 11)

**Gegenstand:** [slice-qa-sicherheit](../plan/planning/in-progress/slice-qa-sicherheit.md) (Welle welle-qualitaet-grundlage) nach Umsetzung 1f17d0d und a735892 sowie Fix-Runde 84a6b78, Stand `84a6b78` (im Worktree enthalten, Basis `c0ffd7a`). Bezug: [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [ADR-0011](../plan/adr/0011-pruefwertformat.md); `SPEC-010`, `SPEC-023`, `SPEC-043`, `SPEC-051`.
**Rolle:** Verifier (gegen DoD, Spec und ADR; kein Diff-Review). Eingang: Review [2026-10-31-5d95bd2-slice-qa-sicherheit.md](2026-10-31-5d95bd2-slice-qa-sicherheit.md) (F-1 bis F-12) und die Fix-Runde.
**Methode:** alle Läufe Docker-only; Mutationen und Messeinbauten im Quelltext des Worktrees, je danach mit `git checkout -- src` zurückgesetzt (Baum sauber); ein Docker-Build zur Zeit, kein Lauf über 10 Minuten (`timeout 540`). Ausgaben lagen im Scratchpad, nicht im Repo.

## Urteil

Die Umsetzung ist **bestätigt**: die vier Liefer-Punkte der DoD (Loginspektion, Zeitgleichheit/Platzbelegung/Drosselung, TLS- und Konfigurationsnegativtests, Doku-Update) sind an Code und bestandenen Tests belegt, `make gates` ist grün, und fünf der sechs geprüften Mutationen an Produktcode färben die vorgesehene Testklasse rot. Die Findings F-1 bis F-8 des Reviews sind **behoben** (F-6 mit einer Grenze, siehe unten). Befunde: **kein HIGH, ein MEDIUM, vier LOW**. Das MEDIUM ist eine Folge der Fix-Runde selbst: das KDoc des Adapters sagt jetzt „Zeit gemessen“, drei Stellen der Spezifikation (höherer Rang) sagen weiter „nicht gemessen“. Der Slice ist **noch nicht schließbar**: die Closure-Pflichten (Review-Haken, Closure-Notiz, Ausgänge der Risiken, Paarungen) stehen aus. Eine Closure ist nicht Teil dieses Auftrags.

## Eigene Läufe

| Lauf | Ergebnis |
|---|---|
| `docker build --no-cache-filter test --target test --progress=plain .` auf `84a6b78` | Exit 0, `BUILD SUCCESSFUL in 1m 18s`, 738 Tests, 738 `PASSED`, keine rote und keine übersprungene Testzeile; alle sieben neuen Klassen und `TlsRenegotiationTest` PASSED |
| `make gates` (einmal) | Exit 0: `baseline-verify` OK (54 Dateien), `d-check` 459 Dateien 0 Befunde, `detekt-selbsttest`, Laufzeittest des Images grün; die Stage `test` kam aus dem Cache des Frischlaufs darüber |
| `make docs-check` | nach Anlage dieses Berichts grün (0 Befunde), siehe Ende |

## Mutationen an Produktcode

Sechs Mutationen in einem Lauf (disjunkte Stellen), danach nach Testklasse zugeordnet; die sechste einzeln wiederholt, weil sie nicht gefangen wurde. Ergebnis des Sammellaufs: 738 Tests, 29 rot.

| # | Mutation | Erwartete Klasse | Ergebnis |
|---|---|---|---|
| M1 | `UserListAuthAdapter.decide`: Scheinvergleich entfernt (`entry != null && verify(entry.secret, …)`) | `AuthTimingTest` | **gefangen**: `AuthTimingTest` zwei Tests rot (gleich lang; gemischte Liste), dazu drei Strukturtests in `UserListAuthAdapterTest` |
| M2 | `AuthExecutor`: `pool.limited` durch `pool.dispatcher` | `AuthPoolOccupationTest` | **gefangen**: beide Tests rot, dazu `AuthExecutorTest`, `HandshakeDriverTest`, `HandshakeWiringTest` |
| M3 | `PreloginNegotiation`: bei `REQUIRED` `tlsFollows = false` (Klartext-Herabstufung) | `TlsHandshakeNegativeTest` | **gefangen**: alle fünf Tests rot, dazu `PreloginNegotiationTest`, `HandshakeMatrixTest`, `TlsWiringTest` |
| M4 | `plainTestModeFrom`: `equals("true", ignoreCase = true)` | `SecurityConfigNegativeTest` | **gefangen**: `Der Testmodus fuer plain gilt nur bei genau true …` rot, dazu `ConfigUsersFileTest`, `UserListParserTest` |
| M5 | `PendingLogin.toString` nennt `$database` | `LoginPathTextTest` | **gefangen**: `Eine angenommene Anmeldung zeigt in keiner Textdarstellung einen Wert` rot |
| M6a | `TlsReader.afterRecord`: Antwort der Leseseite entfällt (`answered` immer `Ok`) | `TlsRenegotiationTest` | **nicht gefangen**: `TlsRenegotiationTest` PASSED; rot nur `TlsLoopbackTest > key update requested by the peer …` |
| M6b | `TlsWriter.answer()` ohne Wirkung | `TlsRenegotiationTest` | **nicht gefangen**: wie M6a, einziger roter Test ist der KeyUpdate-Test (1 von 738) |
| M6c | `TlsReader.afterRecord`: `NEED_TASK` führt keine Aufgaben aus | `TlsRenegotiationTest` | **gefangen**: `Eine Neuverhandlung in TLS 1_2 wird beantwortet …` rot (1 von 738) |

Zu M6: Der Test fängt die Rechenschritte der Neuverhandlung (`NEED_TASK`), aber nicht den Wegfall der Antwort der Leseseite, weil der Schreibpfad (`TlsWriter.write` → `wrap`) die Handshake-Daten beim nächsten Nutzdatenschreiben ausgibt. Die im Register genannte Mutation („`afterRecord` lehnt `NEED_WRAP` ab“) habe ich nicht in dieser Form nachgebaut; ihre beiden nächsten Verwandten (M6a, M6b) sind grün (L-1).

## Zeitmess-Tests unter Last: Streuung über drei Läufe

Messeinbau (nicht eingecheckt): jeder Zeittest bricht am Ende mit der Messung als Meldung ab (`fail("MESS-…")`), sodass die Zahlen im Lauf erscheinen; je Lauf frische Stage (`--no-cache-filter test`). Last: Lauf 1 Umgebung (Load Average 5 bis 8, 20 Kerne), Lauf 2 zusätzlich 20 Rechenprozesse (Load Average 7 bis 10), Lauf 3 zusätzlich 60 Rechenprozesse (Load Average am Ende 85, einfache Überbuchung der 20 Kerne mehr als dreifach). Mediane in ms:

| Messung | Lauf 1 (Umgebung) | Lauf 2 (+20) | Lauf 3 (+60) |
|---|---|---|---|
| gleich, bekannt/falsch | 42,61 | 43,20 | 63,77 |
| gleich, unbekannt | 42,48 | 42,59 | 63,89 |
| gleich, bekannt/richtig | 43,22 | 42,81 | 63,59 |
| Abstand größter zu kleinster (Schranke 25 %) | 1,7 % | 1,4 % | 0,5 % |
| Gegenprobe ohne Scheinvergleich, unbekannt | 0,0019 | 0,0016 | 0,0023 |
| gemischt: Attrappe / halbe Iterationszahl / `plain` | 85,07 / 42,32 / 0,037 | 84,05 / 43,12 / 0,042 | 127,77 / 64,00 / 0,042 |
| Pool: frei / eine Welle / zwei Wellen (Sollwert 300 / 550 / 850) | 300,5 / 550,4 / 850,7 | 300,4 / 550,2 / 850,4 | 300,3 / 550,2 / 850,3 |
| Fenster des Passworts nach der Freigabe (Grenze 2000) | 0,48 | 0,30 | 2,33 |

Befund aus der Streuung: Die Last verschiebt die absolute Dauer einer Ableitung um rund 50 % (42,6 auf 63,8 ms), das **Verhältnis** der drei Fälle bleibt bei höchstens 1,7 % Abstand, weit unter der Schranke von 25 %; die Abstände der Gegenproben (Faktor 10 000 und mehr; halbe Iterationszahl 0,5) ändern sich nicht. Die Pool-Werte sind durch die Haltezeit bestimmt und streuen unter 1 ms. Die absoluten Werte der Doku („rund 57 ms“, „102/48/0,05 ms“) sind hostgebunden und tragen die Bedingung „Host des Laufs“; auf diesem Host liegen sie bei 42 bis 64 ms. Drei Läufe auf einem Host sind eine Stichprobe, keine Aussage über einen CI-Runner mit einem Kern oder einem Nachbarn mit gleicher Zeitscheibe; die Schranken bleiben Setzungen, wie das KDoc sagt.

## DoD Punkt für Punkt

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | **Loginspektion**: kein Geheimnis im Log (Test), einschließlich Textdarstellungen des Anmeldepfads | **bestätigt** | `LoginLogInspectionTest` (fünf Ausgänge der Anmeldung am Serverprozess über Socket, Gegenprobe des Prüfers) und `LoginPathTextTest` (drei Tests, Gegenprobe) PASSED; M5 färbt `LoginPathTextTest` rot. Die Reichweite ist das minimale Log von heute; das Gesamtsystem trägt [slice-betrieb-log-schnittstelle](../plan/planning/open/slice-betrieb-log-schnittstelle.md) (siehe F-1) |
| 2 | **Zeitgleichheit** (Test) und **Drosselung** (entschieden oder als Carveout geführt); Plätze des `AuthPool` gemessen oder als Grenze genannt | **bestätigt (mit Grenzen)** | `AuthTimingTest` (drei Tests inkl. Gegenprobe und Grenze gemischter Listen), `AuthPoolOccupationTest` (zwei Tests); M1 und M2 rot. Drosselung als [CO-003](../plan/carveouts/CO-003-anmeldedrosselung-fehlt.md) mit Folge-Slice geführt. Grenzen, die die Doku nennt: gemischte Listen trennen messbar (Test 3), Wirkung auf den Transport unter Last und Kopien im Speicher ungemessen |
| 3 | **TLS-Handshake- und Konfigurationsnegativtests** nach `SPEC-043` und `SPEC-010` | **bestätigt** | `TlsHandshakeNegativeTest` (5 Fälle: Klartext-LOGIN7 nach ENCRYPT_REQ, TLS 1.1, Nicht-TLS, Abbruch, fehlendes Vertrauen), `SecurityConfigNegativeTest` (3 Fälle: Richtlinie ohne Rückfall, unlesbarer Speicher/fehlende Kennwortdatei, Testmodus nur bei genau `true`), `TlsRenegotiationTest` (2); M3 und M4 rot. Die Fälle `CERTIFICATE` und `PAIR` der Startprobe prüft weiter `TlsStartProbeTest` auf Komponentenebene |
| 4 | `make gates` grün | **bestätigt** | Exit 0 |
| 5 | Review durchgeführt, Report liegt vor | **bestätigt (Haken offen)** | [2026-10-31-5d95bd2-slice-qa-sicherheit.md](2026-10-31-5d95bd2-slice-qa-sicherheit.md); der Haken in §2 steht auf `[ ]` |
| 6 | Doku-Update bei öffentlichem Vertrag | **bestätigt, mit Rest (M-1)** | `spec/spezifikation.md` §Festlegungen der TLS-Schleife, KDoc von `UserList`, `UserListAuthAdapter`, `AuthExecutor`; die SPEC-051-Stellen der Spezifikation sind nicht nachgezogen |
| 7 | Closure-Notiz mit Lerneintrag | **offen (Closure)** | §7 „Wird bei der Closure gefüllt.“ |
| 8 | Beobachtungs-Register fortgeschrieben | **bestätigt** | drei Beleg-Dateien `evidence/slice-qa-sicherheit.md` (`anmeldepfad-sicherheit-nicht-gebaut`, `geheimnis-kopien-im-speicher-nicht-gemessen`, `tls-neuverhandlung-ohne-begrenzung`), kein Zähler gesetzt |
| 9 | Jedes Risiko aus §6 trägt einen Ausgang | **bestätigt (Haken offen)** | alle vier Risiken tragen einen Ausgang (entfallen, eingetreten, eingetreten, weiter offen mit Träger); Haken `[ ]` bis zur Closure |
| 10 | Drei Paarungen (Anker · Folge-Slice · Register) | **offen (Closure)** | Folge-Slice [slice-qa-anmeldedrosselung](../plan/planning/open/slice-qa-anmeldedrosselung.md) liegt und verweist auf CO-003 zurück; Anker-Feld entsteht mit der Closure |

## Fix-Runde: F-1 bis F-8

| Finding | Behoben? | Beleg |
|---|---|---|
| F-1 Träger der Gesamtsystem-Loginspektion | **ja** | Alle Fundstellen (Suche nach `Loginspektion`, `Gesamtinspektion`, `Inspektionstest`) nennen denselben Träger: [welle-betrieb-diagnose](../plan/planning/welle-betrieb-diagnose.md) §1 (Grenzen), §3 (Inspektionstest über alle Zeilen), §5 (`slice-qa-sicherheit` nicht mehr als blockiert aufgeführt), §8; [slice-betrieb-log-schnittstelle](../plan/planning/open/slice-betrieb-log-schnittstelle.md) (§1 und DoD „Ablösung und Redaktion“ führen den Inspektionstest über alle erfassten Zeilen als Gate-Test des Gesamtsystems, Register-Zeile); [slice-adr-observability](../plan/planning/open/slice-adr-observability.md) (§1 und Register-Zeile); [slice-qa-sicherheit](../plan/planning/in-progress/slice-qa-sicherheit.md) (§1, §6 mit Ausgang „weiter offen“ und Träger). Widerspruch zwischen Welle und Slice entfällt. Rest: L-3 |
| F-2 KDoc `TlsChannel` | **ja** | Satz nennt die Neuverhandlung als beantwortet („`TlsRenegotiationTest`, JDK-Gegenstelle, dreimal; keine Begrenzung“), deckungsgleich mit der Spezifikation |
| F-3 Aussage von `AuthPoolOccupationTest` | **ja** | Test hält nun `delay(HOLD_MILLIS)` vor der Lesbarkeitsprüfung, das KDoc sagt „mindestens [HOLD_MILLIS] bei einem Platz“ und nennt Dauer darüber und Verhältnis zu `LOGIN_TIMEOUT` als nicht belegt; dieselbe Einschränkung steht im KDoc von `AuthExecutor` und im Register. Der Test belegt die Aussage: das Passwort bleibt nach 300 ms lesbar und ist nach der Freigabe überschrieben (0,3 bis 2,3 ms nach der Freigabe gemessen) |
| F-4 CO-003 ohne ausgenommene Regel | **teilweise** | Der Satz „nimmt keine Regel und keine Schwelle aus“ benennt den Befund jetzt im Carveout; die Form ist unverändert, siehe L-2 |
| F-5 KDoc `UserListAuthAdapter` | **ja** | „Zeit gemessen von `AuthTimingTest`“; das schafft aber den Widerspruch zur Spezifikation (M-1) |
| F-6 Reichweite „nie im Klartext“ | **ja (Reichweite), Gegenprobe unverändert** | Spezifikation: „die Marke des Servers erscheint nicht im Klartext in seinem Ausgang“, Semikolon-Großschreibung behoben; Test prüft nur noch den Ausgang des Servers (`sent`), Testname und KDoc sagen „Marke“ und nennen den Eingang als Beleg-fremd. Die Gegenprobe prüft weiter nur die Hilfsfunktion `contains` |
| F-7 Messbedingung „rund 550 ms“ | **ja** | KDoc: „gemessen ab 50 ms nach dem Beginn der belegenden Aufrufe; nominell 600 und 900 ms“; die gemessenen 550 und 850 ms habe ich in allen drei Läufen reproduziert |
| F-8 §8 veraltet | **ja** | §8 nennt die zwei Register-Treffer (3×) und die ausgefüllte Modus-Deklaration (Kürzel `TDS`, Greenfield); beides stimmt mit `harness/conventions.md` und `observations/BEO-TDS/` überein |

F-9 bis F-12 waren INFO. Zu F-9 liefert dieser Bericht die Streuung (Abschnitt oben); zu F-10 die Mutationen (L-1); F-11 und F-12 haben keine Folge.

## Konformität mit ADR und Spezifikation

- [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md) (`Accepted`): SQL-Authentifizierung über den Adapter, §Offen Drosselung als [CO-003](../plan/carveouts/CO-003-anmeldedrosselung-fehlt.md) mit Folge-Slice geführt, Zeitgleichheit gemessen; die ADR selbst ist unverändert (§3.5).
- [ADR-0011](../plan/adr/0011-pruefwertformat.md) (`Accepted`): Attrappe ist der `pbkdf2`-Eintrag mit der höchsten Iterationszahl; die Messung der gemischten Liste (Attrappe 85 ms, halbe Iterationszahl 42 ms, `plain` 0,04 ms) bestätigt die in `SPEC-051` genannte Grenze und ist als Grenze, nicht als Zusage geführt.
- [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md) (`Accepted`) und `SPEC-010`: kein unbemerkter Rückfall auf Klartext (M3 rot), Abschwächung nur durch ausdrückliche Konfiguration, im Log sichtbar (`SecurityConfigNegativeTest`, M4 rot).
- `SPEC-023`: die Festlegungen der TLS-Schleife nennen KeyUpdate in TLS 1.3 und Neuverhandlung in TLS 1.2 mit Messbedingung und ohne „nie“; eine Begrenzung gibt es nicht, die Rechenlast ist nicht gemessen.
- `SPEC-051`: siehe M-1.
- Hard Rules: kein `@Suppress` in `src/` (§3.2); `config/detekt/detekt.yml`, `Makefile`, `tools/`, `harness/mk/` nicht im Diff, keine Schwelle gesenkt (§3.6); keine Meilensteine in `spec/architecture.md` (§3.4, Datei nicht berührt); Aussagen in KDoc und Spezifikation tragen Messbedingung oder „Setzung“ (§3.8), Rest siehe M-1 und L-1; Kommentare im Indikativ ohne abwesenden Text (§3.7), außer dem Zustandsfeld in M-1.

## CO-003 und Folge-Slice (Modul 7)

Regelkonform in der Form: die sechs Pflichtfelder (Status, Datum angelegt, Letzte Prüfung, betroffenes Gate, Geltungsbereich, Folge-Slice) stehen; der Auflösungs-Trigger ist beobachtbar (Folge-Slice in `done/` **und** eine ADR `Accepted` zur Drosselung oder zum Verzicht); der Folge-Slice existiert als [slice-qa-anmeldedrosselung](../plan/planning/open/slice-qa-anmeldedrosselung.md) und verweist auf den Carveout zurück (Ziel, Start-Trigger, DoD); die Geltungs-Konfiguration nennt die zwei Stellen, die `CO-003` im KDoc tragen (`AuthExecutor`, `AuthPoolOccupationTest`); [carveouts/README.md](../plan/carveouts/README.md) führt die Zeile, die Welle führt ihn in der Slice-Tabelle. Eine Zeile in `harness/README.md` §Sensors ist nicht fällig, weil kein Gate betroffen ist. Der Folge-Slice trägt höchstens drei Liefer-Punkte (zwei) und benennt sein einziges Risiko (Ausgang „offen bis zur Closure“, vor der Closure zulässig). Die Frage, ob der Carveout das richtige Werkzeug ist, bleibt L-2.

## Plan gegen Code

Alle Zeilen der Tabelle in §3 des Slice liegen vor: sieben neue Testklassen (`LoginPathTextTest`, `LoginLogInspectionTest`, `AuthTimingTest`, `AuthPoolOccupationTest`, `TlsHandshakeNegativeTest`, `SecurityConfigNegativeTest`, `TlsRenegotiationTest`), `TlsTestSupport.kt` (18 Zeilen in `JdkTlsPeer.read`), CO-003 und Folge-Slice. Über die Tabelle hinaus berührt der Diff `UserList.kt`, `UserListAuthAdapter.kt`, `AuthExecutor.kt` (KDoc, in der DoD als Doku-Update genannt), `spec/spezifikation.md` (ein Satz), Register und Wellen-Dateien; die Fix-Runde fügt `TlsChannel.kt` (KDoc) und die Nachbar-Slices hinzu. Am Produktcode ändert sich keine Zeile außer KDoc (Prüfung: die Diffs von 1f17d0d und 84a6b78 gegen ihre Vorgänger über `src/main` enthalten nur KDoc- und Kommentarzeilen). Der Plan nennt in §3 `slice-betrieb-log-schnittstelle` nicht; das ist die F-1-Folge in §1 und §6.

## Findings

| ID | Kategorie | Befund | Beleg |
|---|---|---|---|
| M-1 | MEDIUM | Die Fix-Runde zieht das KDoc von `UserListAuthAdapter` auf „Zeit gemessen von `AuthTimingTest`“ (F-5), die Spezifikation (Rang 2) sagt an drei Stellen weiter das Gegenteil: „das ist weder gebaut noch gemessen, ein Test muss es belegen“ (Zeile 36), „Gebaut und strukturell belegt …, nicht gemessen“ (Zeile 1065), „Bauart des Vergleichs (Anforderung, strukturell belegt, nicht gemessen) … nichts davon ist gemessen“ (Zeile 1122). Nach der Quellenrangfolge gewinnt die Spezifikation; das KDoc und der Test widersprechen ihr, und Zeile 36 ist auch für „gebaut“ falsch. Der DoD-Haken „Doku-Update, falls ein öffentlicher Vertrag berührt“ ist gesetzt, die SPEC-051-Stellen sind nicht im Diff. Kein Verstoß gegen §3.8 (die Spezifikation behauptet zu wenig, nicht zu viel), aber ein Zustandsfeld, das nicht beschreibt, was da ist (§3.7). | `spec/spezifikation.md` Zeilen 36, 1065, 1122; `UserListAuthAdapter.kt` „Zeit gemessen von `AuthTimingTest`“ |
| L-1 | LOW | `TlsRenegotiationTest` fängt den Wegfall der Antwort der Leseseite nicht (M6a, M6b grün; nur der KeyUpdate-Test von `TlsLoopbackTest` wird rot), er fängt `NEED_TASK` (M6c). Die Spezifikation sagt „beantwortet die Schleife“; belegt ist, dass Nutzdaten nach der Neuverhandlung in beide Richtungen laufen, nicht, dass die Leseseite die Antwort schreibt. Das Register nennt die Mutation „`afterRecord` lehnt `NEED_WRAP` ab“ mit „(Lauf, kein abgelegter Beleg)“, ehrlich gekennzeichnet; ich konnte sie nicht in dieser Form nachbauen. | `TlsRenegotiationTest.kt`; `BEO-TDS/tls-neuverhandlung-ohne-begrenzung/evidence/slice-qa-sicherheit.md` |
| L-2 | LOW | CO-003 hält die Form, nimmt aber, wie der Carveout jetzt selbst sagt, keine Regel und keine Schwelle aus (`Betroffenes Gate: keines`); er führt einen offenen Punkt der [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md) §Offen. Modul 7 kennt den Carveout als „konkrete Gate-/Regelausnahme“; die Plan-DoD hat ihn ausdrücklich zugelassen („entschieden oder als Carveout geführt“). Der Trigger hängt an einer Entscheidung des Nutzers ohne Termin, und die Welle ([welle-qualitaet-grundlage](../plan/planning/welle-qualitaet-grundlage.md)) schließt erst mit dem Folge-Slice. Kein Regelbruch, eine Werkzeugwahl, die die Closure der Welle und das Carveout-Audit mittragen müssen. | `CO-003-anmeldedrosselung-fehlt.md` „Betroffenes Gate“ |
| L-3 | LOW | Zwei Stellen nennen `slice-qa-sicherheit` noch als Träger der Gesamtinspektion: [welle-handshake-login-results.md](../plan/planning/done/welle-handshake-login-results.md) Zeile 52 („Zeitgleichheit, Drosselung, Loginspektion über das Gesamtsystem, Plätze des `AuthPool` unter Last“ → `slice-qa-sicherheit` (`open/`)), und der Stand des Registers `anmeldepfad-sicherheit-nicht-gebaut/state.md` führt als Träger nur `slice-qa-sicherheit` ohne den Zusatz, dass die Gesamtinspektion bei `slice-betrieb-log-schnittstelle` liegt. Die erste ist ein Zeitdokument (Stand des Closure-Tages, nicht anzupassen), die zweite ein Zustandsfeld. Außerdem steht in [slice-betrieb-log-schnittstelle](../plan/planning/open/slice-betrieb-log-schnittstelle.md) §1 unter „Ausdrücklich NICHT“ ein Punkt, der die neue Zusage des Slice enthält (`die Loginspektion über das Gesamtsystem … trägt dieser Slice`) und `slice-qa-sicherheit` „(geliefert)“ nennt, obwohl der Slice noch in `in-progress/` liegt. | genannte Dateien |
| L-4 | LOW | Das vierte Risiko in §6 trägt den Ausgang „weiter offen“ mit dem Zusatz „(Nutzerfrage im Bericht der Fix-Runde)“. Die Verlagerung der Gesamtinspektion auf einen offenen Slice hat der Implementer vorgenommen; die Frage an den Nutzer steht in einem Bericht, den das Repo nicht trägt. Vor `done/` braucht der Ausgang einen im Repo auflösbaren Anker (die Bestätigung des Nutzers oder den Verweis auf die Stelle, an der die Entscheidung steht). | `slice-qa-sicherheit.md` §6, vierter Punkt |
| I-1 | INFO | Closure-Pflichten stehen aus: Haken Review (Report liegt vor), Ausgänge, Paarungen, Closure-Notiz mit Lerneintrag. Die Closure-Notiz sollte die Messbedingungen der Zahlen tragen (Docker-Image des Laufs, Host des Laufs: 42 bis 64 ms je nach Last gemessen, im Register 57 ms; das Verhältnis, nicht der Betrag, ist die Aussage) und die Mutation M6. | §7 des Slice |

## Ende

`make docs-check` auf dem Stand dieses Berichts: grün, `d-check: 460 Datei(en) geprüft, 0 Befund(e)`.
