# Das Beenden aus dem Programm des Einbettenden ist nur nacheinander aus einem Thread gemessen

**Sub-Area:** `*` (öffentlicher Einstieg, Lebenszyklus)

Getestet ist: `stop` zweimal nacheinander aus einem Thread, Abbruch bei ungültiger Konfiguration, „Verbindung abgelehnt“ nach `stop`. Nicht gemessen sind `stop` aus mehreren Threads, `stop` während `start`, Zeilenquellen, die beim Beenden offen bleiben oder werfen, und das Wiederbinden des Ports nach `stop`. `SPEC-059` sagt für die ersten drei „nicht festgelegt“. [ADR-0013](../../../../adr/0013-backend-grenzen.md) regelt die Grenzen nur für den Protokollkern.
