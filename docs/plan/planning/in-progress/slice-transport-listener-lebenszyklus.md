# Slice slice-transport-listener-lebenszyklus: Listener und Verbindungslebenszyklus: Annahme, Startprobe und geordnetes Schließen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-transport-tls

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-005 · SPEC-008 · SPEC-010 · SPEC-017 · SPEC-043 · SPEC-044 · SPEC-047 · ARC-002 · ARC-003

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein TCP-Listener nimmt Verbindungen an, gibt jeder einen eigenen Scope und eine Übergabe-Schnittstelle an die spätere Protokollschicht, prüft vor dem Binden die TLS-Konfiguration mit einer Startprobe und beendet sich geordnet; ein Loopback-Test durchläuft Listener, beide TLS-Pfade und Nachrichtenaustausch (Smoke der Welle).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Verbindungs-Zustandsautomat, PRELOGIN-/LOGIN7-Ablauf, Auswahl des Pfades je Client und die Antwort `TDS_SHUTDOWN` — Zustandsautomat und Fehlersemantik sind [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) (beide `Proposed`); der Listener übergibt eine Verbindung an einen Handler und kennt keine Protokollzustände. Eine „laufende Anfrage“ kennt der Transport nur als Rückmeldung des Handlers.
- Abbruch laufender Anfragen (Cancel an das Backend, `CANCEL_TIMEOUT`, SPEC-029, SPEC-033) — braucht Session und Backend-Port; der Listener bietet nur die Frist `SHUTDOWN_GRACE` und das Trennen am Ende.
- Validierung der Gesamtkonfiguration und Parsen von `ENCRYPTION_POLICY` (ARC-002, SPEC-010): der Listener bekommt eine validierte Bind-Adresse, einen Port und TLS-Material; wer sie aus Umgebung oder Datei liest, ist eine Lieferung des Packages `config`.
- Logging und Spans (SPEC-017, `tds.connection`) und Metriken — der Listener meldet Ereignisse an eine Schnittstelle; die Anbindung an Telemetrie ist Sache von `observability` (Träger: Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md)).
- Start des Serverprozesses, Composition Root, JVM-Flags des Prozesses (`runtime`) und ein Make-Ziel zum Starten.
- MARS, Pooling und `sp_reset_connection` — ungemessen laut Clientprofil.
- Läufe gegen den Referenzclient und einen echten SQL Server — `slice-qa-kompatibilitaet`, `slice-qa-stabilitaet`; dieser Slice belegt Listener-Verhalten am Loopback.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Listener** (SPEC-005, [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): Bind auf Adresse und Port, Annahmeschleife, je Verbindung ein eigener Coroutine-Scope und verbindungslokaler Zustand; der Absturz oder das Trennen einer Verbindung beeinträchtigt keine andere. Ein belegter Port beendet den Start mit einer Diagnose, die Adresse und Port nennt; nach jedem fehlgeschlagenen Start laufen keine Threads und kein Listener des Servers weiter.
- [ ] **Verbindungslebenszyklus** (SPEC-008, SPEC-044, SPEC-047, [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)): `LOGIN_TIMEOUT` ab Verbindungsaufbau, bis der Handler „Login abgeschlossen“ meldet, danach schließt der Transport ohne Antwort; Client-Disconnect räumt Scope und Kanal auf; geordnetes Schließen: der Listener schließt zuerst, eine im selben Moment angenommene Verbindung endet sofort, eine Verbindung ohne laufende Anfrage und ohne halb empfangene Nachricht endet sofort, die übrigen höchstens bis `SHUTDOWN_GRACE`, danach wird getrennt; eine zweite Shutdown-Anforderung ist wirkungslos.
- [ ] **TLS-Startprobe** (SPEC-043, SPEC-023, [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): Zertifikatskette und Schlüssel werden geladen und mit der Schleife aus `slice-transport-tls-schleife` im Speicher gegen eine Gegenstelle geprüft, die nur die Gültigkeit der Kette prüft, vor dem Binden des Ports und mit den konfigurierten Versionen und Suiten; Diagnose `CERTIFICATE`, `PRIVATE_KEY` oder `PAIR` ohne Dateipfad und ohne Inhalt in Meldung und Log.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Smoke der Welle (Loopback, Docker): der Listener nimmt eine Verbindung an, im 7.4-Pfad läuft der Handshake in `0x12`-Paketen, im 8.0-Pfad ab dem ersten Byte; je eine Nachricht (byteweise zerstückelt) geht hin und zurück durch Listener, Schleife, Nachrichtenleser und -schreiber. Der Handler im Test ist ein Echo, kein Protokoll.
- [ ] Mehrere gleichzeitige Verbindungen (Zahl im Test genannt), davon einige mit defektem Handshake, abgeschnittener Nachricht und abruptem Trennen: der Prozess bleibt am Leben, die guten Verbindungen laufen weiter ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)).
- [ ] Startprobe: je ein Test für abgelaufenes Zertifikat, unlesbare Kette, verschlüsselten oder nicht zugelassenen Schlüssel und nicht passendes Paar; jeder meldet den richtigen Teil, und der Port ist danach nicht gebunden. Testdaten nach der Entscheidung zum Testzertifikat (§6).
- [ ] Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Sensors nur bei einem neuen Target; `contracts/security-profile.md`, falls die Form von Zertifikat und Schlüssel dort festgelegt wird).
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
| `src/main/kotlin/io/github/pt9912/tdswire/transport/` (Unterpackages, z. B. `listener`, `tls`) <!-- d-check:ignore (geplant: neue Datei) --> | neu | Listener, Verbindungs-Scope, Handler-Schnittstelle, Fristen, Startprobe und TLS-Material-Lader; keine Imports aus `port`, `simulation`, `runtime` (SPEC-041) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Loopback-Smoke, Parallelität mit defekten Verbindungen, Fristen mit steuerbarer Zeit, Startprobe je Teil; `@spec`-Tag je Test (SPEC-008, SPEC-043, SPEC-044) |
| Testzertifikate | neu | Herkunft offen (Frage an den Nutzer, §6) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf:

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/transport/listener/` | neu | `TdsListener` (Bind, Annahmeschleife, Beenden), `ManagedConnection` (Scope, `LOGIN_TIMEOUT`, Ende), `ConnectionRegistry` (Aufnahme gegen Beginn des Beendens gesperrt), `ConnectionHandler`/`Connection` (Handler-Schnittstelle), `ListenerConfig`/`ListenerTimes`/`Delayer` (Fristen über eine steuerbare Zeitquelle), `ListenerEvents` |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/tls/` (`TlsMaterial`, `TlsStartProbe`, `ProbeClient`) | neu | PKCS#12-Lader mit Diagnose, Startprobe im Speicher gegen eine Gegenstelle, die nur die Gültigkeit der Kette prüft |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/` und `src/test/kotlin/io/github/pt9912/tdswire/transport/tls/TlsStartProbeTest.kt` | neu | Smoke, parallele Verbindungen, Fristen, Beenden, Start, Startprobe je Teil |
| `src/test/kotlin/io/github/pt9912/tdswire/testsupport/KeyVariants.kt` | neu | Abwandlungen der Testzertifikate der Stage `testkeys` im Test (anderes Schlüsselkennwort, fremder Schlüssel, EC, RSA 1024): kein Eingriff in `Dockerfile` und `build.gradle.kts` |

Festlegungen des Laufs (nicht gemessen, für die Closure-Notiz): Handler-Schnittstelle mit zwei Meldungen (`loginCompleted`, `requestRunning`; eine unvollständig empfangene Nachricht meldet der Handler als „läuft“); eigene `AsynchronousChannelGroup` mit zwei Threads je Listener; Zeitquelle `Delayer` statt `kotlinx-coroutines-test`; die Startprobe nutzt den Pfad ab dem ersten Byte (die Kapselung prüft sie nicht, `SPEC-043`); RSA ab 2048 Bit als zugelassene Schlüsselform; `jdk.tls.acknowledgeCloseNotify` bleibt beim JDK-Standard, der Server antwortet nicht auf `close_notify` der Gegenstelle (Voreinstellung des Aufrufers, vom Nutzer nicht bestätigt); der Listener schließt beim Beenden und bei Fristablauf den TCP-Kanal ohne eigenes `close_notify`, weil er den TLS-Kanal des Handlers nicht kennt.

- Reihenfolge: Listener und Scope zuerst, dann Fristen und Schließen, zuletzt die Startprobe (sie braucht die Schleife und das Material).
- Die Handler-Schnittstelle nach ARC-003 („Protocol-Schnittstellen“) wird so klein wie möglich gehalten: Verbindung entgegennehmen, „Login abgeschlossen“ und „Anfrage läuft“ melden. Ihre endgültige Form legt die Protokollschicht fest; der Slice nennt sie in §7 als Festlegung.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-transport-tls` ist eingetreten, `slice-transport-tls-schleife` liegt in `done/` (erfüllt: die Schleife in beiden Pfaden steht auf `main`), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Startprobe samt Material-Lader sprengt den Rahmen. Schnittvorschlag: Slice C1 = Listener und Lebenszyklus, Slice C2 = Startprobe und TLS-Material; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Handler-Schnittstelle lässt sich nicht ohne eine Entscheidung zum Zustandsautomaten ([ADR-0004](../../adr/0004-session-zustandsautomat.md)) schneiden, oder das Format von Zertifikat und Schlüssel ist ungeklärt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Größe: drei Liefer-Punkte mit Listener, Fristen und Startprobe; die Startprobe hängt an der Schleife und am Material-Format und kann den Slice sprengen. Rückführung und Schnitt in §4. — **Ausgang:** offen bis zur Closure.
- Format von Zertifikat und Schlüssel (PKCS#12, PEM): SPEC-043 nennt die Teile, aber nicht die Formen; ein PEM-Lader ohne Fremdbibliothek ist Eigencode mit Sicherheitsrelevanz. Entschieden (Nutzer, 2026-10-30): PKCS#12 (JDK-Standard), kein PEM-Lader. — **Ausgang:** offen bis zur Closure.
- Die Startprobe prüft laut SPEC-043 weder Vertrauen noch die TDS-Kapselung; ein Test darf daraus nicht „der Client akzeptiert das Zertifikat“ ableiten. — **Ausgang:** offen bis zur Closure.
- Die Frist `LOGIN_TIMEOUT` ist laut Spezifikation vorläufig (15 s); Tests dürfen sie nur über eine steuerbare Zeitquelle prüfen, nicht über Wartezeit, sonst wird das Test-Gate langsam und flackert. — **Ausgang:** offen bis zur Closure.
- Ein geordnetes Schließen mit laufender Anfrage ist ohne Session nicht vollständig belegbar; der Slice belegt Fristen und Trennen mit einem Test-Handler, nicht das Verhalten gegen eine echte Anfrage. Die Lücke steht in der Notiz und im Folge-Slice der funktionalen Welle. — **Ausgang:** offen bis zur Closure.
- Gleichzeitige Verbindungen: der Spike maß 200 (einzelner Lauf); der Test hier nennt eine kleinere Zahl und behauptet keine Last (`slice-qa-performance`). — **Ausgang:** offen bis zur Closure.
- Zeitbegrenzung des TLS-Handshakes: die Schleife aus `slice-transport-tls-schleife` setzt keine Frist; eine Gegenstelle kann den Handshake mit einzelnen Bytes beliebig lange offenhalten. `LOGIN_TIMEOUT` läuft ab Verbindungsaufbau und umfasst deshalb den Handshake in beiden Pfaden; ein Test mit steuerbarer Zeit belegt, dass eine Verbindung im Handshake nach Ablauf der Frist getrennt wird. — **Ausgang:** offen bis zur Closure.
- Antwort auf `close_notify` der Gegenstelle: die JDK-Engine antwortet ohne `jdk.tls.acknowledgeCloseNotify` nicht von selbst, die Schleife auch nicht (Festlegung der TLS-Schleife, `TlsReader`). Ob der Server beim geordneten Schließen (`SPEC-044`) auf ein `close_notify` des Clients mit eigenem `close_notify` antwortet oder das Flag setzt, ist eine Nutzerentscheidung; der Slice legt sie vor und setzt sie um, oder führt die Antwort als Folge-Slice. — **Ausgang:** offen bis zur Closure.
- Schichtgrenzen: `transport` darf `protocol`-Schnittstellen nutzen (ARC-003), `make detekt` belegt das nur über `import`-Zeilen (SPEC-041); der Plan hält die Handler-Schnittstelle in `transport` und importiert nichts aus `protocol`. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `transport` ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage`. Betrifft diesen Slice als Implementierungs-Slice: jede Aussage trägt einen Test oder ist als Festlegung gekennzeichnet (§2, §6). Kein weiterer Folge-Slice; die Closure-Notiz hält fest, ob das Muster wieder auftrat.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 2×, offen. Betrifft jede Aussage über das Verhalten gegen einen echten SQL Server oder den Referenzclient: der Slice macht keine (§1, §6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 2×, offen. Betrifft den TDS-8.0-Pfad: dort gilt MS-TDS- und ADR-Lesart, nicht Messung am Server dieses Repos.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Betrifft die Startprobe und das Fehlen von Pfad und Inhalt in Diagnosen ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); Zeitgleichheit und Prüfwertformat bleiben bei `slice-qa-sicherheit`.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft dieses Dokument und die Closure-Notiz: `ADR-*` und `TDS-*` stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`; `adapter-festlegungen-ohne-slice` berührt den Listener nicht.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
