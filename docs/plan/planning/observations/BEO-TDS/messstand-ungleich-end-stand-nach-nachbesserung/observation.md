# Die Messzahlen eines Protokolls gelten für einen Stand, den die Nachbesserung danach im gemessenen Weg ändert

**Sub-Area:** `*` (Qualität, Messungen)

Der Messlauf lief vor dem Review auf `9f236f41`; die Nachbesserung zu F-5 (`3d722337`) änderte danach `BulkDispatch.statement` und `probe`, den Weg jedes `sql_batch` in B und C. Das Protokoll nannte den Stand richtig, sagte aber nicht, dass der End-Stand im gemessenen Weg abweicht; der Verifier fand es als V-1 (MEDIUM). Die Wiederholung auf dem End-Stand lief auf einem weniger ruhigen Host und trennt Kosten der Änderung nicht von Streuung und Fremdlast. Der Plan sah den Messlauf erst nach Review und Verifikation vor (§3 Ablauf Punkt 4); der Bau maß früher. Verwandt: `BEO-TDS/aussagen-ueber-messung-hinaus` (dort ist die Aussage zu breit, hier ist der gemessene Code nicht mehr der gelieferte), `BEO-TDS/nachbesserung-ohne-zweiten-review`.
