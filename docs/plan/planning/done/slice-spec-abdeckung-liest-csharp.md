# Slice slice-spec-abdeckung-liest-csharp: `make spec-abdeckung` liest `@spec`-Tags in den C#-Tests des Referenzclients

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-045 (der Nachweis, der heute fehlt) · SPEC-050 (Festlegungen der Harness-Werkzeuge, Abschnitt 7 der Spezifikation, soweit sie den Erzeuger der Abdeckungsmatrix beschreiben; ob der Abschnitt den `@spec`-Tag und seine Leseorte nennt, prüft der Implementer und zieht ihn nach)

**Verantwortlich:** pt9912.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `make spec-abdeckung` zählt einen bestandenen C#-Test des Referenzclients als Nachweis, wenn er die Festlegung mit dem Kommentar-Tag `// @spec SPEC-<NNN>` nennt; `SPEC-045` steht danach in `docs/plan/spec-abdeckung.md` mit seinen Nachweisen statt als Waise, und der Selbsttest des Erzeugers belegt, dass ein roter, nicht gelaufener oder unbekannt getaggter C#-Test nicht zählt.

**Herkunft und Nutzerentscheidung.** Die 20 Einheitstests des Mitschnitt-Werkzeugs (`referenztest-dotnet/Mitschnitt/MitschnittEinheitTests.cs`) tragen `// @spec SPEC-045` und laufen grün in `make referenztest-dotnet` (Dockerfile-Stage `test`); `tools/spec-abdeckung.sh` liest nur den KDoc-Block-Tag in Kotlin und führt nur Gradle-Berichte, deshalb zeigt die Matrix `SPEC-045` mit null Nachweisen (`BEO-TDS/spec-abdeckung-liest-nur-kotlin`, Verwandte: `BEO-TDS/dod-wortlaut-ueber-der-bauart`). **Nutzerentscheidung vom 2026-11-06** („deine empfehlungen“): das Werkzeug wird erweitert; die Alternative (die Logik der Vergleichstests nach Kotlin verlegen) ist verworfen, weil sie den Zuschnitt von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) ändern würde, der den Referenzclient im .NET-Gerüst führt.

**Die Leseregel bleibt die der Tests, die schon da sind.** Der Tag steht als Kommentarzeile `// @spec SPEC-<NNN> [SPEC-<NNN> ...]` über der Testmethode (Setzung des Planners: so tragen ihn die bestehenden 22 Stellen; eine andere Form hieße, sie umzuschreiben), Kennungen durch Leerzeichen getrennt, mehrere Zeilen addieren sich. Welche Methode der Tag erreicht (Attribut `[Fact]`, `[Theory]`, weitere Attribute dazwischen), legt der Implementer an den bestehenden Dateien fest und beschreibt es im Kopf des Erzeugers wie die Kotlin-Regel dort. Das Urteil bleibt „mindestens ein bestandener getaggter Test“; der Bericht ist der Lauf der Stage `test-bericht` des .NET-Gerüsts (Planer-Wortlaut war „Stage `test`“; geliefert ist die Schwesterstage `test-bericht` der Basis `test-quellen`, siehe §7 Abweichung vom Wortlaut).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die beiden getaggten Tests im Lauf `LAUF=mitschnitt` (`referenztest-dotnet/Vergleich/MitschnittLaufTests.cs`, Kategorie `MitschnittAbspielen`) — sie laufen nur lokal in `make vergleichslauf-d` (Server-Container, EULA-Entscheidung des Nutzers), nicht in der Stage `test`; der Erzeuger sieht sie nicht in seinem Bericht und führt sie als „nicht gelaufen“, was wahr ist. Nutzerentscheidung vom 2026-11-06 („deine Empfehlungen“): die zwei lokalen C#-Tests gehen nicht in `spec-abdeckung` ein; Frage 1 in §6 ist damit beantwortet.
- Ein neues Paket (JUnit-Logger oder anderes) für die Berichte des .NET-Laufs — jedes NuGet-Paket geht durch Sperre, Signaturliste und Audit-Verzicht ([ADR-0035](../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md)); der eingebaute TRX-Logger von `dotnet test` braucht keines. Wählt der Implementer anders, ist es eine Entscheidung mit ADR-Bezug und kein Detail.
- Die Abdeckung der C#-Tests durch `make test` oder die Gate-Kette — `make spec-abdeckung` ist kein Gate (Entscheidung des Nutzers vom 2026-10-30, Kopf von `tools/mk/spec-abdeckung.mk`); dieser Slice macht daraus kein Gate.
- Das Nachtaggen weiterer C#-Tests mit anderen Festlegungen als `SPEC-045` — welche Festlegung ein Test belegt, urteilt der Autor des Tests; der Slice liefert die Lesbarkeit, nicht neue Tags.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [x] **Der Erzeuger liest C# und den .NET-Bericht:** `tools/spec-abdeckung.sh` liest zusätzlich den Kommentar-Tag in den C#-Quellen des Referenzclients (Eingang `TEST_QUELLE`-Verwandtschaft: ein eigener Eingang für die C#-Verzeichnisse mit Vorgabe) und den Testbericht der Stage `test-bericht` des .NET-Gerüsts, einer Schwesterstage von `test` (Abweichung vom ursprünglichen Wortlaut „Stage `test`“, siehe §7; ein TRX-Bericht des eingebauten Loggers, aus der Stage per Ausgabe herausgeführt wie Matrix und Waisenzahl heute); ein getaggter C#-Test zählt, wenn Klasse und Methode im Bericht ohne Fehler und ohne Übersprung stehen, sonst steht er in den bestehenden Abschnitten „rot/übersprungen“ und „nicht gelaufen“. `make spec-abdeckung` bleibt kein Gate; die Zeile in `harness/README.md` (Werkzeuge) und der Kopf des Erzeugers nennen den neuen Eingang. Nach dem Lauf führt `docs/plan/spec-abdeckung.md` für `SPEC-045` die bestandenen C#-Tests (Beleg: die Matrix, erzeugt und eingecheckt wie bisher).
- [x] **Selbsttest:** `make spec-abdeckung-selbsttest` erzeugt einen Mini-Bestand mit C#-Quelle und TRX-Bericht und belegt je Fall (Happy: bestandener Test zählt; Negative: roter, nicht gelaufener, übersprungener, unbekannt getaggter C#-Test zählt nicht; Boundary: mehrere Tags und mehrere Zeilen addieren sich, Tag ohne folgende Testmethode, Kotlin- und C#-Nachweis derselben Kennung, Methode mit mehreren Attributen); mindestens eine Mutation des Erzeugers (C#-Eingang entfernt) macht den Selbsttest rot.

Nachweis-Pflichten für beide Punkte:

- [x] Wirkung am echten Bestand: `make spec-abdeckung` vorher und nachher, die Zahl der Nachweise von `SPEC-045` und die Waisenzahl stehen in der Closure-Notiz (vorher null Nachweise, 2026-11-06).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Werkzeuge: Zeilen `make spec-abdeckung` und `make spec-abdeckung-selbsttest`).
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
| `tools/spec-abdeckung.sh` | update | C#-Tag lesen, TRX-Bericht lesen, Kopf mit Eingängen und Grenzen ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)) |
| `tools/spec-abdeckung-selbsttest.sh` | update | Fälle und Mutation für C# (Happy/Boundary/Negative) |
| `Dockerfile` (Stage `spec-abdeckung`, `spec-abdeckung-export`) und `referenztest-dotnet/Dockerfile` (Stagen `test`, `test-quellen`, `test-bericht`; geplant war „Stage `test`“) | update | der Bericht der .NET-Stage gelangt zum Erzeuger; Eingangslisten der Stempel (`tools/stempel/*.eingaenge`) und `make stempel-eingaenge-pruefung` mitziehen, wenn eine `COPY`-Quelle hinzukommt |
| `tools/mk/spec-abdeckung.mk`, `harness/README.md` | update | Aufruf, Zeilen der Werkzeuge |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `SPEC-045` mit Nachweisen |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Die Stage `test` des .NET-Gerüsts schreibt den Bericht bisher nicht heraus (`dotnet test --no-restore --nologo`, ein roter Test bricht den Build); der Implementer wählt den Weg, den Bericht zu erhalten, ohne dass ein roter Test den Bericht verliert (der Bericht eines roten Laufs ist der, der die roten Tests zeigt), und ohne die Gate-Stage `referenztest-dotnet` zu verlangsamen oder zu ändern, die in `make gates` im Gruppenschritt `tests` läuft.
- Vor dem Bau: `BEO-TDS/spec-abdeckung-liest-nur-kotlin` und die Wirkung auf die Eingangslisten der Stempel prüfen ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)); das ist Teil des ersten Laufs, nicht dieses Plans.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): kein Vorgänger-Slice; die Nutzerentscheidung „Werkzeug erweitern“ liegt vor (2026-11-06), `make gates` auf `main` ist grün, und ein Verantwortlicher ist im Kopf eingetragen. Der Slice hat keine Kante zu den Bau-Slices der Antwort-Tokens und kann zu jeder Zeit laufen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Weg des Berichts aus der .NET-Stage in die Stage `spec-abdeckung` verlangt einen Umbau der Eingänge der Stempel, der mehr als Fragmente und Listen berührt; Schnittvorschlag: erst der Erzeuger mit Selbsttest (Punkt 2) gegen einen Bericht aus der Hand, dann die Verdrahtung.
- `in-progress` → `open` (blockiert — Carveout?): die Stage `test-bericht` des .NET-Gerüsts (geplant als „Stage `test`“) liefert ohne neues Paket keinen lesbaren Bericht; der Nutzer entscheidet über ein Paket (Sperre, Signaturliste) oder über eine andere Form.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beide Punkte der DoD in §2 erledigt, `make gates` grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zahlen vorher und nachher für `SPEC-045`, die Grenzen (Bericht der Stage `test-bericht`, nicht der lokalen Läufe; Urteil nur „mindestens ein bestandener getaggter Test“) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Lokale Läufe in der Matrix.** Die zwei getaggten Tests des Abspielens laufen nur lokal und gehen nicht in `spec-abdeckung` ein (Nutzerentscheidung vom 2026-11-06, „deine Empfehlungen“); sie bleiben „nicht gelaufen“, solange der Erzeuger nur den Bericht der Stage `test-bericht` kennt (ein lokaler Bericht aus `make vergleichslauf-d` ist nicht reproduzierbar im Sinne von [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), EULA). — **Ausgang:** entfallen (entschieden).
- **Ein Tag in einem Kommentar sagt nichts über die Methode darunter.** Ein Tag über einer Hilfsmethode oder einer umbenannten Methode würde dem falschen Namen zugerechnet; die Namenszusage gegen den Bericht („nicht gelaufen“) fängt das für fehlende, nicht für vertauschte Namen. — **Ausgang:** weiter offen, mit Grenze: gefangen wird ein fehlender Name („nicht gelaufen“), nicht ein vertauschter; ein Tag über einer anderen vorhandenen und bestandenen Methode zählt für diese (Review F-3, Kopf von `tools/spec-abdeckung.sh`). Ein Sensor liegt nicht vor; kein Träger.
- **Der Bericht der .NET-Stage hängt am Layer-Cache** (`make referenztest-dotnet` übergeht die Stage, wenn nichts Kopiertes geändert ist): die Matrix könnte einen alten Lauf zeigen. Der Implementer legt fest, ob der Erzeuger den Bericht frisch erzwingt (`BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`). — **Ausgang:** teils entfallen, teils weiter offen: der C#-Teil ist frisch erzwungen (`--no-cache-filter test-bericht`, im Bau-Protokoll von Review und Verifikation als ausgeführt gesehen); der Gradle-Teil hängt weiter am Layer-Cache seiner Quellen (Verifikation V-5, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, Handlung aus `implement-slice` Schritt 24, kein Gate-Sensor).

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

Closure am 2026-11-06 (Planner). Zuerst der Stand des Implementers (mit den Berichtigungen aus Review und Verifikation), danach die Closure-Teile.

**Stand des Implementers (2026-11-06).**

- **Wirkung am echten Bestand.** Vorher (eingecheckte Matrix auf `cbe4fb06`, Lauf vom selben Tag): `SPEC-045` mit 0 Nachweisen, 10 Festlegungen ohne Nachweis (`Stand: 42 von 56`). Nachher (`make spec-abdeckung`): `SPEC-045` mit 20 bestandenen Modul-Tests (`MitschnittEinheitTests`), 2 nicht gelaufen (die zwei Tests von `LAUF=mitschnitt`, wie entschieden), 9 ohne Nachweis (`Stand: 43 von 56`). Die übrigen Zeilenänderungen der Matrix stammen von Kotlin-Tests, die seit der letzten Erzeugung auf `main` hinzukamen (`FlaecheEmbeddingTest`, `TelemetryFelderEmbeddingTest`).
- **Setzung Weg des Berichts.** Die Stage `test-bericht` in `referenztest-dotnet/Dockerfile` führt dieselben Tests wie `test` mit dem eingebauten TRX-Logger aus. **Setzung (Review F-1):** `test` und `test-bericht` erben von der Basis `test-quellen`, die `COPY`-Zeilen und die Argumente (`DOTNET_TEST_ARGS`) stehen nur dort; `test-bericht` fügt allein Logger und Ergebnisordner hinzu. **Die Kopplung hält für `COPY` und `DOTNET_TEST_ARGS`, nicht für weitere Flags in der `RUN`-Zeile von `test`** (Verifikation V-2: Mutation 1, ein `--filter` nur dort, blieb grün, die Matrix unverändert; Mutation 2 in der Basis folgte die Matrix). Ein Gleichlauf-Sensor für die `RUN`-Zeile liegt nicht vor und der Slice behauptet keinen. Der Bericht ist der der Stage `test-bericht`, nicht der von `test` selbst (`|| echo`, ein roter Test bricht sie nicht; ein Bau-Fehler lässt `/bericht` fehlen und bricht den Export); `test-bericht-export` gibt `bericht.trx` per `--output` heraus. `make spec-abdeckung` baut das zuerst und reicht das Verzeichnis als benannten Build-Kontext `dotnet-bericht` an die Wurzel-Stage `spec-abdeckung-export` weiter. Das Gate `make referenztest-dotnet` führt dieselben Befehle aus wie zuvor (die Stage `test` erbt jetzt von `test-quellen`); kein neues NuGet-Paket.
- **Setzung Cache-Falle (Risiko 3).** Der Aufruf trägt `--no-cache-filter test-bericht`: die C#-Tests laufen bei jedem `make spec-abdeckung` neu (Restore bleibt im Cache), ein alter Lauf kann nicht in die Matrix. Belegt: im Lauf mit unveränderten Quellen, die schon einmal gebaut waren, zeigt das Bau-Protokoll die Schritte `COPY` und `RUN dotnet test` der Stage als ausgeführt (nicht `CACHED`, `Passed: 28`). Die Wirkung eines roten Tests am echten Bestand: ein absichtlich roter Assert in `MitschnittEinheitTests.cs` (zurückgesetzt) ergab 19 bestanden und 3 nicht bestanden mit dem Test als `rot`, nach dem Zurücksetzen wieder 20 und 2. Offen bleibt der Gradle-Teil, der weiter am Layer-Cache seiner Quellen hängt (`BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, Nachweis-Datei ergänzt).
- **Rot gesehen.** `make spec-abdeckung-selbsttest` prüft je Mutation des Erzeugers, dass die C#-Fälle rot werden: `cs-eingang-entfernt`, `trx-ergebnis-immer-bestanden`, `tag-haftet-ueber-andere-zeilen`, `feldzeile-mit-zuweisung-ist-methode` (Review F-2), `trx-ohne-methodenzuordnung` (je Ausgabe „Mutation … rot gesehen“); eine Mutation, die den Erzeuger nicht ändert, bricht den Selbsttest. Am echten Bestand: der rote Assert oben.
- **Spec-Stelle.** `spec/spezifikation.md` nennt den Erzeuger und seine Leseorte nicht (nur die Zeile `SPEC-052` erwähnt den Tag `@spec`); kein Nachzug nötig.
- **Grenzen, die gelten.** Ein C#-Tag haftet nur an einer Methodenzeile in einer Zeile mit Typ, Namen und `(`; Attribute über mehrere Zeilen, geschachtelte Klassen und `record`/`struct` sind nicht gelesen (Kopf von `tools/spec-abdeckung.sh`). Risiko 2 (Tag über der falschen Methode) bleibt wie benannt: gefangen wird nur ein fehlender, kein vertauschter Name.
- **Befund an anderer Stelle.** Der Kopfkommentar von `referenztest-dotnet/Mitschnitt/MitschnittEinheitTests.cs` sagt, `make spec-abdeckung` lese nur Kotlin-Tests; das stimmt nicht mehr. Die Datei gehört dem parallelen Slice `slice-qa-vergleichslauf-d-messungen` und ist hier nicht geändert.

**Closure (Planner, 2026-11-06).**

**Belege der Rollen.** Review: [2026-11-06-dd87c07-review-slice-spec-abdeckung-liest-csharp](../../../reviews/2026-11-06-dd87c07-review-slice-spec-abdeckung-liest-csharp.md) (0 HIGH, 1 MEDIUM, 4 LOW, 3 INFO). Verifikation: [2026-11-06-429aa2f-verifikation-slice-spec-abdeckung-liest-csharp](../../../reviews/2026-11-06-429aa2f-verifikation-slice-spec-abdeckung-liest-csharp.md) (closure-fähig; 0 HIGH, 0 MEDIUM, 2 LOW, 3 INFO). Die Nachbesserung `429aa2f` (F-1 bis F-5) hat der Verifier am Text und mit Läufen gegengelesen; ein zweiter Review durch einen dritten Kontext hat sie nicht gesehen. Die Anpassungen der Closure (V-1, V-2) sind Text und Kommentare, keine Logik; sie ändern den Kopf von `tools/spec-abdeckung.sh` und eine Zeile des Matrix-Vorspanns, den der Erzeuger schreibt (Matrix von Hand gleich nachgezogen, siehe Gate-Läufe).

| Befund | Klasse | Ausgang |
|---|---|---|
| F-1 (MEDIUM) Bericht aus Schwesterstage `test-bericht`, Aussagen nannten `test`, kein Gleichlauf-Sensor | Zweite Stage mit kopiertem Rezept ohne Gleichlauf; Aussage „Stage X“ trifft Schwesterstage | Basis `test-quellen` für `COPY` und `DOTNET_TEST_ARGS`; Restrisiko V-2 (unten) |
| F-2 (LOW) Feldzeile mit Zuweisung als Methode gelesen | Leseregel im Kommentar breiter als das Verhalten | Regel und Selbsttest-Fall, fünfte Mutation |
| F-3 (LOW) Grenze „vertauschter Name“ nur im Zeitdokument | Grenze nur im Zeitdokument, nicht beim Erzeuger | im Kopf des Erzeugers genannt; Risiko 2 weiter offen |
| F-4 (LOW) Kommentar zum Fehlerpfad ungenau | Kommentar über Fehlerpfad ungenau | berichtigt |
| F-5 (LOW) Zählangaben „21“, „23“ im Plan | Zählangabe im Plan ohne Messung | berichtigt (20 und 22) |
| F-6 (INFO) / V-3 Kopfkommentar `MitschnittEinheitTests.cs` Z. 13 bis 14 falsch | Fortschreibung lässt zweite Fundstelle stehen | **nicht geändert**: Datei gehört dem parallelen Slice [slice-qa-vergleichslauf-d-messungen](../in-progress/slice-qa-vergleichslauf-d-messungen.md); dort zu berichtigen (Anschluss, siehe Folge-Slices) |
| F-7, F-8 (INFO) Vorher-Wert aus eingecheckter Matrix; Stempel-Listen | — | gegengelesen, kein Ausgang nötig |
| V-1 (LOW) Planner-Wortlaut „Stage `test`“ in DoD §2, Plan §3, §4 Rückführung 2, §1 | DoD-Wortlaut anders als die Bauart (`BEO-TDS/dod-wortlaut-ueber-der-bauart`) | Wortlaut in §1, §2, §3 und §4 an `test-bericht` angepasst und als **Abweichung vom ursprünglichen Wortlaut** gekennzeichnet. Die Zusage (Bericht des .NET-Laufs, Gate-Stage nicht verändert) ist geliefert; die Lieferform ist eine Schwesterstage der Basis `test-quellen`, nicht `test` selbst |
| V-2 (LOW) Setzung „Gleichlauf-Sensor nicht nötig“ breiter als der Beleg | Aussage trägt weniger weit als geschrieben | **eingeschränkt** auf „`COPY` und `DOTNET_TEST_ARGS`“ in §7, im Kopf von `tools/spec-abdeckung.sh` und im Vorspann der Matrix. Die `RUN`-Zeile mitzukoppeln (Option des Verifiers: `RUN` in die Basis, Logger über eine Variable) ist eine Änderung am Dockerfile der Gate-Stage und **nicht in diesem Slice gebaut**; sie steht als offene Schuld mit Vorschlag im Register (`BEO-TDS/schwesterstage-ohne-gleichlauf-sensor`) |
| V-4 (INFO) Grep nach weiteren Fundstellen | — | außer V-1 und V-3 keine |
| V-5 (INFO) Gradle-Teil der Matrix am Layer-Cache | Matrix hängt am Cache | Restrisiko benannt, siehe Risiko 3 |

**Belegte Läufe der Closure (Planner, im Worktree des Zweigs).** `make gates`: siehe Zeile „Gate-Läufe“ unten. Frischbeleg der Stage (Cache-Falle G1): der Verifier fuhr `docker build --target test --no-cache-filter test` (28 bestanden) und den Selbsttest mit `docker build --no-cache --target spec-abdeckung-selbsttest`; `make spec-abdeckung` erzwingt `test-bericht` selbst mit `--no-cache-filter` (Bau-Protokoll: `COPY` und `RUN dotnet test` ausgeführt, nicht `CACHED`).

**Rot gesehen, Grenzen.** Rot gesehen: fünf Mutationen des Erzeugers im Selbsttest (Implementer, Verifier frisch wiederholt), zwei eigene des Reviewers (`NotExecuted` als bestanden; Rangvergleich invertiert), ein absichtlich roter Assert am echten Bestand (19/3, vom Implementer). **Nicht rot gesehen:** (a) Änderung der `RUN`-Zeile der Stage `test` (V-2, Mutation 1 blieb grün). (b) Die Wirkung eines vertauschten Tags auf eine andere vorhandene, bestandene Methode (Risiko 2). (c) Der Gradle-Teil bei altem Cache (Risiko 3). (d) Der rote Assert am echten Bestand ist nicht vom Verifier wiederholt.

**Messklasse.** Die Zahlen der Wirkung (0 auf 20 bestandene Nachweise für `SPEC-045`, 2 nicht gelaufen, Waisen 10 auf 9, `42 von 56` auf `43 von 56`) sind Zählungen aus einem Lauf des Erzeugers, den Review und Verifikation wiederholt haben (Matrix byte-gleich), keine Zeitmessung. Der Vorher-Wert stammt aus der eingecheckten Matrix auf `cbe4fb06`. Eine Laufzeit des neuen Wegs (zweiter Build, `--no-cache-filter`) ist nicht gemessen.

**Risiko-Ausgänge** stehen in §6.

**Steering-Loop-Eintrag:** gezählt, nicht verkörpert; der Planner verkörpert nichts still. Lerneintrag (Klassen der Befunde): (1) **Kopplung ohne Mutationsbeleg:** zwei Stages, die ein Rezept teilen sollen, hielten für `COPY` und die Argumentvariable (Mutation 2 belegt), aber niemand fuhr zuvor eine Mutation an der `RUN`-Zeile; die Setzung „Sensor nicht nötig“ stand ohne diesen Beleg da und der Verifier fand die Lücke mit einer Mutation (V-2, davor F-1). Die Lehre: eine Kopplung gilt für das, was eine Mutation rot oder folgsam gezeigt hat, und nur dafür. (2) **Aussagen breiter als der Beleg** wieder (F-1 „Bericht der Stage `test`“, F-2 Leseregel im Kopf, F-3 Grenze, V-2), diesmal in Kommentaren und Setzungen des Werkzeugs. (3) **Planner-Wortlaut gegen die Lieferform** (V-1): der Plan nannte „Stage `test`“, der Implementer wählte mit Grund die Schwesterstage; derselbe Befund wie in `slice-referenzabgleich-mitschnitt`. (4) **Zweite Fundstelle** (F-6, V-3) wieder, mit Ausgang: an den Slice übergeben, der die Datei hält. (5) Matrix von Hand nachgezogen, weil der Erzeuger sie schreibt: Text im Erzeuger und in der Matrix müssen zusammen geändert werden; `make spec-abdeckung` bestätigt es. Zähler und Vorschläge: Register unten. Kein `liegt in`: nichts wurde verkörpert.

**Vorschlag, Entscheidung beim Nutzer (keine Regel geschrieben):** die `RUN`-Zeile der Gate-Stage `test` in die Basis `test-quellen` ziehen (Aufruf `dotnet test $DOTNET_TEST_ARGS`, der TRX-Logger als zusätzliche Variable in `test-bericht`), damit die Kopplung vollständig ist; oder die Einschränkung bewusst stehen lassen. Als Folge-Arbeit am Dockerfile ein eigener kleiner Slice, falls entschieden.

**Beobachtungs-Register (`../observations/`):** Evidence `slice-spec-abdeckung-liest-csharp.md` neu in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-1, F-2, F-3, V-2), [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) (F-6, V-3), [BEO-TDS/dod-wortlaut-ueber-der-bauart](../observations/BEO-TDS/dod-wortlaut-ueber-der-bauart/observation.md) (V-1) und neu die Beobachtung [BEO-TDS/schwesterstage-ohne-gleichlauf-sensor](../observations/BEO-TDS/schwesterstage-ohne-gleichlauf-sensor/observation.md) (F-1, V-2; 1×). Bereits vor der Closure vorhanden: Evidence in [BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor](../observations/BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor/observation.md) (Risiko 3; die Datei trägt jetzt auch V-5). Der Stand von [BEO-TDS/spec-abdeckung-liest-nur-kotlin](../observations/BEO-TDS/spec-abdeckung-liest-nur-kotlin/observation.md) ist auf *Ausgang gesetzt* fortgeschrieben (Träger geliefert, Slice geschlossen). Ohne eigenen Evidence-Eintrag geblieben: F-4 und F-5 (je eine Stelle, im Slice berichtigt); wer sie als Klasse zählen will, legt die Evidence nach.

**Folge-Slices:** keine angelegt. Zwei Anschlüsse, beide ohne eigene Datei: (a) die Berichtigung des Kopfkommentars `referenztest-dotnet/Mitschnitt/MitschnittEinheitTests.cs` Z. 13 bis 14 gehört in [slice-qa-vergleichslauf-d-messungen](../in-progress/slice-qa-vergleichslauf-d-messungen.md) (die Datei liegt in dessen Fläche; der Slice kann es dort in seine Nachweise aufnehmen, sein Start-Trigger nennt diesen Slice nicht); (b) die Kopplung der `RUN`-Zeile (Vorschlag oben) hat keinen Träger, bis der Nutzer entscheidet.

**Drei Paarungen:** Anker: kein Anker gesetzt, weil nichts verkörpert wurde. Folge-Slice: keiner genannt; die Anschlüsse (a) und (b) sind keine Folge-Slices. Register: jede genannte Kennung `BEO-TDS/<slug>` besteht als Verzeichnis und trägt ein nicht leeres `evidence/` (nach dem `git mv` zu prüfen).

**Gate-Läufe:** `make gates` im Worktree des Zweigs auf `199226e5` (Planner): Exit 0, einschließlich `gate-zuordnung` (16 Gates), `stempel-eingaenge` und der Selbsttests der Stempel. `make docs-check`: 1004 Dateien, 0 Befunde. `make spec-abdeckung` nach der Textänderung: Matrix unverändert, 9 Festlegungen ohne Nachweis, `SPEC-045` 20 bestanden / 2 nicht gelaufen. `git merge main`: bereits aktuell. Kein Frischbeleg `--no-cache` der Stage `test-bericht` durch den Planner; er stützt sich auf `--no-cache-filter test-bericht` im Aufruf von `make spec-abdeckung` (Bau-Protokoll des Planner-Laufs: Matrix reproduziert) und die Frischläufe des Verifiers.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt Werkzeug (Shell, Docker), das .NET-Gerüst und Dokumentation, keine dieser Strukturen trägt eine eigene Strukturregel neben dem Repo insgesamt. `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des Worktrees auf `main` `5d1be057`, 2026-11-06; Namensliste, Texte der Treffer gelesen). Treffer:

- `BEO-TDS/spec-abdeckung-liest-nur-kotlin` — der Gegenstand; Stand wird mit diesem Slice von *offen — kein Träger* auf *Träger entschieden* fortgeschrieben.
- `BEO-TDS/dod-wortlaut-ueber-der-bauart` — die Abgrenzung der zwei lokalen Tests und der Wortlaut „Lauf in `make test`“ sind gegen Bauart und Werkzeug gelesen.
- `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` — Risiko 3 in §6.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — Kennungen in Slice und Closure-Notiz als Link.

**Modus-Begründungsblock.** Alle berührten Sub-Areas GF.
