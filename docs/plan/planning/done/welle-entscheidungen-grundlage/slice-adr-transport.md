# Slice slice-adr-transport: ADR-0003 entscheiden: Transportframework und Netzwerk-Laufzeit

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-046 · ARC-003 · ARC-008 · ARC-013 · SPEC-027

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

**Ziel:** [ADR-0003](../../adr/0003-transportframework.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Framework und Pufferstrategie.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [x] [ADR-0003](../../adr/0003-transportframework.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [x] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
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
| `docs/plan/adr/0003-transportframework.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md`, `spec/architecture.md` | prüfen | Stellen aus `Schärft:`; blieben unverändert, die Sicht bleibt technologieneutral |
| `contracts/security-profile.md` | update | JVM-Flag für den Handshake im TDS-7.4-Pfad |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** (Spike per Dockerfile ohne Mount, Prototypen in Kotlin auf JDK 21 mit `-Xmx256m`, Server mit 4 CPUs und 4 Threads; echter Client `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 im selben Docker-Netzwerk; Zahlen aus dem Bericht des Spike-Agenten, bislang nicht wiederholt):

| Messung | A Netty 4.1.118 | B Java NIO (Selector, `SSLEngine`) | C Coroutines 1.10.1 (`AsynchronousSocketChannel`, `SSLEngine`) |
|---|---|---|---|
| Echter Client, TDS 7.4 (`Encrypt=true`): Server loggt LOGIN7 `0x74000004` | ja | ja | ja |
| Echter Client, TDS 8.0 (`Encrypt=Strict`): Server loggt LOGIN7 `0x08000000` | ja | ja | ja |
| Fragmentierung (byteweise, 1 bis 40 und 1 bis 3000 Byte), 1000 Wiederholungen je Pfad | 1000/1000 | 1000/1000 | 1000/1000 |
| 100 defekte Verbindungen (falsche Längen, abgeschnitten, Abbruch im Handshake) | alle geschlossen, Prozess lebt | alle geschlossen, Prozess lebt | alle geschlossen, Prozess lebt |
| 100 MiB an einen mit 1 MiB/s lesenden Client: Heap / Direct (Maximum) | 10 MB / 4 MB | 4 MB / unter 1 MB | 4 MB / unter 1 MB |
| Kontrolle A ohne `isWritable`-Prüfung (nach 25 s abgebrochen) | Direct 128 MB, wächst mit der Antwort | nicht gebaut | nicht gebaut |
| 200 gleichzeitige Verbindungen × 64 KiB, Runde 3: Verbindungen/s; MiB/s | 474; 59,3 | 672; 84,0 | 660; 82,5 |
| Zeilen Prototyp (ohne Test-Client) | 120 | 157 | 126 |
| Direkte Abhängigkeiten (ohne Kotlin-Stdlib) | 1 (`netty-handler`, 6 weitere Netty-Jars) | 0 | 1 (`kotlinx-coroutines-core`) |
| Code für die TDS-Kapselung des TLS-Handshakes | ca. 32 Zeilen, drei zusätzliche Handler | 8 Zeilen plus geteilter `Stripper` (19 Zeilen) | 9 Zeilen plus `Stripper` |

Befunde:

- Mit TLS 1.3 sendet der JDK-Server noch im Handshake ein NewSessionTicket; es landet im TDS-Paket vom Typ `0x12`, der Client liest danach rohes TLS und bricht mit „Unrecognized record version“ ab. Das JVM-Flag `-Djdk.tls.server.newSessionTicketCount=0` löst das in allen drei Optionen. Mit erzwungenem TLS 1.2 tritt der Fehler nicht auf. Eine Lösung ohne Flag ist nicht gebaut.
- Rückstau hält den Speicher in allen drei Optionen begrenzt, aber nur, wenn der Schreiber ihn beachtet: bei A über `isWritable`, bei B über High-Water-Mark 256 KiB und OP_WRITE, bei C über das suspendierende Schreiben.
- Die Unterschiede im Durchsatz stammen aus einem einzelnen Lauf, Runde 1 enthält JIT-Anlauf.
- Die Prototypen schalten den TLS-Handshake am Ergebnis `FINISHED` der `SSLEngine` auf rohes TLS um (B und C je ca. 8 bis 9 Zeilen); bei Netty erledigt der `SslHandler` das, dafür sind drei zusätzliche Handler und ein Sniffer nötig, der nach dem ersten Byte die Pipeline umsteckt. Für `AsynchronousSocketChannel` braucht C Adapter mit `suspendCancellableCoroutine` (ca. 12 Zeilen).
- Wiederholung durch die Verifikation (nur Option C): beide Pfade mit dem echten Client, Fragmentierung 1000/1000 je Pfad, 100 defekte Verbindungen (Prozess lebt), 100 MiB bei langsamem Leser (Heap höchstens 4 MB), 200 Verbindungen Runde 3: 609 Verbindungen/s und 76,2 MiB/s.

Nicht gemessen: Latenz-Perzentile, Last über 200 Verbindungen, CPU, Live-Heap bei 200 Verbindungen (der Spitzenwert 167 bis 180 MB ist unbereinigter Müll), ob die Fragmentierung beim Server als kleine Lese-Stücke ankam, Neuverhandlung und Wiederaufnahme, Idle-Timeouts, Langzeitlauf. Bei A fehlt der Fall, dass nach dem PRELOGIN schon TLS-Bytes im Lesepuffer liegen. Warum der .NET-Client nach LOGIN7 bei A mit einem anderen Fehler endet als bei B und C, ist nicht geklärt (die Dummy-Antwort ist gleich). Der Spike deckt Framing, Handshake und Streaming ab, nicht TDS-Tokens, ATTENTION oder MARS.


## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-clientprofil` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Spike mit fragmentierten Paketen und TLS ist ohne Lasttest nicht aussagekräftig.

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

- Der Spike mit fragmentierten Paketen und TLS ist ohne Lasttest nicht aussagekräftig. — **Ausgang:** entfallen: Der Spike enthielt Fragmentierung, TLS in beiden Pfaden, defekte Verbindungen, Rückstau bei 100 MiB und 200 gleichzeitige Verbindungen; die Grenzen der Messung stehen in der ADR unter „Nicht gemessen“.

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

- **Was hat funktioniert:** Ein Agent baute die drei Prototypen und den Test-Client aus Dockerfiles ohne Mount; die Verifikation wiederholte Option C und bestätigte die Zahlen im Rahmen des Rauschens.
- **Was ging anders als geplant:** Die Messung dauerte gut 30 Minuten. Der Review fand sechs Aussagen über die Messung hinaus oder ohne Auswahlkriterium (Fragmentierung, Rückstau, Umschaltpunkt, Auswahlgrund, Folgepflicht für `ARC-008`, Bibliotheksnamen in der Sicht); sie wurden in der ADR begrenzt, und die Änderungen an `ARC-003` und `ARC-013` wurden zurückgenommen, weil die Sicht technologieneutral bleibt.
- **Steering-Loop-Eintrag:** Aussagen über die Messung hinaus, gezählt 3×, geplant für die Closure von `welle-entscheidungen-grundlage`; Auslöser: `BEO-TDS/aussagen-ueber-messung-hinaus` (slice-adr-clientprofil, slice-adr-tls, slice-adr-transport — 3×).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-adr-transport.md` in `BEO-TDS/aussagen-ueber-messung-hinaus/` ergänzt, Zähler steht damit bei 3x; `state.md` trägt den Ausgang „geplant“. `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten/` ist nicht wieder aufgetreten (Zähler bleibt bei 1x).
- **Folge-Slices:** `slice-adr-session-zustand` und `slice-adr-backend-streaming` — liegen als Dateien in `open/` und messen auf dem gewählten Transport.
- **Risiken aus §6:** Das eine Risiko ist entfallen, siehe §6.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von `welle-entscheidungen-grundlage`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
