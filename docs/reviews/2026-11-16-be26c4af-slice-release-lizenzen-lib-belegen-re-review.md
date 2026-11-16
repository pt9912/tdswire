# Review-Report: slice-release-lizenzen-lib-belegen (Re-Review) — 2026-11-16

**Review-Art:** Code (Doku und Beleg), gegen Plan, ADR und Hard Rules; nicht gegen die DoD. Re-Review: nur die Commits nach dem Report-Stand, plus Gesamtstand der berührten Aussagen

**Gegenstand:** Branch `slice-release-lizenzen-lib-belegen`, `git diff 7559de60..be26c4af` (Commits `cef5ef0e`, `be26c4af`; 7 Dateien: Messprotokoll, Slice-Plan §3, drei Register-Dateien, eine neue Lizenzdatei, der Erst-Review-Report). Beide Commits ändern keinen Quelltext, kein Skript, keine Make- oder Build-Datei.

**Skill:** `.harness/skills/reviewer.md` @ `be26c4af` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:**

- Slice [slice-release-lizenzen-lib-belegen](../plan/planning/in-progress/slice-release-lizenzen-lib-belegen.md) (§2, §3)
- [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) (Entscheidung 3, Mess-Pflicht 8), [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md) (Offen)
- [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)
- `AGENTS.md` 3.7, 3.8, 5; Erst-Review [2026-11-16-7559de60-slice-release-lizenzen-lib-belegen-review](2026-11-16-7559de60-slice-release-lizenzen-lib-belegen-review.md)

Selbst gefahren (nur lesend): `sha256sum` über alle 7 Dateien unter `lizenzen/lib/`; `curl` auf raw.githubusercontent.com (NOTICE-Quelle), api.github.com (Wurzel-Listen der Tags, `contents/license`), repo1.maven.org (drei Jars, `unzip -l`, im Scratchpad abgelegt); Grep über Repo; `make docs-check` (Exit 0, 1512 Dateien, 0 Befunde). Nicht gefahren: `make gates`, `make clean-build`, `make e2e`, Docker-Bau. Keine künstliche Last; verfügbarer Speicher 11,3 bis 12,2 GiB.

## Status der Findings des Erst-Reviews

| ID | Alt | Status | Nachweis |
|---|---|---|---|
| F-1 | MEDIUM | behoben | `license/NOTICE.txt` von Coroutines 1.10.1 neu abgerufen: SHA-256 `421d45ac…26c4`, 452 Byte; gleich dem Hash der abgelegten Datei und der Beleg-Tabelle. Die Wurzelliste des Tags (api.github.com) führt `license`; `contents/license` hat genau einen Eintrag `license/NOTICE.txt`. Protokoll nennt jetzt Fund, Hash, Größe, Suchweg und den ersten Fehlversuch; Befund 3 und „Kann das ZIP sie erfüllen?“ sind nachgezogen. |
| F-2 | LOW | behoben | `unzip -l` an den drei Maven-Central-Jars: stdlib `MANIFEST.MF`, drei `.kotlin_module`, `versions/9/module-info.class`, kein `com.android.tools/`, kein `proguard/`; Coroutines `MANIFEST.MF`, `kotlinx-coroutines-core.kotlin_module`, `kotlinx_coroutines_core.version`, `com.android.tools/` (5 Einträge inkl. Verzeichnisse), `proguard/` (2 Einträge), `versions/9/`; annotations `MANIFEST.MF`, `versions/9/`. Das Protokoll sagt dasselbe (eine Beobachtung: es nennt die Quelle der Jars „Dateien aus `build/libs/lib`“, ich habe die Central-Jars gelesen; deren Hash ist laut Protokoll gleich). |
| F-3 | LOW | behoben | Protokoll trennt „Zuordnung, nicht Beleg“: die NOTICE gilt nach eigenem Wortlaut der „Kotlin Compiler distribution“; belegt ist nur der Ort im Projekt-Tag; ob (d) für das Jar greift, ist offene Frage 2. |
| F-4 | LOW | behoben | Slice §3 führt die Zeile „dieser Slice, §7“. |
| F-5 | LOW | behoben | `observation.md` nennt jetzt „Kotlin-Standardbibliothek, Coroutines, JetBrains-Annotations“. |
| F-6 | INFO | behoben | `state.md`: „Fundstellen: …“ statt „bleiben als frühere Fundstellen“. |
| F-7 | INFO | teilweise (kein Befund) | Das Protokoll sagt jetzt ausdrücklich, dass der Review die Artefakt-Seite nicht wiederholt und der Verifier sie am Docker-Bau prüft. Ich habe sie ebenfalls nicht wiederholt (Docker-Bau außerhalb der Sensoren der Änderungsart). |

## Findings (neu)

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | LOW | Frage 4 sagt „Stand des Tags kann sich nicht ändern“. Ein git-Tag lässt sich verschieben; belegt ist nur der Stand beim Abruf am 2026-11-16 (Tag-Name, kein Commit-Hash genannt). | `AGENTS.md` 3.8 | `docs/messungen/lizenzen-lib-2026-11-16.md` · `Stand des Tags kann sich nicht ändern` | ja: Grep; `git/trees/<Tag>` liefert den Commit-SHA, der im Protokoll fehlt | Zusage breiter als Beleg (Unveränderlichkeit eines Tags) |
| N-2 | LOW | Die Wurzel- und `license/`-Listen sind gelesen, die Unterverzeichnisse der Tags (zum Beispiel `kotlinx-coroutines-core/`, `common/`) nicht. Die Zeile „Hinweisdateien unter anderem Namen als NOTICE.txt“ wurde aus „Nicht belegt“ gestrichen; der Satz „weitere Hinweisdateien an der Wurzel führt der Tag nicht“ ist auf die Wurzel begrenzt und belegt, die Streichung trägt weiter, als gelesen wurde. | `AGENTS.md` 3.8 | `docs/messungen/lizenzen-lib-2026-11-16.md` · `Rechtliche Wirkung der Beilage; ob das ZIP „Derivative Work“` (Abschnitt „Nicht belegt“) | ja: Diff gegen `7559de60` | Streichung aus „Nicht belegt“ breiter als Lesung |
| N-3 | INFO | Befund 3 nennt „ohne sie fehlte sie im ZIP, ohne dass ein Sensor es meldet“: Konjunktiv über den Zustand ohne die Datei; trägt aber eine echte Kopplung (der Bau-Slice legt aus `lizenzen/lib/`). Kein Verstoß, ein Hinweis nach 3.7. | `AGENTS.md` 3.7 | `docs/messungen/lizenzen-lib-2026-11-16.md` · `ohne sie fehlte sie im ZIP` | nein | Kommentar-/Aussageform |
| N-4 | INFO | Offene Frage 4 steht als „(Beantwortet durch Nachlesen …)“ unter „Offene Fragen an den Nutzer“; Frage 2 bündelt jetzt vier Teilfragen (Dritt-Lizenzen, zwei NOTICEs, Form, Boost-Ausnahme). Jede ist einzeln benannt, die Zählung 1 bis 4 bleibt eindeutig. | Maintainability | `docs/messungen/lizenzen-lib-2026-11-16.md` · `4. (Beantwortet durch Nachlesen` | nein | Lesbarkeit der Fragenliste |

## Nachgeprüft, ohne Befund

**(a) Byte-Gleichheit.** `find lizenzen/lib -type f | sort | xargs sha256sum`: sieben Dateien, jeder Hash gleich der Zeile der Tabelle „Ablage“ (annotations `8c1e966c…`, stdlib `cfc7749b…`, `0b09a83d…`, `8d8291ca…`, `d1bc53b4…`, Coroutines `b1febe63…`, `421d45ac…`). Die neue NOTICE-Quelle ist erneut abgerufen und byte-gleich (Hash, 452 Byte). Die Wurzel-Listen von Coroutines (`…/git/trees/1.10.1`: `license`, `LICENSE.txt`, `README.md`, `CHANGES.md`, kein `NOTICE*`, kein `COPYING*`) und annotations (`…/git/trees/23.0.0`: `LICENSE.txt`, `README.md`, `CHANGELOG.md` u. a., kein `license`, kein `NOTICE*`, kein `COPYING*`) stimmen mit dem Protokoll überein.

**(b) Nachgezogene Stellen.** Grep über Protokoll, Register (`state.md`, `observation.md`, `evidence/`), Slice, `spec/`, `docs/`, `harness/` nach „keine NOTICE“, „keinen Gegenstand“, „erfüllbar“, „nicht gelesen“, „Wurzelverzeichnis“, der Bibliotheken-Liste: im Protokoll kein Rest der alten Lesart; Befund 3, Abschnitt Coroutines, offene Fragen 2 und 4, „Nicht belegt“ nachgezogen; `evidence/…`: „Lizenz- und Hinweisdateien“, „offenen Fragen“; `state.md`, `observation.md` stimmen. Treffer außerhalb (Slice-Zeilen 54, 55, 97, 105; frühere Slices und Reviews; [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) Zeile 83) tragen die alte Lesart nicht (Slice-DoD verlangt Hinweisdatei „wenn die Lizenz sie verlangt“, [ADR-0050](../plan/adr/0050-github-release-im-tag-lauf.md) nennt Lockfile-Koordinaten). `releasing.md` Schritt 7 („Hinweise … führt das Artefakt …“) bleibt nach Plan stehen (Slice §1/§2, Bau-Slice). Kein Treffer in `spec/` zu Lizenzhinweisen der Bibliotheken.

**(c) META-INF.** Siehe F-2; Beschreibung stimmt je Jar mit der Ausgabe von `unzip -l` überein, ebenso „in keinem Jar eine Datei mit LICENSE oder NOTICE im Namen“.

**(d) Aussagen und Vorbehalt.** „Lesung, keine Rechtsauskunft“ steht im Kopf und in Befund 3; Rechtsfragen (Derivative Work, (b), (c), Boost-Ausnahme, Zuordnung der Kotlin-NOTICE) sind den Fragen 1 und 2 zugeordnet; Frage 3 (Jahreszahlen) und Frage 4 (jetzt beantwortet) einzeln benannt. Keine Entscheidung wird still getroffen.

**(e) `make docs-check`:** Exit 0 (direkt gelesen), 1512 Dateien, 0 Befunde.

**Nachbesserung ohne zweiten Review (Skill-Regel).** Die Commits `cef5ef0e`, `be26c4af` ändern ausschließlich Doku, Register und eine Lizenz-Datendatei; dieser Re-Review liegt vor. Haken: kein `- [x]` im Diff.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `lizenzen/lib/` (7 Dateien) | geprüft, ohne Befund |
| `docs/messungen/lizenzen-lib-2026-11-16.md` | Befunde N-1, N-2, N-3, N-4; übrige geänderte Aussagen tragen |
| Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage/` | geprüft, ohne Befund |
| Slice-Plan (§3 Zeile ergänzt) | geprüft, ohne Befund; keine Haken gesetzt |
| Hard Rules 3.1, 3.2, 3.5 (keine ADR berührt), 3.6; Spec-Straten (keine Spec-Datei berührt) | geprüft, ohne Befund |
| Nicht lesbar für mich | Hashes am `build/libs/lib` des Docker-Baus (Verifier); die Rechtsfragen selbst |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 2 |
| INFO | 2 |

## Verdikt

F-1 bis F-6 sind behoben, F-7 ist als Grenze ausgewiesen. Neu: zwei LOW (N-1, N-2), zwei INFO. Kein HIGH, kein MEDIUM; das Verdikt des Erst-Reviews (ein MEDIUM) ist ausgeräumt. Ein weiterer Re-Review ist nicht nötig, wenn N-1 und N-2 angenommen oder begründet bleiben.
