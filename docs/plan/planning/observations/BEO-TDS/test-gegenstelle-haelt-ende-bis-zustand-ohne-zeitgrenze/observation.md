# Eine Test-Gegenstelle verzögert das Ende per `yield`-Schleife, bis ein Zustand verlassen ist, ohne eigene Zeitgrenze

**Sub-Area:** `*` (Spezifikation, Listener)

`FakeHandshakeChannel.holdEndWhile`, `ScriptedWire.holdEndWhile` und die Vorbelegung in `Session`/`WireSession` halten Ende und Lesefehler, solange der Zustand `RUNNING`, `STREAMING` oder `CANCELLING` ist (`while (holdEndWhile()) yield()`), weil ein Ende während der Anfrage jetzt ein Disconnect ist. Bleibt der Zustand je in einem Anfragezustand hängen, wird aus einem Assertionsfehler ein Warten bis zur Zeitgrenze des Tests.
