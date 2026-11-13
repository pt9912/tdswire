# Ein Test leitet seine Datenmenge aus Puffergrößen der Umgebung ab und gilt nur für Hosts mit ähnlichen Obergrenzen

**Sub-Area:** `*` (Tests, Prozesslauf)

Der Prozesslauf-Test wählt die Menge der ungelesenen Antwort als Vierfaches der Summe der Obergrenzen von `tcp_wmem` und `tcp_rmem` (gelesen im Namensraum der Kind-JVM) mit einer Setzung von 128 MiB als Grenze. Auf einem Host mit großen Puffern bricht er mit der Meldung zur Datenmenge ab, ohne Fehler des Produkts. Belegt ist nur ein Host (Obergrenzen 4 194 304 und 6 291 456).

Trigger: erster Host mit anderen Obergrenzen oder ein rotes Gate auf einer anderen Maschine.
