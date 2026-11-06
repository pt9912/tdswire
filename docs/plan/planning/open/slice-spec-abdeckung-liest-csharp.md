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

**Verantwortlich:** — bis zur Priorisierung (pt9912 trägt sich beim Übergang `open` → `next` ein).

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

**Herkunft und Nutzerentscheidung.** Die 21 Einheitstests des Mitschnitt-Werkzeugs (`referenztest-dotnet/Mitschnitt/MitschnittEinheitTests.cs`) tragen `// @spec SPEC-045` und laufen grün in `make referenztest-dotnet` (Dockerfile-Stage `test`); `tools/spec-abdeckung.sh` liest nur den KDoc-Block-Tag in Kotlin und führt nur Gradle-Berichte, deshalb zeigt die Matrix `SPEC-045` mit null Nachweisen (`BEO-TDS/spec-abdeckung-liest-nur-kotlin`, Verwandte: `BEO-TDS/dod-wortlaut-ueber-der-bauart`). **Nutzerentscheidung vom 2026-11-06** („deine empfehlungen“): das Werkzeug wird erweitert; die Alternative (die Logik der Vergleichstests nach Kotlin verlegen) ist verworfen, weil sie den Zuschnitt von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) ändern würde, der den Referenzclient im .NET-Gerüst führt.

**Die Leseregel bleibt die der Tests, die schon da sind.** Der Tag steht als Kommentarzeile `// @spec SPEC-<NNN> [SPEC-<NNN> ...]` über der Testmethode (Setzung des Planners: so tragen ihn die bestehenden 23 Stellen; eine andere Form hieße, sie umzuschreiben), Kennungen durch Leerzeichen getrennt, mehrere Zeilen addieren sich. Welche Methode der Tag erreicht (Attribut `[Fact]`, `[Theory]`, weitere Attribute dazwischen), legt der Implementer an den bestehenden Dateien fest und beschreibt es im Kopf des Erzeugers wie die Kotlin-Regel dort. Das Urteil bleibt „mindestens ein bestandener getaggter Test“; der Bericht ist der Lauf der Stage `test` des .NET-Gerüsts.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die beiden getaggten Tests im Lauf `LAUF=mitschnitt` (`referenztest-dotnet/Vergleich/MitschnittLaufTests.cs`, Kategorie `MitschnittAbspielen`) — sie laufen nur lokal in `make vergleichslauf-d` (Server-Container, EULA-Entscheidung des Nutzers), nicht in der Stage `test`; der Erzeuger sieht sie nicht in seinem Bericht und führt sie als „nicht gelaufen“, was wahr ist. Ob ein lokaler Bericht in die Matrix eingehen darf, ist Frage 1 in §6.
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

- [ ] **Der Erzeuger liest C# und den .NET-Bericht:** `tools/spec-abdeckung.sh` liest zusätzlich den Kommentar-Tag in den C#-Quellen des Referenzclients (Eingang `TEST_QUELLE`-Verwandtschaft: ein eigener Eingang für die C#-Verzeichnisse mit Vorgabe) und den Testbericht der Stage `test` des .NET-Gerüsts (ein TRX-Bericht des eingebauten Loggers, aus der Stage per Ausgabe herausgeführt wie Matrix und Waisenzahl heute); ein getaggter C#-Test zählt, wenn Klasse und Methode im Bericht ohne Fehler und ohne Übersprung stehen, sonst steht er in den bestehenden Abschnitten „rot/übersprungen“ und „nicht gelaufen“. `make spec-abdeckung` bleibt kein Gate; die Zeile in `harness/README.md` (Werkzeuge) und der Kopf des Erzeugers nennen den neuen Eingang. Nach dem Lauf führt `docs/plan/spec-abdeckung.md` für `SPEC-045` die bestandenen C#-Tests (Beleg: die Matrix, erzeugt und eingecheckt wie bisher).
- [ ] **Selbsttest:** `make spec-abdeckung-selbsttest` erzeugt einen Mini-Bestand mit C#-Quelle und TRX-Bericht und belegt je Fall (Happy: bestandener Test zählt; Negative: roter, nicht gelaufener, übersprungener, unbekannt getaggter C#-Test zählt nicht; Boundary: mehrere Tags und mehrere Zeilen addieren sich, Tag ohne folgende Testmethode, Kotlin- und C#-Nachweis derselben Kennung, Methode mit mehreren Attributen); mindestens eine Mutation des Erzeugers (C#-Eingang entfernt) macht den Selbsttest rot.

Nachweis-Pflichten für beide Punkte:

- [ ] Wirkung am echten Bestand: `make spec-abdeckung` vorher und nachher, die Zahl der Nachweise von `SPEC-045` und die Waisenzahl stehen in der Closure-Notiz (vorher null Nachweise, 2026-11-06).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Werkzeuge: Zeilen `make spec-abdeckung` und `make spec-abdeckung-selbsttest`).
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
| `tools/spec-abdeckung.sh` | update | C#-Tag lesen, TRX-Bericht lesen, Kopf mit Eingängen und Grenzen ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)) |
| `tools/spec-abdeckung-selbsttest.sh` | update | Fälle und Mutation für C# (Happy/Boundary/Negative) |
| `Dockerfile` (Stage `spec-abdeckung`, `spec-abdeckung-export`) und `referenztest-dotnet/Dockerfile` (Stage `test`) | update | der Bericht der .NET-Stage gelangt zum Erzeuger; Eingangslisten der Stempel (`tools/stempel/*.eingaenge`) und `make stempel-eingaenge-pruefung` mitziehen, wenn eine `COPY`-Quelle hinzukommt |
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
- `in-progress` → `open` (blockiert — Carveout?): die Stage `test` des .NET-Gerüsts liefert ohne neues Paket keinen lesbaren Bericht; der Nutzer entscheidet über ein Paket (Sperre, Signaturliste) oder über eine andere Form.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Beide Punkte der DoD in §2 erledigt, `make gates` grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zahlen vorher und nachher für `SPEC-045`, die Grenzen (Bericht der Stage `test`, nicht der lokalen Läufe; Urteil nur „mindestens ein bestandener getaggter Test“) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Frage 1 an den Nutzer — lokale Läufe in der Matrix.** Die zwei getaggten Tests des Abspielens laufen nur lokal. Setzung des Planners: sie bleiben „nicht gelaufen“, solange der Erzeuger nur den Bericht der Stage `test` kennt; ein lokaler Bericht aus `make vergleichslauf-d` (nicht reproduzierbar im Sinne von [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), EULA) ginge als Nachweis in eine eingecheckte Matrix nur auf Entscheidung des Nutzers. — **Ausgang:** offen bis zur Closure.
- **Ein Tag in einem Kommentar sagt nichts über die Methode darunter.** Ein Tag über einer Hilfsmethode oder einer umbenannten Methode würde dem falschen Namen zugerechnet; die Namenszusage gegen den Bericht („nicht gelaufen“) fängt das für fehlende, nicht für vertauschte Namen. — **Ausgang:** offen bis zur Closure.
- **Der Bericht der .NET-Stage hängt am Layer-Cache** (`make referenztest-dotnet` übergeht die Stage, wenn nichts Kopiertes geändert ist): die Matrix könnte einen alten Lauf zeigen. Der Implementer legt fest, ob der Erzeuger den Bericht frisch erzwingt (`BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`). — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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
