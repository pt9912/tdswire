# Ein Test ändert die Standardzone der JVM, und kein Sensor hält die sequenzielle Ausführung

**Sub-Area:** `*` (Tests, Build und Gates)

Ein Test setzt einen JVM-weiten Zustand (`TimeZone.setDefault`) und stellt ihn im finally-Block zurück. Das ist nur sicher, solange die Tests nicht parallel laufen; die Test-JVM läuft heute ohne parallele Ausführung und ohne Forks, aber kein Gate und kein Test prüft diese Voraussetzung. Ein späteres `maxParallelForks` oder eine parallele Testausführung würde andere Tests unbemerkt beeinflussen.
