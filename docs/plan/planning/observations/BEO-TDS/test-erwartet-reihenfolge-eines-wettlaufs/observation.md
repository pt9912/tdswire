# Ein Test erwartet die Reihenfolge eines Wettlaufs: Client schließen, Server sofort beenden, Ereignis erwarten

**Sub-Area:** `*` (Tests, Verifikation)

Ein Test schließt den Client (oder beendet den Prozess) und beendet den Server sofort, erwartet aber ein Ereignis, das der Server erst nach der Antwort oder nach dem Schließen der Verbindung abgibt (`LOGIN_ACCEPTED` nach der Anmeldeantwort, `CONNECTION_CLOSED` nach dem Schließen). Unter Last überholt das Ende des Servers das Ereignis, der Test wird rot und ist im nächsten Lauf grün. Die Abhilfe ist ein Warten auf das Ereignis vor dem Beenden (Latch, `eventually`) oder ein Beenden mit offenem Client (Grund `SHUTDOWN`, deterministisch). Verwandt: `BEO-TDS/wanduhr-tests-flackern-unter-last` (dort wird die Wanduhr verdächtigt, die Hypothese ist dieselbe Reihenfolge), `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge` (dort hängt eine Mutation an der Reihenfolge, hier der Test selbst).
