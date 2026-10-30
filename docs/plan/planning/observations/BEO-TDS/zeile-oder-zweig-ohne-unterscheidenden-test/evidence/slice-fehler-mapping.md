**Vorgang:** slice-fehler-mapping
**Fund:** `protocol` und `failure` mit `streamSynchronous = false` waren ungetestet; die Mutation „Parameter nur in `protocol` ignorieren“ hätte keinen Test gefärbt (Review F-6, nicht gefahren, aus dem Test gelesen). Behoben mit einem Test je Einstieg (Commit `3a15f14`, Befund laut Verifikation behoben); die Mutation selbst ist nicht nachgefahren.
