# Ein Fund der Abnahme gegen den Testserver ist erst nach einer Messung am echten Server eingeordnet

**Sub-Area:** `*` (Abnahme, Testserver)

Fund F3 der Abnahme mit d-migrate (Fremdschlüssel mit `on_delete: restrict` oder `no_action` endet mit Exit 5) sah wie eine Abweichung des Testservers aus und blieb im Abnahmeprotokoll zunächst als offene Art (b) oder (c) stehen. Erst der Lauf derselben Fälle am echten SQL Server 2022 zeigte: Verhalten des Clients, am echten Server gleich, keine Abweichung des Testservers. Ohne diese Messung wäre ein Folge-Slice für den Testserver entstanden. Prüfpunkt beim Einordnen eines Fundes einer Abnahme: bevor ein Fund als Fehler des Servers oder als Folge-Slice gilt, die Fälle am echten Server mit demselben Client fahren (EULA-Freigabe des Nutzers für das gepinnte Image gilt).
