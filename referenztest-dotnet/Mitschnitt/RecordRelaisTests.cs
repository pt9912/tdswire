using System.Net;
using System.Net.Sockets;
using Xunit;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Tests des Werkzeugs der Messung zum Zusatz „ Operation cancelled by user.“ (SPEC-045, TDS-QA-01) ohne Referenzinstanz: Zerlegung eines
/// Bytestroms in TDS-Pakete der Anmeldung und TLS-Records, und das Relais ohne TLS-Beendigung. Sie belegen das Werkzeug, nicht das
/// Verhalten einer Instanz. Zuordnung `@spec SPEC-045` je Test als Kommentar `// @spec` (Kopf von `tools/spec-abdeckung.sh`).
/// </summary>
[Trait("Kategorie", "MitschnittEinheit")]
public class RecordRelaisTests
{
    private static byte[] TdsPrelogin(int nutzlast, byte art = 0x12) =>
        new byte[] { art, 0x01, (byte)((8 + nutzlast) >> 8), (byte)(8 + nutzlast), 0, 0, 1, 0 }.Concat(new byte[nutzlast]).ToArray();

    private static byte[] TlsRecord(int nutzlast) =>
        new byte[] { 0x17, 0x03, 0x03, (byte)(nutzlast >> 8), (byte)nutzlast }.Concat(new byte[nutzlast]).ToArray();

    // @spec SPEC-045
    [Fact]
    public void Der_Zerleger_trennt_TDS_Pakete_und_TLS_Records_auch_ueber_Lesegrenzen_hinweg()
    {
        var strom = TdsPrelogin(4).Concat(TlsRecord(21)).Concat(TlsRecord(300)).ToArray();
        var zerleger = new RecordZerleger();
        // Lesegrenzen mitten im Kopf (nach 2 Bytes) und mitten in einem Record (nach 40 Bytes).
        zerleger.Fuettern(strom.AsSpan(0, 2), 10);
        zerleger.Fuettern(strom.AsSpan(2, 38), 20);
        zerleger.Fuettern(strom.AsSpan(40), 30);

        Assert.Equal(new[] { ('T', 12), ('S', 26), ('S', 305) }, zerleger.Abschnitte.Select(a => (a.Art, a.Laenge)).ToArray());
        // Die Zeit eines Abschnitts ist die des Lesevorgangs, der ihn vervollständigt: das TDS-Paket (12 Bytes) und der erste Record
        // (bis Byte 38) enden im zweiten Lesevorgang, der zweite Record erst im dritten.
        Assert.Equal(new long[] { 20, 20, 30 }, zerleger.Abschnitte.Select(a => a.Ticks).ToArray());
        Assert.Equal(new byte[] { 0x12, 0x17, 0x17 }, zerleger.Abschnitte.Select(a => a.Typ).ToArray());
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Zerleger_zaehlt_zwei_Records_in_einem_Lesevorgang_einzeln()
    {
        var zerleger = new RecordZerleger();
        zerleger.Fuettern(TlsRecord(21).Concat(TlsRecord(21)).ToArray(), 5);

        Assert.Equal(new[] { 26, 26 }, zerleger.Abschnitte.Select(a => a.Laenge).ToArray());
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Zerleger_beendet_die_Zerlegung_bei_einem_unbekannten_ersten_Byte()
    {
        var zerleger = new RecordZerleger();
        zerleger.Fuettern(TlsRecord(3).Concat(new byte[] { 0x55, 0x00, 0x00, 0x00, 0x00, 0x00 }).Concat(TlsRecord(3)).ToArray(), 1);

        Assert.Single(zerleger.Abschnitte);
    }

    // @spec SPEC-045
    [Fact]
    public async Task Das_Relais_reicht_die_Bytes_unveraendert_durch_und_zeichnet_die_Records_beider_Richtungen_auf()
    {
        using var ziel = new TcpListener(IPAddress.Loopback, 0);
        ziel.Start();
        var zielPort = ((IPEndPoint)ziel.LocalEndpoint).Port;
        var antwort = TdsPrelogin(4, 0x04).Concat(TlsRecord(21)).Concat(TlsRecord(21)).ToArray();
        var anfrage = TlsRecord(7);
        var gesehen = new byte[anfrage.Length];
        var server = Task.Run(() =>
        {
            using var verbindung = ziel.AcceptTcpClient();
            var s = verbindung.GetStream();
            s.ReadExactly(gesehen);
            s.Write(antwort);
            s.Flush();
            // Der Server hält die Verbindung offen, bis der Client sie schließt.
            while (s.Read(new byte[16]) > 0)
            {
            }
        });

        using var relais = new RecordRelais("127.0.0.1", zielPort);
        using var client = new TcpClient();
        client.Connect("127.0.0.1", relais.Port);
        var cs = client.GetStream();
        cs.Write(anfrage);
        cs.Flush();
        var empfangen = new byte[antwort.Length];
        cs.ReadExactly(empfangen);

        Assert.Equal(antwort, empfangen);
        Assert.Equal(anfrage, gesehen);
        client.Close();
        await server.WaitAsync(TimeSpan.FromSeconds(10));
        Assert.Equal(new[] { ('S', 12) }, relais.VomClient().Select(a => (a.Art, a.Laenge)).ToArray());
        Assert.Equal(new[] { ('T', 12), ('S', 26), ('S', 26) }, relais.VomServer().Select(a => (a.Art, a.Laenge)).ToArray());
        Assert.Equal(new byte[] { 0x04, 0x17, 0x17 }, relais.VomServer().Select(a => a.Typ).ToArray());
    }

    /// <summary>
    /// Der Server aus der Hand schreibt nach dem ATTENTION des Clients einen Record (30 Bytes), wartet 150 ms und schreibt den Record
    /// der Länge 26 und einen kurzen (10 Bytes). Ergebnis: Zeit bis zum ersten Byte beim Client und Zeit, bis alle 66 Bytes da sind.
    /// </summary>
    private static async Task<(double ErstesByteMs, double AlleMs, byte[] Empfangen)> AttentionUndAntwort(Eingriff eingriff, int verzoegerungMs)
    {
        using var ziel = new TcpListener(IPAddress.Loopback, 0);
        ziel.Start();
        var zielPort = ((IPEndPoint)ziel.LocalEndpoint).Port;
        var erster = TlsRecord(25);
        var zweiter = TlsRecord(21);
        var dritter = TlsRecord(5);
        var server = Task.Run(() =>
        {
            using var verbindung = ziel.AcceptTcpClient();
            verbindung.NoDelay = true;
            var s = verbindung.GetStream();
            s.ReadExactly(new byte[TlsRecord(2).Length]);
            s.Write(erster);
            s.Flush();
            Thread.Sleep(150);
            s.Write(zweiter);
            s.Flush();
            s.Write(dritter);
            s.Flush();
            while (s.Read(new byte[16]) > 0)
            {
            }
        });

        using var relais = new RecordRelais("127.0.0.1", zielPort, eingriff, antwortLaenge: zweiter.Length, verzoegerungMs: verzoegerungMs);
        using var client = new TcpClient { NoDelay = true };
        client.Connect("127.0.0.1", relais.Port);
        var cs = client.GetStream();
        relais.Scharf();
        var uhr = System.Diagnostics.Stopwatch.StartNew();
        cs.Write(TlsRecord(2));
        cs.Flush();
        var gesamt = erster.Length + zweiter.Length + dritter.Length;
        var empfangen = new byte[gesamt];
        empfangen[0] = (byte)cs.ReadByte();
        var erstesByte = uhr.Elapsed.TotalMilliseconds;
        cs.ReadExactly(empfangen.AsSpan(1));
        var alle = uhr.Elapsed.TotalMilliseconds;
        client.Close();
        await server.WaitAsync(TimeSpan.FromSeconds(10));
        return (erstesByte, alle, empfangen);
    }

    // @spec SPEC-045
    [Fact]
    public async Task Eingriff_Einzeln_gibt_jeden_Record_sofort_weiter()
    {
        var (erstes, alle, empfangen) = await AttentionUndAntwort(Eingriff.Einzeln, 0);

        Assert.Equal(TlsRecord(25).Concat(TlsRecord(21)).Concat(TlsRecord(5)).ToArray(), empfangen);
        Assert.True(erstes < 100, $"erstes Byte nach {erstes} ms");
        Assert.True(alle >= 140, $"alle Bytes nach {alle} ms");
    }

    // @spec SPEC-045
    [Fact]
    public async Task Eingriff_Buendeln_haelt_den_ersten_Record_bis_der_der_Antwortlaenge_da_ist()
    {
        var (erstes, alle, empfangen) = await AttentionUndAntwort(Eingriff.Buendeln, 0);

        Assert.Equal(TlsRecord(25).Concat(TlsRecord(21)).Concat(TlsRecord(5)).ToArray(), empfangen);
        Assert.True(erstes >= 140, $"erstes Byte nach {erstes} ms");
        Assert.True(alle - erstes < 100, $"Rest nach {alle - erstes} ms");
    }

    // @spec SPEC-045
    [Fact]
    public async Task Eingriff_Verzoegern_haelt_nur_den_Record_der_Antwortlaenge_und_die_danach_zurueck()
    {
        var (erstes, alle, empfangen) = await AttentionUndAntwort(Eingriff.Verzoegern, 400);

        Assert.Equal(TlsRecord(25).Concat(TlsRecord(21)).Concat(TlsRecord(5)).ToArray(), empfangen);
        Assert.True(erstes < 100, $"erstes Byte nach {erstes} ms");
        Assert.True(alle >= 500, $"alle Bytes nach {alle} ms");
    }
}
