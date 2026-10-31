# Eine Fehlerantwort wird hinter ein halb geschriebenes Token gesetzt

**Sub-Area:** `*` (Streaming, Sequenzierer, Paket-Schreiber)

Fällt ein Schritt der Senke (Paketbau, Kanal) mitten in einem Token, schreibt der Fehlerpfad ERROR und DONE hinter das halbe oder fehlende Stück; der Client sähe einen Tokenstrom mit falscher Token-Grenze statt des Fehlers. Die Zusage „Fehlerantwort nur, solange der Strom synchron ist“ braucht eine Prüfung der Token-Grenze am Strom, nicht nur an der Fangstelle des Sequenzierers.
