# Ein DoD-Punkt des E2E-Gerüsts ist benannt, aber weder gemessen noch von einer Stufe getragen

**Sub-Area:** `*` (Produkt-E2E, `tools/e2e.sh`, Abdeckungs-Sicht)

Eine Messung (Laufzeit warm, kalt, Vergleich mit den Einzelläufen) oder eine Stufe für eine Zusage (Beenden und abrupter TCP-Abbruch am Prozess) steht im Slice-Plan als Ziel, wird bei der Closure aber nicht geliefert. Der Slice-Plan beschreibt einen Zuschnitt (vier Stufen, warm und kalt), der Bestand hat sich bis zur Closure verändert (sieben Stufen); die Messung des alten Zuschnitts gilt für den neuen nicht. Ohne Eintrag hängt der Punkt an keiner Datei.
