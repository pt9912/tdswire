using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Ein Abschnitt des Bytestroms: ein TDS-Paket der Anmeldung (<c>'T'</c>) oder ein TLS-Record (<c>'S'</c>), mit dem ersten Byte
/// (TDS-Paketart 0x12 oder 0x04, TLS-Inhaltsart 0x14 bis 0x17), Gesamtlänge und Zeit.
/// </summary>
internal sealed record StromRecord(char Art, byte Typ, int Laenge, long Ticks);

/// <summary>
/// Zerlegt den Bytestrom einer Richtung einer TDS-Verbindung mit TLS in Abschnitte, ohne zu entschlüsseln: solange der Strom mit
/// 0x12 (Anfrage PRELOGIN) oder 0x04 (Antwort darauf) beginnt, ist es ein TDS-Paket (trägt den TLS-Handschlag; Länge aus Byte 2 und 3,
/// Netzwerkreihenfolge); beginnt er mit 0x14 bis 0x17, ist es ein TLS-Record (Länge aus Byte 3 und 4, Netzwerkreihenfolge, plus 5 Byte
/// Kopf). Jedes andere erste Byte beendet die Zerlegung (der Rest des Stroms wird nicht gedeutet). Die Zeit eines Abschnitts ist die
/// des Lesevorgangs, der ihn vervollständigt hat; sie hängt an der Aufteilung des Stroms durch den Leser und ist keine Aussage über
/// die Sendezeit. Ist <see cref="BeiRecord"/> gesetzt, gibt der Zerleger jeden vollständigen Abschnitt mit seinen Bytes heraus (und den
/// nicht gedeuteten Rest mit <c>null</c> als Abschnitt).
/// </summary>
internal sealed class RecordZerleger
{
    private readonly List<byte> _kopf = new();
    private readonly List<byte> _roh = new();
    private int _fehlt;
    private char _art;
    private byte _typ;
    private int _laenge;
    private bool _beendet;

    public List<StromRecord> Abschnitte { get; } = new();

    public Action<byte[], StromRecord?>? BeiRecord { get; init; }

    private void Fertig(long ticks)
    {
        var record = new StromRecord(_art, _typ, _laenge, ticks);
        Abschnitte.Add(record);
        if (BeiRecord != null)
        {
            var roh = _roh.ToArray();
            _roh.Clear();
            BeiRecord(roh, record);
        }
    }

    public void Fuettern(ReadOnlySpan<byte> bytes, long ticks)
    {
        var i = 0;
        while (i < bytes.Length)
        {
            if (_beendet)
            {
                BeiRecord?.Invoke(_roh.Concat(bytes[i..].ToArray()).ToArray(), null);
                _roh.Clear();
                return;
            }

            if (_fehlt > 0)
            {
                var n = Math.Min(_fehlt, bytes.Length - i);
                if (BeiRecord != null) _roh.AddRange(bytes.Slice(i, n).ToArray());
                _fehlt -= n;
                i += n;
                if (_fehlt == 0) Fertig(ticks);
                continue;
            }

            _kopf.Add(bytes[i++]);
            if (BeiRecord != null) _roh.Add(_kopf[^1]);
            var erste = _kopf[0];
            var tds = erste is 0x12 or 0x04;
            var kopfLaenge = tds ? 8 : 5;
            if (!tds && (erste < 0x14 || erste > 0x17))
            {
                _beendet = true;
                continue;
            }

            if (_kopf.Count < kopfLaenge) continue;
            _art = tds ? 'T' : 'S';
            _typ = erste;
            _laenge = tds ? (_kopf[2] << 8) | _kopf[3] : kopfLaenge + ((_kopf[3] << 8) | _kopf[4]);
            _kopf.Clear();
            if (_laenge < kopfLaenge)
            {
                _beendet = true;
                continue;
            }

            _fehlt = _laenge - kopfLaenge;
            if (_fehlt == 0) Fertig(ticks);
        }
    }
}

/// <summary>
/// Eingriff des Relais in die Antworten des Servers nach dem ersten TLS-Record des Clients, der auf <see cref="RecordRelais.Scharf"/>
/// folgt (das ATTENTION des Messschritts). <see cref="Keiner"/>: Bytes unverändert in den Stücken des Lesevorgangs. Die anderen
/// geben die Antworten Record für Record weiter: <see cref="Einzeln"/> jeden Record sofort in einem eigenen Schreibvorgang;
/// <see cref="Verzoegern"/> wie Einzeln, der erste Record der Länge <c>antwortLaenge</c> aber erst nach der Verzögerung;
/// <see cref="Buendeln"/> hält die Records, bis der erste der Länge <c>antwortLaenge</c> da ist (höchstens eine halbe Sekunde), und gibt
/// alle zusammen in einem Schreibvorgang weiter.
/// </summary>
internal enum Eingriff
{
    Keiner,
    Einzeln,
    Verzoegern,
    Buendeln,
}

/// <summary>
/// Ein Relais ohne TLS-Beendigung (Bytes unverändert, in beide Richtungen), das den Strom jeder Richtung in
/// <see cref="StromRecord"/> zerlegt. Anders als der TLS-beendende Proxy der Messreihe am Draht verändert es die Aufteilung der
/// TLS-Records nicht: der Client erhält dieselben Records wie ohne Relais (ohne <see cref="Eingriff"/>; mit Eingriff ändert es
/// allein Zeitpunkt und Bündelung der Schreibvorgänge, nicht die Bytes). Nimmt genau eine Verbindung an.
/// </summary>
internal sealed class RecordRelais : IDisposable
{
    private static readonly TimeSpan HalteGrenze = TimeSpan.FromMilliseconds(500);

    private readonly TcpListener _lauscher = new(IPAddress.Loopback, 0);
    private readonly RecordZerleger _vomClient;
    private readonly RecordZerleger _vomServer;
    private readonly object _sperre = new();
    private readonly object _schreiben = new();
    private readonly Eingriff _eingriff;
    private readonly int _antwortLaenge;
    private readonly int _verzoegerungMs;
    private readonly List<byte> _gehalten = new();
    private volatile bool _scharf;
    private volatile bool _attention;
    private volatile bool _wirkt = true;
    private Timer? _haltezeit;
    private TcpClient? _client;
    private TcpClient? _server;
    private Stream? _zumClient;

    public RecordRelais(string host, int port, Eingriff eingriff = Eingriff.Keiner, int antwortLaenge = 0, int verzoegerungMs = 0)
    {
        _eingriff = eingriff;
        _antwortLaenge = antwortLaenge;
        _verzoegerungMs = verzoegerungMs;
        _vomClient = new RecordZerleger { BeiRecord = BeiRecordDesClients };
        _vomServer = eingriff == Eingriff.Keiner ? new RecordZerleger() : new RecordZerleger { BeiRecord = BeiRecordDesServers };
        _lauscher.Start();
        Task.Run(() => Annehmen(host, port));
    }

    public int Port => ((IPEndPoint)_lauscher.LocalEndpoint).Port;

    /// <summary>Der nächste TLS-Datenrecord des Clients gilt als ATTENTION: von seiner Ankunft an wirkt der Eingriff.</summary>
    public void Scharf() => _scharf = true;

    private void BeiRecordDesClients(byte[] roh, StromRecord? record)
    {
        if (_scharf && record is { Typ: 0x17 }) _attention = true;
    }

    private void Annehmen(string host, int port)
    {
        try
        {
            _client = _lauscher.AcceptTcpClient();
            _lauscher.Stop();
            _client.NoDelay = true;
            _server = new TcpClient { NoDelay = true };
            _server.Connect(host, port);
            var vomClient = _client.GetStream();
            var vomServer = _server.GetStream();
            _zumClient = vomClient;
            Task.Run(() => Pumpe(vomClient, vomServer, _vomClient, true));
            Task.Run(() => Pumpe(vomServer, vomClient, _vomServer, false));
        }
        catch (Exception e) when (e is SocketException or ObjectDisposedException or IOException or InvalidOperationException)
        {
            Schliessen();
        }
    }

    private void Schreiben(byte[] bytes)
    {
        lock (_schreiben)
        {
            if (_zumClient == null || bytes.Length == 0) return;
            _zumClient.Write(bytes, 0, bytes.Length);
            _zumClient.Flush();
        }
    }

    private void Halten(byte[] roh, bool freigeben)
    {
        lock (_gehalten)
        {
            _gehalten.AddRange(roh);
            if (_haltezeit == null && !freigeben) _haltezeit = new Timer(_ => Freigeben(), null, HalteGrenze, Timeout.InfiniteTimeSpan);
            if (freigeben) FreigebenUnterSperre();
        }
    }

    private void Freigeben()
    {
        lock (_gehalten) FreigebenUnterSperre();
    }

    private void FreigebenUnterSperre()
    {
        _haltezeit?.Dispose();
        _haltezeit = null;
        _wirkt = false;
        var bytes = _gehalten.ToArray();
        _gehalten.Clear();
        try
        {
            Schreiben(bytes);
        }
        catch (Exception e) when (e is IOException or ObjectDisposedException or SocketException)
        {
            // Ende der Verbindung.
        }
    }

    private void BeiRecordDesServers(byte[] roh, StromRecord? record)
    {
        var antwort = _attention && _wirkt && record != null && record.Laenge == _antwortLaenge && record.Typ == 0x17;
        switch (_eingriff)
        {
            case Eingriff.Verzoegern when antwort:
                _wirkt = false;
                Thread.Sleep(_verzoegerungMs);
                Schreiben(roh);
                break;
            case Eingriff.Buendeln when _attention && _wirkt:
                Halten(roh, antwort);
                break;
            default:
                Schreiben(roh);
                break;
        }
    }

    private void Pumpe(Stream von, Stream nach, RecordZerleger zerleger, bool vomClient)
    {
        var puffer = new byte[65536];
        try
        {
            int n;
            while ((n = von.Read(puffer, 0, puffer.Length)) > 0)
            {
                var ticks = Stopwatch.GetTimestamp();
                lock (_sperre) zerleger.Fuettern(puffer.AsSpan(0, n), ticks);
                if (vomClient || _eingriff == Eingriff.Keiner)
                {
                    lock (vomClient ? nach : _schreiben)
                    {
                        nach.Write(puffer, 0, n);
                        nach.Flush();
                    }
                }
            }
        }
        catch (Exception e) when (e is IOException or ObjectDisposedException or SocketException)
        {
            // Ende der Verbindung: die Aufzeichnung bleibt, wie sie ist.
        }
        finally
        {
            Schliessen();
        }
    }

    /// <summary>Die bisher aufgezeichneten Abschnitte von Client zu Server.</summary>
    public IReadOnlyList<StromRecord> VomClient()
    {
        lock (_sperre) return _vomClient.Abschnitte.ToList();
    }

    /// <summary>Die bisher aufgezeichneten Abschnitte von Server zu Client.</summary>
    public IReadOnlyList<StromRecord> VomServer()
    {
        lock (_sperre) return _vomServer.Abschnitte.ToList();
    }

    private void Schliessen()
    {
        _client?.Close();
        _server?.Close();
    }

    public void Dispose()
    {
        _lauscher.Stop();
        Schliessen();
        lock (_gehalten)
        {
            _haltezeit?.Dispose();
            _haltezeit = null;
        }
    }
}
