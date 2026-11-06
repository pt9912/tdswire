# Die Prüfsummen neuer Gradle-Abhängigkeiten stammen vom ersten Abruf, eine Signaturprüfung gibt es nicht

**Sub-Area:** `*` (Build und Gates, Lieferkette)

`gradle/verification-metadata.xml` trägt `verify-signatures` auf `false`; `make deps-lock` schreibt Lock und Prüfsummen einer neuen Abhängigkeit beim ersten Abruf, und dieser Abruf vertraut dem Repository. Für die .NET-Pakete führt das Repo dieselbe Lücke als Carveout ([CO-002](../../../../carveouts/done/CO-002-nuget-signaturpruefung-fehlt.md)); für die Gradle-Abhängigkeiten (heute Kotlin, Coroutines, mit dem Simulations-Backend Jackson und snakeyaml) steht sie nur in [ADR-0012](../../../../adr/0012-simulations-backend-beschreibung-und-formen.md) als Satz und in keinem Träger.
