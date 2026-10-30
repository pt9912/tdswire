# Der Build-Cache verdeckt die Wiederholung grüner Testläufe

**Sub-Area:** `*` (Test-Stage in Docker, `make gates`)

Ein Test, der in einer Docker-Stage läuft, wird nach einem grünen Lauf gecacht; ein weiterer `make gates` zeigt `CACHED` und führt ihn nur aus, wenn sich Eingänge der Stage ändern. Wiederholte grüne Gate-Läufe belegen die Stabilität eines zeit- oder lastabhängigen Tests deshalb nicht erneut; dafür gilt `--no-cache-filter test` oder ein `docker run` des Test-Image.
