# Ein Referenzclient-Test kann nicht zeigen, dass der Server bei Class 20 schließt, weil der Client selbst schließt

**Sub-Area:** `*` (Tests, Kompatibilität)

Microsoft.Data.SqlClient 7.1.1 beendet die Verbindung bei einem Fehler der Class 20 selbst (Messung S03/S04 in [ADR-0007](../../../../adr/0007-fehler-und-attention.md)). Ein Test am Referenzclient, der „Folgeanfrage scheitert, `State` ist `Closed`“ prüft, belegt deshalb das Verhalten des Clients; ob der Server schließt, belegt allein ein Rohbytes-Test (`BEO-TDS/server-schliesst-bei-class-20-pipeline`). Wer die Aussage „Server schließt bei Class 20“ auf den Referenzclient-Test stützt, trägt sie breiter als der Beleg.
