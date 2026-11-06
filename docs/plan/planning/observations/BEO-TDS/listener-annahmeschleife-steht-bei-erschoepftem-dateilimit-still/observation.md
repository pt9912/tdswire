# Bei erschöpftem Limit offener Dateien endet die Meldestelle des Annehmens mit `NoClassDefFoundError`, und die Annahmeschleife steht ohne Meldung

**Sub-Area:** `*` (Transport, Listener)

Ist das Limit offener Dateien einer JVM erschöpft und ist die Klasse, die `TdsListener.acceptOne` im `failed`-Callback braucht (`Accept.Failed`), noch nicht geladen, kann die JVM sie nicht laden: der Callback endet mit einem `NoClassDefFoundError` (Thread `tdswire-listener-io-1`), die Continuation wird nicht fortgesetzt, die Annahmeschleife steht, und weder ein `ACCEPT_FAILED` noch eine Rückkehr kommt an. Gemessen in `AcceptChild` (Test-Kind-JVM mit `ulimit -n`), indem `preloadClasses()` entfällt; mit vorgeladenen Klassen entsteht `ACCEPT_FAILED` und der Server nimmt danach wieder an. Ob das ein Produktfehler ist, der eine Änderung braucht (etwa die Klassen des Fehlerpfads beim Start laden), ist nicht entschieden.
