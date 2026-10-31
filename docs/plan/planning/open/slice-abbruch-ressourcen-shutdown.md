# Slice slice-abbruch-ressourcen-shutdown: Ressourcenfreigabe bei Disconnect und geordnetes Beenden mit TDS_SHUTDOWN

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0007](../../adr/0007-fehler-und-attention.md)

**Berührte Spec-Stellen:** SPEC-029 · SPEC-031 · SPEC-033 · SPEC-034 · SPEC-044 · SPEC-047 · SPEC-049 · ARC-015

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

**Ziel:** Ein abrupter oder regulärer Verbindungsabbruch in jedem Zustand gibt Cancel, Backend-Strom und Sitzungsressourcen frei, ohne den Prozess zu gefährden, und das geordnete Beenden nach `SPEC-044` lässt laufende Anfragen bis `SHUTDOWN_GRACE` abschließen, beantwortet spätere Anfragen mit `TDS_SHUTDOWN` und kehrt nach spätestens `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` zurück.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Abbruchpfad selbst (Cancel, `CANCELLING`, Frist) — `slice-attention-cancel-timeout`; dieser Slice ruft ihn bei Disconnect und nach Ablauf der Shutdown-Frist.
- Listener, Sockets und Erkennen des Verbindungsendes im Transport — Welle `welle-transport-tls`; dieser Slice nimmt das Verbindungsende als Ereignis und den Listener-Stop als Aufruf.
- Fuzz-Gate (defekte Paketlängen, Offsets und Typwerte, Zerstückelung) — `slice-qa-stabilitaet` (geschlossen, Anmeldepfad); hier ein Lecktest mit mehreren Verbindungen, kein Gate. Die Durchsetzung von `MAX_CONNECTIONS` und der Test bis zur Grenze gehören dagegen **hierher** (DoD-Punkt 1): `slice-qa-stabilitaet` hat die Grenze nicht gebaut und schiebt sie auf diesen Slice zurück.
- Observability von Verbindungsende und Shutdown (`SPEC-017`) — Träger: Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md); nur der minimale Log-Anschluss.
- Garantierte Unterbrechbarkeit externer Backendoperationen — Out-of-Scope von [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe).
- Zeitgleichheit und Anmeldepfad-Sicherheit — `slice-qa-sicherheit`; Drosselung — `slice-qa-anmeldedrosselung`.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Disconnect in jedem Zustand (Handshake, `IDLE`, `RUNNING`, `STREAMING`, `CANCELLING`; abrupter TCP-Abbruch und reguläres Schließen): Cancel höchstens einmal, Backend-Strom geschlossen, Sitzung entfernt, keine Schreibversuche auf die tote Verbindung, der Prozess läuft weiter; Lecktest mit vielen Verbindungen am Fake-Backend (Zähler offener Ströme und Sitzungen). Dazu gehört der offene Punkt aus [ADR-0004](../../adr/0004-session-zustandsautomat.md): Bei 50 harten Abbrüchen (RST) während `RUNNING` und `STREAMING` traten im Spike nicht abgefangene `IOException: Broken pipe` in den Threads der Kanalgruppe auf (Ursache im Spike-Adapter, nicht analysiert); ein Test mit hartem Abbruch zählt unbehandelte Ausnahmen in den Threads der Laufzeit, und ihre Zahl ist 0 oder die Ursache steht in der Closure-Notiz. **Grenze `MAX_CONNECTIONS`** (`SPEC-034`, Träger aus `slice-qa-stabilitaet`, das sie nicht geliefert hat): Der Listener nimmt höchstens `MAX_CONNECTIONS` gleichzeitige Verbindungen auf (Konfigurationsschlüssel mit Bereichsprüfung, Voreinstellung 1000 vorläufig; gezählt wird dort, wo die Aufnahme heute zählt, im Verbindungsregister des Listeners); eine Verbindung über der Grenze wird abgewiesen, ohne dass der Prozess oder eine bestehende Verbindung leidet, und nach dem Ende einer Verbindung nimmt der Listener wieder auf. Der Test läuft bis zur Grenze mit einer kleinen Grenze (genau `MAX_CONNECTIONS` Verbindungen sind zugleich im Handler, die nächste wird abgewiesen, danach wird nach dem Ende einer Verbindung wieder aufgenommen) und mit dem Verbindungssturm aus `slice-qa-stabilitaet` über der Grenze. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Negative und Boundary, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), `SPEC-029`, `SPEC-034`)
- [ ] Geordnetes Beenden nach `SPEC-044` Nr. 1 bis 5 mit gesteuerter Uhr: Listener schließt zuerst; Verbindung ohne laufende Anfrage endet sofort; laufende Anfrage schließt innerhalb der Frist regulär ab; eine Anfrage nach dem Beginn wird mit ERROR `TDS_SHUTDOWN` und DONE mit Fehlerstatus beantwortet, danach endet die Verbindung; nach Ablauf der Frist Abbruch wie bei ATTENTION, nach `CANCEL_TIMEOUT` Trennen; Rückkehr nach spätestens Frist plus Abbruchfrist; zweite Anforderung wirkungslos; `SHUTDOWN_GRACE` = 0 sofort. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Beenden)
- [ ] Der Stop des Bootstrap (`slice-bootstrap-konfiguration`) ruft diesen Ablauf statt des Stops ohne Frist-Logik; Rohbytes-Smoke: Prozess-Stop mit einer idle Verbindung und einer laufenden Anfrage. (Folge-Slice aus `welle-handshake-login` §6, geordnetes Beenden)
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` (Package-Name ein Vorschlag) | update | Aufräumpfad bei Verbindungsende; Sitzungsregister |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` (Bootstrap) | update | Stop ruft das geordnete Beenden; Frist aus der Konfiguration (`SPEC-047`) |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/listener/`, `src/main/kotlin/io/github/pt9912/tdswire/config/` | update | Aufnahme-Grenze `MAX_CONNECTIONS` (`SPEC-034`) im Verbindungsregister des Listeners, Konfigurationsschlüssel mit Bereichsprüfung |
| Geordnetes Beenden (Package-Name offen) | neu | Ablauf `SPEC-044` Nr. 1 bis 5, `TDS_SHUTDOWN` über das Mapping |
| `src/test/kotlin/io/github/pt9912/tdswire/` | neu | Lecktest, Beenden mit gesteuerter Uhr, Rohbytes-Smoke Stop ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)) |
| `spec/spezifikation.md` | update | `SPEC-044` und `SPEC-029` nach den Nutzerentscheidungen (Zeitpunkt des Schließens nach `TDS_SHUTDOWN`, Verbindungen mit unvollständiger Nachricht oder laufendem Handshake) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-fehler-abbruch` ist eingetreten, `slice-attention-cancel-timeout` liegt in `done/`, die Slices `slice-transport-listener-lebenszyklus` und `slice-bootstrap-konfiguration` liegen in `done/` (Verbindungsende-Ereignis, Listener-Stop, Stop-Pfad stehen), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2. Schnittvorschlag: Disconnect (DoD-Punkt 1) und geordnetes Beenden (DoD-Punkte 2 und 3) als zwei Slices; die Welle bekommt dann fünf.
- `in-progress` → `open` (blockiert — Carveout?): Der Transport meldet kein Verbindungsende als Ereignis, oder die Nutzerentscheidung zum Zeitpunkt des Schließens nach `TDS_SHUTDOWN` fehlt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`) und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Slice ist der größte der Welle: Disconnect über alle Zustände (mit der Grenze `MAX_CONNECTIONS` im selben Liefer-Punkt), ein fünfteiliger Ablauf und der Bootstrap-Anschluss. Die Rückführung nennt den Schnittvorschlag; die Grenze `MAX_CONNECTIONS` ist der Teil, der sich am leichtesten ablösen lässt (eigener Slice mit dem Sturmtest aus `slice-qa-stabilitaet`). — **Ausgang:** offen bis zur Closure
- Spec-Lücke: `SPEC-034` nennt das Ressourcenlimit als Fehlermodus der Anmeldung, beschreibt aber nicht, was der Client bei einer abgewiesenen Verbindung sieht (Schließen ohne Antwort oder ERROR vor dem Login). Entscheidungsbedarf (Nutzer); bis dahin ist „Schließen ohne Antwort“ im Test eine Festlegung ohne Messung, am Referenzclient nicht gemessen. — **Ausgang:** offen bis zur Closure
- Spec-Lücke: `SPEC-044` beschreibt nicht, wann eine Verbindung mit unvollständig empfangener Nachricht oder mitten im Handshake endet (Nr. 2 nennt nur Verbindungen ohne unvollständige Nachricht, Nr. 3 und 4 laufende Anfragen). Entscheidungsbedarf (Nutzer). — **Ausgang:** offen bis zur Closure
- Entscheidungsbedarf (Nutzer; offener Punkt von [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), Träger ist dieser Slice, DoD-Punkt 2): Zeitpunkt des Schließens nach `TDS_SHUTDOWN` und Schließen auch nach DONE mit ATTENTION-Status im Beenden (aus `SPEC-044` Nr. 2 und 4 abgeleitet, nicht gemessen). — **Ausgang:** offen bis zur Closure
- Die Nummer und Meldung von `TDS_SHUTDOWN` kommen aus `slice-fehler-mapping`; ein Herunterfahren ist am echten Server nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Obergrenze des Beendens bei einem Handler, der den Abbruch ignoriert (gemeldet von [slice-transport-listener-lebenszyklus](../done/welle-transport-tls/slice-transport-listener-lebenszyklus.md) §6, Review F-10): `TdsListener.shutdown()` kehrt erst zurück, wenn jeder Handler endet; `SPEC-044` Nr. 5 nennt Frist plus `CANCEL_TIMEOUT` als Obergrenze, der Listener setzt sie ohne Session nicht durch, und `terminate` gibt nach 5 s ohne Meldung auf. DoD-Punkt 2 (Rückkehr nach spätestens Frist plus Abbruchfrist) trägt den Punkt; ein Test mit einem Handler, der den Abbruch ignoriert, gehört dazu. — **Ausgang:** offen bis zur Closure.
- Zeitverhalten: Tests mit gesteuerter Uhr belegen den Ablauf, nicht reale Fristen unter Last; `SHUTDOWN_GRACE` (10 s) und `CANCEL_TIMEOUT` (5 s) sind vorläufige Defaults. — **Ausgang:** offen bis zur Closure
- Disconnect bei vom Server geschlossener Verbindung mit Pooling im Client ist nicht gemessen; ebenso TDS 8.0. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Ein Fake-Backend belegt die Freigabe von Strom und Sitzung, nicht die Freigabe von Ressourcen eines echten Treibers (Port-Vertrag: das Backend sorgt selbst, Annahme). — **Ausgang:** offen bis zur Closure

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Packages (`protocol`, `transport`, `runtime`) sind Pfad-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, Stand *geplant*. Treffer: `TDS_SHUTDOWN` und Verbindungsende am echten Server nicht gemessen (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, Stand *geplant*. Treffer: Pooling bei vom Server geschlossener Verbindung (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, Stand *verkörpert*. Treffer: Lecktest und Zeittests belegen das Fake-Backend und die gesteuerte Uhr, nicht den Betrieb.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: Kennungen stehen als Link.
- Ohne Treffer: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
