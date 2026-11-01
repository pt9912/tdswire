# Der Container-Teil von SPEC-052 hat keinen Test, der ihn nach dem Umbau trägt

**Sub-Area:** `*` (Spezifikation, Abdeckung)

`SPEC-052` sagt, das Container-Abbild entstehe aus dem Klassenpfad des Testservers und der Lauf `make laufzeittest` prüfe den Testserver-Container. Der Tag `@spec` nennt heute nur den Lizenztext. `make spec-abdeckung` zeigt eine Waise für diesen Teil erst, wenn der Umbau den Testserver trägt; vorher sagt der Zielzustand-Vermerk, dass die Aussage noch nicht gilt.
