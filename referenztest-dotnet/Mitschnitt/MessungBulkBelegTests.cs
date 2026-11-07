using System.Text;
using Xunit;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Belege der Messreihe zu Massenladen, MERGE, Katalog und Strukturänderung sowie der Zusatzmessungen
/// (slice-qa-vergleichslauf-d-messungen-bulk-merge, SPEC-045): die abgelegten Messungen unter
/// <c>Mitschnitt/messung/bulk-merge-katalog/&lt;2022|2025&gt;</c> (aufgenommen mit <c>make vergleichslauf-d LAUF=bulk</c>, Einzelmessung je
/// Szenario und Instanz, Erzeuger: <c>Vergleich/MessungBulkTests.cs</c>) gehen durch dieselbe Auswertung wie die Aufnahme
/// (<see cref="MessAuswertung"/>); die Aussagen des Messprotokolls (<c>docs/messungen/referenzabgleich.md</c>, Abschnitt Messreihe zu
/// Massenladen, MERGE und Katalog), die in der Ablage stehen, haben hier eine Zeile. Die Tests laufen ohne Referenzinstanz in der
/// Dockerfile-Stage `test`: sie belegen, dass die abgelegte Messung die Aussage trägt, nicht dass der Server sie in einem anderen Lauf
/// wiederholt. Grenze: was der Client sah und nicht auf den Draht kam, steht nur in der Ausgabe des Laufs (<c>messung: client …</c>,
/// <c>unprepare-direkt</c>) und hat hier keine Zeile: die Meldungen des Clients bei Typ-, Längen- und NULL-Verstoß (M-11) und die
/// Zählung der Fehler am Client für 1 bis 2000 Nummern (M-22). Ebenso nicht fixiert: die Maße der Typen außerhalb der Tabelle `katalog_typen`
/// (M-17, Wiederholung an `produkte`).
/// </summary>
[Trait("Kategorie", "MitschnittEinheit")]
public class MessungBulkBelegTests
{
    private static string Verzeichnis(string stufe) => Path.Combine(AppContext.BaseDirectory, "Mitschnitt", "messung", "bulk-merge-katalog", stufe);

    private static readonly Dictionary<string, Dictionary<string, string>> Auswertungen = new();

    private static Dictionary<string, string> Z(string stufe)
    {
        lock (Auswertungen)
        {
            if (Auswertungen.TryGetValue(stufe, out var vorhanden)) return vorhanden;
            var zeilen = new Dictionary<string, string>();
            foreach (var pfad in Directory.GetFiles(Verzeichnis(stufe), "*.messung").Order(StringComparer.Ordinal))
            {
                var datei = MessDatei.Lesen(File.ReadAllText(pfad, Encoding.UTF8));
                foreach (var (kennung, text) in MessAuswertung.Zeilen(datei)) zeilen.Add(kennung, text);
            }

            Auswertungen[stufe] = zeilen;
            return zeilen;
        }
    }

    private static string F(string stufe, string kennung) =>
        Z(stufe).TryGetValue(kennung, out var wert) ? wert : throw new KeyNotFoundException($"{stufe}: {kennung}");

    private static int Zaehle(string stufe, string kennung, string token) => F(stufe, kennung).Split(' ').Count(t => t == token);

    public static IEnumerable<object[]> Stufen => new[] { new object[] { "2022" }, new object[] { "2025" } };

    // ---- Ablage -----------------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Ablage_hat_16_Messungen_mit_Anmeldung_und_alle_Auswertungen_ohne_Zerlegungsproblem(string stufe)
    {
        var dateien = Directory.GetFiles(Verzeichnis(stufe), "*.messung");
        Assert.Equal(16, dateien.Length);
        Assert.DoesNotContain(Z(stufe), z => z.Value.Contains("PROBLEM", StringComparison.Ordinal) || z.Value.Contains("ZERLEGUNG-ABGEBROCHEN", StringComparison.Ordinal));
        foreach (var pfad in dateien)
        {
            var datei = MessDatei.Lesen(File.ReadAllText(pfad, Encoding.UTF8));
            Assert.Equal(TdsPaket.Prelogin, datei.Nachrichten[0].Typ);
            Assert.Equal(TdsPaket.Login7, datei.Nachrichten[2].Typ);
            Assert.Contains(datei.Herkunft, h => h.Key == "image" && h.Value.Contains("@sha256:", StringComparison.Ordinal));
            Assert.Contains(datei.Herkunft, h => h.Key == "schema" && h.Value.Contains("sha256:", StringComparison.Ordinal));
            Assert.Equal(Path.GetFileNameWithoutExtension(pfad), datei.Szenario);
        }
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Ablage_enthaelt_in_LOGIN7_nur_x_an_Stelle_von_Rechner_Benutzer_Kennwort_Server_und_keine_Namen_der_Umgebung(string stufe)
    {
        foreach (var pfad in Directory.GetFiles(Verzeichnis(stufe), "*.messung"))
        {
            var text = File.ReadAllText(pfad, Encoding.UTF8);
            var datei = MessDatei.Lesen(text);
            var login = datei.Nachrichten.Single(n => n.Typ == TdsPaket.Login7).Daten;
            foreach (var (angabe, verschleiert) in new[] { (36, false), (40, false), (44, true), (52, false) })
            {
                var offset = login[angabe] | (login[angabe + 1] << 8);
                var zeichen = login[angabe + 2] | (login[angabe + 3] << 8);
                Assert.True(zeichen > 0, $"{pfad}: Feld {angabe} leer");
                var feld = login[offset..(offset + (zeichen * 2))];
                if (verschleiert) feld = feld.Select(b => (byte)(((b ^ 0xA5) << 4) | ((b ^ 0xA5) >> 4))).ToArray();
                Assert.Equal(new string('x', zeichen), Encoding.Unicode.GetString(feld));
            }

            Assert.All(login[72..78], b => Assert.Equal(0, b));
            Assert.All(login[16..24], b => Assert.Equal(0, b));
            foreach (var muster in new[] { "Tdswire-Ref", "tdswire-vgl" })
            {
                Assert.False(Redaktion.Enthaelt(Encoding.UTF8.GetBytes(text), muster), pfad);
                foreach (var n in datei.Nachrichten)
                {
                    Assert.False(Redaktion.Enthaelt(n.Daten, muster), $"{pfad}: {muster}");
                    Assert.DoesNotContain(muster, Encoding.Unicode.GetString(n.Daten), StringComparison.OrdinalIgnoreCase);
                }
            }
        }
    }

    // ---- Liefer-Punkt 1a: Massenladen -----------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Massenladen_sendet_INSERT_BULK_als_Text_dann_Paket_7_und_beantwortet_es_mit_DONE_COUNT_Befehl_0xf0(string stufe)
    {
        Assert.StartsWith("Art 1 Status 0x01: insert bulk [bulk_typen] ([Id] BigInt, [Zahl] Int, [Klein] SmallInt, [Flag] Bit, [Preis] Decimal(10,2), [Text] NVarChar(50)", F(stufe, "bulk-typen-null/02/anfrage"), StringComparison.Ordinal);
        Assert.Contains("with (CHECK_CONSTRAINTS, FIRE_TRIGGERS)", F(stufe, "bulk-mehrere-nachrichten/02/anfrage"), StringComparison.Ordinal);
        Assert.Equal("DONE Status -, Befehl 0x00fd, Zeilen -", F(stufe, "bulk-typen-null/02/done"));
        Assert.Equal("Art 7 Status 0x01", F(stufe, "bulk-typen-null/03/anfrage"));
        Assert.Equal("DONE", F(stufe, "bulk-typen-null/03/tokenfolge"));
        Assert.Equal("DONE Status COUNT, Befehl 0x00f0, Zeilen 3", F(stufe, "bulk-typen-null/03/done"));
        Assert.Equal("anzahl=1 [(3)]", F(stufe, "bulk-typen-null/04/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Eine_Quelle_ueber_mehrere_Nachrichten_ergibt_je_Batch_einen_INSERT_BULK_und_eine_Nachricht_mit_eigenem_Zaehler(string stufe)
    {
        foreach (var (nr, zaehler) in new[] { (3, 1000), (5, 1000), (7, 500) })
        {
            Assert.StartsWith("Art 1 Status 0x01: insert bulk [bulk_menge]", F(stufe, $"bulk-mehrere-nachrichten/{nr - 1:D2}/anfrage"), StringComparison.Ordinal);
            Assert.Equal("Art 7 Status 0x04", F(stufe, $"bulk-mehrere-nachrichten/{nr:D2}/anfrage"));
            Assert.Equal($"DONE Status COUNT, Befehl 0x00f0, Zeilen {zaehler}", F(stufe, $"bulk-mehrere-nachrichten/{nr:D2}/done"));
        }

        Assert.Equal("anzahl=1 [(2500)]", F(stufe, "bulk-mehrere-nachrichten/08/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Eine_leere_Quelle_sendet_weder_INSERT_BULK_noch_Paket_7(string stufe)
    {
        Assert.Equal("Art 1 Status 0x01: SELECT COUNT_BIG(*) FROM bulk_leer", F(stufe, "bulk-leer/01/anfrage"));
        Assert.False(Z(stufe).ContainsKey("bulk-leer/02/anfrage"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Ein_Verstoss_gegen_Typ_Laenge_oder_NULL_endet_im_Client_nach_INSERT_BULK_mit_ATTENTION_und_ohne_Paket_7(string stufe)
    {
        foreach (var szenario in new[] { "bulk-verstoss-typ", "bulk-verstoss-laenge", "bulk-verstoss-null" })
        {
            Assert.StartsWith("Art 1 Status 0x01: insert bulk", F(stufe, szenario + "/02/anfrage"), StringComparison.Ordinal);
            Assert.Equal("DONE Status -, Befehl 0x00fd, Zeilen - ; DONE Status ERROR|ATTN, Befehl 0x00fd, Zeilen -", F(stufe, szenario + "/02/done"));
            Assert.Equal("DONE || DONE", F(stufe, szenario + "/02/tokenfolge"));
            Assert.StartsWith("Art 1 Status 0x01: SELECT COUNT_BIG(*)", F(stufe, szenario + "/03/anfrage"), StringComparison.Ordinal);
            Assert.Equal("anzahl=1 [(0)]", F(stufe, szenario + "/03/zeilen"));
        }
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Ein_Verstoss_gegen_den_Schluessel_antwortet_der_Server_auf_Paket_7_mit_ERROR_2627_und_DONE_ERROR_Befehl_0xf0(string stufe)
    {
        Assert.Equal("Art 7 Status 0x01", F(stufe, "bulk-verstoss-schluessel/03/anfrage"));
        Assert.Equal("ERROR INFO DONE || DONE", F(stufe, "bulk-verstoss-schluessel/03/tokenfolge"));
        Assert.StartsWith("Fehler Nummer 2627, Class 14, State 1, Meldung 'Violation of PRIMARY KEY constraint 'pk_bulk_schluessel'", F(stufe, "bulk-verstoss-schluessel/03/fehler"), StringComparison.Ordinal);
        Assert.Equal("DONE Status ERROR, Befehl 0x00f0, Zeilen - ; DONE Status ATTN, Befehl 0x00fd, Zeilen -", F(stufe, "bulk-verstoss-schluessel/03/done"));
        Assert.Equal("anzahl=1 [(1)]", F(stufe, "bulk-verstoss-schluessel/04/zeilen"));
    }

    // ---- Liefer-Punkt 1b: MERGE ----------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void MERGE_OUTPUT_action_antwortet_mit_einer_Spalte_action_je_betroffener_Zeile_und_DONEINPROC_Befehl_0x117(string stufe)
    {
        const string DoneEins = "DONEINPROC Status MORE|COUNT, Befehl 0x0117, Zeilen 1 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -";
        Assert.StartsWith("Art 3 Status 0x01: sp_ExecuteSql(#10)", F(stufe, "merge-skip/01/anfrage"), StringComparison.Ordinal);
        Assert.Equal("COLMETADATA ROW+ DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "merge-skip/01/tokenfolge"));
        Assert.Equal("[$action:nvarchar(laenge=20,genauigkeit=0,skala=0) nullbar=nein]", F(stufe, "merge-skip/01/colmetadata"));
        Assert.Equal("anzahl=1 [('INSERT')]", F(stufe, "merge-skip/01/zeilen"));
        Assert.Equal(DoneEins, F(stufe, "merge-skip/01/done"));
        Assert.Equal("RETURNSTATUS 0", F(stufe, "merge-skip/01/rueckgabe"));
        Assert.Equal("anzahl=1 [('UPDATE')]", F(stufe, "merge-update/02/zeilen"));
        Assert.Equal(DoneEins, F(stufe, "merge-update/02/done"));
        Assert.Equal("anzahl=1 [('INSERT')]", F(stufe, "merge-skip/03/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void MERGE_ohne_Ausgabezeile_sendet_COLMETADATA_ohne_ROW_und_DONEINPROC_mit_Zaehler_0(string stufe)
    {
        Assert.Equal("COLMETADATA DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "merge-skip/02/tokenfolge"));
        Assert.Equal("DONEINPROC Status MORE|COUNT, Befehl 0x0117, Zeilen 0 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "merge-skip/02/done"));
        Assert.Equal("anzahl=0 []", F(stufe, "merge-skip/02/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void MERGE_mit_mehreren_Zeilen_liefert_eine_Zeile_je_Quellzeile_in_der_Reihenfolge_der_Quelle_und_den_Zaehler_der_Zeilen(string stufe)
    {
        Assert.Equal("anzahl=3 [('UPDATE');('INSERT');('INSERT')]", F(stufe, "merge-mehrere-zeilen/01/zeilen"));
        Assert.Equal("DONEINPROC Status MORE|COUNT, Befehl 0x0117, Zeilen 3 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "merge-mehrere-zeilen/01/done"));
        Assert.Equal("anzahl=2 [('UPDATE');('UPDATE')]", F(stufe, "merge-mehrere-zeilen/02/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void MERGE_Fehler_enden_mit_ERROR_INFO_DONEINPROC_MORE_ERROR_und_RETURNSTATUS_der_Fehlernummer(string stufe)
    {
        Assert.Equal("COLMETADATA ERROR INFO DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "merge-fehler/01/tokenfolge"));
        Assert.StartsWith("Fehler Nummer 2628, Class 16, State 1, Meldung 'String or binary data would be truncated", F(stufe, "merge-fehler/01/fehler"), StringComparison.Ordinal);
        Assert.Equal("RETURNSTATUS 2628", F(stufe, "merge-fehler/01/rueckgabe"));
        Assert.Equal("DONEINPROC Status MORE|ERROR, Befehl 0x0117, Zeilen - ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "merge-fehler/01/done"));
        Assert.Equal("COLMETADATA ROW+ ERROR INFO DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "merge-fehler/02/tokenfolge"));
        Assert.Equal("anzahl=1 [('INSERT')]", F(stufe, "merge-fehler/02/zeilen"));
        Assert.Equal("RETURNSTATUS 2627", F(stufe, "merge-fehler/02/rueckgabe"));
        Assert.Equal("ERROR RETURNSTATUS DONEPROC", F(stufe, "merge-fehler/03/tokenfolge"));
        Assert.StartsWith("Fehler Nummer 208, Class 16, State 1", F(stufe, "merge-fehler/03/fehler"), StringComparison.Ordinal);
        Assert.Equal("DONEPROC Status ERROR, Befehl 0x00e0, Zeilen -", F(stufe, "merge-fehler/03/done"));
    }

    // ---- Liefer-Punkt 2: Katalog und Strukturänderung -------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Katalogspalten_max_length_precision_scale_sind_smallint_tinyint_tinyint_und_is_identity_ist_bit_nicht_nullbar(string stufe)
    {
        var meta = F(stufe, "katalog-abfragen/03/colmetadata");
        Assert.Contains("type_name:nvarchar(laenge=256,genauigkeit=0,skala=0) nullbar=nein", meta, StringComparison.Ordinal);
        Assert.Contains("max_length:smallint(laenge=2,genauigkeit=0,skala=0) nullbar=nein", meta, StringComparison.Ordinal);
        Assert.Contains("precision:tinyint(laenge=1,genauigkeit=0,skala=0) nullbar=nein", meta, StringComparison.Ordinal);
        Assert.Contains("scale:tinyint(laenge=1,genauigkeit=0,skala=0) nullbar=nein", meta, StringComparison.Ordinal);
        Assert.Contains("is_nullable:bitn(laenge=1,genauigkeit=0,skala=0) nullbar=ja", meta, StringComparison.Ordinal);
        Assert.Contains("is_identity:bit(laenge=1,genauigkeit=0,skala=0) nullbar=nein", meta, StringComparison.Ordinal);
        Assert.Contains("column_id:int(laenge=4,genauigkeit=0,skala=0) nullbar=nein", meta, StringComparison.Ordinal);
        Assert.Equal(meta, F(stufe, "katalog-struktur/14/colmetadata"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Typnamen_und_Masse_von_bigint_bit_time_datetime2_und_nvarchar_max_in_sys_columns(string stufe)
    {
        var zeilen = F(stufe, "katalog-abfragen/03/zeilen");
        foreach (var erwartet in new[]
        {
            "('Id','bigint',8,19,0,0,0,0,1,",
            "('Flag','bit',1,1,0,1,0,0,5,",
            "('Uhr','time',5,16,7,1,0,0,6,",
            "('Uhr3','time',4,12,3,1,0,0,7,",
            "('Zeitpunkt','datetime2',8,27,7,1,0,0,8,",
            "('Zeitpunkt3','datetime2',7,23,3,1,0,0,9,",
            "('Datum','date',3,10,0,1,0,0,10,",
            "('Preis','decimal',9,10,2,1,0,0,11,",
            "('Text','nvarchar',400,0,0,1,0,0,13,",
            "('Lang','nvarchar',-1,0,0,1,0,0,14,",
        })
            Assert.Contains(erwartet, zeilen, StringComparison.Ordinal);
        Assert.StartsWith("anzahl=18 ", zeilen, StringComparison.Ordinal);
        Assert.Contains("('Id','bigint',8,19,0,0,0,0,1,", F(stufe, "katalog-struktur/14/zeilen"), StringComparison.Ordinal);
        Assert.Contains("('Seit','datetime2',8,27,7,1,0,0,5,", F(stufe, "katalog-struktur/14/zeilen"), StringComparison.Ordinal);
        Assert.Contains("('Bezeichnung','nvarchar',400,0,0,0,0,0,2,", F(stufe, "katalog-struktur/14/zeilen"), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Katalogspalten_der_Texte_haben_die_gemessenen_Typen_Laengen_und_Nullbarkeiten(string stufe)
    {
        var meta = F(stufe, "katalog-abfragen/03/colmetadata");
        foreach (var erwartet in new[]
        {
            "column_name:nvarchar(laenge=256,genauigkeit=0,skala=0) nullbar=ja",
            "is_computed:bitn(laenge=1,genauigkeit=0,skala=0) nullbar=ja",
            "default_definition:nvarchar(laenge=65535,genauigkeit=0,skala=0) nullbar=ja",
            "seed_value:sql_variant(laenge=8009,genauigkeit=0,skala=0) nullbar=ja",
            "increment_value:sql_variant(laenge=8009,genauigkeit=0,skala=0) nullbar=ja",
            "computed_definition:nvarchar(laenge=65535,genauigkeit=0,skala=0) nullbar=ja",
            "computed_persisted:bitn(laenge=1,genauigkeit=0,skala=0) nullbar=ja",
        })
            Assert.Contains(erwartet, meta, StringComparison.Ordinal);
        Assert.Equal("COLMETADATA ORDER NBCROW+ DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "katalog-abfragen/03/tokenfolge"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Typnamen_und_Masse_von_int_smallint_tinyint_float_varchar_char_uniqueidentifier_und_varbinary_in_sys_columns(string stufe)
    {
        var zeilen = F(stufe, "katalog-abfragen/03/zeilen");
        foreach (var erwartet in new[]
        {
            "('Zahl','int',4,10,0,1,0,0,2,",
            "('Klein','smallint',2,5,0,1,0,0,3,",
            "('Winzig','tinyint',1,3,0,1,0,0,4,",
            "('Gleit','float',8,53,0,1,0,0,12,",
            "('Schmal','varchar',50,0,0,1,0,0,15,",
            "('Zeichen','char',3,0,0,1,0,0,16,",
            "('Kennung','uniqueidentifier',16,0,0,1,0,0,17,",
            "('Binaer','varbinary',16,0,0,1,0,0,18,",
        })
            Assert.Contains(erwartet, zeilen, StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Der_Eindeutigkeitsschluessel_erscheint_in_der_Indexabfrage_und_in_der_Eindeutigkeitsabfrage(string stufe)
    {
        // 18 bis 25: Tabelle katalog_kunde (20: Primärschlüssel, 22: Index, 24: Eindeutigkeit).
        Assert.Equal("anzahl=1 [('Id')]", F(stufe, "katalog-abfragen/20/zeilen"));
        Assert.Equal("anzahl=1 [('uq_katalog_kunde_email',1,0,NULL,2,'Email',1,0,0)]", F(stufe, "katalog-abfragen/22/zeilen"));
        Assert.Equal("anzahl=1 [('uq_katalog_kunde_email','Email')]", F(stufe, "katalog-abfragen/24/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Strukturabweisung_des_Servers_nennt_2714_2705_4902_und_lehnt_eine_Tabelle_ohne_Schluessel_und_mehrere_Spalten_nicht_ab(string stufe)
    {
        Assert.StartsWith("Fehler Nummer 2714, Class 16, State 6, Meldung 'There is already an object named 'produkte'", F(stufe, "katalog-struktur/08/fehler"), StringComparison.Ordinal);
        Assert.StartsWith("Fehler Nummer 2705, Class 16, State 4, Meldung 'Column names in each table must be unique", F(stufe, "katalog-struktur/09/fehler"), StringComparison.Ordinal);
        Assert.StartsWith("Fehler Nummer 4902, Class 16, State 1, Meldung 'Cannot find the object \"gibtesnicht\"", F(stufe, "katalog-struktur/11/fehler"), StringComparison.Ordinal);
        Assert.Equal("ERROR DONE", F(stufe, "katalog-struktur/08/tokenfolge"));
        Assert.Equal("DONE Status ERROR, Befehl 0x00fd, Zeilen -", F(stufe, "katalog-struktur/08/done"));
        Assert.Equal("-", F(stufe, "katalog-struktur/10/fehler"));
        Assert.Equal("DONE Status -, Befehl 0x00c6, Zeilen -", F(stufe, "katalog-struktur/10/done"));
        Assert.Equal("-", F(stufe, "katalog-struktur/12/fehler"));
        Assert.Contains("('A','int',4,10,0,1,0,0,4,", F(stufe, "katalog-struktur/16/zeilen"), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void CREATE_TABLE_und_ALTER_TABLE_ADD_antworten_mit_DONE_ohne_COUNT_und_den_Befehlen_0xc6_und_0xd8(string stufe)
    {
        Assert.StartsWith("Art 1 Status 0x01: CREATE TABLE [produkte]", F(stufe, "katalog-struktur/04/anfrage"), StringComparison.Ordinal);
        Assert.Equal("DONE Status -, Befehl 0x00c6, Zeilen -", F(stufe, "katalog-struktur/04/done"));
        Assert.Equal("ENVCHANGE DONE", F(stufe, "katalog-struktur/04/tokenfolge"));
        Assert.StartsWith("Art 1 Status 0x01: ALTER TABLE [kunden] ADD [Email]", F(stufe, "katalog-struktur/05/anfrage"), StringComparison.Ordinal);
        Assert.Equal("DONE Status -, Befehl 0x00d8, Zeilen -", F(stufe, "katalog-struktur/05/done"));
        Assert.Equal("DONE", F(stufe, "katalog-struktur/05/tokenfolge"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Sichten_fuer_Indizes_Standardwerte_Pruefbedingungen_und_Fremdschluessel_liefern_je_einen_Fall_mit_den_Spalten_der_Abfrage(string stufe)
    {
        // 26: Spalten mit Standardwert und Identität; 29: Fremdschlüssel; 30: Index mit eingeschlossener Spalte; 31: Prüfbedingung; 28: Schlüssel.
        var bestellung = F(stufe, "katalog-abfragen/26/zeilen");
        Assert.Contains("('Id','int',4,10,0,0,1,0,1,NULL,0x380001000000,0x380001000000,NULL,NULL)", bestellung, StringComparison.Ordinal);
        Assert.Contains("('Menge','int',4,10,0,0,0,0,3,'((1))',NULL,NULL,NULL,NULL)", bestellung, StringComparison.Ordinal);
        Assert.Contains("('Status','nvarchar',40,0,0,0,0,0,4,'(N'neu')',NULL,NULL,NULL,NULL)", bestellung, StringComparison.Ordinal);
        Assert.Equal("anzahl=1 [('fk_bestellung_kunde','KundeId','katalog_kunde','Id','CASCADE','NO_ACTION',1)]", F(stufe, "katalog-abfragen/29/zeilen"));
        Assert.Equal("anzahl=2 [('ix_bestellung_status',0,0,NULL,2,'Menge',0,0,1);('ix_bestellung_status',0,0,NULL,2,'Status',1,0,0)]", F(stufe, "katalog-abfragen/30/zeilen"));
        Assert.Equal("anzahl=1 [('ck_bestellung_menge','([Menge]>(0))')]", F(stufe, "katalog-abfragen/31/zeilen"));
        Assert.Equal("anzahl=0 []", F(stufe, "katalog-abfragen/32/zeilen"));
        Assert.Equal("anzahl=1 [('Id')]", F(stufe, "katalog-abfragen/28/zeilen"));
        Assert.False(Z(stufe).ContainsKey("katalog-abfragen/34/anfrage"));
    }

    // ---- Liefer-Punkt 3: Zusatzmessungen -------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Ein_Batch_mit_mehr_als_100_unbekannten_Nummern_von_sp_unprepare_meldet_jede_Nummer(string stufe)
    {
        foreach (var (nr, anzahl) in new[] { (1, 100), (2, 101), (3, 150) })
        {
            Assert.Equal(anzahl, Zaehle(stufe, $"zusatz-unprepare-viele/{nr:D2}/tokenfolge", "ERROR"));
            Assert.Equal(anzahl, Zaehle(stufe, $"zusatz-unprepare-viele/{nr:D2}/tokenfolge", "DONEPROC"));
            Assert.Equal(anzahl, F(stufe, $"zusatz-unprepare-viele/{nr:D2}/done").Split(" ; ").Count(d => d == "DONEPROC Status MORE|ERROR, Befehl 0x00e0, Zeilen -" || d == "DONEPROC Status ERROR, Befehl 0x00e0, Zeilen -"));
            Assert.Contains($"handle {900000 + anzahl - 1}.", F(stufe, $"zusatz-unprepare-viele/{nr:D2}/fehler"), StringComparison.Ordinal);
        }

        Assert.Equal("COLMETADATA ROW+ DONE", F(stufe, "zusatz-unprepare-viele/04/tokenfolge"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void EXEC_sp_unprepare_endet_mit_DONEPROC_Befehl_0xe0_als_Batch_und_als_RPC_auch_bei_unbekannter_Nummer(string stufe)
    {
        Assert.Equal("RETURNSTATUS DONEPROC", F(stufe, "zusatz-unprepare-curcmd/02/tokenfolge"));
        Assert.Equal("DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "zusatz-unprepare-curcmd/02/done"));
        Assert.Equal("DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "zusatz-unprepare-curcmd/04/done"));
        Assert.Equal("DONEPROC Status ERROR, Befehl 0x00e0, Zeilen -", F(stufe, "zusatz-unprepare-curcmd/05/done"));
        Assert.Equal("RETURNSTATUS 8179", F(stufe, "zusatz-unprepare-curcmd/05/rueckgabe"));
        Assert.Equal("DONEPROC Status MORE, Befehl 0x00e0, Zeilen - ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "zusatz-unprepare-curcmd/08/done"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Das_Flag_Feld_jeder_RETURNVALUE_eines_Ausgabewerts_ist_0x0000_auch_bei_NULL_und_ohne_Zuweisung(string stufe)
    {
        for (var nr = 1; nr <= 24; nr++)
            Assert.Equal("RETURNVALUE Ordinal 2 Flags 0x0000", F(stufe, $"zusatz-returnvalue-flag/{nr:D2}/einzelheiten"));
        Assert.Contains("Typ intn, Wert 5000000000", F(stufe, "zusatz-returnvalue-flag/05/rueckgabe"), StringComparison.Ordinal);
        Assert.Contains("Wert NULL", F(stufe, "zusatz-returnvalue-flag/08/rueckgabe"), StringComparison.Ordinal);
        Assert.Contains("Typ bitn", F(stufe, "zusatz-returnvalue-flag/08/rueckgabe"), StringComparison.Ordinal);
        Assert.Contains("Typ numericn, Wert NULL", F(stufe, "zusatz-returnvalue-flag/10/rueckgabe"), StringComparison.Ordinal);
        Assert.Contains("Typ nvarchar, Wert NULL", F(stufe, "zusatz-returnvalue-flag/14/rueckgabe"), StringComparison.Ordinal);
        Assert.Contains("Typ date, Wert NULL", F(stufe, "zusatz-returnvalue-flag/16/rueckgabe"), StringComparison.Ordinal);
        Assert.Contains("Typ datetime2, Wert NULL", F(stufe, "zusatz-returnvalue-flag/20/rueckgabe"), StringComparison.Ordinal);
    }
}
