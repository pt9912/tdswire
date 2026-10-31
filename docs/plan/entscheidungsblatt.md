# Entscheidungsblatt

**Stand:** 2026-10-31 (Zeitdokument; gilt für den Repo-Stand dieses Tages und wird nicht fortgeschrieben).

**Zweck:** Der Nutzer bestätigt oder ändert jede Empfehlung des Koordinators in einem Durchgang. Dieses Blatt trifft keine Entscheidung und ändert weder Spezifikation noch ADR. Die Tabelle *Entscheidung* am Ende nimmt die Antworten auf; die Umsetzung (Planner, Architect, ADR) folgt erst danach.

**Lesart der Felder:** *Kontext* nennt die Fundstelle, an der die Frage steht. *Empfehlung* ist die des Koordinators, soweit nicht „Planner-Vorschlag“ dasteht. Zahlen und Zitate stammen aus den verlinkten Dateien; was dort „Setzung“ oder „nicht gemessen“ heißt, heißt es hier ebenso.

---

## Teil A — Entscheidungen

### 1. Grenzwert der Prepared-Handles je Sitzung

- **Frage:** Wie viele Prepared-Handles darf eine Sitzung halten, bevor der definierte Fehler kommt?
- **Kontext:** [slice-rpc-prepared-handles](planning/open/slice-rpc-prepared-handles.md) §2 („Der Grenzwert … ist eine Festlegung des Nutzers (§6) und nicht gemessen“) und §4 (Start-Bedingung: Grenzwert vom Nutzer entschieden); [Closure von slice-antwort-mehrteilig-output](planning/done/slice-antwort-mehrteilig-output.md) („für den Grenzwert findet sich … keine Zahl“); Entscheidung von [ADR-0005](adr/0005-rpc-und-prepared-statements.md).
- **Optionen:** (a) 1000; (b) eine andere Zahl; (c) konfigurierbar mit Voreinstellung.
- **Empfehlung:** (a) 1000 als Setzung, die Voreinstellung ist nicht gemessen und wird so in SPEC-021 gekennzeichnet. Die Zahl blockiert den Start des Slice und ist ohne Messung am echten Server nicht besser zu begründen.
- **Schaltet frei:** Start von `slice-rpc-prepared-handles` (`open/` nach `next/`), damit der Rest von [welle-anfrageverarbeitung](planning/welle-anfrageverarbeitung.md).
- **Umsetzer:** Planner (Slice-Text, SPEC-021-Festlegung durch den Implementer des Slice).

### 2. Drosselung wiederholter Anmeldeversuche (CO-003)

- **Frage:** Wird eine Drosselung gebaut (Art und Grenzwerte), oder wird der Verzicht per ADR festgehalten?
- **Kontext:** [CO-003](carveouts/CO-003-anmeldedrosselung-fehlt.md) (Auflösungs-Trigger: Folge-Slice in `done/` und eine `Accepted`-ADR für Drosselung oder Verzicht); [slice-qa-anmeldedrosselung](planning/open/slice-qa-anmeldedrosselung.md); [ADR-0009](adr/0009-authentifizierungsmodus.md) §Offen.
- **Optionen:** (a) Folge-ADR zur Art (je Benutzername, je Quelladresse, Verzögerung oder Sperre) und Umsetzung im Folge-Slice; (b) Folge-ADR, die den Verzicht festhält.
- **Empfehlung:** (a), der Folge-Slice bleibt in `welle-qualitaet-grundlage`. CO-003 nennt die Wahl der Art und der Grenzwerte als Entscheidung des Nutzers; ohne ADR kann der Carveout nicht aufgelöst werden.
- **Schaltet frei:** Auflösung von CO-003; Closure von [welle-qualitaet-grundlage](planning/welle-qualitaet-grundlage.md) (Kriterium nur, soweit die Welle-Datei es führt, ungeprüft).
- **Umsetzer:** Architect (ADR) mit Nutzerwahl, danach Planner (Slice nach `next/`).

### 3. Zuschnitt von slice-qa-kompatibilitaet, Container-Modell, SSPI/NTLM

- **Frage:** Wann und wie startet der Slice, und was wird als nicht lieferbar benannt?
- **Kontext:** [slice-qa-kompatibilitaet](planning/open/slice-qa-kompatibilitaet.md) §4 („Präzisierung … Zuschnitt beim Nutzer“) und §6 (Container-Modell offen); [ADR-0008](adr/0008-dotnet-kompatibilitaetstests.md); [ADR-0009](adr/0009-authentifizierungsmodus.md) §Offen; [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität). In `main` gibt es laut §4 keinen Anfragepfad im Produktionsprozess und keine Verdrahtung ab dem ersten Byte (TDS 8.0).
- **Optionen Zuschnitt:** A zurückstellen, bis Simulations-Backend-Integration und TDS-8.0-Träger in `done/` liegen; B auf die Anmeldung zuschneiden; C die Voraussetzungen vorziehen.
- **Optionen Container:** zwei Container (Server und Client getrennt) oder ein gemeinsamer Container.
- **Optionen SSPI/NTLM:** messen oder als nicht lieferbar benennen (die DoD erlaubt „je Punkt mit Begründung als nicht lieferbar benannt“).
- **Empfehlung:** A; zwei Container (Server und Client); SSPI/NTLM als nicht lieferbar benennen. A hält die DoD ehrlich, weil sie sonst auf Ungebautes zeigt; die Einschätzung „nicht lieferbar“ für SSPI/NTLM ist die des Koordinators und im Repo nicht gemessen.
- **Schaltet frei:** Start-Trigger und DoD-Text von `slice-qa-kompatibilitaet`; Schärfung von [ADR-0008](adr/0008-dotnet-kompatibilitaetstests.md) nur, wenn das Container-Modell dort etwas ändert (dann Folge-ADR).
- **Umsetzer:** Planner (Slice-Text); Architect, falls das Container-Modell [ADR-0008](adr/0008-dotnet-kompatibilitaetstests.md) berührt.

### 4. Träger-Slice für die TDS-8.0-Verdrahtung in main

- **Frage:** Bekommt die Verdrahtung des TLS-Pfads ab dem ersten Byte einen Slice?
- **Kontext:** [slice-qa-kompatibilitaet](planning/open/slice-qa-kompatibilitaet.md) §6 („Benannte Lücke — kein Träger für die TDS-8.0-Verdrahtung“; „Ein neuer Slice entsteht nur auf Entscheidung des Nutzers“); Register [tds-8-0-und-pooling-nicht-gemessen](planning/observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/state.md).
- **Optionen:** anlegen; nicht anlegen (die TDS-8.0-Messungen der DoD gelten dann als nicht lieferbar).
- **Empfehlung:** anlegen, in `welle-anfrageverarbeitung` oder `welle-qualitaet-grundlage` (Welle wählt der Planner). Ohne Träger bleibt Option A aus Punkt 3 unerfüllbar.
- **Schaltet frei:** Start-Voraussetzung von `slice-qa-kompatibilitaet`; die TDS-8.0-Aussage des Codecs kann von Festlegung auf gemessen gehoben werden.
- **Umsetzer:** Planner.

### 5. Basis-Image des Laufzeit-Artefakts

- **Frage:** Wird Distroless Java 21 (`nonroot`) als Basis-Image bestätigt?
- **Kontext:** [slice-release-artefakt-lizenz](planning/done/slice-release-artefakt-lizenz.md) §6 („Nutzerbestätigung“, gewählt ist Distroless Java 21) und Closure („Offene Entscheidungen beim Nutzer“); Digest laut Closure am 2026-10-31 für den Index-Tag ermittelt, von der Verifikation nicht erneut gemessen.
- **Optionen:** bestätigen; anderes Image (z. B. JRE-Image).
- **Empfehlung:** bestätigen. Die Wahl ist gebaut, per Digest gepinnt und läuft ohne Root; ein Wechsel kostet einen neuen Digest und Laufzeittest.
- **Schaltet frei:** Entlastet die Risikozeile des Slice; kein Folge-Slice.
- **Umsetzer:** Nutzer (Bestätigung), Planner vermerkt sie.

### 6. Zusatzklasse für Lastenheft-Bindungen in der Sensors-Tabelle

- **Frage:** Wird eine Zusatzklasse (Form `TDS-RB-01` u. ä.) in `harness/conventions.md` deklariert?
- **Kontext:** [Verifikation slice-release-artefakt-lizenz](../reviews/2026-10-31-175d1e4-verifikation-slice-release-artefakt-lizenz.md) V-3 („Keine Zusatzklasse deklariert“; Präzedenz `make workflow-lint` mit `TDS-QA-06`); §Zusatzklassen von [conventions.md](../../harness/conventions.md) steht nur als Platzhalter.
- **Optionen:** eintragen; Bindungen auf ADR/Carveout beschränken.
- **Empfehlung:** eintragen (Klasse `Lastenheft-Bindung`, Form `TDS-<FA|QA|RB>-<NN>`). Zwei Zeilen von [harness/README.md](../../harness/README.md) §Sensors nutzen sie bereits.
- **Schaltet frei:** Form-Konformität der Sensors-Tabelle.
- **Umsetzer:** Planner (Änderung an `harness/conventions.md`, kein ADR nötig, solange nur Form deklariert wird).

### 7. SPEC-Zeile für Laufzeit-Artefakt und Lizenz

- **Frage:** Bekommt die Spezifikation eine Zeile für das Laufzeit-Artefakt und die Lizenz?
- **Kontext:** [Verifikation slice-release-artefakt-lizenz](../reviews/2026-10-31-175d1e4-verifikation-slice-release-artefakt-lizenz.md) V-1 (`LizenzTest` trägt keinen `@spec`-Tag mehr, weil keine Zeile die Lizenz fordert); [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz).
- **Optionen:** ja (neue SPEC-Zeile, `LizenzTest` bekommt ihren Tag); nein (DoD-Zeile „jeder Test trägt `@spec`“ mit Ausnahme versehen).
- **Empfehlung:** ja. Die Abdeckungsmatrix führt den Test dann unter einer Festlegung statt als Ausnahme.
- **Schaltet frei:** Tag am `LizenzTest`; schließt die Spec-Lücke der Closure.
- **Umsetzer:** Architect/Planner (Spec-Zeile liegt in `spec/spezifikation.md`, Rang 2; dieses Blatt ändert sie nicht).

### 8. Dateirechte und Übergabe der Benutzerliste

- **Frage:** Wer besitzt die Benutzerliste im Container, mit welchen Rechten liest sie der Nutzer ohne Root, und wie kommt sie herein (Mount, Secret, Umgebungsvariable auf einen Pfad)?
- **Kontext:** [ADR-0011](adr/0011-pruefwertformat.md) §Offen 5 („nicht gemessen“); [slice-release-artefakt-lizenz](planning/done/slice-release-artefakt-lizenz.md) §6 („Entscheidung liegt beim Nutzer“); Register [benutzerliste-dateirechte-im-betrieb-offen](planning/observations/BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen/observation.md); Träger des Betriebstexts [slice-release-doku-prozess](planning/open/slice-release-doku-prozess.md).
- **Optionen:** Betreiber-Pflicht dokumentieren (Betriebstext, ohne Messung); Rechte messen und festlegen (Folge-ADR); vom Server beim Laden prüfen lassen (Änderung am Lader).
- **Empfehlung (Planner-Vorschlag, Koordinator-Empfehlung liegt nicht vor):** Betriebstext in `slice-release-doku-prozess` mit Messung der Rechte im Container, Festlegung danach per Folge-ADR; kein Code vorab.
- **Schaltet frei:** Schließt den Punkt in [ADR-0011](adr/0011-pruefwertformat.md) §Offen 5 und das Register.
- **Umsetzer:** Architect (ADR), Planner (Slice-Text).

### 9. Lizenzhinweise der Bibliotheken

- **Frage:** Verlangt die Auslieferung Hinweise zu den Lizenzen der gelieferten Bibliotheken?
- **Kontext:** [slice-release-artefakt-lizenz](planning/done/slice-release-artefakt-lizenz.md) §6 („Rechtsfrage ohne Prüfung im Repo“); [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz) verlangt nur die MIT-Lizenz des Projekts; [ADR-0012](adr/0012-simulations-backend-beschreibung-und-formen.md) nimmt `jackson-dataformat-yaml` auf.
- **Optionen:** Hinweisdatei (z. B. NOTICE) im Artefakt; keine Hinweise; Prüfung durch Dritte vorab.
- **Empfehlung (Planner-Vorschlag):** Rechtsfrage; das Repo kann sie nicht entscheiden. Der Nutzer nennt die Antwort; bei „ja“ trägt `slice-release-doku-prozess` die Hinweisdatei.
- **Schaltet frei:** DoD-Text von `slice-release-doku-prozess`.
- **Umsetzer:** Nutzer (Rechtsauskunft), dann Planner.

### 10. DONE statt DONEPROC im Fehlerfall einer RPC-Antwort

- **Frage:** Schreibt der Server im Fehlerfall einer RPC-Antwort DONEPROC (wie Messzeile R02) oder bleibt es bei DONE?
- **Kontext:** [Closure slice-antwort-mehrteilig-output](planning/done/slice-antwort-mehrteilig-output.md) §7 Spec-Lücke 4 („kein Träger im Repo“; gepinnt als Setzung vom Test `ResponseRpcTest`, „gegen die einzige Messzeile R02“ von [ADR-0007](adr/0007-fehler-und-attention.md)).
- **Optionen:** (a) DONEPROC im Fehlerfall von RPC, Folge-ADR und Folge-Slice in `welle-fehler-abbruch`; (b) DONE als Setzung belassen; (c) in `slice-qa-vergleichslauf-d` messen und danach entscheiden.
- **Empfehlung:** (a). Die einzige Messzeile spricht für DONEPROC; der Code weicht ab, und kein Slice der Welle trägt die Korrektur.
- **Schaltet frei:** Träger für Spec-Lücke 4; Abgleich von SPEC-030 mit [ADR-0007](adr/0007-fehler-und-attention.md).
- **Umsetzer:** Architect (Folge-ADR), Planner (Slice in `welle-fehler-abbruch`).

### 11. Drei Folge-ADR-Fragen ohne Träger

- **Frage:** Werden drei Spannungen zwischen Accepted-ADR und Spezifikation oder Verhalten per Folge-ADR geklärt?
- **Kontext:**
  - (a) [ADR-0013](adr/0013-backend-grenzen.md) Entscheidung 6 sagt, `OutputParameter` bleibe „bis zur Abbildung abgelehnt“; die Spezifikation lässt ihn im RPC zu ([Closure slice-antwort-mehrteilig-output](planning/done/slice-antwort-mehrteilig-output.md) Spec-Lücke 1).
  - (b) [ADR-0004](adr/0004-session-zustandsautomat.md) führt den Zuschnitt der Zustandsaufzählung offen, SPEC-001 legt „flach“ fest ([Closure slice-sql-batch-dispatch](planning/done/slice-sql-batch-dispatch.md) §7, „Folge-ADR nötig“).
  - (c) Ein ungültig kodierter RPC ergibt `TDS_UNSUPPORTED_RPC` (Class 16, Verbindung offen), ein ungültiger SQL_BATCH `TDS_PROTOCOL_ERROR` (Class 20, schließt); [ADR-0007](adr/0007-fehler-und-attention.md) sagt zur Fehlerfolge ungültig kodierter RPC nichts ([Review slice-rpc-executesql-parameter](../reviews/2026-10-31-092b7a1-slice-rpc-executesql-parameter.md) F-1).
- **Optionen:** je Punkt Folge-ADR; Spec an die ADR angleichen; Spannung als Setzung stehen lassen.
- **Empfehlung:** eine Folge-ADR für (a) und (b) (beide sind Spannung ADR gegen Spec), für (c) ein Absatz in der Folge-ADR aus Punkt 10, weil beide die Fehlerfolge der RPC-Antwort betreffen. Die zusammenfassende Zuordnung ist Planner-Einschätzung.
- **Schaltet frei:** Auflösung von Spec-Lücken, die in den Closures als „kein Träger“ stehen.
- **Umsetzer:** Architect (ADR), danach Planner.

### 12. slice-qa-performance um Rückstau, Heap und zurückgehaltenen Abschluss ergänzen

- **Frage:** Nimmt der Slice die drei Messpunkte in DoD oder Risiko auf?
- **Kontext:** [slice-qa-performance](planning/open/slice-qa-performance.md) (DoD nennt Referenz-Workload, Lasttest, Grenzwerte, nicht diese Punkte); [Closure welle-backend-streaming](planning/done/welle-backend-streaming-results.md) („`slice-qa-performance` führt den Punkt ‚zurückgehaltener Abschluss‘ nicht“); Register [rueckstau-im-produktivbetrieb-nicht-gemessen](planning/observations/BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen/state.md); [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance).
- **Optionen:** ja; nein (Punkte bleiben „nicht gemessen“ im Register).
- **Empfehlung:** ja. Der Slice ist der benannte Träger der Last, und das Register führt die Lücke seit sechs Evidence-Dateien.
- **Schaltet frei:** Schließt Register-Zeile R4 (Teil B) und die Paarung „Folge-Slice“ der Welle-Closure.
- **Umsetzer:** Planner.

### 13. Träger der Loginspektion über das Gesamtsystem

- **Frage:** Wer trägt den Gate-Test, der über alle Log-Zeilen des Gesamtsystems kein Geheimnis findet?
- **Kontext:** [Review slice-qa-sicherheit](../reviews/2026-10-31-5d95bd2-slice-qa-sicherheit.md) F-1 (Trägerlücke zwischen Slices); Fix-Runde setzt als Träger [slice-betrieb-log-schnittstelle](planning/open/slice-betrieb-log-schnittstelle.md) (Stand: [slice-qa-sicherheit](planning/in-progress/slice-qa-sicherheit.md) §6 „Nutzerfrage im Bericht der Fix-Runde“); [welle-betrieb-diagnose](planning/welle-betrieb-diagnose.md).
- **Optionen:** (a) `slice-betrieb-log-schnittstelle` (heutiger Stand); (b) `slice-qa-sicherheit` bleibt `in-progress`, bis der Sammler existiert; (c) eigener Slice in `welle-betrieb-diagnose`.
- **Empfehlung (Planner-Vorschlag, Koordinator-Empfehlung liegt nicht vor):** (a) bestätigen. Das Gesamtsystem-Gate braucht den Sammler, der erst in `welle-betrieb-diagnose` entsteht; (b) hielte einen Slice und damit ein WIP-Platz (Punkt 18) auf unbestimmte Zeit.
- **Schaltet frei:** Closure von `slice-qa-sicherheit` ohne offene Trägerlücke.
- **Umsetzer:** Planner.

### 14. Kompatibilitätsmatrix vor der Closure von slice-qa-wartbarkeit neu aufnehmen; Lücken 3, 4, 14

- **Frage:** (a) Wird die Matrix auf den Kopf von `main` neu aufgenommen, bevor `slice-qa-wartbarkeit` schließt, und die Zitat-Prüfung ein Hilfsziel? (b) Werden die Lücken 3, 4 und 14 als DoD-Text in `slice-qa-kompatibilitaet` aufgenommen?
- **Kontext:** [Kompatibilitätsmatrix](kompatibilitaetsmatrix.md) Kopf („Merge-Stand `bda6cd6`“, 280 Testfälle; Datum 2026-10-30); [slice-qa-wartbarkeit](planning/in-progress/slice-qa-wartbarkeit.md) §6 („Wegwerf-Prüfung im Lauf, kein Gate“); Lücke 3 (`xml` als RPC-Parameter, kein Träger), Lücke 4 (Batch-Flag, kein Träger), Lücke 14 (Kennwortwechsel und FeatureExt `0x02`, kein Träger).
- **Optionen (a):** a) neu aufnehmen vor der Closure; b) Stand `bda6cd6` mit Hinweis lassen. Zitat-Prüfung: als Hilfsziel in `tools/mk/` (kein Gate) oder Wegwerf-Lauf.
- **Optionen (b):** DoD-Text in `slice-qa-kompatibilitaet`; „nicht lieferbar“ mit Begründung; Lücken streichen.
- **Empfehlung:** (a) neu aufnehmen vor der Closure, Zitat-Prüfung als Hilfsziel (`make`, kein Gate); (b) die drei Lücken als DoD-Text in `slice-qa-kompatibilitaet`. Die Matrix zitiert Tests eines Standes, der nicht mehr der Kopf ist; die Lücken haben heute keinen Träger.
- **Schaltet frei:** Closure von `slice-qa-wartbarkeit`; Träger für drei Lücken.
- **Umsetzer:** Planner (DoD-Text), Implementer (Matrix, Hilfsziel).

### 15. Offene [ADR-0006](adr/0006-backend-vertrag-und-streaming.md)-Punkte ohne Träger

- **Frage:** Bekommen zwei offene Punkte einen Träger?
- **Kontext:** [Closure welle-backend-streaming](planning/done/welle-backend-streaming-results.md) („ausdrücklich weiter offen, ohne Slice als Träger“; „Paarung (b)“): (i) statement-abbrechende (R04) gegen batch-abbrechende Fehler (R05 bis R07, [ADR-0007](adr/0007-fehler-und-attention.md)), vom `Failure`-Ereignis nicht getragen; (ii) Hilfsadapter für blockierende Backends ([ADR-0013](adr/0013-backend-grenzen.md) Entscheidung 3, ausgelöst durch den Re-Evaluierungs-Trigger „echtes Backend, dessen Treiber blockiert“).
- **Optionen:** (i) Folge-ADR plus Slice in `welle-fehler-abbruch`, oder Verzicht; (ii) Slice erst beim Eintreten des Triggers, oder jetzt anlegen.
- **Empfehlung (Planner-Vorschlag, Koordinator-Empfehlung liegt nicht vor):** (i) Entscheidung zusammen mit der Folge-ADR aus Punkt 10 (gleiches Thema Fehlerfolge); (ii) nicht anlegen, solange der Trigger nicht eingetreten ist, und das im Register als begründet offen führen.
- **Schaltet frei:** Paarung „Folge-Slice“ der geschlossenen Welle ohne Ausnahme.
- **Umsetzer:** Architect (ADR), Planner.

### 16. Nutzerentscheidungen aus welle-backend-streaming nie als bestätigt vermerkt

- **Frage:** Bestätigt der Nutzer nachträglich drei Entscheidungen?
- **Kontext:** [Closure welle-backend-streaming](planning/done/welle-backend-streaming-results.md) („Nutzerentscheidungen aus den Slice-Risiken“): (a) Ort der Modelltypen in `domain` und von `QueryBackend`/`QueryExecution` in `port` (der Slice nennt den Entscheider „Aufrufer“); (b) Einzelzuordnung der Failure-Abbildung auf Nummer und Severity (Gruppen entschieden, Einzelzuordnung `SPEC-011` bis `SPEC-016` als „Vorschlag“); (c) Zuordnung der Streaming-Aussage zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) statt [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t) (Welle-Datei §1, „offene Frage 1“).
- **Optionen:** je Punkt bestätigen, ändern oder als Setzung belassen.
- **Empfehlung:** nachträglich bestätigen. Das Verzeichnis führt alle drei als „vom Nutzer nicht bestätigt“; Bestätigung kostet eine Antwort und schließt eine Zeile des Registers.
- **Schaltet frei:** Streichung der Einträge aus [lesart-einer-nutzerentscheidung-unbestaetigt](planning/observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/state.md), soweit sie dort stehen (nicht geprüft).
- **Umsetzer:** Nutzer; Planner vermerkt (Closure-Notiz, Spec „Vorschlag“ → „Festlegung“ über den Architect).

### 17. Lesart „abschließend“ und Offen 12 (a)/(c) von [ADR-0012](adr/0012-simulations-backend-beschreibung-und-formen.md)

- **Frage:** Ist die Liste der positiven Formen des Simulations-Backends abschließend, und wird die Zeile „Offene Entscheidungen“ zu [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) als erledigt geführt?
- **Kontext:** [ADR-0012](adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 12 ((a) Bestätigung der Lesart „abschließend“, (b) erledigt, (c) Zeile als erledigt führen); Register [lesart-einer-nutzerentscheidung-unbestaetigt](planning/observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/state.md) (10 Evidence-Dateien, Stand offen, „Entscheidung beim Nutzer“).
- **Optionen:** (a) bestätigen / nicht bestätigen; (c) erledigt führen / offen lassen.
- **Empfehlung (Planner-Vorschlag, Koordinator-Empfehlung liegt nicht vor):** Die Entscheidung gehört dem Nutzer, weil sie das Lastenheft (Rang 1) liest. Mit Punkt 16 zusammen beantworten, damit die Klasse in einem Zug abgearbeitet wird.
- **Schaltet frei:** Abnehmbarkeit von [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) nach dem Lastenheft; Teil von Register-Zeile R1.
- **Umsetzer:** Nutzer; Planner vermerkt im Entscheidungs-Slice.

### 18. WIP-Limit: slice-qa-wartbarkeit und slice-qa-sicherheit gleichzeitig in-progress

- **Frage:** Bleiben beide Slices gleichzeitig in `in-progress/`, oder wird einer zurückgeführt?
- **Kontext:** [Roadmap](planning/in-progress/roadmap.md) „In Arbeit (Welle welle-qualitaet-grundlage)“ nennt beide; `in-progress/` enthält zusätzlich [slice-sim-auswahl-streaming](planning/in-progress/slice-sim-auswahl-streaming.md). Das Regelwerk (`modul-05-planning-harness.md` §Trigger und WIP-Limit) nennt „WIP-Limit pro Rolleninhaber = 1 … pro Lauf“; die `Verantwortlich:`-Felder wurden nicht geprüft.
- **Optionen:** beide freigeben; einen nach `open/` oder `next/` zurückführen; Begründung der Freigabe im Drift-Log der Roadmap.
- **Empfehlung (Planner-Vorschlag, Koordinator-Empfehlung liegt nicht vor):** Freigabe nur, wenn unterschiedliche Läufe mit unterschiedlichem `Verantwortlich:` sie tragen; sonst `slice-qa-wartbarkeit` zuerst schließen (Punkt 14).
- **Schaltet frei:** Reihenfolge der Closures in `welle-qualitaet-grundlage`.
- **Umsetzer:** Planner.

---

## Teil B — Register: Klassen mit mindestens drei Evidence-Dateien ohne Verkörperung

Gezählt am 2026-10-31 aus `docs/plan/planning/observations/BEO-TDS/*/evidence` (Zahl der Dateien) und gegen das jeweilige `state.md` gelesen. Nicht verkörpert und Stand `offen` (12 Klassen). Die Klassen mit Stand `geplant` ([messung-am-echten-sql-server-fehlt](planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md) 21×, [tds-8-0-und-pooling-nicht-gemessen](planning/observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/state.md) 13×, [anmeldepfad-sicherheit-nicht-gebaut](planning/observations/BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut/state.md) 6×) tragen einen Slice als Träger und stehen hier nicht; verkörpert sind [aussagen-ueber-messung-hinaus](planning/observations/BEO-TDS/aussagen-ueber-messung-hinaus/state.md) 35×, [offene-punkte-ohne-traeger](planning/observations/BEO-TDS/offene-punkte-ohne-traeger/state.md) 16× und [test-stage-cache-verdeckt-wiederholung](planning/observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/state.md) 4×.

Abweichungen gegenüber der Liste des Koordinators: Zähler und Stand stimmen für alle dort genannten Klassen. Die Klasse `geheimnis-kopien-im-speicher-nicht-gemessen` (4×) stand nicht auf der Liste und kommt als R12 hinzu; deren `state.md` sagt „(3×)“, die Zahl der Dateien ist 4.

| Nr | Kennung | Zähler | Kurzbeschreibung | Empfehlung |
|---|---|---|---|---|
| R1 | [lesart-einer-nutzerentscheidung-unbestaetigt](planning/observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/state.md) | 10 | Engere Lesart einer Nutzerentscheidung steht als Festlegung ohne Bestätigung in Spec, KDoc und Test. | Nutzerentscheidung: Punkte 16 und 17 beantworten; danach Zeile mit Begründung auf `gestrichen`, soweit die Lesarten bestätigt sind. |
| R2 | [zeile-oder-zweig-ohne-unterscheidenden-test](planning/observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/state.md) | 8 | Eigencode (Wartebedingung, Fehlerzweig, `NonCancellable`), dessen Entfernung jeden Test grün lässt; gefunden per Mutation. | Verkörpern als Prüfpunkt im Reviewer-Skill ([reviewer.md](../../.harness/skills/reviewer.md), MEDIUM-Liste): „Zweig oder Wartebedingung, dessen Entfernung keinen Test rot macht“. |
| R3 | [strukturtest-ohne-variation-der-eingabe](planning/observations/BEO-TDS/strukturtest-ohne-variation-der-eingabe/state.md) | 6 | Test behauptet Auswahl- oder Gleichheitseigenschaft mit einheitlicher Eingabe; jede Mutation bleibt grün. | Verkörpern als Prüfpunkt im Reviewer-Skill (MEDIUM-Liste), zusammen mit R2 und R10 als Gruppe „Test belegt nicht, was er behauptet“. |
| R4 | [rueckstau-im-produktivbetrieb-nicht-gemessen](planning/observations/BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen/state.md) | 6 | Rückstau am Socket nur auf einer Maschine gemessen (Loopback, 32 MiB), unter Last ungemessen. | Träger-Slice: `slice-qa-performance` (Punkt 12); State auf `geplant` mit Kennung. |
| R5 | [spec-einzelfaelle-ohne-lastenheft-deckung](planning/observations/BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung/state.md) | 4 | Einzelfälle in Spec und Vertrag, deren Deckung durch das Lastenheft nicht belegt ist. | Nutzerentscheidung (Lastenheft-Nachzug, Deckungsnachweis je Einzelfall oder Streichung); kein Vorschlag des Koordinators. |
| R6 | [risikozeile-nennt-behobenen-zustand](planning/observations/BEO-TDS/risikozeile-nennt-behobenen-zustand/state.md) | 4 | Risikozeile eines `open/`-Slice beschreibt einen Werkzeugmangel, den eine Reparatur auf `main` schon behoben hat. | Verkörpern als Prüfpunkt im Planner-/Closure-Ablauf ([plan-welle](../../.claude/commands/plan-welle.md), [close-welle](../../.claude/commands/close-welle.md)): übernommene Risikozeilen gegen den Ist-Stand lesen; Zeile nennt Folgepflicht, nicht den Werkzeugzustand. |
| R7 | [kennungen-ohne-link-in-neuen-dokumenten](planning/observations/BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten/state.md) | 4 | Neue Dokumente nennen `ADR-*`/`TDS-*` ohne Link; `docs-check` meldet `id-unlinked`. | Streichen mit Begründung: gedeckt. [.d-check.yml](../../.d-check.yml) führt Muster für `ADR-*` (`link-policy: always`) und `TDS-(FA|QA|RB)-NN` (`link-policy: prose`), `make docs-check` ist in `make gates`. Die Beobachtung beschreibt Nacharbeit, keinen ungedeckten Fehler (Deckung nicht an einem roten Lauf nachgestellt). |
| R8 | [dod-satz-verlangt-ablage-die-der-slice-ersetzt](planning/observations/BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt/state.md) | 4 | Implementer hält die Ablage an einer Stelle statt an zwei; der DoD-Wortlaut bleibt, der Haken bleibt offen. | Verkörpern als Prüfpunkt im Planner-/Closure-Ablauf: weicht eine Nutzerentscheidung vom DoD-Wortlaut ab, ändert der Planner den Wortlaut vor der Verifikation. |
| R9 | [abdeckungsmatrix-veraltet-ohne-sensor](planning/observations/BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor/state.md) | 4 | Die committete Abdeckungsmatrix steht nach Test-Änderungen hinter dem Bestand; nichts meldet es. | Sensor prüfen: Hilfsziel `make spec-abdeckung` bei jeder Closure mit Tests (Handlung im Closure-Ablauf), kein Gate (die Matrix ist abhängig vom Testlauf und wird bei Closure erzeugt). Ob ein Stand-Vergleich als Gate-Sensor sinnvoll ist, entscheidet der Nutzer. |
| R10 | [test-belegt-nur-den-fake](planning/observations/BEO-TDS/test-belegt-nur-den-fake/state.md) | 3 | Vertragstest liefert das selbst gebaute Skript unverändert zurück; keine Verhaltensänderung am Produktionscode macht ihn rot. | Verkörpern als Prüfpunkt im Reviewer-Skill (siehe R3). |
| R11 | [schranken-test-mit-ungeprueftem-zaehler](planning/observations/BEO-TDS/schranken-test-mit-ungeprueftem-zaehler/state.md) | 3 | Nachweis „höchstens N Bytes“ prüft nur die Obergrenze; zählt die Testhilfe nichts, bleibt er grün. | Verkörpern als Prüfpunkt im Reviewer-Skill (Gegenprobe: Untergrenze), zusammen mit R3. |
| R12 | [geheimnis-kopien-im-speicher-nicht-gemessen](planning/observations/BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen/state.md) | 4 | Kopien des Passworts in JVM und Adapter (Schlüsselobjekt, UTF-8-Puffer, `PBEKeySpec`) sind gelesen, nicht gemessen. | Planner-Vorschlag: Nutzerentscheidung. [slice-qa-sicherheit](planning/in-progress/slice-qa-sicherheit.md) §8 nennt den Slice als Träger, die DoD führt keinen Punkt dazu. Entweder DoD-Punkt (Speicherabzug) oder Streichen mit Begründung „Aussage im Code als nicht gemessen gekennzeichnet“. |

---

## Teil C — Entscheidung

Zum Ausfüllen: `j` (Empfehlung bestätigt), `n` (abgelehnt) oder die gewählte Option. Wo die Empfehlung ein Planner-Vorschlag ist, ist `j` die Bestätigung dieses Vorschlags.

| Nr | Entscheidung |
|---|---|
| 1 | |
| 2 | |
| 3 (Zuschnitt) | |
| 3 (Container) | |
| 3 (SSPI/NTLM) | |
| 4 | |
| 5 | |
| 6 | |
| 7 | |
| 8 | |
| 9 | |
| 10 | |
| 11 | |
| 12 | |
| 13 | |
| 14 (a) | |
| 14 (b) | |
| 15 | |
| 16 | |
| 17 | |
| 18 | |
| R1 | |
| R2 | |
| R3 | |
| R4 | |
| R5 | |
| R6 | |
| R7 | |
| R8 | |
| R9 | |
| R10 | |
| R11 | |
| R12 | |
