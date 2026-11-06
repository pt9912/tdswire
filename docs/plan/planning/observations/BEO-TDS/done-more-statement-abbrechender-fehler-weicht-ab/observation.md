# Bei statement-abbrechenden Fehlern schreibt der Sequenzierer eine andere Variante als der echte Server

**Sub-Area:** `*` (Sequenzierer, Tokenstrom)

Bei einem statement-abbrechenden Fehler sendet der echte SQL Server `DONE` mit `MORE` und `ERR`; die Spezifikation legt fest, dass der Sequenzierer die Variante ohne `MORE` schreibt, weil `Failure` keine Klasse trägt. Das ist eine bekannte Abweichung vom gemessenen Referenzverhalten (`TDS-QA-01`), aus dem Code begründet und als Festlegung geführt statt als Schuld. `BEO-TDS/messung-am-echten-sql-server-fehlt` führt nur die fehlende Messung am Produkt, nicht diese Abweichung.
