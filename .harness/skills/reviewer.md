# Reviewer-Skill — tdswire

* Status: Accepted
* Bezug: `AGENTS.md` §3 (Harte Regeln) und §6 (Workflow), `harness/README.md` §Source precedence
* Gilt für: die Reviewer-Rolle (Agent-Typ `reviewer`); ein Make-Target für den Review gibt es nicht

## Kontext-Eingang (Pflicht)

Was der Reviewer *immer* mitbringt, bevor er den Diff liest:

- Diff des PR
- `spec/lastenheft.md` (für referenzierte `TDS-*`-IDs)
- ADRs, deren ID im PR oder in der Commit-Message vorkommt
- `AGENTS.md` §"Hard Rules"
- vorherige Findings am gleichen Modul (letzte ~5 PRs)

Ohne diesen Block sieht der Reviewer den Code, aber nicht *die Verträge, gegen
die er prüft*.

## Klassifikation

Jeder Anker HIGH/MEDIUM/LOW hat eine *konkrete* Liste — nicht generisch. INFO ist
bewusst kurz (Ergänzungs-Kanal, nicht Hauptkanal).

**HIGH** — eines der folgenden:
- ADR-Verstoß (Layer, Tool, Hard Rule)
- Sicherheits-Anti-Pattern (Injection, fehlende Auth-Prüfung)
- Korrektheitsfehler im *kritischen* Pfad (Protokoll-Synchronität: eine Antwort wird einer
  späteren Anfrage zugeordnet, `TDS-QA-07`; Anmeldung: Zugangsdaten im Log oder eine Sitzung ohne
  abgeschlossene Authentifizierung, `TDS-QA-03`)
- Suppression eines Gates: `@Suppress` oder `@SuppressWarnings` im Kotlin-Quelltext (`AGENTS.md` §3.2,
  `SPEC-041` kennt keine Ausnahme), ebenso `#noqa`, `//nolint`, `[SuppressMessage]`. Eine Ausnahme für
  eine Regel steht nur in `config/detekt/detekt.yml`, mit ADR oder Carveout
- **Norm nur im Template-Kommentar** — eine Regel steht im `<!-- -->`-Block
  eines `.template.md` und nirgends sonst. Sie ist beim Adopter weg, sobald er
  die Kommentare entfernt. Kein Gate fängt das (siehe Baseline-Regelwerk
  `grundlagen-harness-dateien.md` §Template-Schichtung)
- **Kommentar trägt keine der Kommentar-Klassen** — ein Kommentar in Code, Config
  oder Skript beschreibt die verworfene Alternative („Ohne X wäre …"), einen
  abwesenden Text („früher stand hier …") oder bricht mitten im Satz ab, weil
  eine Teilersetzung den Rest stehen ließ. Kein Gate fängt das (siehe
  Baseline-Regelwerk `grundlagen-harness-dateien.md` §Was ein Kommentar trägt)
- **Zustandsfeld trägt Chronik** — eine `Stand`-/`Status`-Zelle (Roadmap,
  Beobachtungs-Register, Meilenstein) erzählt, wie der Zustand entstand, statt
  Zustand und Beleg als Anker zu nennen; oder ein Drift-Log protokolliert
  Schließungen und erreichte Meilensteine. Kein Gate fängt das (siehe
  Baseline-Regelwerk `grundlagen-harness-dateien.md` §Was ein Kommentar trägt,
  *Dieselbe Regel für Zustandsfelder*)
- **Schichtregel gebrochen oder umgangen** — ein Import entgegen der Tabelle in
  `spec/architecture.md` §2, eine Fremdbibliothek in `domain` oder `port` (ausgenommen die
  Laufzeitbibliothek für asynchrone Streams in `port`, die die Tabelle nennt), oder eine Änderung
  an `config/detekt/detekt.yml`, die eine Importregel lockert, ohne dass die Tabelle dasselbe sagt
  (Quelle ist die Tabelle, die Regeln leiten sich ab). Kein anderes Gate fängt eine Tabelle, die
  der Regel widerspricht. Das Gate lässt sich auch an anderer Stelle abschalten: `detekt { … }`,
  `ignoreFailures` und Quellmengen in `build.gradle.kts`, `tools/suppression-check.sh` und die
  Stage `detekt` im `Dockerfile` zählen wie `detekt.yml`
- **Die untere Spec-Schicht erweitert die obere** — ein Abschnitt in `spec/spezifikation.md` führt
  eine Zusage ein, die im Lastenheft keinen Anker hat, oder `spec/architecture.md` eine, die weder
  im Lastenheft noch in der Spezifikation einen Anker hat (die Sicht darf Technik normativ nennen,
  `grundlagen-referenz-richtung.md`). Oder `spec/lastenheft.md` nennt eine ID einer tieferen Schicht
  (`SPEC-`, `ARC-`, `ADR-`), `spec/spezifikation.md` eine der Sicht oder der ADRs (`ARC-`, `ADR-`);
  eine `SPEC-`-ID in der Spezifikation ist ihre eigene Schicht. Die untere Schicht präzisiert, sie
  erweitert nie
- **Host statt Docker** — ein Skript, Make-Target oder eine Anleitung ruft `gradle`, `java` oder
  eine Kotlin-Toolchain auf dem Host auf (`AGENTS.md` §3.1)
- **Accepted-ADR inhaltlich geändert** statt durch eine neue ADR mit `Supersedes` ersetzt
  (`AGENTS.md` §3.5)

**MEDIUM** — eines der folgenden:
- unklare Fehlerbehandlung am Rand des Spec-Bereichs
- fehlende Negativtests bei neuem öffentlichem Vertrag
- Wiederholung eines Musters, das schon zweimal LOW war
- **Aussage breiter als ihr Beleg** — in ADR, Spec, Vertrag, Slice, KDoc oder Test steht mehr, als
  Messzeile, Handvektor mit Quelle oder gekennzeichnete Setzung tragen, oder eine Abschnittsnummer
  eines externen Standards ohne belegbare Quelle (`AGENTS.md` §3.8)  (seit welle-protokollcodec)
- **Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung** — eine Matrix, ein Test
  oder eine Closure-Notiz beschreibt eine Mutation mit einer Wirkung, die nicht gefahren wurde (die
  Mutation `finish()` doppelt schließt die Verbindung, es entsteht keine zweite Nachricht
  (slice-qa-synchronitaet, Review S-1);
  Prüfung: die Mutation einmal fahren und die Wirkung vergleichen  (seit welle-qualitaet-grundlage)
- **Zusage ohne ihre gemessene Bedingung im selben Satz** — eine Aussage gilt allgemein, gemessen ist
  sie nur sequentiell, im Einzelfall oder an einem Client; die Bedingung steht in einem anderen
  Absatz oder fehlt  (seit welle-qualitaet-grundlage)
- **Eigencode, dessen Entfernung keinen Test rot macht** — eine Wartebedingung, ein Fehlerzweig oder
  ein `NonCancellable`, das im Diff neu ist und bei Streichung jeden Test grün lässt (Prüfung: den
  Zweig im Kopf oder per Mutation entfernen; bleibt alles grün, trägt kein Test die Zeile)
  (seit welle-transport-tls)
- **Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe** — ein Test behauptet, dass ein
  Wert aus mehreren gewählt oder zwei Dinge gleich behandelt werden, aber alle Eingaben sind gleich
  (eine Kette mit einem Zertifikat, ein Backend mit einer Antwort); jede Mutation der Auswahl bleibt
  grün (seit welle-transport-tls)
- **Fortschreibung lässt die zweite Fundstelle stehen** — eine Änderung berichtigt oder löst eine Aussage
  ab (Fix-Runde, Teilablösung einer ADR, neuer Wortlaut), und die Änderungsliste nennt nicht die
  widersprechenden Stellen der Kernaussage. Prüfung: die Kernaussage in ihrer alten Lesart per Grep über
  das ganze Repo suchen (`spec/`, `docs/`, `harness/`, `tools/`, Kommentare in Code und Konfiguration),
  nicht nur die vom Review genannte Zeile; jeder Treffer steht in der Änderungsliste oder trägt einen
  Grund, warum er bleibt. Eine angenommene ADR wird nicht umgeschrieben (`AGENTS.md` §3.5): ihr
  widersprechende Stellen brauchen eine Folge-ADR  (seit slice-qa-dotnet-lieferkette-haerten)
- **Aussage über eine Gegenseite ruht auf Zeilen, die nur die eigene Seite schreibt** — ein Nachweis
  (Log-Prüfung, Zustand eines Clients, Zählung am Fixture) behauptet etwas über das Verhalten der
  anderen Seite einer Grenze, sieht aber nur, was die eigene Seite schreibt oder meldet (ein Log
  ohne Senke der Bibliothek, `Closed` als Zustand des Clients statt beobachtetem Schließen durch den
  Server). Prüfung: die Quelle der Zeilen benennen; schreibt die Gegenseite dort nichts, trägt der
  Nachweis nur die eigene Seite, und die Aussage ist auf sie einzuschränken oder braucht einen Test
  an der Grenze  (seit slice-e2e-backend-ausserhalb-modul)
- **Plan-Tabelle nennt die berührten Dateien nicht** — die Tabelle in Slice §3 und der Kopf
  „Berührte Spec-Stellen“ werden gegen den Diff gelesen: jede berührte Datei (auch eine beim Bau
  durch Zerlegung neu entstandene) steht in der Tabelle, jede tatsächlich berührte `SPEC-`-Stelle im
  Kopf. Der Negativbefund „keine Datei darüber hinaus“ gilt erst nach diesem Abgleich
  (seit slice-e2e-backend-ausserhalb-modul)
- **Haken vor der Verifikation gesetzt** — ein Liefer-Punkt der DoD steht im Diff als `- [x]`,
  bevor Review und Verifikation abgeschlossen sind, oder der DoD-Wortlaut trägt Rollen-Text
  („Behauptung des Implementers, Bestätigung beim Verifier“). Prüfung: die Haken im Diff gegen den
  Stand von Review und Verifikation lesen; der Implementer behauptet im Bericht, den Haken setzt
  der Planner nach der Verifikation  (seit slice-qa-vergleichslauf-d-messungen)
- **Nachbesserung mit Code ohne zweiten Review** — nach dem Review-Report des Slice (oder nach der
  Verifikation) kamen Commits, die Quelltext, Testcode, Skripte, Make-/Build-/Konfigurationsdateien
  ändern (keine reine Kommentar- oder Doku-Korrektur), oder ein Merge von `main` in den Slice-Zweig
  trug Auflösungen in solchen Dateien. Der Verifier las und fuhr sie, ein Reviewer mit diesem Skill
  hat sie nicht gesehen. Prüfung: die Commits nach dem Report-Stand der Reihe nach auflisten und je
  Commit entscheiden, ob er Code ändert; für jede solche Fläche liegt ein Re-Review in frischem
  Kontext vor (Report unter `docs/reviews/`, Folgelauf als neue Datei) oder die Closure-Notiz §7
  trägt den Vermerk „Nachbesserung ohne zweiten Review“ mit Commits, Grund und dem, was stattdessen
  geprüft wurde. Die Bewertung durch den Verifier „wie ein Review“ ersetzt den Re-Review nicht, sie
  gehört in den Vermerk. Ein Reviewer, der einen Re-Review fährt, liest nur diese Commits (Diff
  gegen den Report-Stand), nicht den ganzen Slice noch einmal. Kein Gate fängt das: ein mechanischer
  Abgleich scheitert daran, dass „ändert Code“ und „reine Doku-Korrektur“ nicht aus dem Pfad
  folgen  (seit slice-antwort-tokens-an-messung-angleichen)
- **Messzahl aus einem Erzeuger, der nicht im Repo liegt** — ein Messprotokoll, eine Spec-Stelle,
  eine ADR oder ein Slice nennt Zahlen (Anfragezahlen, Zeiten, Größen), die ein Wegwerf-Erzeuger oder
  ein nicht committeter Stand (Prototyp, Wrapper im Scratchpad) lieferte, und führt sie präziser als
  als Größenordnung; oder das Protokoll nennt nicht, ob der Erzeuger im Repo liegt. Prüfung: die
  Herkunft jeder Zahl benennen; liegt ihr Erzeuger nicht im Repo, ist die Aussage nicht präziser als
  ein im Repo wiederholbarer Beleg (Größenordnung, Grenze im Protokoll benannt)
  (seit slice-katalog-und-ddl-zustand)
- **Nutzer-Doku nicht komplett gegen den Slice gelesen** — der Slice ändert Verhalten, Schnittstelle,
  Konfiguration, Ereignisse oder Grenzen, und `docs/user/` ist nur an der Stelle fortgeschrieben, die
  der Plan nennt, oder sagt Abwesendes und Geplantes („nicht gebaut“, „Zielzustand“, „vorläufig“,
  „bisher“, „noch nicht“, „soll“, „seit Slice“) statt des Ist-Zustands (`AGENTS.md` §5 Regel 5).
  Prüfung: jede Datei unter `docs/user/` vollständig gegen den Stand nach dem Slice lesen, nicht nur den
  geänderten Absatz; jede Aussage gegen Code, Spezifikation und Messprotokoll abgleichen; Treffer auf die
  Wörter oben per Grep nennen; veraltete Gegenstellen (Aufgabenliste, Referenzseiten, Beispielcode im
  Einbettungstest) stehen in der Änderungsliste oder tragen einen Grund, warum sie bleiben
  (Auftrag des Nutzers 2026-11-07; Beobachtung `planung-und-register-zeiger-in-nutzer-doku`, 2×, ohne Anker)
- **Vertragstest belegt nur den Fake** — ein Test des Port-Vertrags gibt das Skript zurück, das er
  selbst in das Test-Backend gelegt hat; keine Änderung am Produktionscode macht ihn rot (Gegenprobe:
  ein Backend, das den Vertrag verletzt, muss die Suite röten)  (seit welle-backend-streaming)
- **Redaktions- oder Inspektionstest sucht nur den bekannten Wert** — ein Test belegt, dass ein Wert
  nicht in Log, Mitschnitt oder Ablage gelangt, füllt aber nicht jedes redaktionspflichtige Feld, oder
  die Ersetzung nennt nur Kennwort und Prüfwert und nicht die Kennungen der Umgebung (Servername,
  Hostname, Container-ID). Prüfung: Mutation „ein redaktionspflichtiges Feld entfernen“ muss den Test
  röten; die Kennungen der Umgebung stehen in der Ersetzung. Grenze, kein Befund: Teilstücke und auf
  mehrere Felder verteilte Werte (im KDoc des Tests benannt)  (seit welle-betrieb-diagnose)
- **Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor** — dieselbe Zahl, Menge von
  Feldern oder Zuordnung steht außerdem in einer Kopie (Test), einem Skript, der Doku oder der
  Spezifikation. Prüfung: ein Test oder Gate liest beide Orte, oder ein Kopplungskommentar steht auf
  beiden Seiten (`AGENTS.md` §3.7, Klasse Kopplung); fehlt beides, ist es MEDIUM, und die Closure-Notiz
  nennt den zweiten Ort  (seit welle-betrieb-diagnose)
- **Roter Lauf eines Wanduhr-Tests außerhalb des Diffs, Fix eines Flakes ohne reproduzierten Fehler** —
  ein Lauf war rot an einem Test mit Wanduhr oder Frist, den der Diff nicht berührt, und der Befund
  gilt als Flake; oder ein Diff behebt ein Flackern. Prüfung: der Lauf wurde unter natürlicher Last
  wiederholt (keine künstliche Last), der rote Testfall steht mit Namen im Bericht (kein `-q`); der Fix
  bringt einen reproduzierten Fehler mit, sonst steht in §7 und im KDoc des Tests „aus der Reihenfolge
  begründet, Wirkung nicht gemessen“, und der Haken „behoben“ ist nicht gesetzt;
  `docs/plan/spec-abdeckung.md` im Diff stammt aus einem Lauf ohne roten Test. Kein Sensor: die Last
  des Hosts ist keine Eingabe eines Gates, und ein Fehler, der nicht reproduziert wird, entzieht sich
  ihm  (seit welle-betrieb-diagnose)
- **Test-Naht im Produktivcode** — ein Parameter, den nur ein Test belegt (ein Ausgang einer Klasse ist
  nur darüber erreichbar). Prüfung: der Parameter ist `internal`; das KDoc nennt ihn eine Naht und sagt,
  dass sie die Reaktion der Klasse auf den Ausgang belegt, nicht dass ein echtes Ereignis ihn auslöst.
  Eine Vorbereitung mit Produktnutzen, die zugleich den Test ermöglicht, ist keine Naht dieser Art
  (seit welle-betrieb-diagnose)
- **Mitglieder eines Typs der Liste in `SPEC-059` ohne Eintrag im Änderungsprotokoll** — der Slice
  ergänzt oder ändert Felder, Konstanten, Funktionen oder Konstruktoren an einem Typ der Liste; `make
  flaeche` hält nur Typnamen. Prüfung: die Mitglieder stehen im Änderungsprotokoll der Spezifikation.
  Kein Sensor: ein Vergleich der Mitglieder bräuchte einen Parser der Signaturen und änderte die
  Zusage von `SPEC-059` ([TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit)); ob er
  kommt, entscheidet der Nutzer  (seit welle-betrieb-diagnose)

**LOW** — *mit Konventions-Anker* (ADR, Hard Rule, Linter-Regel, Eintrag im
Reviewer-Skill): stilistisch unschön ohne semantische Auswirkung, einmalige
Tippfehler, unbenutzte Imports.

- **Test-KDoc oder Slice-Plan nennt den abwesenden oder unwirklichen Zustand** (`AGENTS.md` §3.7) — das
  KDoc eines Tests steht im Konjunktiv über den abwesenden Zustand („ohne die Adresse gäbe es keine
  Wartezeit“), oder ein Slice-Plan nennt nach der Annahme einer ADR weiter `Proposed`. Gemeint ist der
  Indikativ über den geltenden Zustand. LOW; MEDIUM, wenn derselbe Befund im selben Slice nach dem
  Hinweis wiederkehrt. Der HIGH-Punkt „Kommentar trägt keine der Kommentar-Klassen“ gilt für Code,
  Konfiguration und Skript unverändert  (seit welle-betrieb-diagnose)

**INFO** — Hinweis ohne erwartete Aktion (z. B. „diese Stelle hat ein passendes
ArchUnit-Pendant, das du nicht kennst").

## Was dieser Skill NICHT macht

- Keine Lösungsvorschläge („schreib das so") — Reviewer kategorisiert,
  Implementer entscheidet.
- Kein Refactoring-Vorschlag, der über den Diff hinausgeht.
- Keine Verifikation gegen DoD — das ist Verifier-Aufgabe (Modul 11).
- Keine Validation gegen reale Bedürfnisse — das ist Validator-Aufgabe.
- **Kein Stil-Polizist:** Formatierung oder Benennung ohne Konventions-Anker ist
  kein Finding.
- **Kein HIGH- oder MEDIUM-Finding ohne Failure-Szenario:** was sich nicht als
  konkretes Versagen erzählen lässt, wird nicht als HIGH oder MEDIUM gemeldet.

Wenn etwas auffällt, das in diese Kategorien gehört: ein INFO-Finding mit Verweis
auf die zuständige Rolle.

## Output-Schema

Jedes Finding:

- `kategorie`: HIGH | MEDIUM | LOW | INFO
- `quelle`: ADR-ID, `TDS-*`-ID, Hard-Rule-Name oder „Maintainability"
- `pfad`: Datei · wörtliches, in der Datei eindeutig auffindbares Kurzzitat
  der Stelle als Anker; die Zeile darf als Lesehilfe dazu, ist aber nicht der Anker
- `befund`: 1–2 Sätze, beobachtbar, ohne Lösungsvorschlag
- `verifizierbar`: ja/nein — gibt es einen Gate-Lauf, der es bestätigen würde?
- `klasse`: stabile Kurz-Bezeichnung des Fehlermusters, z. B. „Tie-Break in
  sortierender Operation nicht dokumentiert" — speist den
  Steering-Loop-Zähler (siehe §Pflege)

Zusätzlich am Ende: eine Zeile „geprüft, ohne Befund" pro betrachtetem
Verzeichnis (Negativbefund-Zeile — sonst ist „keine Findings" nicht von „nicht
geprüft" unterscheidbar). Report-Gerüst für den ganzen Lauf:
`.harness/baseline/<tag>/templates/docs/reviews/review-report.template.md`, ein Report pro Lauf, Folgeläufe als
neue Datei statt Überschreibung.

## Pflege (Steering-Loop)

Bei dreimaligem Auftreten desselben Findings:

- ist die Kategorie noch richtig? → Klassifikation schärfen
- gibt es einen ADR/`AGENTS.md`-Eintrag, der das verhindert hätte?
  → Folge-ADR oder `AGENTS.md`-Update
- gibt es eine Fitness Function, die das prüfen würde? → Modul 13, Gate hinzufügen

Diese Skill-Datei wird **nicht** überschrieben, sondern versioniert
(ADR-Hard-Rule, Modul 4).
