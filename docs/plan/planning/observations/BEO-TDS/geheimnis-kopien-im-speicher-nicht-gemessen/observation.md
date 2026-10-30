# Kopien des Passworts im Speicher sind nicht gemessen

**Sub-Area:** `*` (Anmeldepfad, Geheimnisschutz)

Das Überschreiben des Passwortfelds schützt nur dieses Feld. Welche weiteren Kopien in JVM und Adapter entstehen (das Schlüsselobjekt der JDK-Ableitung, der UTF-8-Zwischenpuffer, `PBEKeySpec`), ist aus dem Code gelesen und nicht gemessen; ein Speicherabzug liegt nicht vor.
