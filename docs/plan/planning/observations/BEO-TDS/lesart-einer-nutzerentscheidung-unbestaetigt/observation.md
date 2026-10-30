# Eine Lesart zu einer Nutzerentscheidung ist Setzung des Slice und vom Nutzer unbestätigt

**Sub-Area:** `*` (Typabbildung, Spezifikation)

Der Nutzer entscheidet einen Grundsatz (hier: Genauigkeit größer als die Scale wird abgewiesen statt gerundet). Der Slice braucht für Randfälle eine engere Lesart (nachlaufende Nullen zählen nicht, 100-ns-Raster, Grenze von 1000 Stellen der Mantisse) und setzt sie. Sie steht in Spec, KDoc und Tests als Festlegung ohne Messung und ohne Bestätigung; ein Slice-Abschluss kann sie nicht schließen, und ein Slice trägt sie nicht, weil „Nutzerentscheidung“ kein Träger ist.
