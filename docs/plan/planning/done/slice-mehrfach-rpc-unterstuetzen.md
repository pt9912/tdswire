# Slice slice-mehrfach-rpc-unterstuetzen: Mehrere RPCs in einer Nachricht unterstützen (Batch-Flag der RPC-Nachricht)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** keine (Vorrat; war `welle-anfrageverarbeitung` zugeordnet (Nutzerentscheid 2026-11-07), seit deren Closure am selben Tag ohne Welle; Start-Trigger unverändert (Priorisierung durch den Nutzer).)

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) (`Accepted`; der Mehrfach-RPC liegt dort auf der Seite ERROR und DONE, bis dieser Slice ihn unterstützt)

**Berührte Spec-Stellen:** SPEC-011 (Parserfehler) · SPEC-030 (RPC-Varianten, Trennlinie des Abschlusses, Zuordnung des Mehrfach-RPC) · SPEC-013 · Abschnitt „RPC im Backend-Vertrag“ (eine Nachricht, mehrere Aufrufe: Reihenfolge der Antworten und Ordinal je Aufruf). Die Spezifikation ändert dieser Slice bei der Umsetzung, nicht der Plan.

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-07.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Server nimmt eine RPC-Nachricht mit mehreren RPCs an (Batch-Flag der RPC-Nachricht, am Referenzclient `SqlDataAdapter` mit `UpdateBatchSize` 2) und beantwortet sie so, wie es der echte SQL Server tut, statt sie als Protokollfehler (`TDS_PROTOCOL_ERROR`, Class 20, Verbindung geschlossen) abzuweisen.

**Eingang (Herkunft).** Am echten Server ist `UpdateBatchSize 2` gültig; der Test `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` (`make e2e` Stufe 6) pinnt das Verhalten dieses Servers, keinen Messbefund. [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) ordnet den Fall der Seite ERROR und DONE zu und nennt die Unterstützung ein eigenes Planungsthema; Nutzerentscheid vom 2026-11-07. Die Antwortform des echten Servers auf eine Nachricht mit mehreren RPCs (Reihenfolge der Abschlüsse, RETURNSTATUS und RETURNVALUE je Aufruf, Ordinal) ist **nicht gemessen** und die erste Aufgabe dieses Slice.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Trennlinie des Abschlusses für Struktur- und Kodierfehler (ERROR und DONE) — Gegenstand von [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md); dieser Slice nimmt nur den Mehrfach-RPC aus der Seite ERROR und DONE heraus, wenn er ihn unterstützt (dann ist der Fall kein Fehler mehr).
- Eine Antwort des echten Servers auf eine ungültig kodierte RPC-Nachricht — kein Messauftrag (Nutzerentscheid 2026-11-07, akzeptiertes Negativ der ADR).
- Weitere Formen außerhalb des Katalogs von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (`sp_prepare`, `sp_unprepare` als RPC, Cursor): andere Gegenstände.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei; Vorschlag, bei der Priorisierung zu bestätigen):

- [x] **Messung:** die Antwort des echten SQL Servers auf eine RPC-Nachricht mit mehreren RPCs (`UpdateBatchSize` 2, beide Instanzen) am Draht aufgezeichnet: Reihenfolge von DONEINPROC, DONEPROC, RETURNSTATUS, RETURNVALUE je Aufruf, Statusbits, Ordinal (Messprotokoll `docs/messungen/referenzabgleich.md`); der Erzeuger der Messung liegt im Repo (Muster `referenztest-dotnet/Vergleich/MessungWireTests.cs`, Mitschnitt unter `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/`), kein Wegwerf-Prototyp.
- [x] **Spezifikation und Bau:** Parser und Dispatch nehmen mehrere RPCs an und rufen das Backend je Aufruf; die Spezifikation führt die gemessene Antwortform; die Zuordnung des Mehrfach-RPC zur Seite ERROR und DONE in `SPEC-030` entfällt.
- [x] **Nachweis:** der Test `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` wird durch einen Test ersetzt, der `UpdateBatchSize` 2 gelingen lässt (Begründung im Commit, kein stilles Löschen); Matrix (Lücke 4) und `make e2e` auf den neuen Stand.

Nachweis-Pflichten für alle Punkte:

- [x] Jede Aussage steht an einem Test, einem Mitschnitt oder ist als Festlegung gekennzeichnet.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (Backend-Vertrag: ein RPC je Aufruf).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` (Parser, `RpcParameterReader.kt`) und `protocol/dispatch/` (`RpcBinder.kt`, `RequestLoop.kt`) | update | mehrere RPCs einer Nachricht lesen und je Aufruf an das Backend geben (Ansatz nach der Messung) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/` | update | Antwortform je Aufruf nach der Messung |
| `spec/spezifikation.md` (`SPEC-011`, `SPEC-030`, Backend-Vertrag), `docs/plan/kompatibilitaetsmatrix.md` | update | gemessene Lesart, Streichung der Zuordnung des Mehrfach-RPC |
| `referenztest-dotnet/` (Lücke 4 der Matrix), `tools/e2e.sh` | update | Test mit `UpdateBatchSize` 2 gelingt; Messfall für das Messprotokoll |

Weitere berührte Dateien (Stand des Diffs):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` (`RpcParser.kt`, `RpcParameterReader.kt`, `RpcCatalog.kt`, `RpcRequest.kt`), `protocol/wire/ByteReader.kt`, `protocol/response/` (`ResponseRun.kt`, `ResponseSequencer.kt`, `RpcTrailer.kt`), `protocol/dispatch/` (`RpcBinder.kt`, `RpcParameters.kt`, `RequestLoop.kt`), `domain/QueryRequest.kt`, `port/QueryBackend.kt` | update | Trennbyte lesen, Parameterbudget je Nachricht, `MORE\|0x80` am DONEPROC, Folge von Aufrufen; KDoc der öffentlichen Fläche (dieselbe `requestId` nacheinander) |
| `src/test/kotlin/` (`RpcCallSequenceTest`, `RpcSequenceDispatchTest`, `RpcSequenceAbortTest`) | neu | Parser, Dispatch, Fehler, Frist, Abbruch |
| `docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md` | update | Backend-Vertrag und Antwortformen (`AGENTS.md` §5 Regel 5) |
| `docs/messungen/referenzabgleich.md`, `referenztest-dotnet/Vergleich/MessungWireTests.cs`, `referenztest-dotnet/Mitschnitt/` (`MessAuswertung.cs`, Belege, `messung/{2022,2025}/rpc-batch-*.messung`), `tools/mk/vergleichslauf-d.mk` | update, neu | Messung W-10 bis W-13, Erzeuger im Repo |
| `docs/plan/e2e-abdeckung.md`, `tools/e2e.sh`, `tools/kompatibilitaetstest.sh`, `tools/log-muster.sh`, `tools/log-muster-selbsttest.sh`, `docs/plan/spec-abdeckung.md` | update | Stufe 6 und ihre Log-Prüfung, Abdeckungssicht, Matrix der Spezifikation |

Zusätzliche `SPEC-*` der Tests: `SPEC-001`, `SPEC-009`, `SPEC-027`, `SPEC-029`, `SPEC-045`, `SPEC-048`. Der Nutzer hat am 2026-11-17 bestätigt: nur `0xFF` trennt, die Parametergrenze 4096 gilt je Nachricht.

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Reihenfolge: erst die Messung (sie bestimmt Antwortform und Ordinal), dann Spezifikation, dann Bau.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Priorisierung durch den Nutzer und ein Verantwortlicher im Kopf; die Messung am echten Server braucht die EULA-Bestätigung des Nutzers für den Lauf (`make vergleichslauf-d`, nur lokal).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Messung zeigt eine Antwortform, die den Sequenzierer über den Mehrfach-RPC hinaus umbaut; Schnitt: Messung als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): der Backend-Port trägt keine Aufrufreihe (ein `QueryRequest` je Nachricht); der Nutzer entscheidet über die Form des Ports.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, `make e2e` grün mit gelingendem `UpdateBatchSize` 2, Review-Report liegt vor, Closure-Notiz mit Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Antwortform ungemessen.** Ohne Messung bliebe jede Reihenfolge Setzung (`AGENTS.md` §3.8). — **Ausgang:** entfallen, soweit gemessen: Trennbyte, Antwortform je Aufruf, Ordinal und der Fehler im mittleren von drei Aufrufen sind am echten SQL Server (2022 und 2025) gemessen, Messprotokoll W-10 bis W-13, [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidungen 1 bis 3; was ungemessen bleibt, steht in §7 („Weiter offen“) und als Setzung in `SPEC-030`.
- **Port-Form.** Ein Aufruf je `QueryRequest` kann die Aufrufreihe einer Nachricht nicht abbilden, wenn das Backend sie als Einheit sehen soll. — **Ausgang:** entfallen als Blocker, als Setzung entschieden: [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 8 (ein `QueryRequest` je Aufruf, nacheinander, dieselbe `requestId` und Frist, Signatur unverändert; vom Nutzer am 2026-11-17 angenommen); ein Backend, das die Aufrufreihe als Einheit sehen will, ist nicht bedient (kein Verbraucher, Trigger der ADR).

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

Geschlossen 2026-11-17 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-mehrfach-rpc-unterstuetzen`, Spitze `db85d955` (Basis `b3f4d7aa`; der Verifikationsbericht nennt `4c7b069b`, danach nur Berichte); danach der Merge von `main` (`f668fe7a`) und diese Closure (nur Doku, Kommentare und Text; keine Logik). Prüfung: Review [2026-11-17-b898101e](../../../reviews/2026-11-17-b898101e-slice-mehrfach-rpc-unterstuetzen.md) (F-1 bis F-5 MEDIUM, F-6 bis F-9 LOW, F-10 bis F-13 INFO), Re-Review [2026-11-17-13ef2e4e](../../../reviews/2026-11-17-13ef2e4e-slice-mehrfach-rpc-unterstuetzen-re-review.md) (N-1 bis N-3 LOW, N-4 und N-5 INFO; F-8, F-11, F-12 unverändert), Verifikation [2026-11-17-4c7b069b](../../../reviews/2026-11-17-4c7b069b-verifikation-slice-mehrfach-rpc-unterstuetzen.md) (V-1 MEDIUM, V-2 und V-3 LOW, V-4 INFO).

**Gegenstand.** Der Server nimmt eine RPC-Nachricht mit mehreren RPCs an und beantwortet sie wie der echte SQL Server in den gemessenen Fällen: Trennbyte `0xFF`, je Aufruf die Antwort der Form RPC, DONEPROC `MORE|0x80` außer am letzten Aufruf, das Ordinal der RETURNVALUE zählt je Aufruf von vorn, ein Fehler beendet nur seinen Aufruf ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md), `Accepted`, ersetzt [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) Entscheidung 3 teilweise; [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)). `UpdateBatchSize` 2 gelingt am Serverprozess.

**Beleg je DoD-Punkt (Bestätigung durch den Verifier).**

- Punkt 1, Messung: erfüllt, mit benannter Grenze. [Messprotokoll](../../../messungen/referenzabgleich.md#mehrere-rpcs-in-einer-nachricht-messungen-vom-2026-11-17) W-10 bis W-13; Mitschnitte `rpc-batch-{dml,drei,ausgabewert,fehler}.messung` je Instanz (2022, 2025) unter `referenztest-dotnet/Mitschnitt/messung/`; Erzeuger im Repo (`MessungWireTests.cs`, `MessAuswertung.cs`), die Belegtests über die Ablage liefen grün in `make gates`. Die Aufzeichnung am echten Server hat die Verifikation nicht wiederholt (EULA, nur lokal).
- Punkt 2, Spezifikation und Bau: erfüllt. `SPEC-030` trennt Messung und Setzung, `SPEC-011` und `SPEC-013` gezogen, die Zuordnung des Mehrfach-RPC zur Seite ERROR und DONE entfällt; neue Tests `RpcCallSequenceTest`, `RpcSequenceDispatchTest`, `RpcSequenceAbortTest`; `make clean-build` ohne roten Testfall.
- Punkt 3, Nachweis: erfüllt. Der Test der Ablehnung ist ersetzt (Begründung in `fec43d39`); `Mehrere_RPCs_in_einer_Nachricht_gelingen_mit_UpdateBatchSize_2` ist bestanden. Stufe 6 (`make e2e`, 2026-11-17, ein Lauf, ein Host, 16 s, Kategorie `Luecken` 7 von 7): Messzeilen „UpdateBatchSize 2: Update meldet 3; Verbindung danach Open“ und „UpdateBatchSize 1: Update meldet 2; Verbindung danach Open“; die Log-Prüfung „kein request_failed mit TDS_PROTOCOL_ERROR“ grün. Gemessen sind zwei Aufrufe plus einer (drei Zeilen), ohne Ergebnismenge, ohne Ausgabewert und ohne Fehler am Serverprozess (Verifikation V-4). Die Texte „Lauf steht aus“ in Matrix (Lücke 4, Abschnitt 7c, Stand-Satz), `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md` und dem Kommentar in `tools/kompatibilitaetstest.sh` sind auf diese Messzeilen gezogen (Text, keine Logik).
- Nachweise: Aussagen an Test, Mitschnitt oder als Festlegung gekennzeichnet (`SPEC-030`, [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) trennen W-10 bis W-13 von den Setzungen); `make gates` Exit 0 (Verifier); beide Reviews liegen vor, F-8 bleibt LOW, F-11 und F-12 unverändert (INFO); Doku (`docs/user/einbettung-und-betrieb.md`, `docs/user/referenz-konfiguration.md`, KDoc von `QueryRequest` und `QueryBackend.execute`) trägt die wiederholte `requestId`; keine Änderung der Signatur, daher kein Eintrag in `SPEC-059`.

**Zuordnung je Aussage.**

- *Gemessen* (echter Server, 2022 und 2025, Einzelmessungen je Szenario, `Microsoft.Data.SqlClient` 7.1.1, `sp_executesql` mit `INSERT`, höchstens drei Aufrufe je Nachricht): Trennbyte `0xFF` ohne zweites ALL_HEADERS (W-10); DONEPROC des ersten Aufrufs `MORE|0x80`, des letzten ohne Bits (W-11); Ordinal der RETURNVALUE je Aufruf von vorn (W-12); Fehler im mittleren von drei Aufrufen: DONEINPROC `MORE|ERROR`, RETURNSTATUS 2627, DONEPROC `MORE|0x80` ohne Fehlerbit, der dritte Aufruf läuft (W-13; die Clientseite von W-13 stammt aus der Beobachtungszeile des Laufs, nicht aus der Ablage).
- *Am Serverprozess gemessen* (Stufe 6, ein Lauf, ein Host): die zwei Messzeilen oben, kein Protokollfehler im Log.
- *Handvektor:* keiner; die Orakel der Tests sind die Bytes der Messung.
- *Setzung am Server dieser Bibliothek durch Tests belegt, nicht am echten Server gemessen:* Abbruch und Frist mitten in der Nachricht (`RpcSequenceAbortTest`), eine Frist je Nachricht (`RpcSequenceDispatchTest`).
- *Festlegung ohne Messung* (Nutzerbestätigung 2026-11-17 zu den ersten beiden): nur `0xFF` trennt; 4096 Parameter je Nachricht und 1024 Aufrufe; `sp_prepexec` und `sp_execute` in einer Nachricht ergeben `SPEC-013`; ein Fehler, der die Verbindung schließt, beendet die Nachricht; Fehler vor dem ersten Ergebnis eines nicht letzten Aufrufs (DONEPROC mit Fehlerbit und `MORE|0x80`); ein `QueryRequest` je Aufruf (Port-Form, [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 8, vom Nutzer 2026-11-17 angenommen).

**Was ging anders als geplant.** (a) Der Plan (§4) vermutete die Rückführung „Antwortform ändert den Sequenzierer über den Mehrfach-RPC hinaus“ und „Port trägt keine Aufrufreihe“; keine trat ein, die Port-Form blieb als Setzung, die Signatur unverändert. (b) Review F-1: die angenommene [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) sagte nach dem Bau das Gegenteil von Spec und Code; die Folge-ADR ist [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (nicht [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) überschrieben, `AGENTS.md` §3.5). (c) Review F-2: die Plan-Tabelle nannte neun Dateigruppen und vier `SPEC-*` nicht; nachgezogen. (d) Review F-3, F-5, F-6: drei Zusagen ohne Test (Frist je Nachricht, Abbruch und Frist mitten in der Nachricht, gemessene Fehlerform), mit Tests und Mutation nachgebessert. (e) Die Messung des Fehlerszenarios zeigte im Gesamtlauf keinen Fehler, weil `dml-rpc` die verletzte Zeile gelöscht hatte; das Szenario hat jetzt eine eigene Tabelle (Register, siehe unten). (f) `ServerSmokeTest` war im Lauf des Implementers einmal rot; weder im Re-Review noch beim Verifier reproduziert, der Fall ist nicht benannt.

**Gates.** `make gates` Exit 0 auf `4c7b069b` (Verifier, 21 s; die Klassen `werkzeug` und `stempel` hatten einen Beleg und wurden übersprungen, `tests` und `immer` liefen; .NET-Referenztests 171 bestanden). Den frischen Lauf der Tests und von detekt belegt `make clean-build` des Verifiers (Exit 0, 605 s, 1448 `PASSED`-Zeilen, keine rote und keine übersprungene), `make e2e` Exit 0 (9 von 9 Stufen, 417 s). Der Lauf auf dem Stand dieser Closure (nach dem Merge von `main`, nur Doku, Kommentare und Text) steht in der Übergabe des Planners, nicht in dieser Notiz. Stempel nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md); die Stempel kennt die CI nicht, sie läuft voll ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md)).

**Weiter offen.**

1. **Nutzer, [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) trägt Entwurfssätze (V-2, LOW):** die ADR ist `Accepted` und führt noch „Annahme durch den Nutzer steht aus“ (Kopf von „Entscheidung“), „Bis zur Annahme bleibt Befund F-1 offen“ (Empfehlung des Architects 4) und „Bis dahin steht [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) unverändert … Befund F-1 bleibt offen“ (Konsequenzen). Der Planner ändert die ADR nicht (`AGENTS.md` §3.5). Frage an den Nutzer: Klarstellung ohne inhaltliche Änderung oder Hinnehmen.
2. **Ungemessen** (benannte Lücken, kein eigener Slice, Setzung in `SPEC-030` und [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md)): Fehler im ersten oder letzten Aufruf (am echten Server und am Referenzclient; gemessen ist die Mitte von drei); Fehler vor dem ersten Ergebnis eines nicht letzten Aufrufs; Aufrufe mit Ergebnismengen in einer Nachricht (Stufe 6 hat keine); `sp_prepexec` und `sp_execute` in einer Nachricht am Referenzclient (am echten Server nicht gemessen, bei `UpdateBatchSize` mit vorbereiteten Befehlen); das Trennbyte `0x80`; mehr als drei Aufrufe je Nachricht am echten Server und die Grenzen 4096 und 1024; Abbruch und Frist mitten in der Nachricht am Serverprozess und am echten Server; ein anderer Client als `Microsoft.Data.SqlClient` 7.1.1 (mssql-jdbc, ODBC, FreeTDS, `sqlcmd`) und andere Versionen; `ContinueUpdateOnError`; Mutationen je Zusage (die zu F-3 hat der Re-Review gefahren, die zu F-5 nicht). Kein Messauftrag (akzeptiertes Negativ der ADR, Trigger dort); ein Folge-Slice ist nicht angelegt, ohne Kennung.
3. **Flake `ServerSmokeTest`** (Re-Review N-4): einmal rot im Lauf des Implementers, nicht reproduziert, Testfall unbenannt; Träger ist das Register (`BEO-TDS/flake-ohne-namen-des-roten-testfalls`), kein Fix im Diff.

**Register.** Fortgeschrieben, kein Zähler gesetzt (je eine Datei `evidence/slice-mehrfach-rpc-unterstuetzen.md`): `BEO-TDS/aussagen-ueber-messung-hinaus` (94 Dateien; Review F-4, F-6, F-7, Re-Review N-2, Verifikation V-3, V-4), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (49; Review F-1, Re-Review N-1), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (26; Review F-2), `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (12; Re-Review N-3), `BEO-TDS/flake-ohne-namen-des-roten-testfalls` (2; Re-Review N-4), `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (12; Re-Review N-1, Verifikation V-2). Neu angelegt, 1×, offen: `BEO-TDS/messreihe-szenarien-teilen-den-bestand` (das Fehlerszenario sah im Gesamtlauf keinen Fehler, weil ein anderes Szenario die Zeile gelöscht hatte) und `BEO-TDS/fehler-vor-dem-ersten-ergebnis-im-mehrfach-rpc-ungemessen` (die Setzung DONEPROC mit Fehlerbit für einen Fehler ohne Vorereignis, gegen die gemessene Form nach dem Beginn einer Anweisung; Review F-6). Die Beobachtung `BEO-TDS/parserfehler-einer-rpc-nachricht-endet-in-done-statt-doneproc` ist `gestrichen` ([ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md)), ihr Zeiger auf diesen Slice zeigt nach `done/`.

**Lese-Schritt (Repo ohne Wellen-Betrieb, diese Closure löst ihn aus).** Gelesen, was in der Ablage 3× erreicht hat und von diesem Slice berührt wurde: `aussagen-ueber-messung-hinaus` (94, verkörpert in `AGENTS.md` §3.8 und `.harness/skills/reviewer.md`), `fortschreibung-laesst-zweite-fundstelle-stehen` (49, verkörpert im Reviewer-Skill und in Schritt 20c von `implement-slice`), `plan-tabelle-nennt-neue-datei-nicht` (26, verkörpert im Reviewer-Skill), `abdeckungsmatrix-veraltet-ohne-sensor` (12, verkörpert als Handlung, Schritt 24 von `implement-slice`), `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (12, Stand `offen`, Entscheidung beim Nutzer). Feststellung: keine erreicht mit diesem Slice die Schwelle neu; alle fünf sind wiedergekehrt, Reviewer oder Verifier fingen jedes Auftreten, der Implementer hatte die Tabelle, die zweite Fundstelle und die Abdeckungssicht nicht von sich aus nachgezogen. Retirement-Check der verankerten Klassen: wiedergekehrt, keine Lockerung. Die neuen Beobachtungen (1×) und `flake-ohne-namen-des-roten-testfalls` (2×) liegen unter der Schwelle.

**Drei Paarungen** (Repo ohne Wellen-Betrieb, hier geprüft nach dem Move). *Anker* — diese Notiz verkörpert nichts und trägt kein Feld `liegt in`; nichts zu paaren. *Folge-Slice* — kein Folge-Slice benannt, der einen Punkt annehmen müsste; die offenen Punkte oben haben Träger Nutzer ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md)-Klarstellung) oder das Register (Flake, neue Beobachtungen). *Register* — acht Dateien `evidence/slice-mehrfach-rpc-unterstuetzen.md` in sechs vorhandenen und zwei neuen Verzeichnissen, jede mit Zeiger auf diesen Slice in `done/`; `make docs-check` Exit 0.

**Lerneintrag (Steering Loop).**

- **Benannte Spec-Lücke** (kein `liegt in`, nichts verkörpert): der Mehrfach-RPC ist am echten Server nur für `sp_executesql` mit `INSERT`, höchstens drei Aufrufe und den Fehler in der Mitte gemessen; die ungemessenen Fälle (Ergebnismengen, Fehler im ersten oder letzten Aufruf, `0x80`, `sp_prepexec`, Grenzen, anderer Client) stehen als Setzung oder „nicht gemessen“ in `SPEC-030` und [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md).
- **Geschärft, keine neue Regel:** (i) Ein Test, der eine Setzung pinnt, steht in der Matrix nicht unter einer Zeile „gemessen“, und sein KDoc nennt nur die Messpunkte, deren Eingabe er trägt (Review F-6, Re-Review N-2; `AGENTS.md` §3.8, `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert; Retirement-Check: wiedergekehrt, die Regel bleibt). (ii) Eine Aussage über einen noch ausstehenden Lauf („steht aus“) hat ein Verfallsdatum und stand an vier Stellen (Matrix, `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md`, Skript-Kommentar), die der Verifier nach dem Lauf einzeln nachziehen musste; dieselbe Klasse wie `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`. Der Befund trägt keinen neuen Sensor, er zählt als Wiederauftreten. (iii) Eine Messreihe, deren Szenarien eine Tabelle teilen, zeigt je Reihenfolge und Teilmenge ein anderes Ergebnis: das Fehlerszenario hat jetzt eine eigene Tabelle und prüft den Fehler im Beleg; die übrigen Szenarien teilen `wire_dml` weiter (Register, 1×).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt Bibliothek, Spezifikation und Testwerkzeug. `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste, 2026-11-07). Treffer: `BEO-TDS/parserfehler-einer-rpc-nachricht-endet-in-done-statt-doneproc` (gestrichen durch [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md); der Gegenstand dieses Slice ist sein Eingang) und `BEO-TDS/aussagen-ueber-messung-hinaus` (die Antwortform braucht die Messung vor dem Bau).

**Modus-Begründungsblock.** Alle berührten Sub-Areas GF.
