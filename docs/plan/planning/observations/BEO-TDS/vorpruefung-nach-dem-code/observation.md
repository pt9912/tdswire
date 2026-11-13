# Die Vorprüfung, die der Plan vor dem Bau verlangt, entsteht am fertigen Code

**Sub-Area:** `*` (Slice-Planung, Implementierung)

Ein Plan verlangt eine Vorprüfung mit Hypothesen als „erster Schritt, vor dem Bau“ und eine Fortschreibung vor dem Code, falls eine fällt. Das Ergebnis entstand erst am Lauf des fertigen Tests; der Plan-Commit steht vor dem Test-Commit, die Ergebnisse aber nicht vor dem Code. Fiele eine Hypothese, geschähe der Rückfall nach dem Bau statt davor. Die Vorgabe „vor dem Bau“ ist damit für den Fall des Fallens nicht geprüft worden.
