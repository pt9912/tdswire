# Eine DoD nennt eine Entscheidung des Nutzers als Inhalt der Lieferung

**Sub-Area:** `*` (Planung, Slice-Schnitt)

Die Liefer-Punkte eines Slice verlangen Ergebnisse "nach den Entscheidungen des Nutzers". Der Implementer kann den Punkt nicht schließen; der Haken bleibt offen, der Slice kommt nicht nach `done/`, und die Rückführung `in-progress` → `open` ist zwar benannt, der Plan sagt aber nicht, welcher Weg gilt. Der Schnitt gehört in die Planung: Entscheidung und Lieferung in getrennte Slices, die DoD nennt nur das Lieferbare. Verwandt, eigener Mechanismus: `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (dort Abhängigkeit von einem Slice, hier von einer Entscheidung).
