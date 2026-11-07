# Ein Messlauf ist intermittierend rot, und das Protokoll nennt zuerst weder Rate noch Ursache

**Sub-Area:** `*` (Referenzabgleich, Messwerkzeug)

Der Messlauf `make vergleichslauf-d LAUF=bulk` brach im Szenario `bulk-mehrere-nachrichten` in rund einem von vier Läufen ab (Transportfehler des Clients, Reset der Verbindung im Proxy). Das Messprotokoll sagte zunächst „ein roter Lauf“; die Rate und der Zusammenhang (TLS-Records mit mehreren Paketen einer Nachricht 7) wurden erst nach dem Review mit Wiederholungen (`MESSUNG_WIEDERHOLEN`) und einer Mutation (18 von 20 rot) belegt, der Fix (jedes Paket in einem eigenen Record) mit 64 von 64 Läufen grün.
