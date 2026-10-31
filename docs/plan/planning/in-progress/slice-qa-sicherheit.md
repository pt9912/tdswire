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
- Loginspektion über das Gesamtsystem (Sammler, Korrelations-IDs, Ereignisse) — Träger [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md); dieser Slice prüft das minimale Log von heute, das Gesamtsystem-Gate gibt es erst, wenn der Sammler existiert.
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
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`spec/spezifikation.md` §Festlegungen der TLS-Schleife zur Neuverhandlung; KDoc von `UserList`, `UserListAuthAdapter`, `AuthExecutor` mit den Messwerten).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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
| `docs/plan/carveouts/CO-003-anmeldedrosselung-fehlt.md`, `docs/plan/planning/open/slice-qa-anmeldedrosselung.md` | neu | Drosselung: die Wahl der Art trifft der Nutzer, nicht der Implementer; deshalb Carveout mit Folge-Slice |

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
- Verwaiste und wartende Adapteraufrufe des Anmeldepfads ([slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md), Review F-1, Nachverifikation N-1 und N-2): `AuthPool` begrenzt die gleichzeitigen Aufrufe je Pool (Voreinstellung 4 Plätze auf `Dispatchers.IO`), eine Last-Aussage und eine Drosselung wiederholter Anmeldeversuche fehlen, und das Passwort eines bei Fristablauf wartenden Aufrufs ist erst überschrieben, wenn die Warteschlange ihn erreicht. Gemessen ist nur die Begrenzung im Unit-Test mit Test-Adapter; die Wirkung auf den Transport unter Last und die Dauer des Zeitfensters sind es nicht. Träger war der zweite Liefer-Punkt (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`). — **Ausgang:** eingetreten: Platzbelegung und Zeitfenster sind in `AuthPoolOccupationTest` gemessen (Test-Adapter, nicht die Wirkung auf den Transport unter Last); die Drosselung ist als [CO-003](../../carveouts/CO-003-anmeldedrosselung-fehlt.md) geführt, Folge-Slice [slice-qa-anmeldedrosselung](../open/slice-qa-anmeldedrosselung.md).
- Die Loginspektion über das Gesamtsystem hat durch die Planung der Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) bisher bei diesem Slice gelegen, obwohl ihr Sammler dort entsteht. — **Ausgang:** weiter offen: Träger ist jetzt [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md) (DoD „Ablösung und Redaktion“); dieser Slice prüft nur das minimale Log von heute (Nutzerfrage im Bericht der Fix-Runde).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` (Kürzel `TDS`, Greenfield); der Slice berührt das ganze Repo, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (3×) und `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (3×) sind die Treffer und tragen diesen Slice als Träger; die Belege dieses Slice stehen dort in `evidence/`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
