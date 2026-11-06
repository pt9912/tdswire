using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.EigenesBackend;

/// <summary>
/// Ein eigenes In-Memory-Backend des Integrators am Referenzclient (Microsoft.Data.SqlClient 7.1.1) über die
/// Prozessgrenze (TDS-FA-08, TDS-RB-04; `make eigenes-backend`, Stufe 8 von `make e2e`, kein Gate). Der Serverprozess
/// ist die Dockerfile-Stage `eigenesbackendserver`: `TdsServer.start` mit `BackendSource.Custom(EigenesBackend)`, ein
/// `QueryBackend`, das nicht an das Simulations-Backend delegiert (`EigenesBackendServer.kt`, nur öffentliche Fläche der
/// Bibliothek). Das Ziel steht in TDSWIRE_ZIEL (host:port); TDS 7.4 ohne Verschlüsselung und ohne Pool.
/// Belegt: gefülltes und leeres Ergebnis aus dem Backend; ein Backend, das am Start mit einem Fehler antwortet
/// (`TDS_BACKEND_ERROR`, so meldet es ein nicht erreichbares Backend), ein Fehler nach Zeilen und ein `execute`, das
/// eine Ausnahme wirft, ergeben je einen definierten Fehler beim Client, der Text des Backends erreicht ihn nicht,
/// und der Server beantwortet danach Anfragen (dieselbe oder eine neue Verbindung).
/// Nicht belegt: ein Backend, das hängt (Frist des Servers: Stufe 7 an der Simulation), Cancel am eigenen Backend,
/// parametrisierte Abfragen und Änderungsanweisungen, TLS und TDS 8.0, Pooling, ein Verbraucher außerhalb des
/// Gradle-Projekts gegen das gebaute Jar, ein echtes externes Backend (Datenbank, Netz), das Schließen der Verbindung
/// durch den Server nach 50007 (`Closed` ist der Zustand am Client, wie in Stufe 7), die Ausgabe der Bibliothek (der
/// Server läuft ohne Senke).
/// </summary>
[Trait("Kategorie", "EigenesBackend")]
public class EigenesBackendTests
{
    // Kopplung: wie OWN_BACKEND_ROWS_BEFORE_FAILURE in EigenesBackendServer.kt.
    private const int ZeilenVorDemFehler = 3;

    private readonly ITestOutputHelper _ausgabe;

    public EigenesBackendTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static SqlConnection Verbinden()
    {
        var ziel = Environment.GetEnvironmentVariable("TDSWIRE_ZIEL")
            ?? throw new InvalidOperationException("TDSWIRE_ZIEL fehlt (host:port)");
        var trenner = ziel.LastIndexOf(':');
        var zeichenfolge = $"Server=tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]};User ID=alice;Password=secret;"
            + "Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=15;Command Timeout=60";
        var verbindung = new SqlConnection(zeichenfolge);
        verbindung.Open();
        return verbindung;
    }

    private static void ErwarteGefuellt(SqlConnection verbindung)
    {
        using var befehl = new SqlCommand("SELECT Id, Name FROM eigen", verbindung);
        using var leser = befehl.ExecuteReader();
        Assert.Equal(new[] { "Id", "Name" }, Enumerable.Range(0, leser.FieldCount).Select(leser.GetName));
        Assert.True(leser.Read());
        Assert.Equal((1, "Eins"), (leser.GetInt32(0), leser.GetString(1)));
        Assert.True(leser.Read());
        Assert.Equal((2, "Zwei"), (leser.GetInt32(0), leser.GetString(1)));
        Assert.False(leser.Read());
        Assert.False(leser.NextResult());
    }

    private void Messung(SqlException e) =>
        _ausgabe.WriteLine($"messung: SqlException Number {e.Number}, Class {e.Class}, State {e.State}, Meldung '{e.Message}'");

    [Fact]
    public void Das_eigene_Backend_beantwortet_ein_gefuelltes_und_ein_leeres_Ergebnis()
    {
        using var verbindung = Verbinden();

        ErwarteGefuellt(verbindung);

        using var befehl = new SqlCommand("SELECT Id, Name FROM leer", verbindung);
        using var leser = befehl.ExecuteReader();
        Assert.Equal(2, leser.FieldCount);
        Assert.False(leser.HasRows);
        Assert.False(leser.Read());
        Assert.False(leser.NextResult());
    }

    [Fact]
    public void Ein_Fehler_des_Backends_am_Start_ist_ein_definierter_Fehler_und_die_Verbindung_bleibt_nutzbar()
    {
        using var verbindung = Verbinden();
        using var befehl = new SqlCommand("SELECT Id, Name FROM fehler", verbindung);

        var ausnahme = Assert.Throws<SqlException>(() =>
        {
            using var leser = befehl.ExecuteReader();
            while (leser.Read())
            {
            }
        });

        Messung(ausnahme);
        Assert.Equal(50004, ausnahme.Number);
        Assert.Equal(16, ausnahme.Class);
        Assert.DoesNotContain("Text des Backends", ausnahme.Message);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        ErwarteGefuellt(verbindung);
    }

    [Fact]
    public void Ein_Fehler_des_Backends_nach_Zeilen_ist_ein_definierter_Fehler_und_die_Verbindung_bleibt_nutzbar()
    {
        using var verbindung = Verbinden();
        using var befehl = new SqlCommand("SELECT Id, Name FROM fehler_nach_zeilen", verbindung);
        var gelesen = 0;

        var ausnahme = Assert.Throws<SqlException>(() =>
        {
            using var leser = befehl.ExecuteReader();
            while (leser.Read()) gelesen++;
        });

        Messung(ausnahme);
        Assert.Equal(ZeilenVorDemFehler, gelesen);
        Assert.Equal(50004, ausnahme.Number);
        Assert.Equal(16, ausnahme.Class);
        Assert.DoesNotContain("Text des Backends", ausnahme.Message);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        ErwarteGefuellt(verbindung);
    }

    [Fact]
    public void Ein_Backend_dessen_execute_eine_Ausnahme_wirft_ergibt_einen_definierten_Fehler_und_der_Server_bleibt_verfuegbar()
    {
        using (var verbindung = Verbinden())
        using (var befehl = new SqlCommand("SELECT Id, Name FROM wirft", verbindung))
        {
            var ausnahme = Assert.Throws<SqlException>(() =>
            {
                using var leser = befehl.ExecuteReader();
                while (leser.Read())
                {
                }
            });

            Messung(ausnahme);
            _ausgabe.WriteLine($"messung: Zustand der Verbindung nach dem Fehler: {verbindung.State}");
            // Gemessen: TDS_INTERNAL_ERROR, Nummer 50007, Class 20 (SPEC-015); der Text der Ausnahme erreicht den Client nicht.
            Assert.Equal(50007, ausnahme.Number);
            Assert.Equal(20, ausnahme.Class);
            Assert.DoesNotContain("Geheimnis", ausnahme.Message);
            Assert.DoesNotContain("IOException", ausnahme.Message);
        }

        // Der Server bleibt für weitere Verbindungen verfügbar: neue Verbindungen erhalten die Antworten des Backends.
        for (var runde = 0; runde < 3; runde++)
        {
            using var neu = Verbinden();
            ErwarteGefuellt(neu);
        }
    }
}
