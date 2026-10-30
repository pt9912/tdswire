# Slice slice-auth-adapter-benutzerliste: Auth-Adapter-Schnittstelle und feste Benutzerliste

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-handshake-login

**Bezug:** [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-012 · SPEC-048 · SPEC-051 · ARC-002 · [TDS-FA-02.a](../../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung) · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

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

**Ziel:** Eine Kotlin-Schnittstelle für den Authentifizierungsadapter nach `contracts/auth-adapter.md` und die Einstiegsimplementierung mit fester Benutzerliste (Kennung, Prüfwert) liefern Annahme (mit Principal) oder Ablehnung (mit einem Grund nur für das Log), vergleichen in konstanter Zeit über die Länge hinaus, führen bei unbekannter Kennung einen Scheinvergleich aus und weisen eine fehlerhafte Liste beim Validieren ab; ohne Netzwerk und ohne LOGIN7-Kenntnis.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Entschleierung des LOGIN7-Passworts, Erkennung nicht unterstützter Modi, die feste Ablehnungsantwort (ERROR 18456) und der Umgang mit einer Ausnahme im Adapter — das ist der Anmeldepfad (`slice-handshake-login`); der Adapter entscheidet nach dem Vertrag allein über Annahme oder Ablehnung und liefert weder Text noch Nummer für den Client.
- Laden der Liste aus Datei oder Umgebung — `slice-bootstrap-konfiguration`; dieser Slice validiert eine Liste, die als Wert im Speicher vorliegt.
- Messung der Zeitgleichheit von unbekannter Kennung und falschem Passwort und Drosselung wiederholter Versuche — `slice-qa-sicherheit` ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen). Hier entsteht die Bauart mit einem Strukturtest, keine Zeitaussage.
- Adapter-Varianten außer der festen Benutzerliste (eigene Benutzerverwaltung eines Betreibers, Backend-Weiterreichung): Die Betreiberanforderung ist nicht abnahmebindend (`BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`); die Schnittstelle bleibt dafür offen, gebaut wird sie nicht.
- Passwortwechsel (`fChangePassword`): kein Teil des Adaptervertrags der ersten Version, der Anmeldepfad lehnt ihn ab.
- Die Wahl von Prüfwertformat und Konfigurationsweg: Sie ist Voraussetzung (§4), nicht Inhalt; sie steht in [ADR-0011](../../adr/0011-pruefwertformat.md). Dort hält die Adapter-Seite den Prüfwert als Wert (Verfahren, Parameter, Salz, Hash), parst und validiert ihn, vergleicht und kennt den Testmodus als Eingabewert (Planannahme der ADR, nicht gemessen); die Konfigurationsseite besitzt das Modell und liest die Datei.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Schnittstelle** im Package `config` (ARC-002, SPEC-051; Wertetypen, die weitere Schichten brauchen, im Package `domain`): Kotlin-Signatur des Adapters mit der Eingabe nach `contracts/auth-adapter.md` (Kennung, Passwort als überschreibbares Zeichenfeld, Verbindungskontext, Authentifizierungsmerkmale; der Verschlüsselungsmodus als Eingabe ist dort ein ungemessener Entwurf und bleibt als Festlegung gekennzeichnet) und der Ausgabe `Accepted(principal)` oder `Rejected(reason)`. Ein Test belegt, dass keine automatisch erzeugte Textdarstellung (`toString`) von Eingabe, Ausgabe oder Benutzerliste Passwort, Kennung oder Prüfwert enthält ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). `contracts/auth-adapter.md` führt die festgelegte Signatur; der Hinweis, sie sei noch festzulegen, wird nachgezogen.
- [ ] **Einstiegsimplementierung** (feste Benutzerliste, Prüfwertformat nach der Entscheidung aus §4): bekannte Kennung mit richtigem Passwort ergibt `Accepted`, falsches Passwort und unbekannte Kennung ergeben `Rejected` mit einem Grund ohne Passwort und ohne Kennung; der Vergleich läuft in konstanter Zeit über die Länge hinaus, bei unbekannter Kennung läuft ein Scheinvergleich. Belegt als Strukturtest (der Scheinvergleich wird tatsächlich ausgeführt, der Vergleich bricht nicht beim ersten Unterschied ab); eine Zeitaussage entsteht daraus nicht. Randfälle der Eingabe: leere Kennung und leeres Passwort (der Client öffnet damit, [ADR-0009](../../adr/0009-authentifizierungsmodus.md)), 128 UTF-16-Einheiten, Surrogatpaar, Eingabe über der Grenze (Ablehnung, keine Ausnahme).
- [ ] **Validierung der Liste beim Start** ([TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)): doppelte Kennung, leere Kennung, ungültiger Prüfwert und leere Liste ergeben einen Fehlerwert; die Diagnose nennt die Art des Fehlers und den Listenplatz, nie Passwort oder Prüfwert. Welche dieser Fälle den Start abbrechen, ist als Festlegung im KDoc gekennzeichnet (das Lastenheft verlangt, dass eine sicherheitsrelevante Fehlkonfiguration abgewiesen wird, [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). Aus den offenen Punkten von [ADR-0011](../../adr/0011-pruefwertformat.md) trägt dieser Slice fünf, jeweils vom Nutzer entschieden oder gemessen und als Festlegung gekennzeichnet, sonst in der Closure als weiter offen mit Grund geführt: ob im Testmodus ein niedrigerer Iterationsboden gilt, ob Kommentare und Leerzeilen im Zeilenformat zulässig sind, wie Zeilenenden (insbesondere CRLF) behandelt werden, welche Hash- und Mindest-Salzlänge gelten und wie lang die Ziffernfolge der Iterationen sein darf; die Dateirechte und die Übergabe der Datei im Betrieb trägt `slice-bootstrap-konfiguration`.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage der Implementierung steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements); die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (Register, `BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8). Insbesondere: Das Überschreiben des Passwortfelds schützt nur dieses Feld, nicht Paket-, Lese- und TLS-Puffer ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Geheimnisschutz); kein KDoc behauptet mehr.
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
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (Unterpackage, z. B. `auth`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Adapter-Schnittstelle, Einstiegsimplementierung, Validierung; importiert nur `domain` (ARC-002, SPEC-041) |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/` | update | Principal und weitere Wertetypen, soweit mehr als eine Schicht sie braucht; Standardbibliothek only |
| `contracts/auth-adapter.md` | update | Kotlin-Signatur, Prüfwertformat und Konfigurationsweg der Liste, sobald entschieden; Entwurfs-Hinweise bleiben, wo nichts gemessen ist |
| `src/test/kotlin/io/github/pt9912/tdswire/config/` <!-- d-check:ignore (geplant: neues Package) --> | neu | Happy/Boundary/Negative nach SPEC-012, SPEC-051 und [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose); Textdarstellungs-Test nach [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit); `@spec`-Tag je Test |
| `gradle/libs.versions.toml`, `gradle.lockfile`, `gradle/verification-metadata.xml` | update, nur falls das Prüfwertformat eine Abhängigkeit braucht | per `make deps-lock` (Netz), nicht von Hand |
Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: zuerst die Schnittstelle (der Handshake-Slice braucht sie als Typ), dann die Einstiegsimplementierung, dann die Validierung.
- `make detekt` läuft auch über `detektTest`; jeder Befund in neuem Code wird im Code behoben, nie unterdrückt oder in `config/detekt/detekt.yml` gelockert (`AGENTS.md` §3.2, §3.6).
## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-handshake-login` ist eingetreten, `slice-adr-pruefwertformat` liegt in `done/` (Prüfwertformat und Konfigurationsweg der Benutzerliste sind entschieden: Nutzerentscheidung, festgehalten als `Accepted`-ADR oder als Fortschreibung von SPEC-051 und `contracts/auth-adapter.md`) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2. Schnittvorschlag: Slice A1 = Schnittstelle, Slice A2 = Einstiegsimplementierung und Validierung; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): Das entschiedene Prüfwertformat braucht eine Abhängigkeit, die sich nicht per Lock und Prüfsumme fixieren lässt, oder die Entscheidung wird zurückgenommen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine). Die Notiz setzt den Stand von `BEO-TDS/adapter-festlegungen-ohne-slice` fort: Dieser Slice ist der Träger der Kotlin-Signatur.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Prüfwertformat und Konfigurationsweg sind in [ADR-0011](../../adr/0011-pruefwertformat.md) (`Accepted`) entschieden: Verfahrenskennung je Eintrag, `pbkdf2-sha256` mit Boden 210 000, Standard 600 000 und Obergrenze 10 000 000, `plain` nur im Testmodus, eigene Datei mit eigenem Parser. Sechs Punkte bleiben dort offen; fünf davon trägt DoD-Punkt 3 dieses Slice und der Nutzer hat sie am 2026-10-30 entschieden, wie im Code gesetzt (Setzungen, nicht gemessen); die Dateirechte trägt `slice-bootstrap-konfiguration`. Die Detailsetzungen (`AUTH_USERS_FILE` als Umgebungsvariable, Listenplatz als Zeilennummer) sind Voreinstellung, keine ausdrückliche Wahl des Nutzers. — **Ausgang:** offen bis zur Closure.
- Überschneidung mit `slice-qa-sicherheit`: Dessen Liefer-Punkt nennt das Prüfwertformat ebenfalls ("entschieden oder als Carveout geführt"). Das Prüfwertformat wird in `slice-adr-pruefwertformat` vorgezogen entschieden; jener Punkt schrumpft dort, die Änderung an `slice-qa-sicherheit` begründet dessen Closure-Notiz. Dieser Plan ändert `slice-qa-sicherheit` nicht. — **Ausgang:** offen bis zur Closure.
- Der konstante Vergleich ist nur strukturell belegt. Messung und JIT-/GC-Einfluss sind ungemessen; eine Aussage "zeitgleich" oder "nicht unterscheidbar" darf in Code, KDoc und Notiz nicht stehen (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.
- Die Signatur hängt am Nebenläufigkeitsmodell: Ein Adapter mit eigener Benutzerverwaltung darf `LOGIN_TIMEOUT` (SPEC-008) nicht überschreiten, ein blockierender Aufruf im Coroutinen-Transport ([ADR-0003](../../adr/0003-transportframework.md)) wäre ein Fehler, ein suspendierender Aufruf bindet die Schnittstelle an die Laufzeit. Welche Form gilt, entscheidet dieser Slice im Einklang mit [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Proposed`) und hält sie als Festlegung fest. — **Ausgang:** offen bis zur Closure.
- Ungemessene Vergleichsregeln: Groß-/Kleinschreibung und Unicode-Normalisierung der Kennung, Verhalten bei leerer Kennung in der Liste. Die Wahl ist eine Festlegung ohne Messung am Referenzclient oder echten SQL Server. — **Ausgang:** offen bis zur Closure.
- Das Passwort liegt im Adapteraufruf als Zeichenfeld; eine Kopie in JVM oder Adapter ist nicht ausgeschlossen ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Geheimnisschutz). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die Pakete `config` und `domain` ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/adapter-festlegungen-ohne-slice` — 1×, offen. Treffer: Dieser Slice ist der Träger der Kotlin-Signatur; die Closure-Notiz führt den Stand fort.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Treffer: Bauart des konstanten Vergleichs und Strukturtest hier, Messung und Drosselung bei `slice-qa-sicherheit` (§6).
- `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt` — 1×, offen. Treffer: Sie begründet die Austauschbarkeit des Adapters; eine Festlegung des Betreibers liegt nicht vor, die Schnittstelle ist deshalb nicht abnahmebindend begründet.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage`. Treffer: jede Aussage der Implementierung trägt einen Test oder ist als Festlegung gekennzeichnet (§2, §6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Dokument als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
