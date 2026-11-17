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

**Verantwortlich:** pt9912

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

- [x] **Messreihe Antwortform:** Szenarien der Kategorie `MessungWire` für Fehler im ersten und im letzten Aufruf, für den Fehler vor dem ersten Ergebnis eines nicht letzten Aufrufs und für Ergebnismengen in einem Aufruf (Gegenstand 1 und 2), Einzelmessung je Instanz (2022, 2025), Ablage unter `referenztest-dotnet/Mitschnitt/messung/<Instanz>/`, Messzeilen und Grenzen im Messprotokoll; ein nicht herstellbarer Fall steht mit Begründung als „nicht lieferbar“.
- [x] **Messreihe Clientform und Grenzen:** `sp_prepexec` und `sp_execute` in einer Mehrfach-Nachricht, `ContinueUpdateOnError`, die Grenzen 4096 und 1024 (Gegenstand 3 bis 5), je mit Messzeile je Instanz oder „nicht lieferbar“ mit Begründung (siehe §6: der Referenzclient muss die Form erzeugen können). *Erfüllt mit Einschränkung (Verifikation): die Grenze ist nur nach unten begrenzt, angenommen sind 1025 Aufrufe und 4206 Parameter.*
- [x] **Abgleich der Setzungen:** je Setzung in [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), Kompatibilitätsmatrix und [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (Entscheidungen 3, 5, 7) belegt, widerlegt oder weiter ungemessen; Spec und Matrix führen den Beleg, bei Widerspruch steht die Frage „Code angleichen / Zielbild anpassen“ mit den Messzeilen in §7 und beim Nutzer (kein stilles Anpassen). *Erfüllt, soweit der Planner es hakt: die Fragen stehen in §7, die drei Entscheidungen des Nutzers stehen aus; der Verifier hatte den Punkt „teilweise“ befunden, weil §7 noch leer war.*
- [x] `make docs-check`, `make flaeche`, `make kopplung-zahlen` und `make gates` Exit 0 (Art der Änderung: Doku/Spec; Tests an Szenarien: `make spec-abdeckung` einmal, bei geänderten Zitaten der Matrix `make matrix-zitate`; `harness/README.md` §Minimal agent workflow). Ändert ein Nutzerentscheid `src/` oder `testserver/`, ist das ein Folge-Slice mit der Zeile „`src/` oder `testserver/`“ derselben Tabelle.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [x] Doku-Update: Messprotokoll [referenzabgleich](../../../messungen/referenzabgleich.md), `SPEC-030`, Matrix, Kopf von `tools/mk/vergleichslauf-d.mk` (Zahl der Szenarien), falls öffentlicher Vertrag berührt.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben (u. a. `BEO-TDS/fehler-vor-dem-ersten-ergebnis-im-mehrfach-rpc-ungemessen`, `BEO-TDS/messreihe-szenarien-teilen-den-bestand`, `BEO-TDS/messung-am-echten-sql-server-fehlt`); keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

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
| `spec/spezifikation.md` (`SPEC-003`) | update (nicht im ersten Plan) | Begründung der Setzung zu `ntext` an die Messung gezogen (Referenzclient sendet die Deklarationsliste als `ntext`); Wortlaut der Setzung unverändert, Entscheidung beim Nutzer |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeilen und Zitate zu den neuen Messungen |
| `tools/mk/vergleichslauf-d.mk` | update | Kopfkommentar: Zahl der Szenarien und Laufzeit der Reihe `wire` |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung` nach den Belegtests (Tests geändert) |
| `tools/vergleichslauf-d.sh` | unverändert | der Lauf `wire` ersetzt die Ablage je Instanz als Ganzes; die 26 älteren Dateien stellt `git checkout` wieder her (nicht neu aufgenommen), die zehn neuen kommen hinzu |
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

- Der Referenzclient (`Microsoft.Data.SqlClient` 7.1.1) erzeugt die Form nicht: Ergebnismengen und Prozeduraufrufe in `UpdateBatchSize`-Gruppen, `sp_prepexec` beim Batch, 1024 Aufrufe oder 4096 Parameter je Nachricht liegen möglicherweise außerhalb dessen, was die Batch-Aktualisierung des Clients sendet; die Form ist vor der Messung nicht belegt. — **Ausgang:** teilweise eingetreten (`sp_prepexec` von sich aus nicht lieferbar, W-19; die übrigen Formen herstellbar).
- Eine Messung widerlegt eine Setzung von [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (zum Beispiel Entscheidung 3 für die Fehler an den Rändern oder Entscheidung 7). — **Ausgang:** eingetreten für Entscheidung 7 (W-20) und Entscheidung 5 (W-21, W-22); Behandlung nur nach Nutzerentscheid (§7); Entscheidung 3 ist belegt.
- Messreihen teilen Tabellen und Zeilen und beeinflussen sich je Reihenfolge (`BEO-TDS/messreihe-szenarien-teilen-den-bestand`, 1×). — **Ausgang:** entfallen; jedes neue Szenario legt seine Tabelle selbst an.
- Einzelmessung je Instanz, ein Host, ein Client in einer Version: die Aussage trägt nicht weiter (AGENTS §3.8). — **Ausgang:** weiter offen; Grenzen stehen im Messprotokoll (§7 Ungemessen).
- Last des Hosts verfälscht die Laufzeitangaben, nicht die Bytes. — **Ausgang:** eingetreten, benannt; Lastmittel 9,29 vor und 7,06 nach dem Lauf, die Bytes sind nicht betroffen.
- Größe: drei Liefer-Punkte. — **Ausgang:** entfallen; drei Liefer-Punkte, keine Rückführung.

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

Geschlossen 2026-11-17 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-mehrfach-rpc-messluecken`; Messung und Tests `a32c8618`, Review-Nachbesserung `e934b9af`, Namen und Kommentare nach dem Re-Review `93cde5fb`, Verifikationsbericht `53d1abdd` (der Bericht nennt als Stand `93cde5fb`); danach der Merge von `main` (`5c738cb2`, ein Konflikt: das Änderungsprotokoll von `spec/spezifikation.md`, beide Zeilen erhalten), die Textkorrektur V-1 (`bb2d8c53`) und diese Closure (nur Doku und Text; keine Logik, kein `src/`, kein `testserver/`). Prüfung: Review [2026-11-17-slice-mehrfach-rpc-messluecken](../../../reviews/2026-11-17-slice-mehrfach-rpc-messluecken.md) (F-1 und F-2 MEDIUM, F-3 bis F-6 LOW, F-7 bis F-10 INFO), Re-Review [2026-11-17-slice-mehrfach-rpc-messluecken-re-review](../../../reviews/2026-11-17-slice-mehrfach-rpc-messluecken-re-review.md) (N-1 MEDIUM, N-2 LOW, N-3 INFO), Verifikation [2026-11-17-93cde5fb-verifikation-slice-mehrfach-rpc-messluecken](../../../reviews/2026-11-17-93cde5fb-verifikation-slice-mehrfach-rpc-messluecken.md) (V-1 und V-2 LOW, V-3 und V-4 INFO; kein HIGH, kein MEDIUM).

**Gegenstand.** Die Fälle, die [slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md) als ungemessen führte, sind am echten SQL Server (2022 und 2025, Einzelmessungen vom 2026-11-17) gemessen oder als „nicht lieferbar“ begründet: zehn neue Szenarien der Kategorie `MessungWire`, Ablage je Instanz unter `referenztest-dotnet/Mitschnitt/messung/`, Messzeilen W-14 bis W-22 im [Messprotokoll](../../../messungen/referenzabgleich.md#messlücken-zu-mehreren-rpcs-in-einer-nachricht-messungen-vom-2026-11-17). Zwei Setzungen von [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) und eine von [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) widersprechen der Messung oder sind durch sie berührt; das Zielbild ist **nicht** geändert, die Fragen an den Nutzer stehen unten.

**Beleg je DoD-Punkt (Bestätigung durch den Verifier).**

- Messreihe Antwortform: erfüllt (Verifikation, 15 Punkte an den Bytes nachgerechnet, beide Instanzen). W-14 (Fehler im ersten und im letzten Aufruf), W-16 (Fehler vor dem ersten Ergebnis), W-17 und W-18 (Ergebnismengen, Ordinal).
- Messreihe Clientform und Grenzen: erfüllt mit Einschränkung. W-15 (`ContinueUpdateOnError`), W-19 und W-20 (`sp_prepexec`, `sp_execute`), W-21 (1024 und 1025 Aufrufe), W-22 (4086 und 4206 Parameter). Einschränkung: die Grenze ist nur nach unten begrenzt, angenommen sind 1025 Aufrufe und 4206 Parameter; eine Grenze des echten Servers ist nicht gefunden. Als „nicht lieferbar“ begründet: `sp_prepexec` von sich aus (der Referenzclient sendet es in den drei geprüften Formen nicht, W-19), das Trennbyte `0x80`, Abbruch und Frist mitten in der Nachricht, eine Grenze oberhalb der Messung.
- Abgleich der Setzungen: je Setzung belegt, widerlegt oder weiter ungemessen geführt (`SPEC-030`, Matrix, Protokoll; der Wortlaut der Setzungen blieb stehen); die Fragen „Code angleichen / Zielbild anpassen“ mit den Messzeilen stehen unten. Der Verifier hatte diesen Punkt als teilweise erfüllt befunden, weil genau diese Fragen in §7 fehlten; mit diesem Abschnitt sind sie geschrieben, der Haken sagt nur das, nicht, dass der Nutzer entschieden hat.
- Gates, Review, Doku-Update, Closure-Notiz, Register, Risiken, Paarungen: siehe unten.

**Zuordnung je Aussage.**

- *Gemessen* (echter Server 2022 und 2025, Einzelmessung je Szenario und Instanz, `Microsoft.Data.SqlClient` 7.1.1, `INSERT`, `SELECT`, `DECLARE`, Prozedur `wire_proc_ergebnis`): Fehler im ersten und im letzten Aufruf (2627; DONEPROC `MORE|0x80` ohne Fehlerbit am ersten, ohne Bits am letzten, W-14); `ContinueUpdateOnError` ändert in einem Szenario weder Antwort noch Anfrage (W-15); Übersetzungsfehler 208 vor dem ersten Ergebnis: DONEPROC `MORE|ERROR|0x80` (`0x0083`), Laufzeitfehler 8134 nach COLMETADATA: Fehlerbit am DONEINPROC, DONEPROC ohne Fehlerbit (W-16); Ergebnismengen und DML gemischt (W-17); Ordinal der RETURNVALUE 2 in beiden Aufrufen, per Prozedurname 0 (W-18); `sp_prepexec` und `sp_execute` als Prozedur per Name werden angenommen, auch mit dem Handle aus Aufruf 1 derselben Nachricht (W-20); 1024 und 1025 Aufrufe sowie 4086 und 4206 Parameter werden angenommen (W-21, W-22).
- *Gemessen, negativ:* der Referenzclient sendet `sp_prepexec` und `sp_execute` in den drei geprüften Formen nicht von sich aus (W-19); das Trennbyte `0x80` steht in keiner der 36 Ablagen je Instanz.
- *Setzung durch die Messung belegt:* [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 3 für den Übersetzungsfehler vor dem ersten Ergebnis (DONEPROC `0x0083`). Weiter Setzung: der Fehler, der die Verbindung schließt.
- *Festlegung, der die Messung widerspricht (beim Nutzer):* Entscheidung 7 (Ablehnung von `sp_prepexec` und `sp_execute` in einer Mehrfach-Nachricht) und Entscheidung 5 (höchstens 1024 Aufrufe und 4096 Parameter je Nachricht), siehe die Fragen.
- *Setzung, nicht gemessen:* `ntext` als RPC-Parameter bleibt `TDS_UNSUPPORTED_TYPE` ([SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)); der Referenzclient sendet bei 1360 und 1400 Parametern je Aufruf die Deklarationsliste als `ntext` (W-22), ob der Server dieser Bibliothek eine solche Nachricht annimmt, ist nicht gemessen.
- *Handvektor:* keiner; die Orakel der Belegtests sind die Bytes der Ablage.

**Offene Entscheidungen des Nutzers (nicht entschieden; die Spec ist das Zielbild, die Empfehlung ist die des Planners).** Folge-Slices und Folge-ADRs sind nicht angelegt.

1. **`sp_prepexec` und `sp_execute` in einer Mehrfach-Nachricht** ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 7 gegen W-20). Setzung: der Server dieser Bibliothek lehnt sie ab (`SPEC-013`). Messung: der echte Server nimmt sie an, auch `sp_execute` mit dem Handle von `sp_prepexec` aus derselben Nachricht (Handle 4 in `rpc-batch-vorbereitet/08`); der Referenzclient sendet sie nur über `CommandType.StoredProcedure` mit dem Namen der Prozedur, nicht von sich aus (W-19). Optionen: **(A) Zielbild anpassen**: eine Folge-ADR (`Supersedes` für Entscheidung 7) bestätigt die Ablehnung als bewusste Abweichung vom echten Server, mit der Begründung, dass kein gemessener Sender sie in einer Mehrfach-Nachricht von sich aus erzeugt. **(B) Code angleichen**: ein Folge-Slice nimmt beide an und verwaltet die Handles nach [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) (ein Handle aus Aufruf 1 gilt für Aufruf 2 derselben Nachricht). Empfehlung: **A**, weil der Aufwand (Handle-Verwaltung innerhalb einer Nachricht) keinem gemessenen Sender dient; wäre ein solcher Client gefunden, wäre B der Weg. Möglicher Träger bei A: eine Folge-ADR ohne Slice (Nachziehen der Nutzer-Doku und der KDoc); bei B: ein Slice „sp-prepexec-in-mehrfach-nachricht-annehmen“.
2. **Grenzen je Nachricht** ([ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 5 gegen W-21 und W-22). Festlegung ohne Messung: höchstens 1024 Aufrufe und 4096 Parameter. Messung: der echte Server nimmt 1025 Aufrufe und 4206 Parameter an; eine Grenze ist nicht gefunden. Optionen: **(A) Zielbild anpassen**: die Schutzgrenze bleibt, eine Folge-ADR sagt ausdrücklich, dass sie strenger ist als der echte Server (Schutz der Rechenzeit und des Speichers je Nachricht, nicht Kompatibilität). **(B) Code angleichen**: die Grenzen anheben, die Schutzwirkung neu begründen und messen. Empfehlung: **A**; das entspricht der Nutzerbestätigung vom 2026-11-17 („4096 je Nachricht bleibt“). Die Grenzen hängen an Frage 3: Nachrichten mit 1360 Parametern je Aufruf erreichen die Zählgrenze nicht, wenn der Typ `ntext` vorher abgelehnt wird.
3. **`ntext` als RPC-Parameter** ([SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), Setzung: `text`, `ntext` und `image` bleiben `TDS_UNSUPPORTED_TYPE`). Messung: der Referenzclient sendet die Deklarationsliste bei 1360 und 1400 Parametern je Aufruf als `ntext` (W-22); die Schwelle ist nicht gemessen; ob der Server dieser Bibliothek solche Aufrufe annimmt, ist nicht gemessen. Optionen: **(A) Code angleichen**: `ntext` als RPC-Parameter lesen (mindestens als Deklarationsliste von `sp_executesql`). **(B) Zielbild anpassen**: `ntext` bleibt unsupported, als benannte Ausnahme (Parameterlisten dieser Größe scheitern an diesem Server). Empfehlung: erst messen, dann entscheiden; ohne Messung neigt der Planner zu **A** für die Deklarationsliste, weil der Referenzclient die Form ohne Zutun des Nutzers erzeugt, sobald die Parameterliste groß ist. Möglicher Träger: ein Slice „ntext-deklarationsliste-am-server-messen“ (Aufruf mit 1360 Parametern am Serverprozess, danach A oder B).

**Folgen der Entscheidungen, nicht ausgeführt:** nach der Entscheidung sind die Nutzer-Doku (`docs/user/einbettung-und-betrieb.md`, Zeile zu `sp_prepexec` und `sp_execute` in einer Nachricht; `docs/user/referenz-konfiguration.md`, Zeile „mehrere RPCs in einer Nachricht“) und die KDoc von `RpcParser` und `RequestLoop` nachzuziehen. Sie sagen heute den Ist-Zustand des Codes (Review F-8, `AGENTS.md` §5 Regel 5) und sind in dieser Closure nicht geändert.

**Was ging anders als geplant.** (a) Der Plan (§6) nannte als Risiko, dass der Referenzclient die Form nicht erzeugt; eingetreten für `sp_prepexec` von sich aus (W-19), die übrigen Formen sind herstellbar (`SqlBatch`, Prozedur per Name). (b) Die Messung berührt `SPEC-003`, das der Plan nicht nannte (§3 jetzt mit der Zeile): der Referenzclient sendet `ntext`; der Wortlaut der Setzung ist unverändert, nur die Begründung ist an die Messung gezogen (Verifikation, zulässig nach `AGENTS.md` §3.8). (c) Review F-1 und F-2 (MEDIUM): `SPEC-003` und `SPEC-030` sagten mehr als der Beleg (ohne Bedingung im Satz); eingeschränkt. (d) Re-Review N-1 (MEDIUM): der umbenannte Test sagte „jede RETURNVALUE in Mehrfach-Nachrichten“, prüfte den ersten Aufruf; der Name nennt jetzt die Grenze. (e) Verifikation V-1: W-15 „die Option wirkt nur im Client“ war breiter als die Ablage; eingeschränkt (`bb2d8c53`).

**Nachbesserung ohne zweiten Review.** `93cde5fb` (Namen und Kommentare der Belegtests nach dem Re-Review, Wortlaut in Matrix und Protokoll; laut Verifier nur Namen, Kommentare, Doku, keine Logik) hat keinen eigenen Re-Review; geprüft hat sie der Verifier am Stand `93cde5fb`. `e934b9af` hat seinen Re-Review (Re-Review N-1 bis N-3). Die Textkorrektur `bb2d8c53` und diese Closure ändern nur Text.

**Gates.** Verifier auf `93cde5fb`: `make clean-build` Exit 0 (569 s, 1474 `PASSED`-Zeilen), `make e2e` Exit 0 (9 Stufen, 149 s), `make gates FORCE=1` Exit 0, `make docs-check` Exit 0, 199 Belegtests der Stage `test` bestanden; beide Läufe belegen nur den unveränderten Produktstand (`src/` und `testserver/` nicht berührt), die Messaussagen tragen die Ablagen. Auf dem Stand dieser Closure (nach dem Merge von `main` und dem Move): `make docs-check` (1643 Dateien, 0 Befunde), `make flaeche`, `make kopplung-zahlen` und `make gates FORCE=1` Exit 0 (32 s, Layer-Cache), `make spec-abdeckung` einmal (Exit 0, `docs/plan/spec-abdeckung.md` unverändert gegen die Fassung nach dem Merge); Stempel nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md), die CI kennt sie nicht ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md)).

**Weiter offen.**

1. **Nutzer:** die drei Entscheidungen oben.
2. **Ungemessen, Träger Nutzer** (kein Messauftrag, kein Slice angelegt): Fehler außerhalb der gemessenen Formen (gemessen: 2627, 208 im ersten und mittleren, 8134 im ersten Aufruf; nicht: Fehler in `sp_prepexec` und `sp_execute`, in einer unbekannten ProcID, in verschachtelten Prozeduren, der verbindungsschließende Fehler, 208 im letzten Aufruf); andere Clients und Versionen als `Microsoft.Data.SqlClient` 7.1.1 (mssql-jdbc nur für den Testserver in [slice-mssql-jdbc-gegen-testserver](../open/slice-mssql-jdbc-gegen-testserver.md); ODBC, FreeTDS, `sqlcmd` ohne Träger); Grenzen oberhalb 1025 Aufrufe und 4206 Parameter; die Schwelle, ab der der Client `ntext` sendet; Abbruch und Frist mitten in der Nachricht am echten Server (am Skript-Backend durch `RpcSequenceAbortTest` gedeckt); das Trennbyte `0x80` (kein Sender); `sp_prepexec` von sich aus in anderen Formen (mehrere Parameter, andere Typen); Befehle, Typen und Prozeduren außerhalb der Szenarien; Wiederholbarkeit: je Szenario eine Aufnahme je Instanz, ein Host, ein Lastzustand (Lastmittel 7 bis 9), Streuung zwischen Läufen nicht gemessen.
3. **Wartung (V-2, LOW):** die Zahl 36 der Ablagen steht in Kommentar, Protokoll und `tools/mk/vergleichslauf-d.mk`; der Sensor liegt nur an der 24 der Mehrfach-Nachrichten (Test). Wer eine Ablage hinzufügt, zieht die Orte nach; kein Sensor, akzeptiertes Negativ.
4. **Kein Gate baut `Vergleich/MessungWireTests.cs`** (Review F-9, Verifikation V-4): ein Übersetzungsfehler dort fiele erst im nächsten Messlauf auf; der Verifier baute die Stage `vergleich` einmal von Hand. Gilt für die Reihe seit ihrer Einführung.

**Risiken aus §6, Ausgang.** (1) Referenzclient erzeugt die Form nicht: *teilweise eingetreten*, `sp_prepexec` von sich aus nicht lieferbar (W-19), die übrigen Formen herstellbar. (2) Eine Messung widerlegt eine Setzung: *eingetreten* für Entscheidung 7 (W-20) und Entscheidung 5 (W-21, W-22); Behandlung nur nach Nutzerentscheid, siehe die drei Fragen; Entscheidung 3 ist belegt, nicht widerlegt. (3) Messreihen teilen Tabellen: *entfallen*, jedes der zehn neuen Szenarien legt seine Tabelle selbst an (Verifikation, Plan-vs-Code-Diff); die älteren Szenarien teilen `wire_dml` weiter (Register, 1×). (4) Einzelmessung, ein Host, ein Client: *weiter offen*, die Grenzen stehen im Protokoll (siehe Ungemessen). (5) Last verfälscht die Laufzeit: *eingetreten, benannt*, Lastmittel 9,29 vor und 7,06 nach dem Lauf (98 s, unter der Messsperre); die Bytes sind nicht betroffen. (6) Größe: *entfallen*, drei Liefer-Punkte, keine Rückführung nach §4.

**Register.** Fortgeschrieben, kein Zähler gesetzt (je eine Datei `evidence/slice-mehrfach-rpc-messluecken.md`): `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-1, F-2, F-6, Re-Review N-1, N-2, Verifikation V-1), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Review F-1: `SPEC-003` blieb neben der neuen Messung stehen; F-5 und N-1: Testname neben der Prüfung), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (Verifikation Plan-vs-Code: `SPEC-003`, zwei Zeilen fehlten in §3), `BEO-TDS/nachbesserung-ohne-zweiten-review` (Vermerk für `93cde5fb`), `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server` (die zwei Festlegungen und `ntext`). Neu angelegt, 1×, offen: `BEO-TDS/ntext-deklarationsliste-langer-parameterlisten-am-server-ungemessen`. Nachgeführt ohne neue Evidence: `BEO-TDS/fehler-vor-dem-ersten-ergebnis-im-mehrfach-rpc-ungemessen` (beantwortet), `BEO-TDS/messung-am-echten-sql-server-fehlt` (Stand), `BEO-TDS/messreihe-szenarien-teilen-den-bestand` (die Gegenmaßnahme ist eingehalten, kein weiteres Auftreten).

**Lese-Schritt (Repo ohne Wellen-Betrieb, diese Closure löst ihn aus).** Gelesen, was in der Ablage 3× erreicht hat und von diesem Slice berührt wurde: `aussagen-ueber-messung-hinaus` (verkörpert in `AGENTS.md` §3.8 und `.harness/skills/reviewer.md`), `fortschreibung-laesst-zweite-fundstelle-stehen` (verkörpert im Reviewer-Skill und in Schritt 20c von `implement-slice`), `plan-tabelle-nennt-neue-datei-nicht` (verkörpert im Reviewer-Skill), `nachbesserung-ohne-zweiten-review` (verkörpert, Vermerk geführt), `spec-setzung-widerspricht-messung-am-echten-server` (Träger entschieden für die früheren Fälle). Feststellung: alle fünf sind wiedergekehrt; Reviewer, Re-Review oder Verifier fingen jedes Auftreten, der Implementer hatte die zweite Fundstelle (`SPEC-003`, Testname) und die Tabelle nicht von sich aus nachgezogen. Retirement-Check der verankerten Klassen: wiedergekehrt, keine Lockerung. Die Closure macht keine Aussage über die Wirkung der Regeln; ob ein Sensor über die Prosa hinaus entsteht, entscheidet der Nutzer. Die neue Beobachtung (1×) liegt unter der Schwelle.

**Drei Paarungen** (Repo ohne Wellen-Betrieb, geprüft nach dem Move). *Anker* — diese Notiz verkörpert nichts und trägt kein Feld `liegt in`; nichts zu paaren. *Folge-Slice* — kein Folge-Slice angelegt oder benannt, den ein Punkt annehmen müsste; die möglichen Träger (Folge-ADR oder Slice je Frage, „ntext-deklarationsliste-am-server-messen“) sind Vorschläge, ihr Anlegen hängt an der Entscheidung des Nutzers; die offenen Punkte haben den Träger Nutzer oder das Register. *Register* — fünf Dateien `evidence/slice-mehrfach-rpc-messluecken.md` in fünf vorhandenen Verzeichnissen und eine in einem neuen, jede mit Zeiger auf diesen Slice in `done/`; `make docs-check` Exit 0.

**Lerneintrag (Steering Loop).**

- **Benannte Spec-Lücke** (kein `liegt in`, nichts verkörpert): drei Stellen, an denen das Zielbild von der Messung abweicht oder sie nicht kennt, stehen als offene Entscheidung in `SPEC-030`, `SPEC-003` und der Matrix; Wortlaut der Setzungen unverändert. Die Entscheidung liegt beim Nutzer (siehe oben).
- **Geschärft, keine neue Regel:** (i) Eine Messaussage der Form „X wirkt nur …“ oder „der Client sendet …“ trägt so weit wie das gemessene Szenario und der Client in einer Version; der Review fing es fünfmal in diesem Slice (F-1, F-2, F-6, N-1, N-2) und der Verifier ein sechstes Mal (V-1) an einem Satz, den der Review F-6 schon einmal eingeschränkt hatte (`AGENTS.md` §3.8, `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert; Retirement-Check: wiedergekehrt, die Regel bleibt). (ii) Eine Messung mit einem neuen Typ am Draht (`ntext`) berührt die Setzung, die ihn ausschließt, an einer zweiten Stelle (`SPEC-003`), die der Plan nicht nannte (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`; dieselben Klassen, kein neuer Sensor). (iii) Ein Messslice, dessen Zielbild eine Festlegung ohne Messung trägt (Grenzen, Ablehnung), erzeugt mit der Messung eine Nutzerentscheidung statt eines Codes; die Messung zuerst und die Entscheidung danach ist die Reihenfolge, die diesen Slice ohne Änderung an `src/` abschließen ließ.

**Herkunft:** [slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md) §7 „Weiter offen“ Punkt 2 (Ungemessen), [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Abschnitt „Re-Evaluierungs-Trigger“ (eine Messung, die eine Festlegung widerlegt oder ergänzt), Verifikation [2026-11-17-4c7b069b](../../../reviews/2026-11-17-4c7b069b-verifikation-slice-mehrfach-rpc-unterstuetzen.md) (V-4), Messprotokoll [Mehrere RPCs in einer Nachricht](../../../messungen/referenzabgleich.md#mehrere-rpcs-in-einer-nachricht-messungen-vom-2026-11-17) (W-10 bis W-13). Nutzerentscheidung 2026-11-17: „ok 2 slices“.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `fehler-vor-dem-ersten-ergebnis-im-mehrfach-rpc-ungemessen` (1×, offen; der Fehler im ersten Aufruf berührt ihn), `messreihe-szenarien-teilen-den-bestand` (1×, offen), `messung-am-echten-sql-server-fehlt`, `aussagen-ueber-messung-hinaus` und `festlegung-gilt-nur-fuer-das-clientprofil` (verkörpert beziehungsweise beim Nutzer; die Messung ist die Gegenmaßnahme), `flake-ohne-namen-des-roten-testfalls` (nicht Gegenstand, siehe §1). Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
