**Vorgang:** slice-qa-sicherheit
**Fund:** `TlsRenegotiationTest`: Nach dem Handschlag in TLS 1.2 verlangt die Gegenstelle dreimal eine Neuverhandlung; die Schleife beantwortet jede, Nutzdaten laufen in beide Richtungen unversehrt, weder Daten der Gegenstelle noch des Servers erscheinen im Klartext auf dem Draht. Eine Mutation, die die Neuverhandlung nicht mehr beantwortet, macht den Test rot.
**Stand:** offen — erster Beleg.
