using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Integration;

/// <summary>
/// Massenladen des Referenzclients (`SqlBulkCopy`, Microsoft.Data.SqlClient 7.1.1) gegen den Serverprozess mit
/// Simulations-Backend nach SPEC-036 (Form B) und SPEC-040 (Dockerfile-Stage `integrationsserver`,
/// `make simulations-integration`, Stufe 3 von `make e2e`). Das Ziel steht in TDSWIRE_ZIEL (host:port); die Tabelle
/// `bulk_ziel` steht in `testserver/resources/integration/sim.yaml` und füllt der Bestand des Testservers (Id BIGINT
/// nicht nullbar als Schlüssel, Text NVARCHAR(10), Zahl INT, Preis DECIMAL(10,2)). Belegt ist das Verhalten des
/// Referenzclients gegen den Server dieses Repos in TDS 7.4 ohne Verschlüsselung und ohne Pool, nicht gegen einen
/// echten SQL Server (dort: Messreihe M-8 bis M-12, `make vergleichslauf-d`) und nicht TDS 8.0.
/// </summary>
[Trait("Kategorie", "Integration")]
public class SqlBulkCopyTests
{
    private readonly ITestOutputHelper _ausgabe;

    public SqlBulkCopyTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static SqlConnection Verbinden()
    {
        var ziel = Environment.GetEnvironmentVariable("TDSWIRE_ZIEL")
            ?? throw new InvalidOperationException("TDSWIRE_ZIEL fehlt (host:port)");
        var trenner = ziel.LastIndexOf(':');
        var zeichenfolge = $"Server=tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]};User ID=alice;Password=secret;"
            + "Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=15;Command Timeout=120";
        var verbindung = new SqlConnection(zeichenfolge);
        verbindung.Open();
        return verbindung;
    }

    private static DataTable Zeilen(params (long Id, string? Text, int? Zahl, decimal? Preis)[] werte)
    {
        var tabelle = new DataTable();
        tabelle.Columns.Add("Id", typeof(long));
        tabelle.Columns.Add("Text", typeof(string));
        tabelle.Columns.Add("Zahl", typeof(int));
        tabelle.Columns.Add("Preis", typeof(decimal));
        foreach (var (id, text, zahl, preis) in werte)
        {
            tabelle.Rows.Add(id, (object?)text ?? DBNull.Value, (object?)zahl ?? DBNull.Value, (object?)preis ?? DBNull.Value);
        }

        return tabelle;
    }

    private static SqlBulkCopy Kopierer(SqlConnection verbindung)
    {
        var kopierer = new SqlBulkCopy(verbindung) { DestinationTableName = "bulk_ziel" };
        foreach (var spalte in new[] { "Id", "Text", "Zahl", "Preis" })
        {
            kopierer.ColumnMappings.Add(spalte, spalte);
        }

        return kopierer;
    }

    private static List<(long Id, string? Text, int? Zahl, decimal? Preis)> Lesen(SqlConnection verbindung, params long[] ids)
    {
        var gelesen = new List<(long, string?, int?, decimal?)>();
        using var befehl = new SqlCommand(
            $"SELECT Id, Text, Zahl, Preis FROM bulk_ziel WHERE Id IN ({string.Join(", ", ids)})", verbindung);
        using var leser = befehl.ExecuteReader();
        while (leser.Read())
        {
            gelesen.Add((
                leser.GetInt64(0),
                leser.IsDBNull(1) ? null : leser.GetString(1),
                leser.IsDBNull(2) ? null : leser.GetInt32(2),
                leser.IsDBNull(3) ? null : leser.GetDecimal(3)));
        }

        return gelesen.OrderBy(zeile => zeile.Item1).ToList();
    }

    [Fact]
    public void Massenladen_mit_SqlBulkCopy_laedt_die_Zeilen_in_den_Bestand_und_zaehlt_sie()
    {
        using var verbindung = Verbinden();
        using var kopierer = Kopierer(verbindung);

        kopierer.WriteToServer(Zeilen(
            (1, "Äpfel", 10, 12.34m),
            (2, null, null, null),
            (3, "", -7, 0.00m)));

        var gelesen = Lesen(verbindung, 1, 2, 3);
        _ausgabe.WriteLine($"bulk_ziel: {gelesen.Count} Zeilen gelesen");
        Assert.Equal(
            new List<(long, string?, int?, decimal?)>
            {
                (1, "Äpfel", 10, 12.34m),
                (2, null, null, null),
                (3, "", -7, 0.00m),
            },
            gelesen);
    }

    [Fact]
    public void Mehrere_Nachrichten_je_Batch_laden_alle_Zeilen()
    {
        using var verbindung = Verbinden();
        using var kopierer = Kopierer(verbindung);
        kopierer.BatchSize = 2;
        long kopiert = -1;
        kopierer.SqlRowsCopied += (_, e) => kopiert = e.RowsCopied;
        kopierer.NotifyAfter = 1;

        kopierer.WriteToServer(Zeilen(
            (10, "a", 1, 1.00m), (11, "b", 2, 2.00m), (12, "c", 3, 3.00m), (13, "d", 4, 4.00m), (14, "e", 5, 5.00m)));

        Assert.Equal(5, kopiert);
        Assert.Equal(new long[] { 10, 11, 12, 13, 14 }, Lesen(verbindung, 10, 11, 12, 13, 14).Select(zeile => zeile.Id).ToArray());
    }

    [Fact]
    public void Schluesselverstoss_endet_mit_2627_ohne_Zeile_der_Nachricht_und_die_Verbindung_bleibt_nutzbar()
    {
        using var verbindung = Verbinden();
        using (var erste = Kopierer(verbindung))
        {
            erste.WriteToServer(Zeilen((20, "vorhanden", 1, 1.00m)));
        }

        using var zweite = Kopierer(verbindung);
        var ausnahme = Assert.Throws<SqlException>(() => zweite.WriteToServer(Zeilen((21, "neu", 2, 2.00m), (20, "doppelt", 3, 3.00m))));

        _ausgabe.WriteLine($"Schluesselverstoss: Number {ausnahme.Number}, Class {ausnahme.Class}, State {ausnahme.State}");
        Assert.Equal(2627, ausnahme.Number);
        Assert.Equal(System.Data.ConnectionState.Open, verbindung.State);
        Assert.Equal(new long[] { 20 }, Lesen(verbindung, 20, 21).Select(zeile => zeile.Id).ToArray());
    }
}
