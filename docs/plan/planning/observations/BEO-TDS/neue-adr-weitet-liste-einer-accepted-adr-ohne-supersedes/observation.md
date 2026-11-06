# Eine neue ADR weitet eine Liste, die eine angenommene ADR schließt, und sagt nichts zum Verhältnis

**Sub-Area:** `*` (ADRs)

Eine ADR führt `Supersedes: keines` und nennt die ältere angenommene ADR nur im Bezug, erweitert aber eine Aussage, die diese als geschlossen führt (hier: „Gelesene Anweisungsformen bleiben die Tabelle S1, W1, W2, W3 und C“ in [ADR-0012](../../../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14, erweitert um Katalogabfragen, DDL, Massenladen, `MERGE`). Nach `Accepted` stünden zwei ADRs mit entgegengesetzter Aussage nebeneinander, und die Frage, ob das ein teilweises `Supersedes` ist, wäre nie gestellt (`AGENTS.md` §3.5). Gesucht wird beim Schreiben einer ADR: welche angenommene ADR sagt über denselben Gegenstand „bleibt“ oder „abschließend“.
