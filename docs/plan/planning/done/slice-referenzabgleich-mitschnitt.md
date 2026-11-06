# Slice slice-referenzabgleich-mitschnitt: Referenzabgleich, Mitschnitt-Lauf: Paketmitschnitte der Referenzinstanz gegen den Server abspielen und feldweise vergleichen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)

**Berührte Spec-Stellen:** SPEC-004 · SPEC-020 · SPEC-030 · SPEC-031 · SPEC-045 · SPEC-050

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Redigierte Paketmitschnitte des Referenzclients gegen die Referenzinstanz werden gegen einen frisch gestarteten Server abgespielt und feldweise verglichen (Nachrichten- und Tokenfolge, COLMETADATA, Zeilenwerte und -zahl, DONE-Status, ERROR, ENVCHANGE); dieser Lauf hält die Messung der Tokenreihenfolge, der DONE-Statusbits und der RETURNVALUE-Zählung am Wire wiederholbar (die einmalige Messung steht in [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Live-Lauf — [slice-referenzabgleich-live](../done/slice-referenzabgleich-live.md); ein Mitschnitt spielt die Anfragen des Clients ab, nicht den Client (SPEC-045): fragt der Client wegen einer anderen Antwort anders, zeigt das nur der Live-Lauf.
- Referenzinstanz, Container, Make-Target des Vergleichslaufs und EULA — [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md).
- Verschlüsselte Mitschnitte und der TDS-8.0-Pfad — die Referenzinstanz bietet TDS 8.0 nicht an ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)); Mitschnitte entstehen unverschlüsselt (`Encrypt=false`), der verschlüsselte Pfad bleibt ohne Wire-Abgleich und steht so in der Closure.
- Fuzz und Fragmentierung — [slice-qa-stabilitaet](../done/slice-qa-stabilitaet.md); der Mitschnitt spielt gültige Nachrichten ab.
- Das Beheben gefundener Abweichungen — je Befund ein Slice nach der Messung.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Aufnahme:** Redigierte Mitschnitte ohne Secrets für die Szenarien aus SPEC-045 (Batch, RPC mit Ausgabewert, mehrteiliges Ergebnis, Fehler, Cancel; Cancel-Szenarien zusätzlich nach dem Anschluss in §4), aufgenommen mit einem mitschneidenden Proxy (Setzung des Planners, in §7 vom Nutzer nicht bestätigt), abgelegt mit Herkunft (Version der Instanz, Image-Digest, Client-Version, Schema-Stand); ein Test belegt, dass kein Passwort und kein Prüfwert in den Ablagen steht, mit Eingaben, die beides enthalten. **Lieferumfang gegenüber SPEC-045:** das Szenario *Anmeldung* ist nicht aufgenommen — bei `Encrypt=false` laufen PRELOGIN-Vorspann und LOGIN7 verschlüsselt und gelangen nicht in die Ablage (Beleg: Pakettypen der 34 Dateien nur Batch, RPC, ATTENTION); die Anmeldung trägt der Live-Lauf ([slice-referenzabgleich-live](../done/slice-referenzabgleich-live.md)). Folge: ENVCHANGE nach der Anmeldung steht in keinem Szenario des Mitschnitts. Die Lücke steht im Register (`BEO-TDS/anmeldung-am-draht-nicht-aufgenommen`) mit Träger-Vorschlag.
- [x] **Abspielen und Vergleich:** Die Mitschnitte laufen gegen einen frisch gestarteten Server; der Vergleich ist feldweise nach SPEC-045 (Nachrichten- und Tokenfolge, COLMETADATA mit Typ, Länge, Precision, Scale und Nullbarkeit, Zeilenwerte und -zahl, DONE-Status und Zählung, ERROR mit Nummer, State, Class und Text, ENVCHANGE); vom Server selbst vergebene Werte nur auf vorhanden oder nicht vorhanden.
- [x] **Ausnahmeliste und Messaussage:** Die Ausnahmeliste im Format und im Nummernraum des Live-Laufs (Art *so gewollt* oder *Lücke*; die Prosa-Liste A-n in `docs/messungen/referenzabgleich.md` ist eine, geteilt sind A-2, A-3, A-6); maschinenlesbar zwei Dateien, `live-ausnahmen.tsv` (Schlüssel: Schritt) und `mitschnitt-ausnahmen.tsv` (Schlüssel: Szenario/NN/Feld), weil die Schlüsselräume verschieden sind (Setzung des Implementers, vom Planner hingenommen: `SPEC-045` verlangt eine Liste, nicht eine Datei); ein Sensor für den Gleichlauf der geteilten Nummern fehlt (`BEO-TDS/zwei-ausnahmelisten-ohne-gleichlauf-sensor`); die Aussagen des Codecs und der ADRs zu Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung (bisher Handvektor und Festlegung) sind je Punkt von *Festlegung* auf *gemessen* gehoben oder als Abweichung geführt, soweit [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) sie nicht schon einmalig gemessen hat (dort ist der Wire-Teil seit der Planung vom 2026-11-01 verankert); dieser Slice wiederholt die Aussagen als abspielbaren Vergleich gegen den Server und führt Abweichungen in der Ausnahmeliste des Mitschnitt-Laufs.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt den Tag `@spec SPEC-045` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests sind grün. **Lieferumfang gegenüber der Konvention:** die Tests sind C#; der Tag steht dort als Kommentar `// @spec SPEC-045` über der Testfunktion, nicht als KDoc-Block-Tag, und `make spec-abdeckung` liest nur Kotlin, `SPEC-045` bleibt in `docs/plan/spec-abdeckung.md` ohne Nachweis. Die Tests laufen in `make referenztest-dotnet` (Klasse `tests` in `make gates`), nicht in `make test`. Der Mangel ist Schuld des Werkzeugs und steht im Register (`BEO-TDS/spec-abdeckung-liest-nur-kotlin`).
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Aufnahme- und Abspielwerkzeug <!-- d-check:ignore (geplant: Ort und Sprache nach Entscheidung) --> | neu | Testwerkzeug in Docker; Ort und Sprache offen (Frage in §6) |
| Mitschnitte <!-- d-check:ignore (geplant: Ablage nach Entscheidung) --> | neu | redigiert, mit Herkunft; Ablage im Repo oder außerhalb offen (Frage in §6) |
| Ausnahmeliste (`referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`, Prosa in `docs/messungen/referenzabgleich.md`) | neu | Format und Nummernraum des Live-Laufs; eigene Datei, weil die Schlüsselräume verschieden sind (§2, Punkt 3) |
| `tools/mk/` | update | Make-Target des Mitschnitt-Laufs; Eintrag in `harness/README.md` §Sensors |
| Tests des Werkzeugs <!-- d-check:ignore (geplant: neue Datei) --> | neu | Redaktion, Vergleich Happy/Boundary/Negative; `@spec`-Tag SPEC-045 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-referenzabgleich-live](../done/slice-referenzabgleich-live.md) liegt in `done/` (Format der Ausnahmeliste, Server-Seite und Schema), und ein Verantwortlicher ist im Kopf eingetragen.

**Anschluss** (Nutzerentscheid 2026-11-06, aus [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md)): Dieser Slice trägt die Lücke, die jener Slice beim Cancel-Teil von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) ungemessen lässt, damit sie nicht an keiner Datei hängt. Zu messen sind am Draht des Referenzclients (aufgenommen gegen die Referenzinstanz, abgespielt gegen den Server): (a) „N ATTENTION ergeben N Bestätigungen“, also ob der Client bei `Cancel()` und Command-Timeout kurz hintereinander zwei ATTENTION sendet und das zweite `DONE[ATTN]` der Folgeabfrage zuordnet, und (c) ATTENTION im Leerlauf. Beide gehören zu den Cancel-Szenarien der Aufnahme (§2, erster Punkt), kein vierter Liefer-Punkt. Ist (c) über die Client-API nicht auslösbar (Annahme, nicht gemessen), steht das in der Closure-Notiz als Ergebnis, und die Lücke bleibt in Zeile 12 der Kompatibilitätsmatrix benannt. Weicht die Messung von der Setzung „der Server bestätigt jedes ATTENTION einzeln“ ab, ist das ein Re-Evaluierungs-Fall von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md). Start-Trigger: unverändert (siehe **Start**); der Anschluss ändert ihn nicht.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder das Werkzeug sprengt den Rahmen; Schnittvorschlag: Aufnahme und Abspielen als zwei Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Aufnahme ist mit Docker-only und ohne Mount nicht möglich (Rechte für Paketaufnahme) — der Nutzer entscheidet über eine Ausnahme als Carveout.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt für Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung je ein Ergebnis (gemessen mit Beleg oder Abweichung), die Grenzen (eine Instanz, TDS 7.4 unverschlüsselt) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Doppelte Zuständigkeit: Tokenreihenfolge, Statusbits und RETURNVALUE-Zählung am Wire misst die einmalige Messreihe von [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (mit mitschneidendem Proxy, Planung vom 2026-11-01, umgekehrt zur früheren Aufteilung, nach der dieser Slice sie trug und [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) gekürzt war). Die Client-API zeigt sie nach ihrer Gestalt nicht (Annahme, nicht gemessen). Dieser Slice nutzt Proxy, Mitschnitte und Messzeilen als Eingang, spielt sie ab und vergleicht; die Aufnahme-Methode (unten) ist Start-Trigger jenes Slice. Setzung des Planners, nicht vom Nutzer bestätigt. — **Ausgang:** eingetreten (Überschneidung: Proxy, Mitschnitte und die Messpunkte Tokenreihenfolge, DONE-Statusbits, RETURNVALUE-Zählung, Ordinal, ENVCHANGE nach der Anmeldung, ATTENTION und Cancel-Antwort sind in diesem Slice gemessen, `docs/messungen/referenzabgleich.md` Ergebnis 1 bis 6; [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) behält Fehler in `sp_executesql`, ENVCHANGE bei `RESETCONNECTION`, Anmeldung am Draht und den Rest; sein Start-Trigger ist nachgeführt, die Kürzung seiner DoD ist Sache des Nutzers).
- Aufnahme-Methode (Frage in §6): ein Proxy zwischen Client und Instanz, ein Mitschnitt auf Netzwerkebene oder ein Log der Instanz; jede Methode braucht Docker-only, kein Mount und Basis-Images per Digest, und eine zusätzliche Komponente im Netzwerk kann Pakete verändern. — **Ausgang:** weiter offen (gewählt ist der mitschneidende Proxy im Container des Referenzclients, Setzung des Planners; er reicht durch und liest mit, die Neuaufnahme 2025 war bis auf eine zeitabhängige Zeile byte-gleich zur abgelegten, aber eine Veränderung der Pakete durch den Proxy ist nicht gegen eine zweite Methode gemessen; die Bestätigung der Methode durch den Nutzer fehlt).
- Abspielwerkzeug (Frage in §6): Ort und Sprache (Test-Quelltext der JVM, Hilfsprojekt im .NET-Gerüst) sind offen; es ist Testcode ohne Wirkung auf den Produktivpfad, aber Eigencode, der Vergleiche entscheidet. — **Ausgang:** weiter offen (Ort und Sprache entschieden: .NET-Gerüst, Eigencode im Test-Stage ohne neue Abhängigkeit, `--network=none` bleibt; 28 Tests frisch grün; offen bleibt, ob der Vergleich Abweichungen in verschiedenen Feldern fängt — keine Mutation am Vergleich gefahren, Verifikation V-5, Register `BEO-TDS/strukturtest-ohne-variation-der-eingabe`).
- Felder, die die Gegenstelle selbst vergibt (Paket-ID, Sitzungs-ID, Zeitstempel, Collation-Bytes, ENVCHANGE-Werte, Versionstext), dürfen den Vergleich nicht kippen; die Liste dieser Felder ist eine Setzung (SPEC-045) und steht mit Begründung in der Ausnahmeliste. — **Ausgang:** entfallen (Servername, Handle und ENVCHANGE-Werte gehen nur als vorhanden oder leer in den Vergleich; 324 Felder je Teillauf grün; Verifikation hat Servername in der Ablage als `xxxxxxxxxxxx` geprüft).
- Mitschnitte hängen am Schema-Stand der Instanz: eine Schemaänderung macht sie ungültig, und die Neuaufnahme gehört in denselben Schritt wie die Änderung (SPEC-045). — **Ausgang:** weiter offen (der Hash des Testschemas steht im Kopf der Aufnahme; das Abspielen vergleicht ihn nicht mit `testschema.sql`, Grep in `MitschnittLaufTests.cs`; Register `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`).
- Größe und Ablage der Mitschnitte (Frage in §6): im Repo wachsen sie mit den Szenarien, außerhalb sind sie nicht reproduzierbar; Mitschnitte enthalten nur Testdaten und keine Secrets. — **Ausgang:** entfallen (Ablage im Repo: 34 Dateien, 72820 Bytes, je Datei höchstens 256 KiB per Test, `make docs-check` grün; keine Secrets, Redaktion an den Dateien von Review und Verifikation geprüft, Servername redigiert).
- Anschluss-Lücke aus [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md): (a) und (c) des Cancel-Teils von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) sind am Referenzclient nicht gemessen, bis dieser Slice schließt; die Messung hängt an der Aufnahme-Methode (oben) und daran, dass der Client die Fälle über seine API auslöst. — **Ausgang:** entfallen (gemessen, Ergebnis 5 im Messprotokoll: (a) bei `Cancel()` und Frist im Abstand von 100 ms sendet der Client ein ATTENTION; (c) `Cancel()` an einem beendeten oder nicht gestarteten Befehl sendet keines; die Setzung „der Server bestätigt jedes ATTENTION einzeln“ ist nicht widerlegt, der Re-Evaluierungs-Trigger von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) ist nicht eingetreten; die zweiteilige Antwort des echten Servers ist als Lücke A-13 geführt. Rest in Zeile 12 der Kompatibilitätsmatrix: Abstände unter 100 ms, andere Wege als `Cancel()`).
- Die Aussage „gleich wie die Referenz“ gilt nur für die aufgenommenen Szenarien einer Instanz in einem Lauf, nicht allgemein (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** weiter offen (dauerhafte Grenze: 17 Szenarien, zwei Instanzen, TDS 7.4 unverschlüsselt, `ConnectRetryCount=1`; Register `BEO-TDS/aussagen-ueber-messung-hinaus`).

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

- **Belege:** Review [2026-11-06-01b5c7eb-slice-referenzabgleich-mitschnitt](../../../reviews/2026-11-06-01b5c7eb-slice-referenzabgleich-mitschnitt.md) (0 HIGH, 4 MEDIUM, 2 LOW, 6 INFO; F-1 bis F-12), Verifikation [2026-11-06-c712427a-verifikation-slice-referenzabgleich-mitschnitt](../../../reviews/2026-11-06-c712427a-verifikation-slice-referenzabgleich-mitschnitt.md) (DoD-konform, soweit der Wortlaut erfüllbar war; Plan-Befunde P-1 bis P-4, Restvermerke V-1 bis V-5). Messprotokoll, Ergebnis 1 bis 6 und Ausnahmeliste A-10 bis A-18: [referenzabgleich](../../../messungen/referenzabgleich.md). Bezug: [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md). Bedingung der Läufe: die EULA-Zustimmung des Nutzers pt9912 für das gepinnte Developer-Image (2026-11-01, 2026-11-06), lokal; keine Rechtsaussage des Repos. Messbedingung: `ConnectRetryCount=1` (Standard des Clients, Nutzerentscheidung 2026-11-06, Empfehlung übernommen).
- **Was hat funktioniert:** Proxy, Redaktion und Abspieler laufen im .NET-Gerüst ohne neue Abhängigkeit; 17 Szenarien je Instanz (2022, 2025) sind aufgenommen und gegen den frisch gestarteten Server abgespielt: 324 Felder je Teillauf, 515 gleich und 133 gleich der gelisteten Abweichung, nachgezählt von Verifikation (648 = 2 x 324). Die Neuaufnahme 2025 war bis auf eine zeitabhängige Zeile byte-gleich zur Ablage. Die Messung hat die Setzungen zu Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung geprüft und vier davon widerlegt (siehe unten).
- **Was ging anders als geplant:** (1) Die Anmeldung ist nicht aufgenommen (bauartbedingt, §2 Punkt 1, P-1). (2) Der Tag `@spec` steht in C# als Kommentar, `make spec-abdeckung` sieht ihn nicht (§2, P-2). (3) Zwei Ausnahmelisten-Dateien statt einer (§2 Punkt 3, P-3). (4) Das Review fand eine Messbedingung neben dem Standard des Clients (`ConnectRetryCount=0` statt 1, F-1): mit 1 steht auf 2022 ein SESSIONSTATE-Token, auf 2025 nicht (A-18); die Aufnahme ist mit 1 wiederholt. (5) Zwei Arten *so gewollt* trugen keinen Beleg (A-13, A-14, F-2) und stehen als *Lücke*. (6) Der Servername der Instanz stand im ERROR-Token von 8 Dateien (F-5) und wird beim Ablegen redigiert, mit Einheitstest.
- **Frischer Lauf der Closure (am Stand dieser Closure-Commits):** `docker build --no-cache-filter test --target test referenztest-dotnet`: 28 Tests grün (Frischbeleg, `make referenztest-dotnet` allein zeigt `CACHED`); `make vergleichslauf-d LAUF=mitschnitt` und `LAUF=aufnahme-2025` durch die Verifikation (Exit 0). `make docs-check` und `make gates`: siehe Commit-Reihe der Closure.
- **Grenze der Aussage „grün“:** Der Lauf ist grün, wenn jede Abweichung mit der Liste übereinstimmt, nicht wenn der Server dem Zielbild entspricht. 133 von 648 Feldern weichen ab (2022: 69, 2025: 64): 1 Regel *so gewollt* (A-6), 73 *Lücke*. Gemessen: 17 Szenarien, zwei Instanzen, ein Lauf je Instanz und Szenario, ein Host, TDS 7.4 mit `Encrypt=false` (Anmeldung verschlüsselt und nicht in der Ablage), Abspielen ohne TLS; abgespielt werden die Anfragen des Clients, nicht der Client; ATTENTION wird nach Paketzahl nachgebildet, nicht nach Zeit; keine Folgeanfrage mit Handle, keine Transaktion. Mehrere ATTENTION je Anfrage sind am Server dieses Repos nicht abgespielt.
- **Messaussagen (Pflicht der Closure: Tokenreihenfolge, DONE-Statusbits, RETURNVALUE-Zählung je ein Ergebnis):** Tokenreihenfolge: gemessen, RETURNSTATUS vor RETURNVALUE und beide vor DONEPROC, RETURNSTATUS auch bei `sp_prepexec` und `sp_executesql` ohne Ausgabewert (Wert 0); COLMETADATA hinter RETURNVALUE und Fehler im RPC nicht gemessen. DONE-Statusbits: gemessen, `COUNT` auch bei 0 Zeilen, `MORE|COUNT` im ersten DONE einer Mehrfachantwort, `MORE|ERROR` bei statement-abbrechendem Fehler im Batch (Abweichung vom Sequenzierer). RETURNVALUE-Zählung: gemessen, Ordinal 2 für den dritten Parameter von `sp_executesql`, Ordinal 0 für das Handle von `sp_prepexec`; ein Fall je Instanz (Abweichung von der Setzung der Spezifikation).
- **Zuordnung, Stand der Closure:**

  | Aussage | Zuordnung | Wert und Grenze |
  |---|---|---|
  | 324 Felder je Teillauf, 515 gleich, 133 gleich der Liste | gemessen (Referenzclient gegen Server dieses Repos, abgespielter Mitschnitt von 2022 und 2025) | Einzelmessung 2026-11-06; Implementer, Reviewer, Verifier |
  | Tokenfolgen, DONE-Bits, Ordinal, RETURNSTATUS | gemessen am Draht der Instanz | 17 Szenarien, ein Lauf je Instanz; Ordinal und Bits bei statement-abbrechendem Fehler je ein Fall |
  | Server weicht ab (A-10 bis A-18, 73 Regeln) | gemessen, Schuld des Servers am Zielbild | Lücke; Träger siehe Folge-Slices und Register |
  | A-6 Text des Quellfehlers | Festlegung | SPEC-012, `TDS_BACKEND_ERROR` |
  | ATTENTION: ein ATTENTION bei `Cancel()` und Frist im Abstand von 100 ms; keines bei `Cancel()` im Leerlauf | gemessen | vier Aufnahmen bzw. je ein Lauf je Instanz; andere Wege, Abstände unter 100 ms nicht |
  | SESSIONSTATE nur auf 2022 bei `ConnectRetryCount=1` | gemessen | ein Lauf je Einstellung und Instanz; Ursache in der Anmeldung nicht untersucht |
  | Kein Passwort, kein Prüfwert, kein Servername in der Ablage | Test (Eingaben mit beidem, Findbarkeit im Rohstrom belegt) und Prüfung am Dateiinhalt | Review und Verifikation; die Redaktion sucht den bekannten Wert, keine anderen Kennungen |
  | `ConnectRetryCount=1` ist der Standard des Clients | Festlegung (Matrix, Setzung des Nutzers) | nicht vom Lauf erhoben (Verifikation) |
  | Aufnahme-Methode Proxy | Festlegung (Setzung des Planners) | vom Nutzer nicht bestätigt |
  | Anmeldung und ENVCHANGE nach der Anmeldung | nicht gemessen | Lücke, Register `BEO-TDS/anmeldung-am-draht-nicht-aufgenommen` |
  | TDS 8.0, Pooling, `RESETCONNECTION` | nicht gemessen | [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) `Proposed`; Träger [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md) |

- **Vier offene Konflikte mit dem Zielbild (Nutzerentscheidungen, nicht entschieden).** Maßstab des Nutzers (2026-11-06): die Spezifikation ist Zielbild, der echte SQL Server die Referenz. Der Planner entscheidet keinen davon; er empfiehlt. Jeder ist als Beobachtung geführt (`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`, für die Statusbits `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`).
  1. **Ordinal der RETURNVALUE** (Spec: nach der Reihenfolge der Ereignisse; gemessen: Position in der Aufrufliste, Ordinal 2). Empfehlung: Spec dem echten Server angleichen; vor dem Bau ein zweiter Fall mit mehreren Ausgabewerten (Messauftrag für [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md)), weil bisher ein Fall je Instanz vorliegt. **Entscheidung beim Nutzer.**
  2. **`curCmd` der DONE-Familie** (Spec: 0; gemessen: `0x00c1`, `0x00fd`, `0x00e0`, nie 0; A-10, 23 Regeln). Vorschlag: Spec und Port angleichen (der Client liest den Wert nicht, der Live-Lauf ist grün). **Entscheidung beim Nutzer.**
  3. **`MORE` bei statement-abbrechendem Fehler** (Spec: Variante ohne `MORE`; gemessen: `MORE|ERROR`, die zweite Anweisung läuft). Vorschlag: Spec angleichen, `Failure` um die Klasse erweitern; der Träger der Messung ist entschieden, der Bau ist Folge-Slice. **Entscheidung beim Nutzer.**
  4. **RETURNSTATUS-Regel** (Spec: ohne `ReturnStatus` des Backends entsteht keiner; gemessen: RETURNSTATUS 0 bei jedem gemessenen RPC). Vorschlag: der Sequenzierer schreibt RETURNSTATUS 0, wenn das Backend keinen nennt; verwandt mit `BEO-TDS/testserver-sendet-bei-sp-prepexec-kein-returnstatus`. **Entscheidung beim Nutzer.**
  Eine ADR ist davon nicht berührt; berührt sind `SPEC-030`, `SPEC-031`, `SPEC-036` und die Sequenzer-Festlegung. Gemessen aufgenommen und im Messprotokoll geführt sind außerdem die Cancel-Antwort (zwei Nachrichten, die erste `DONE[ERROR]`, kein ERROR-Token bei wartendem Befehl; der Text „A severe error …“ kommt dort vom Client; Register `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab`) und ATTENTION, dazu die Redaktion (Kennwort, Prüfwert, Servername im ERROR-Token).
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Gelernt und gezählt, nicht verkörpert: (1) **Messbedingung ≠ Standard des Gegenübers:** eine Aufnahme mit abweichender Einstellung (`ConnectRetryCount=0`) zeigte einen anderen Draht als der Standard (F-1); jede Messaussage nennt die Bedingung und, wo sie vom Standard abweicht, die Gegenmessung (`BEO-TDS/aussagen-ueber-messung-hinaus`, Evidence ergänzt, zusammen mit F-2 *so gewollt* ohne Beleg und F-3 „einzeln“ über dem Beleg). (2) **Fortschreibung lässt die zweite Fundstelle stehen:** zwei Vermerke „nicht gemessen“ standen nach der Nachbesserung noch (F-4, V-1; Matrix Zeile 275, Spec Zeile 1224; `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, Evidence ergänzt; das Review (F-4) benannte die Stellen in Spec, Matrix und beiden `state.md`, die Nachbesserung zog sie bis auf diese zwei nach). (3) **DoD-Wortlaut über der Bauart:** zwei Wortlaute der DoD waren am Entwurfstisch nicht erfüllbar (Szenario *Anmeldung* bei verschlüsselter Anmeldung; KDoc-Tag in C#), ein dritter beschrieb eine Struktur (eine Datei), die der Schlüsselraum nicht trägt; sie fielen bei Review und Verifikation auf, nicht bei der Planung (`BEO-TDS/dod-wortlaut-ueber-der-bauart`, neu, 1×; verwandt `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt`, verkörpert in `.claude/commands/implement-slice.md` Schritt 22, dessen Regel „Planner ändert den Wortlaut vor der Verifikation“ hier nicht griff: die Abweichungen fielen im Review auf, F-10 und F-11, und der Wortlaut blieb bis zu dieser Closure unverändert). (4) **Lücke ohne bauenden Träger:** vier Nummern der Liste (A-10, A-11, A-15, A-18) hingen an keiner offenen Arbeit (F-7, P-4; `BEO-TDS/mitschnitt-luecken-ohne-traeger`, mit Träger-Vorschlägen). (5) **Kopplung eines Laufs außerhalb der Gates:** der Abspiel-Lauf hängt an Namen im Dockerfile (F-6), das Schema-Hash wird nicht verglichen (`BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`, Evidence ergänzt). (6) **Werkzeug sieht eine Sprache:** `make spec-abdeckung` liest nur Kotlin (F-10, P-2; `BEO-TDS/spec-abdeckung-liest-nur-kotlin`, neu). Als **benannte Spec-Lücke** bleibt: `SPEC-045` nennt beim Mitschnitt-Lauf ENVCHANGE und keine Grenze dafür, dass die verschlüsselte Anmeldung am Draht bei `Encrypt=false` nicht in der Ablage steht; `SPEC-045` sagt weiter „eine Instanz der Version 2022“ (Nachzug: [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md)). **Schwellen-Übertritte (Lese-Schritt):** kein Eintrag erreicht durch diese Closure neu 3×; zwei Zählungen stehen schon über der Schwelle und sind verkörpert (`aussagen-ueber-messung-hinaus`, `fortschreibung-laesst-zweite-fundstelle-stehen`). **Vorschlag, nicht verkörpert:** die Familie der DoD-Wortlaut-Beobachtungen (`dod-wortlaut-ueber-der-bauart`, `dod-nennt-pflichtfeld-das-die-spezifikation-als-zielzustand-fuehrt`, `dod-nennt-nutzerentscheidung-als-lieferinhalt`, `folgepflicht-in-dod-ohne-messweg`, `dod-haengt-an-offenem-slice-ohne-rueckfuehrung` 3×, `dod-satz-verlangt-ablage-die-der-slice-ersetzt` verkörpert) als eine Planungs-Prüfung „jeden Lieferpunkt gegen Bauart und Werkzeug lesen: kann das Werkzeug ihn sehen, kann die Bauart ihn liefern?“ in `/plan-welle` aufnehmen; Entscheidung beim Nutzer.
- **Nachtrag Steering-Loop-Eintrag (2026-11-06, Entscheidung des Nutzers „deine empfehlungen“):** Punkt (3) darunter ist entschieden: [BEO-TDS/dod-wortlaut-ueber-der-bauart](../observations/BEO-TDS/dod-wortlaut-ueber-der-bauart/observation.md) und die verwandte Klasse [BEO-TDS/dod-nennt-nutzerentscheidung-als-lieferinhalt](../observations/BEO-TDS/dod-nennt-nutzerentscheidung-als-lieferinhalt/observation.md) sind als Planner-Schritt 6b verkörpert: jeden Lieferpunkt der DoD gegen Bauart und Werkzeug lesen (sieht das Werkzeug den Punkt, kann die Bauart ihn liefern) und prüfen, ob er eine Entscheidung des Nutzers als Lieferinhalt nennt. Slice-Beleg: P-1, P-2, P-3 (Bauart) und F-10, F-11. Die Ausdehnung von `.claude/commands/implement-slice.md` Schritt 22 auf Befunde des Reviews ist nicht entschieden und nicht geschrieben. liegt in `.claude/commands/plan-welle.md`
- **Beobachtungs-Register (`../observations/`):** Neu angelegt, je `offen`, je mit `evidence/slice-referenzabgleich-mitschnitt.md`: `BEO-TDS/anmeldung-am-draht-nicht-aufgenommen`, `BEO-TDS/spec-abdeckung-liest-nur-kotlin`, `BEO-TDS/zwei-ausnahmelisten-ohne-gleichlauf-sensor`, `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert`, `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`, `BEO-TDS/dod-wortlaut-ueber-der-bauart`. `evidence/slice-referenzabgleich-mitschnitt.md` ergänzt in `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`, `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab`, `BEO-TDS/haken-vor-verifikation-gesetzt` (V-2), `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (V-3), `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` (F-9), `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (V-5), `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (F-6, Schema-Hash). `BEO-TDS/mitschnitt-luecken-ohne-traeger` trägt die Träger-Vorschläge für A-10, A-11, A-15, A-18 in `state.md`. Die Zähler folgen aus den Dateien; nichts verkörpert, kein Stand `verkörpert` oder `geplant` gesetzt.
- **Folge-Slices:** keine neuen angelegt. Vorgeschlagen, ohne Kennung und nicht angelegt (Bestätigung durch den Nutzer): ein Bau-Slice „Antwort-Tokens an die Messung angleichen“ (Ordinal, `curCmd`, `MORE`, RETURNSTATUS; nach den Entscheidungen 1 bis 4), die Aufnahme von A-11 in [slice-codec-datentypen-erweiterung](../open/slice-codec-datentypen-erweiterung.md), und die Anmeldung am Draht mit TLS-beendendem Proxy samt SESSIONSTATE-Ursache (A-18) in [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md); DoD jener Slices nicht geändert. Start-Trigger nachgeführt: [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (Wahl der Aufnahme-Methode: Proxy gebaut, Bestätigung durch den Nutzer fehlt). Weiter: [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md), [slice-release-entscheidungen](../open/slice-release-entscheidungen.md).
- **Risiken aus §6:** je ein Ausgang, siehe §6 (acht Risiken: eines eingetreten, drei entfallen, vier weiter offen mit Register-Verweis oder Nutzerfrage).
- **Offene Entscheidungen beim Nutzer:** die vier Konflikte mit dem Zielbild (Ordinal, `curCmd`, `MORE`, RETURNSTATUS-Regel); Bestätigung der Aufnahme-Methode Proxy; Hinnahme von zwei Ausnahmelisten-Dateien (P-3, Planner hat hingenommen, `SPEC-045` nicht berührt); Träger-Vorschläge im Register (P-4: A-10, A-11, A-15, A-18; P-1: Anmeldung am Draht); ob `make spec-abdeckung` C# lesen soll oder die Logik nach Kotlin wandert (P-2); Kürzung der DoD von [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md), dessen Messpunkte 1 bis 6 hier schon gemessen sind; die DoD-Prüfung im Vorschlag oben.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-referenzabgleich-release](../welle-referenzabgleich-release.md). Anker ↔ Folge-Slice: kein Anker gesetzt; die genannten Folge-Slices sind Dateien in `open/`. Register ↔ Closure-Notiz: alle genannten Kennungen existieren als Verzeichnis mit nicht leerem `evidence/`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt Testwerkzeug, Docker-Stages und Dokumentation; keine dieser Strukturen trägt eine eigene Strukturregel (Achse 1) oder eine eigene Inventur-Linie (Achse 2) neben dem Repo insgesamt: eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md)). Die einmalige Wire-Messung (Tokenreihenfolge, DONE-Statusbits, RETURNVALUE-Zählung) trägt [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md); dieser Slice trägt die Wiederholung als abspielbaren Vergleich (§6, Überschneidung).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 7×, Stand *geplant* (Träger [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md)). Betrifft die Grenze: kein Wire-Abgleich für TDS 8.0 und TLS.
- `BEO-TDS/vergleichslauf-d-ungeklaert` — 1×, offen (Träger [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md)). Betrifft Ablage, Häufigkeit und Machbarkeit in CI: Träger bleibt [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md).
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, Stand *geplant* (Träger [slice-qa-sicherheit](../done/slice-qa-sicherheit.md)). Betrifft die Redaktion der Mitschnitte: Passwort und Prüfwert erscheinen nirgends in der Ablage (§2).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über die Wire-Gleichheit nennt Instanz, Version und Szenario (§6).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft den Vergleichstest: Abweichungen in verschiedenen Feldern statt in einem (§2).
- `BEO-TDS/ablage-im-repo-bricht-docs-check` — 1×, offen. Betrifft Aufnahme und Ablage: Ausgaben außerhalb des Repos (`mktemp -d`, `trap`), wenn das Rezept sie sammelt.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
