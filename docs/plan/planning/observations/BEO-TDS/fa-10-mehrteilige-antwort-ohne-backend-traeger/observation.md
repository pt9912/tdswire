# Mehrteilige Antworten und Ausgabewerte sind am Referenzclient nicht prüfbar, weil kein Backend sie liefert

**Sub-Area:** `*` (Simulations-Backend, Kompatibilitätstests)

Der Codec baut die Antwortform für mehrere Ergebnismengen und Ausgabewerte ([TDS-FA-10](../../../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)); das Simulations-Backend liefert sie nicht (zwei Anweisungen in einem Text: `Unsupported SQL.`, 50001; ein Ausgabeparameter bekommt keinen Rückgabewert). Am Referenzclient ist die Form daher nicht messbar, und die Stufe der Szenarien kann FA-10 nicht deklarieren. Ein Träger der Backend-Seite ist nicht benannt.
