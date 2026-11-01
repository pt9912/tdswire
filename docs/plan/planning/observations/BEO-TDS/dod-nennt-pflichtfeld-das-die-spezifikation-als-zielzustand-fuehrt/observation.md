# Eine DoD nennt ein Pflichtfeld, das die Spezifikation als Zielzustand führt und der Bau nicht trägt

**Sub-Area:** `*` (Planung, Spezifikation, Telemetrie)

Die DoD eines Slice nennt `request_id` unter den Pflichtfeldern des Ereignisses („soweit vorhanden“), die Spezifikation führt es zugleich als „Zielzustand, nicht gebaut“, weil keine gebaute Ereignisart zu einer Anfrage gehört außer dem Anfragefehler. Der Bau trägt das Feld nicht; die Abweichung lässt sich aus „soweit vorhanden“ nach beiden Seiten lesen und fiel der Verifikation auf, nicht dem Plan. Verwandt, aber eigener Mechanismus: `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt` (dort ersetzt der Bau bewusst, hier fehlt die Entscheidung) und `BEO-TDS/slice-plan-veraltet-nach-gelieferter-adr` (dort altert der Plan nach einer ADR, hier widersprechen sich Plan und Spezifikation von Anfang an).
