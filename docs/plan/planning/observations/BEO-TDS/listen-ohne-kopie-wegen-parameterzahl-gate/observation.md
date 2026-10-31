# Eine Gate-Grenze der Parameterzahl hält eine Datenklasse davon ab, ihre Listen zu kopieren

**Sub-Area:** `*` (Simulations-Backend, Gates)

`SimTable` hat acht Konstruktorparameter und ist eine `data class`; als `class` mit kopierendem Konstruktor bricht `make detekt` mit `LongParameterList` (Grenze 6). Die Tabelle behält deshalb `columns` und `primaryKey` unkopiert, ihr Index und ihre Validierung gelten nur zum Zeitpunkt der Übergabe; der Katalog kopiert seine Liste. Die Zusage steht in KDoc und `SPEC-035` („der Aufrufer ändert sie nicht“), kein Test und kein Gate hält sie. Lösungswege ohne Lockerung der Schwelle: ein Fabrikaufruf oder ein sekundärer Konstruktor, der kopiert, oder ein Zuschnitt von `SimTable` auf weniger Parameter; eine Lockerung von `LongParameterList` bräuchte eine ADR (`AGENTS.md` §3.6).
