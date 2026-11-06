# Das Simulations-Backend erkennt weniger Anweisungsformen als der echte SQL Server, und „die Liste ist abschließend“ ist unbestätigt

**Sub-Area:** `*` (Simulations-Backend, Erkennung)

Der Server dieses Repos lehnt `SELECT 2` (Anweisung ohne Tabelle) und `WHERE Id = 1 OR Id = 2` mit `Number 50001, Class 16`, „Unsupported SQL.“ ab; der echte SQL Server nimmt beide an. Grundlage der Ablehnung ist die Liste der erkannten Formen in `SPEC-036`; dass sie abschließend ist, ist eine Lesart, die der Nutzer nicht bestätigt hat (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`), und das Lastenheft ([TDS-FA-11](../../../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) schließt `OR` nicht aus. Am Zielbild gemessen ist es eine Abweichung des Servers, kein Ziel.
