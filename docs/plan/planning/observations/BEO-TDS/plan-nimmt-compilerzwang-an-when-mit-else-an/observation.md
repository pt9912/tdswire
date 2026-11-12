# Der Plan nimmt an, der Compiler erzwinge die Ergänzung eines `when`, das einen `else`-Zweig hat

**Sub-Area:** `*` (Planung)

Eine Plan-Aussage der Form „der Compiler erzwingt die Ergänzung“ gilt nur für ein `when` über eine Aufzählung ohne `else`. Hat das `when` einen `else`-Zweig, übersetzt es mit dem neuen Mitglied unverändert, und der Schutz („das Mitglied bewirkt keinen Zustandswechsel“) liegt allein im Test. Der Plan listete die Datei trotzdem als zu ändernde Datei; die Verifikation fand die Abweichung (Plan gegen Diff). Prüfpunkt beim Schreiben des Plans: die Fundstellen des Typs auf `else` ansehen.
