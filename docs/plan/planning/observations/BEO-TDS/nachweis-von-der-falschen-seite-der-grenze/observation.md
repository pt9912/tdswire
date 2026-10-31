# Ein Test belegt die Anforderung von innen statt am Zugangsweg, den die Nachweis-Zeile nennt

**Sub-Area:** `*` (Tests, Verifikation)

Die Nachweis-Zeile des Lastenhefts nennt einen Zugangsweg (bei [TDS-RB-04](../../../../../../spec/lastenheft.md#tds-rb-04--externes-backend): „Integration mit einem In-Memory-Testbackend“). Der Test liegt im Modul, sieht dort `internal` und baut Backend und Server mit Typen zusammen, die der Integrator nicht erreicht. Er besteht auch dann, wenn der Zugangsweg nach außen fehlt; die Lücke zeigt erst ein Test von der Seite des Integrators. Verwandt, aber eigener Mechanismus: `BEO-TDS/test-belegt-nur-den-fake` (dort ist die geprüfte Stelle der Fake, hier die Seite der Grenze) und `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test` (dort fehlt der Test über die Grenze zweier Schichten, hier liegt der Test auf der falschen Seite der Modulgrenze).

## Benannt, nicht gezählt

Anmerkung des Nutzers am 2026-10-31 zum Befund `BEO-TDS/oeffentlicher-einstieg-ohne-traeger`: Hätten passende Tests über die öffentliche API bestanden, wäre der fehlende Einstieg aufgefallen. Der Satz ist keine Messung und bewegt den Zähler nicht.
