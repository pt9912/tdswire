# Eine Schwesterstage teilt das Rezept einer Gate-Stage, und kein Sensor hält die Kopplung über das Geprüfte hinaus

**Sub-Area:** `*` (Docker-Stages, `referenztest-dotnet/Dockerfile`)

Die Stage `test-bericht` führt dieselben C#-Tests wie die Gate-Stage `test` aus und liefert der Abdeckungsmatrix den Bericht. Beide erben von der Basis `test-quellen` (`COPY`-Zeilen, `DOTNET_TEST_ARGS`); diese Kopplung hält, die Mutation in der Basis folgte die Matrix. Die `RUN`-Zeile von `test` steht außerhalb der Basis: ein Flag nur dort (Mutation 1 des Verifiers, `--filter`) lässt die Matrix Tests als bestanden zählen, die das Gate nicht mehr ausführt, und nichts wird rot. Die Setzung „ein Gleichlauf-Sensor ist nicht nötig“ stand ohne diesen Mutationsbeleg da.

Vorschlag (Entscheidung beim Nutzer, nichts gebaut): die `RUN`-Zeile in die Basis ziehen (`dotnet test $DOTNET_TEST_ARGS`, Logger als zusätzliche Variable in `test-bericht`) oder die Einschränkung bewusst stehen lassen.
