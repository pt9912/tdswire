**Vorgang:** slice-qa-dotnet-referenztest-geruest
**Fund:** Der zweite Lauf von `make referenztest-dotnet` zeigt `CACHED` für den Testschritt (Review F5, Verifikation I-2); die 22 Läufe des Smoke-Tests in der Verifikation entstanden durch `docker run` der Test-Image, nicht durch wiederholte Gate-Läufe.
