using System.Net;
using System.Net.Sockets;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Der mitschneidende Proxy der Aufnahme: lauscht auf einem freien Port von 127.0.0.1 im Container des Clients, verbindet die
/// erste ankommende Verbindung mit dem Ziel und reicht die Bytes in beide Richtungen unverändert und ohne Verzögerung durch;
/// <see cref="Mitschreiber"/> liest dieselben Bytes mit. Der Proxy verändert den Verkehr nicht, kann ihn aber zeitlich
/// verschieben (ein Zwischenschritt je Richtung). Weitere Verbindungen nimmt er nicht an.
/// </summary>
internal sealed class Aufnahme : IDisposable
{
    private readonly TcpListener _lauscher = new(IPAddress.Loopback, 0);
    private readonly Mitschreiber _schreiber = new();
    private readonly string _host;
    private readonly int _port;
    private TcpClient? _client;
    private TcpClient? _server;

    public Aufnahme(string host, int port)
    {
        _host = host;
        _port = port;
        _lauscher.Start();
        Task.Run(Annehmen);
    }

    public int Port => ((IPEndPoint)_lauscher.LocalEndpoint).Port;

    public Mitschreiber Schreiber => _schreiber;

    private void Annehmen()
    {
        try
        {
            _client = _lauscher.AcceptTcpClient();
            _lauscher.Stop();
            _server = new TcpClient();
            _server.Connect(_host, _port);
            var vomClient = _client.GetStream();
            var vomServer = _server.GetStream();
            Task.Run(() => Pumpe(vomClient, vomServer, _schreiber.VomClient));
            Task.Run(() => Pumpe(vomServer, vomClient, _schreiber.VomServer));
        }
        catch (Exception e) when (e is SocketException or ObjectDisposedException or IOException or InvalidOperationException)
        {
            Schliessen();
        }
    }

    // Die Beobachtung geht der Weitergabe voran: der Wechsel in den Klartext ist gesetzt, bevor der Server die erste Anfrage sieht
    // und antwortet.
    private void Pumpe(Stream von, Stream nach, Action<byte[], int> beobachter)
    {
        var puffer = new byte[16384];
        try
        {
            int n;
            while ((n = von.Read(puffer, 0, puffer.Length)) > 0)
            {
                beobachter(puffer, n);
                nach.Write(puffer, 0, n);
                nach.Flush();
            }
        }
        catch (Exception e) when (e is IOException or ObjectDisposedException or SocketException or InvalidDataException)
        {
            // Ende der Verbindung: der Aufrufer sieht das Ergebnis über Anhalten.
        }
        finally
        {
            Schliessen();
        }
    }

    private void Schliessen()
    {
        _client?.Close();
        _server?.Close();
    }

    public IReadOnlyList<Ereignis> Anhalten() => _schreiber.Anhalten();

    public void Dispose()
    {
        _lauscher.Stop();
        Schliessen();
    }
}

/// <summary>Das Ergebnis des Abspielens eines Austauschs: die Antwortnachrichten, die gesendeten ATTENTION und ein Problem.</summary>
internal sealed record AbspielAustausch(IReadOnlyList<byte[]> Antworten, int Attention, string? Problem);

/// <summary>
/// Spielt die Anfragen eines Mitschnitts gegen einen Server ab. Der Client (hier der Referenzclient) meldet sich über diesen
/// Relais am Server an; der Relais reicht PRELOGIN und LOGIN7 im Gleichschritt durch, bis die Antwort der Anmeldung LOGINACK
/// trägt, und sendet danach selbst die aufgenommenen Anfragen, ohne dass der Client etwas sendet. Der Server muss die Sitzung
/// ganz im Klartext führen: abspielbar ist nur eine PRELOGIN-Antwort mit ENCRYPT_NOT_SUP (siehe
/// <see cref="VerschluesselungOption"/>); jede andere bricht das Abspielen mit einer Meldung ab. ATTENTION sendet der Relais nach so vielen Antwortpaketen, wie der Mitschnitt nennt,
/// oder, wenn die Antwort davor endet, sofort danach. Setzung: das Zeitverhalten des Clients ist damit nur nach Paketzahl
/// nachgebildet, nicht nach Zeit.
/// </summary>
internal sealed class Abspieler : IDisposable
{
    public const int PaketGroesse = 8000;
    private static readonly TimeSpan Frist = TimeSpan.FromSeconds(15);
    private readonly TcpListener _lauscher = new(IPAddress.Loopback, 0);

    public Abspieler() => _lauscher.Start();

    public int Port => ((IPEndPoint)_lauscher.LocalEndpoint).Port;

    /// <summary>Der Wert der Option ENCRYPTION (Token 1) der PRELOGIN-Antwort oder -1.</summary>
    public static int VerschluesselungOption(byte[] preloginAntwort)
    {
        var i = 0;
        while (i + 5 <= preloginAntwort.Length && preloginAntwort[i] != 0xFF)
        {
            var offset = (preloginAntwort[i + 1] << 8) | preloginAntwort[i + 2];
            if (preloginAntwort[i] == 0x01 && offset < preloginAntwort.Length) return preloginAntwort[offset];
            i += 5;
        }

        return -1;
    }

    public Task<IReadOnlyList<AbspielAustausch>> Abspielen(IReadOnlyList<Austausch> austausche, string host, int port) =>
        Task.Run<IReadOnlyList<AbspielAustausch>>(() =>
        {
            using var vomClient = _lauscher.AcceptTcpClient();
            using var zumServer = new TcpClient();
            zumServer.Connect(host, port);
            var c = vomClient.GetStream();
            var s = zumServer.GetStream();
            s.ReadTimeout = (int)Frist.TotalMilliseconds;
            Anmeldung(c, s);
            return AnfragenSenden(austausche, s);
        });

    private static byte[] NachrichtDurchreichen(Stream s, Stream c)
    {
        using var nachricht = new MemoryStream();
        while (true)
        {
            var p = TdsPaket.Lesen(s) ?? throw new EndOfStreamException("Server schliesst in der Anmeldung");
            c.Write(p.Bytes());
            c.Flush();
            nachricht.Write(p.Nutzlast);
            if (p.Ende) return nachricht.ToArray();
        }
    }

    private static void Anmeldung(Stream c, Stream s)
    {
        while (true)
        {
            var p = TdsPaket.Lesen(c) ?? throw new EndOfStreamException("Client schliesst in der Anmeldung");
            s.Write(p.Bytes());
            s.Flush();
            if (!p.Ende || p.Typ is not (TdsPaket.Prelogin or TdsPaket.Login7)) continue;
            var antwort = NachrichtDurchreichen(s, c);
            if (p.Typ == TdsPaket.Prelogin)
            {
                var option = VerschluesselungOption(antwort);
                if (option != 2) throw new InvalidOperationException($"Die PRELOGIN-Antwort nennt ENCRYPTION {option}, abspielbar ist nur 2 (ENCRYPT_NOT_SUP)");
                continue;
            }

            var folge = TokenLeser.Lesen(antwort).Folge;
            if (folge.Contains("ERROR")) throw new InvalidOperationException("Der Server lehnt die Anmeldung ab");
            if (folge.Contains("LOGINACK")) return;
        }
    }

    private static IReadOnlyList<AbspielAustausch> AnfragenSenden(IReadOnlyList<Austausch> austausche, Stream s)
    {
        var ergebnisse = new List<AbspielAustausch>();
        string? abbruch = null;
        foreach (var austausch in austausche)
        {
            if (abbruch != null)
            {
                ergebnisse.Add(new AbspielAustausch(Array.Empty<byte[]>(), 0, "nicht abgespielt: " + abbruch));
                continue;
            }

            var ergebnis = Austauschen(austausch, s);
            ergebnisse.Add(ergebnis);
            if (ergebnis.Problem != null) abbruch = "der Austausch davor blieb ohne vollstaendige Antwort";
        }

        return ergebnisse;
    }

    private static AbspielAustausch Austauschen(Austausch austausch, Stream s)
    {
        var antworten = new List<byte[]>();
        var nachricht = new MemoryStream();
        var gelesen = 0;
        var letztesEnde = false;
        var gesendet = 0;

        void Lesen1()
        {
            var p = TdsPaket.Lesen(s) ?? throw new EndOfStreamException("Server schliesst die Verbindung");
            gelesen++;
            letztesEnde = p.Ende;
            nachricht.Write(p.Nutzlast);
            if (p.Ende)
            {
                antworten.Add(nachricht.ToArray());
                nachricht.SetLength(0);
            }
        }

        try
        {
            foreach (var paket in TdsPaket.Zerlegen(austausch.Typ, austausch.Anfrage, PaketGroesse)) s.Write(paket);
            s.Flush();
            if (austausch.AttentionNach.Count == 0)
            {
                do Lesen1();
                while (!letztesEnde);
            }
            else
            {
                foreach (var nach in austausch.AttentionNach)
                {
                    while (gelesen < nach && !letztesEnde) Lesen1();
                    s.Write(TdsPaket.Schreiben(TdsPaket.Attention, true, 1, ReadOnlySpan<byte>.Empty));
                    s.Flush();
                    gesendet++;
                    letztesEnde = false;
                }

                while (antworten.Count == 0 || !TokenLeser.Lesen(antworten[^1]).AttnQuittung) Lesen1();
            }
        }
        catch (Exception e) when (e is IOException or EndOfStreamException or InvalidDataException or ObjectDisposedException)
        {
            return new AbspielAustausch(antworten, gesendet, e is IOException and not EndOfStreamException ? "Zeitueberschreitung beim Warten auf die Antwort" : "Verbindung vom Server beendet");
        }

        return new AbspielAustausch(antworten, gesendet, null);
    }

    public void Dispose() => _lauscher.Stop();
}
