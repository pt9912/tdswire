# Eine Nachbesserung nach dem Review wird verifiziert, aber nicht erneut gereviewt

**Sub-Area:** `*` (Rollen, Review)

Nach dem Review-Report folgen Commits mit neuem Code oder neuen Skripten (Behebung der Befunde), die der Reviewer nicht gesehen hat; der Verifier liest und fährt sie, ein zweites Augenpaar mit dem Reviewer-Skill hat sie nicht. Die Closure führt sie als „Nachbesserung ohne zweiten Review“ und setzt die Review-Zeile der DoD nur mit diesem Vermerk (Regel `haken-vor-verifikation-gesetzt`: der Haken folgt der Deckung).
