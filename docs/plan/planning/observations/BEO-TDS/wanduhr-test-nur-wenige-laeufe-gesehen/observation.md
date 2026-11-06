# Tests mit Wanduhr, Pipe-Füllung und Fristen sind nur in wenigen Läufen unter Last gesehen

**Sub-Area:** `*` (Tests, Verifikation)

`TestserverLogProcessTest` und `LogAdapterTest` tragen Fristen (2 s, 250 ms, 5 s), eine gefüllte Pipe (64 KiB), 1 500 Anmeldungen je Lauf und eine Schranke von 15 s gegen erwartete etwa 2,5 s. Die Verifikation sah fünf volle Läufe bei Last 18 bis 26 ohne Flanke; fünf Läufe belegen keine Stabilität. Die Schranken sind weit; ein Flackern ist nicht beobachtet und nicht ausgeschlossen.
