# Eine Tabelle von Hand neben der Folge treibt auseinander, sobald die Folge sich ändert

**Sub-Area:** `*` (Tests)

Ein Test führt eine Folge von Schritten (Nachrichten, Aufrufe) und daneben von Hand gepflegte Tabellen, die dem ersten roten Zähler die Anfrageart nachschlagen (`REPLY_KINDS`, `CALL_KINDS` in `RequestSequenceTest`), damit die Meldung des Tests sagt, welcher Schritt betroffen ist. Die Tabellen und die Folge halten keinen Sensor gegeneinander: Wer die Folge ändert, ändert die Tabellen mit oder die Meldung nennt den falschen Schritt, und der Test bleibt dabei grün oder rot aus dem richtigen Grund.
