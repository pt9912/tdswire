# Ein Test belegt eine Konfiguration nicht, weil das JDK dieselbe Wirkung schon im Default hat

**Sub-Area:** `*` (Tests, Verifikation)

Ein Test soll belegen, dass eine Konfiguration wirkt (Mindestversion, Suiten), wird aber von der Voreinstellung der Laufzeit getragen oder gibt beiden Seiten dieselbe Konfiguration: das JDK sperrt TLS 1.1 ohnehin, und Server und Gegenstelle bekamen im Positivtest dieselben Werte. Entfernt jemand die Zeile, die die Konfiguration setzt, bleibt der Test grün. Erst eine Mutation der Konfiguration zeigt es; die Abhilfe ist ein Negativtest ohne gemeinsame Version oder Suite mit einer Gegenprobe. Verwandt, aber eigener Mechanismus: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (dort variiert die Eingabe des Tests nicht, hier trägt die Voreinstellung der Laufzeit die Aussage).
