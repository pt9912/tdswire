# Slice slice-testserver-up-down: Testserver von Hand starten und stoppen: Make-Targets, Test-Image, Port, Konfiguration, Selbsttest


**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md).

**Berührte Spec-Stellen:** SPEC-052 (Harness-Werkzeug `laufzeittest`, Stage `testserver`) · SPEC-005 · SPEC-010 (Umgebungsvariablen des Testservers). Ob SPEC-052 um ein Target für den Handlauf zu ergänzen ist, klärt der Slice in §6; die Spec nennt diesen Slice nie.

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `make testserver-up` startet den Testserver als Container im Hintergrund aus einem lokal gebauten Test-Image, veröffentlicht den TDS-Port auf dem Host und kehrt erst zurück, wenn der Server antwortet; `make testserver-down` stoppt und entfernt ihn; dazu `testserver-logs` und `testserver-status`.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Anleitung zum manuellen Test (Beispielaufrufe mit Clients, TLS-Schritte, Grenzen): [slice-testserver-manuell-doku](../done/slice-testserver-manuell-doku.md); die Targets tragen nur eine Kurzhilfe in `make help`.
- Ein Produkt-Image, Registry, Signierung: [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) schließt das Produkt-Image aus; das Image bleibt lokal.
- Aufnahme in `make gates`, `make e2e` oder die CI: ein dauerhaft laufender Container gehört in keinen Durchgang; der Selbsttest ist ein Werkzeug (kein Gate), Zuordnung nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md).
- Änderungen an `src/main` oder am Verhalten des Testservers: der Container startet den vorhandenen `main`.

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

- [x] `make testserver-up` / `testserver-down` (mit `-logs`, `-status`): Start im Hintergrund, Host-Port veröffentlicht (Voreinstellung nur Loopback `127.0.0.1`, Port und Image-Tag über Make-Variablen), Rückkehr erst bei Bereitschaft (Zeile `tdswire: gestartet` im Log), zweiter `up` bei laufendem Container bricht mit klarer Meldung ab, `down` ohne Container ist ein Erfolg ohne Wirkung.
- [x] Konfiguration des manuellen Laufs: Benutzerliste (Voreinstellung der Testwert `alice` / `secret` im Testmodus), Verschlüsselungsrichtlinie, optionale eigene Beschreibungsdatei der Simulations-Tabellen; eine Beispielbeschreibung mit Tabellen liegt bei. Die Voreinstellung nennt in der Ausgabe von `up` Host, Port, Benutzer und den Hinweis „Testwerte, keine Verschlüsselung“.
- [x] Selbsttest (`make testserver-selbsttest`, kein Gate): `up`, Rohbytes-Login gegen den veröffentlichten Port vom Host-Netz aus, `down`; danach kein Container, kein Netzwerk, Port frei. Zu jedem Zweig des Skripts (zweiter `up`, `down` ohne Container, Port belegt) eine Mutation, die den Selbsttest rot färbt.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Zeilen in `harness/README.md` §Sensors (Werkzeuge) für die neuen Targets; `make help` zeigt sie.
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
| `tools/mk/testserver.mk` | neu | Targets `testserver-up`, `-down`, `-logs`, `-status`, `-selbsttest`; eingebunden über `tools/mk/*.mk` |
| `tools/testserver.sh` | neu | Ablauf: Image bauen (Stage `testserver`), Container mit `-p` auf Loopback, Benutzerliste per `docker cp` wie in `tools/laufzeittest.sh`, Bereitschaft abwarten, Meldungen |
| `Dockerfile` | update (falls nötig) | Stage `testserver` bleibt; nur wenn der manuelle Lauf eine Änderung braucht (Beispielbeschreibung, Heap), sonst unverändert |
| `src/testserver/resources/manuell/` | neu | Beispielbeschreibung der Simulations-Tabellen für den Handlauf |
| `tools/testserver-selbsttest.sh` | neu | Zweige und Mutationen des Skripts |
| `tools/stempel/*.eingaenge` | update | Eingangslisten der Klassen, soweit die neuen Dateien von einem Gate gelesen werden ([ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)); sonst bewusst nicht |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Gebaut wird auf `tools/laufzeittest.sh` auf: dieselbe Stage, dieselbe Übergabe der Benutzerliste; der Unterschied ist Hintergrundlauf, `-p`, Bereitschaft und `down`.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` auf `main` grün; die Wahl der Fragen in §6 ist getroffen oder der Slice nimmt die dort genannte Voreinstellung.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Selbsttest verlangt mehr als einen Rohbytes-Login (etwa TLS) oder die Stage `testserver` braucht eine Umstellung, die mehr als eine Datei im Dockerfile berührt.
- `in-progress` → `open` (blockiert — Carveout?): eine Nutzerentscheidung aus §6 ändert die Voreinstellung der Port-Bindung oder der Anmeldung so, dass [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) fortzuschreiben ist.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, Review und Verifikation durchgeführt, Closure-Notiz mit Lerneintrag, und die Targets sind auf `main` von einem Menschen oder einem Agenten in frischem Kontext mit den Aufrufen der Kurzhilfe gestartet und gestoppt (Beleg: Ausgabe in der Closure-Notiz, mit Last).

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) sagt, der Testserver bindet die Bibliothek, damit sie getestet wird; ob ein Handlauf mit veröffentlichtem Port darunter fällt oder eine Fortschreibung braucht — **Ausgang:** **entfallen:** der Architect hat vor dem Start geprüft ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidungen 1 und 4, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidungen 1 und 2): ein Handlauf ist Testen, eine Bind-Adresse ist eine Setzung des Slice, keine Fortschreibung nötig. Die Prüfung steht im Verlauf dieser Sitzung, nicht in einer Datei; der Satz in §4 zur Rückführung nach `open/` überzieht, eine geänderte Voreinstellung der Port-Bindung berührt [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) nicht.
- Port-Bindung und Anmeldung — **Ausgang:** **entfallen:** Nutzerentscheidung am 2026-11-05: Loopback voreingestellt, per `TESTSERVER_HOST` wählbar, `up` warnt bei einem Host außer Loopback; die Voreinstellung `alice` / `secret` bleibt. Belegt durch den Selbsttest (Bindung an `127.0.0.1`, Warnung, Mutationen `loopback` und `warnung`).
- Name und Ort des Images — **Ausgang:** **entfallen:** Nutzerentscheidung am 2026-11-05: `tdswire-testserver:lokal`, über `TESTSERVER_IMAGE` überschreibbar, kein Registry-Push.
- Streit mit `make laufzeittest` um Port oder Containernamen — **Ausgang:** **entfallen:** der Selbsttest nimmt Namen und Port aus der Prozessnummer (`tdswire-st-<pid>`, Port 20000 bis 39999), `laufzeittest` benutzt ein eigenes internes Netzwerk ohne veröffentlichten Port; ein Handlauf auf 1433 und der Selbsttest liefen nebeneinander. Ein zweiter Handlauf auf demselben Port bricht mit der Meldung „Port belegt?“ ab (Mutationen `port-belegt-*`).
- Der Dauerlauf-Container wird nach einem Absturz des Rechners nicht aufgeräumt — **Ausgang:** **weiter offen:** nicht eingetreten. Verwandt ist die Grenze „Abbruch von `up` durch SIGINT lässt den Container stehen“ (Review F-9, im Kopf von `tools/testserver.sh` genannt); der nächste `up` meldet ihn klar. Ein eigener Register-Eintrag entsteht bei Eintreten.

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

**Gegenstand:** geliefert, DoD vollständig. Code: `7c1bef2` (Targets, Skript, Selbsttest, Beispielbeschreibung), Fix-Runde `d469afc` (Review F-1 bis F-8), danach die Prüfung „kein Netzwerk“ im Selbsttest und die Hilfe von `testserver-status`. Berichte: [Review](../../../reviews/2026-11-05-7c1bef2-slice-testserver-up-down.md) (kein HIGH, vier MEDIUM, alle eingearbeitet), [Verifikation](../../../reviews/2026-11-05-d469afc-verifikation-slice-testserver-up-down.md) (closure-fähig mit drei Bedingungen, kein HIGH, kein MEDIUM).

| Bedingung der Verifikation | Ausgang |
|---|---|
| B-1 `make gates` und `make testserver-selbsttest` auf `main` | `make gates` grün auf `d469afc` (Exit 0); `make testserver-selbsttest` grün auf dem Stand dieser Closure: 59 s mit Stages im Cache (Last 12 bis 15), beim ersten Lauf 159 s; 9 Mutationen färben rot (`zweiter-up-abbruch`, `zweiter-up-meldung`, `down-ohne-container`, `port-belegt-aufraeumen`, `port-belegt-meldung`, `nicht-bereit-aufraeumen`, `nicht-bereit-meldung`, `loopback`, `warnung`). `make gates` nach den letzten Änderungen steht im Commit dieser Closure. |
| B-2 Ausgang des ersten Risikos | **entfallen**, siehe §6 (Architect-Prüfung vor dem Start; die Prüfung liegt nicht als Datei vor, die Begründung steht in §6). |
| B-3 Lauf auf `main` mit Last (§5) | Aufrufe der Kurzhilfe am 2026-11-05 auf `main`, Last 15,7 (20 Kerne): `make testserver-up` kehrte nach 3,4 s zurück (Image im Cache), Ausgabe `bereit auf 127.0.0.1:1433`, `alice` / `secret`, „Testwerte, keine Verschluesselung“; `make testserver-status` meldet „laeuft“; `make testserver-logs` zeigt `tdswire: gestartet, Port 1433`; `make testserver-down` entfernt den Container, ein zweites `down` ist ein Erfolg ohne Wirkung; danach 0 Container, Port 1433 frei. Ausführender: der Implementer (Agent) und, in frischem Kontext, der Verifier. Kein Mensch, der den Plan nicht geschrieben hat, und kein frischer Klon; das Mehr der Welle bleibt Sache von [slice-testserver-manuell-doku](../done/slice-testserver-manuell-doku.md). |

Weitere Punkte der Verifikation: Die Hilfe von `testserver-status` sagte „Exit 1“; über `make` endet der Aufruf mit Exit 2 — Hilfe und README sagen jetzt „sonst Fehler“. Die Kostenzeile des Selbsttests nennt Last, Cache-Zustand und Kernel. „Kein Netzwerk“ (DoD Punkt 3) war wahr, aber ungeprüft: `up` legt keines an; der Selbsttest prüft es jetzt (`docker network ls`). Die Stempel-Eingangslisten sind bewusst nicht angefasst (`make stempel-eingaenge-pruefung` grün), das Dockerfile ist unverändert.

**Grenzen (nicht belegt):** TLS im Handlauf (`REQUIRED` mit Schlüsselspeicher und Passwortdatei, Dateirechte nach `docker cp`), ein Login mit der eigenen Benutzerliste, Fehlerpfade von `docker cp`, Abbruch durch SIGINT — im Kopf von `tools/testserver.sh` und des Selbsttests als „Nicht belegt“ genannt (`AGENTS.md` §3.8). Die Anleitung dafür liegt bei [slice-testserver-manuell-doku](../done/slice-testserver-manuell-doku.md).

**Steering-Loop-Eintrag:** geschärfte Beobachtung, keine neue Regel: Der Review fand im Selbsttest eines Werkzeug-Skripts Zweige ohne unterscheidende Mutation (Loopback-Voreinstellung, Warnung, Pfad „nicht bereit“) und eine Mutation, die den Zweig nur über sein Aufräumen traf. Register: Evidenz in [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md) (weiteres Auftreten, Klasse bereits verkörpert in `.harness/skills/reviewer.md`, kein neuer Zielort). Für den Slice galt: je Zweig des Skripts ein Vermerk `zweig:<name>` am Code und eine Mutation, die genau diesen Vermerk trifft, geprüft mit „die Mutation ändert das Skript“ im Selbsttest selbst (`mutiere` bricht rot ab, wenn `sed` nichts ändert).

**Beobachtungs-Register:** eine weitere Datei in `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/evidence/slice-testserver-up-down.md`; sonst keine Beobachtung angefallen. Die Klasse `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen` bleibt unberührt (Testwert, keine Lösung).


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

**Vorgelagert — Sub-Area-Wahl prüfen:** berührt wird `tools` und `src/testserver` des Default-Bereichs `*` (Kürzel `TDS`); keine zu grobe Sub-Area erkennbar.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; Treffer: `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen` (1×, Übergabe der Benutzerliste im Container; der Handlauf nimmt Testwerte und löst sie nicht), `BEO-TDS/eingangsliste-je-stage-von-hand` (3×, über der Schwelle, nur gemeldet: neue Dateien in den Eingangslisten).

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.


### Sub-Area: `*` (Default des Repos)

- **Modus:** GF
- **Konventionen-Dichte:** Greenfield-Deklaration in `harness/conventions.md`, Modus-Deklaration pro Sub-Area.
- **Phase-Reife:** Phase 3, die Targets folgen dem bestehenden Muster von `tools/mk/laufzeittest.mk`.
- **Evidenz-/Diskrepanz-Risiko:** niedrig (GF); die Messung am laufenden Container ist Teil der Closure.
- **Reconciliation-Aufwand:** keiner (GF).
