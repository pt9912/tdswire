# Slice slice-qa-sicherheit: Sicherheit: Zugangsdaten im Log und TLS-Modus

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-010 · SPEC-012 · SPEC-023 · SPEC-028 · SPEC-043

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Zugangsdaten stehen nie im Klartext im Log, produktiv ist ein freigegebener TLS-Modus aktiv, und unsichere Testmodi werden nicht unbemerkt produktiv.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Enumerationsfestigkeit als Abnahmekriterium — [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) verlangt sie nicht; `SPEC-012` sichert nur die gleiche Ablehnung zu. Die Zeitgleichheit des Passwortvergleichs und die Drosselung wiederholter Anmeldeversuche aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen sind dagegen Gegenstand dieses Slice (Test bzw. Entscheidung, nicht Abnahmekriterium); das Prüfwertformat der Benutzerliste ist in [ADR-0011](../../adr/0011-pruefwertformat.md) (`Accepted`) entschieden und gehört nicht mehr dazu.
- Loginspektion über das Gesamtsystem (Sammler, Korrelations-IDs, Ereignisse) — Träger [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md); dieser Slice prüft das minimale Log von heute, das Gesamtsystem-Gate gibt es erst, wenn der Sammler existiert.
- Der Authentifizierungsmodus selbst — [ADR-0009](../../adr/0009-authentifizierungsmodus.md) wählt SQL-Authentifizierung über einen Adapter (Status `Proposed`, wird `Accepted`); dieser Slice entscheidet ihn nicht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Loginspektion: kein Geheimnis im Log (Test), einschließlich der Textdarstellungen der Typen des Anmeldepfads (`PendingLogin`, `AuthenticatedSession`, Ereignisse des Handshakes), die `slice-handshake-login` per `toString` verbirgt, ohne dass ein Test sie pinnt.
- [x] Zeitgleichheit des Passwortvergleichs (Test) und Drosselung ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen) entschieden oder als Carveout geführt; die Zeitgleichheit von unbekanntem Benutzernamen und falschem Passwort, die Drosselung und der Scheinvergleich bei unbekanntem Benutzernamen sind laut [ADR-0011](../../adr/0011-pruefwertformat.md) weder entschieden noch gemessen. Das Prüfwertformat ist dort entschieden. Der Auth-Slice liefert die Bauart des Scheinvergleichs (Attrappe ist der `pbkdf2`-Eintrag mit der höchsten Iterationszahl) mit Strukturtests; bei gemischten Listen (niedrigere Iterationszahl oder `plain` neben `pbkdf2-sha256`) durchläuft ein bekannter Benutzer einen anderen Rechenschritt als die Attrappe (`spec/spezifikation.md` `SPEC-051`), und die Messung der Zeit schließt diesen Fall ein oder nennt ihn als Grenze. Dazu gehört die Belegung der Plätze des `AuthPool` aus [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md): Ein nicht angemeldeter Client kann mit mehreren Verbindungen alle Plätze für die Dauer einer Ableitung belegen, regulär anmeldende Clients warten dahinter; die Zahl der Plätze (Voreinstellung 4, Festlegung ohne Messung) und das Zeitfenster, in dem das Passwort eines wartenden, abgebrochenen Aufrufs im Speicher liegt, sind ungemessen. Der Slice misst beides oder nennt es als Grenze.
- [x] TLS-Handshake- und Konfigurationsnegativtests nach `SPEC-043` und `SPEC-010`.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
      Der Haken gilt für den Stand 5d95bd2; die Fix-Runde 84a6b78 und die Mini-Runde f8f2c35 haben kein eigenes Review (§7, „Reports“).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`spec/spezifikation.md` §Festlegungen der TLS-Schleife zur Neuverhandlung; KDoc von `UserList`, `UserListAuthAdapter`, `AuthExecutor` mit den Messwerten).
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
| `protocol/session/LoginPathTextTest` | neu | Textdarstellungen des Anmeldepfads (`PendingLogin`, `AuthenticatedSession`, Ereignisse, Aktionen, Zulassung), mit Gegenprobe des Prüfers, nach `SPEC-012` |
| `runtime/LoginLogInspectionTest` | neu | Loginspektion am Serverprozess über Socket: Annahme, falsches Passwort, unbekannter Benutzername, zu lange Eingabe, Adapterfehler mit Geheimnis im Ausnahmetext, nach `SPEC-012` und `SPEC-048` |
| `config/auth/AuthTimingTest` | neu | Zeitgleichheit mit echter Ableitung: gleiche Iterationszahl, Gegenprobe ohne Scheinvergleich, Grenze gemischter Listen, nach `SPEC-051` |
| `protocol/session/AuthPoolOccupationTest` | neu | Belegung der Plätze und Zeitfenster des Passworts eines abgebrochenen, wartenden Aufrufs, nach `SPEC-008` |
| `runtime/TlsHandshakeNegativeTest` | neu | Handschlag-Negativfälle am Prozess mit `REQUIRED` (Klartext-LOGIN7, TLS 1.1, Nicht-TLS, Abbruch, fehlendes Vertrauen), nach `SPEC-010`, `SPEC-023` |
| `runtime/SecurityConfigNegativeTest` | neu | Konfigurationsnegativtests der Richtlinie, des Speichers und des Testmodus für `plain`, nach `SPEC-010`, `SPEC-043` |
| `transport/tls/TlsRenegotiationTest` | neu | Neuverhandlung in TLS 1.2 durch die Gegenstelle, nach `SPEC-023` |
| `testsupport/TlsTestSupport.kt` | update | `JdkTlsPeer.read` führt Aufgaben und Schreibschritte einer Neuverhandlung aus (die Gegenstelle drehte sich vorher in der Entschlüsselung im Kreis) |
| `docs/plan/carveouts/done/CO-003-anmeldedrosselung-fehlt.md`, `docs/plan/planning/done/slice-qa-anmeldedrosselung.md` | neu | Drosselung: die Wahl der Art trifft der Nutzer, nicht der Implementer; deshalb Carveout mit Folge-Slice |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) und [ADR-0009](../../adr/0009-authentifizierungsmodus.md) Accepted.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der TLS-Modus des Clientprofils ist offen.

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

- Die Wahl in [ADR-0009](../../adr/0009-authentifizierungsmodus.md) ist `Proposed` und kann sich bei der Verifikation ändern; Tests der Anmeldung bleiben bis zum Statuswechsel vorläufig. — **Ausgang:** entfallen: [ADR-0009](../../adr/0009-authentifizierungsmodus.md) steht auf `Accepted` (geprüft beim Start dieses Slice); die Tests der Anmeldung hängen an dem Stand.
- Neuverhandlung in TLS 1.2 ist von der TLS-Schleife (`slice-transport-tls-schleife`) weder behandelt noch getestet (`spec/spezifikation.md` `SPEC-023` §Festlegungen der TLS-Schleife). Der TLS-Handshake-Test dieses Slice stellt fest, wie sich der Server bei einer Neuverhandlung durch die Gegenstelle verhält (Ergebniswert, nie Klartext), oder benennt die Aussage als nicht gemessen. — **Ausgang:** eingetreten: `TlsRenegotiationTest` stellt fest, dass die Schleife eine Neuverhandlung in TLS 1.2 beantwortet und nie Klartext schreibt; eine Begrenzung gibt es nicht, die Rechenlast ist nicht gemessen (weiter offen als `BEO-TDS/tls-neuverhandlung-ohne-begrenzung`).
- Verwaiste und wartende Adapteraufrufe des Anmeldepfads ([slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md), Review F-1, Nachverifikation N-1 und N-2): `AuthPool` begrenzt die gleichzeitigen Aufrufe je Pool (Voreinstellung 4 Plätze auf `Dispatchers.IO`), eine Last-Aussage und eine Drosselung wiederholter Anmeldeversuche fehlen, und das Passwort eines bei Fristablauf wartenden Aufrufs ist erst überschrieben, wenn die Warteschlange ihn erreicht. Gemessen ist nur die Begrenzung im Unit-Test mit Test-Adapter; die Wirkung auf den Transport unter Last und die Dauer des Zeitfensters sind es nicht. Träger war der zweite Liefer-Punkt (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`). — **Ausgang:** eingetreten: Platzbelegung und Zeitfenster sind in `AuthPoolOccupationTest` gemessen (Test-Adapter, nicht die Wirkung auf den Transport unter Last); die Drosselung ist als [CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) geführt, Folge-Slice [slice-qa-anmeldedrosselung](../done/slice-qa-anmeldedrosselung.md).
- Die Loginspektion über das Gesamtsystem hat durch die Planung der Welle [welle-betrieb-diagnose](welle-betrieb-diagnose.md) bisher bei diesem Slice gelegen, obwohl ihr Sammler dort entsteht. — **Ausgang:** weiter offen: Träger ist jetzt [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) (DoD „Ablösung und Redaktion“); dieser Slice prüft nur das minimale Log von heute (Träger vom Nutzer bestätigt 2026-10-31).

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

- **Was hat funktioniert:** Die vier Liefer-Punkte (Loginspektion, Zeitgleichheit mit Platzbelegung und Drosselung, TLS- und Konfigurationsnegativtests, Doku-Update) liegen als sieben neue Testklassen und KDoc-Nachzug vor; am Produktcode ändert sich außer KDoc nichts. Die Mutationen der Verifikation röteten M1 (Scheinvergleich entfernt: `AuthTimingTest`), M2 (`pool.limited` durch `pool.dispatcher`: `AuthPoolOccupationTest`), M3 (Klartext-Herabstufung bei `REQUIRED`: `TlsHandshakeNegativeTest`), M4 (Testmodus nicht nur bei genau `true`: `SecurityConfigNegativeTest`), M5 (`PendingLogin.toString` nennt die Datenbank: `LoginPathTextTest`) und M6c (`NEED_TASK` ohne Aufgaben: `TlsRenegotiationTest`).
- **Was ging anders als geplant:** (a) Der Review (0 HIGH, 3 MEDIUM, 5 LOW, 4 INFO) fand die Gesamtsystem-Loginspektion ohne Träger (F-1), zwei KDoc-Aussagen gegen die nachgezogene Spezifikation oder gegen die Messung (F-2, F-3) und mehrere Aussagen breiter als der Beleg (F-5 bis F-7); die Fix-Runde 84a6b78 behob F-1 bis F-8 (F-4 nur teilweise). (b) Die Verifikation (0 HIGH, 1 MEDIUM, 4 LOW, 1 INFO) fand M-1 (die Fix-Runde setzte das KDoc auf „Zeit gemessen“, drei Stellen der Spezifikation sagten „nicht gemessen“) und L-1 (`TlsRenegotiationTest` färbt M6a und M6b nicht). (c) Die Mini-Runde f8f2c35 zog M-1, L-1 (Leseseite), L-3 und L-4 nach. (d) Dass die Fix-Runde das KDoc änderte, aber die Spezifikation nicht, ist die Wiederkehr von `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`.
- **Reports:** [Review F-1 bis F-12](../../../reviews/2026-10-31-5d95bd2-slice-qa-sicherheit.md), [Verifikation M-1, L-1 bis L-4, I-1](../../../reviews/2026-10-31-84a6b78-verifikation-slice-qa-sicherheit.md). **Wer was gesehen hat:** (1) Den Stand 5d95bd2 (Umsetzung 1f17d0d und a735892) hat der Reviewer gesehen. (2) Die Fix-Runde 84a6b78 hat **kein eigenes Review**; sie hat der Verifier gesehen (Mutationen, Streuung, Lesen von Spezifikation und KDoc) und der Implementer. (3) Die Mini-Runde f8f2c35 (Spezifikation, `TlsRenegotiationTest`, `TlsTestSupport.kt`, Verweise) hat **kein eigenes Review und keine eigene Verifikation**; sie haben nur der Implementer und der Planner gesehen, und die Durchsicht des Planners ist das Lesen von `git show f8f2c35` und ein Lauf der Tests, kein zweiter Kontext. (4) Diese Closure-Notiz hat nur der Planner gesehen.
- **Durchsicht der Mini-Runde (Planner):** *M-1 ist behoben.* Die Spezifikation führt die Zeitgleichheit an den drei Stellen der Verifikation (Anmeldeablauf Punkt 2, „Pflichten des Authentifizierungsadapters“, „Bauart des Vergleichs“) als gebaut, strukturell belegt und gemessen, mit Messbedingung (Test mit echter Ableitung, Median von 15 Aufrufen, Docker-Image des Repos, 42 bis 64 ms je Aufruf bei 210 000 Iterationen auf zwei Hosts, Toleranz 25 %), und nennt die gemischte Liste als Grenze; die Suche nach „Zeitgleichheit“ in `spec/` und `src/main` findet nur noch diesen Sinn, und der Absatz „Gemessen am Authentifizierungsadapter“ führt sie nicht mehr unter „Nicht gemessen“. Die angenommenen [ADR-0009](../../adr/0009-authentifizierungsmodus.md) und [ADR-0011](../../adr/0011-pruefwertformat.md) sind nach `AGENTS.md` §3.5 nicht angefasst. *Ob der geschärfte `TlsRenegotiationTest` M6a und M6b färbt, ist nicht gesehen:* der Commit f8f2c35 trägt keinen Beleg dafür, und ich habe die Mutationen nicht gebaut. Gelesen habe ich nur, dass der Server jetzt in einem eigenen Lesevorgang wartet, während die Gegenstelle die Neuverhandlung zu Ende führt (`awaitHandshakeDone`), und in dieser Zeit nichts schreibt; das ist der Mechanismus, der die Antwort der Leseseite erzwingen soll, und sein Beleg ist eine Mutation, die aussteht. Die Aussage der Spezifikation „beantwortet die Schleife“ ist bis dahin belegt für Nutzdaten in beide Richtungen und für `NEED_TASK` (M6c), nicht für den Schreibschritt der Leseseite. Das Register hat die zurückgenommene Angabe „Mutation machte den Test rot“ berichtigt (`BEO-TDS/tls-neuverhandlung-ohne-begrenzung`).
- **Frischer Lauf der Closure:** `docker build --no-cache-filter test --target test .` auf dem Stand e99d24a (`git merge main` meldete „Bereits aktuell“), einmal: Exit 0, `BUILD SUCCESSFUL in 1m 48s`, 738 Tests `PASSED`, keine rote und keine übersprungene Testzeile (die Treffer der Suche nach „FAILED“ und „SKIPPED“ sind der Testname `FAILED_CLOSE_CONNECTION` und die Aufgabe `checkKotlinGradlePluginConfigurationErrors`); die Stage `test` lief ohne Cache, darin `TlsRenegotiationTest` (2 Tests) und `AuthTimingTest` (3) PASSED. `make spec-abdeckung`: Matrix unverändert, „14 Festlegung(en) ohne Nachweis“ wie vor diesem Slice, keine neue Waise. `make gates` vor jedem Commit der Closure grün; das Ergebnis nach dem Move steht im Bericht der Closure, nicht in dieser Zeile.

**Messbedingungen der Zeitmess-Tests** (`AuthTimingTest`, `AuthPoolOccupationTest`): Docker-Stage `test` des Repos, Ableitung mit echtem PBKDF2 bei 210 000 Iterationen, Median von 15 Aufrufen im Wechsel nach drei Aufwärmrunden. Streuung (Verifikation, drei Läufe auf einem Host, Last: Umgebung, +20, +60 Rechenprozesse): Dauer je Aufruf 42,5 bis 43,2 ms, 42,6 bis 43,2 ms und 63,6 bis 63,9 ms; der **Abstand** der drei Fälle (bekannt/falsch, unbekannt, bekannt/richtig) 1,7 %, 1,4 % und 0,5 % gegen die Schranke von 25 %. Die Last verschiebt den **Betrag** um rund 50 % (42 bis 64 ms, hostgebunden; die Zahl „57 ms“ im Register und im KDoc ist ein Lauf des Reviews), das **Verhältnis** bleibt; die Aussage der Tests ist das Verhältnis, nicht der Betrag. Gegenproben: unbekannt ohne Scheinvergleich 0,0016 bis 0,0023 ms; gemischte Liste Attrappe 84 bis 128 ms, halbe Iterationszahl 42 bis 64 ms, `plain` 0,04 ms. Platzbelegung: 300, 550 und 850 ms gegen die Sollwerte, Streuung unter 1 ms (durch die Haltezeit bestimmt, nicht durch die Last); Fenster des Passworts nach der Freigabe 0,30 bis 2,33 ms gegen die Grenze von 2000 ms. **Drei Läufe, ein Host:** das ist eine Stichprobe, keine Aussage über einen CI-Runner mit einem Kern oder einem Nachbarn mit gleicher Zeitscheibe; die Schranken (25 %, Faktor 10, 1,5, 2000 ms) bleiben Setzungen.

**Zuordnung je Aussage.** *gemessen* heißt ein bestandener Lauf am Code dieses Slice mit handgebauten Eingaben und Attrappen, **nicht** an einem echten SQL Server und nicht am Referenzclient; *Handvektor* ein Fall mit Rechnung von Hand; *Selbstkonsistenz* Code gegen Code aus demselben Slice; *Festlegung* eine Entscheidung ohne Messung.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen (Eigencode, Serverprozess über Socket) | Kein Geheimnis im Log bei fünf Ausgängen der Anmeldung (Annahme, falsches Passwort, unbekannter Benutzername, zu lange Eingabe, Adapterfehler mit Geheimnis im Ausnahmetext); keine Textdarstellung des Anmeldepfads nennt einen Wert. Reichweite: das minimale Log von heute | `LoginLogInspectionTest`, `LoginPathTextTest`; M5 rot |
| gemessen (Eigencode, echte Ableitung) | Bei gleicher Iterationszahl brauchen bekannter Benutzername mit falschem Passwort, unbekannter und bekannter mit richtigem Passwort innerhalb von 25 % gleich lang; bei gemischter Liste trennt die Zeit bekannte von unbekannten Benutzernamen (Grenze, keine Zusage) | `AuthTimingTest` (3 Tests); M1 rot (zwei Tests); Messbedingungen oben; `SPEC-051` |
| gemessen (Eigencode, Test-Adapter) | Mit 4 Plätzen warten reguläre Aufrufe hinter belegenden; das Passwort eines wartenden Aufrufs ist nach 300 ms lesbar und nach der Freigabe überschrieben; die Dauer darüber ist nicht gemessen | `AuthPoolOccupationTest` (2 Tests); M2 rot |
| gemessen (Eigencode, Prozess) | Bei `REQUIRED` weist der Server Klartext-LOGIN7, TLS 1.1, Nicht-TLS, Abbruch und fehlendes Vertrauen ab, ohne Antwortbytes, ohne Marke im Log, bleibt am Leben und gibt den Port frei; Richtlinie ohne Rückfall, unlesbarer Speicher, Testmodus nur bei genau `true` | `TlsHandshakeNegativeTest` (5), `SecurityConfigNegativeTest` (3); M3 und M4 rot |
| gemessen (JDK-Gegenstelle, wenige Runden) | In TLS 1.2 laufen Nutzdaten nach einer Neuverhandlung der Gegenstelle in beide Richtungen unversehrt, die Marke des Servers steht nicht im Klartext im Ausgang des Servers; die Aufgaben der Neuverhandlung (`NEED_TASK`) führt die Leseseite aus | `TlsRenegotiationTest` (2 Tests, mehrere Runden); M6c rot |
| **nicht gesehen** | Dass die Leseseite der Schleife den Schreibschritt der Neuverhandlung ausführt und der Test den Wegfall bemerkt (M6a, M6b): bis zur Mini-Runde f8f2c35 nicht gefangen (Verifikation L-1); mit der Mini-Runde laut Absicht des Implementers behoben, ein Lauf mit M6a und M6b liegt nicht vor | Verifikation L-1; Register `BEO-TDS/tls-neuverhandlung-ohne-begrenzung` |
| Handvektor | Sollwerte der Wartezeit 300, 550 und 850 ms aus Haltezeit und Anlaufversatz von 50 ms (nominell 600 und 900 ms) | KDoc von `AuthExecutor` (F-7); in drei Läufen der Verifikation reproduziert |
| Selbstkonsistenz | Die Gegenproben des Prüfers (leere Liste, Marke im Draht) prüfen die Hilfsfunktion `contains`, nicht das Produkt; die Gegenprobe „ohne Scheinvergleich“ läuft an einem Wrapper im Test, die Produkt-Mutation ist M1 | `LoginPathTextTest`, `TlsRenegotiationTest`, `AuthTimingTest` |
| Festlegung | Die Schranken der Zeit- und Pooltests (25 %, Faktor 10, 1,5, 150 ms je Welle, 2000 ms, Wächter 12 s); die Attrappe als `pbkdf2`-Eintrag mit der höchsten Iterationszahl ([ADR-0011](../../adr/0011-pruefwertformat.md)); die Wahl der SQL-Authentifizierung über einen Adapter ([ADR-0009](../../adr/0009-authentifizierungsmodus.md)) | KDoc der Tests; `SPEC-051` |
| Festlegung | Die Neuverhandlung in TLS 1.2 wird beantwortet, eine Begrenzung gibt es nicht | `spec/spezifikation.md` `SPEC-023` §Festlegungen der TLS-Schleife |
| nicht gemessen | Rechenlast durch viele Neuverhandlungen; die Loginspektion über das Gesamtsystem (Sammler, Korrelations-IDs, Ereignisse); die Wirkung des Anmeldepfads auf den Transport unter Last und Kopien des Passworts im Speicher von JVM und Adapter; die Dauer des Zeitfensters über 300 ms; die Zeitgleichheit bei gemischten Listen (gemessen ist ihre Ungleichheit); ein CI-Runner mit einem Kern; Dateirechte und Übergabe der Benutzerliste im Betrieb; die Drosselung (nicht entschieden) | Plan §1 und §6; Register |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Risiken aus §6:** alle vier tragen genau einen Ausgang (siehe §6): eins entfallen ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) steht auf `Accepted`), zwei eingetreten (Neuverhandlung gemessen, mit weiter offener Rechenlast im Register; Platzbelegung und Zeitfenster gemessen, Drosselung als [CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) mit Folge-Slice), eins weiter offen (die Loginspektion über das Gesamtsystem, Träger [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md); der Träger ist vom Nutzer bestätigt, 2026-10-31).
- **Steering-Loop-Eintrag:** (1) **Lerneintrag, geschärfte Regel, nicht verkörpert:** Eine Aussage der Art „die Schleife beantwortet“ trägt erst, wenn die Mutation, die die Antwort entfernt, den Test rötet; ein Register-Eintrag „Mutation machte den Test rot“ ohne abgelegten Beleg trägt nicht (Review F-10, Verifikation L-1: M6a und M6b blieben grün). Das ist die Wiederkehr von `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (jetzt 10×, über der Schwelle, **nicht verkörpert; Entscheidung beim Nutzer**: Regel, Sensor, Träger-Slice oder Streichung) und von `BEO-TDS/aussagen-ueber-messung-hinaus` (37×, verkörpert in `AGENTS.md` §3.8; die Wiederkehr F-3, F-6, F-7, M-1 trat nach der Verkörperung auf und ist behoben). Ein Anker mit `liegt in` steht hier nicht, weil mit diesem Slice keine Regel mit Herkunfts-Anker verkörpert wurde. (2) **Lerneintrag, nicht verkörpert:** Hebt eine Fix-Runde die Aussage im KDoc von „nicht gemessen“ auf „gemessen“, sucht sie die Kernaussage über `spec/`, `src/main` und `docs/plan/planning/` in derselben Runde (M-1, F-2, F-5, L-3); das ist `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, mit diesem Slice bei 2×, unter der Schwelle, offen. (3) **Benannte Spec-Lücke**, ohne `liegt in`, weil sie in einer versionierten Spec steht: `spec/spezifikation.md` `SPEC-051` führt die Zeitgleichheit bei gemischten Listen als Grenze, keine Zusage; ob ein Betreiber gemischte Listen unterbinden, die Attrappe je Iterationszahl stufen oder die Grenze tragen soll, ist nicht entschieden, und [ADR-0011](../../adr/0011-pruefwertformat.md) trägt die Wahl der Attrappe, nicht diese Folge. Zusätzlich gezählt und nicht verkörpert, ohne Neuanlage: (4) `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` steht bei 6× (Zeitgleichheit, Scheinvergleich und Plätze gemessen; der Rest liegt bei [slice-qa-anmeldedrosselung](../done/slice-qa-anmeldedrosselung.md), [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) und [slice-release-artefakt-lizenz](../done/slice-release-artefakt-lizenz.md)); (5) `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` steht bei 4×, über der Schwelle, **nicht verkörpert; Entscheidung beim Nutzer**; (6) `BEO-TDS/tls-neuverhandlung-ohne-begrenzung` ist neu, 1×, **weiter offen** (offen ist, ob der Server die Neuverhandlung ablehnen oder begrenzen soll; die Rechenlast ist ungemessen).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-qa-sicherheit.md` liegt in [BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut](../observations/BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut/observation.md) (Zähler 6), [BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen](../observations/BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen/observation.md) (Zähler 4), [BEO-TDS/tls-neuverhandlung-ohne-begrenzung](../observations/BEO-TDS/tls-neuverhandlung-ohne-begrenzung/observation.md) (Zähler 1; die Angabe zur Mutation berichtigt), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-3, F-6, F-7, M-1; Zähler 37), [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) (F-2, F-5, M-1, L-3; Zähler 2) und [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md) (L-1; Zähler 10). Der Stand (`state.md`) ist für `zeile-oder-zweig-ohne-unterscheidenden-test`, `fortschreibung-laesst-zweite-fundstelle-stehen`, `anmeldepfad-sicherheit-nicht-gebaut` und `tls-neuverhandlung-ohne-begrenzung` nachgezogen. Keine neue Beobachtung in dieser Closure angelegt.
- **Folge-Slices:** keine neuen. [slice-qa-anmeldedrosselung](../done/slice-qa-anmeldedrosselung.md) **bleibt** der Folge-Slice von [CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) (Status `Aktiv`; Auflösungs-Trigger: Folge-Slice in `done/` und eine `Accepted`-ADR zur Drosselung oder zum Verzicht); die Welle [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) schließt erst mit ihm. Die Loginspektion über das Gesamtsystem trägt [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md).
- **Drei Paarungen:** Anker: der Eintrag trägt kein `liegt in`, die Paarung entfällt. Folge-Slice: [slice-qa-anmeldedrosselung](../done/slice-qa-anmeldedrosselung.md) und [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) existieren als Datei in `open/`, [slice-release-artefakt-lizenz](../done/slice-release-artefakt-lizenz.md) in `done/`. Register: jede genannte Kennung ist ein Verzeichnis mit nicht leerem `evidence/`.
- **Startbarkeit des Nachfolgers:** Der Start-Trigger von [slice-qa-anmeldedrosselung](../done/slice-qa-anmeldedrosselung.md) ist „slice-qa-sicherheit in `done/`“ und mit diesem Move erfüllt; **offen bleibt allein der Verantwortliche** (der Kopf trägt „— bis zur Priorisierung.“). Der Slice ist startbar, sobald ein Verantwortlicher eingetragen und der Slice nach `next/` und `in-progress/` bewegt ist; die Art der Drosselung entscheidet der Nutzer, nicht der Implementer. Die Welle [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md) ist danach nicht komplett: außer ihm liegen weitere ihrer Slices nicht in `done/`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` (Kürzel `TDS`, Greenfield); der Slice berührt das ganze Repo, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (3×) und `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (3×) sind die Treffer und tragen diesen Slice als Träger; die Belege dieses Slice stehen dort in `evidence/`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
