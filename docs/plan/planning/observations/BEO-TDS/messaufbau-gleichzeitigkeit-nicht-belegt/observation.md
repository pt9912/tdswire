# Ein Messaufbau mit vielen gleichzeitigen Sitzungen belegt die Gleichzeitigkeit nicht in jeder Zeile

**Sub-Area:** `*` (Qualität, Messungen)

Der Client eines Messlaufs startet N Sitzungen, und der Thread-Pool des Clients startete sie gestaffelt (bei 256 Sitzungen bis zu 38 s nach der ersten): die Stufe war nicht gleichzeitig, die Zahlen waren ein Artefakt des Clients. Behoben durch einen eigenen Thread je Sitzung; ob die Sitzungen gleichzeitig starteten, belegt erst die Zahl `start_max_ms` (2 bis 38 ms), und die steht nur in den Zeilen der Blockier-Zellen, nicht in denen der Leser-, Abbruch- und Import-Zellen. Das Protokoll sagt es; ein Sensor hält es nicht. Verwandt: `BEO-TDS/aussagen-ueber-messung-hinaus` (dort ist die Aussage zu breit, hier fehlt die Beleg-Zahl im Aufbau).
