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

- [x] **Schnittstelle** im Package `config` (ARC-002, SPEC-051; Wertetypen, die weitere Schichten brauchen, im Package `domain`): Kotlin-Signatur des Adapters mit der Eingabe nach `contracts/auth-adapter.md` (Kennung, Passwort als überschreibbares Zeichenfeld, Verbindungskontext, Authentifizierungsmerkmale; der Verschlüsselungsmodus als Eingabe ist dort ein ungemessener Entwurf und bleibt als Festlegung gekennzeichnet) und der Ausgabe `Accepted(principal)` oder `Rejected(reason)`. Ein Test belegt, dass keine automatisch erzeugte Textdarstellung (`toString`) von Eingabe, Ausgabe oder Benutzerliste Passwort, Kennung oder Prüfwert enthält ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). `contracts/auth-adapter.md` führt die festgelegte Signatur; der Hinweis, sie sei noch festzulegen, wird nachgezogen.
- [x] **Einstiegsimplementierung** (feste Benutzerliste, Prüfwertformat nach der Entscheidung aus §4): bekannte Kennung mit richtigem Passwort ergibt `Accepted`, falsches Passwort und unbekannte Kennung ergeben `Rejected` mit einem Grund ohne Passwort und ohne Kennung; der Vergleich läuft in konstanter Zeit über die Länge hinaus, bei unbekannter Kennung läuft ein Scheinvergleich. Belegt als Strukturtest (der Scheinvergleich wird tatsächlich ausgeführt, der Vergleich bricht nicht beim ersten Unterschied ab); eine Zeitaussage entsteht daraus nicht. Randfälle der Eingabe: leere Kennung und leeres Passwort (der Client öffnet damit, [ADR-0009](../../adr/0009-authentifizierungsmodus.md)), 128 UTF-16-Einheiten, Surrogatpaar, Eingabe über der Grenze (Ablehnung, keine Ausnahme).
- [x] **Validierung der Liste beim Start** ([TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)): doppelte Kennung, leere Kennung, ungültiger Prüfwert und leere Liste ergeben einen Fehlerwert; die Diagnose nennt die Art des Fehlers und den Listenplatz, nie Passwort oder Prüfwert. Welche dieser Fälle den Start abbrechen, ist als Festlegung im KDoc gekennzeichnet (das Lastenheft verlangt, dass eine sicherheitsrelevante Fehlkonfiguration abgewiesen wird, [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)). Aus den offenen Punkten von [ADR-0011](../../adr/0011-pruefwertformat.md) trägt dieser Slice fünf, jeweils vom Nutzer entschieden oder gemessen und als Festlegung gekennzeichnet, sonst in der Closure als weiter offen mit Grund geführt: ob im Testmodus ein niedrigerer Iterationsboden gilt, ob Kommentare und Leerzeilen im Zeilenformat zulässig sind, wie Zeilenenden (insbesondere CRLF) behandelt werden, welche Hash- und Mindest-Salzlänge gelten und wie lang die Ziffernfolge der Iterationen sein darf; die Dateirechte und die Übergabe der Datei im Betrieb trägt `slice-bootstrap-konfiguration`.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage der Implementierung steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements); die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (Register, `BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8). Insbesondere: Das Überschreiben des Passwortfelds schützt nur dieses Feld, nicht Paket-, Lese- und TLS-Puffer ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Geheimnisschutz); kein KDoc behauptet mehr.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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

- Prüfwertformat und Konfigurationsweg sind in [ADR-0011](../../adr/0011-pruefwertformat.md) (`Accepted`) entschieden: Verfahrenskennung je Eintrag, `pbkdf2-sha256` mit Boden 210 000, Standard 600 000 und Obergrenze 10 000 000, `plain` nur im Testmodus, eigene Datei mit eigenem Parser. Sechs Punkte bleiben dort offen; fünf davon trägt DoD-Punkt 3 dieses Slice und der Nutzer hat sie am 2026-10-30 entschieden, wie im Code gesetzt (Setzungen, nicht gemessen); die Dateirechte trägt `slice-bootstrap-konfiguration`. Die Detailsetzungen (`AUTH_USERS_FILE` als Umgebungsvariable, Listenplatz als Zeilennummer) sind Voreinstellung, keine ausdrückliche Wahl des Nutzers. — **Ausgang:** **weiter offen** → Träger-Slice `slice-bootstrap-konfiguration` für den offenen Rest (Dateirechte und Übergabe der Datei im Betrieb, Lesen der Datei samt UTF-8-BOM; die DoD des Slice nennt alle drei, geprüft gegen den Inhalt der Datei). Die fünf Punkte sind erledigt: vom Nutzer am 2026-10-30 entschieden und im Code, im KDoc und im Vertrag als Setzung geführt, nicht gemessen; [ADR-0011](../../adr/0011-pruefwertformat.md) bleibt unverändert und führt sie weiter unter „Offen“ ([AGENTS.md](../../../../AGENTS.md) §3.5), ob eine Folge-ADR sie schließt, ist eine Frage an den Nutzer (siehe §7).
- Überschneidung mit `slice-qa-sicherheit`: Dessen Liefer-Punkt nennt das Prüfwertformat ebenfalls ("entschieden oder als Carveout geführt"). Das Prüfwertformat wird in `slice-adr-pruefwertformat` vorgezogen entschieden; jener Punkt schrumpft dort, die Änderung an `slice-qa-sicherheit` begründet dessen Closure-Notiz. Dieser Plan ändert `slice-qa-sicherheit` nicht. — **Ausgang:** **entfallen** — nicht eingetreten: Das Prüfwertformat steht in [ADR-0011](../../adr/0011-pruefwertformat.md), und `slice-qa-sicherheit` führt in seiner DoD nur noch Zeitgleichheit, Drosselung und den Scheinvergleich; eine doppelte Zusage besteht nicht. Diese Closure ergänzt dort einen Satz zur Grenze bei gemischten Listen.
- Der konstante Vergleich ist nur strukturell belegt. Messung und JIT-/GC-Einfluss sind ungemessen; eine Aussage "zeitgleich" oder "nicht unterscheidbar" darf in Code, KDoc und Notiz nicht stehen (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** **weiter offen** → Beobachtungs-Register `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (Evidence dieser Closure, Träger `slice-qa-sicherheit`). Die Suche der Verifikation fand keine Zeitaussage in Code, KDoc und Vertrag; die Bauart ist strukturell belegt, die Zeit nicht gemessen.
- Die Signatur hängt am Nebenläufigkeitsmodell: Ein Adapter mit eigener Benutzerverwaltung darf `LOGIN_TIMEOUT` (SPEC-008) nicht überschreiten, ein blockierender Aufruf im Coroutinen-Transport ([ADR-0003](../../adr/0003-transportframework.md)) wäre ein Fehler, ein suspendierender Aufruf bindet die Schnittstelle an die Laufzeit. Welche Form gilt, entscheidet dieser Slice im Einklang mit [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Proposed`) und hält sie als Festlegung fest. — **Ausgang:** **entfallen** — entschieden und als Festlegung gehalten (Vertrag, KDoc): Der Aufruf blockiert und ist nicht `suspend`; der Aufrufer hält den Transport-Thread frei und `LOGIN_TIMEOUT` ein. [ADR-0004](../../adr/0004-session-zustandsautomat.md) steht inzwischen auf `Accepted`. Die Pflicht des Aufrufers und die ungemessene Wirkung auf den Transport führt `slice-handshake-login` seit dieser Closure in DoD-Punkt 2 und §6.
- Ungemessene Vergleichsregeln: Groß-/Kleinschreibung und Unicode-Normalisierung der Kennung, Verhalten bei leerer Kennung in der Liste. Die Wahl ist eine Festlegung ohne Messung am Referenzclient oder echten SQL Server. — **Ausgang:** **weiter offen** → Beobachtungs-Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (Evidence dieser Closure, Träger `slice-qa-reproduzierbarkeit`). Die Festlegung selbst steht: Benutzernamen werden zeichengenau verglichen (keine Faltung, keine Normalisierung), eine leere Kennung in der Liste ist ein Fehler; Tests pinnen sie (Verifikation, Liefer-Punkt 2: abweichende Groß-/Kleinschreibung, Abweichung um ein Zeichen und Leerzeichen werden abgelehnt; Fehlerart `EMPTY_USER_NAME` im Parser-Test).
- Das Passwort liegt im Adapteraufruf als Zeichenfeld; eine Kopie in JVM oder Adapter ist nicht ausgeschlossen ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Geheimnisschutz). — **Ausgang:** **weiter offen** → Beobachtungs-Register `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (neu, 1×; kein Träger-Slice, siehe §7). Eingetreten ist, was der Plan nannte: Das `SecretKey`-Objekt der JDK-Ableitung wird nicht überschrieben, und zwei Überschreib-Schritte haben keinen Test; der Vertrag nennt beides.

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

- **Was hat funktioniert:** Der Schnitt trug: Schnittstelle, Einstiegsimplementierung und Validierung lagen in einem Slice, die Rückführung nach §4 war nicht nötig. Das Orakel (`oraclePbkdf2`, eigene Schleife über `javax.crypto.Mac`) und die RFC-Vektoren hielten die Ableitung des JDK-Pfads fest, und die Mutationen des Verifiers fanden, was die Tests übersahen: 41 Mutationen, 34 rot, 7 überlebt; davon 3 echte Lücken (M22 und die bewusst offenen M06c, M06d) und 4 äquivalente Überlebende (M08, M09f, M11c, M24). Der Implementer hatte vor der Verifikation 20 Mutationen gefahren und nach den Befunden 3 weitere (M22 und zwei weitere Mutationen der Attrappen-Wahl, alle rot; Angabe des Implementers, die Liste liegt nicht im Repo, der Verifier hat nach den Befunden nicht erneut mutiert).
- **Was ging anders als geplant:** (a) Der Scheinvergleich nahm den ersten Eintrag der Liste; bei gemischten Listen (andere Iterationszahl, `plain`) läuft ein anderer Rechenschritt als für den bekannten Benutzer (Review F-1, Verifikation M-1). Jetzt ist die Attrappe der `pbkdf2`-Eintrag mit der höchsten Iterationszahl (bei Gleichstand der erste, bei nur `plain` der erste Eintrag); ein Test mit gemischter Liste belegt die Wahl. Die Grenze bleibt und steht im KDoc und im Vertrag; eine Zeitaussage entsteht daraus nicht. (b) Die fünf offenen Punkte aus [ADR-0011](../../adr/0011-pruefwertformat.md) waren vom Implementer gesetzt statt entschieden oder offen geführt (Review F-3, Verifikation M-2); der Nutzer hat sie am 2026-10-30 bestätigt, sie stehen als Entscheidung des Nutzers, als Setzung, nicht gemessen im KDoc und im Vertrag. (c) Die Überschreib-Aussage reichte weiter als der Beleg (Verifikation L-1): M06c (`PBEKeySpec.clearPassword()`) und M06d (UTF-8-Zwischenpuffer des `plain`-Pfads) überleben bewusst ohne Test, weil beide Puffer von außen nicht beobachtbar sind; das `SecretKey`-Objekt der JDK-Ableitung wird nicht überschrieben. Die Aussage ist auf das Belegte begrenzt. (d) Der Vertrag widersprach sich beim Scheinvergleich (F-4), die Testkommentare standen teils im Konjunktiv (F-5), ein Tippfehler (F-6), `ITERATIONS_DEFAULT` hat keinen Leser und ist so gekennzeichnet (F-10). (e) Die Redundanz M08 (Form-Prüfung neben der Rück-Kodierung) und M09f (`\d` ist ohne Flag ASCII) bleibt bewusst stehen: beide Prüfungen sind äquivalent, keine fehlt im Verhalten. (f) Das UTF-8-BOM am Dateianfang entfernt der Parser nicht; der Vertrag führt es als Vormerkung für das Lesen der Datei (F-11), Träger `slice-bootstrap-konfiguration`. (g) Die erzeugte Abdeckungsmatrix `docs/plan/spec-abdeckung.md` stand auf „OHNE NACHWEIS“ für SPEC-051 und SPEC-012 (Verifikation L-4); diese Closure hat sie mit `make spec-abdeckung` neu erzeugt (SPEC-051: 29 Tests, SPEC-012: 1 Test; die Neuerzeugung änderte auch Zahlen anderer Zeilen, die seit der letzten Erzeugung gewachsen waren). Die Befunde sind eingearbeitet (Merge `345bee5`).
- **Reports:** [Review](../../../reviews/2026-10-30-bd9eac0-slice-auth-adapter-benutzerliste.md) (kein HIGH; F-1 und F-3 MEDIUM, F-2 und F-4 bis F-7 LOW, F-8 bis F-11 INFO) und [Verifikation](../../../reviews/2026-10-30-bd9eac0-verifikation-slice-auth-adapter-benutzerliste.md) (kein HIGH; M-1 und M-2 MEDIUM, L-1 bis L-6 LOW; 171 Tests grün, Zuwachs 28 gegenüber 143), beide gegen den Merge `bd9eac0`. Die Nachbesserungen aus `40184eb` hat kein Reviewer und kein Verifier erneut geprüft; `make gates` lief nach ihnen. Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, außerhalb der Tests ruft bisher niemand den Adapter.
- **Laufzeit von `make test`:** Die Task `:test` brauchte vorher 2,1, 1,7 und 1,9 s und nachher 2,8, 3,2 und 2,8 s (Verifikation, 3 abwechselnde Läufe je Seite, ein Host mit 20 logischen CPUs, `--no-cache-filter test`); der Zuwachs liegt bei etwa 1 s für 28 Tests. Die Gesamtdauer des Docker-Builds streute (32 bis 47 s) stärker als der Zuwachs. Keine Leistungszusage.

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt hier beobachtet in einem Lauf dieses Repos (keine Messung an einem Microsoft-SQL-Server oder am Referenzclient); *Handvektor* heißt ein Vektor mit Quelle, vom Verifier gegen die Quelle geprüft; *Selbstkonsistenz* heißt Code gegen Code, auch wo das Orakel eine zweite Implementierung ist; *Festlegung* heißt Setzung ohne Messung und ohne Vektor.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | Laufzeit-Zuwachs der Task `:test` von etwa 1 s (Mittel 1,9 s auf 2,9 s) für 28 Tests | Verifikation, Abschnitt 6, 3 Läufe je Seite; ein Host, keine Leistungszusage |
| Handvektor | PBKDF2-HMAC-SHA-256 des JDK liefert für `passwd`/`salt`/c = 1 und `Password`/`NaCl`/c = 80 000 die Erwartungswerte aus den Testvektoren von RFC 7914 §11 (erste 32 Byte). Der Verifier hat die Werte gegen den RFC-Text bestätigt (<https://www.rfc-editor.org/rfc/rfc7914.txt>) und mit Python (`hashlib.pbkdf2_hmac` und eine eigene Schleife über `hmac`) gegengerechnet; ebenso das leere Passwort, die Kollision `?` und `\uD800` (gleicher Hash) und NFC gegen NFD (verschiedene Hashes) | `AuthPrimitivesTest`; Verifikation Abschnitt 5. Der zweite Vektor (c = 80 000) läuft nur gegen das JDK, nicht gegen das Orakel; eine Quellenangabe (URL) steht im Repo nicht |
| Selbstkonsistenz | Das Orakel `oraclePbkdf2` (RFC 8018 §5.2, Abschnittsnummer vom Verifier am RFC-Text bestätigt) und der JDK-Pfad stimmen in jedem Adaptertest mit 210 000 Iterationen überein. Das Orakel teilt mit dem JDK-Pfad die HMAC-SHA-256-Primitive des Providers; unabhängig sind Iterations- und XOR-Schleife, Blockbildung und Passwort-/Salz-Umgang | `AuthTestSupport.kt`, `UserListAuthAdapterTest`, `AuthPrimitivesTest`; Verifikation M14, M20 rot |
| Selbstkonsistenz | Parser: 17 Fehlerarten mit 1-basierter Zeilennummer, ASCII-Ziffern, kanonisches Base64 mit Padding, Hash genau 32 Byte, Salz mindestens 16 Byte, `plain` nur bei genau `true`; Fehlerwert ohne Prüfwert und Namen | `UserListParserTest`; Verifikation M07, M09a bis M09e, M10a bis M10c, M12, M15 bis M19, M21, M23 rot |
| Selbstkonsistenz (Strukturtest, keine Zeitaussage) | Bei unbekanntem Benutzernamen läuft dieselbe Ableitung wie für die Attrappe (Iterationen, Salzlänge, Ausgabelänge) und ein Vergleich über alle 32 Byte; der Vergleich bricht nicht beim ersten Unterschied ab und untersucht bis zur längeren Länge; bei gemischter Liste nimmt die Attrappe die höchste Iterationszahl | `UserListAuthAdapterTest` mit `RecordingPrimitives`; Verifikation M01 bis M05 rot; M22 überlebte, danach Test mit gemischter Liste (Angabe des Implementers) |
| Selbstkonsistenz | Keine Textdarstellung von `AuthRequest`, `PasswordChars`, `ConnectionContext`, `Principal`, `Accepted`, `UserList` und `UserEntry` enthält Passwort, Benutzernamen oder Prüfwert (13 Fälle) | `AuthTextRepresentationTest`; Verifikation M11a, M11b, M11d, M11e, M25 rot, M11c äquivalent |
| Festlegung (Nutzerentscheidung, 2026-10-30) | Testmodus-Boden gleich dem sonstigen Boden 210 000; keine Kommentare (`#` ist ein gewöhnlicher Eintrag), leere Zeile ist ein Fehler; CRLF nicht toleriert (jedes CR ist der Fehler `CARRIAGE_RETURN`); Hash genau 32 Byte, Salz mindestens 16 Byte, keine Salz-Obergrenze; Iterationen als Ziffernfolge von 1 bis 8 Ziffern ohne führende Null. Gesetzt, nicht gemessen | KDoc von `UserList` und `Pbkdf2Limits`, Vertrag `contracts/auth-adapter.md` §Einstiegsimplementierung; [ADR-0011](../../adr/0011-pruefwertformat.md) bleibt unverändert ([AGENTS.md](../../../../AGENTS.md) §3.5) |
| Festlegung | Die Attrappe des Scheinvergleichs ist der `pbkdf2`-Eintrag mit der höchsten Iterationszahl (Gleichstand: der erste; nur `plain`: der erste Eintrag, ohne Ableitung). Bei gemischten Listen durchläuft ein bekannter Benutzer mit niedrigerer Iterationszahl oder `plain` einen anderen Rechenschritt | KDoc von `UserList`, Vertrag; kein Zeitbeleg |
| Festlegung | Benutzernamen werden zeichengenau verglichen (keine Faltung, keine Normalisierung); eine leere Kennung in der Liste ist ein Fehler; der erste Fehler in Dateireihenfolge bricht die Prüfung ab; die Verfahrenskennung ist case-sensitiv und klein | KDoc, Vertrag; `UserListAuthAdapterTest`, `UserListParserTest` |
| Festlegung | Der Aufruf des Adapters blockiert und ist nicht `suspend`; der Aufrufer hält den Transport-Thread frei und `LOGIN_TIMEOUT` ein | Vertrag §Kotlin-Signatur; Träger der Pflicht `slice-handshake-login` |
| Festlegung | Eingaben über 128 UTF-16-Einheiten (Kennung oder Passwort) ergeben `Rejected(INPUT_TOO_LONG)` ohne Ausnahme und ohne Ableitung; 128 Einheiten, auch als 64 Surrogatpaare, werden angenommen. Die Grenze ist nur mit ASCII gemessen ([ADR-0009](../../adr/0009-authentifizierungsmodus.md)), Surrogatpaare als Einheiten gezählt ist Festlegung | `UserListAuthAdapterTest`; Verifikation M13a bis M13c rot |
| Festlegung | Der Adapter überschreibt nur eigene Kopien: das an die Ableitung übergebene Zeichenfeld, die abgeleiteten Bytes, den UTF-8-Kandidaten des `plain`-Pfads (je mit Test, M06a und M06b rot); `PBEKeySpec.clearPassword()` und die Nullung des UTF-8-Zwischenpuffers haben keinen Test (M06c, M06d überleben bewusst), das `SecretKey`-Objekt der Ableitung wird nicht überschrieben | KDoc von `PasswordChars` und `UserListAuthAdapter`, Vertrag; aus dem Code gelesen |
| Festlegung (Voreinstellung 2026-10-30, keine Wahl des Nutzers) | `AUTH_USERS_FILE` als Umgebungsvariable; Listenplatz als 1-basierte Zeilennummer; `ITERATIONS_DEFAULT` 600 000 ohne Leser im Code (kennzeichnet die Setzung des Standards) | Vertrag, KDoc von `StoredSecret` |
| nicht gemessen | Zeitgleichheit von unbekanntem Benutzernamen und falschem Passwort, Dauer von Ableitung und Vergleich, Wirkung einer blockierenden Ableitung auf den Transport, Dateirechte und Übergabe im Betrieb, Kopien des Passworts im Speicher (JDK-Schlüsselobjekt), Verhalten eines echten SQL Servers und des Referenzclients bei abweichend geschriebenen Kennungen | Register unten; Träger `slice-qa-sicherheit`, `slice-bootstrap-konfiguration`, `slice-handshake-login`, `slice-qa-reproduzierbarkeit` |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Dateirechte und Übergabe der Datei im Betrieb ([ADR-0011](../../adr/0011-pruefwertformat.md) §Offen) | `slice-bootstrap-konfiguration` (`open/`) | DoD-Punkt 1 (Konfiguration) nennt beide seit `slice-adr-pruefwertformat`; §6 führt das Risiko |
| Lesen der Datei: UTF-8-BOM am Anfang, keine Normalisierung der Zeilenenden (Review F-11) | `slice-bootstrap-konfiguration` (`open/`) | ein Satz in DoD-Punkt 1 seit dieser Closure |
| Abbildung `SecretChars` nach `PasswordChars` mit Überschreiben des Zwischenfelds; Aufruf des blockierenden Adapters abseits des Transport-Threads; ungemessene Wirkung auf den Transport (Review F-8) | `slice-handshake-login` (`open/`) | DoD-Punkt 2 und ein Risiko in §6 seit dieser Closure |
| Zeitgleichheit, Drosselung, Grenze des Scheinvergleichs bei gemischten Listen | `slice-qa-sicherheit` (`open/`) | DoD-Punkt 2 nennt Zeitgleichheit, Drosselung und Scheinvergleich; die gemischte Liste steht seit dieser Closure dort |
| Kopien des Passworts im Speicher | kein Träger-Slice; Register `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` | offen; `slice-qa-sicherheit` führt die Loginspektion im Log, keine Speichermessung (Frage an den Nutzer) |
| Die fünf entschiedenen Punkte stehen in [ADR-0011](../../adr/0011-pruefwertformat.md) weiter unter „Offen“, die Zeile `SPEC-051` nennt die Einzelheiten „teils offen“ | kein Träger | Folge-ADR nötig, wenn die ADR die Punkte als entschieden führen soll (Re-Evaluierungs-Trigger: Entscheidung des Nutzers über Folge-ADR oder Nachzug von `SPEC-051`); die Frage geht an den Nutzer, „Nutzerentscheidung“ ist kein Träger |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Spec-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Fünf Feststellungen. (1) **Ein Strukturtest mit einheitlicher Eingabe kann die behauptete Auswahl nicht verfehlen.** Der Test „Unbekannter Benutzer führt Ableitung und Vergleich aus“ blieb grün, als die Attrappe der letzte statt der erste Eintrag war (M22), weil alle Einträge dieselbe Form hatten. Das ist eine Prüffrage für den Reviewer und eine Mutation für den Verifier, kein Sensor; Register: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (neu, 1×, verwandt mit `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, eigener Mechanismus; ob beide eine Klasse sind, ist eine Frage an den Nutzer). (2) **Ein Slice, der offene Punkte einer `Accepted`-ADR trägt, setzt sie nicht selbst.** Der Plan nannte zwei Wege (vom Nutzer entschieden oder gemessen, sonst weiter offen), der Implementer nahm einen dritten; erst Review und Verifikation holten die Nutzerentscheidung ein. Der Absatz zu offenen ADR-Punkten in `.claude/commands/implement-slice.md` Schritt 24 gilt nur für Entscheidungs-Slices; Register: `BEO-TDS/offene-punkte-ohne-traeger` (weiteres Auftreten, Stand bleibt verkörpert). Ob der Absatz auf Implementierungs-Slices mit ADR-Punkten ausgedehnt wird, entscheidet der Nutzer. (3) **Das Muster der Aussagen über den Beleg hinaus trat trotz der verkörperten Regel wieder auf** (Überschreib-Aussage, „erster Eintrag“ als belegt, Vertragswiderspruch, RFC-Abschnittsnummern ohne Quelle): ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus`, keine Schärfung; [AGENTS.md](../../../../AGENTS.md) §3.8 war der Maßstab von Review und Verifikation. (4) **Benannte Spec-Lücke:** `SPEC-051` und der Vertrag führen Einzelfälle der Benutzerliste (Hash- und Salzlänge, Kommentarlosigkeit, CRLF, Ziffernlänge, zeichengenauer Vergleich), die das Lastenheft ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)) nicht als gedeckt belegt; Register: `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` (jetzt 2×). Eine `TDS-*`-Zeile legt dieser Slice nicht an, das Lastenheft ändert er nicht; die Zeile `SPEC-051` nennt die Einzelheiten weiter „teils offen“ und ist nachzuführen (Frage an den Nutzer). (5) **`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` erreicht mit diesem Slice 3×** (neu: Grenze des Scheinvergleichs bei gemischten Listen); der Stand wechselt von offen auf geplant, Träger `slice-qa-sicherheit` führt Zeitgleichheit, Drosselung und Scheinvergleich in seiner DoD. Die Welle-Closure von `welle-handshake-login` liest den Eintrag im Lese-Schritt.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-auth-adapter-benutzerliste.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-2, F-4, F-5, F-7; Verifikation M-1, L-1), `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (Review F-1, Verifikation M-1), `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/offene-punkte-ohne-traeger` (Review F-3, F-8, F-11; Verifikation M-2) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (Kennung zeichengenau, ungemessen). Zwei neue Beobachtungen, je mit dieser Evidence-Datei: `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` und `BEO-TDS/strukturtest-ohne-variation-der-eingabe`. Kein neues Auftreten: `BEO-TDS/adapter-festlegungen-ohne-slice` (Stand gestrichen, bleibt es: die Kotlin-Signatur ist geliefert und hat ihren Träger, das Auftreten „Träger-Slice nicht angelegt“ ist ausgeschlossen), `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt` (der Slice trifft keine Wahl, die auf der Annahme über den Betreiber ruht; die Schnittstelle bleibt dafür offen, die Rückfrage beim Betreiber bleibt unbeantwortet), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (`make docs-check` ohne Befund), `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (die Verifikation fuhr `--no-cache-filter test` und behauptete keine Stabilität aus gecachten Läufen) und die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle sechs tragen genau einen Ausgang (siehe §6): zwei entfallen, vier weiter offen (eines mit Träger-Slice, drei im Register).
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-bootstrap-konfiguration`, `slice-handshake-login` und `slice-qa-sicherheit` existieren in `open/` und führen die Punkte in ihrem Inhalt (Tabelle oben). Register: sieben Evidence-Dateien dieses Slice, siehe oben.

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
