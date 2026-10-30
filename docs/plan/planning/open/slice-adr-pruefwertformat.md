# Slice slice-adr-pruefwertformat: Prüfwertformat der festen Benutzerliste und Konfigurationsweg entscheiden

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

**Berührte Spec-Stellen:** [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) · SPEC-010 · [TDS-FA-02.a](../../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung) · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

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

**Ziel:** Das Prüfwertformat der festen Benutzerliste und der Konfigurationsweg der Liste sind vom Nutzer anhand gemessener Optionen entschieden und stehen als Fortschreibung von [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) und `contracts/auth-adapter.md` oder als neue ADR fest; die offene Frage aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen lautet: Prüfwertformat und Konfigurationsweg.

**Richtung des Nutzers (Eingabe, keine Festlegung):** Das Format ist konfigurierbar. Jeder Eintrag trägt vor dem Prüfwert eine Verfahrenskennung. Beispiele: `{plain}…` ist nur bei ausdrücklich eingeschaltetem Testmodus zulässig und führt sonst zum Startabbruch; `{pbkdf2-sha256}Salz$Iterationen$Hash` nutzt das JDK-Verfahren `PBKDF2WithHmacSHA256` ohne Fremdbibliothek; spätere Verfahren kommen als zusätzliche Kennung hinzu. Der Slice prüft diese Richtung mit Spikes und legt dem Nutzer Optionen vor (§3); er entscheidet nicht selbst, und kein Detail der Beispiele gilt vor der Wahl als gesetzt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode (Parser, Ableitung, Vergleich, Laden der Datei) — der Slice entscheidet und baut nicht; der Code entsteht in `slice-auth-adapter-benutzerliste` (Prüfwert, Verfahren, Validierung, Vergleich) und `slice-bootstrap-konfiguration` (Laden, Konfigurationsmodell).
- Zeitgleichheit des Vergleichs und Drosselung wiederholter Versuche — `slice-qa-sicherheit` ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen); die Spikes messen die Dauer einer Ableitung, nicht den Zeitunterschied zwischen unbekannter Kennung und falschem Passwort.
- Der Authentifizierungsmodus und die Adapter-Schnittstelle selbst — von [ADR-0009](../../adr/0009-authentifizierungsmodus.md) entschieden und `Accepted`; dieser Slice schreibt sie nicht um (Hard Rule `AGENTS.md` §3.5).
- Betrieb der Prüfwert-Datei (Dateirechte, Übergabe in den Container, Geheimnisverwaltung des Betreibers) — Betriebsfrage; der Slice misst nur, wo ein Wert je Weg sichtbar wird, und nennt die Rechte als Anforderung an den Betreiber, falls der Nutzer die Option wählt.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Spikes durchgeführt (S1 bis S3 aus §3); Messwerte, Optionen und Befund stehen als Tabelle in diesem Slice und in der Entscheidung. Der Spike-Code liegt im Scratchpad, ist temporär, nie im Repo und nach der Entscheidung gelöscht.
- [ ] Der Nutzer hat gewählt: Kennungsschema, Verfahrensumfang, Untergrenze der Iterationszahl, Konfigurationsweg und -format, Testmodus-Schalter (Optionen A bis E in §3). Die Wahl ist festgehalten als Fortschreibung von [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) und `contracts/auth-adapter.md` **oder** als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted`, Index und Geschichte nachgezogen), mit der Begründung, warum [ADR-0009](../../adr/0009-authentifizierungsmodus.md) (`Accepted`) nicht überschrieben wird (§3).
- [ ] Anschlussstellen nachgezogen: Spec-Stellen aus dem `Schärft:`-Feld bzw. SPEC-051; die Slices `slice-auth-adapter-benutzerliste` und `slice-bootstrap-konfiguration` (Start-Trigger, Risiken, Aufteilung der Verantwortung für Prüfwert und Konfigurationsmodell) und `slice-qa-sicherheit` (Prüfwertformat-Punkt, nur soweit die Entscheidung ihn auflöst).
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
| Spikes im Scratchpad (Dockerfile je Spike, ohne Mount, Basis-Images per Digest) | neu, danach löschen | nie im Repo; Ergebnisse gehen als Tabelle in diesen Slice und in die Entscheidung |
| `spec/spezifikation.md` (SPEC-051, ggf. Konfigurationsschlüssel in §3, Historie) | update | Fortschreibung, falls der Nutzer sie statt einer ADR wählt, sonst nur Stellen aus dem `Schärft:`-Feld |
| `contracts/auth-adapter.md` (§Einstiegsimplementierung: „Konfigurationsweg und Format sind noch festzulegen“, §Gemessen) | update | festgelegtes Format und Weg; Entwurfs-Hinweise bleiben, wo nichts gemessen ist |
| `docs/plan/adr/<nächste Nummer>-….md` und `docs/plan/adr/README.md` | neu / update, nur bei ADR-Weg | Entscheidung mit Alternativen, Fitness Function, Geschichte; Index |
| `docs/plan/planning/open/slice-auth-adapter-benutzerliste.md`, `slice-bootstrap-konfiguration.md` | update | Verweis auf die Entscheidung, Aufteilung (siehe unten) |
| `docs/plan/planning/open/slice-qa-sicherheit.md` | update, nur falls nötig | Prüfwertformat-Punkt in §2; Begründung in §7 dieses Slice |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

### Spikes (Scratchpad, Docker-only, nichts mounten)

Jeder Spike ist ein Dockerfile mit einem JDK-Basis-Image per Digest; Eingaben werden per `COPY` ins Image gelegt, es gibt keinen Mount und keine Installation auf dem Host (`AGENTS.md` §3.1). Messwerte gelten für das gebaute Image und die Hardware des Spike-Laufs; die Hardware wird in der Messtabelle genannt. Ein Spike belegt, was er misst, und nicht mehr.

- **S1 — Ableitung mit dem JDK.** Welche `SecretKeyFactory`-Algorithmen das JDK-Image anbietet (Liste; ob dort außer PBKDF2-Varianten weitere Verfahren stehen, ist Messgegenstand, nicht Annahme). `PBKDF2WithHmacSHA256` mit Iterationszahlen aus einer Staffel (zum Beispiel 10 000, 100 000, 600 000, 1 000 000; die Staffel legt der Spike fest und nennt sie): Dauer je Ableitung, wiederholt und mit Aufwärmlauf getrennt ausgewiesen, im Verhältnis zu `LOGIN_TIMEOUT` (15 s, SPEC-008). Eingaben an den Rändern der Adapter-Eingabe: leeres Passwort, 128 Einheiten, Surrogatpaar, Umlaut; welche Byte-Kodierung des Passworts vor der Ableitung (UTF-8, UTF-16) das JDK trägt und wie sie in den Prüfwert eingeht. Ausgabelänge und Kodierung von Salz und Hash (Base64 oder Hex) mit den Folgen für die Zeichen im Prüfwert (Trennzeichen `$`). Keine Zeitgleichheitsaussage.
- **S2 — Kennungsschema und Parser.** Die Kennungsschemata aus Option A werden mit Mitteln des JDK und der Kotlin-Standardbibliothek geparst: gültige Einträge, fehlende oder unbekannte Kennung, leerer Rest, zu wenige oder zu viele Felder, nicht numerische oder negative Iterationszahl, Iterationszahl unter der Untergrenze und über einer Obergrenze, Groß-/Kleinschreibung der Kennung. Je Fall: ergibt der Parser einen Fehlerwert mit Art und Listenplatz, und enthält er weder Prüfwert noch Passwort? Ein Format-Kandidat mit Parser-Bibliothek (Option D) wird nur gemessen, soweit sie sich per Lock und Prüfsumme fixieren lässt (`make deps-lock` braucht Netz, im Spike also nur als Befund „fixierbar ja/nein“), und trägt die Kosten einer neuen Abhängigkeit in die Optionstabelle.
- **S3 — Konfigurationsweg.** Für die Wege aus Option D: Kennungen am Rand der Eingabe (0 bis 128 Einheiten, leer, Doppelpunkt, Gleichheitszeichen, Leerzeichen, Unicode) werden in das Dateiformat geschrieben und gelesen; welche Zeichen das Format maskieren muss. Sichtbarkeit der Prüfwerte je Weg im Spike-Container (Datei im Image, Umgebungsvariable): ob `docker inspect` und der Prozess-Umgebungsblock des Containers den Wert zeigen. Ergebnis ist eine Matrix „Weg × wo der Wert lesbar ist“, keine Aussage über Dateirechte im Betrieb. Wie `plain` ohne Testmodus abgewiesen wird: Startabbruch vor dem Binden des Ports, Meldung ohne Prüfwert.

### Optionen für die Nutzerentscheidung

Die Optionen sind Vorschläge zur Wahl; der Nutzer wählt je Gruppe, und eine Gruppe darf auch eine andere als die genannten Antworten haben. Der Slice legt sie mit den Messwerten aus S1 bis S3 vor.

| Gruppe | Frage | Optionen |
|---|---|---|
| A Kennungsschema | Wie nennt der Eintrag sein Verfahren? | A1 Präfix `{kennung}` vor dem Prüfwert (Richtung des Nutzers); A2 eigenes Feld im Eintrag (Schlüssel `verfahren`); A3 `$kennung$…`-Stil. Gemeinsam zu klären: Eintrag ohne Kennung ist Startabbruch oder hat ein Standardverfahren; eine unbekannte Kennung ist Startabbruch. |
| B Verfahrensumfang | Welche Verfahren in der ersten Version? | B1 `plain` (nur Testmodus) und `pbkdf2-sha256`; B2 nur `pbkdf2-sha256` (Testbetrieb mit einem Prüfwert zu bekanntem Passwort); B3 B1 oder B2 plus ein weiteres JDK-Verfahren, falls S1 eines zeigt. Gemeinsam zu klären: wie spätere Verfahren ergänzt werden (eine Kennung je Verfahren, im Code als geschlossene Menge) und ob ein Verfahren ohne Fremdbibliothek Bedingung bleibt. |
| C Iterationszahl | Untergrenze und Obergrenze? | C1 feste Untergrenze aus S1, nicht konfigurierbar; C2 Untergrenze mit Standardwert, vom Betreiber nicht unter einen harten Boden senkbar; C3 keine Untergrenze (nur Testmodus). Die Obergrenze schützt vor einem Eintrag, dessen Ableitung die Anmeldung über `LOGIN_TIMEOUT` hinaus blockiert; sie wird aus S1 begründet. Der Scheinvergleich bei unbekannter Kennung rechnet mit denselben Parametern; wie, regelt der Auth-Slice. |
| D Konfigurationsweg und -format | Woher kommt die Liste, in welcher Form? | Weg: D1 Datei, Pfad per Umgebungsvariable oder Argument; D2 Umgebungsvariablen; D3 eigene Datei nur für die Benutzerliste, getrennt von der übrigen Konfiguration. Format: F1 `java.util.Properties` (JDK); F2 eigenes Zeilenformat; F3 ein Format mit Parser-Bibliothek (neue Abhängigkeit mit Lock und Prüfsumme). Gemeinsam zu klären: Zeichenkodierung der Datei, Maskierung von Sonderzeichen in der Kennung (S3). |
| E Testmodus-Schalter | Was schaltet `plain` frei? | E1 derselbe ausdrückliche Testmodus wie die Abschwächung der Verschlüsselung (SPEC-010); E2 ein eigener Schalter nur für `plain`. In beiden Fällen nennt der Start-Log den Modus (analog SPEC-010), und `plain` ohne Schalter bricht den Start ab. |

### Entscheidungsform und Verhältnis zur Authentifizierungs-ADR

[ADR-0009](../../adr/0009-authentifizierungsmodus.md) ist `Accepted` und wird nicht überschrieben (`AGENTS.md` §3.5). Sie wählt SQL-Authentifizierung über einen Adapter mit fester Benutzerliste und nennt Konfigurationsweg und Prüfwertformat als „noch festzulegen“ (§Entscheidung) und als offen (§Offen); sie entscheidet beides nicht. Das Festlegen füllt daher eine benannte Lücke und widerspricht ihrer Entscheidung nicht. Zwei Formen stehen zur Wahl, der Nutzer entscheidet:

- **Fortschreibung** von [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) (technisch fortschreibbar) und `contracts/auth-adapter.md`: genügt, wenn die Wahl nur Format und Defaults festlegt. Begründung für die Unversehrtheit der ADR: Die Spezifikation darf geschärft werden, die ADR bleibt wörtlich stehen, und ihr §Offen bleibt als Stand zum Zeitpunkt ihrer Annahme lesbar.
- **Neue ADR** mit Begründung, Alternativen (Optionen A bis E), Messmatrix und Geschichte, `Schärft:` SPEC-051 und `Bezug:` auf [ADR-0009](../../adr/0009-authentifizierungsmodus.md) ohne `Supersedes`, weil sie nichts entscheidet, das hier geändert würde. Der Planner neigt zur ADR, wenn der Nutzer einen Testmodus-Schalter mit Sicherheitswirkung (Option E) oder eine neue Abhängigkeit (Option D, F3) wählt: Beides ist dauerhaft und betrifft zwei Slices; das ist eine Neigung, keine Entscheidung. Würde die Wahl [ADR-0009](../../adr/0009-authentifizierungsmodus.md) widersprechen, wäre das eine `Supersedes`-ADR; die Spikes zeigen derzeit keinen solchen Fall, das ist vor der Wahl zu prüfen.

### Verantwortung für Prüfwert und Konfigurationsmodell

Die Grenze zwischen den beiden Anschluss-Slices ist Teil der Entscheidung und wird dort festgehalten. Der Plan nimmt an, ohne es zu setzen: `slice-auth-adapter-benutzerliste` hält den Prüfwert als Wert (Verfahren, Parameter, Salz, Hash), parst und validiert ihn, vergleicht und kennt den Testmodus als Eingabewert (`plain` ohne Testmodus ergibt einen Fehlerwert); `slice-bootstrap-konfiguration` besitzt das Konfigurationsmodell, liest Datei oder Umgebung nach dem entschiedenen Weg, reicht die Einträge als Text an die Validierung des Auth-Slice weiter, führt den Testmodus-Schlüssel und bricht bei einem Fehlerwert den Start ab. Weicht die Entscheidung davon ab, werden beide Slices in §3/§4 angepasst.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-handshake-login` ist eingetreten, ein Verantwortlicher ist im Kopf eingetragen und der Nutzer steht für die Wahl der Optionen A bis E bereit. Der Slice braucht den Codec nicht; er kann vor `slice-auth-adapter-benutzerliste` und `slice-bootstrap-konfiguration` laufen und ist ihre Voraussetzung.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Nutzer entscheidet nicht, oder die gewählte Option braucht eine Abhängigkeit, die sich nicht per Lock und Prüfsumme fixieren lässt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben. Jede Aussage der Entscheidung trägt eine Messzeile (S1 bis S3) oder ist als Setzung des Nutzers gekennzeichnet (`BEO-TDS/aussagen-ueber-messung-hinaus`).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Richtung des Nutzers wird als gesetzt gelesen, bevor die Spikes sie geprüft haben (zum Beispiel Beispielformat `Salz$Iterationen$Hash` oder Kennung in geschweiften Klammern). — **Ausgang:** offen bis zur Closure.
- Überschneidung mit `slice-qa-sicherheit`: Dessen Liefer-Punkt nennt das Prüfwertformat ebenfalls („entschieden oder als Carveout geführt“). Dieser Slice entscheidet es vorgezogen; `slice-qa-sicherheit` bleibt beim Anlegen dieses Slice unverändert, weil seine Änderung eine Entscheidung vorwegnähme, die noch nicht gefallen ist. Mit der Closure wird der Punkt dort auf die verbleibenden Gegenstände (Zeitgleichheit, Drosselung) gekürzt und der Verweis auf die Entscheidung gesetzt, die Änderung ist in §7 begründet. — **Ausgang:** offen bis zur Closure.
- Die Dauer einer Ableitung ist an Image und Hardware des Spike-Laufs gemessen; sie gilt weder für den Produktivbetrieb noch für die parallele Last mehrerer Anmeldungen. Eine Untergrenze der Iterationszahl ist damit eine Festlegung mit Messwert, keine Leistungszusage; die Wirkung auf den Coroutinen-Transport ([ADR-0003](../../adr/0003-transportframework.md)) bei blockierender Ableitung ist nicht gemessen. — **Ausgang:** offen bis zur Closure.
- Ein Testmodus-Schalter kann den Betrieb mit `plain` unbemerkt machen, wenn er mit anderen Schaltern geteilt wird (Option E1). — **Ausgang:** offen bis zur Closure.
- Prüfwerte in Datei oder Umgebung: die Sichtbarkeit je Weg wird gemessen, die Dateirechte und die Übergabe im Betrieb nicht. — **Ausgang:** offen bis zur Closure.
- Eine Parser-Bibliothek (Option F3) wäre eine neue Abhängigkeit mit Lock und Prüfsumme; ohne Netz im Spike ist nur „fixierbar ja/nein“ zu befunden. — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure geschrieben. Die Ruheort-Regel gilt für jeden Pfad: Verweise werden so geschrieben, wie sie vom Ruheort `done/` auflösen.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec, Vertragsdatei und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/`) durchgegangen. Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice` (1×, offen; Konfigurationsweg des Adapters — dieser Slice trägt ihn), `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (1×, offen; Sicherheitsaussagen zum Prüfwert) und `BEO-TDS/aussagen-ueber-messung-hinaus` (2×; Grund für die Messzeilen-Pflicht in §5).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
