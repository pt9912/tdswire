# Slice slice-betrieb-zustandsereignisse: Betriebszustand: Zustandsereignisse, Startdiagnose am Prozess und Konfigurationsabdeckung der Limits

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md)

**Berührte Spec-Stellen:** SPEC-005 · SPEC-009 · SPEC-010 · SPEC-033 · SPEC-034 · SPEC-043 · SPEC-044 · SPEC-047 · ARC-002 · ARC-008 · ARC-009 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

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

**Ziel:** Die Akzeptanzkriterien von [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) sind am Serverprozess belegt: ein gestarteter Server meldet seine Zustandsereignisse über die Log-Schnittstelle, ein belegter Port und eine ungültige sicherheitsrelevante Konfiguration enden mit verständlicher Diagnose und ohne gebundenen Port, und jede Konstante der Spezifikation mit Frist- oder Grenzwert hat einen Konfigurationsschlüssel mit Validierung.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Startprobe der TLS-Konfiguration (SPEC-043) und die Diagnose für einen belegten Port als Code — [slice-transport-listener-lebenszyklus](../next/slice-transport-listener-lebenszyklus.md) (Welle [welle-transport-tls](../welle-transport-tls.md)); dieser Slice formuliert sie nicht neu, er belegt am Prozess, dass ihre Meldung über die Schnittstelle erscheint.
- Laden und Validieren der Konfiguration, Exit-Code und Abbau nach fehlgeschlagenem Start als Mechanik — [slice-bootstrap-konfiguration](slice-bootstrap-konfiguration.md).
- Das Verhalten des geordneten Beendens (SPEC-044, Frist `SHUTDOWN_GRACE`, `TDS_SHUTDOWN`) — [slice-abbruch-ressourcen-shutdown](slice-abbruch-ressourcen-shutdown.md) (Welle [welle-fehler-abbruch](../welle-fehler-abbruch.md)); hier erscheinen nur Beginn und Ende des Beendens als Ereignis.
- Semantik und Durchsetzung von `QUERY_TIMEOUT` und `CANCEL_TIMEOUT` — [slice-attention-cancel-timeout](slice-attention-cancel-timeout.md) und [slice-adr-backend-grenzen](slice-adr-backend-grenzen.md) (Frist); dieser Slice legt höchstens den Schlüssel samt Bereichsprüfung an und behauptet keine Wirkung.
- Metriken und Spans — [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md); eine Verwaltungsoberfläche und das Neuladen der Konfiguration zur Laufzeit — nach [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) bzw. [slice-bootstrap-konfiguration](slice-bootstrap-konfiguration.md) ausgeschlossen.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Zustandsereignisse** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), Happy Path): Start mit wirksamer Konfiguration ohne Geheimnisse, Pfade und Prüfwerte, Listener gebunden (Adresse, Port), Beginn und Ende des Beendens, Login angenommen und abgelehnt (der Grund steht nur im Log, SPEC-012), Verbindung geöffnet und geschlossen mit Grund, Abschwächung der Verschlüsselung sichtbar (SPEC-010), Anfragefehler mit `error_code`; belegt mit dem Sammler-Adapter am Serverprozess, jede Zeile mit den Pflichtfeldern, soweit vorhanden.
- [ ] **Startdiagnose am Prozess** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), Boundary und Negative): Prozesstest in Docker — ein belegter Port beendet den Start mit Exit-Code ungleich null und einer Diagnose, die Adresse und Port nennt; ein unlesbares Zertifikat oder Schlüssel nennt den Teil nach SPEC-043 ohne Pfad und Inhalt; eine ungültige oder unsichere Konfiguration (etwa abgeschwächte Verschlüsselung ohne ausdrückliche Konfiguration) wird abgewiesen und aktiviert keinen unsicheren Default; danach laufen weder Listener noch Threads.
- [ ] **Konfigurationsabdeckung:** Eine Tabelle ordnet jede Konstante aus Abschnitt 3 der Spezifikation (`LISTEN_PORT`, `MAX_TDS_PACKET_BYTES`, `MAX_MESSAGE_BYTES`, `LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES`, `SHUTDOWN_GRACE`, `QUERY_TIMEOUT`, `ENCRYPTION_POLICY`, `CANCEL_TIMEOUT`, `MAX_CONNECTIONS`) einem Konfigurationsschlüssel, dem Träger-Slice und einem Test zu; fehlt in den Slices der Wellen der Schlüssel, legt dieser Slice ihn mit Bereichsprüfung an, ohne Verhalten zu ergänzen. Die Tabelle steht in der Closure-Notiz.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
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
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` und `config/` <!-- d-check:ignore (geplant: neue Datei) --> | update | Zustandsereignisse an den Stellen von Start, Bindung, Beenden; fehlende Konfigurationsschlüssel |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Ereignisfolge am Serverprozess mit Sammler; Konfigurationstests Happy/Boundary/Negative; `@spec`-Tag SPEC-005, SPEC-010, SPEC-043 |
| `tools/mk/` | update | Make-Target des Prozesstests in Docker (Name legt der Implementer fest, Eintrag in `harness/README.md` §Sensors) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-log-schnittstelle](slice-betrieb-log-schnittstelle.md), [slice-bootstrap-konfiguration](slice-bootstrap-konfiguration.md), [slice-transport-listener-lebenszyklus](../next/slice-transport-listener-lebenszyklus.md) und [slice-abbruch-ressourcen-shutdown](slice-abbruch-ressourcen-shutdown.md) liegen in `done/`, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Konfigurationsabdeckung sprengt den Rahmen; Schnittvorschlag: Abdeckung als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): ein Slice, dessen Verhalten hier belegt werden soll, ist nicht in `done/`.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Konfigurationstabelle, die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Überschneidung mit den Tests von [slice-bootstrap-konfiguration](slice-bootstrap-konfiguration.md) und [slice-transport-listener-lebenszyklus](../next/slice-transport-listener-lebenszyklus.md) (Exit-Code, belegter Port): jene prüfen die Mechanik am Loopback und in der Einheit, dieser Slice den Prozess und den Inhalt der Meldung; bei Überschneidung wird hier gekürzt, nicht dort. — **Ausgang:** offen bis zur Closure.
- `QUERY_TIMEOUT` (SPEC-009) hat keinen benannten Träger für die Semantik; [slice-attention-cancel-timeout](slice-attention-cancel-timeout.md) führt die Frage, welche Frist `TDS_TIMEOUT` auslöst, als Spec-Lücke. Ein Schlüssel ohne Wirkung läse sich wie eine wirksame Einstellung; der Slice legt ihn nur an, wenn die Semantik entschieden ist, sonst steht er in der Tabelle als offen mit Träger (AGENTS.md §3.8). — **Ausgang:** offen bis zur Closure.
- `MAX_CONNECTIONS` (SPEC-034): Die Durchsetzung hat in den Plänen keinen eindeutigen Träger ([slice-qa-stabilitaet](slice-qa-stabilitaet.md) testet, [slice-abbruch-ressourcen-shutdown](slice-abbruch-ressourcen-shutdown.md) berührt die Stelle); findet der Slice beim Start keinen, nennt die Closure die Lücke und legt einen Folge-Slice an. — **Ausgang:** offen bis zur Closure.
- „Verständlich“ ist nicht messbar: Das Kriterium ist, dass die Diagnose Ursache und Schlüssel bzw. Adresse und Port nennt und nie Pfad, Inhalt oder Prüfwert; die Beurteilung trägt der Review. — **Ausgang:** offen bis zur Closure.
- Ein Prozesstest mit belegtem Port kann an der Portwahl im Container flackern; der Test belegt den Port selbst und nennt ihn. — **Ausgang:** offen bis zur Closure.
- Gebaut ist die Mechanik der Wellen; der Slice belegt Verhalten gegen den Rohbytes-Client und die Prozessausgabe, nicht gegen den Referenzclient. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die Observability-Schicht (ARC-009) ist ein Pfad-Cluster (Achse 3) mit eigener Importregel (Achse 1 plausibel); eine eigenständig abgleichbare Inventur-Linie (Achse 2) besteht erst mit dem Code. Der Modus Greenfield bliebe derselbe, eine eigene Zeile in `harness/conventions.md` löste keine andere Regel aus; `*` bleibt, eine Ausdifferenzierung ist Sache des Nutzers und hier nicht vorgenommen.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, Stand *geplant* (Träger [slice-qa-sicherheit](slice-qa-sicherheit.md)). Betrifft die Ereignisse zu Login und Konfiguration: Passwort und Prüfwert erscheinen nirgends (§2).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über die Diagnose nennt, was geprüft ist (§2, §6).
- `BEO-TDS/offene-punkte-ohne-traeger` — 6×, Stand *verkörpert* (`.claude/commands/implement-slice.md`). Betrifft `QUERY_TIMEOUT` und `MAX_CONNECTIONS`: jeder offene Punkt braucht einen Träger als Datei (§6).
- `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` — 1×, offen. Betrifft die Prüfung „kein Thread läuft weiter“: Zähler mit Untergrenze.
- `BEO-TDS/ablage-im-repo-bricht-docs-check` — 1×, offen. Betrifft das Make-Rezept des Prozesstests, falls es Ausgaben im Repo ablegt (`mktemp -d` mit `trap`).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft den Prozesstest in der Docker-Stage: gecachte grüne Läufe belegen Wiederholung nicht.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
