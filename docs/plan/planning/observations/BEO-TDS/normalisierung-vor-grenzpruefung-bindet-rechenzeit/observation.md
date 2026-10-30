# Eine Normalisierung läuft vor der Grenzprüfung und bindet Rechenzeit

**Sub-Area:** `*` (Codec und Typabbildung)

Ein Wert wird skaliert oder gekürzt, bevor seine Größe geprüft ist, und der Aufwand wächst mit dem Wert: `setScale` im Encoder vor der Precision-Prüfung, `stripTrailingZeros` in der Abbildung vor der Mantissengrenze. Das Ergebnis ist am Ende ein definierter Fehler, die Rechenzeit (oder bei sehr großem Exponent eine Ausnahme statt des Fehlerwerts) aber nicht begrenzt. Der Review findet es nur, wenn er den Pfad mit einem extremen Wert nachstellt; über die Abbildung war der Encoder-Pfad nicht erreichbar.
