# Die Atomizität einer Operation ist nur mit einem Stresstest belegt, der die Lücke nicht tötet

**Sub-Area:** `*` (Telemetrie, Tests)

Das Ende eines Spans ist `compareAndSet`; ein Stresstest (acht Threads, Barriere, 5000 Runden) tötet die Mutation „Setzen ohne Prüfung“, nicht die Mutation „Prüfen-dann-Setzen“ (Exit 0 unter Last 10 bis 26). Die KDoc nennt das Verfahren „Stresstest, kein Beweis“. Eine deterministische Prüfung (Hook zwischen Lesen und Setzen) ist nicht gebaut.
