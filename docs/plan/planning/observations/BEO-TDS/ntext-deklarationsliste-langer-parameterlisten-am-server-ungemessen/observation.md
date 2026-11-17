# Der Referenzclient sendet bei sehr vielen Parametern die Deklarationsliste als `ntext`; ob der Server dieser Bibliothek sie annimmt, ist nicht gemessen

**Sub-Area:** `*` (RPC-Parser, `SPEC-003`, `SPEC-030`)

Der Referenzclient (`Microsoft.Data.SqlClient` 7.1.1) sendet bei 1360 und 1400 Parametern je Aufruf den Text von `sp_executesql` und die Deklarationsliste als `ntext` (Messprotokoll W-22, Ablage `rpc-batch-parameter-4086` und `-4206`); die Schwelle, ab der er es tut, ist nicht gemessen. `SPEC-003` setzt `text`, `ntext` und `image` auf `TDS_UNSUPPORTED_TYPE`. Eine Nachricht dieser Form an den Server dieser Bibliothek ist weder gesendet noch beantwortet worden; die Frage „Code angleichen (`ntext` lesen) oder Zielbild anpassen“ steht beim Nutzer (Closure-Notiz von `slice-mehrfach-rpc-messluecken`, Frage 3).
