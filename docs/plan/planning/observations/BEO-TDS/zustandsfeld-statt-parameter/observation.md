# Ein Wert wird als Zustandsfeld eines geteilten Objekts geführt statt als Parameter durch die Aufrufe

**Sub-Area:** `*` (Bibliothek, Protokollschicht)

Die Kennung der Anfrage wird im `ErrorMapper` als Feld geführt (gesetzt in `RequestLoop.answer`, gelöscht im `finally`), weil sie sonst durch 13 Aufrufstellen in 8 Dateien gereicht würde. Das trägt, solange eine Schleife eine Anfrage nach der anderen beantwortet und das Aufgeben die Verbindung schließt; fällt eine der beiden Bedingungen, bleibt eine falsche oder fehlende Kennung am Feld hängen, ohne dass der Compiler es zeigt.
