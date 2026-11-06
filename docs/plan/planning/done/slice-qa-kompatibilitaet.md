# Slice slice-qa-kompatibilitaet: Kompatibilität: Ende-zu-Ende-Szenarien mit echtem .NET-Client

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-025 · SPEC-032 · SPEC-050

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

**Ziel:** Die freigegebenen Ende-zu-Ende-Szenarien (Anmeldung, SELECT, parametrisierte Abfrage, NULL, Fehler, Abbruch) laufen automatisiert mit einem echten .NET-Client gegen einen Testserver (`KompatibilitaetsServer.kt` im Test-Quelltext, ein Serverprozess mit dem Simulations-Backend; ein ausführbarer Server ist nicht das Produkt, das Produkt ist die JVM-Bibliothek, [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache)) und sind Stufen des E2E-Durchgangs, kein Gate ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Geliefert ist die Szenarienliste ohne Abbruch (Cancel, siehe unten). Aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen gehören dazu die Anmeldemessungen, die dort fehlen: Windows-SSPI (NTLM), TDS 8.0 mit SQL-Authentifizierung, mehrpaketiges LOGIN7, Pooling mit `ResetConnection`. Das Testprojekt samt Restore-Sperre und Make-Target liefert [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md); dieser Slice schreibt die Szenarien dazu. Aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) §Offen kommen `sp_unprepare` unter Pooling, die `SET`-Text-Szenarien (`SchemaOnly`, `KeyInfo`), Transaktionen (Pakettyp `0x0E`) und TDS 8.0 für RPC hinzu. Dieser Slice ist zugleich der Träger der Beobachtung `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (Ausgang aus `welle-protokollcodec`): Der Codec ist für TDS 8.0 und Pooling bisher nur nach MS-TDS festgelegt, nicht am Server dieses Repos gemessen (Closure-Notiz [welle-protokollcodec-results](../done/welle-protokollcodec-results.md)). State und Meldung eines echten SQL Servers gehören nicht hierher, sondern in den Vergleichslauf D von [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Referenzabgleich gegen eine SQL-Server-Instanz (`SPEC-045`, `SPEC-050`) — [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) hat ihn als Vergleichslauf D entschieden; Träger ist [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md), dieser Slice führt keinen Referenzabgleich.
- Lastmessung — gehört zu `slice-qa-performance`, weil sie eine andere Messmethode und ein anderes Gate braucht.
- Abbruch (Cancel) am Referenzclient — der Server beantwortet ATTENTION während einer Anfrage nicht, ein Test mit Folgeabfrage kann nicht bestehen; Führung als Carveout [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md), Folge-Slice [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md). Das Wort „Abbruch“ im Ziel ist damit nicht geliefert.
- Mehrteilige Antworten und Ausgabewerte ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)) am Referenzclient — das Simulations-Backend liefert sie nicht (zwei Anweisungen in einem Text: `Unsupported SQL.`, 50001; ein Ausgabeparameter bekommt keinen Rückgabewert); der Codec baut die Antwortform, kein Backend füllt sie. Lücke 16 der Matrix, Ausgang in §6.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Szenarien nach `SPEC-025`/`SPEC-032` (Liste in [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) §3, Träger-Spalte) laufen im Testprojekt des Gerüsts grün gegen den Testserver (Stufe 5, 13 Tests); `make referenztest-dotnet` bleibt in `make gates`, die neuen Stufen 5 bis 7 laufen über `make kompatibilitaet` und `make e2e`, kein Gate ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Cancel ist nicht Teil ([CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md)). **E2E-Stufe:** Je Szenario des Referenzclients (Anmeldung, SELECT, parametrisierte Abfrage, NULL, Fehler, Typkatalog; die mehrteiligen Antworten mit OUTPUT sind nicht lieferbar, Lücke 16) ist als Stufe im Produkt-E2E ([slice-e2e-geruest](../done/slice-e2e-geruest.md)) mit den Kennungen [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client) deklariert (FA-10 ausdrücklich nicht, Lücke 16) und ruft den Lauf über die Prozessgrenze (Referenzclient gegen den Serverprozess in einem zweiten Container), nicht über `internal`-Zugriff im Modul; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`. Gehört ein Typ- oder Antwortform-Szenario nicht zu den Szenarien dieses Punktes, steht die Lücke für [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) bzw. [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) in §6 mit Ausgang, statt in der Sicht zu fehlen.
- [x] Anmeldemessungen und RPC-Messungen aus dem Ziel (Windows-SSPI, TDS 8.0, mehrpaketiges LOGIN7, Pooling mit `ResetConnection` und `sp_unprepare`, `SET`-Text-Szenarien, Transaktionen, Größen der RPC-Parametertypen `FLTN`, `MONEYN`, `DATETIMN`, `GUID` und der Zuschlag bei `DATETIMEOFFSETN` laut [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Lücke 7) am Testserver dieses Repos gemessen und in der Kompatibilitätsmatrix (Abschnitt 7) und im KDoc nachgetragen (die Accepted-ADRs bleiben unberührt, `AGENTS.md` §3.5), oder je Punkt mit Begründung als nicht lieferbar bzw. nicht gemessen benannt (Windows-SSPI: nicht lieferbar, [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md); mehrpaketiges LOGIN7 und das `RESETCONNECTION`-Bit: nicht gemessen, kein Messweg in diesem Slice); für TDS 8.0 und Pooling auch die Aussage des Codecs (KDoc) von *Festlegung* auf *gemessen* gehoben oder eingeschränkt (TDS 8.0 gehoben; Pooling eingeschränkt: die zweite Nutzung gelingt, das Bit ist nicht beobachtet). Die Closure-Notiz von `welle-protokollcodec` bleibt unverändert. **E2E-Stufe:** Die TDS-8.0-Messung mit TLS am Referenzclient ist als Stufe im Produkt-E2E ([slice-e2e-geruest](../done/slice-e2e-geruest.md)) mit den Kennungen [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) deklariert und ruft den Lauf über die Prozessgrenze (Referenzclient oder Rohbytes-Client gegen den Serverprozess), nicht über `internal`-Zugriff im Modul; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`.
- [x] Die Lücken 3, 4 und 14 der [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md), die heute keinen Träger haben, sind getragen: `xml` als RPC-Parameter (Lücke 3, Abweichung von `SPEC-003`), das Batch-Flag mit mehreren RPCs in einer Nachricht (Lücke 4, [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) §Nicht gemessen) sowie Kennwortwechsel und FeatureExt `0x02` im LOGIN7 (Lücke 14) sind je am Referenzclient gemessen und in der Matrix nachgetragen, oder je Punkt mit Begründung als nicht lieferbar benannt.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. (`harness/README.md` nachgezogen; die Spec-Nachzüge `SPEC-004`, `SPEC-025`, `SPEC-032`, `SPEC-050` und Folgepflicht (2) und (3) von [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) sind kein Teil dieses Slice, Spec-Änderungen sind ein eigener Schritt; benannt in §7 und im Register `BEO-TDS/spec-nachzug-nach-messung-offen`.)
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
| Szenarien im Testprojekt des Gerüsts ([slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md)) | neu | Szenarien Anmeldung, SELECT, RPC, NULL, Fehler, Abbruch nach [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) |
| Make-Fragment des Gerüsts | update | Szenarien in den Testlauf; `GATE_CHECKS +=` je nach Entscheidung im Gerüst-Slice |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Accepted und ein lauffähiger Server beantwortet die Anmeldung, und [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) liegt in `done/` (Gerüst: Testprojekt, Restore-Sperre, Make-Target).

**Zuschnitt A laut [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) (`Accepted`).** Der Wortlaut „ein lauffähiger Server beantwortet die Anmeldung" ist schon erfüllt, trägt die DoD aber nicht: In `main` gibt es keinen Anfragepfad im Produktionsprozess (`Bootstrap.kt` verdrahtet `HandshakeConnectionHandler` ohne `QueryBackend`; außer in Tests existiert keine Implementierung in `src/main`), und der Pfad ab dem ersten Byte (TDS 8.0) ist nicht verdrahtet (KDoc von `runtime/ConnectionWiring.kt`). Die Start-Voraussetzung, die die DoD braucht: [slice-sim-aenderung-integration](../done/welle-simulations-backend/slice-sim-aenderung-integration.md) liegt in `done/` (das Simulations-Backend ist in `main` verdrahtet), und [slice-tds8-verdrahtung](../done/slice-tds8-verdrahtung.md) (TDS-8.0-Verdrahtung) liegt in `done/`. Die ADR wählt Zuschnitt A (zurückstellen bis zu diesen Voraussetzungen; verworfen: B Anmeldung allein, C Voraussetzungen vorziehen), zwei Container (Server und Client getrennt) und SSPI/NTLM als nicht lieferbar mit Grund; die ADR ist `Accepted`, der Start gilt.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Das Clientprofil (`SPEC-004`) ist nicht festgelegt.

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

- Offene Punkte aus [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Simulations-Backend), die an der Messung des Referenzclients hängen: Form C (die Texte, die der Referenzclient selbst stellt, etwa die `SET`-Texte bei `SchemaOnly` und `KeyInfo`) bleibt leer, bis diese Messung sie liefert; ob der Schlüssel als Metadatum zum Client gesendet werden muss (Schlüsselkennzeichen im Spaltenvertrag und in COLMETADATA, ändert Port und Codec) und ob das Identitätsmerkmal bei `KeyInfo` sichtbar ist, ist nicht gemessen. Die Messung liefert den Befund; ein Folge-Schritt dazu entsteht erst mit ihm. — **Ausgang:** weiter offen (die Texte sind gemessen, der Log nennt die `SET`-Texte `SchemaOnly` und `KeyInfo` wörtlich; der Schlüssel als Metadatum und das Identitätsmerkmal bei `KeyInfo` sind nicht beantwortet, die Matrix sagt es; der Folge-Schritt entsteht mit einem Befund, den dieser Slice nicht lieferte).
- Die Pflicht-Matrix B ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), jetzt `Accepted`) ist nicht gemessen; ihr Umfang kann sich bei der Verifikation ändern. — **Ausgang:** entfallen (gemessen: Stufen 5 bis 7 mit 13, 17 und 5 Tests, der Umfang ist benennbar; in der Verifikation unverändert, Mutationsstichprobe rot; was nicht gemessen ist, steht in §7).
- Die TLS-Schleife (`slice-transport-tls-schleife`) ist nur gegen eine JDK-`SSLEngine` im selben Prozess gemessen; am Referenzclient stehen TLS 1.2, andere Suiten als `TLS_AES_256_GCM_SHA384` und TDS 8.0 mit der eigenen Schleife aus. Der Lauf gegen den Referenzclient misst beide Pfade mit der Schleife dieses Repos und trägt nach, was er findet, oder benennt es als nicht lieferbar. — **Ausgang:** weiter offen (TDS 7.4 und TDS 8.0 mit `TLS_AES_256_GCM_SHA384` am Referenzclient belegt; TLS 1.2 und andere Suiten nicht beobachtet; Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
- Setzungen des Listeners und der Startprobe, die nur gegen eine JDK-Gegenstelle laufen ([slice-transport-listener-lebenszyklus](../done/welle-transport-tls/slice-transport-listener-lebenszyklus.md) §6, alle drei ohne Messung und vom Nutzer nicht bestätigt): (a) der Server antwortet nicht auf ein `close_notify` des Clients (`jdk.tls.acknowledgeCloseNotify` beim JDK-Standard); (b) RSA-Schlüssel ab 2048 Bit; (c) die Startprobe prüft weder Vertrauen noch Namen, ob der Referenzclient das geprüfte Zertifikat annimmt, ist nicht gemessen. Der Lauf gegen den Referenzclient beobachtet (a) und (c) und trägt nach, was er findet, oder benennt es als nicht lieferbar. — **Ausgang:** weiter offen ((c) für dieses Zertifikat bestätigt: der Client nimmt es mit Namensprüfung an, ein falscher Name scheitert; (a) und (b) ohne Messung, das Ergebnis zu (a) liegt nicht vor; Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).

- **TDS-8.0-Verdrahtung.** Der Pfad ab dem ersten Byte ist in `main` nicht verdrahtet (KDoc von `runtime/ConnectionWiring.kt`); die Listener-Welle hat ihn nur im Test gegen eine JDK-Gegenstelle belegt. Träger der Verdrahtung ist [slice-tds8-verdrahtung](../done/slice-tds8-verdrahtung.md); bis er in `done/` liegt, sind die TDS-8.0-Messungen der DoD nicht lieferbar. Das Container-Modell (zwei Container, Server und Client getrennt) entscheidet [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) (Teil-Supersede der Container-Sätze von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)). — **Ausgang:** entfallen (der Slice `slice-tds8-verdrahtung` liegt in `done/`; TDS 8.0 mit SQL-Authentifizierung, Batch und RPC am Referenzclient gemessen, Stufe 6).
- Der Start-Trigger in §4 folgt Zuschnitt A laut [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md); die DoD-Anpassung (zwei Container, SSPI/NTLM nicht lieferbar mit Grund) setzt der Planner nach `Accepted`. — **Ausgang:** entfallen (die ADR ist `Accepted`; §4 und DoD sind nachgezogen).
- **Lücke 16: mehrteilige Antworten und Ausgabewerte ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)) am Referenzclient** (Verifikation V-1; [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Lücke 16 verweist auf diesen Abschnitt). Das Simulations-Backend liefert keine mehrteilige Antwort und keinen Rückgabewert für einen Ausgabeparameter; der Codec baut die Antwortform, kein Backend füllt sie. Ein Träger der Backend-Seite (ein Backend, das mehrere Anweisungen und Ausgabewerte bedient) ist **nicht benannt**; ihn zu benennen ist eine Entscheidung des Nutzers, nicht dieses Slice. — **Ausgang:** weiter offen (Register `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger`; Stufe 5 trägt FA-10 deshalb nicht).

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

- **Gegenstand:** entfällt (der Slice lieferte, mit den Einschränkungen unten).
- **Was hat funktioniert:** Die drei Liefer-Punkte liegen vor. Stufe 5 (13 Tests, `SzenarienTests`), Stufe 6 (17 Tests, `AnmeldungTests`) und Stufe 7 (5 Tests, `LueckenTests`) laufen mit dem Referenzclient `Microsoft.Data.SqlClient` in einem zweiten Container gegen den Testserver `KompatibilitaetsServer.kt` (Test-Quelltext, Serverprozess mit dem Simulations-Backend) über die Prozessgrenze; Aufruf über `make kompatibilitaet` und als Stufen von `make e2e`, nicht in `make gates` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Der Testserver ist Testwerkzeug, kein Produkt (Produkt ist die JVM-Bibliothek, [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache); [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) ist `Proposed`). Gemessen sind unter anderem TDS 8.0 mit SQL-Authentifizierung für Batch und RPC, die Namensprüfung mit Gegenprobe, die `SET`-Texte `SchemaOnly` und `KeyInfo`, `BeginTransaction`, die Typgrößen `FLTN`, `MONEYN`, `DATETIMN`, `GUID` und der Zuschlag bei `DATETIMEOFFSETN`, `xml` (50003), mehrere RPCs (50006), Kennwortwechsel und AccessToken (18456). Die Verifikation fuhr die drei Kategorien selbst (13 von 13, 17 von 17, 5 von 5) und bekam per Mutation (Zuschlag `OFFSET_BYTES` bei `DATETIMEOFFSETN` entfernt) genau den DATETIMEOFFSET-Fall rot.
- **Was ging anders als geplant:** (a) Der Review (kein HIGH, ein MEDIUM, fünf LOW) fand das KDoc des Testservers breiter als die Messung (M-1, ein Server mit fester Paketgröße 8000 ließ den Test grün); behoben durch Einschränken in der Fix-Runde `39e231d`. (b) Die Verifikation (kein HIGH, drei MEDIUM V-1 bis V-3, Planner-Sache) nannte den Slice „noch nicht closure-fähig“: der Eintrag zu FA-10 in §6 fehlte, die DoD-Wortlaute wichen ab, der Slice-Text nannte [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) noch `Proposed`. Die Entscheidungen stehen unten. (c) Der Rahmen änderte sich während des Slice: Die Nutzerentscheidung vom 2026-11-01 setzt die JVM-Bibliothek als Produkt und den ausführbaren Server als Testserver (Lastenheft 0.6.0, [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) `Proposed`); die Stufen 5 bis 7 laufen von Beginn an gegen einen Testserver und sind davon nicht berührt, der Slice-Text sagt es jetzt so.
- **Reports:** [Review M-1, L-1 bis L-5, I-1 bis I-6](../../../reviews/2026-10-31-b257f85-slice-qa-kompatibilitaet.md), [Verifikation V-1 bis V-8](../../../reviews/2026-11-01-39e231d-verifikation-slice-qa-kompatibilitaet.md). **Wer was gesehen hat:** (1) Den Stand `b257f85` hat der Reviewer gesehen. (2) Die Fix-Runde `39e231d` hat kein eigenes Review; der Verifier hat M-1, L-1 bis L-4, I-3 und I-4 nachgeprüft, L-5 nicht im Einzelnen. (3) Diese Closure-Notiz, die Entscheidungen und die Register-Einträge hat nur der Planner gesehen; ein Gegenlesen durch einen zweiten Kontext liegt nicht vor.
- **Frischer Lauf der Closure:** `make gates` auf dem Stand vor dieser Notiz: Exit 0, 4 min 55 s (kalt nicht belegt, Last anderer Agenten nicht gemessen); `make docs-check` nach dem Eintragen 0 Befunde. Der Planner hat `make kompatibilitaet` nicht gefahren; die Läufe des Verifiers (112 s, 24 s und 29 s unter Last, Bau jeweils eingeschlossen) sind der Beleg für die 35 Tests, ein vollständiges `make e2e` ist nicht gefahren.

**Zuordnung je Aussage.** *gemessen (Referenzclient, Serverprozess)* heißt ein bestandener Lauf von `Microsoft.Data.SqlClient` 7.1.1 gegen den Testserver in einem zweiten Container, **nicht** gegen einen echten SQL Server; *Festlegung* eine Entscheidung ohne Messung; *nicht gemessen* ohne Beleg.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen (Referenzclient, Serverprozess) | Die Szenarien Anmeldung, Ablehnung, Batch-SELECT, Stern, NULL, acht Typen, parametrisierte Abfrage, Einfügen/Ändern/Löschen und Fehler laufen grün | `SzenarienTests` (13); Matrix 7a |
| gemessen (Referenzclient, Serverprozess) | TDS 8.0 (`Encrypt=Strict`) mit SQL-Authentifizierung für Batch und RPC; falscher Name scheitert in TDS 7.4 und 8.0 mit −2146893019, richtiger gelingt; `SET`-Texte `SchemaOnly` und `KeyInfo` im Log wörtlich; `BeginTransaction` Class 20 mit Schließen; acht RPC-Typfälle gelesen und mit 50002 abgewiesen | `AnmeldungTests` (17); Matrix 7b; Mutation der Verifikation rot |
| gemessen (Referenzclient, Serverprozess) | `xml` gibt 50003 und schließt; mehrere RPCs geben 50006 und schließen (`UpdateBatchSize` 1 gelingt); Kennwortwechsel gibt 18456 (`UNSUPPORTED_PASSWORD_CHANGE`); AccessToken gibt 18456 (`UNSUPPORTED_FEDERATED_AUTHENTICATION`) | `LueckenTests` (5); Matrix 7c |
| gemessen, eingeschränkt | Pooling: die zweite Nutzung gelingt auf derselben Sitzung (Log-Prüfung im Skript); das Batch-Flag ist nur aus der Wirkung geschlossen; Paketgröße 512 nur als Annahme durch den Client | Matrix Lücken 9 und 4, 7b; KDoc |
| Festlegung | Die Ursache der Lücke 16 liegt beim Simulations-Backend (kein Backend liefert mehrteilige Antworten); ein Träger ist nicht benannt | Matrix Lücke 16; `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` |
| nicht gemessen | mehrpaketiges LOGIN7; das `RESETCONNECTION`-Bit; TLS 1.2 und andere Suiten als `TLS_AES_256_GCM_SHA384`; `close_notify` (a) und RSA 2048 Bit (b); `sp_unprepare`; FeatureExt `0x02` am Draht; Paketierung der Antworten; Schlüssel als Metadatum und Identitätsmerkmal bei `KeyInfo`; Cancel; ein echter SQL Server | Abgrenzung der Verifikation; Register |
| nicht lieferbar | Windows-SSPI (NTLM) | [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md); Matrix Lücke 14 (Einschätzung, nicht gemessen) |

**Entscheidungen des Planners zu den Befunden der Verifikation** (Begründung kurz):

- **V-1 (FA-10, Lücke 16):** Eintrag in §6 ergänzt, Ausgang *weiter offen*, Register `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger`. Der Träger der Backend-Seite bleibt offen und ist als offen benannt: einen Träger zu schaffen heißt einen Slice für ein erweitertes Backend anlegen, das ist Sache des Nutzers. Der Rang-Zeiger der Matrix löst damit auf.
- **V-2 (Nachtragsort):** Option (a). Die DoD heißt jetzt „in Matrix und KDoc nachgetragen“; die Accepted-ADRs bleiben unverändert (`AGENTS.md` §3.5). Die Fortschreibung von `SPEC-004` (Freigabepunkte Pooling, `RESETCONNECTION`, Transaktionen, `SET`-Text) und von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (bräuchte eine Folge-ADR) ist als Folge im Register benannt (`BEO-TDS/spec-nachzug-nach-messung-offen`); ein neuer Slice entsteht nicht ohne den Nutzer.
- **V-3 (mehrpaketiges LOGIN7, `RESETCONNECTION`-Bit):** beide *nicht gemessen, kein Messweg in diesem Slice*. Grund: Der Testserver wertet das Bit nicht aus und zählt die Pakete einer LOGIN7-Nachricht nicht; ohne Mitschnitt ist beides am Referenzclient nicht beobachtbar. „Nicht lieferbar“ wäre zu stark, weil ein Messweg denkbar ist; der Weg (Paketzählung im Testserver, Bit im Log) steht als Evidence in `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, ob er machbar ist, ist nicht geprüft.
- **V-4 (Cancel, FA-10):** §1 nennt beide jetzt unter „Ausdrücklich NICHT“ ([CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) für Cancel, Lücke 16 für FA-10); die DoD nimmt FA-10 aus der Kennungenliste von Stufe 5 und sagt es dort.
- **V-5 (Accepted, DoD):** §4 und §6 auf `Accepted` gesetzt; DoD auf „zwei Container, SSPI nicht lieferbar“, „kein Gate“ und „Testserver“ nachgezogen. Die Spec-Nachzüge sind **nicht** in diesem Slice geändert: `SPEC-025` nennt auch Cancel, das die Stufen nicht belegen, und `SPEC-050` meint den Referenzabgleich, der aussteht; der Satz „noch einzurichten“ ist nur zum Teil falsch, die Änderung nicht klein und belegt. Folgepflichten: `SPEC-025`/`SPEC-032` (Stufen laufen, Container-Modell zwei Container, Cancel ausgenommen), Folgepflicht (3) von [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) (Matrix B nicht in `make gates`, falls der Satz in der Spezifikation steht), `SPEC-004`; Träger: der Nutzer als eigener Spec-Schritt, Register `BEO-TDS/spec-nachzug-nach-messung-offen`.
- **V-6 und V-7:** Die Zahlen `264 s` und `18 s` in `harness/README.md` sind die des Implementers, vom Verifier nicht nachgemessen (3.8); sie bleiben mit dieser Einschränkung stehen. `make matrix-zitate` prüft Abschnitt 7 nicht und `tools/kompatibilitaetstest.sh` hat keinen Selbsttest: beides ist ein benannter Befund, kein Auftrag dieses Slice.

**Ausgang je Risiko aus §6:** [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Form C weiter offen · Pflicht-Matrix B entfallen · TLS-Schleife weiter offen · Listener-Setzungen weiter offen · TDS-8.0-Verdrahtung entfallen · Start-Trigger entfallen · Lücke 16 weiter offen. Je genau einer.

**Steering-Loop-Einträge.**

- **Geschärfte Regel / neuer Sensor:** keine verkörpert. Gezählt, nicht verkörpert: `BEO-TDS/aussagen-ueber-messung-hinaus` (weitere Wiederkehr nach der Verkörperung, KDoc und Testnamen stärker als die Assertion) und `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt` (weitere Wiederkehr nach der Verkörperung; der Wortlaut lag hier auf Planner-Seite: Nachträge „in den ADRs“ bei unveränderlichen Accepted-ADRs).
- **Benannte Spec-Lücke:** Die Spezifikation führt die Messungen dieses Slice nicht (`SPEC-004`-Freigabepunkte, `SPEC-025`/`SPEC-032`/`SPEC-050` „noch einzurichten“): `BEO-TDS/spec-nachzug-nach-messung-offen`. Und die FA-10-Zusage hat am Referenzclient keinen Beleg: `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger`.
- **Lehre, ohne Regel:** Ein Rang-Zeiger aus einem Abdeckungsdokument auf einen Slice-Abschnitt („Ausgang: weiter offen“, §6) lief ins Leere, weil der Eintrag im Slice nicht im selben Schritt angelegt wurde; die Verifikation fand es als V-1. Einmal beobachtet, keine Regel.

**Register.** Fortgeschrieben: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (16 Evidence-Dateien), `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt`; neu: `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` (1×), `BEO-TDS/spec-nachzug-nach-messung-offen` (1×). **Über der Schwelle, Entscheidung beim Nutzer:** `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` hat mit diesem Slice 16 Evidence-Dateien; der Planner verkörpert nicht und weist weder `verkörpert` noch `geplant` zu. Die beiden anderen fortgeschriebenen Einträge sind bereits verkörpert.

**Paarungen.** Anker: kein `liegt in`, nichts verkörpert. Folge-Slice: Cancel hat [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md) ([CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md)); die Backend-Seite von FA-10, der Spec-Nachzug und der Rest der TDS-8.0-Beobachtung haben keinen Folge-Slice (Entscheidung beim Nutzer). Register: oben.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` steht noch als Vorlage; der Slice berührt das ganze Repo (`*`), eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
