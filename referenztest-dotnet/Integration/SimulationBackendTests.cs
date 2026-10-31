using System.Diagnostics;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Integration;

/// <summary>
/// Integrationslauf nach SPEC-040: der Referenzclient (Microsoft.Data.SqlClient 7.1.1) gegen den Serverprozess mit
/// Simulations-Backend (Dockerfile-Stage `integrationsserver`, `make simulations-integration`). Das Ziel steht in
/// TDSWIRE_ZIEL (host:port). Belegt ist das Verhalten des Referenzclients gegen den Server dieses Repos in TDS 7.4
/// ohne Verschlüsselung und ohne Pool, nicht gegen einen echten SQL Server, nicht TDS 8.0, nicht Last.
/// Die Tabellen und Zahlen stehen in `IntegrationServer.kt` (BIG_ROWS, BIG_TEXT_CHARS, BROKEN_AFTER).
/// Cancel mit Folgeabfrage gehört nicht zu diesem Lauf: der Server beantwortet ein ATTENTION-Paket während einer
/// Anfrage nicht. Die Ausnahme ist befristet und als CO-004 geführt (docs/plan/carveouts/); der Lauf ist Stufe 3 des
/// E2E-Durchgangs `make e2e`, kein Gate.
/// </summary>
[Trait("Kategorie", "Integration")]
public class SimulationBackendTests
{
    // Kopplung: wie BIG_ROWS, BIG_TEXT_CHARS und BROKEN_AFTER in IntegrationServer.kt.
    private const int GrosseZeilen = 500_000;
    private const int GrosseZeichen = 128;
    private const int ZeilenVorDemFehler = 3;

    private readonly ITestOutputHelper _ausgabe;

    public SimulationBackendTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

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

    [Fact]
    public void Auswahl_mit_Bedingung_liefert_Metadaten_und_die_passenden_Zeilen()
    {
        using var verbindung = Verbinden();
        using var befehl = new SqlCommand("SELECT Id, Name, Age, Score FROM person WHERE Age = 36", verbindung);

        using var leser = befehl.ExecuteReader();

        Assert.Equal(4, leser.FieldCount);
        Assert.Equal(new[] { "Id", "Name", "Age", "Score" }, Enumerable.Range(0, 4).Select(leser.GetName));
        Assert.True(leser.Read());
        Assert.Equal(1, leser.GetInt32(0));
        Assert.Equal("Ada", leser.GetString(1));
        Assert.Equal(36, leser.GetInt32(2));
        Assert.Equal(10.50m, leser.GetDecimal(3));
        Assert.True(leser.Read());
        Assert.Equal(3, leser.GetInt32(0));
        Assert.Equal("Cy", leser.GetString(1));
        Assert.True(leser.IsDBNull(3));
        Assert.False(leser.Read());
        Assert.False(leser.NextResult());
    }

    [Fact]
    public void Leeres_Ergebnis_hat_Metadaten_und_keine_Zeile()
    {
        using var verbindung = Verbinden();

        using (var ohneTreffer = new SqlCommand("SELECT Id, Name FROM person WHERE Id = 99", verbindung))
        using (var leser = ohneTreffer.ExecuteReader())
        {
            Assert.Equal(2, leser.FieldCount);
            Assert.False(leser.HasRows);
            Assert.False(leser.Read());
        }

        using var ausDerDatei = new SqlCommand("SELECT Id, Name FROM leer", verbindung);
        using var leerer = ausDerDatei.ExecuteReader();
        Assert.Equal(2, leerer.FieldCount);
        Assert.False(leerer.Read());
    }

    [Fact]
    public void Zeilenquelle_oberhalb_der_Speichergrenze_kommt_vollstaendig_an()
    {
        using var verbindung = Verbinden();
        using var befehl = new SqlCommand("SELECT Id, Text FROM gross", verbindung);
        var uhr = Stopwatch.StartNew();

        using var leser = befehl.ExecuteReader();
        var gezaehlt = 0;
        var letzteId = 0;
        while (leser.Read())
        {
            gezaehlt++;
            letzteId = leser.GetInt32(0);
            Assert.Equal(GrosseZeichen, leser.GetString(1).Length);
        }

        uhr.Stop();
        _ausgabe.WriteLine($"gross: {gezaehlt} Zeilen zu {GrosseZeichen} Zeichen in {uhr.ElapsedMilliseconds} ms");
        Assert.Equal(GrosseZeilen, gezaehlt);
        Assert.Equal(GrosseZeilen, letzteId);
    }

    [Fact]
    public void Fehler_der_Zeilenquelle_kommt_als_SqlException_nach_den_Zeilen_und_die_Verbindung_bleibt_nutzbar()
    {
        using var verbindung = Verbinden();
        using var befehl = new SqlCommand("SELECT Id, Text FROM kaputt", verbindung);
        var gelesen = 0;

        var ausnahme = Assert.Throws<SqlException>(() =>
        {
            using var leser = befehl.ExecuteReader();
            while (leser.Read()) gelesen++;
        });

        Assert.Equal(ZeilenVorDemFehler, gelesen);
        _ausgabe.WriteLine($"kaputt: Number {ausnahme.Number}, Class {ausnahme.Class}, State {ausnahme.State}, Meldung '{ausnahme.Message}'");
        // Gemessen mit SqlClient 7.1.1: Number 50004 (TDS_BACKEND_ERROR), Class 16, Meldung "Backend error.".
        Assert.Equal(50004, ausnahme.Number);
        Assert.Equal(16, ausnahme.Class);
        Assert.DoesNotContain("Geheimnis", ausnahme.Message);
        Assert.Equal(System.Data.ConnectionState.Open, verbindung.State);

        using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        using var leserFolge = folge.ExecuteReader();
        Assert.True(leserFolge.Read());
        Assert.Equal(1, leserFolge.GetInt32(0));
        Assert.False(leserFolge.Read());
    }
}
