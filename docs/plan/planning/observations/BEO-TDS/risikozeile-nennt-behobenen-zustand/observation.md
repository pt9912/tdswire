# Eine Risikozeile in einem Nachbar-Slice beschreibt einen Zustand, den eine Reparatur außerhalb des Slice behoben hat

**Sub-Area:** `*` (Planung, Entscheidungs-Slices)

Ein Slice in `open/` führt eine Risikozeile über einen Mangel eines Werkzeugs („eine Reparatur ist außerhalb dieses Slice beauftragt“). Die Reparatur landet auf `main`, die Zeile bleibt stehen und beschreibt dann einen Zustand, den es nicht mehr gibt; sie fällt erst der nächsten Verifikation auf. Die Zeile soll die Folgepflicht nennen, nicht den Zustand des Werkzeugs.
