# Slice slice-adr-fehler-attention: ADR-0007 entscheiden: Fehlerbehandlung und ATTENTION-Semantik

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0007](../../adr/0007-fehler-und-attention.md)

**Berührte Spec-Stellen:** SPEC-044 · SPEC-047 · SPEC-048 · SPEC-049 · SPEC-029 · SPEC-031 · SPEC-011 · SPEC-012 · SPEC-016 · ARC-015 · [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) · [TDS-FA-07.a](../../../../spec/spezifikation.md#tds-fa-07a--attention-und-cleanup)

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

**Ziel:** [ADR-0007](../../adr/0007-fehler-und-attention.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Fehlerklassen, die eine Wiederverwendung der Verbindung erlauben; ob nach `TDS_SHUTDOWN` und nach einem internen Fehler (`SPEC-048`) die Verbindung stets endet, wie `SPEC-044` und `SPEC-049` es vorsehen, oder die Recovery-Regel Ausnahmen kennt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und liegt nicht im Repo (Beleg: `git ls-files` führt keine Datei `spike*`, `run.sh`, `mutate.sh`, `dotnet/`, `*.csproj` oder `*.cs`, und `git grep` findet in `src` weder `AsynchronousSocketChannel` noch `runInterruptible`). Dass er nach der Entscheidung gelöscht wäre, ist nicht belegt; siehe §7.
- [x] [ADR-0007](../../adr/0007-fehler-und-attention.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [x] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst, soweit die ADR entscheidet: `SPEC-011`, `SPEC-012`, `SPEC-029`, `SPEC-031`, `SPEC-048`, `SPEC-049` und `TDS-FA-06.a` tragen sie (Spec-Historie 2026-10-30). `SPEC-016` bleibt bewusst bei „Fehler/Disconnect“, bis die Behandlung von Cancel und Timeout als Fehler entschieden ist; `SPEC-044`, `SPEC-047` und `TDS-FA-07.a` nennt die Spec-Historie nicht, sie sind nicht als nachgezogen belegt. `ARC-015` bleibt nach Hard Rule 3.4 frei von ADR-Bezügen.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8). Reports: [Review](../../../reviews/2026-10-30-f4b3b87-adr-0004-0006-0007.md), [Re-Review](../../../reviews/2026-10-30-46be900-re-review-adr-0004-0006-0007-0011.md) und [Verifikation](../../../reviews/2026-10-30-46be900-verifikation-adr-0004-0006-0007-0010-0011.md).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. Berührt ist die Spezifikation (`SPEC-011`, `SPEC-012`, `SPEC-029`, `SPEC-031`, `SPEC-048`, `SPEC-049`, `TDS-FA-06.a`); ein Vertrag unter `contracts/` ist für diese Entscheidung nicht berührt.
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
| `docs/plan/adr/0007-fehler-und-attention.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** für [ADR-0007](../../adr/0007-fehler-und-attention.md) (Messung vom 2026-10-30). Spike per Dockerfile ohne Mount, `Microsoft.Data.SqlClient` 7.1.1 auf `dotnet/sdk:10.0.403`, `Encrypt=false`, `Pooling=false` (außer R18/R19), eine Verbindung je Szenario. Zwei Messobjekte, in der ADR getrennt ausgewiesen: **R**, der echte SQL Server 2022 (Digest `sha256:cf8f0ab2…`, 16.00.4305) hinter einem mitschneidenden TCP-Proxy im Client-Container, und **T**, ein in C# geschriebener Testserver mit skriptbaren Antworten. R sagt, was ein echter Server sendet; T sagt, wie der Referenzclient auf eine bestimmte Antwort reagiert. Es lief kein Server dieses Repos. Der Spike-Code liegt nur im Scratchpad und ist nicht im Repo; Roh-Logs sind nicht abgelegt, die ADR trägt die Zusammenfassung. Aus den Messungen ergab sich:

- R (ein Lauf je Zeile, R zweimal gefahren, Verhalten gleich): Fehler der Class ≤ 19 mit ERROR und DONE[ERR] lassen die Verbindung offen und die Folgeabfrage antwortet richtig; ab Class 20 sendet der Server weitere Fehler (2745, 596) und schließt. Statement-abbrechende Fehler (8134) lassen den Rest des Batches laufen, Batch-abbrechende (208, `THROW`, `XACT_ABORT`) nicht. Eine offene Transaktion überlebt einen Fehler ohne `XACT_ABORT`. Cancel und Timeout beantwortet der Server mit zwei Nachrichten (abgebrochene Antwort mit DONE[ERR], dann DONE[ATTN]); die Verbindung bleibt offen. Im gemessenen Deadlock wiederholte weder Server noch Client.
- T (ein Lauf je Zeile): Der Client schließt ab Class 20 selbst; fehlendes DONE, DONE ohne ATTN-Bit als Bestätigung oder eine ignorierte ATTENTION lassen den Client bis zum Timeout hängen (plus rund 5 s Wartezeit auf die Bestätigung bei ignoriertem ATTENTION ohne Antwort), danach ist die Verbindung geschlossen.
- Nicht gemessen: TDS 8.0 und TLS, Client-Disconnect mitten in der Anfrage, ATTENTION-Race im Moment des letzten DONE, Pooling mit vom Server geschlossener Verbindung, andere Clients, ein Server dieses Repos.
- Abhängigkeit von [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Accepted`; die Punkte bleiben dort offen): ATTENTION in jedem Anfragezustand, Sitzungszustand nach Fehler, `RESETCONNECTION`, Zeitpunkt des Schließens bei `TDS_SHUTDOWN`. Die Stellen sind in der ADR benannt; ihre Träger stehen in §7.
- **Entscheidung des Nutzers (2026-10-30):** enge Form von Option B mit A als Grundregel. Die Verbindung bleibt offen nur bei Class ≤ 19 mit vollständigem ERROR und DONE[ERR] sowie nach Cancel oder Timeout mit gesendetem DONE[ATTN]; bei Class ≥ 20, `TDS_INTERNAL_ERROR` (`SPEC-048`) und `TDS_SHUTDOWN` (`SPEC-049`) schließt der Server nach ERROR und DONE[ERR], bei nicht mehr sicher abschließbarem Strom (`SPEC-031`) schließt er (ob davor ERROR und DONE gesendet werden, ist offen). Gemessen sind nur Class 20 und 25; `SPEC-048`/`SPEC-049` sind Setzung. [ADR-0007](../../adr/0007-fehler-und-attention.md) trägt Messung, Entscheidung und die offenen Punkte (Cancel und Timeout als Fehler, Fehlersemantik im Strom, ATTENTION im Handshake, Ende des Handshakes nach abgelehntem Login, Class der eigenen Fehlercodes, Inhalt der Cancel-Antwort); `TDS-FA-06.a`, `SPEC-029`, `SPEC-048` und `SPEC-049` sind nachgezogen.

- **Nachführung (Entscheidung des Nutzers, 2026-10-30):** (1) Nach abgelehntem Login (ERROR 18456, Class 14, DONE mit Fehlerstatus) schließt der Server; ein weiteres LOGIN7 wird nicht angenommen: Ausnahme zur Class-Regel, Setzung, nicht gemessen. (2) Eigene Anfragefehler tragen Class 16 (Verbindung bleibt offen), `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` Class 20 (Server schließt): Setzung, nicht an Referenzclients gemessen; die Zuordnung jedes Codes legt der Fehler-Mapping-Slice fest. (3) Nicht abschließbarer Strom: Schließen ohne ERROR und DONE. Damit sind Handshake-Ende, Class der eigenen Codes und Verhalten bei nicht sendbarem Abschluss aus den offenen Punkten von [ADR-0007](../../adr/0007-fehler-und-attention.md) genommen; offen bleiben Cancel und Timeout als Fehler, ATTENTION im Handshake (Eigentümer [ADR-0004](../../adr/0004-session-zustandsautomat.md)), Inhalt der Cancel-Antwort, `RESETCONNECTION`, Sitzungszustand nach Fehler, Zeitpunkt des Schließens nach `TDS_SHUTDOWN` und die Abbildung statement- und batch-abbrechender Fehler (Zuständigkeit Backend-/Mapping-Slice).
- Die ADR ist `Accepted` (nach Review, Re-Review und Verifikation, und nach der Annahme von [ADR-0004](../../adr/0004-session-zustandsautomat.md)).

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-session-zustand` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die Fehler-/Cancel-Matrix zeigt Desynchronisation, die eine Folge-ADR klären muss.

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

- Die Fehler-/Cancel-Matrix zeigt Desynchronisation, die eine Folge-ADR klären muss. — **Ausgang:** entfallen: Die Desynchronisation zeigte sich nur in den absichtlich fehlerhaften Antworten des Testservers (fehlendes DONE oder fehlende ATTN-Bestätigung: der Referenzclient wartet bis zum Timeout und schließt dann selbst, T, S10, S12, S14, S16, S18); die Regel der ADR schließt sie aus, indem sie Abschluss und Bestätigung verlangt und im Zweifel schließt. Eine Folge-ADR für die Matrix selbst ist nicht nötig; die offenen Punkte der ADR haben Träger (§7).
- [ADR-0007](../../adr/0007-fehler-und-attention.md) hing an der Annahme von [ADR-0004](../../adr/0004-session-zustandsautomat.md) (Abhängigkeitszirkel, Re-Review 4-5). — **Ausgang:** entfallen: [ADR-0004](../../adr/0004-session-zustandsautomat.md) ist vor dieser ADR `Accepted`; die vier Punkte, die beide ADRs offen führen, haben Träger (§7).
- ATTENTION im Handshake hatte keinen Entscheider: `slice-attention-cancel-timeout` schloss ihn aus, kein anderer Slice führte ihn. — **Ausgang:** eingetreten: Der Punkt steht seit dieser Closure in der DoD von [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (Nutzerentscheidung vor dem Start); Beobachtung [BEO-TDS/offene-punkte-ohne-traeger](../observations/BEO-TDS/offene-punkte-ohne-traeger/observation.md).
- Setzungen des Nutzers über die Messung hinaus: Class 21 bis 24, `TDS_INTERNAL_ERROR` und `TDS_SHUTDOWN` mit Verbindungsende, Schließen nach abgelehntem Login, Class 16 und 20 der eigenen Codes, Schließen ohne ERROR und DONE bei nicht abschließbarem Strom; am echten SQL Server sind nur Class 20 und 25 (über `RAISERROR … WITH LOG` als `sa`) und die Fehler der Class ≤ 19 gemessen. — **Ausgang:** weiter offen: → [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (geplant: [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md); dessen DoD nennt diese Fälle nicht einzeln, siehe §7).
- TDS 8.0 und TLS sind für Fehler- und ATTENTION-Framing abgeleitet, nicht gemessen; Pooling mit vom Server geschlossener Verbindung ist nicht gemessen. — **Ausgang:** weiter offen: → [BEO-TDS/tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md) (geplant: [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md)).
- Es gibt keinen Server dieses Repos; alle Aussagen über „was der Server tun muss“ sind aus dem echten SQL Server (R) und dem Testserver (T) abgeleitet. — **Ausgang:** weiter offen: → [slice-fehler-zustandsuebergang](../open/slice-fehler-zustandsuebergang.md) und [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (Rohbytes-Tests am Produkt) und [slice-qa-synchronitaet](../open/slice-qa-synchronitaet.md) (Matrix am Referenzclient).

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

- **Was hat funktioniert:** Der Spike trennte zwei Messobjekte (R: ein echter SQL Server 2022 hinter einem mitschneidenden Proxy; T: ein Testserver mit skriptbaren Antworten, an dem der Referenzclient `Microsoft.Data.SqlClient` 7.1.1 reagiert) und wies sie in der ADR getrennt aus: R sagt, was ein echter Server sendet, T, wie der Client auf eine bestimmte Antwort reagiert. Der Nutzer wählte die enge Form von Option B mit A als Grundregel (im Zweifel schließt der Server). Review, Re-Review und Verifikation liefen je in frischem Kontext, [ADR-0007](../../adr/0007-fehler-und-attention.md) ist danach `Accepted`, nach der Annahme von [ADR-0004](../../adr/0004-session-zustandsautomat.md) (Entscheidung der Optionen: Nutzer, 2026-10-30).
- **Was ging anders als geplant:** Der Review fand fünf MEDIUM-Findings zu dieser ADR (7-1 bis 7-5), darunter den Wertebereich der Class-Schwelle über die gemessenen Class 20 und 25 hinaus (7-4) und Verschärfungen in `SPEC-048` und `SPEC-049` ohne Setzungsmarke (7-5); der Re-Review fand die Regel für Codes ohne Einzelzuordnung nicht eindeutig (7-8), die Verifikation `SPEC-012` nicht unter „Schärft“ (V-3) und eine Rückfallregel, die `SPEC-030` widersprach (V-4). Alles ist in der ADR behoben bzw. als Setzung gekennzeichnet. Der Slice lag bis zu dieser Closure in `open/`, obwohl an ihm gearbeitet wurde (Verifikation V-25, Planner); `Verantwortlich` ist mit der Closure gesetzt und die Übergänge `open → next → in-progress` sind als reine Moves nachgeholt, die Arbeit selbst lief davor. Der Spike-Code liegt nicht im Repo und ist nach der Entscheidung nicht gelöscht: im Scratchpad der Sitzung außerhalb des Repos liegen weiterhin Spike-Verzeichnisse; die DoD-Zeile sagt deshalb nur „nicht im Repo“.
- **Träger der offenen Punkte von [ADR-0007](../../adr/0007-fehler-und-attention.md)** (jeder Slice ist eine Datei im Planning-Lifecycle; was dort in DoD oder Risiko steht, ist geprüft):
  - Cancel und Timeout als Fehler (`TDS_TIMEOUT`, `SPEC-016`): [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (DoD-Punkt 3, §6) und [slice-fehler-mapping](../open/slice-fehler-mapping.md) §6.
  - Inhalt der Cancel-Antwort (eine oder zwei Nachrichten): [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (DoD-Punkt 1, §6).
  - Abbildung statement- und batch-abbrechender Fehler im Strom: [slice-backend-sequenzierer](../open/slice-backend-sequenzierer.md) §6 und [slice-fehler-mapping](../open/slice-fehler-mapping.md) §6.
  - Zuordnung jedes weiteren eigenen Fehlercodes zu seiner Class: [slice-fehler-mapping](../open/slice-fehler-mapping.md) (DoD-Punkt 1, §6).
  - ATTENTION im Handshake und Antwort auf unzulässige Übergänge: [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (DoD-Punkt 2, mit der Closure von `slice-adr-session-zustand` ergänzt), [slice-fehler-zustandsuebergang](../open/slice-fehler-zustandsuebergang.md) (DoD-Punkt 3) und [slice-handshake-login](../open/slice-handshake-login.md) (DoD-Punkt 1).
  - `RESETCONNECTION` und Sitzungszustand nach einem Fehler: [slice-fehler-zustandsuebergang](../open/slice-fehler-zustandsuebergang.md) (DoD-Punkt 3).
  - Zeitpunkt des Schließens nach `TDS_SHUTDOWN` und nach DONE mit ATTENTION-Status im Beenden: [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md) (DoD-Punkt 2, §6).
  - Nicht gemessen (TDS 8.0, Pooling, Class 21 bis 24, Server dieses Repos): [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) und [slice-qa-reproduzierbarkeit](../in-progress/slice-qa-reproduzierbarkeit.md); deren DoD nennt Class 21 bis 24, Herunterfahren und ATTENTION im Leerlauf nicht einzeln (Frage an den Nutzer, ob sie dort ergänzt werden).
  - Folge-ADR nötig: nur, wenn einer dieser Slices die Class-Schwelle oder die Grundregel A anders entscheidet, als die `Accepted`-ADR es festschreibt (Re-Evaluierungs-Trigger der ADR); die offenen Punkte selbst sind keine Abweichung.
- **Steering-Loop-Eintrag:** Spec-Lücke benannt: `SPEC-016` nennt „Fehler/Disconnect“ für `TDS_TIMEOUT`, und unter den Konstanten (`SPEC-047`, `SPEC-033`, `SPEC-034`) steht keine serverseitige Anfrage-Frist; ob Cancel und Timeout als Fehler gelten, ist offen und wird in `slice-attention-cancel-timeout` entschieden (kein `liegt in`, weil in einer versionierten Spec und nicht an einem Zielort geführt). Gezählt, nicht verkörpert: [BEO-TDS/offene-punkte-ohne-traeger](../observations/BEO-TDS/offene-punkte-ohne-traeger/observation.md).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-adr-fehler-attention.md` ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md), [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md), [BEO-TDS/tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md) und [BEO-TDS/offene-punkte-ohne-traeger](../observations/BEO-TDS/offene-punkte-ohne-traeger/observation.md). Die Zähler folgen aus den Dateien. `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` ist in diesem Slice nicht belegt aufgetreten.
- **Folge-Slices:** keine neuen; die Träger oben existieren als Dateien.
- **Risiken aus §6:** Zwei entfallen, eines eingetreten (DoD-Punkt in `slice-attention-cancel-timeout`), drei weiter offen mit Träger, siehe §6.
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
