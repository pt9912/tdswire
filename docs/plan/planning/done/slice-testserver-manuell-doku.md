# Slice slice-testserver-manuell-doku: Anleitung zum manuellen Test des Testservers: Befehle, Port, Anmeldung, TLS, Beispiele, Grenzen


**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md).

**Berührte Spec-Stellen:** —

**Verantwortlich:** pt9912 (Zweig slice-testserver-manuell-doku)

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine Anleitung, nach der ein Mensch ohne Vorwissen den Testserver mit `make testserver-up` startet, einen eigenen Client anmeldet, Anfragen stellt und mit `make testserver-down` stoppt, mit den Grenzen dessen, was der Testserver kann.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Targets selbst: [slice-testserver-up-down](../done/slice-testserver-up-down.md); diese Doku beschreibt gebaute Targets und beginnt erst, wenn der Slice in `done/` liegt.
- Die Einbettung der Bibliothek in eine eigene Anwendung und die Release-Schritte: [slice-release-doku-prozess](../next/slice-release-doku-prozess.md); der Testserver ist dort nur ein Verweis.
- Der Nachvollzug von einem frischen Klon durch eine zweite Person: Closure-Trigger der Welle [welle-testserver-manuell](../welle-testserver-manuell.md), nicht dieses Slice.

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

- [x] Die Anleitung (Ort: Vorschlag `docs/user/testserver-manuell.md`, siehe §6) <!-- d-check:ignore (geplante Datei) --> nennt die Befehle `testserver-up`, `-down`, `-logs`, `-status`, Host, Port, Benutzer, die Verschlüsselungsrichtlinie, die Beispiel-Tabellen mit ihren Spalten und je einen Beispielaufruf für zwei Clients, und jede Aussage über das Verhalten des Servers trägt einen Beleg (Test, Messung) oder ist als nicht gemessen gekennzeichnet. *Haken mit Nennung (Wortlaut):* verlangt sind zwei Beispielaufrufe, nicht zwei gemessene; Beispiel 1 (Rohbytes-Client) ist gemessen, Beispiel 2 (Zeichenfolge für Microsoft.Data.SqlClient) steht als „nicht gemessen“ gekennzeichnet — der Wortlaut lässt die Kennzeichnung ausdrücklich zu; die Abweichung vom Plan-Ziel „gemessen“ steht in §6 und §7.
- [x] Die Grenzen stehen darin: was der Testserver nicht kann (zum Beispiel Anfragearten außerhalb der Spec, Last, Windows-Authentifizierung), dass es kein Produkt ist ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)), und dass die Testwerte nicht für Daten genutzt werden dürfen, die Schutz brauchen.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Verweis aus `README.md` und `harness/README.md` auf die Anleitung gesetzt; `make docs-check` grün.
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
| `docs/user/testserver-manuell.md` | neu | Anleitung (Ort siehe §6) <!-- d-check:ignore (geplante Datei) --> |
| `README.md` | update | Verweis |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Jeder Beispielaufruf wird vor der Aufnahme gegen einen laufenden `testserver-up` ausgeführt; die Ausgabe steht als Beleg im Slice §7.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-testserver-up-down](../done/slice-testserver-up-down.md) liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Anleitung braucht mehr als zwei Client-Beispiele, weil jedes eigene Installationsschritte verlangt.
- `in-progress` → `open` (blockiert — Carveout?): ein Client lässt sich ohne Lizenzentscheidung des Nutzers nicht aufrufen (EULA).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, Review und Verifikation durchgeführt (die Verifikation führt die Beispielaufrufe selbst aus), Closure-Notiz mit Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Ort der Anleitung: `docs/user/` (Betrieb, Qualität) oder `docs/maintainer/` <!-- d-check:ignore (Verzeichnis besteht nicht) --> — Vorschlag `docs/user/`, weil sie Embedder beim Testen ihrer Einbettung nutzen — **Ausgang:** weiter offen. Der Ort ist nach dem Vorschlag dieses Slice gesetzt (`docs/user/testserver-manuell.md`), vom Nutzer nicht bestätigt; die Rückfrage an den Nutzer steht aus. Register: [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md) (Klasse; für diesen Vorgang keine Evidence-Datei geschrieben, Auftreten ist hier zu zählen, wenn der Nutzer anders entscheidet). Träger: Rückfrage beim Nutzer, spätestens beim Nachvollzug von einem frischen Klon (Closure-Trigger der Welle [welle-testserver-manuell](../welle-testserver-manuell.md)).
- Welche Clients die Beispiele nennen: ein Rohbytes-Beispiel ist ohne Lizenz möglich; `sqlcmd` und JDBC-Treiber bringen eigene Lizenz und Installation mit — **Ausgang:** weiter offen. Gesetzt ist, abweichend vom Vorschlag: Rohbytes-Client des Repos (Beispiel 1) und Microsoft.Data.SqlClient (Beispiel 2, Zeichenfolge aus dem Referenztest `SimulationBackendTests.cs`) statt JDBC, weil der Referenzclient des Repos schon da ist und keine neue Lizenz mitbringt; vom Nutzer nicht bestätigt (gleiche Rückfrage wie beim Ort). `sqlcmd` und JDBC sind nicht aufgenommen. Register: [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md).
- Aussagen über das Verhalten gegenüber einem Client sind nur so weit belegt wie die Läufe von `make kompatibilitaet` (Referenzclient Microsoft.Data.SqlClient) — **Ausgang:** weiter offen. Beispiel 2 (SqlClient) ist in der Anleitung als „nicht gemessen“ gekennzeichnet, weil im Handlauf kein .NET-Client mit Anfragefähigkeit zur Hand war; ebenso `REQUIRED` mit Zertifikat und jede SQL-Anfrage. Folge: eine Messung, wenn jemand die Anleitung auf einem Rechner mit .NET-Client nachvollzieht (Trigger der Welle [welle-testserver-manuell](../welle-testserver-manuell.md)); Register: [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md).

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

**Gegenstand:** geliefert, DoD vollständig mit einer benannten Auslegung (Punkt „zwei Clients“, siehe unten). Commits: `76c72d8` (Anleitung `docs/user/testserver-manuell.md`, Verweis in `README.md`), `68ea37f` (Review-Befunde F-1 bis F-5, F-8), `97b47e6` (Verweis in `harness/README.md`), danach die Commits dieser Closure. Berichte: [Review](../../../reviews/2026-11-05-76c72d8-slice-testserver-manuell-doku.md) (kein HIGH, zwei MEDIUM, drei LOW, drei INFO), [Verifikation](../../../reviews/2026-11-05-97b47e6-verifikation-slice-testserver-manuell-doku.md) (closure-fähig mit Bedingungen B-1 bis B-4, kein HIGH, kein MEDIUM, zwei LOW, zwei INFO).

**Abweichung vom DoD-Wortlaut, entschieden vom Planner:** Die DoD verlangt „je einen Beispielaufruf für zwei Clients“ und für jede Aussage Beleg oder die Kennzeichnung „nicht gemessen“. Beispiel 1 (Rohbytes-Client) ist gemessen (Handlauf des Implementers, Wiederholung durch den Verifier). Beispiel 2 (Zeichenfolge für Microsoft.Data.SqlClient, aus `SimulationBackendTests.cs` Zeile 36 übernommen, Teile wörtlich geprüft) ist **nicht gegen einen Handlauf gemessen** und so gekennzeichnet. Haken gesetzt, weil der Wortlaut die Kennzeichnung zulässt; Plan §3 (jeder Beispielaufruf vor der Aufnahme ausgeführt) ist für Beispiel 2 nicht erfüllt — dieser Teil bleibt offen und steht in §6 (Risiko 3) als weiter offen.

| Bedingung der Verifikation | Ausgang |
|---|---|
| B-1 `make gates` grün, mit Ausgabe | `make gates` auf `main` `3cfdd01`: Exit 0. Im Zweig `worktree-agent-a06a02c6e95a86d85` auf dem Stand dieser Closure: siehe Zeile „Gates im Zweig“ unten. `make docs-check` im Verifikationslauf: 724 Dateien, 0 Befunde. |
| B-2 Review-Befunde F-6 und F-7 | F-7 durch `97b47e6` (Verweis in `harness/README.md`). F-6 (Beleg-Ablage): die Ausgaben stehen in der Verifikation (Ausgabeabgleich, 14 Zeilen, selbst gefahren am 2026-11-05, Port 24733, `TESTSERVER_NAME=tdswire-verif-mt`) und in der Anleitung als „Zusammenstellung mehrerer Aufrufe“. Belegt: `testserver-up` bereit nach 4,0 s (Image im Cache, parallele Last des `make e2e`; Anleitung nennt 3,6 s als Einzelmessung), Statuszeile `laeuft`, Log `tdswire: gestartet, Port 1433`, Rohbytes-Client `laufzeit-client: Login ok` (Exit 0) und `Login fehlgeschlagen` bei falschem Kennwort (Exit 1, zusätzlich Gradle-Meldung), zweiter `up` bricht ab, `down` gestoppt und entfernt, zweiter `down` ohne Wirkung, `status` nach `down` „nicht gestartet“ mit Fehler, Warnung bei `TESTSERVER_HOST=0.0.0.0`. |
| B-3 Closure-Notiz, Register, Ausgänge, Paarungen | diese Sektion, §6 und das Register (unten). |
| B-4 Nachvollzug von einem frischen Klon durch eine zweite Person | bleibt offen; kein Befund dieses Slice, Closure-Trigger der Welle [welle-testserver-manuell](../welle-testserver-manuell.md). |

**Gates im Zweig:** `make gates` auf dem Stand dieser Closure (Zweig `worktree-agent-a06a02c6e95a86d85`, Basis `3cfdd01`): Exit 0. Die Klassen `werkzeug`, `tests` und `stempel` wurden bei passendem Stempel übersprungen ("tests uebersprungen, Beleg fuer d1fbf866...", "stempel uebersprungen, Beleg fuer ffe4e8f0..."; der Slice ändert nur Markdown), die Gates der Klasse `immer` liefen: `make docs-check` 729 Dateien, 0 Befunde; `gate-zuordnung` 15 Gates, Selbsttest 33 Fälle; `stempel-eingaenge` ok.

**Offene Befunde:** F-1 (LOW, Gradle-Meldung bei falschem Kennwort) und F-2 (LOW, Host der Einzelmessung 3,6 s) der Verifikation sind nicht eingearbeitet; sie ändern keine Aussage der Anleitung, die Zeilen stimmen. Als Folge-Hinweis für die Nachbesserung der Anleitung, kein Träger-Slice angelegt.

**Grenzen (nicht belegt):** Beispiel 2 (SqlClient) ohne Handlauf; ein Start mit `REQUIRED` (kein Zertifikat zur Hand), insbesondere das späte „nicht bereit“ (siehe Register); jede SQL-Anfrage gegen den Server; Windows-Authentifizierung, Last und Anfragearten außerhalb von [SPEC-036](../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) sind in der Anleitung als Grenzen genannt, nicht gemessen. Ort (`docs/user/`) und Client-Wahl sind nach dem Vorschlag des Slice gesetzt, vom Nutzer nicht bestätigt; die Rückfrage an den Nutzer steht aus.

**Steering-Loop-Eintrag:** geschärfte Beobachtung, keine neue Regel: Der Review fand in der Anleitung abgeleitete Aussagen (Verhalten bei `REQUIRED`, Setzung der Spezifikation) ohne Kennzeichnung; behoben durch „abgeleitet“ / „nicht gemessen“ im Text. Die Klasse ist verkörpert in `AGENTS.md` §3.8; weiteres Auftreten, kein neuer Zielort. Aus dem Schreiben der Anleitung fiel ein Fund im Werkzeug an: das Skript bricht bei `REQUIRED` ohne passende Benutzerliste erst mit „nicht bereit“ ab. Spec-Lücke: keine.

**Beobachtungs-Register:**

- Weiteres Auftreten: [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md), Evidence `evidence/slice-testserver-manuell-doku.md`.
- Neu: [BEO-TDS/start-required-ohne-benutzerliste-bricht-spaet-ab](../observations/BEO-TDS/start-required-ohne-benutzerliste-bricht-spaet-ab/observation.md) (1×, offen, Träger: noch keiner; Folge-Hinweis im `state.md`, kein Slice angelegt).
- Berührt, nicht fortgeschrieben: [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md) (Ort und Client-Wahl, §6) und [BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen](../observations/BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen/observation.md) (die Anleitung nennt die Testwerte; Dateirechte im Betrieb bleiben Sache von [slice-release-doku-prozess](../next/slice-release-doku-prozess.md)).

**Paarungen:** Anker — keiner, nichts verkörpert (kein `liegt in`). Folge-Slice — keiner angelegt; der neue Register-Eintrag trägt „Träger: noch keiner“ ausdrücklich. Register — getragen (oben).


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

**Vorgelagert — Sub-Area-Wahl prüfen:** berührt wird nur Dokumentation des Default-Bereichs `*` (Kürzel `TDS`).

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (Aussagen über die Messung hinaus: Beispiele nur mit Beleg). Der Zähler steht in `observations/BEO-TDS/`; keine weiteren Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.


### Sub-Area: `*` (Default des Repos)

- **Modus:** GF
- **Konventionen-Dichte:** Greenfield-Deklaration in `harness/conventions.md`.
- **Phase-Reife:** Phase 3.
- **Evidenz-/Diskrepanz-Risiko:** niedrig (GF); Belege durch Ausführen der Beispiele.
- **Reconciliation-Aufwand:** keiner (GF).
