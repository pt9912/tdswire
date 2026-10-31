# Eine Aussage über das Verhalten über eine Schichtgrenze hinweg ruht auf dem Code, nicht auf einem Test

**Sub-Area:** `*` (Dispatch, Framing, Tests, Verifikation)

Eine Aussage gilt für den Zusammenbau zweier Schichten (hier: eine mehrpaketige RPC-Nachricht wird vor dem Dispatch zu einer Nachricht zusammengesetzt, ein PLP-Typ in der Mitte der Parameterliste lässt den Rest ungelesen, ein Folge-RPC wird beantwortet). Die Einzelschichten haben Tests, die Kombination nicht; die Aussage trägt über die Lesart des Codes, bis die Nachverifikation sie als Lücke benennt.
