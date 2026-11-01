# Slice slice-qa-vergleichslauf-d: Vergleichslauf D: Referenzabgleich gegen einen echten SQL Server

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-045 · SPEC-050

**Verantwortlich:** — bis zur Priorisierung (pt9912 trägt sich beim Übergang `open` → `next` ein).

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Rahmen des Vergleichslaufs D ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Option D, Referenzabgleich nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig)) steht: ein eigenes Make-Target startet einen SQL-Server-Container und den Referenzclient, die Anmeldung am echten Server ist gemessen, der Tag für TDS 8.0 ist ermittelt oder als nicht verfügbar begründet, und die Ausnahmeliste samt Messung von Laufzeit und Bedarf steht im Repo.

**Entscheidung des Nutzers (pt9912, 2026-11-01):** Vergleichslauf D wird gemacht. Die Zustimmung zu `ACCEPT_EULA=Y` für das SQL-Server-Developer-Image ist eine **Entscheidung des Nutzers**, nicht das Ergebnis einer Rechtsprüfung im Repo; die Rechtsprüfung bleibt beim Nutzer, das Repo macht keine Rechtsaussage. Gewählt ist die Empfehlung: Developer-Image für Entwicklung und Test, lokal und nur im Make-Target dieses Laufs, nie in `make gates`, nie in der CI ohne ausdrückliche Freigabe des Nutzers. Die frühere Zustimmung steht in [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) (EULA, 2026-10-30) und bleibt unverändert; diese Entscheidung engt ihren Gebrauch ein.

**Zerlegung (Planner, 2026-11-01).** Der Umfang dieses Slice trug vier Gegenstände, dazu kamen die offenen Messungen aus [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md), [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) und [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md). Das sind mehr als drei Liefer-Punkte, und sie haben verschiedene Eingänge: Rahmen und Anmeldung brauchen nur Container und Referenzclient; die Wire-Messungen brauchen zusätzlich einen mitschneidenden Proxy. Deshalb liefert dieser Slice Rahmen, Anmeldung und TDS-8.0-Tag; die Messreihe am Wire liegt bei [slice-qa-vergleichslauf-d-messungen](slice-qa-vergleichslauf-d-messungen.md), Start erst nach diesem Slice.

**Übernimmt:** den Gegenstand des DoD-Punkts 3 von [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md). Der Schnitt ist eine Setzung der Closure jenes Slice: der CI-Teil war lieferbar, D hängt an den Szenarien des Referenzclients aus [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) und hatte in der Datei des alten Slice keine Rückführung dafür.

**Umfang** (aus [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md) §1 übernommen):

- Make-Target für D, Container der Referenzinstanz, Referenz-Seite der Läufe, außerhalb von `make gates`.
- Ausnahmeliste für das gemessene Schema, darunter der Eintrag *so gewollt* für die feste Meldung ohne Kennung (`SPEC-012`); Laufzeit sowie Netz-, Platten- und Speicherbedarf auf dem Zielsystem gemessen.
- Die EULA-Entscheidung des Nutzers (siehe oben) als gesetzte Variable mit Zeiger im Make-Fragment; keine Rechtsprüfung.
- Anmelde-Szenario in D (falsches Kennwort, unbekannter Benutzer; einmal gemessen 18456, State 1, Class 14, Meldung mit Benutzernamen, [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen). Der interne State ist am Client nicht sichtbar.
- SQL-Server-Image-Tag für TDS 8.0, siehe §6.

**Die Antwortseite des Codecs** (Reihenfolge der Antwort-Tokens, Statusbits der DONE-Familie, ordinale Zählung der RETURNVALUE; Träger der Beobachtung `BEO-TDS/messung-am-echten-sql-server-fehlt`) steht nicht mehr hier, sondern in [slice-qa-vergleichslauf-d-messungen](slice-qa-vergleichslauf-d-messungen.md): die Client-API zeigt Tokenfolge und Statusbits nach ihrer Gestalt nicht (Annahme, nicht gemessen), die Messung braucht einen Mitschnitt am Wire.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Szenarien des Referenzclients (S1 bis S18) — sie schreibt [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md); D führt sie gegen den echten Server aus und braucht sie als Eingang (Start-Trigger).
- Die Server-Seite des Live-Laufs, die Ausnahmeliste je Szenario und der Mitschnitt-Lauf — [slice-referenzabgleich-live](slice-referenzabgleich-live.md) und [slice-referenzabgleich-mitschnitt](slice-referenzabgleich-mitschnitt.md); dieser Slice liefert Target, Container und Rahmen, jene füllen sie mit dem Server des Repos. Das Produkt ist die Bibliothek, der Server ein Testserver ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); dieser Slice spricht ihn nicht an, die Referenz-Seite hat nur den echten SQL Server.
- Die Wire-Messungen (Tokenfolge, DONE-Statusbits, RETURNVALUE-Zählung, Fehler in `sp_executesql`, ENVCHANGE bei `RESETCONNECTION`, Cancel-Antwort) — [slice-qa-vergleichslauf-d-messungen](slice-qa-vergleichslauf-d-messungen.md); sie brauchen einen mitschneidenden Proxy, den dieser Slice nicht liefert.
- Aufnahme von D in `make e2e` oder `make gates` — nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) ist ein E2E-Lauf ein Lauf, der den Serverprozess dieses Repos über seine Außengrenze anspricht; D tut das nicht (Referenz-Seite), ist also kein E2E-Lauf und bekommt ein eigenes Target. `make gates` schließt die Nutzerentscheidung aus; ob der Live-Lauf später eine Stufe von `make e2e` wird, entscheidet [slice-referenzabgleich-live](slice-referenzabgleich-live.md).
- NuGet-Audit und `trustedSigners` des Referenztests — [slice-qa-dotnet-lieferkette-haerten](slice-qa-dotnet-lieferkette-haerten.md) ([CO-001](../../carveouts/CO-001-nuget-audit-aus.md), [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md)).
- Die Wahl des Referenzclients — durch [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) entschieden.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Rahmen:** Vergleichslauf D als eigenes Make-Target (Fragment unter `tools/mk/`), nicht in `make gates`, nicht in `make e2e`; Eintrag in `harness/README.md` §Werkzeuge; zwei Container in eigenem internen Netzwerk ohne Mount und ohne veröffentlichten Port, Namen mit PID, Bereitschaftsschleife und `trap` (Muster von `make kompatibilitaet`); das SQL-Server-Image per Digest im Dockerfile gepinnt wie die anderen Basis-Images; Ausnahmeliste als Datei mit Pfad, Szenario, Grund und Art (*so gewollt* oder *Lücke*) nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), darunter die feste Login-Meldung ohne Kennung; Laufzeit sowie Netz-, Platten- und Speicherbedarf auf dem Zielsystem (lokaler Host) gemessen und im Kopf des Fragments genannt; die EULA-Entscheidung des Nutzers (2026-11-01, pt9912, Grenzen wie in §1) als gesetzte Variable mit Zeiger im Make-Fragment und in der Ausnahmeliste dokumentiert, ohne Rechtsaussage. Ein Lauf in der CI und die Aufnahme in `make gates` sind nicht vorgesehen (Nutzerentscheidung) und brauchen eine neue Entscheidung des Nutzers.
- [ ] **Anmeldung:** Anmelde-Szenario gegen den echten Server (falsches Kennwort, unbekannter Benutzer) im Lauf D; gemessen, was der Referenzclient beobachtet (Number, State, Class, Meldung), gegen die einmalige Messung in [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen und [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md); der Befund steht in der Ausnahmeliste.
- [ ] **TDS-8.0-Tag:** gemessen, ob ein SQL-Server-Image `Encrypt=Strict` beantwortet und worin der Fehler der Referenzinstanz `2022-latest` (`Class=20, Number=0`) liegt, im Tag oder in der Konfiguration des Servers (eine Konfigurationsmöglichkeit belegt der Implementer beim Messen mit Quelle, der Planner kennt keine belegte); Ergebnis ist ein Tag mit Digest und Messzeile, oder die Begründung, warum keiner lieferbar ist; der Befund steht in der Ausnahmeliste; ändert er eine Aussage von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), entsteht eine Folge-ADR (die `Accepted`-ADR bleibt unverändert).

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `tools/mk/` (Fragment für D) | neu | Make-Target des Vergleichslaufs, Container der Referenzinstanz im eigenen Netzwerk ohne Mount ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [SPEC-050](../../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)) |
| `harness/README.md` | update | Zeile des Targets in §Sensors oder §Werkzeuge; Bindung |
| Ausnahmeliste | neu | Abweichungen mit Pfad, Szenario, Grund und Art (*so gewollt* oder *Lücke*), nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) |
| `referenztest-dotnet/` | update | Anmelde-Szenario (falsches Kennwort, unbekannter Benutzer) gegen den echten Server; `Kompatibilitaet/Ziel.cs` liest das Ziel aus `TDSWIRE_ZIEL`, hält aber Benutzer, Kennwort und Vertrauensanker des Testservers fest (gelesen am 2026-11-01), der echte Server braucht eigene Werte, ohne die Tests des Testservers zu ändern; Szenarien für Mehrfach-Resultset und RPC mit Ausgabewert gehören zu [slice-qa-vergleichslauf-d-messungen](slice-qa-vergleichslauf-d-messungen.md) |
| `Dockerfile` | update | Stage und Digest-Pin für das SQL-Server-Image (die Basis-Images stehen dort per Digest) |
| Skript des Laufs <!-- d-check:ignore (geplant: Name nach dem Muster von tools/kompatibilitaetstest.sh) --> | neu | Ablauf, `trap`, Bereitschaft, Messzeiten; Selbsttest nach dem Muster von `make skript-selbsttest`, soweit der Ablauf Verzweigungen hat |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) liegt in `done/` (Szenarien des Referenzclients; Stand 2026-11-01 erfüllt), und ein Verantwortlicher ist im Kopf eingetragen (offen: pt9912 trägt sich beim Übergang `open` → `next` ein).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Anmeldung oder TDS-8.0-Tag als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): `mcr.microsoft.com` oder das Image der Referenzinstanz ist nicht erreichbar, der Nutzer nimmt die EULA-Entscheidung vom 2026-11-01 zurück oder der Hersteller ändert die Bedingungen des Images, oder die Szenarien des Referenzclients fehlen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (D selbst steht nicht darin; ein Lauf des Targets von D ist als Beleg in der Closure-Notiz genannt) und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Offene Frage — Tag für TDS 8.0.** Die Aussagen widersprechen sich. [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) misst, dass `Encrypt=Strict` gegen `2022-latest` mit `Class=20, Number=0` scheitert, nennt den Grund (SQL Server 2022 biete TDS 8.0 nicht an) aber nur als gefolgert und den Tag für TDS 8.0 als nicht ermittelt. [slice-referenzabgleich-live](slice-referenzabgleich-live.md) schreibt dagegen, die Referenzinstanz 2022 biete den TDS-8.0-Pfad nicht an, und lässt den Abgleich dort entfallen. Der Vorgänger-Slice wies „SQL-Server-Tag für TDS 8.0“ dem Vergleichslauf D zu (Review F-12). **Setzung des Planners (2026-11-01, nicht vom Nutzer bestätigt):** dieser Slice misst (Liefer-Punkt 3), und zwar zuerst, ob der Fehler am Tag oder an der Konfiguration des Servers liegt; „bietet nicht an“ gilt für [slice-referenzabgleich-live](slice-referenzabgleich-live.md) erst als Ausschluss, wenn diese Messung es trägt. — **Ausgang:** offen bis zur Closure.
- Die Szenarien des Referenzclients ([slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md)) sind der Eingang von D; fehlen sie, bleibt D ohne Anmelde- und Antwort-Szenarien. — **Ausgang:** entfallen: [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) liegt in `done/` (Stand 2026-11-01); ob sie das Ziel als Parameter tragen, klärt der Implementer im ersten Lauf.
- Laufzeit, Platten- und Speicherbedarf sind nur an einem Host gemessen ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md): ein Lauf, `2022-latest` beweglich, bis auf den Digest); die Messung dieses Slice gilt für den lokalen Host, nicht für CI-Runner (dort läuft D nicht, siehe EULA-Entscheidung). Die Frage nach Häufigkeit und Machbarkeit in der CI bleibt als `BEO-TDS/vergleichslauf-d-ungeklaert` offen. — **Ausgang:** offen bis zur Closure.
- Die EULA-Zustimmung (`ACCEPT_EULA=Y`) war rechtlich ungeprüft. — **Ausgang:** eingetreten als Entscheidung: der Nutzer pt9912 hat am 2026-11-01 die Zustimmung für das Developer-Image als eigene Entscheidung getroffen (Grenzen in §1: lokal, nur im Make-Target des Laufs, nie in `make gates`, nie in der CI ohne ausdrückliche Freigabe); die Rechtsprüfung bleibt beim Nutzer, im Repo ist sie nicht geführt und nicht behauptet. Jede Ausweitung (CI, Gate, Forks) braucht eine neue Entscheidung des Nutzers; der Eintrag steht im Make-Fragment und in der Ausnahmeliste.
- Der Lauf hängt an der Erreichbarkeit von `mcr.microsoft.com` und an einem beweglichen Tag; der Digest-Pin hält den Stand, nicht die Verfügbarkeit. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt das ganze Repo, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des Worktrees auf `main`, 2026-11-01); alle tragen die Sub-Area `*`. Treffer:

- `BEO-TDS/vergleichslauf-d-ungeklaert` — 1×, Stand *offen*, Träger dieser Slice (EULA entschieden, Tag für TDS 8.0 und Bedarf hier; Häufigkeit und Machbarkeit in der CI bleiben offen).
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 25×, Stand *geplant*; Träger der Messungen ist [slice-qa-vergleichslauf-d-messungen](slice-qa-vergleichslauf-d-messungen.md), dieser Slice liefert den Container dafür. Diese Zerlegung ist selbst kein neues Auftreten: ein Evidence-Eintrag entsteht erst bei der Closure.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — Stand *geplant*; berührt den TDS-8.0-Tag (Liefer-Punkt 3).
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — berührt das Anmelde-Szenario (Meldung ohne Kennung, `SPEC-012`).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — Stand *verkörpert* (`AGENTS.md` §3.8): jede Aussage der Ausnahmeliste und der Closure nennt Instanz, Digest und Szenario.
- `BEO-TDS/ablage-im-repo-bricht-docs-check` — Ausgaben des Laufs außerhalb des Repos (`mktemp -d`, `trap`), wenn das Rezept sie sammelt.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — Slice und Closure-Notiz nennen Kennungen als Link.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
