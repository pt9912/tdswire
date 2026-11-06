# Der konstante Passwortvergleich ist bei gemischten Listen zurückgenommen und hat keinen Träger

**Sub-Area:** `*` (Authentifizierungsadapter, Spezifikation)

Die Spezifikation führt den Passwortvergleich in konstanter Zeit als Anforderung an den Produktivcode (`TDS-QA-03`) und nimmt sie für eine gemischte Benutzerliste (niedrigere Iterationszahl oder `plain` neben `pbkdf2-sha256`) im selben Absatz zurück: „das ist eine Grenze, keine Zusage“. Dass dort die Zeit bekannte von unbekannten Benutzernamen trennt, ist gemessen; wer es schließt oder die Zurücknahme bestätigt, steht nirgends. Grundsatz des Nutzers vom 2026-11-06: die Spezifikation ist das Zielbild, der Code folgt ihr.
