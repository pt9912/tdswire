# Ein neuer Parametertyp wird im Parser gebaut, und andere Auswerter des Typs bleiben auf dem alten Stand

**Sub-Area:** `*` (Parser der RPC-Parameter, Spezifikation, E2E-Stufe, Nutzer-Doku, Test-Vektoren)

Der Slice nimmt `DECIMALN` mit kurzem Wert und `DATETIMN` mit 8 Byte an, weil mssql-jdbc sie sendet. Der Parser ist angepasst; die anderen Stellen, die denselben Typ auswerten, sagen weiter „abgelehnt“ oder „nicht gemessen“: `SPEC-030` und die Stufe 7 von `tools/e2e.sh` (Re-Review R-1), die Nutzer-Doku zum `MERGE` (R-2, R-3), ein Test-Vektor, der gemessen heißt und Werte außerhalb der Aufnahme trägt (R-5). Ein Fall der Messung (`0,00` als `DECIMALN` mit Länge 2) hat bis zur Verifikation keinen Vektor (V-8). Das Muster: ein neuer Typ hat mehr Fundstellen als den Parser; die Suche danach (Grep über `spec/`, `docs/`, `tools/`) gehört zum Bau.
