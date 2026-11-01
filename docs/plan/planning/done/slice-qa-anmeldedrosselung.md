# Slice slice-qa-anmeldedrosselung: Drosselung wiederholter Anmeldeversuche entscheiden

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-008 · SPEC-012 · SPEC-051

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-31.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Für wiederholte Anmeldeversuche ist entschieden, ob und wie der Server sie drosselt, und die Entscheidung ist gebaut und gemessen, oder der Verzicht ist per ADR festgehalten; damit löst der Slice [CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) auf.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Enumerationsfestigkeit als Abnahmekriterium — [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) verlangt sie nicht; `SPEC-012` sichert nur die gleiche Ablehnung zu.
- Zeitgleichheit des Passwortvergleichs und Belegung der Plätze des `AuthPool` — beides misst [slice-qa-sicherheit](../done/slice-qa-sicherheit.md) und nennt die Grenzen; dieser Slice baut keine Abhilfe für die Zeitgrenze gemischter Listen.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Entscheidung über die Drosselung (Art, Schlüssel, Grenzwerte, oder Verzicht) als ADR `Accepted`: [ADR-0016](../../adr/0016-anmeldedrosselung.md) ist `Accepted` (Option B, Grenzwerte als Setzung).
- [x] Bei einer Drosselung: Umsetzung im Anmeldepfad mit Tests, die zeigen, dass wiederholte Versuche gebremst werden und reguläre Anmeldungen nicht dauerhaft blockiert sind (Messung der Wirkung auf die Wartezeit, wie `AuthPoolOccupationTest` sie für die Plätze misst).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`; [Review](../../../reviews/2026-10-31-f4ec33b-slice-qa-anmeldedrosselung.md), [Verifikation](../../../reviews/2026-11-01-7897412-verifikation-slice-qa-anmeldedrosselung.md)) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist; [CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) aufgelöst.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| ADR zur Drosselung | vorhanden | [ADR-0016](../../adr/0016-anmeldedrosselung.md) (`Accepted`, 2026-10-31: Option B, Verzögerung ohne Sperre je Quelladresse) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/LoginThrottle.kt` | neu | Zähler je Adresse (Fehlversuche, laufende Versuche), Staffel, Fenster, begrenzte Tabelle; Uhr und Warten austauschbar |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/HandshakeDriver.kt` | update | Wartezeit vor dem Adapteraufruf, Fehlversuch zählt nur bei Ablehnung des Adapters; Passwort bei abgebrochenem Warten überschrieben |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/AuthExecutor.kt` | update | `AuthPool` trägt die Drosselung (ein Objekt je Prozess); KDoc nennt `CO-003` nicht mehr |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/channel/` | update | `SuspendingChannel.remoteAddress` (Voreinstellung `null`), `SocketChannelAdapter` liest sie, `PeekedChannel`, `SwitchableChannel` und `TlsChannel` geben sie weiter |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/listener/` | update | `Connection.remoteAddress`, `ManagedConnection` gibt die des Kanals weiter |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/ConnectionWiring.kt` | update | der Handler übergibt `connection.remoteAddress` an den Treiber |
| `spec/spezifikation.md` | update | `SPEC-053` (Konstanten und Verhalten, Setzung), Verweise in `SPEC-008` und `SPEC-012`, Historie; eigener Commit (Folgepflicht der ADR) |
| `src/test/kotlin/` (`LoginThrottleTest`, `LoginThrottleDriverTest`, `LoginThrottleWiringTest`, `LoginThrottleTimingTest`, `RemoteAddressTest`, `TlsRemoteAddressTest`, `ListenerRemoteAddressTest`) | neu | Staffel, Fenster, Erfolg, laufende Versuche, Tabellengrenze, Reihenfolge Warten vor Adapter, Gleichheit für bekannte und unbekannte Kennung, Echtzeit-Wartezeit, Adresse am echten Socket |

Die Liste ist im ersten Lauf des Implementers erweitert.

Entscheidungen des Nutzers (2026-11-01): (1) Ein Kanal ohne bekannte Adresse der Gegenstelle bleibt ungedrosselt; die Alternativen (gemeinsamer Schlüssel, Verbindung schließen) sind verworfen. (2) `SPEC-053` steht als ausdrücklich gekennzeichnete Setzung ohne Lastenheft-Anker; kein Eingriff ins Lastenheft.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-sicherheit](../done/slice-qa-sicherheit.md) in `done/` (seine Messungen von Zeitgleichheit und Platzbelegung sind die Eingabe der Entscheidung).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0016](../../adr/0016-anmeldedrosselung.md) verliert den Status `Accepted`.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Eine Drosselung je Benutzername lässt sich zur Sperre eines regulären Benutzers missbrauchen (ein Dritter sperrt ihn mit falschen Versuchen). — **Ausgang:** entfallen: Der Schlüssel der Drosselung ist die Adresse der Gegenstelle ([ADR-0016](../../adr/0016-anmeldedrosselung.md) Option B, `SPEC-053`); die Kennung geht nicht ein, es gibt keine Sperre, nur eine Verzögerung mit Deckel 5 s. Die Verifikation bestätigt gleiche Wartezeit für bekannte und unbekannte Kennung und fängt eine kennungsabhängige Wartezeit als Mutation.
- Eine Drosselung je Quelladresse trifft Clients hinter gemeinsamen Adressen; nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` im Register; der Re-Evaluierungs-Trigger steht in [ADR-0016](../../adr/0016-anmeldedrosselung.md).
- Grenzen der Wirkung, die der Slice benennt und nicht misst: Schwall gleichzeitiger Verbindungen (der `AuthPool` begrenzt die Rate, nicht die Staffel), IPv6 mit vielen Adressen je Präfix, Proxy mit gemeinsamer Adresse, Log der Drosselung. — **Ausgang:** weiter offen: → `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` im Register.

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

- **Was hat funktioniert:** Die Wartezeit liegt vor dem Adapteraufruf und belegt keinen Platz des `AuthPool`; der Schlüssel ist die Adresse ohne Port und Zone, Kennung und Passwort gehen nicht ein. Die Verifikation ([Bericht](../../../reviews/2026-11-01-7897412-verifikation-slice-qa-anmeldedrosselung.md)) fand weder HIGH noch MEDIUM; zwei frische Testläufe ohne Cache (je 808 bestanden, 0 rot) und drei Mutationen (Passwort-Überschreiben im Abbruchzweig entfernt, Zählen bei jedem Ergebnis, kennungsabhängige Wartezeit) werden von genau den zuständigen Tests gefangen.
- **Zuordnung der Aussagen:**
  - *Gemessen* (Docker-Image des Laufs, Werte gelten nur dort): Wartezeit in Echtzeit in `LoginThrottleTimingTest` (Sollwerte 200, 400 und 400 ms, Fenster 1 s) und am echten Socket auf `127.0.0.1` in `LoginThrottleWiringTest` (fünf Fehlversuche ohne Wartezeit, sechster 250 ms, siebter 500 ms und angenommen, nach 60 s frei); die Adresse der Gegenstelle am echten Socket und über TLS in `RemoteAddressTest`, `TlsRemoteAddressTest` und `ListenerRemoteAddressTest`.
  - *Handvektor / Selbstkonsistenz:* die zwölf Stufen der Staffel (250 ms, verdoppelt bis 5 s) und die Fenster- und Verdrängungsregeln gegen die Zusage in `SPEC-053` mit gesteuerter Uhr; das prüft den Code gegen seine Setzung, nicht gegen einen fremden Maßstab.
  - *Festlegung:* alle Zahlen (5 freie Fehlversuche, 250 ms bis 5 s, 60 s, 10000 Adressen) sind Setzung der ADR, nicht gemessen; `SPEC-053` steht als Setzung ohne Lastenheft-Anker, vom Nutzer bestätigt (2026-11-01); ein Kanal ohne bekannte Adresse bleibt ungedrosselt (fail-open), vom Nutzer bestätigt (2026-11-01).
  - *Nicht gemessen:* ein Schwall gleichzeitiger Verbindungen (Durchsatz, nur der Zählerstand bei sieben gleichzeitigen Eintritten ist getestet), IPv6 mit vielen Adressen je Präfix, Verhalten hinter einem Proxy, die Log-Ausgabe der Drosselung (sie schreibt nichts), die Streuung der Zeit-Tests unter Last (kein Rot in zwei frischen Läufen und drei Mutationsläufen bei Systemlast um 16 auf 20 Kernen; ein Stillstand der JVM über 200 ms bei einem freien Versuch ist nicht beobachtet und nicht ausgeschlossen).
- **Was ging anders als geplant:** Der Review fand ein HIGH an der Spec-Schicht: `SPEC-053` führte eine Zusage ohne Anker im Lastenheft ein, und die ADR benannte diese Erweiterung als Entscheidung des Nutzers, ohne dass die Annahme sie ausdrücklich nennt. Der Nutzer bestätigte die Setzung ohne Anker; die Fix-Runde kennzeichnete sie in der Spezifikation und nahm die Bedingung „nur eine Verbindung nach der anderen“ in die Zusage auf. Der Plan nannte nach der Annahme der ADR noch `Proposed` (Verifikation F-1), die Tabelle von [CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) die entfernte Nennung in der KDoc von `AuthExecutor` (Review F-5).
- **Steering-Loop-Eintrag:** benannte Spec-Lücke: Das Lastenheft verlangt keinen Schutz gegen wiederholte Anmeldeversuche ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); die Zusage steht nur als gekennzeichnete Setzung in `SPEC-053`. Ob das Lastenheft eine Zusage aufnimmt, entscheidet der Nutzer; bis dahin trägt die Spezifikation die Lücke selbst. Verkörpert ist damit keine Regel, der Eintrag trägt kein `liegt in`.
- **Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer:** `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` steht mit diesem Slice bei 3× (`slice-antwort-mehrteilig-output`, `slice-qa-sicherheit`, `slice-qa-anmeldedrosselung`): Die Fix-Runde entfernte die Nennung von `CO-003` im Code, die Tabelle des Carveouts behauptete sie weiter; Spec und Code nannten verschiedene Verdrängungsregeln. Den Lese-Schritt macht die Welle-Closure.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-qa-anmeldedrosselung.md` ergänzt in `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (7×, geplant, Drosselung gebaut), in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×) und in `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert); neu angelegt `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (1×) für die Konjunktiv-Funde in Test-KDoc und die veralteten Zustandsangaben im Plan.
- **Folge-Slices:** keine neuen; die offenen Grenzen trägt das Register.
- **Offene Kleinigkeit für einen Implementer:** KDoc von `LoginThrottleTimingTest` (Verifikation F-2: Konjunktiv „wäre nötig“; F-3: die Messzeile nennt drei Läufe, belegt sind fünf mit Last um 16).
- **Risiken aus §6:** siehe §6 (Fremdsperre je Benutzername entfallen; Clients hinter gemeinsamer Adresse und die Grenzen der Wirkung weiter offen).
- **Drei Paarungen:** Anker: kein Eintrag mit `liegt in`, nichts zu paaren. Folge-Slice: keine. Register: die vier Einträge oben liegen als Dateien vor. Die Welle-Closure prüft sie erneut.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Der Slice berührt das ganze Repo (`*`), eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` trägt die Drosselung als offenen Punkt und ist hier der Träger.

Alle berührten Sub-Areas GF.
