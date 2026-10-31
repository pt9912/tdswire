# Ein OutOfMemoryError im Test bricht den Lauf ab, statt den Test zu röten

**Sub-Area:** `*` (Tests)

Wirft ein Test oder der geprüfte Code einen `OutOfMemoryError`, verlässt er die Testfunktion und bricht den Lauf ab: der Test steht als SKIPPED, Folgetests laufen nicht, die Meldung nennt keinen Testnamen. Das Gate bleibt rot, die Ursache ist im Log nicht benannt.
