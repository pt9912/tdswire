# Ein gemeinsamer Test-Helfer wird für einen Test verlängert und verlängert dabei die Zeitaussage jedes anderen Aufrufers

**Sub-Area:** `*` (Tests, Review)

Ein Helfer, den viele Tests teilen (hier `eventually` mit rund 90 Aufrufen), bekommt eine längere Zeitgrenze, weil ein Test sie braucht. Zwei andere Tests (`HandshakeWiringTest`) nutzten die alte Grenze als Teil ihrer Aussage: ein Warten, das nach rund fünf Sekunden endet, belegt `LOGIN_TIMEOUT=1` gegen den Standard von 15 s. Mit 60 s blieben beide Tests grün, auch wenn der Wert der Konfiguration verloren ging (Mutationen H1 und H2 überlebten). Der Autor sah es nicht; der Review fing es mit Mutationen an Tests außerhalb des Diffs. Verwandt, aber anderer Mechanismus: `BEO-TDS/test-kopplung-an-poolgroesse-unbenannt` (dort eine unbenannte Kopplung an eine Zahl, hier eine geänderte Zahl eines geteilten Helfers).
