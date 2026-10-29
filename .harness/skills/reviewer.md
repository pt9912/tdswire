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

**LOW** — *mit Konventions-Anker* (ADR, Hard Rule, Linter-Regel, Eintrag im
Reviewer-Skill): stilistisch unschön ohne semantische Auswirkung, einmalige
Tippfehler, unbenutzte Imports.

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
