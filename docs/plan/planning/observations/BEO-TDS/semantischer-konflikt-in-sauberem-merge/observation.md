# Ein Merge von `main` in einen Zweig, der eine Prüfung lockert, trägt eine Fundstelle der Prüfung, die nur `main` kennt, und kein Textkonflikt zeigt sie

**Sub-Area:** `*` (Slice-Zweige, Zusammenführung)

Der Zweig lockerte die Schlüsselprüfung (`CREATE TABLE` ohne Schlüssel) in `TableStore.insertBatch`; `main` hatte `TableStore.update` mit derselben Prüfung neu angelegt. `git merge-tree` meldete alle drei Dateien als sauber zusammengeführt, und die Tests beider Seiten blieben grün, weil keiner `update` auf einer Tabelle ohne Schlüssel rief; das Ergebnis lehnte ein Mehrzeilen-`UPDATE` mit `IllegalArgumentException` ab. Der Review fand es in einer Kopie mit der Zusammenführung und einem Probe-Test. Prüfpunkt beim Merge: Grep nach der gelockerten Prüfung im Ergebnis des Merge, nicht nur im Diff des Zweigs. Verwandt, andere Klasse: `BEO-TDS/zielbild-aussage-durch-merge-ueberholt` (eine Aussage des Plans wird falsch).
