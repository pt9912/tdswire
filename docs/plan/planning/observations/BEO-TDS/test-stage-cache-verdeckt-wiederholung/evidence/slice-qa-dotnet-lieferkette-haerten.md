**Vorgang:** [slice-qa-dotnet-lieferkette-haerten](../../../../done/slice-qa-dotnet-lieferkette-haerten.md)
**Fund:** [Verifikation](../../../../../../reviews/2026-11-06-ec67bb0-verifikation-slice-qa-dotnet-lieferkette-haerten.md) V-5 und der eigene Lauf: `make referenztest-dotnet` war Exit 0 aus dem Cache, die Signaturprüfung des Restore lief nicht; belegt hat sie erst `docker build --no-cache-filter restore --target test referenztest-dotnet`. `make clean-build` deckt den .NET-Restore nicht ab.
**Stand:** weiteres Auftreten, diesmal für den Restore statt für den Test-Schritt.
