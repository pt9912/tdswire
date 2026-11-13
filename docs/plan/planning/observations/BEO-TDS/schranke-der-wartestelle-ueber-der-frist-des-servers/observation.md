# Die Schranke einer Wartestelle im Test liegt über einer Frist des Servers, die die erwartete Lage vorher beendet

**Sub-Area:** `*` (Tests, Prozesslauf)

Ein Test wartet auf ein Signal mit einer Schranke von 60 s, der Server beendet die offene Anfrage aber nach der Voreinstellung von `QUERY_TIMEOUT` (30 s) selbst. Bleibt das Signal aus, wird der Test an einer Folge rot (fehlendes `DONE[ATTN]`), nicht an der ausbleibenden Zeile. Vorsorge in diesem Fall: `QUERY_TIMEOUT` auf das Maximum 3600 gesetzt. Abgegrenzt von `BEO-TDS/wanduhr-tests-flackern-unter-last` (dort ein roter Lauf unter Last) und `BEO-TDS/zeitgrenze-des-tests-nahe-an-ungeklaerter-schwelle` (dort eine Schwelle, deren Ursache ungeklärt ist; hier ist sie bekannt).
