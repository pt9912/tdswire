# Die Aussage „Form A überschneidet sich nicht mit Form B“ steht in Spec und KDoc, und kein Test fährt die Überschneidung

**Sub-Area:** `*` (Simulations-Backend, Erkennung der Anweisungsformen)

Eine neue Erkennung (Form F) nimmt Text an, den die Spezifikation ausschließt, weil eine ältere Form (die Probe der Zieltabelle) ihren Rest über dieselbe Erkennung rekursiv liest. Spec (`SPEC-036`) und KDoc sagen „überschneidet sich nicht“; kein Test gab der Aussage einen Text, der beide Formen treffen könnte. Der Review fand neun Überschneidungstexte (Batch und `sp_executesql`, mit und ohne schließendes `SET`, verschachtelt) per Wegwerf-Test; behoben durch eine Bedingung der Rekursion und einen Test je Text.
