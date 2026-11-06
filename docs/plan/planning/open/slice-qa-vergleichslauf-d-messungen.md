# Slice slice-qa-vergleichslauf-d-messungen: Vergleichslauf D, Messreihe am Wire: Antwort-Tokens, Fehler in `sp_executesql`, Zurücksetzen und Abbruch am echten SQL Server

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md), [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)

**Berührte Spec-Stellen:** SPEC-030 · SPEC-031 · SPEC-045 · SPEC-050

**Verantwortlich:** — bis zur Priorisierung (pt9912 trägt sich beim Übergang `open` → `next` ein).

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die offenen Aussagen über die Antworten eines echten SQL Servers sind je Punkt von *Setzung* auf *gemessen* gehoben oder als Abweichung geführt: Tokenreihenfolge der RPC-Antwort mit Ausgabewert, Abschluss eines Fehlers in `sp_executesql`, ENVCHANGE bei `RESETCONNECTION`, Cancel-Antwort, Statusbits der DONE-Familie bei statement- und batch-abbrechenden Fehlern im Batch; die Mitschnitte der Messung liegen redigiert im Repo.

**Herkunft.** Dieser Slice ist der Träger, den vier Entscheidungen nennen: [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) (`Accepted`: Spec-Lücken 2 und 3, Reihenfolge von RETURNVALUE und RETURNSTATUS, COLMETADATA nach RETURNVALUE), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Proposed`: DONEPROC im Fehlerfall der RPC-Antwort ist Setzung ohne Messung), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (`Accepted`: ENVCHANGE bei `RESETCONNECTION` offen, Cancel-Antwort R11 und R12 Vorbild) die Beobachtung `BEO-TDS/messung-am-echten-sql-server-fehlt` und (Nutzerentscheid 2026-11-06, Träger-Vorschlag übernommen) die Beobachtung `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`: Die Spezifikation legt fest, dass der Sequenzierer bei statement-abbrechenden Fehlern `DONE` ohne `MORE` schreibt; der echte Server sendet nach dem Stand der Spezifikation `DONE` mit `MORE` und `ERR`. Dieser Slice liefert die Messung, nicht den Bau. Er entstand am 2026-11-01 aus der Zerlegung von [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md), das den Container, das Target und die Ausnahmeliste liefert.

**Methode.** Wie die Messreihe R in [ADR-0007](../../adr/0007-fehler-und-attention.md): der Referenzclient spricht über einen mitschneidenden TCP-Proxy im Client-Container mit dem echten Server; der Proxy zerlegt die TDS-Pakete und die Tokens. Das ist die Setzung des Planners nach diesem Vorbild, nicht die Wahl der Aufnahme-Methode, die [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) §6 als Frage an den Nutzer führt (Frage unten in §6). Anders als beim Spike von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) bleibt der Messcode im Repo, damit die Messung wiederholbar ist.

**Übernimmt (aus dem Vorgänger-Schnitt):** den Wire-Teil des Umfangs von [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) (Antwortseite des Codecs); [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) hatte diesen Teil zuvor für sich übernommen, siehe §6.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Container, Make-Target, Digest-Pin, Ausnahmeliste, Messung von Laufzeit und Bedarf, EULA-Entscheidung, Anmelde-Szenario und TDS-8.0-Tag — [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md); dieser Slice setzt sie voraus (Start-Trigger).
- Das Abspielen von Mitschnitten gegen den Server dieses Repos und der feldweise Vergleich — [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md); hier wird gemessen, was der echte Server sendet, nicht was der Testserver sendet. Das Produkt ist die Bibliothek, der Server ein Testserver ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); dieser Lauf spricht ihn nicht an.
- Das Ändern von Code, Spezifikation oder `Accepted`-ADRs aufgrund eines Befunds — je Befund ein Slice oder eine Folge-ADR nach der Messung. Insbesondere ändert dieser Slice [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) nicht; er liefert die Messung, auf die der Nutzer entscheidet (DoD, Punkt 2).
- Der Bau der Cancel-Zwei-Nachrichten-Form des echten Servers — [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 baut nur `DONE[ATTN]`; hier wird nur gemessen. Der Referenzclient-Lauf gegen den Server dieses Repos gehört zu [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md).
- Der Referenzclient mit `Pooling=true` gegen den Server dieses Repos (Messweg (b) in [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)) — [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md); dort steht Messweg (a) als Folge, die dieser Slice liefert.
- Der Bau zur Messung der Statusbits bei statement-abbrechenden Fehlern im Batch (`Failure` um die Klasse erweitern, Sequenzierer angleichen) — Folge-Slice nach der Messung und nach der Entscheidung zur Einzelzuordnung der Fehlercodes (Spezifikation, Abschnitt 4: offen); hier wird nur gemessen und der Unterschied benannt.
- Statement-abbrechende Fehler im RPC und ungültig kodierte RPC-Nachrichten am echten Server — [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) nennt sie als nicht gemessen und ohne benannten Verbraucher; sie bleiben im Register (`BEO-TDS/offene-punkte-ohne-traeger`).

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

Liefer-Punkte (drei):

- [ ] **Messweg und Antwort-Tokens:** Der mitschneidende Proxy (Zerlegung der Pakete und Tokens) liegt als Testwerkzeug im Repo, läuft in Docker ohne Mount gegen den Container aus [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) und hat Tests (Happy/Boundary/Negative, Tag `@spec` nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); redigierte Mitschnitte ohne Kennwort und Prüfwert liegen mit Herkunft (Digest des Images, Client-Version) im Repo, ein Test belegt die Redaktion mit Eingaben, die beides enthalten. Gemessen für je ein Szenario mit Mehrfach-Resultset und mit RPC mit Ausgabewert: Reihenfolge der Antwort-Tokens (RETURNSTATUS, RETURNVALUE, DONEPROC; Spec-Lücke 2 aus [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md)), Stellung von COLMETADATA hinter RETURNVALUE (Spec-Lücke 3), Statusbits der DONE-Familie, ordinale Zählung der RETURNVALUE. Je Punkt steht *gemessen* oder *Abweichung* in der Ausnahmeliste, und die Kompatibilitätsmatrix (Zeilen „nicht am echten Server gemessen“ für RETURNSTATUS, RETURNVALUE, DONE-Familie, Lücke 8) trägt den Stand.
- [ ] **Fehler in `sp_executesql`:** Am echten Server gemessen, mit welchem Token ein Fehler in `sp_executesql` endet (DONE oder DONEPROC, Statusbits), mit je einem Szenario für einen Fehler vor und einen nach Zeilen, und ob der Referenzclient beide Formen verschieden behandelt. Zusätzlich gemessen im Batch (nicht im RPC): die Statusbits der DONE-Familie (`MORE`, `ERR`, Zählung) bei einem statement-abbrechenden und einem batch-abbrechenden Fehler, je ein Szenario, mit der Aussage, ob der Sequenzierer dieses Repos dieselbe Variante schreibt (`BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab`); die Messung ändert weder Code noch Spezifikation. Das Ergebnis liegt als Messzeile mit Mitschnitt vor. **Entscheidungs-Konsequenz für [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Proposed`), nicht Teil dieses Slice:** nach der Messung entscheidet der Nutzer, Option A anzunehmen oder die ADR zurückzuziehen; der Slice legt nichts an der ADR an, die Closure-Notiz nennt die Messzeile und diese Entscheidung als offenen Punkt für den Nutzer.
- [ ] **Zurücksetzen und Abbruch:** (a) ENVCHANGE im ersten Antwortpaket nach `RESETCONNECTION` (Pooling, zweite Nutzung der Verbindung): Typ und Inhalt aus dem Mitschnitt, oder die Begründung, warum der Proxy es nicht liefert ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Messweg (a)); (b) die Cancel-Antwort (`Command.Cancel()` bei `WAITFOR DELAY`, R11 und R12 in [ADR-0007](../../adr/0007-fehler-und-attention.md)) als Mitschnitt abgelegt und mit der Beschreibung in [ADR-0007](../../adr/0007-fehler-und-attention.md) verglichen; nur Messung, kein Bau. Beide Ergebnisse stehen in der Ausnahmeliste.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jede Aussage steht an einem Test, einem Mitschnitt oder ist als Festlegung gekennzeichnet; die Zuordnung *Messung | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`); Nachträge in der Spezifikation und in ADRs führt dieser Slice nicht aus; sie sind je Befund benannte Folge-Arbeit.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Werkzeuge, falls ein Target hinzukommt; Kompatibilitätsmatrix).
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
| Mitschneidender Proxy <!-- d-check:ignore (geplant: Ort und Sprache nach der Wahl der Aufnahme-Methode) --> | neu | Zerlegung von Paketen und Tokens nach dem Vorbild der Messreihe R; Testwerkzeug in Docker, kein Mount ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| Mitschnitte <!-- d-check:ignore (geplant: Ablage nach Entscheidung) --> | neu | redigiert, mit Herkunft; außerhalb des Repos gesammelte Ausgaben (`mktemp -d`, `trap`), nur Redigiertes wandert ins Repo |
| `referenztest-dotnet/` | update | Szenarien für Mehrfach-Resultset, RPC mit Ausgabewert, Fehler in `sp_executesql` (vor und nach Zeilen), statement- und batch-abbrechender Fehler im Batch (Statusbits der DONE-Familie), Pooling mit zweiter Nutzung, Cancel; Ziel und Zugangsdaten als Parameter wie in [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) |
| `tools/mk/` und Skript des Laufs <!-- d-check:ignore (geplant: Name nach dem Muster von tools/kompatibilitaetstest.sh) --> | update | Messlauf als Kategorie oder Parameter des Targets aus [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md); kein Gate, nicht in `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) |
| Ausnahmeliste <!-- d-check:ignore (geplant: Datei aus slice-qa-vergleichslauf-d) --> | update | Messzeilen und Abweichungen mit Art *so gewollt* oder *Lücke* |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeilen, die „nicht am echten Server gemessen“ tragen, bekommen den gemessenen Stand oder bleiben mit Begründung |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive Testdatei-Zeilen mit der Anforderungs-Kennung in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) liegt in `done/` (Container, Target, Ausnahmeliste), die Wahl der Aufnahme-Methode ist entschieden (Frage in [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) §6; Vorschlag des Planners: mitschneidender Proxy wie in der Messreihe R, sonst entfällt der Wire-Teil und der Slice schrumpft auf die vom Client beobachtbaren Punkte), und ein Verantwortlicher ist im Kopf eingetragen.

**Stand des Start-Triggers:** Die Aufnahme-Methode ist als mitschneidender Proxy gebaut ([slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md), Setzung des Planners, vom Nutzer nicht bestätigt; die Bestätigung ist Bedingung dieses Triggers). Proxy, Redaktion und 17 Mitschnitte je Instanz liegen im Repo; gemessen sind dort Tokenfolge, DONE-Statusbits, Ordinal der RETURNVALUE, RETURNSTATUS, ATTENTION und die Cancel-Antwort (`docs/messungen/referenzabgleich.md`, Ergebnis 1 bis 6). Offen für diesen Slice bleiben Fehler in `sp_executesql`, ENVCHANGE bei `RESETCONNECTION`, die Anmeldung am Draht (`BEO-TDS/anmeldung-am-draht-nicht-aufgenommen`), SESSIONSTATE (A-18) und ein zweiter Fall für das Ordinal; die DoD in §2 ist noch nicht gekürzt (Entscheidung des Nutzers, siehe Closure-Notiz des Mitschnitt-Slice).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder der Proxy sprengt den Rahmen; Schnittvorschlag: Proxy mit Antwort-Tokens als einer, Fehler in `sp_executesql` und Zurücksetzen mit Abbruch als zweiter Slice.
- `in-progress` → `open` (blockiert — Carveout?): die Aufnahme ist mit Docker-only und ohne Mount nicht möglich (Rechte für Paketaufnahme), oder der Container aus [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) steht nicht zur Verfügung; der Nutzer entscheidet über eine Ausnahme als Carveout.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, der Review-Report liegt vor, und die Closure-Notiz trägt je Messpunkt (Tokenreihenfolge, COLMETADATA, Statusbits, Ordinalzählung, Fehlerabschluss in `sp_executesql`, Statusbits bei statement- und batch-abbrechenden Fehlern im Batch, ENVCHANGE, Cancel-Antwort) ein Ergebnis (gemessen mit Beleg, Abweichung oder nicht lieferbar mit Grund), die Grenzen (eine Instanz, Digest, TDS 7.4 unverschlüsselt oder mit TLS, je nach Aufnahme) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Doppelte Zuständigkeit mit [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md).** Dieser Slice (Messreihe, einmal, mit Proxy) und der Mitschnitt-Slice (Aufnahme, Abspielen, feldweiser Vergleich) berühren dieselben Aussagen und brauchen dasselbe Werkzeug. Der Planner hat den Wire-Teil am 2026-11-01 aus dem Mitschnitt-Slice hierher gelegt, weil [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md), [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) und [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) den Vergleichslauf D als Träger nennen; der Mitschnitt-Slice nutzt Proxy und Mitschnitte als Eingang und trägt die Wiederholbarkeit. Setzung, nicht vom Nutzer bestätigt (Frage unten). — **Ausgang:** offen bis zur Closure.
- **Frage an den Nutzer — Aufnahme-Methode.** Ein mitschneidender Proxy (Vorbild Messreihe R) verändert den Verkehr möglicherweise; ein Mitschnitt auf Netzwerkebene oder das Log der Instanz sind die Alternativen aus [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) §6. Die Wahl ist Start-Trigger dieses Slice. — **Ausgang:** offen bis zur Closure.
- **Die Messung kann ein Ergebnis ergeben, das eine `Accepted`-ADR berührt** (Reihenfolge der Tokens, Cancel-Antwort, ENVCHANGE). Hard Rule 3.5: kein Eingriff in die ADR; der Befund führt zu einer Folge-ADR oder einem Folge-Slice. — **Ausgang:** offen bis zur Closure.
- **[ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) ist `Proposed`, der Bau weicht von ihr ab.** Die Messung entscheidet nicht; es entscheidet der Nutzer nach der Messung (Annahme von Option A oder Zurückziehen der ADR). Bis dahin trägt der Quelltext (`failedDone()`) eine Setzung ohne Messung. — **Ausgang:** offen bis zur Closure.
- **Der Proxy zeigt nur, was er zerlegen kann.** Der Parser der Messreihe R kennt das ENVCHANGE bei `RESETCONNECTION` nicht ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)); ein Befund „Token unbekannt“ ist kein Ergebnis. — **Ausgang:** offen bis zur Closure.
- Der Slice kann mehr als drei Liefer-Punkte tragen, falls der Proxy mehr Aufwand ist als die Messungen (Liefer-Punkt 1 ist der dichteste). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt Testwerkzeug, Docker und Dokumentation, keine dieser Strukturen trägt eine eigene Strukturregel oder Inventur-Linie neben dem Repo insgesamt (eine von drei Achsen, unter der Schwelle). `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des Worktrees auf `main`, 2026-11-01); alle tragen die Sub-Area `*`. Treffer:

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 25×, Stand *geplant*; dieser Slice ist neben [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) der Träger der Messung selbst (Evidenz-Risiko: die Beobachtung wurde bisher in 25 Vorgängen als Setzung gezählt und nie durch einen Lauf gelöst).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — Stand *verkörpert* (`AGENTS.md` §3.8): jede Aussage der Closure nennt Instanz, Digest und Szenario.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — Redaktion der Mitschnitte: Kennwort und Prüfwert erscheinen nirgends in der Ablage.
- `BEO-TDS/ablage-im-repo-bricht-docs-check` — Ausgaben außerhalb des Repos sammeln (`mktemp -d`, `trap`).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — Tests des Proxys variieren die Eingabe (verschiedene Tokens, nicht ein Fall).
- `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab` — Träger der Messung der DONE-Statusbits bei statement-abbrechenden Fehlern im Batch (Nutzerentscheid 2026-11-06); der Bau ist Folge-Slice.
- `BEO-TDS/offene-punkte-ohne-traeger` — statement-abbrechende Fehler im RPC bleiben dort (Ausschluss in §1).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — Slice und Closure-Notiz nennen Kennungen als Link.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
