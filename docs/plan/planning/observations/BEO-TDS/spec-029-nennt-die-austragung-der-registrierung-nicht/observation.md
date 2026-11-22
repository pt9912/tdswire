# SPEC-029 nennt die Austragung der Registrierung des Backends nicht, ihre Tests tragen das Tag trotzdem

**Sub-Area:** `*` (Spezifikation, Simulations-Backend)

[SPEC-029](../../../../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) behandelt das Signalisieren des Cancel an die Backend-Operation; die Austragung der Ausführung aus der Registrierung des Simulations-Backends nach Ende, Fehler, Cancel und `close()` steht dort nicht ausdrücklich. Die sieben Testfälle von `SimulationBackendRegistrationTest` tragen `@spec SPEC-029` und zählen in der Abdeckungsmatrix (53 auf 60); das Tag ist die Zuordnung durch den Slice, keine Aussage der Spezifikation. Offen ist, ob die Austragung eine Zusage der Spezifikation ist (dann benennt `SPEC-029` oder `SPEC-035` sie) oder eine Eigenschaft der Umsetzung (dann trägt der Test ein anderes oder kein Tag). Eine Änderung der Spezifikation ist Entscheidung des Nutzers.
