# Verifikationsbericht: slice-referenzabgleich-adr-0028-nachzug (HEAD 0a518989)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-referenzabgleich-adr-0028-nachzug](../plan/planning/in-progress/slice-referenzabgleich-adr-0028-nachzug.md). Maßstab: DoD §2, Plan §3, [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (Accepted), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), `AGENTS.md` §3.4, §3.5, §3.7, §3.8.
* Stand: Zweig `slice-referenzabgleich-adr-0028-nachzug`, HEAD `0a518989` (Umsetzung `0acbaf39`, Review-Report `f030821c`, Nacharbeit `0a518989`), Basis `main` `603e7486`; Arbeitsbaum vor diesem Bericht sauber. DoD-Haken: keine gesetzt, und ich setze keine.
* Eingang: Review [2026-11-13-0acbaf39-review-slice-referenzabgleich-adr-0028-nachzug.md](2026-11-13-0acbaf39-review-slice-referenzabgleich-adr-0028-nachzug.md) (F-1 bis F-7). Art der Änderung: Doku oder Spec (Dockerfile und Make nur Kommentare), daher kein Gradle-, `clean-build`-, `e2e`- oder `vergleichslauf-d`-Lauf.

## Urteil

**Konform, ohne HIGH und MEDIUM.** Alle drei Liefer-Punkte sind am End-Stand belegt; jede Aussage über 2022 und 2025 trägt höchstens so weit wie [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) und `docs/messungen/referenzabgleich.md` (`AGENTS.md` §3.8). F-1 bis F-4 des Reviews sind aufgehoben, F-5 bis F-7 benannt. Drei LOW und zwei INFO stehen unten. Offen sind Aufgaben des Planners: Haken, Closure-Notiz, Register.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `make docs-check` | Exit 0, 2 s (vor dem Commit dieses Berichts; danach erneut, siehe unten) |
| `make flaeche` | Exit 0, 1 s |
| `make kopplung-zahlen` | Exit 0, 1 s |
| `make gates` | Exit 0, 9 s; kurz, weil Docker-Cache und Stempel für unveränderte Eingänge greifen (die Änderungen liegen außerhalb der Stage-Eingänge); Log endet mit `gate-zuordnung`, `gate-zuordnung-selbsttest` 33 Fälle, `stempel-eingaenge` ok |
| `make skript-selbsttest` | nicht gefahren: Diff berührt kein Skript (`tools/mk/vergleichslauf-d.mk` und `Dockerfile` nur Kommentare) |

Gesamtlaufzeit rund 10 Minuten, fast alles Lesen von Diff, Messprotokoll, ADR und Grep; Sensoren zusammen 13 s. Keine künstliche Last, kein Gradle.

## DoD je Punkt

| DoD-Punkt | Stand | Beleg (Datei:Zeile am End-Stand) |
|---|---|---|
| 1 Spezifikation: `SPEC-045` und Zeile `SPEC-050` | erfüllt | `spec/spezifikation.md:1546`: zwei Instanzen mit Digests `sha256:cf8f0ab2…` und `sha256:245f6b1c…` (stimmen mit `referenztest-dotnet/Dockerfile:84,87`), `2025-latest` als Referenzinstanz des TDS-8.0-Pfads ausdrücklich als Setzung, Bedingung im Satz (Einzelmessung, ein Host, selbst erzeugtes Zertifikat, `tlscert`/`tlskey`, Vertrauensanker beim Client, vier Varianten); 2022: scheitert, Log „TLS 1.0 bis 1.2“, „nicht geklärt“ ob andere Konfiguration; 2025: gelingt ohne Zusatzeinstellung, „TLS 1.0 bis 1.3“, Standardzertifikat „nicht gemessen“. Alle Zahlen und Bedingungen gegen B-3, B-4 und [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (Kontext) gedeckt. `spec/spezifikation.md:2174` (`SPEC-050`): beide Tags, je ein Container. Kein Slice, keine ADR, kein Datum im Text (§3.4) |
| 2 Pläne | erfüllt | `done/slice-referenzabgleich-live.md:40` (§1: gemessene Lage statt „gefolgert“/„entfällt“; „dieser Slice gleicht den Pfad nicht ab“ ist Aussage zum Slice, nicht zum Server); weitere Stellen `:112`, `:123`, `:164`, `:194`; `done/slice-referenzabgleich-mitschnitt.md:39` (§1) und `:159`. Alle mit Bedingung (Einzelmessung 2026-11-01, ein Host) und Verweis auf B-3/B-4 |
| 3 Messwerkzeug | erfüllt | `referenztest-dotnet/Dockerfile:86-87` (Stufe `referenzinstanz-2025`, [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md), Bedingung); `tools/mk/vergleichslauf-d.mk:14-16` (Läufe `<lauf>-2022`/`-2025` je Instanz, Setzung ausdrücklich „nicht gemessen“, `*-tds8` gemessen); `docs/messungen/referenzabgleich.md:103` (B-6 „Geschlossen“ mit Verweis auf [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) `Accepted`, „nicht geklärt“ für die 2022-Konfiguration) |
| Nachweis: `make gates` grün | erfüllt | s. o. |
| Nachweis: Review, Report | erfüllt | [Review-Report](2026-11-13-0acbaf39-review-slice-referenzabgleich-adr-0028-nachzug.md); die Nacharbeit `0a518989` hatte keinen zweiten Review und ist hier gezielt gelesen (V-3) |
| Doku-Update öffentlicher Vertrag | erfüllt/n.a. | `docs/user/` und `README.md` ohne Aussage zum Thema (Grep); `harness/README.md` nennt „2022, 2025“ bereits |
| Closure-Notiz, Register, Ausgänge §6, Paarungen | offen (Planner) | Haken nicht gesetzt; §6 trägt noch „offen bis zur Annahme“ |

Treue zum Lauf: Die Aussage „die Läufe der Schritte, Mitschnitte und Messreihen gibt es für beide Instanzen“ deckt `tools/vergleichslauf-d.sh:162-179` (`live`, `aufnahme`, `mitschnitt`, `wire`, `bulk`, `jdbc`, `schema` jeweils `-2022` und `-2025`; `2022-tds8` erwartet Strict `scheitert`, `2025-tds8` `gelingt`).

## Review-Findings gegen den Endstand

| ID | Stand am End-Stand |
|---|---|
| F-1 (MEDIUM, SPEC-050 im Kopf) | aufgehoben: Kopf `…-nachzug.md:12` „SPEC-045, SPEC-050“, DoD-Zeile 42 und Plan-Tabelle nennen `SPEC-050`. Der Wortlaut der DoD-Zeile hängt „(Referenzinstanzen)“ vor den auf `SPEC-045` bezogenen Klammersatz „Absatz Grenzen“ (V-2, LOW) |
| F-2 (Rollenverteilung enger als der Lauf; Setzung vs. Messung) | aufgehoben: Spec `:1546` und `.mk:14-16` sagen „beide Instanzen für alle Läufe“, die Rolle als Setzung; „gemessen“ bezieht sich nur auf `Strict` |
| F-3 (B-6 Zitat, „seither“) | teilweise aufgehoben: „schreibt wörtlich“ ist „lautete … Stand 2026-11-01“, „seither“ ist weg; das Zitat steht weiter als Befund eines Zustands, den der Plan nicht mehr trägt (V-4, INFO, kein Fehler: der Befund beschreibt den Fund des Tages) |
| F-4 (`done/slice-referenzabgleich-live.md` §8) | aufgehoben: `:164` „an 2022 in B-3 abgelehnt, an 2025 in B-4 als Sonde gemessen“, `:194` Register-Zeile angepasst |
| F-5 (Digests in zwei Dokumenten) | INFO, bleibt: Plan verlangt „Digest“; Pflegestellen Dockerfile, Spec, Messprotokoll |
| F-6 (Fortschreibung über DoD-Wortlaut) | INFO, bleibt: `:112`/`:123`/`:164` sind Ständen nachgezogen, die Plan-Tabelle §3 deckt beide Dateien |
| F-7 ([ADR-0007](../plan/adr/0007-fehler-und-attention.md)) | siehe offener Punkt für den Nutzer |

## Findings des Verifiers

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | LOW | `SPEC-050` sagt „Aufteilung nach `SPEC-045`“; `SPEC-045` benennt keine Aufteilung der Schritte (beide Instanzen führen alle Läufe), sondern nur die Rolle von `2025-latest` für den TDS-8.0-Pfad. Der Verweis ist zu eng formuliert, trägt aber keine falsche Zahl. Vorschlag: „Rollen der Instanzen nach `SPEC-045`“. | `spec/spezifikation.md:2174` |
| V-2 | LOW | DoD-Zeile 1 des Slice hängt die Zeile `SPEC-050` vor die Klammer „(Absatz Grenzen, Satz … entfällt dort)“, die nur `SPEC-045` meint; der Satz „entfällt dort“ steht in `SPEC-050` nicht. Das ist Wortlaut, der Plan trägt die Aussage im Kopf und in der Tabelle (Planner kann beim Haken glätten). | `docs/plan/planning/in-progress/slice-referenzabgleich-adr-0028-nachzug.md:42` |
| V-3 | LOW | Die Historie der Spezifikation (`## 8. Historie`) führt jede inhaltliche Änderung der Spezifikation mit Datum und Anforderungs-ID (zuletzt 2026-11-13); dieser Slice ändert `SPEC-045` und `SPEC-050` ohne Zeile. Keine DoD-Pflicht und kein Gate, aber Bruch mit der Praxis der Datei; die Historie darf Datum und ADR nennen, der Fließtext nicht. Vorschlag: Zeile `SPEC-045`/`SPEC-050` (`TDS-QA-01`, [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md)). | `spec/spezifikation.md:2176ff.` |
| V-4 | INFO | B-6 zitiert in der Vergangenheitsform („lautete“) einen Planwortlaut, der in `done/slice-referenzabgleich-live.md:40` nicht mehr steht. Das Protokoll hält den Befund des Tages; ein Zitat auf abwesenden Text ist nach `AGENTS.md` §3.7 nur tragbar, weil Messprotokolle Befunde mit Datum führen (§5 Regel 7). Kürzung nach Ermessen des Planners. | `docs/messungen/referenzabgleich.md:103` |
| V-5 | INFO | Zweite Fundstellen mit dem Stand vor der Annahme bleiben in geschlossenen Zeitdokumenten: `done/slice-antwort-tokens-an-messung-angleichen.md:63` („Proposed“), `done/slice-qa-vergleichslauf-d.md:134,157,161` (Empfehlung/„Proposed“, benannte Spec-Lücke), `done/slice-abnahme-d-migrate.md`, `done/slice-referenzabgleich-live.md:167` und `…-mitschnitt.md:167` (Steering-Loop). Sie beschreiben den Stand ihrer Zeit und tragen als Zeitdokumente; keine Aktion. Ein Registereintrag fehlt dafür nicht, die Klasse steht in `fortschreibung-laesst-zweite-fundstelle-stehen`. | siehe Spalte |

Keine DoD-Verletzung und keine Spec-Lücke gefunden, die Tests und Review nicht sähen.

## ADR-Unverändertheit und Spezifikation

`git diff 603e7486..HEAD -- docs/plan/adr` ist leer: [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (Accepted, Geschichtszeile 2026-11-13) und [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) unverändert (`AGENTS.md` §3.5). Der ADR-Index führt 0028 als Accepted. Die Änderungen in `spec/spezifikation.md` (Zeilen 1546, 2174) nennen weder ADR noch Slice noch Welle noch Datum (§3.4).

## Zweite Fundstelle (Grep)

Muster: „nicht abgleichbar“, „bietet TDS 8.0 (nicht an)“, „gefolgert“, „entfällt dort“, „nur eine Instanz“, „eine Instanz der Version“, „Proposed“ im Zusammenhang mit [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) über `spec`, `docs/user`, `docs/plan`, `docs/messungen`, `harness`, `tools`, `referenztest-dotnet`, `README.md`, `AGENTS.md`. Treffer außerhalb Zeitdokumenten: nur die Zitate in Slice, Register-Evidence, [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (Supersedes-Liste) und [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (Accepted, unveränderlich) sowie die Zeitdokumente aus V-5. `spec`, `docs/user`, `harness`, `tools`, `referenztest-dotnet`, `README.md` ohne Treffer.

**Offener Punkt für den Nutzer (kein Befund gegen den Slice):** [ADR-0007](../plan/adr/0007-fehler-und-attention.md) (Accepted, Zeile 105) sagt „der Server R bietet TDS 8.0 nicht an ([ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md))“; [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) benennt diese Stelle nicht in ihrer `Supersedes`-Liste, und eine Accepted-ADR wird nicht überschrieben. Es bräuchte eine Folge-ADR mit `Supersedes` (oder einen Nachtrag in einer ohnehin entstehenden ADR); ich habe nichts geändert. Ebenso sagt [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) Zeile 83 „der Grund, TDS 8.0 werde nicht angeboten, ist gefolgert“, das [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) für den Satz in Zeile 69 ersetzt, für Zeile 83 nicht wörtlich.

## Register (Vorschläge, nicht geschrieben)

* `BEO-TDS/spec-nachzug-nach-messung-offen`: Stand nennt den Träger `slice-referenzabgleich-adr-0028-nachzug` für `SPEC-045`; `SPEC-050` steht dort unter „Rest ohne Träger“, ist aber für die Referenzinstanzen jetzt nachgezogen. Beim Schließen des Slice den Stand auf `SPEC-045` und `SPEC-050` (Zeile Referenzinstanzen) als umgesetzt führen; der Rest bleibt offen.
* `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`: Stand bleibt offen (Slice misst nicht); Evidenz: die Pläne tragen jetzt die gemessene Lage statt der Folgerung.
* `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`: weitere Evidenz F-4 (Register-Zeile in §8 stand neben dem nachgezogenen §1/§7) und V-5 (Zeitdokumente mit dem Stand vor der Annahme, bewusst stehen gelassen). Zählt zum bestehenden Eintrag.
* Klasse „Plan nennt berührte Stelle nicht“ (F-1, `SPEC-050` fehlte im Kopf): prüfen, ob ein bestehender Eintrag (etwa zu Plan/DoD gegenüber Diff) sie führt; sonst Neuaufnahme 1×. Klasse „Rollenverteilung als Messung ausgegeben“ (F-2) steht inhaltlich bei `aussagen-ueber-messung-hinaus` (verkörpert, §3.8): Evidenz, nicht Neueintrag.
* `BEO-TDS/accepted-adr-wortlaut-…`-nah, neu 1×: „Accepted-ADR nennt eine Aussage einer anderen Accepted-ADR ([ADR-0007](../plan/adr/0007-fehler-und-attention.md)), die die Supersedes-Liste der Folge-ADR nicht trägt“ (siehe offener Punkt).
