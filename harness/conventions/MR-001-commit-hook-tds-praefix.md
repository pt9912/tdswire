# MR-001 — Commit-Hook erkennt das Vertrags-Präfix TDS

- **Datum:** 2026-10-29
- **Geltungsbereich:** `.githooks/commit-msg` (der Träger der Commit-Kennungs-Prüfung); `tools/harness/commit-msg-traceability.sh` bleibt unverändert.
- **Ersetzt-Baseline-Regel:** [`grundlagen-traceability.md` §Traceability-Constraint](../../.harness/baseline/v6.18.1/regelwerk/grundlagen-traceability.md#traceability-constraint)
- **Adaption:** Der Commit-Hook lässt eine Message auch durch, wenn eine Nicht-Kommentarzeile eine Kennung `TDS-(FA|QA|RB)-NN` trägt. Die Kennungs-Menge des mitgelieferten Skripts (`ADR-`, `LH-`, `MR-`, `slice-`), die Ausnahme für `Merge `/`Revert ` und die Exit-Codes entscheidet weiter das Skript, das der Träger danach aufruft. Geprüft wird die Anwesenheit einer Kennung, nicht ihre Wahrheit — wie in der Baseline.
- **Begründung:** `MR-000` legt `TDS` als Vertrags-Präfix fest, `harness/README.md` §Traceability rules und `.d-check.yml` (`ids`) führen es, und `make doc-commits` nimmt `TDS-*` an. Das mitgelieferte Skript kennt nur das Standard-Präfix `LH`; ein Commit, der nur `TDS-FA-11` oder `TDS-RB-05` nennt, fiele am Hook, obwohl er die Zusage erfüllt. Das Skript wird bei jedem Bootstrap neu geschrieben, der Träger nicht (skip-if-present).
- **Auflösungs-Trigger:** sobald das mitgelieferte Skript das deklarierte Vertrags-Präfix selbst kennt, oder das Repo auf das Standard-Präfix `LH` wechselt.
