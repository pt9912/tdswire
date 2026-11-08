# Der Träger einer bekannten Abweichung steht in einem Slice, den `main` inzwischen umgeschrieben hat

**Sub-Area:** `*` (Slice-Pläne, Register)

Ein Slice trägt eine gemessene, nicht gebaute Abweichung mit einer Risikozeile in einem Slice, der noch nicht gebaut ist. Läuft der Zweig länger, schreibt `main` den Träger-Slice um (hier: Neuschnitt nach [ADR-0041](../../../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) und [ADR-0042](../../../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)); die Zeile kollidiert beim Merge oder fällt weg, und die Abweichung hat keinen Träger mehr, der die Closure überlebt. Ein Risiko mit „offen bis zur Closure“ ist kein Lieferpunkt. Gegenmittel in diesem Fall: ein eigener kleiner Bau-Slice mit Lieferpunkten statt einer Risikozeile in einem fremden Slice.
