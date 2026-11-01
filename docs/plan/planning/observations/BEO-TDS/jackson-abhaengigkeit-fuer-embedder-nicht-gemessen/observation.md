# Ob ein Einbettender ohne Jackson auf dem Klassenpfad auskommt, ist nicht gemessen

**Sub-Area:** `*` (öffentlicher Einstieg, Abhängigkeiten)

Die YAML-Bibliothek hängt als `implementation` am Projekt und wird nur für `simulationFile` gebraucht. Die öffentliche Signatur des Einstiegs trägt keinen Jackson-Typ (gelesen). Das Source-Set `integrator` nimmt Jackson nur als `runtimeOnly`; ein Lauf ohne Jackson auf dem Klassenpfad ist nicht gemessen. [ADR-0025](../../../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Proposed`) nimmt `simulationFile` und Jackson aus der Bibliothek.
