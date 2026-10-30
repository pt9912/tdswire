# Ein Ausgang ist nur über einen Einhängepunkt im Produktivcode testbar

**Sub-Area:** `*` (Tests, Produktivcode)

Ein Ausgang einer Klasse (hier: die Senke lehnt ein Token ab) ist nur über ein Token erreichbar, das der Erzeuger im Betrieb nie bildet. Der Test kommt nur durch einen Konstruktorparameter heran, der im Produktivcode steht, vom Betrieb nie belegt wird und öffentlich sichtbar ist (hier `RequestLoop.sinkAround`, Voreinstellung die Identität). Die Naht beweist die Reaktion der Klasse auf den Ausgang, nicht, dass ein echtes Ereignis ihn auslöst; ein Satz im KDoc, der das Gegenteil behauptet, trägt keinen Beleg. Offen ist, ob eine Regel „Naht nur `internal`, mit Festlegung im KDoc“ daraus wird.
