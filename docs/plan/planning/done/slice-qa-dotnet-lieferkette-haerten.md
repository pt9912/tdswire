# Slice slice-qa-dotnet-lieferkette-haerten: .NET-Restore: Signaturprüfung und Schwachstellenprüfung entscheiden

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-025

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die beiden befristeten Ausstiege des .NET-Restore aus [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) werden beendet oder dauerhaft entschieden: [CO-001](../../carveouts/done/CO-001-nuget-audit-aus.md) (`NuGetAudit=false`) und [CO-002](../../carveouts/done/CO-002-nuget-signaturpruefung-fehlt.md) (keine Signaturprüfung der Pakete).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Szenarien gegen den Server dieses Repos — Träger ist [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md); dieser Slice berührt die Lieferkette des Restore, keine Testinhalte.
- Vergleichslauf D, EULA-Prüfung — Träger ist [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Liefer-Punkt 1 — Signaturprüfung:** Eine funktionierende `trustedSigners`-Liste (Autorenzertifikate der Pakete im Lock, Repository-Zertifikate von nuget.org) und `signatureValidationMode=require` in `referenztest-dotnet/nuget.config`, belegt durch einen grünen Lauf von `make referenztest-dotnet` und einen roten Lauf, sobald **weder** der Autoren- noch der Repository-Eintrag eines Pakets in der Liste steht (enge Lesart, Nutzerentscheidung 2026-11-06, V-1: die Einträge wirken als ODER; ein unbekannter Autor mit nuget.org-Gegensignatur bleibt grün, solange der Repository-Eintrag steht; `owners` am Repository erst bei Bedarf); [CO-002](../../carveouts/done/CO-002-nuget-signaturpruefung-fehlt.md) ist aufgelöst (Trigger (a)). Der Wortlaut „bei einem Paket, dessen Signierer nicht in der Liste steht“ galt vor der Messung des ODER und ist hier angeglichen.
- [x] **Liefer-Punkt 2 — Schwachstellenprüfung:** Der Nutzer entscheidet, ob `NuGetAudit=false` dauerhaft bleibt. Bleibt es, braucht der Verzicht eine ADR (offen, ob eine ADR nötig ist: Begründung dort), und `make referenztest-dotnet-audit` bleibt das manuelle, nicht reproduzierbare Werkzeug; wird der Audit wieder angeschaltet, ist zu entscheiden, wie ein vom Abfragetag abhängiges Ergebnis mit `make gates` vereinbar ist. [CO-001](../../carveouts/done/CO-001-nuget-audit-aus.md) ist danach aufgelöst (entschieden: dauerhafter Verzicht, [ADR-0035](../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) Entscheidung 2, Trigger (b)).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `harness/README.md` §Sensors, Zeile „Nicht behauptet".
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
| `referenztest-dotnet/nuget.config` | update | `trustedSigners`, `signatureValidationMode` ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) |
| `referenztest-dotnet/TdsWire.Referenztests.csproj` | update | `NuGetAudit` nach Entscheidung |
| `docs/plan/carveouts/` | update | beide Carveouts nach `done/` |
| ADR | neu, falls der Nutzer einen dauerhaften Verzicht entscheidet | Gate-Lockerung braucht eine ADR |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) liegt in `done/`; der Nutzer hat eine Zeitbox für den `trustedSigners`-Versuch gesetzt.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der `trustedSigners`-Versuch sprengt die Zeitbox; dann eigener Slice je Liefer-Punkt.
- `in-progress` → `open` (blockiert — Carveout?): die Entscheidung des Nutzers zum Audit fehlt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, beide Carveouts nach `done/` verschoben und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der aus [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) bekannte Befund: mit dem Repository-Zertifikat als einzigem Eintrag scheiterten Pakete („signed but not by a trusted signer“); der Aufwand einer vollständigen Liste ist nicht gemessen. — **Ausgang:** *eingetreten, gemessen, entfallen als Hindernis.* Der Befund stimmt für genau diese Konfiguration: nur `1F4B311D…` als Eintrag lässt `Microsoft.SqlServer.Server 1.0.0` und `xunit.abstractions 2.0.3` scheitern, weil beide ein anderes Repository-Zertifikat tragen. Der Aufwand der vollständigen Liste ist gemessen: fünf Autoren- und drei Repository-Zertifikate für die 36 Pakete, ein Lauf je Zeile des [Messprotokolls](../../../messungen/dotnet-signaturpruefung-2026-11-06.md) (Einzelmessung, ein Host).

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

- **Belege:** Review [2026-11-06-d08d891a-slice-qa-dotnet-lieferkette-haerten](../../../reviews/2026-11-06-d08d891a-slice-qa-dotnet-lieferkette-haerten.md) (0 HIGH, 1 MEDIUM, 3 LOW, 2 INFO; Liefer-Punkt 1), Verifikation [2026-11-06-ec67bb0-verifikation-slice-qa-dotnet-lieferkette-haerten](../../../reviews/2026-11-06-ec67bb0-verifikation-slice-qa-dotnet-lieferkette-haerten.md) (0 HIGH, 1 MEDIUM, 2 LOW, 2 INFO), Re-Review [2026-11-06-ec67bb0-re-review-slice-qa-dotnet-lieferkette-haerten](../../../reviews/2026-11-06-ec67bb0-re-review-slice-qa-dotnet-lieferkette-haerten.md) (0 HIGH, 1 MEDIUM, 4 LOW, 2 INFO), Messprotokoll [dotnet-signaturpruefung-2026-11-06](../../../messungen/dotnet-signaturpruefung-2026-11-06.md), Entscheidung [ADR-0035](../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) (`Accepted`). Bezug: [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit).
- **Was geliefert wurde:** `referenztest-dotnet/nuget.config` führt `signatureValidationMode=require` und eine `trustedSigners`-Liste (3 Microsoft-, 2 xUnit-Autoren-, 3 Repository-Zertifikate, SHA-256, für die 36 Pakete des Locks); der Verzicht auf `NuGetAudit` ist dauerhaft und per ADR entschieden, `make referenztest-dotnet-audit` bleibt das manuelle Werkzeug. [CO-001](../../carveouts/done/CO-001-nuget-audit-aus.md) (Trigger (b)) und [CO-002](../../carveouts/done/CO-002-nuget-signaturpruefung-fehlt.md) (Trigger (a)) sind aufgelöst und liegen in `carveouts/done/`.
- **Entscheidungen des Nutzers (2026-11-06, „deine empfehlungen“ / „i, deine empfehlung“):** (V-1) Es gilt die **enge Lesart**: Autoren- und Repository-Eintrag wirken als ODER, der rote Lauf entsteht erst, wenn beide fehlen; `owners` am Repository erst bei Bedarf. Der DoD-Wortlaut von Liefer-Punkt 1 ist darauf angeglichen. Der Verzicht auf `NuGetAudit` ist dauerhaft ([ADR-0035](../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md)). Ein Selbsttest mit Mutation der Liste gehört nicht in diesen Slice, sondern ins Register ([BEO-TDS/signaturliste-ohne-selbsttest](../observations/BEO-TDS/signaturliste-ohne-selbsttest/observation.md)). Der Wortlaut der Antworten stammt aus dem Auftrag dieser Closure und aus [ADR-0035](../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) §Kontext; ein Transkript liegt im Repo nicht (Re-Review R-6).
- **Was anders lief:** Der Rot-Beleg zeigte nicht, was die DoD annahm. Die Liste wirkt nicht als UND über die Pakete, sondern als ODER über die Eintragsarten: ein Repository-Eintrag ohne `owners` lässt jedes von nuget.org gegengezeichnete Paket zu, die Autorenzertifikate binden erst, wenn er fehlt. Rot wurde der Lauf nur ohne xUnit-Autoren **und** ohne Repository-Eintrag (acht Pakete, Exit 1, `NU3034`). Kommentar der `nuget.config`, ADR und Protokoll sagten zunächst Bruch bei neuem Paket (F-1, R-1) und wurden auf das Gemessene eingeschränkt.
- **Finding-Klassen (Lerneintrag, Zählung im Register):**
  - *Aussage breiter als die Messung:* F-1 (Kommentar: „neues Paket bricht den Restore“, gemessen das Gegenteil), F-2 (drei statt acht Pakete), F-3 (Erklärung ohne Messzeile), R-1 (ADR-Entscheidung widersprach dem eigenen Kontext), R-2 (Alternative „nur Repository-Zertifikate“ mit einem Grund, den die Messung nicht trägt), R-5 (Überschrift „Gate-Lockerung“ über einem Absatz, der keine Lockerung beschreibt; Setzung ohne Kennzeichen). → [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md).
  - *Fortschreibung lässt die zweite Fundstelle stehen:* F-5 und R-4 (die Teilablösung von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) nannte zunächst nicht alle überholten Stellen: Messgrenzen, Optionstabelle, „Offen:“-Aufzählung, Geschichtszeile); F-4 (Kommentar zeigt auf den Slice statt auf das dauerhafte Protokoll, Rang-Zeiger). → [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md).
  - *Lesart einer Nutzerentscheidung unbestätigt:* V-1 (der DoD-Wortlaut trägt den gemessenen Rotfall nur in der engen Lesart; entschieden vom Nutzer), R-6 (Zitat „deine empfehlungen“ nicht im Repo belegbar), R-7 (Verweis auf V-1). → [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md).
  - *Rot-Fall ohne Sensor:* V-2; der Rotfall ist von Hand gemessen und von der Verifikation einmal wiederholt, `make gates` sieht ihn nicht. → [BEO-TDS/signaturliste-ohne-selbsttest](../observations/BEO-TDS/signaturliste-ohne-selbsttest/observation.md).
  - *Review-Abdeckung und Nummern:* V-3 (Nachbesserung `ec67bb05` und ADR waren nicht gereviewt) ist durch den Re-Review geschlossen; R-3 und V-4 (ADR-Nummer und Indexzeile gegen `main`) entfielen im Merge `3e794a3c`. Der Re-Review fand an den neuen Texten ein MEDIUM (R-1), das die Verifikation nicht gesehen hatte; das spricht für den Re-Review der Fix-Stellen als eigenen Schritt (wie bei früheren Slices).
  - *Zustandsabhängiger Beleg (V-5):* Der Cache von `make referenztest-dotnet` hält den Lauf grün, ohne den Restore und damit die Signaturprüfung zu wiederholen. Frischbeleg: `docker build --no-cache-filter restore --target test referenztest-dotnet`. `make clean-build` deckt den .NET-Restore nicht ab (Zeile `make clean-build` in `harness/README.md`). `harness/README.md` nennt die Cache-Falle jetzt an der Zeile `make referenztest-dotnet`. → [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md).
- **Zertifikatsrotation (Nutzerentscheidung: in die Closure-Notiz):** Die Liste bindet den Restore an Fingerabdrücke von Zertifikaten, die nicht dem Repo gehören. Wechselt nuget.org sein Repository-Zertifikat oder ein Autor das seine, kann der Restore ohne Änderung im Repo rot werden (`make referenztest-dotnet`, `make referenztest-dotnet-lock`, ein Frischlauf von `make gates`); ein Eintrag der Liste ist dann zu ergänzen. Das ist eine Folgerung, nicht gemessen ([Messprotokoll](../../../messungen/dotnet-signaturpruefung-2026-11-06.md) §Rotation; [ADR-0035](../../adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) Annahme (a)). **Bereits abgelaufen** sind zwei Autorenzertifikate der Liste: `AA12DA22…` (gültig bis 2023-10-05, Microsoft) und `B8F6A977…` (bis 2021-06-03, xUnit); die Pakete mit ihnen bestehen den grünen Restore, die Ursache (etwa der Zeitstempel der Signatur) ist nicht gemessen. Ablauf allein macht den Restore also nicht rot; wie sich ein Wechsel verhält, ist offen. Wer eine rote Rotation behebt, findet im Protokoll nur „ein Eintrag der Liste muss ergänzt werden“ (Verifikation, Konformität). Als Vorschlag, nicht als Regel: die Zertifikate wie im Messprotokoll mit `dotnet nuget verify --all` über die `.nupkg` des Restore lesen, den Fingerabdruck der Liste ergänzen, die Zeile im Protokoll nachtragen. → [BEO-TDS/restore-haengt-am-zertifikatsstand-des-tages](../observations/BEO-TDS/restore-haengt-am-zertifikatsstand-des-tages/observation.md).
- **Steering-Loop-Eintrag:** Verkörpert ist die Prüfung „Fortschreibung lässt die zweite Fundstelle stehen“ im Reviewer-Skill (siehe den Nachtrag oben); sonst ändert die Closure weder Regel noch Gate. Der Lerneintrag ist die gezählte Finding-Klasse „Kommentar, ADR und DoD nennen die Wirkungsform nicht, die die Messung zeigt“ (angenommen UND über die Pakete, gemessen ODER über die Eintragsarten), eingeordnet unter [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md); die Regel ist dort bereits verkörpert (`AGENTS.md` §3.8) und hat den Entwurf nicht verhindert, der Review fand die Funde. **Schwellen-Übertritte:** (1) [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) steht mit diesem Slice bei 11 Evidence-Dateien und ist verkörpert (Reviewer-Skill, Prüfung „Änderungsliste nennt die widersprechenden Stellen der Kernaussage“). (2) [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md) steht bei 15 und wird bei der Welle-Closure gelesen (Entscheidung des Nutzers, 2026-11-06). (3) [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md) steht bei 8 und ist für die Gradle-Stages verkörpert (`make clean-build`); für den .NET-Restore bleibt der Frischbeleg der `docker build`-Aufruf, genannt in `harness/README.md` und im Kopf von `tools/mk/referenztest-dotnet.mk`, ohne eigenes Target (Entscheidung des Nutzers, 2026-11-06). Benannte Spec-Lücke: keine; die Spezifikation trifft keine Aussage zur Paketsignatur, [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) trägt die Zusage.
- **Nachtrag Steering-Loop-Eintrag (2026-11-06, Entscheidung des Nutzers „deine empfehlungen“):** Die Klasse [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) (11 Evidence-Dateien) ist verkörpert, damit ist (1) des Eintrags darunter entschieden. Regel: Prüfung „Änderungsliste nennt die widersprechenden Stellen der Kernaussage (Grep nach der alten Lesart über das ganze Repo)“, Kategorie MEDIUM; Slice-Beleg dieser Closure: F-5, R-4. liegt in `.harness/skills/reviewer.md`
- **Beobachtungs-Register (`../observations/`):** neu [BEO-TDS/restore-haengt-am-zertifikatsstand-des-tages](../observations/BEO-TDS/restore-haengt-am-zertifikatsstand-des-tages/observation.md) (1×); [BEO-TDS/signaturliste-ohne-selbsttest](../observations/BEO-TDS/signaturliste-ohne-selbsttest/observation.md) bleibt bei 1× (ein Vorgang zählt einmal), Fund ergänzt; `evidence/slice-qa-dotnet-lieferkette-haerten.md` neu in `aussagen-ueber-messung-hinaus`, `fortschreibung-laesst-zweite-fundstelle-stehen`, `lesart-einer-nutzerentscheidung-unbestaetigt` und `test-stage-cache-verdeckt-wiederholung`.
- **Risiken aus §6:** Eines, mit Ausgang (eingetreten, gemessen, entfallen als Hindernis).
- **Belegläufe der Closure:** am Stand `2567e5f4` (Zweig closure-slice-dotnet-lieferkette, Einzellauf, ein Host): `make docs-check` 811 Dateien, 0 Befunde; `make gates` Exit 0, die Klasse `tests` lief (`referenztest-dotnet` gebaut, nicht übersprungen), kein Stempel-Beleg vorhanden; Frischbeleg `docker build --no-cache-filter restore --target test referenztest-dotnet` Exit 0, `dotnet restore` lief (Layer nicht aus dem Cache), Test `Passed: 1, Failed: 0`. Nicht gefahren: Rotläufe des Messprotokolls (Verifikation und Review haben sie nachgefahren), CI.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md). Anker ↔ Folge-Slice: kein Folge-Slice, beide Carveouts aufgelöst; Register: die genannten Kennungen existieren als Verzeichnisse mit nicht leerem `evidence/`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` deklariert nur `*` (Default, GF); der Slice berührt `referenztest-dotnet/`, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** `../observations/BEO-TDS/` durchgegangen; kein Eintrag betrifft die Lieferkette des .NET-Restore.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
