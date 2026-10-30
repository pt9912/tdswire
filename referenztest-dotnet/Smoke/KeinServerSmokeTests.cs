using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using Microsoft.Data.SqlClient;
using Xunit;

namespace TdsWire.Referenztests.Smoke;

/// <summary>
/// Gerüstbeleg: Restore, Build und Test laufen in Docker ohne Mount. Ohne Netz läuft nur die
/// Stage `test` (<c>--network=none</c>); der Restore holt die Pakete von api.nuget.org. Der Test
/// braucht keinen TDS-Server und sagt nichts über das Verhalten eines TDS-Servers (ADR-0008,
/// TDS-QA-06).
/// </summary>
public class KeinServerSmokeTests
{
    // Gemessen in der Dockerfile-Stage `test` (SqlClient 7.1.1, --network=none, Verbindungszeichenfolge
    // unten): SqlException mit Number 0 und Class 20, Dauer 702 ms bei Connect Timeout=1. Fünf Versuche
    // mit Timeout 3 und 1 endeten im selben Ausnahmetyp. Das Fehlerbild ist nur an SqlClient 7.1.1
    // gemessen; eine neue Version kann Ausnahmetyp, Number oder Class ändern und den Test rot machen,
    // ohne dass das Gerüst defekt ist. Die Dauer lag in 22 Läufen bei 769 bis 1000 ms (etwa 0,8 x
    // Timeout); die Grenze von 5 s ist eine Setzung mit rund Faktor 5 Abstand zu diesem Bereich.
    private static readonly TimeSpan Obergrenze = TimeSpan.FromSeconds(5);

    [Fact]
    public void Verbindung_zu_unbelegtem_Loopback_Port_scheitert_mit_SqlException_innerhalb_der_Zeitgrenze()
    {
        var port = UnbelegterLoopbackPort();
        var zeichenfolge = $"Server=tcp:127.0.0.1,{port};User ID=u;Password=p;Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=1";
        var uhr = Stopwatch.StartNew();

        var ausnahme = Assert.Throws<SqlException>(() =>
        {
            using var verbindung = new SqlConnection(zeichenfolge);
            verbindung.Open();
        });

        uhr.Stop();
        Assert.Equal(0, ausnahme.Number);
        Assert.Equal(20, ausnahme.Class);
        Assert.True(uhr.Elapsed < Obergrenze, $"Dauer {uhr.ElapsedMilliseconds} ms");
    }

    // Der Listener bindet Port 0, das Betriebssystem vergibt einen freien Port; nach dem Stopp
    // lauscht dort niemand mehr.
    private static int UnbelegterLoopbackPort()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }
}
