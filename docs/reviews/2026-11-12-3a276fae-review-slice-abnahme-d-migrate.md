# Review-Report: slice-abnahme-d-migrate — 2026-11-12

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers). Der Diff ist reine Doku.

**Gegenstand:** Diff `d348c1f2..3a276fae` im Zweig `worktree-agent-a21526b2fddb5451e` (Lifecycle-Moves `open` nach `in-progress`, Inhalts-Commits `acd70a20`, `25e733e5`, `3a276fae`).

**Skill:** `.harness/skills/reviewer.md` @ `3a276fae` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-12

**Eingangs-Kontext:**

- Slice `slice-abnahme-d-migrate` (Plan §3, §4, DoD, Risiken §6), Register `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Punkte 3, 9 bis 15)
- `TDS-FA-12`, `TDS-QA-01`, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md); `SPEC-040`; `AGENTS.md` §3.1 bis §3.8, §5 Regel 5 bis 7
- Messprotokolle `docs/messungen/abnahme-d-migrate-2026-11-12.md` und `docs/messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md`, Anleitung `docs/user/testserver-manuell.md` (vollständig)

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make docs-check`, `make flaeche`, `make kopplung-zahlen` | Exit 0, 6 s (Cache) |
| `make gates` | Exit 0, 8 s (Stempel/Cache; kein frischer Beleg über den Cache hinaus) |
| Anleitung Aufgabe 6 nachgespielt: zwei Container (eigene Namen, Ports 15101/15102, eigenes Image), d-migrate 1.12.0 per Digest | `schema migrate --execute` mit `soll.yaml` Exit 0; `data import` ohne Neustart Exit 5 `Source and destination schemas do not match.`; nach `down`/`up` Exit 0, `2 inserted`; `data transfer` (Quelle mit `Email`, Ziel ohne) Exit 3 `Preflight: Column 'kunden.Email' missing in target`; nach `migrate` auf dem Ziel Standardpfad Exit 0 `Transfer complete`, `--on-conflict update` Exit 0. Die Aussagen des Neustart-Hinweises und der Zeilen des Abnahmeprotokolls dazu bestätigt. |
| Zeilenverweise in Protokoll 2 (`SimElementFields.kt` 13 bis 17 und 56, `CatalogForms.kt` ab 126) | stimmen (Zeile 56: `NO_ACTION` ohne Angabe; `FOREIGN_KEYS` ab 126, Block bis 150) |
| `git diff --stat` | nur `docs/` (Moves, Verweis-Nachzüge, 2 Protokolle, Anleitung, Roadmap, Slice-Kopf); kein Produktcode, keine Spec, keine ADR, kein `@Suppress`, keine Gate-Änderung |
| `make clean-build`, `make e2e`, Mutationen | nicht gefahren (Sensor-Zuschnitt, kein Code im Diff) |

Die Zeilen des Protokolls 1 (Exit-Codes, Auszüge) über die nachgespielten Befehle hinaus sind nicht wiederholt; sie ruhen auf dem Scratchpad des Validators.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Anleitung sagt zum Fremdschlüssel mit `restrict`/`no_action`: „Ob der echte SQL Server dieselbe Rückgabe liefert, ist nicht gemessen“. Das Protokoll im selben Diff (Commit `3a276fae`, 14 Läufe, SQL Server 2022) und Fund F3 des Abnahmeprotokolls messen genau das und finden es gleich. Ein Leser der Anleitung hält eine gemessene Aussage für offen und liest „Abweichung des Testservers“ als möglich; Commit `3a276fae` hat die Anleitung nicht fortgeschrieben (nur zwei Protokolldateien). | `AGENTS.md` §3.8, Skill „Fortschreibung lässt die zweite Fundstelle stehen“ / „Nutzer-Doku nicht komplett gelesen“ | `docs/user/testserver-manuell.md` · „Ob der echte SQL Server dieselbe Rückgabe liefert, ist nicht gemessen“ | nein (Grep nach „nicht gemessen“ im Abschnitt 7 zeigt die Stelle) | Nachmessung in Protokoll, Anleitung nicht nachgezogen |
| F-2 | MEDIUM | Die Plan-Tabelle in Slice §3 nennt nur das Abnahmeprotokoll und die Anleitung; der Diff legt `docs/messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md` neu an (und der Plan nennt Register-Fortschreibung nicht, die das Protokoll als Vorschlag trägt: Punkt 16, Anleitung, Abnahmeprotokoll F3). Der Negativbefund „keine Datei darüber hinaus“ gilt erst nach der Nachführung der Tabelle. | Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | `docs/plan/planning/in-progress/slice-abnahme-d-migrate.md` · „Messprotokoll des Validator-Laufs“ | nein | Plan-Tabelle ohne zweite Messdatei |
| F-3 | LOW | Das Abnahmeprotokoll legt die Arten F-(a) „Fehler im Server“, (b) „Lücke der Anleitung“, (c) „abweichend, aber Setzung“ fest, setzt F3 aber auf eine vierte, nicht definierte Art („Verhalten von d-migrate, am echten Server gleich“); das Protokoll zu F3 nennt das „Einordnung (a)“ und schlägt vor, F3 auf „(a) Client“ zu stellen. Dasselbe „(a)“ heißt in beiden Dokumenten etwas anderes. Die Zeile „Funde der Art (a): keine“ ist damit nur mit der Legende lesbar. | Maintainability (Protokoll verweist auf Legende, die F3 nicht trägt) | `docs/messungen/abnahme-d-migrate-2026-11-12.md` · „Verhalten von d-migrate, am echten Server gleich (Nachmessung)“ | nein | Begriffs-Kollision der Fund-Arten |
| F-4 | LOW | Der Absatz „`data transfer`“ der Anleitung nennt für „`schema migrate --execute` bringt das Ziel auf die Struktur der Quelle“ weder Soll-Datei noch Befehl, und bei zwei Containern im Anfangsstand ist der Schritt gar nicht nötig (nur nach Aufgabe 3 mit `Email`). Wer dem Absatz folgt, muss die Datei aus Aufgabe 3 erraten; der Ablauf des Nachspielens ging nur mit der `soll.yaml` aus Aufgabe 3 (`db:`-Präfix am Ziel). | `AGENTS.md` §5 Regel 5 (Anleitung bildet Ist ab), Maintainability | `docs/user/testserver-manuell.md` · „bringt das Ziel auf die Struktur der Quelle“ | nein (von Hand nachgespielt, siehe oben) | Schritt der Anleitung ohne Befehl |
| F-5 | INFO | Die Grenze von F3 im Protokoll 2 ist bis auf einen Satz sauber geführt (Quelltext von d-migrate nicht gelesen, nur 2022, nur `on_delete`, je Fall ein Lauf). Der Kopfsatz „Ergebnis“ und Vorschlag 2 („am echten SQL Server ebenso“) tragen die Grenze nicht im selben Satz. Bei der Fortschreibung der Anleitung (F-1) gehört „SQL Server 2022, d-migrate 1.12.0, nur `on_delete`“ in den Satz. | `AGENTS.md` §3.8, Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“ (nur Hinweis, solange die Anleitung die Aussage noch nicht trägt) | `docs/messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md` · „Einordnung (a): Verhalten von d-migrate, keine Abweichung des Testservers“ | nein | Bedingung nicht im selben Satz |
| F-6 | INFO | Die Läufe beider Protokolle ruhen auf Befehlen im Scratchpad (90 Dateien, Hilfsskripte), nicht im Repo; beide Protokolle sagen es („Ablage“, „Grenzen“). Wiederholbar ist ohne Skript nur nach der Anleitung (Protokoll 1) beziehungsweise mit `tools/jdbc-messung/Aufnahme.java` (Protokoll 2, liegt im Repo). Der Slice §3 hält ein Skript für optional. Zahlen sind Exit-Codes und Auszüge, keine Zeit- oder Mengenzahlen mit Anspruch. | Skill „Messzahl aus einem Erzeuger, der nicht im Repo liegt“ (keine Kategorie erreicht) | `docs/messungen/abnahme-d-migrate-2026-11-12.md` · „Befehle und Ausgaben liegen im Scratchpad des Laufs“ | nein | Beleg ohne Erzeuger im Repo (benannt) |
| F-7 | INFO | Register-Zuordnung im Abnahmeprotokoll (getroffen 9, 14, 15(a); nicht getroffen 10 bis 13 mit 13(a) in der Wirkung, 15(b), 15(c)) stimmt mit den Zeilen der Tabelle D überein. Offene Folgen sind Paarung und Closure: Punkt 16 und Zeigerkorrekturen im Register, Abnahme-F3, Haken, Closure-Notiz stehen bewusst noch aus (alle Haken der DoD sind im Diff ungesetzt; kein Finding). | Register-Paarung (Closure) | `docs/plan/planning/observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md` · „Stand“ | nein | Folgearbeit für Planner |

## Prüfungen zu den Schwerpunkten

1. **§3.8, Zeile je Funktion:** Alle Zeilen sind mit Exit-Code, Auszug und Zustand danach geführt; die nachgespielten Läufe (Import mit/ohne Neustart, Transfer Exit 3, Transfer Standardpfad und `update`) stimmen. „Nicht gelaufen“ steht ehrlich zweimal (Aufgabe 5 Tabelle und Grenzen); Aufgabe 4 und das Zugriffs-/Port-/1-MiB-Szenario sind dort ohne Grün-Behauptung. Kein Widerspruch zwischen Aufgabe-5-Tabelle und Grenzen-Absatz.
2. **F3:** Tabelle der 14 Läufe stimmt in sich (7 Zeilen, je Seite; 4 Zeilen mit Exit 5/1, 3 mit 0/0; 153 s = 11:37:59 bis 11:40:32). Die Aussage ist im Protokoll auf Verhalten, 2022, `on_delete` und je einen Lauf begrenzt; die Anleitung trägt sie noch nicht (F-1). Die Behauptung, Spaltenmetadaten des Testservers seien gleich, wird im Protokoll selbst auf Tests der Bibliothek verwiesen und nicht als Messung geführt.
3. **Anleitung:** Neustart-Hinweis, Port statt 1433 und Fehlerzeilen der Tabelle bestätigt; keine Chronik (die „gefahren am“-Angaben folgen dem bestehenden Muster der Datei); Grep auf „bisher / noch nicht / vorläufig / Zielzustand / nicht gebaut / seit Slice“ ohne neuen Treffer. Befund F-1 und F-4.
4. **Roadmap:** „Arbeit: slice-abnahme-d-migrate (Welle welle-testserver-manuell).“ und „in `in-progress/`: slice-abnahme-d-migrate; zuletzt geschlossen: …“ nennen Zustand und auflösbaren Anker, keine Chronik; Drift-Log-Zeilen ändern nur Pfade (`open/` nach `in-progress/`).
5. **d-check:** `make docs-check` grün; neue Tabellen im Rahmen.
6. **Spec-Übereinstimmung:** Die Anleitung weicht in Happy Path, Boundary und Vorbehalt INT/DECIMAL (Zeilen zu `BIGINT`, Treiber vor dem Server) nicht von `SPEC-040`/`TDS-FA-12` ab; Out-of-Scope (`data profile` als Negativfall, Exit 5) ist im Abnahmeprotokoll geführt.
7. **Berührung:** kein Produktcode, keine Spec, keine ADR, kein `@Suppress`, keine Gate-Lockerung.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `docs/plan/planning/done/`, `observations/`, `docs/reviews/` (Verweis-Nachzüge der Moves) | geprüft, ohne Befund (nur Pfadumschreibungen `open/`/`next/` nach `in-progress/`) |
| `docs/plan/planning/in-progress/roadmap.md` | geprüft, ohne Befund |
| `src/`, `testserver/`, `spec/`, `docs/plan/adr/`, `config/`, `Makefile`, `tools/` | geprüft, ohne Änderung im Diff |
| `docs/messungen/abnahme-d-migrate-2026-11-12.md` | geprüft; Befunde F-3, F-6 |
| `docs/messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md` | geprüft; Befund F-5 |
| `docs/user/testserver-manuell.md` | vollständig gelesen; Befunde F-1, F-4 |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 2 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Nachmessung in Protokoll, Anleitung nicht nachgezogen · Plan-Tabelle ohne zweite Messdatei · Begriffs-Kollision der Fund-Arten · Schritt der Anleitung ohne Befehl

Gesamtlaufzeit des Laufs rund 15 Minuten; größte Posten: Lesen von Protokollen, Anleitung und Register (etwa 8 Minuten), Nachspielen der Anleitung mit zwei Containern (rund 1 Minute Lauf), `make docs-check`/`flaeche`/`kopplung-zahlen`/`gates` zusammen 15 s.
