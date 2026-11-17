# Slice slice-mehrfach-rpc-messluecken: Mehrere RPCs in einer Nachricht — Messlücken am echten SQL Server schließen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Messung zu [slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md) nach Nutzerentscheidung vom 2026-11-17: „ok 2 slices“).

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (`Accepted`; trägt die Setzungen, die dieser Slice misst), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) (Handles von `sp_prepexec`), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) (Referenzclient, Vergleichslauf D)

**Berührte Spec-Stellen:** [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Abschnitt „Mehrere RPCs in einer Nachricht“: Messung und Setzung) · [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Messreihe am Draht). Die Spezifikation ändert dieser Slice nur dort, wo die Messung eine Setzung belegt oder widerlegt, und bei Widerspruch erst nach der Entscheidung des Nutzers (siehe §1).

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** Die Fälle, die [slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md) als „ungemessen“ führt, sind am echten SQL Server (2022 und 2025, gepinnte Instanzen, nur lokal) mit dem Referenzclient gemessen oder tragen die Begründung „nicht lieferbar“; jede Setzung in [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) und [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md), die dadurch einen Beleg bekommt, ist als belegt gekennzeichnet; widerspricht eine Messung einer Setzung, geht die Frage „Code angleichen oder Zielbild anpassen“ an den Nutzer (die Spec ist das Zielbild).

**Gegenstand der Messung** (je Punkt eine eigene Messaussage):

1. Fehler im **ersten** und im **letzten** Aufruf einer Nachricht (gemessen ist bisher die Mitte von drei, W-13) sowie der **Fehler vor dem ersten Ergebnis eines nicht letzten Aufrufs** ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 3, Setzung; `BEO-TDS/fehler-vor-dem-ersten-ergebnis-im-mehrfach-rpc-ungemessen`; Aufnahme durch den Nutzer 2026-11-17).
2. **Ergebnismengen** in einem Aufruf einer Mehrfach-Nachricht (Stufe 6 hat keine; Antwortform, DONEPROC-Bits, Ordinal).
3. **`sp_prepexec` und `sp_execute`** in einer Mehrfach-Nachricht am Referenzclient: sendet er sie überhaupt, und wie antwortet der echte Server ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 7 ist Setzung).
4. Die **Grenzen 4096 Parameter und 1024 Aufrufe** am echten Server ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 5 ist Festlegung ohne Messung).
5. **`ContinueUpdateOnError`**, soweit der Referenzclient es hergibt (Wirkung auf Antwort und Fehlerverlauf).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Abbruch und Frist mitten in der Nachricht am echten Server — schwer messbar (Setzung des Planners, nicht gemessen: der Zeitpunkt mitten in der Nachricht ist mit dem Referenzclient nicht gezielt zu treffen), bleibt Setzung ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 9) und ist am Skript-Backend des Serverprozesses durch Tests gedeckt (`RpcSequenceAbortTest`).
- Andere Clients als `Microsoft.Data.SqlClient` 7.1.1 — Gegenstand von [slice-mssql-jdbc-gegen-testserver](../open/slice-mssql-jdbc-gegen-testserver.md) (nur mssql-jdbc); ODBC, FreeTDS und `sqlcmd` haben keinen Träger und stehen auch dort nicht im Plan.
- Das Trennbyte `0x80` — der Referenzclient sendet es nicht (Setzung und Nutzerbestätigung vom 2026-11-17, [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 6); ohne anderen Sender ist keine Messung herstellbar. Wird es im Lauf sichtbar, steht es im Protokoll, ist aber kein Liefer-Punkt.
- Der Flake `ServerSmokeTest` — Träger ist das Register (`BEO-TDS/flake-ohne-namen-des-roten-testfalls`, 2×); er hat keinen Bezug zu einer Messung am echten Server.
- Die Klarstellung der Entwurfssätze in [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (V-2 der Verifikation) — Entscheidung des Nutzers; der Planner ändert eine `Accepted`-ADR nicht (`AGENTS.md` §3.5).
- Änderungen an `src/` und `testserver/` — nur wenn eine Messung eine Zusage widerlegt **und** der Nutzer „Code angleichen“ entschieden hat; das wäre ein eigener Slice.
- Durchsatz und Last — andere Gegenstände (`make lasttest`, Welle `welle-qualitaet-grundlage`).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **Messreihe Antwortform:** Szenarien der Kategorie `MessungWire` für Fehler im ersten und im letzten Aufruf, für den Fehler vor dem ersten Ergebnis eines nicht letzten Aufrufs und für Ergebnismengen in einem Aufruf (Gegenstand 1 und 2), Einzelmessung je Instanz (2022, 2025), Ablage unter `referenztest-dotnet/Mitschnitt/messung/<Instanz>/`, Messzeilen und Grenzen im Messprotokoll; ein nicht herstellbarer Fall steht mit Begründung als „nicht lieferbar“.
- [ ] **Messreihe Clientform und Grenzen:** `sp_prepexec` und `sp_execute` in einer Mehrfach-Nachricht, `ContinueUpdateOnError`, die Grenzen 4096 und 1024 (Gegenstand 3 bis 5), je mit Messzeile je Instanz oder „nicht lieferbar“ mit Begründung (siehe §6: der Referenzclient muss die Form erzeugen können).
- [ ] **Abgleich der Setzungen:** je Setzung in [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), Kompatibilitätsmatrix und [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (Entscheidungen 3, 5, 7) belegt, widerlegt oder weiter ungemessen; Spec und Matrix führen den Beleg, bei Widerspruch steht die Frage „Code angleichen / Zielbild anpassen“ mit den Messzeilen in §7 und beim Nutzer (kein stilles Anpassen).
- [ ] `make docs-check`, `make flaeche`, `make kopplung-zahlen` und `make gates` Exit 0 (Art der Änderung: Doku/Spec; Tests an Szenarien: `make spec-abdeckung` einmal, bei geänderten Zitaten der Matrix `make matrix-zitate`; `harness/README.md` §Minimal agent workflow). Ändert ein Nutzerentscheid `src/` oder `testserver/`, ist das ein Folge-Slice mit der Zeile „`src/` oder `testserver/`“ derselben Tabelle.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update: Messprotokoll [referenzabgleich](../../../messungen/referenzabgleich.md), `SPEC-030`, Matrix, Kopf von `tools/mk/vergleichslauf-d.mk` (Zahl der Szenarien), falls öffentlicher Vertrag berührt.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben (u. a. `BEO-TDS/fehler-vor-dem-ersten-ergebnis-im-mehrfach-rpc-ungemessen`, `BEO-TDS/messreihe-szenarien-teilen-den-bestand`, `BEO-TDS/messung-am-echten-sql-server-fehlt`); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17, nicht ihr Inhalt im Einzelnen.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Vergleich/MessungWireTests.cs` | update | neue Szenarien neben `rpc-batch-dml`, `-drei`, `-ausgabewert`, `-fehler` (Hilfsfunktion `Einfuegen`); jedes Szenario mit eigener Tabelle, nicht `wire_dml` (`BEO-TDS/messreihe-szenarien-teilen-den-bestand`) |
| `referenztest-dotnet/Mitschnitt/messung/2022/`, `…/2025/` | neu | Ablage je Szenario, vom Lauf erzeugt, nicht von Hand |
| `referenztest-dotnet/Mitschnitt/MessAuswertung.cs`, `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` | update, falls nötig | Auswertung und Belegtests über die Ablage (wie für die vier vorhandenen Szenarien) |
| `docs/messungen/referenzabgleich.md` | update | Abschnitt „Mehrere RPCs in einer Nachricht“ um die Messzeilen (W-14 ff.) und die Grenzen der neuen Messung |
| `spec/spezifikation.md` (`SPEC-030`) | update | belegte Setzungen als gemessen kennzeichnen (AGENTS §3.8); Widerspruch erst nach Nutzerentscheid |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeilen und Zitate zu den neuen Messungen |
| `tools/mk/vergleichslauf-d.mk` | update | Kopfkommentar: Zahl der Szenarien und Laufzeit der Reihe `wire` |
| `docs/plan/adr/0051-…md` | **nicht ändern** | `Accepted` (`AGENTS.md` §3.5); eine Abweichung ist eine Folge-ADR mit `Supersedes`, Entscheidung des Nutzers |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf. Mechanik: `make vergleichslauf-d LAUF=wire` (`wire-2022`, `wire-2025`; `MESSUNG_NUR=<Szenarien>` für eine Teilmenge ohne Ablage), EULA und Bedingungen im Kopf von `tools/mk/vergleichslauf-d.mk`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`). Der Slice ist ein Messslice am echten Server (Docker, EULA des Nutzers vom 2026-11-01 für das gepinnte Developer-Image) und läuft **nicht parallel** zu den Läufen von `make lasttest` und zu Messslices der Welle `welle-qualitaet-grundlage` (Last verfälscht beide Messungen; die Läufe der Reihe `wire` hatten Lastmittel 3 bis 6).

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder die Grenzen (Gegenstand 4) brauchen ein eigenes Werkzeug neben der Reihe `wire`.
- `in-progress` → `open` (blockiert): der Host stellt die Instanzen nicht bereit (Pull, Platz, EULA-Text geändert); oder ein Lauf in der CI wäre nötig, den [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) und das Target ausschließen.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, das Messprotokoll trägt die Zeilen mit Datum, Host, Bedingung und Grenzen, jede Abweichung zwischen Messung und Setzung steht als Frage an den Nutzer in §7, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

- Der Referenzclient (`Microsoft.Data.SqlClient` 7.1.1) erzeugt die Form nicht: Ergebnismengen und Prozeduraufrufe in `UpdateBatchSize`-Gruppen, `sp_prepexec` beim Batch, 1024 Aufrufe oder 4096 Parameter je Nachricht liegen möglicherweise außerhalb dessen, was die Batch-Aktualisierung des Clients sendet; die Form ist vor der Messung nicht belegt. — **Ausgang:** offen bis zur Closure; ein nicht herstellbarer Fall steht als „nicht lieferbar“ mit Begründung, nicht als Setzung mit Beleg.
- Eine Messung widerlegt eine Setzung von [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (zum Beispiel Entscheidung 3 für die Fehler an den Rändern oder Entscheidung 7). — **Ausgang:** offen bis zur Closure; Behandlung nur nach Nutzerentscheid (Code angleichen: Folge-Slice; Zielbild anpassen: Folge-ADR mit `Supersedes`).
- Messreihen teilen Tabellen und Zeilen und beeinflussen sich je Reihenfolge (`BEO-TDS/messreihe-szenarien-teilen-den-bestand`, 1×). — **Ausgang:** offen bis zur Closure; jedes neue Szenario legt seine Tabelle selbst an.
- Einzelmessung je Instanz, ein Host, ein Client in einer Version: die Aussage trägt nicht weiter (AGENTS §3.8). — **Ausgang:** offen bis zur Closure; Grenzen stehen im Messprotokoll.
- Last des Hosts verfälscht die Laufzeitangaben, nicht die Bytes. — **Ausgang:** offen bis zur Closure; Lastmittel wird notiert.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** [slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md) §7 „Weiter offen“ Punkt 2 (Ungemessen), [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Abschnitt „Re-Evaluierungs-Trigger“ (eine Messung, die eine Festlegung widerlegt oder ergänzt), Verifikation [2026-11-17-4c7b069b](../../../reviews/2026-11-17-4c7b069b-verifikation-slice-mehrfach-rpc-unterstuetzen.md) (V-4), Messprotokoll [Mehrere RPCs in einer Nachricht](../../../messungen/referenzabgleich.md#mehrere-rpcs-in-einer-nachricht-messungen-vom-2026-11-17) (W-10 bis W-13). Nutzerentscheidung 2026-11-17: „ok 2 slices“.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `fehler-vor-dem-ersten-ergebnis-im-mehrfach-rpc-ungemessen` (1×, offen; der Fehler im ersten Aufruf berührt ihn), `messreihe-szenarien-teilen-den-bestand` (1×, offen), `messung-am-echten-sql-server-fehlt`, `aussagen-ueber-messung-hinaus` und `festlegung-gilt-nur-fuer-das-clientprofil` (verkörpert beziehungsweise beim Nutzer; die Messung ist die Gegenmaßnahme), `flake-ohne-namen-des-roten-testfalls` (nicht Gegenstand, siehe §1). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
