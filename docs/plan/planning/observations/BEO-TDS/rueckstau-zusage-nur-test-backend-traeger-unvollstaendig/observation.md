# Die Rückstau-Zusage gilt nur für das Test-Backend, und der Träger deckt den Rest nicht

**Sub-Area:** `*` (Streaming, Qualität)

Die Spezifikation belegt den Rückstau am Test-Backend über Zähler, an einem Draht ohne Netz; sie schließt Socket, blockierendes Backend und Simulations-Backend ausdrücklich aus. `TDS-QA-02` verlangt begrenzten Speicher für das Produkt. Der Ausgang „weiter offen“ der Evidence von `slice-backend-streaming-nachweis` zeigt auf `slice-qa-performance`; dessen DoD misst den Socket im Produktivprozess unter Last, nennt aber weder das blockierende Backend noch das Simulations-Backend.
