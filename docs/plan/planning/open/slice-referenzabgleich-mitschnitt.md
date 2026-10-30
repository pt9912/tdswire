# Slice slice-referenzabgleich-mitschnitt: Referenzabgleich, Mitschnitt-Lauf: Paketmitschnitte der Referenzinstanz gegen den Server abspielen und feldweise vergleichen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)

**Berührte Spec-Stellen:** SPEC-004 · SPEC-020 · SPEC-030 · SPEC-031 · SPEC-045 · SPEC-050

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

**Ziel:** Redigierte Paketmitschnitte des Referenzclients gegen die Referenzinstanz werden gegen einen frisch gestarteten Server abgespielt und feldweise verglichen (Nachrichten- und Tokenfolge, COLMETADATA, Zeilenwerte und -zahl, DONE-Status, ERROR, ENVCHANGE); dieser Lauf ist die Messung der Tokenreihenfolge, der DONE-Statusbits und der RETURNVALUE-Zählung am Wire.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Live-Lauf — [slice-referenzabgleich-live](slice-referenzabgleich-live.md); ein Mitschnitt spielt die Anfragen des Clients ab, nicht den Client (SPEC-045): fragt der Client wegen einer anderen Antwort anders, zeigt das nur der Live-Lauf.
- Referenzinstanz, Container, Make-Target des Vergleichslaufs und EULA — [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md).
- Verschlüsselte Mitschnitte und der TDS-8.0-Pfad — die Referenzinstanz bietet TDS 8.0 nicht an ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)); Mitschnitte entstehen unverschlüsselt (`Encrypt=false`), der verschlüsselte Pfad bleibt ohne Wire-Abgleich und steht so in der Closure.
- Fuzz und Fragmentierung — [slice-qa-stabilitaet](../done/slice-qa-stabilitaet.md); der Mitschnitt spielt gültige Nachrichten ab.
- Das Beheben gefundener Abweichungen — je Befund ein Slice nach der Messung.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Aufnahme:** Redigierte Mitschnitte ohne Secrets für die Szenarien aus SPEC-045 (Anmeldung, Batch, RPC mit Ausgabewert, mehrteiliges Ergebnis, Fehler, Cancel), aufgenommen nach der Methode, die der Nutzer wählt (Frage in §6), abgelegt mit Herkunft (Version der Instanz, Client-Version, Schema-Stand); ein Test belegt, dass kein Passwort und kein Prüfwert in den Ablagen steht, mit Eingaben, die beides enthalten.
- [ ] **Abspielen und Vergleich:** Die Mitschnitte laufen gegen einen frisch gestarteten Server; der Vergleich ist feldweise nach SPEC-045 (Nachrichten- und Tokenfolge, COLMETADATA mit Typ, Länge, Precision, Scale und Nullbarkeit, Zeilenwerte und -zahl, DONE-Status und Zählung, ERROR mit Nummer, State, Class und Text, ENVCHANGE); vom Server selbst vergebene Werte nur auf vorhanden oder nicht vorhanden.
- [ ] **Ausnahmeliste und Messaussage:** Dieselbe Ausnahmeliste wie im Live-Lauf (Art *so gewollt* oder *Lücke*); die Aussagen des Codecs und der ADRs zu Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung (bisher Handvektor und Festlegung) sind je Punkt von *Festlegung* auf *gemessen* gehoben oder als Abweichung geführt; die Übergabe dieses Teils aus [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) ist dort vermerkt.

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
| Aufnahme- und Abspielwerkzeug <!-- d-check:ignore (geplant: Ort und Sprache nach Entscheidung) --> | neu | Testwerkzeug in Docker; Ort und Sprache offen (Frage in §6) |
| Mitschnitte <!-- d-check:ignore (geplant: Ablage nach Entscheidung) --> | neu | redigiert, mit Herkunft; Ablage im Repo oder außerhalb offen (Frage in §6) |
| Ausnahmeliste <!-- d-check:ignore (geplant: neue Datei) --> | update | gemeinsame Liste mit dem Live-Lauf |
| `tools/mk/` | update | Make-Target des Mitschnitt-Laufs; Eintrag in `harness/README.md` §Sensors |
| Tests des Werkzeugs <!-- d-check:ignore (geplant: neue Datei) --> | neu | Redaktion, Vergleich Happy/Boundary/Negative; `@spec`-Tag SPEC-045 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-referenzabgleich-live](slice-referenzabgleich-live.md) liegt in `done/` (Format der Ausnahmeliste, Server-Seite und Schema), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder das Werkzeug sprengt den Rahmen; Schnittvorschlag: Aufnahme und Abspielen als zwei Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Aufnahme ist mit Docker-only und ohne Mount nicht möglich (Rechte für Paketaufnahme) — der Nutzer entscheidet über eine Ausnahme als Carveout.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt für Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung je ein Ergebnis (gemessen mit Beleg oder Abweichung), die Grenzen (eine Instanz, TDS 7.4 unverschlüsselt) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Doppelte Zuständigkeit: [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) nennt Tokenreihenfolge, Statusbits und RETURNVALUE-Zählung in seiner DoD. Die Client-API zeigt sie nach ihrer Gestalt nicht (Annahme, nicht gemessen); dieser Slice trägt sie am Wire, und [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) ist dort mit Begründung und Verweis gekürzt. Stellt sich heraus, dass der Client sie doch zeigt, wird die Kürzung zurückgenommen. — **Ausgang:** offen bis zur Closure.
- Aufnahme-Methode (Frage in §6): ein Proxy zwischen Client und Instanz, ein Mitschnitt auf Netzwerkebene oder ein Log der Instanz; jede Methode braucht Docker-only, kein Mount und Basis-Images per Digest, und eine zusätzliche Komponente im Netzwerk kann Pakete verändern. — **Ausgang:** offen bis zur Closure.
- Abspielwerkzeug (Frage in §6): Ort und Sprache (Test-Quelltext der JVM, Hilfsprojekt im .NET-Gerüst) sind offen; es ist Testcode ohne Wirkung auf den Produktivpfad, aber Eigencode, der Vergleiche entscheidet. — **Ausgang:** offen bis zur Closure.
- Felder, die die Gegenstelle selbst vergibt (Paket-ID, Sitzungs-ID, Zeitstempel, Collation-Bytes, ENVCHANGE-Werte, Versionstext), dürfen den Vergleich nicht kippen; die Liste dieser Felder ist eine Setzung (SPEC-045) und steht mit Begründung in der Ausnahmeliste. — **Ausgang:** offen bis zur Closure.
- Mitschnitte hängen am Schema-Stand der Instanz: eine Schemaänderung macht sie ungültig, und die Neuaufnahme gehört in denselben Schritt wie die Änderung (SPEC-045). — **Ausgang:** offen bis zur Closure.
- Größe und Ablage der Mitschnitte (Frage in §6): im Repo wachsen sie mit den Szenarien, außerhalb sind sie nicht reproduzierbar; Mitschnitte enthalten nur Testdaten und keine Secrets. — **Ausgang:** offen bis zur Closure.
- Die Aussage „gleich wie die Referenz“ gilt nur für die aufgenommenen Szenarien einer Instanz in einem Lauf, nicht allgemein (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt Testwerkzeug, Docker-Stages und Dokumentation; keine dieser Strukturen trägt eine eigene Strukturregel (Achse 1) oder eine eigene Inventur-Linie (Achse 2) neben dem Repo insgesamt: eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md)). Träger der Wire-Messung (Tokenreihenfolge, DONE-Statusbits, RETURNVALUE-Zählung); der Zähler dieser Beobachtung steht mit Überschneidung zu [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 7×, Stand *geplant* (Träger [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md)). Betrifft die Grenze: kein Wire-Abgleich für TDS 8.0 und TLS.
- `BEO-TDS/vergleichslauf-d-ungeklaert` — 1×, offen (Träger [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md)). Betrifft Ablage, Häufigkeit und Machbarkeit in CI: Träger bleibt [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md).
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, Stand *geplant* (Träger [slice-qa-sicherheit](slice-qa-sicherheit.md)). Betrifft die Redaktion der Mitschnitte: Passwort und Prüfwert erscheinen nirgends in der Ablage (§2).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über die Wire-Gleichheit nennt Instanz, Version und Szenario (§6).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft den Vergleichstest: Abweichungen in verschiedenen Feldern statt in einem (§2).
- `BEO-TDS/ablage-im-repo-bricht-docs-check` — 1×, offen. Betrifft Aufnahme und Ablage: Ausgaben außerhalb des Repos (`mktemp -d`, `trap`), wenn das Rezept sie sammelt.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
