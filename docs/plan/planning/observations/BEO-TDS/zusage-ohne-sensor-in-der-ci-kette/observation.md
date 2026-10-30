# Eine Zusage der CI-Kette hat keinen Sensor, der ihren Bruch meldet

**Sub-Area:** `*` (CI-Workflow, Docker-Stages, Build-Task)

Eine Zusage der CI- und Build-Kette steht in Kommentar, README-Zeile oder Plan, aber kein Wächter färbt `make gates` oder `make workflow-lint` rot, wenn sie bricht: die SHA-Pins der Actions (actionlint prüft sie nicht), der Aufruf `make gates` im Workflow (ein anderes `run:` bleibt grün), und die Aussage, eine Quelltextänderung löse keinen Download aus (`abhaengigkeitenLaden` überspringt unauflösbare oder unbekannte Konfigurationen und läuft auch mit null geladenen grün). Der Bruch bliebe unbemerkt, bis ein Mensch das Ergebnis liest.
