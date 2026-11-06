# Der Text der Antwort auf `Cancel()` weicht vom echten SQL Server ab, und der echte Server liefert zwei Texte

**Sub-Area:** `*` (Abbruch, Fehlertexte)

Nach `Cancel()` endet der Strom am echten Server (2022 und 2025) mit `Number 0, Class 11, State 0` und dem Text „A severe error occurred on the current command.  The results, if any, should be discarded.“, in einigen Läufen mit dem Zusatz „ Operation cancelled by user.“, in anderen ohne; die Bedingung des Unterschieds ist nicht untersucht, beide Instanzen zeigten beide Varianten. Der Server dieses Repos sendet nur „Operation cancelled by user.“ ([ADR-0027](../../../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 baut `DONE[ATTN]`). Woher der Text beim Server stammt (vom Client erzeugt oder vom Server gesendet), ist nicht festgestellt; dafür braucht es die Messung am Draht. Die Ausnahmeliste führt deshalb zwei Regeln für den Schritt; ein dritter Text macht den Lauf rot.
