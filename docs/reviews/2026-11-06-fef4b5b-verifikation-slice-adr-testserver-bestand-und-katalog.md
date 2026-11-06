# Verifikationsbericht: slice-adr-testserver-bestand-und-katalog (HEAD fef4b5b)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-adr-testserver-bestand-und-katalog` (`in-progress/`, Zweig `slice-adr-testserver-bestand-und-katalog`, HEAD `fef4b5b`, Basis `f12f360`; 7 Commits `e99f4da` bis `fef4b5b`) gegen DoD §2, Plan §3, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und den Review-Report `2026-11-05-f0fdc87-slice-adr-testserver-bestand-und-katalog.md`.
* Selbst gefahren (Arbeitsbaum sauber): `make docs-check`: 772 Dateien, 0 Befunde. `git diff --name-only f12f360..HEAD | grep -v '^docs/'`: leer. Diff gelesen: Messprotokoll, ADR, Index, Slice, vier Folge-Slices, Review-Report.
* Nicht selbst gefahren: `make gates`, `make test` (kein Code im Diff, nichts zu prüfen, das sie sähen); die Messzeilen des Spikes (Prototyp liegt nicht im Repo, nicht wiederholbar; gelesen und nachgerechnet, nicht wiederholt). Den Nutzerentscheid „ja deine empfehlungen“ vom 2026-11-06 habe ich nicht im Original gesehen; belegt ist er nur durch die Zitate in ADR, Index und Slice und die Zusage des Auftrags.

## Gesamturteil

**Closure-fähig: nein, solange V-1 und V-2 offen sind; beide sind Wortlaut-Nachzüge in Dokumenten, keine Neuentscheidung.** Die drei Liefer-Punkte sind in der Sache geliefert; kein Produktionscode, Spec und Lastenheft unverändert, `Accepted` mit zitiertem Nutzerentscheid, Supersedes wörtlich. 0 HIGH, 2 MEDIUM, 2 LOW, 3 INFO. Die Nachweis-Pflichten (`make gates`, Re-Review, Closure-Notiz §7, Risiko-Ausgänge §6) stehen noch offen; das ist für einen Slice in `in-progress/` erwartet und kein Befund.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Spike: Messwerte als Tabelle in Slice **und** ADR; Messpunkte a–d mit Rechner, Last, Image-Digest | erfüllt mit V-3 | Slice §3 „Messwerte“: Rechner (Linux 6.8, 20 Kerne, 31 GiB), Load 3,8 bis 23,4, Digest d-migrate `sha256:308f2775…6491`, Abbild Testserver `sha256:aae719f1…63d2`, Tabelle mit a bis d, e. ADR-Tabelle „Spike“ trägt a bis d, Einschaltung, PRELOGIN mit Zeilennummern; Rechner, Last und Digest stehen dort nur als Verweis aufs Messprotokoll (V-3). Messprotokoll „Bedingungen“ vollständig (Datum, Host, Load per `uptime`, Digests, Docker). Grenzen der Messung benannt (Prototyp, kein echter SQL Server, ATTENTION nicht gemessen). Messpunkte (b)(i) bis (iv) alle belegt (b1–b3, b5–b7, Tabelle (iii), b9–b13); „Bulk abschalten“ als b4 in ADR und Slice. |
| 2 | ADR: nächste Nummer, Bezüge, Index, Messzeile oder Setzung, „keine Transaktion“, Liste neuer Namen | erfüllt | 0033 ist die nächste Nummer (Index endete vorher bei 0032; Nr. 0034 als Verweis auf den anderen Zweig). Bezug: [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md)/-0012/-0021/-0025/-0026 (+ -0017) und TDS-Anforderungen. Index-Zeile in `docs/plan/adr/README.md` mit Status `Accepted`. Nr. 6 „Keine Transaktion (Zusage)“ ausdrücklich, samt Folge für „Bestand bleibt unverändert“. Nr. 4 listet drei neue öffentliche Namen (zweiter Konstruktor, `SimTable.onInsertBatch`, `SimColumn.maxLength`), Fassung N verworfen. Stichprobe Aussage-gegen-Messzeile: Nr. 2 (Typen: gemessen vs. Setzung getrennt), Nr. 3 (Zähler 0 als Setzung), Nr. 7 (Verbindung offen = „gelesen“), Nr. 8–10 (Setzungen gekennzeichnet) tragen. |
| 3 | Spec-Änderungsliste je Folge-Slice vollständig | erfüllt mit V-1, V-2 | Tabelle in Slice §3 deckt die fünf Folge-Slices (katalog, bulk-load, merge-output, tls erledigt, abnahme) und „dieser Slice“ für die PRELOGIN-Tabelle; die Lücken aus F-7 ([TDS-FA-11.a](../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 1, „mehrere Anweisungen in einem Text“, Form C, „OUTPUT-Klauseln“ aus „Nicht erkannt“) sind nachgezogen. Die Anschluss-Blöcke der Folge-Slices tragen die Liste, aber veraltet (V-1, V-2). |

## Prüfung der Randbedingungen

| Bedingung | Urteil | Beleg |
|---|---|---|
| Kein Produktionscode im Diff | ja | Diff nur unter `docs/`; kein `src/`, `config/`, `tools/`, `Makefile`. |
| Spec/Lastenheft unverändert | ja | `git diff f12f360..HEAD -- spec` leer; Lastenheft 0.7.1 (Wortlaut-Freigabe 2026-11-05 liegt in der Basis, `090092d` ist Vorfahre von `f12f360`). |
| `Accepted` nur mit belegtem Nutzerentscheid | ja, mit Vorbehalt | ADR-Kopf, Geschichte und Index zitieren „ja deine empfehlungen“ (2026-11-06); Wahlen einzeln benannt (Fassung F, Z3, `maxLength`, Reihenfolge, MERGE/„abschließend“, Gegendruck bleibt, Ersatz von [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md)). Das Original steht nicht im Repo (Vorbehalt oben). Die Abweichung vom Entwurf (Gegendruck innerhalb einer Nachricht entfällt) ist als Nutzerentscheid geführt (F-2 behoben). |
| Supersedes teilweise [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) wörtlich | ja | [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Kopf zitiert den ersten Satz von Entscheidung 14 buchstabengleich ([ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Zeile 95: „Gelesene Anweisungsformen bleiben die Tabelle S1, W1, W2, W3 und C aus SPEC-036“; Fettdruck weggelassen), benennt, was bleibt, und Offen 12(a). [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) im Diff unberührt (AGENTS.md §3.5). Der Index-Eintrag nennt „ersetzt teilweise [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md)“. |
| Handle-Grenze [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) | ja | [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) „bleibt unberührt“, Ersatz als Folgepflicht 1 mit Träger-Vermerk in Slice §4 (Träger bis zur Anlage: der Vermerk; Anlage gehört dem Planner). |

## Review-Findings: Stichprobe gegen den Report F-1 bis F-17

Der Report enthält 0 HIGH und 9 MEDIUM (F-1 bis F-9). Alle neun am Text geprüft, dazu LOW/INFO nach Stichprobe.

| ID | Urteil | Beleg im HEAD |
|---|---|---|
| F-1 (Positiv-Satz) | behoben | Konsequenzen nennen Exit 1 für `schema compare` (c6), Exit 5 (c5), INT-Fehler (b3), Parser-Anpassung (a7); Grenzen nennen Kriterium. |
| F-2 (Gegendruck, „Weg A“) | behoben | Nr. 5 (i) führt die Abweichung als Nutzerentscheid vom 2026-11-06; Weitergaben heißen Z1 bis Z3, „Weg A“ nur für die Nutzerwahl. |
| F-3 („quadratisch“) | behoben | a5: „nur eine Größe gemessen, ob quadratisch, nicht gemessen“; ADR „Faktor 3 bis 7, Form nicht gemessen“. |
| F-4 (Typabbildung) | behoben | ADR Nr. 2: `integer`, `nvarchar(4000)`, `date`, `decimal` gemessen; `bigint`, `bit`, `time`, `datetime2` Setzung, Skala offen. |
| F-5 (INT mit skip) | behoben | Grenze (1) und Anschluss in abnahme tragen die Bedingung im Satz (Verengung, Parser, Handle-Grenze). |
| F-6 (Beleg nennt Aussage nicht) | behoben | Nr. 7 „gelesen, im Spike nicht gesondert gemessen“; Nr. 3 Zähler 0 als Setzung. |
| F-7 (Spec-Liste) | behoben | Katalog-Zeile nennt [TDS-FA-11.a](../../spec/spezifikation.md#tds-fa-11a--simulations-backend), „nicht erkannt“, Form C; merge-Zeile nennt „OUTPUT-Klauseln“; Literale in bulk-Zeile. |
| F-8 ([ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md)) | behoben | Supersedes teilweise, wörtlich (siehe oben). |
| F-9 (Quelle für Handvektoren) | behoben | Folgepflicht Umsetzung: „keine Bytes … Handvektoren aus erneutem Mitschnitt zu beschaffen“. |
| F-10 (Zahlen) | behoben | b13 jetzt 6,3 s je Nachricht einschließlich Start; Vergleich 2,4-fach mit „zwei Einzelmessungen unter wechselnder Last“. |
| F-11 (Zustandsfelder) | teils | Slice §6 und Start-Trigger in bulk/merge nachgezogen; **aber** der Entwurfs-Vermerk in den Anschluss-Blöcken blieb (V-1). |
| F-12/F-13 | behoben | Überschrift ohne Rolle/Datum; Kosten von Fassung F (Vergleich beim Integrator, `ALTER`-Struktur) in der ADR genannt. |
| F-15 | behoben | b4 in ADR-Tabelle („Massenladen abschalten“). |
| F-14, F-16, F-17 (INFO) | kein Nachzug nötig; F-17 ist durch die Bestätigung des Nutzers (ein Massenladen ist eine Anweisung) in Nr. 6 geschlossen. |

Der Report selbst wurde nach den Korrekturen **nicht neu gefahren**; die Aussage „behoben“ beruht auf meiner Lektüre des HEAD, nicht auf einem Re-Review.

## Findings

| ID | Kategorie | Befund | Pfad | Klasse |
|---|---|---|---|---|
| V-1 | MEDIUM | Alle vier Anschluss-Blöcke der Folge-Slices beginnen noch mit „[ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) …, Entwurf `Proposed`; gilt erst mit `Accepted`“, obwohl derselbe Absatz danach „Entschieden (ADR `Accepted`, 2026-11-06)“ sagt. Ein Leser findet im selben Block zwei Stände; der Block ist das, woran der Start des Folge-Slices liest. Zustandsfeld nicht mit dem Entscheid nachgezogen (AGENTS.md §3.7). | `docs/plan/planning/in-progress/slice-katalog-und-ddl-zustand.md:77`, `slice-schreibpfad-bulk-load.md:76`, `slice-schreibpfad-merge-output.md:76`, `slice-abnahme-d-migrate.md:74` | Zustandsfeld widerspricht sich |
| V-2 | MEDIUM | Spec-Änderungsliste (DoD-Punkt 3) widerspricht der gewählten Entscheidung in der Zeile von `slice-schreibpfad-bulk-load`: der Anschluss-Block sagt SPEC-035 „Weitergabe je Zeile“ und SPEC-059 „nichts in Weitergabe Z1“, gewählt ist Z3 mit `onInsertBatch` (neuer öffentlicher Parameter von `SimTable`, ADR Nr. 4). Die Tabelle in Slice §3 führt bulk/SPEC-059 noch als Wahl („nichts bei Weitergabe Z1; ein bis zwei Namen bei B; ein Parameter von `SimTable` bei C“; „B/C“ sind nach der Umbenennung Z-Namen ohne Bezug). Der Anschluss in `slice-katalog-und-ddl-zustand` nennt SPEC-059 „zweiter Konstruktor … oder vier Namen, je nach Entscheid“ und lässt `SimColumn.maxLength` aus (Tabelle in §3 hat es). Wer SPEC-059 im Folge-Slice schreibt, bekommt aus dem Anschluss nicht die dritte Namensliste der ADR. | `slice-adr-testserver-bestand-und-katalog.md` §3 Tabelle Zeile bulk-load; die Anschluss-Blöcke von bulk-load und katalog-und-ddl-zustand | Änderungsliste nicht auf die Entscheidung gezogen |
| V-3 | LOW | DoD-Punkt 1 verlangt Rechner, Last und Image-Digest „als Tabelle im Slice und in der ADR“. Der Slice trägt sie in der Tabelle; die ADR trägt die Messwerte als Tabelle, verweist für Rechner, Last und Digest aber auf das Messprotokoll (Kontext, Zeile „Spike“). Inhaltlich vollständig und auflösbar, dem Wortlaut nach nur teilweise. | `docs/plan/adr/0033-…md` Abschnitt Kontext | DoD-Wortlaut enger als die Lieferung |
| V-4 | LOW | ADR „Grenzen, die diese Entscheidung nicht auflöst“ zählt (1), (4), (2), (3); die Punkte stehen ungeordnet, und (4) ist mitten in (1) und (2) eingefügt. Kein inhaltlicher Fehler. | `docs/plan/adr/0033-…md` Konsequenzen | Lesbarkeit |
| V-5 | INFO | Offene Träger, die der Slice selbst benennt: neuer Folge-Slice für die ADR zur Handle-Grenze ist noch nicht angelegt (Träger bis dahin: Vermerk in §4); Gegenmessung INT am echten SQL Server hängt an der EULA-Entscheidung des Nutzers. Vor Closure braucht jeder diesen Träger. |
| V-6 | INFO | `make gates` ist nicht gelaufen (kein Code); die Nachweis-Pflicht bleibt für den Implementer vor Closure. `make docs-check` ist grün (772 Dateien, 0 Befunde). |
| V-7 | INFO | Die Zeilenangaben b/c/d/e des Messprotokolls stützen die ADR; ich habe sie gelesen und stichprobenhaft nachgerechnet (a5: 29,7/8,8 = 3,4 und 40,5/5,9 = 6,9 gleich „Faktor 3 bis 7“; b13: 31,4/5 = 6,3 gleich), nicht wiederholt. Alles Gemessene bleibt Einzelmessung eines nicht abgelegten Prototyps gegen kein echtes SQL-Server-Verhalten; die ADR sagt es (Annahme, die kippt). |

## Bedingungen für die Closure

1. V-1 und V-2: Anschluss-Blöcke der vier Folge-Slices und die Zeile bulk-load/SPEC-059 in Slice §3 auf den Stand `Accepted` (Z3, `onInsertBatch`, `SimColumn.maxLength`, Fassung F) ziehen. Dann Re-Review der geänderten Stellen.
2. `make gates` grün, Closure-Notiz §7 und Risiko-Ausgänge §6 (jedes Risiko genau ein Ausgang), Beobachtungs-Register.
3. Folge-Slice für die ADR zur Handle-Grenze anlegen (Planner), sonst steht der Träger nur als Vermerk.
