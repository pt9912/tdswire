# Die Zeilenzahl von `UPDATE` und `DELETE` ist eine feste Konstante des Callbacks, keine Auswertung der Anweisung

**Sub-Area:** `*` (Testserver, Kompatibilitätslauf)

[SPEC-035](../../../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) sagt, die Zahl des Callbacks sei die Zahl der betroffenen Zeilen. Im Testserver des Kompatibilitätslaufs liefert der Callback der Tabelle `person` für `UPDATE` 2 und für `DELETE` 3 (der .NET-Test erwartet dieselben Zahlen); der echte SQL Server meldet für dieselben Anweisungen je 1. Zwei von 25 Schritten des Live-Laufs prüfen die Zählung deshalb nicht; eine Konstante der Fixture trägt keine Aussage über den Server.
