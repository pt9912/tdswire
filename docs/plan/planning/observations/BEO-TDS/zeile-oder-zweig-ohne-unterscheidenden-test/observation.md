# Eine Zeile oder ein Zweig des Produktcodes hat keinen Test, der ihr Entfernen unterscheidet

**Sub-Area:** `*` (Transport, Tests, Verifikation)

Eigencode trägt eine Wartebedingung, einen Fehlerzweig oder ein `NonCancellable`, und seine Entfernung lässt jeden Test grün (hier: Warten auf lebende Threads neben `isTerminated`, Aufräumen im Zweig nach dem Binden, Verdrahtung des Abweisungszweigs im echten Listener). Der Code ist da, der Beleg fehlt; Review und Verifikation finden es per Mutation, nicht durch Lesen. Verwandt, aber eigener Mechanismus: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (dort variiert die Eingabe des Tests nicht, hier gibt es zur Zeile keinen Test) und `BEO-TDS/aussagen-ueber-messung-hinaus` (dort ist die Aussage zu breit, hier der Code ohne Aussage).
