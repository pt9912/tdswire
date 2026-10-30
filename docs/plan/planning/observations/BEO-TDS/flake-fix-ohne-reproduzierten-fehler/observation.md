# Ein Flackern wird durch Analyse behoben, der Fehler ist nie wieder aufgetreten

**Sub-Area:** `*` (Tests, Verifikation)

Ein Test schlägt einmal unter Last fehl (hier: 1 von 200 Wiederholungen bei 60 Busy-Loops), die Änderung folgt der Analyse der Reihenfolge, und ein Lauf mit 200 Wiederholungen unter derselben Last bleibt mit und ohne Änderung grün. Der Fix ist begründet, seine Wirkung nicht gemessen; die Mutation „Änderung zurücknehmen“ überlebt. Verwandt: `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (dort verdeckt der Cache die Wiederholung, hier reproduziert auch der frische Lauf den Fehler nicht).
