using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Gegenmessungen an der Referenzinstanz (`make vergleichslauf-d LAUF=messung-2022|messung-2025`, ADR-0008): was der Client
/// (Microsoft.Data.SqlClient 7.1.1) von einem echten SQL Server sieht, wo das Simulations-Backend bisher auf Setzungen oder
/// auf Messungen mit erfundenen Antworten steht. Jede Zeile `messung: ...` im Bericht ist ein Messwert; die Tests urteilen
/// nicht über Erwartungen, sie bestehen, wenn die Messung lief. Gegenstand: Form der Antworten auf Katalogabfragen, Bulk-Kopie
/// von Ganzzahlen und ganzzahligen Dezimalzahlen, Rückgabestatus und Handle von `sp_prepexec`, `sp_unprepare` mit gültigem und
/// unbekanntem Handle. Nicht belegt: die Abfragen von d-migrate (sie stehen nur im Bytecode seines Treibers, nicht im Repo; hier
/// laufen Standardabfragen auf `sys.*` und `INFORMATION_SCHEMA`), der Treiber von d-migrate (JDBC) und jede Aussage über Token
/// und Statusbits auf dem Draht; das Senden von ATTENTION im Leerlauf ist am Client nicht sichtbar, der Schritt
/// `cancel-im-leerlauf` des Live-Laufs zeigt nur, dass `Cancel()` ohne laufenden Befehl keine Ausnahme wirft.
/// </summary>
[Trait("Kategorie", "VergleichMessung")]
public class ReferenzMessungTests
{
    private readonly ITestOutputHelper _ausgabe;

    public ReferenzMessungTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static SqlConnection Oeffnen() =>
        ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));

    private static void Ausfuehren(SqlConnection verbindung, string text)
    {
        using var befehl = new SqlCommand(text, verbindung);
        befehl.ExecuteNonQuery();
    }

    private static string Form(SqlDataReader leser) =>
        string.Join(", ", Enumerable.Range(0, leser.FieldCount).Select(i => $"{leser.GetName(i)}:{leser.GetDataTypeName(i)}"));

    /// <summary>Gibt Spaltenform und Zeilenzahl jeder Ergebnismenge einer Abfrage zurück oder die Ausnahme.</summary>
    private static string Katalogantwort(SqlConnection verbindung, string text)
    {
        try
        {
            using var befehl = new SqlCommand(text, verbindung);
            using var leser = befehl.ExecuteReader();
            var teile = new List<string>();
            do
            {
                var zeilen = 0;
                while (leser.Read()) zeilen++;
                teile.Add($"[{Form(leser)}] Zeilen {zeilen}");
            }
            while (leser.NextResult());
            return string.Join(" || ", teile);
        }
        catch (SqlException a)
        {
            return ReferenzZiel.Beschreibung(a);
        }
    }

    [Fact]
    public void Katalogabfragen_liefern_die_Form_der_Antwort()
    {
        using var verbindung = Oeffnen();
        Ausfuehren(verbindung, "IF DB_ID('tdswire_messung') IS NOT NULL DROP DATABASE tdswire_messung");
        Ausfuehren(verbindung, "CREATE DATABASE tdswire_messung");
        Ausfuehren(verbindung, "USE tdswire_messung");
        Ausfuehren(verbindung, "CREATE TABLE kunden (Id int NOT NULL PRIMARY KEY, Name nvarchar(100) NULL, Eintritt date NULL)");
        Ausfuehren(verbindung, "CREATE TABLE bestellungen (Id int NOT NULL PRIMARY KEY, KundenId int NOT NULL REFERENCES kunden (Id), Betrag decimal(10,2) NULL)");

        var abfragen = new[]
        {
            "SELECT SCHEMA_NAME()",
            "SELECT DB_NAME()",
            "SELECT name, object_id, schema_id, type, create_date FROM sys.tables",
            "SELECT c.name, c.column_id, c.system_type_id, c.max_length, c.precision, c.scale, c.is_nullable, c.is_identity FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id",
            "SELECT i.name, i.index_id, i.type, i.is_primary_key, i.is_unique FROM sys.indexes i JOIN sys.tables t ON t.object_id = i.object_id",
            "SELECT name, parent_object_id, type FROM sys.key_constraints",
            "SELECT name, parent_object_id, referenced_object_id FROM sys.foreign_keys",
            "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES",
            "SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, IS_NULLABLE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS",
            "SELECT name FROM sys.schemas",
        };
        var anzahl = 0;
        foreach (var abfrage in abfragen)
        {
            var antwort = Katalogantwort(verbindung, abfrage);
            ReferenzZiel.Messung(_ausgabe, $"Katalog '{abfrage}': {antwort}");
            anzahl++;
        }

        Ausfuehren(verbindung, "USE master");
        Ausfuehren(verbindung, "DROP DATABASE tdswire_messung");
        Assert.Equal(abfragen.Length, anzahl);
    }

    [Fact]
    public void Bulk_Kopie_von_Ganzzahlen_und_ganzzahligen_Dezimalzahlen_in_INT_und_DECIMAL()
    {
        using var verbindung = Oeffnen();
        Ausfuehren(verbindung, "IF OBJECT_ID('tempdb..#bulk') IS NOT NULL DROP TABLE #bulk");
        Ausfuehren(verbindung, "CREATE TABLE #bulk (Zahl int NOT NULL, Gross bigint NULL, Preis decimal(10,2) NULL)");

        var faelle = new (string Name, object Zahl, object Gross, object Preis)[]
        {
            ("int, long, decimal mit Bruchteil", 1, 2L, 3.50m),
            ("long im Bereich von int, int fuer bigint, ganzzahliges decimal", 4L, 5, 6m),
            ("long ausserhalb von int", 3_000_000_000L, 7L, 8.25m),
        };
        foreach (var fall in faelle)
        {
            var tabelle = new DataTable();
            tabelle.Columns.Add("Zahl", fall.Zahl.GetType());
            tabelle.Columns.Add("Gross", fall.Gross.GetType());
            tabelle.Columns.Add("Preis", fall.Preis.GetType());
            tabelle.Rows.Add(fall.Zahl, fall.Gross, fall.Preis);
            string ergebnis;
            try
            {
                using var kopie = new SqlBulkCopy(verbindung) { DestinationTableName = "#bulk" };
                kopie.ColumnMappings.Add("Zahl", "Zahl");
                kopie.ColumnMappings.Add("Gross", "Gross");
                kopie.ColumnMappings.Add("Preis", "Preis");
                kopie.WriteToServer(tabelle);
                ergebnis = "gelungen";
            }
            catch (Exception a) when (a is SqlException or InvalidOperationException or OverflowException)
            {
                ergebnis = a is SqlException s ? ReferenzZiel.Beschreibung(s) : $"{a.GetType().Name} '{a.Message}'";
            }

            ReferenzZiel.Messung(_ausgabe,
                $"Bulk-Kopie ({fall.Name}; Quelltypen {fall.Zahl.GetType().Name}/{fall.Gross.GetType().Name}/{fall.Preis.GetType().Name}): {ergebnis}");
        }

        using var lesen = new SqlCommand("SELECT Zahl, Gross, Preis FROM #bulk ORDER BY Zahl", verbindung);
        using var leser = lesen.ExecuteReader();
        var zeilen = new List<string>();
        while (leser.Read()) zeilen.Add($"({leser.GetInt32(0)},{leser.GetInt64(1)},{leser.GetDecimal(2).ToString(System.Globalization.CultureInfo.InvariantCulture)})");
        ReferenzZiel.Messung(_ausgabe, $"Bulk-Kopie, gelesene Zeilen danach: {string.Join(";", zeilen)}");
        Assert.NotEmpty(zeilen);
    }

    [Fact]
    public void Prepexec_und_Unprepare_zeigen_Rueckgabestatus_Handle_und_Antwort_auf_unbekanntes_Handle()
    {
        using var verbindung = Oeffnen();

        // sp_prepexec als gespeicherte Prozedur: Rueckgabestatus ueber einen Parameter ReturnValue, Handle ueber OUTPUT.
        int? handle = null;
        using (var prepexec = new SqlCommand("sp_prepexec", verbindung) { CommandType = CommandType.StoredProcedure })
        {
            var status = prepexec.Parameters.Add("@rueckgabe", SqlDbType.Int);
            status.Direction = ParameterDirection.ReturnValue;
            var handleParameter = prepexec.Parameters.Add("@handle", SqlDbType.Int);
            handleParameter.Direction = ParameterDirection.InputOutput;
            handleParameter.Value = DBNull.Value;
            prepexec.Parameters.Add("@params", SqlDbType.NVarChar, -1).Value = "@x int";
            prepexec.Parameters.Add("@stmt", SqlDbType.NVarChar, -1).Value = "SELECT @x + 1";
            prepexec.Parameters.Add("@x", SqlDbType.Int).Value = 41;
            using var leser = prepexec.ExecuteReader();
            var wert = leser.Read() ? leser.GetInt32(0) : -1;
            leser.Close();
            handle = handleParameter.Value is int h ? h : null;
            ReferenzZiel.Messung(_ausgabe,
                $"sp_prepexec (StoredProcedure, SELECT @x + 1 mit @x = 41): Ergebnis {wert}, Rueckgabestatus {status.Value ?? "kein Wert"}, @handle {(handle.HasValue ? "vergeben" : "nicht vergeben")}");
        }

        Assert.True(handle.HasValue, "sp_prepexec hat kein Handle vergeben");

        string Unprepare(int wert)
        {
            using var befehl = new SqlCommand("sp_unprepare", verbindung) { CommandType = CommandType.StoredProcedure };
            var status = befehl.Parameters.Add("@rueckgabe", SqlDbType.Int);
            status.Direction = ParameterDirection.ReturnValue;
            befehl.Parameters.Add("@handle", SqlDbType.Int).Value = wert;
            try
            {
                befehl.ExecuteNonQuery();
                return $"gelungen, Rueckgabestatus {status.Value ?? "kein Wert"}";
            }
            catch (SqlException a)
            {
                return ReferenzZiel.Beschreibung(a);
            }
        }

        ReferenzZiel.Messung(_ausgabe, "sp_unprepare mit dem vergebenen Handle: " + Unprepare(handle!.Value));
        ReferenzZiel.Messung(_ausgabe, "sp_unprepare mit demselben Handle ein zweites Mal: " + Unprepare(handle.Value));
        ReferenzZiel.Messung(_ausgabe, "sp_unprepare mit dem unbekannten Handle 999999: " + Unprepare(999999));

        string Batch(string text)
        {
            try
            {
                using var befehl = new SqlCommand(text, verbindung);
                befehl.ExecuteNonQuery();
                return "gelungen";
            }
            catch (SqlException a)
            {
                return ReferenzZiel.Beschreibung(a);
            }
        }

        ReferenzZiel.Messung(_ausgabe, "SQL-Batch 'EXEC sp_unprepare 999999' (Form, die der Treiber von d-migrate laut Messung t5 sendet): " + Batch("EXEC sp_unprepare 999999"));
        ReferenzZiel.Messung(_ausgabe, "Verbindung danach: " + verbindung.State);
    }
    /// <summary>
    /// Messaufträge zu ADR-0037 (Offen 1 und 2): `sp_execute` mit unbekanntem Handle (als Prozedur und als SQL-Batch) und der
    /// gemischte Batch gehalten/unbekannt/gehalten. Gemessen ist am Client, ob der Batch bei 8179 abbricht: beide gehaltenen
    /// Handles werden danach mit `EXEC sp_execute` sondiert (gelingt = noch gehalten, 8179 = freigegeben). Die Zahl der Fehler
    /// des Batches steht in `Errors.Count` der Ausnahme; die Tokenfolge auf dem Draht ist am Client nicht sichtbar.
    /// </summary>
    [Fact]
    public void Execute_mit_unbekanntem_Handle_und_gemischter_Unprepare_Batch()
    {
        using var verbindung = Oeffnen();

        int Prepexec()
        {
            using var befehl = new SqlCommand("sp_prepexec", verbindung) { CommandType = CommandType.StoredProcedure };
            var handle = befehl.Parameters.Add("@handle", SqlDbType.Int);
            handle.Direction = ParameterDirection.InputOutput;
            handle.Value = DBNull.Value;
            befehl.Parameters.Add("@params", SqlDbType.NVarChar, -1).Value = "";
            befehl.Parameters.Add("@stmt", SqlDbType.NVarChar, -1).Value = "SELECT 1";
            using var leser = befehl.ExecuteReader();
            while (leser.Read()) { }
            leser.Close();
            return (int)handle.Value;
        }

        string Batch(string text)
        {
            try
            {
                using var befehl = new SqlCommand(text, verbindung);
                befehl.ExecuteNonQuery();
                return "gelungen";
            }
            catch (SqlException a)
            {
                var alle = string.Join(" | ", a.Errors.Cast<SqlError>().Select(f => $"Number {f.Number}, Class {f.Class}, State {f.State}, '{f.Message}'"));
                return $"{a.Errors.Count} Fehler: {alle}";
            }
        }

        string ExecuteProzedur(int wert)
        {
            using var befehl = new SqlCommand("sp_execute", verbindung) { CommandType = CommandType.StoredProcedure };
            befehl.Parameters.Add("@handle", SqlDbType.Int).Value = wert;
            try
            {
                befehl.ExecuteNonQuery();
                return "gelungen";
            }
            catch (SqlException a)
            {
                return ReferenzZiel.Beschreibung(a);
            }
        }

        ReferenzZiel.Messung(_ausgabe, "sp_execute (StoredProcedure) mit unbekanntem Handle 999999: " + ExecuteProzedur(999999));
        ReferenzZiel.Messung(_ausgabe, "SQL-Batch 'EXEC sp_execute 999999': " + Batch("EXEC sp_execute 999999"));

        var h1 = Prepexec();
        var h2 = Prepexec();
        ReferenzZiel.Messung(_ausgabe, $"zwei Handles vergeben ({(h1 != h2 ? "verschieden" : "gleich")}), sp_execute auf beide: {ExecuteProzedur(h1)} / {ExecuteProzedur(h2)}");
        ReferenzZiel.Messung(_ausgabe, $"gemischter Batch 'EXEC sp_unprepare h1;EXEC sp_unprepare 999999;EXEC sp_unprepare h2': {Batch($"EXEC sp_unprepare {h1};EXEC sp_unprepare 999999;EXEC sp_unprepare {h2}")}");
        ReferenzZiel.Messung(_ausgabe, $"danach sp_execute auf h1: {ExecuteProzedur(h1)}; auf h2: {ExecuteProzedur(h2)}");
        ReferenzZiel.Messung(_ausgabe, "Verbindung danach: " + verbindung.State);

        var a1 = Prepexec();
        var a2 = Prepexec();
        ReferenzZiel.Messung(_ausgabe, $"Batch mit zwei Unbekannten und einem Gehaltenen 'EXEC sp_unprepare 999998;EXEC sp_unprepare a1;EXEC sp_unprepare 999999': {Batch($"EXEC sp_unprepare 999998;EXEC sp_unprepare {a1};EXEC sp_unprepare 999999")}");
        ReferenzZiel.Messung(_ausgabe, $"danach sp_execute auf a1: {ExecuteProzedur(a1)}; auf a2 (nicht genannt): {ExecuteProzedur(a2)}");
        Assert.Equal(ConnectionState.Open, verbindung.State);
    }
}
