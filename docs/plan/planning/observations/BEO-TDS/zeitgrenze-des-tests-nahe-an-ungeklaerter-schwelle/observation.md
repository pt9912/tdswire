# Die Zeitgrenze eines Tests liegt nahe an einer Schwelle, deren Ursache nicht untersucht ist

**Sub-Area:** `*` (Tests, Verifikation)

Ein Test gegen den Referenzclient wählt eine Frist (hier Command-Timeout 2 s), bei der das beobachtete Verhalten eintritt; bei einer längeren Frist (3 s) trat es nicht ein, der Grund ist nicht untersucht, der Erkundungslauf ist kein Test und nicht nachlesbar. Ein schnellerer oder stärker belasteter Host kann den Test rot machen, ohne dass der Befund falsch wäre. Die Grenze gehört entweder erklärt oder als Wartungsrisiko am Test genannt.
