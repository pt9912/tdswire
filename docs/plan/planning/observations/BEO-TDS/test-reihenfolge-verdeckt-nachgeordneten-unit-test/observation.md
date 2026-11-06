# `make test` bricht am ersten roten Task ab, und ein Unit-Test hinter einem roten Integrationstest bleibt ungelaufen

**Sub-Area:** `*` (Tests, Verifikation)

Die Stage `test` führt `integratorTest` vor `test` aus und bricht beim ersten roten Task ab. Eine Mutation, die beide Tests rot machen würde, zeigt nur den Integrationstest rot; der Unit-Test, der die Aussage allein tragen soll, kam nicht zur Ausführung. Als Beleg „der Unit-Test fängt die Mutation“ taugt nur ein Lauf, in dem ihn kein früherer Task verdeckt (eine Mutation, die der Einbettungstest nicht auslöst, oder der Unit-Test einzeln).
