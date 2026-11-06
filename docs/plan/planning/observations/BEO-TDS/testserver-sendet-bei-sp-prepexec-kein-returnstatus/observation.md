# Der Testserver sendet bei `sp_prepexec` kein RETURNSTATUS, und mssql-jdbc übernimmt das Handle dann nicht

**Sub-Area:** `*` (Entscheidungs-Slices, Testserver)

Die Antwort auf `sp_prepexec` trägt im gebauten Stand RETURNVALUE `@handle` ohne vorangehendes RETURNSTATUS (Reihe h6 des [Messprotokolls](../../../../../messungen/handle-grenze-optionen-2026-11-06.md)); mssql-jdbc 13.4.0 benutzt das Handle dann nicht: je Ausführung ein `sp_prepexec`, nie ein `sp_execute`, nie ein `sp_unprepare` (h2, h3). Mit RETURNSTATUS vor dem RETURNVALUE (Eingriff im Durchlauf) sendet er `sp_execute` und gibt geschlossene Anweisungen als SQL-Batch frei (t1, t3, t5). `SPEC-030` verlangt die Tokens für diese Formen schon heute; der gebaute Stand weicht von der Zusage ab (nicht in einem Test belegt).
