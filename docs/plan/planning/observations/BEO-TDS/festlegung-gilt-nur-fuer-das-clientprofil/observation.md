# Eine Festlegung der Antwort trägt nur für die Clients des Clientprofils

**Sub-Area:** `*` (Validator-Lauf zur Welle [welle-testserver-manuell](../../../welle-testserver-manuell.md))

Die Spezifikation legt Felder der Server-Antwort fest, ohne sie am Produkt zu messen (PRELOGIN-VERSION `0.0.0.0`); gemessen ist nur, dass der Referenzclient des Clientprofils sie annimmt. Ein Client außerhalb des Profils (hier der JDBC-Treiber mssql-jdbc in d-migrate 1.12.0) kann dieselbe Festlegung ablehnen: Fehlertext `SQL Server version 0 is not supported by this driver`, `Server major version:0`. Verwandt, aber getrennt gezählt: [messung-am-echten-sql-server-fehlt](../messung-am-echten-sql-server-fehlt/observation.md) (was ein echter SQL Server sendet) und [aussagen-ueber-messung-hinaus](../aussagen-ueber-messung-hinaus/observation.md) (Aussagen breiter als die Messung); hier ist der Gegenstand der Client, nicht die Aussage.
