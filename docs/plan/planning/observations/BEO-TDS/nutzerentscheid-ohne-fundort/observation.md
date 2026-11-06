# Ein Nutzerentscheid steht in der ADR als Zitat mit Datum, ohne Fundort

**Sub-Area:** `*` (Entscheidungs-Slices, Testserver)

[ADR-0036](../../../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) gibt den Entscheid „deine empfehlungen“ (2026-11-06) als Zitat mit Datum wieder; wo er steht (Sitzung, Nachricht), nennt sie nicht. Der Verifier kann den Entscheid nicht gegen das Original prüfen und wertet ihn als berichtet. Die Klasse berührt `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` nicht: dort ist die Lesart unbestätigt, hier die Quelle ungenannt.
