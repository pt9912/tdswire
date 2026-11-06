# Der Log-Adapter des Testservers zählt und meldet Verluste an den Rändern der Frist von `close` nicht vollständig

**Sub-Area:** `*` (Testserver, Log)

Gelesen, nicht gemessen (Review F-8): (a) das Ereignis, das der Schreiber bei hängendem Kanal in der Hand hält, zählt weder in der Rest-Zahl noch in `dropped`; (b) ein Ereignis nach `close` wird gezählt, aber nie gemeldet; (c) ein `accept`, das die Phase vor `close` las und nach dem Leeren einreiht, geht ungezählt verloren; (d) schreibt ein zuvor hängender Kanal nachträglich, kann dieses Ereignis nach `tdswire: gestoppt` stehen. Der Erhaltungstest (3001 Zeilen) gilt für einen Leser, der innerhalb der Frist beginnt. Die Grenze steht im KDoc von `LogAdapter` („Grenzen der Verlust-Zählung“). `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` deckt Last, nicht diese Ränder.
