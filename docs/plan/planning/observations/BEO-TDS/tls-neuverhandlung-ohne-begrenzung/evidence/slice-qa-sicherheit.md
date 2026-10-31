**Vorgang:** slice-qa-sicherheit
**Fund:** `TlsRenegotiationTest`: Nach dem Handschlag in TLS 1.2 verlangt die Gegenstelle dreimal eine Neuverhandlung; die Schleife beantwortet jede, Nutzdaten laufen in beide Richtungen unversehrt, die Marke des Servers erscheint nicht im Klartext im Ausgang des Servers. Eine Mutation von `TlsReader.afterRecord`, die NEED_WRAP ablehnt, machte den Test im Lauf rot (Lauf, kein abgelegter Beleg).
**Stand:** offen — erster Beleg.
