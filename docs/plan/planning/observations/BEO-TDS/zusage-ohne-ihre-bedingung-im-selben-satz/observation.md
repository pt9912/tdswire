# Eine Zusage in KDoc oder Spezifikation steht ohne ihre Bedingung im selben Satz

**Sub-Area:** `*` (Bibliothek, Dokumentation)

Ein Satz sagt „genau ein Ereignis je Server“ (`SERVER_STOPPING`, `SERVER_STOPPED`) oder „darf nebenläufig gerufen werden“ (`stop`), und die Bedingung steht nicht im Satz: das Ereignis entsteht nur, wenn `stop` gerufen wird; der Aufruf aus der Senke blockiert; ein abgebrochener Aufruf gibt das Ereignis zum Ende erst mit dem nächsten ab. Der Leser nimmt die Zusage ohne ihre Grenze. Verwandt: `BEO-TDS/aussagen-ueber-messung-hinaus` (dort ist die Aussage breiter als der Beleg, hier ist sie breiter als ihre Bedingung).
