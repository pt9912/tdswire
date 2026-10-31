# Ein Test trägt nach einem Umbau eine andere Zeile, als Name und KDoc sagen

**Sub-Area:** `*` (Tests, Verifikation)

Ein Umbau setzt eine neue Frist oder einen neuen Zweig vor eine bestehende (hier: das Warten auf das erste Byte vor den Treiber). Ein vorhandener Test bleibt grün, beendet seinen Fall aber jetzt an der neuen Zeile; Name und KDoc nennen weiter die alte, und deren Entfernung färbt keinen Test (Mutation an der alten Zeile grün, an der neuen rot). Verwandt, aber eigener Mechanismus: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (dort fehlt der Test zur Zeile, hier ist er da und trägt eine andere) und `BEO-TDS/aussagen-ueber-messung-hinaus` (dort ist die Aussage breiter als der Beleg, hier stimmt der Beleg, aber nicht seine Beschriftung).
