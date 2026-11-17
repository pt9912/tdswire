# Szenarien der Messreihe teilen den Bestand einer Tabelle und stören einander

**Sub-Area:** `*` (Messreihe am Draht, `referenztest-dotnet/Vergleich/MessungWireTests.cs`)

Die Szenarien der Messreihe am Draht laufen nacheinander über dieselbe Tabelle `wire_dml`. Das Fehlerszenario des Mehrfach-RPC legte seine verletzte Zeile (Id 100) im Aufbau an; im Gesamtlauf hatte `dml-rpc` sie vorher gelöscht (`DELETE … WHERE Id >= 10`), und das Szenario zeigte keinen Fehler. Das Szenario hat danach eine eigene Tabelle `wire_batch` mit benanntem Primärschlüssel, legt die Zeile vorab an und prüft den Fehler im Beleg (Messprotokoll, Absatz „Frühere Läufe“). Ein Szenario, das einen Zustand voraussetzt, den ein anderes verändert, zeigt je nach Reihenfolge und Teilmenge (`MESSUNG_NUR`) ein anderes Ergebnis.
