using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Kompatibilitaet;

/// <summary>
/// Messungen der Lücken 3, 4, 13 und 14 der Kompatibilitätsmatrix am Referenzclient (Microsoft.Data.SqlClient 7.1.1)
/// gegen den Serverprozess (`make kompatibilitaet KATEGORIE=Luecken`, Stufe 6 von `make e2e`, ADR-0019, TDS-QA-01):
/// `xml` als RPC-Parameter, mehrere RPCs in einer Nachricht (Batch-Flag, `SqlDataAdapter` mit `UpdateBatchSize`),
/// Kennwortwechsel und FeatureExt der Anmeldung (Zugriffstoken) sowie `RESETCONNECTION` bei Pooling (vorbereitete
/// Befehle über mehrere Open-Phasen derselben physischen Verbindung). Jeder Test pinnt, was der Client sieht; die Zeilen
/// `messung: ...` im Bericht sind die Werte, die die Matrix zitiert. Der Grund der Ablehnung (Kennwortwechsel,
/// Zugriffstoken) und die Fehler von xml und Batch stehen nur im Log des Servers; ihre Prüfung liegt in
/// `tools/kompatibilitaetstest.sh`, ein `dotnet test` allein belegt sie nicht. Nicht belegt: Windows-SSPI, ein echter
/// SQL Server.
/// </summary>
[Trait("Kategorie", "Luecken")]
public class LueckenTests
{
    private readonly ITestOutputHelper _ausgabe;

    public LueckenTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    [Fact]
    public void Xml_als_RPC_Parameter_wird_abgewiesen()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id FROM person WHERE Name = @x", verbindung);
        befehl.Parameters.Add("@x", SqlDbType.Xml).Value = "<a>1</a>";

        var ausnahme = Assert.Throws<SqlException>(() => { befehl.ExecuteScalar(); });

        Ziel.Messung(_ausgabe, "xml-Parameter: " + Ziel.Beschreibung(ausnahme) + $"; Verbindung danach {verbindung.State}");
        Assert.Equal(50003, ausnahme.Number);
        Assert.Equal(20, ausnahme.Class);
        Assert.Equal(ConnectionState.Closed, verbindung.State);
    }

    [Fact]
    public void Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung()
    {
        using var verbindung = Ziel.Oeffnen();
        using var tabelle = new DataTable();
        tabelle.Columns.Add("Id", typeof(int));
        tabelle.Columns.Add("Name", typeof(string));
        tabelle.Rows.Add(21, "Eva");
        tabelle.Rows.Add(22, "Fred");
        using var einfuegen = new SqlCommand("INSERT INTO person (Id, Name) VALUES (@Id, @Name)", verbindung);
        einfuegen.Parameters.Add("@Id", SqlDbType.Int, 0, "Id");
        einfuegen.Parameters.Add("@Name", SqlDbType.NVarChar, 40, "Name");
        einfuegen.UpdatedRowSource = UpdateRowSource.None;
        using var adapter = new SqlDataAdapter { InsertCommand = einfuegen, UpdateBatchSize = 2 };

        var ausnahme = Assert.Throws<SqlException>(() => adapter.Update(tabelle));

        Ziel.Messung(_ausgabe, "UpdateBatchSize 2: " + Ziel.Beschreibung(ausnahme) + $"; Verbindung danach {verbindung.State}");
        Assert.Equal(50006, ausnahme.Number);
        Assert.Equal(20, ausnahme.Class);
        Assert.Equal(ConnectionState.Closed, verbindung.State);
    }

    [Fact]
    public void Einzelne_RPCs_desselben_Adapters_ohne_Batch_gelingen()
    {
        using var verbindung = Ziel.Oeffnen();
        using var tabelle = new DataTable();
        tabelle.Columns.Add("Id", typeof(int));
        tabelle.Columns.Add("Name", typeof(string));
        tabelle.Rows.Add(21, "Eva");
        tabelle.Rows.Add(22, "Fred");
        using var einfuegen = new SqlCommand("INSERT INTO person (Id, Name) VALUES (@Id, @Name)", verbindung);
        einfuegen.Parameters.Add("@Id", SqlDbType.Int, 0, "Id");
        einfuegen.Parameters.Add("@Name", SqlDbType.NVarChar, 40, "Name");
        einfuegen.UpdatedRowSource = UpdateRowSource.None;
        using var adapter = new SqlDataAdapter { InsertCommand = einfuegen, UpdateBatchSize = 1 };

        var zeilen = adapter.Update(tabelle);

        Ziel.Messung(_ausgabe, $"UpdateBatchSize 1: Update meldet {zeilen}; Verbindung danach {verbindung.State}");
        Assert.Equal(2, zeilen);
        Assert.Equal(ConnectionState.Open, verbindung.State);
    }

    private static SqlConnectionStringBuilder MitPool()
    {
        var zeichenfolge = Ziel.Tds74();
        zeichenfolge.Pooling = true;
        zeichenfolge.MaxPoolSize = 1;
        return zeichenfolge;
    }

    /// <summary>
    /// Bereitet in einer offenen Verbindung bis zu <paramref name="anzahl"/> neue Befehle mit dem Marker-Parameter
    /// <paramref name="marker"/> vor und führt sie je einmal aus (der Client sendet kein `sp_unprepare`). Zählt die
    /// beantworteten Befehle; die erste Ablehnung hält <paramref name="abgewiesen"/> fest.
    /// </summary>
    private static int Vorbereiten(SqlConnection verbindung, string marker, int anzahl, ref SqlException? abgewiesen)
    {
        var beantwortet = 0;
        while (abgewiesen is null && beantwortet < anzahl)
        {
            using var befehl = new SqlCommand($"SELECT Id FROM person WHERE Id = @{marker}", verbindung);
            befehl.Parameters.Add($"@{marker}", SqlDbType.Int).Value = 1;
            befehl.Prepare();
            try
            {
                Assert.Equal(1, befehl.ExecuteScalar());
                beantwortet++;
            }
            catch (SqlException ausnahme)
            {
                abgewiesen = ausnahme;
            }
        }

        return beantwortet;
    }

    [Fact]
    public void Pooling_mehr_als_1000_vorbereitete_Befehle_ueber_Open_Phasen_derselben_Verbindung_gelingen()
    {
        SqlConnection.ClearAllPools();
        var zeichenfolge = MitPool();
        SqlException? abgewiesen = null;
        var beantwortet = 0;
        for (var phase = 1; phase <= 3; phase++)
        {
            using var verbindung = Ziel.Oeffnen(zeichenfolge);
            beantwortet += Vorbereiten(verbindung, "zuruecksetzen", 400, ref abgewiesen);
        }

        Ziel.Messung(_ausgabe, $"Pooling, drei Open-Phasen zu je 400 vorbereiteten Befehlen auf einer physischen Verbindung: " +
            $"{beantwortet} beantwortet, " + (abgewiesen is null ? "keine Abweisung" : Ziel.Beschreibung(abgewiesen)));
        SqlConnection.ClearAllPools();
        Assert.Null(abgewiesen);
        Assert.Equal(1200, beantwortet);
    }

    [Fact]
    public void Pooling_innerhalb_einer_Open_Phase_endet_das_1001_Prepare_mit_50010_auch_nach_dem_Zuruecksetzen()
    {
        SqlConnection.ClearAllPools();
        var zeichenfolge = MitPool();
        SqlException? abgewiesen = null;
        int erste_phase;
        using (var erste = Ziel.Oeffnen(zeichenfolge))
        {
            erste_phase = Vorbereiten(erste, "grenze", 600, ref abgewiesen);
        }

        Assert.Null(abgewiesen);
        using var zweite = Ziel.Oeffnen(zeichenfolge);
        var zweite_phase = Vorbereiten(zweite, "grenze", 1100, ref abgewiesen);

        Ziel.Messung(_ausgabe, $"Pooling, erste Open-Phase {erste_phase} vorbereitete Befehle, zweite Open-Phase derselben " +
            $"physischen Verbindung: {zweite_phase} beantwortet, dann " +
            (abgewiesen is null ? "keine Abweisung" : Ziel.Beschreibung(abgewiesen)) + "; Verbindung " + zweite.State);
        Assert.Equal(600, erste_phase);
        Assert.Equal(1000, zweite_phase);
        Assert.NotNull(abgewiesen);
        Assert.Equal(50010, abgewiesen!.Number);
        Assert.Equal(ConnectionState.Open, zweite.State);
        using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", zweite);
        Assert.Equal(1, folge.ExecuteScalar());
        zweite.Close();
        SqlConnection.ClearAllPools();
    }

    [Fact]
    public void Kennwortwechsel_wird_abgewiesen()
    {
        var zeichenfolge = Ziel.Tds74();
        zeichenfolge.Pooling = false;

        var ausnahme = Assert.Throws<SqlException>(() => SqlConnection.ChangePassword(zeichenfolge.ConnectionString, "neu"));

        Ziel.Messung(_ausgabe, "ChangePassword: " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(18456, ausnahme.Number);
        Assert.Equal(14, ausnahme.Class);
    }

    [Fact]
    public void Anmeldung_mit_Zugriffstoken_wird_abgewiesen()
    {
        var zeichenfolge = Ziel.Tds74();
        zeichenfolge.UserID = string.Empty;
        zeichenfolge.Password = string.Empty;
        using var verbindung = new SqlConnection(zeichenfolge.ConnectionString) { AccessToken = "kein-echtes-token" };

        var ausnahme = Assert.Throws<SqlException>(() => verbindung.Open());

        Ziel.Messung(_ausgabe, "AccessToken (Anmeldung mit Zugriffstoken): " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(18456, ausnahme.Number);
        Assert.Equal(14, ausnahme.Class);
    }
}
