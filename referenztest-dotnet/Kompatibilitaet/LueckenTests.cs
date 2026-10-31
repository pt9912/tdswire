using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Kompatibilitaet;

/// <summary>
/// Messungen der Lücken 3, 4 und 14 der Kompatibilitätsmatrix am Referenzclient (Microsoft.Data.SqlClient 7.1.1)
/// gegen den Serverprozess (`make kompatibilitaet KATEGORIE=Luecken`, Stufe 7 von `make e2e`, ADR-0019, TDS-QA-01):
/// `xml` als RPC-Parameter, mehrere RPCs in einer Nachricht (Batch-Flag, `SqlDataAdapter` mit `UpdateBatchSize`),
/// Kennwortwechsel und FeatureExt der Anmeldung (Zugriffstoken). Jeder Test pinnt, was der Client sieht; die Zeilen
/// `messung: ...` im Bericht sind die Werte, die die Matrix zitiert. Nicht belegt: Windows-SSPI, ein echter
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

        Ziel.Messung(_ausgabe, "AccessToken (FeatureExt FEDAUTH): " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(18456, ausnahme.Number);
        Assert.Equal(14, ausnahme.Class);
    }
}
