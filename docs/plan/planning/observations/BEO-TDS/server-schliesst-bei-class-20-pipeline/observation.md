# Das Schließen des Servers bei Class 20 mit gepipelineten Anfragen ist nur am Test-Backend belegt

**Sub-Area:** `*` (Protokollkern, Tests, Verifikation)

Nach einem Fehler der Class 20 schließt der Server die Verbindung nach der Antwort ([ADR-0007](../../../../adr/0007-fehler-und-attention.md)), auch wenn der Client weitere Anfragen schon gesendet hat; es darf kein weiteres Byte folgen. Das belegt allein ein Rohbytes-Test gegen das Test-Backend (Selbstkonsistenz); der Referenzclient kann es nicht zeigen, weil er bei Class 20 selbst schließt (`BEO-TDS/client-schliesst-bei-class-20-selbst`), und am echten SQL Server mit gepipelineten Anfragen ist es nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`). Ob der Schluss mit ungelesenen Bytes im Empfangspuffer einen RST statt eines FIN erzeugt, ist aus dem Code geschlossen und nicht aufgetreten.
