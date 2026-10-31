# Eine Messgröße steht an mehreren Orten, und kein Sensor hält die Orte gegeneinander

**Sub-Area:** `*` (Tests, Spezifikation, Build)

Eine Zahl, die ein Lauf voraussetzt (hier Heap und Textgröße des Integrationslaufs), steht in der Spezifikation, in `harness/README.md`, in `build.gradle.kts` und im Quelltext von Server und Client. Ändert jemand einen Ort, bleiben die anderen stehen und das Gate grün. Der Sensor ist ein Test, der die Orte liest und vergleicht (`IntegrationZahlenTest`); er hält die Instanz, nicht die Klasse.
