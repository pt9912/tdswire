using System.Globalization;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Eine Nachricht im Mitschnitt einer Messung (SPEC-045): Richtung `c` (Client an Server) oder `s`, Paketart, Status des
/// ersten Pakets (Bit 0x08 ist RESETCONNECTION, MS-TDS 2.2.3.1.2), bei ATTENTION die Zahl der Antwortpakete seit der letzten
/// Anfrage, und die zusammengesetzte Nutzlast. Anders als <see cref="Ereignis"/> beginnt die Messung mit PRELOGIN: sie trägt
/// die Anmeldung, LOGIN7 steht redigiert (<see cref="Login7Redaktion"/>).
/// </summary>
internal sealed record Nachricht(char Richtung, byte Typ, byte Status, int Nach, byte[] Daten)
{
    public bool ResetConnection => (Status & 0x08) != 0;
}

/// <summary>
/// Die Ablage einer Messung: Textdatei `# tdswire-messung 1`, Kopfzeilen `# schluessel: wert` (Herkunft), `szenario <name>`
/// und je Nachricht eine Zeile `nachricht <c|s> <typ hex> <status hex> <nach> <nutzlast hex>`. Sie unterscheidet sich von
/// <see cref="MitschnittDatei"/> (nur Anfragen ab der ersten nach der Anmeldung, ohne Status) und wird nicht abgespielt.
/// </summary>
internal sealed class MessDatei
{
    public const string Kennung = "# tdswire-messung 1";

    public List<KeyValuePair<string, string>> Herkunft { get; } = new();

    public string Szenario { get; set; } = "";

    public List<Nachricht> Nachrichten { get; } = new();

    public string Serialisieren()
    {
        var sb = new StringBuilder();
        sb.Append(Kennung).Append('\n');
        foreach (var (schluessel, wert) in Herkunft) sb.Append("# ").Append(schluessel).Append(": ").Append(wert).Append('\n');
        sb.Append("szenario ").Append(Szenario).Append('\n');
        foreach (var n in Nachrichten)
        {
            sb.Append("nachricht ").Append(n.Richtung).Append(' ').Append(n.Typ.ToString("x2", CultureInfo.InvariantCulture)).Append(' ')
                .Append(n.Status.ToString("x2", CultureInfo.InvariantCulture)).Append(' ').Append(n.Nach.ToString(CultureInfo.InvariantCulture))
                .Append(' ').Append(Convert.ToHexString(n.Daten).ToLowerInvariant()).Append('\n');
        }

        return sb.ToString();
    }

    public static MessDatei Lesen(string text)
    {
        var zeilen = text.Split('\n', StringSplitOptions.RemoveEmptyEntries);
        if (zeilen.Length == 0 || zeilen[0] != Kennung) throw new InvalidDataException("Kennung der Messdatei fehlt");
        var datei = new MessDatei();
        foreach (var zeile in zeilen.Skip(1))
        {
            if (zeile.StartsWith("# ", StringComparison.Ordinal))
            {
                var rest = zeile[2..];
                var trenner = rest.IndexOf(": ", StringComparison.Ordinal);
                if (trenner < 0) throw new InvalidDataException("Kopfzeile ohne ': ' : " + zeile);
                datei.Herkunft.Add(new(rest[..trenner], rest[(trenner + 2)..]));
            }
            else if (zeile.StartsWith("szenario ", StringComparison.Ordinal))
            {
                datei.Szenario = zeile["szenario ".Length..];
            }
            else if (zeile.StartsWith("nachricht ", StringComparison.Ordinal))
            {
                var teile = zeile.Split(' ');
                if (teile.Length != 6 || teile[1] is not ("c" or "s")) throw new InvalidDataException("nachricht: " + zeile);
                datei.Nachrichten.Add(new Nachricht(teile[1][0], Convert.ToByte(teile[2], 16), Convert.ToByte(teile[3], 16),
                    int.Parse(teile[4], CultureInfo.InvariantCulture), Convert.FromHexString(teile[5])));
            }
            else
            {
                throw new InvalidDataException("unbekannte Zeile: " + zeile);
            }
        }

        return datei;
    }

    /// <summary>
    /// Sucht ein Geheimnis (Kennwort) in der Textform und in den Nutzlasten (<see cref="Redaktion.Enthaelt"/>) und, ohne Beachtung
    /// der Schreibweise in UTF-16LE, die Namen, die der Ablage nicht gehören (Name des Servers, Name des Rechners des Clients).
    /// Gibt die Beschreibung des ersten Treffers zurück oder null.
    /// </summary>
    public string? Treffer(string? geheimnis, params string[] namen)
    {
        var text = Serialisieren();
        if (!string.IsNullOrEmpty(geheimnis) && Redaktion.Enthaelt(Encoding.UTF8.GetBytes(text), geheimnis)) return "Kennwort in der Textform";
        var nr = 0;
        foreach (var n in Nachrichten)
        {
            nr++;
            if (!string.IsNullOrEmpty(geheimnis) && Redaktion.Enthaelt(n.Daten, geheimnis)) return $"Kennwort in der Nutzlast der Nachricht {nr}";
            foreach (var name in namen.Where(x => !string.IsNullOrEmpty(x)))
            {
                if (!Redaktion.NameErsetzen(n.Daten, name).AsSpan().SequenceEqual(n.Daten)) return $"Name '{name}' in der Nutzlast der Nachricht {nr}";
                if (Encoding.UTF8.GetString(n.Daten).Contains(name, StringComparison.OrdinalIgnoreCase)) return $"Name '{name}' (UTF-8) in der Nutzlast der Nachricht {nr}";
            }
        }

        return null;
    }
}

/// <summary>
/// Redigiert die Nachricht LOGIN7 (MS-TDS 2.2.6.4): Rechnername, Benutzername, Kennwort, Servername, SSPI, Datenbankdatei und
/// neues Kennwort werden durch `x` gleicher Länge ersetzt (das Kennwort in der Verschleierung des Drahtes, damit es als `x`
/// zurückgelesen wird), Prozess- und Verbindungskennung und die ClientID (MAC) durch Nullen. Offsets und Längen bleiben, die
/// Merkmalsliste (FEATUREEXT) bleibt unverändert. Fehlschlag schließt: ist die Nachricht kürzer als der feste Teil oder liegt ein
/// Feld außerhalb, ist die ganze Nutzlast `x`. Grenze: Felder, die dieser Plan nicht nennt (Anwendungsname, Bibliotheksname,
/// Sprache, Datenbank, Merkmalsdaten) bleiben stehen; sie sind keine Zugangsdaten.
/// </summary>
internal static class Login7Redaktion
{
    public const int FesterTeil = 94;

    private static int U16(byte[] b, int offset) => b[offset] | (b[offset + 1] << 8);

    private static uint U32(byte[] b, int offset) => (uint)(b[offset] | (b[offset + 1] << 8) | (b[offset + 2] << 16) | (b[offset + 3] << 24));

    public static byte[] Redigieren(byte[] login7)
    {
        var x = new byte[login7.Length];
        Array.Fill(x, (byte)'x');
        if (login7.Length < FesterTeil) return x;
        var ergebnis = (byte[])login7.Clone();
        // (Offset der Angabe ib, Einheit: Zeichen zu 2 Bytes oder Bytes, verschleiert)
        var felder = new (int Angabe, bool Zeichen, bool Verschleiert)[]
        {
            (36, true, false), (40, true, false), (44, true, true), (52, true, false), (78, false, false), (82, true, false), (86, true, true),
        };
        foreach (var (angabe, zeichen, verschleiert) in felder)
        {
            var offset = U16(login7, angabe);
            long laenge = U16(login7, angabe + 2);
            if (angabe == 78 && laenge == 0xFFFF) laenge = U32(login7, 90);
            if (zeichen) laenge *= 2;
            if (laenge == 0) continue;
            if (offset < FesterTeil || offset + laenge > login7.Length) return x;
            for (var i = 0; i < laenge; i++)
            {
                var wert = (byte)(i % 2 == 0 || !zeichen ? 'x' : 0);
                ergebnis[offset + i] = verschleiert ? Redaktion.Verschleiert(new[] { wert })[0] : wert;
            }
        }

        Array.Clear(ergebnis, 16, 8);
        Array.Clear(ergebnis, 72, 6);
        return ergebnis;
    }

    /// <summary>Die Merkmale (FEATUREEXT) einer LOGIN7-Nachricht als `id:Länge` mit den Daten in Hex, oder ein Hinweis.</summary>
    public static IReadOnlyList<string> Merkmale(byte[] login7)
    {
        var liste = new List<string>();
        if (login7.Length < FesterTeil) return liste;
        var zeiger = U16(login7, 56);
        if (U16(login7, 58) != 4 || zeiger + 4 > login7.Length) return liste;
        var p = (int)U32(login7, zeiger);
        while (p < login7.Length && login7[p] != 0xFF)
        {
            if (p + 5 > login7.Length) break;
            var id = login7[p];
            var laenge = (int)U32(login7, p + 1);
            if (laenge < 0 || p + 5 + laenge > login7.Length) break;
            liste.Add($"0x{id:x2}:{laenge}=0x{Convert.ToHexString(login7, p + 5, laenge).ToLowerInvariant()}");
            p += 5 + laenge;
        }

        return liste;
    }

    /// <summary>Bit- und Zahlenfelder des festen Teils als Text: TDS-Version, Paketgröße, vier Flag-Bytes, Längen der Textfelder.</summary>
    public static string Zusammenfassung(byte[] login7)
    {
        if (login7.Length < FesterTeil) return "zu kurz";
        string Feld(string name, int angabe) => $"{name}={U16(login7, angabe + 2)}";
        return $"TDS 0x{U32(login7, 4):x8}, Paketgroesse {U32(login7, 8)}, Flags 0x{login7[24]:x2} 0x{login7[25]:x2} 0x{login7[26]:x2} 0x{login7[27]:x2}, "
            + string.Join(", ", new[] { Feld("Rechnername", 36), Feld("Benutzer", 40), Feld("Kennwort", 44), Feld("Anwendung", 48), Feld("Server", 52), Feld("Bibliothek", 60), Feld("Sprache", 64), Feld("Datenbank", 68) })
            + ", Merkmale [" + string.Join("; ", Merkmale(login7)) + "]";
    }
}

/// <summary>
/// Die Zustandsmaschine der Aufnahme einer Messung, ohne Netz: nimmt die Bytes beider Richtungen entgegen (entschlüsselt, TDS-Pakete
/// von PRELOGIN an) und setzt Nachrichten zusammen. Anders als <see cref="Mitschreiber"/> beginnt sie mit der ersten Nachricht und hält
/// den Status des ersten Pakets fest; LOGIN7 geht nur redigiert in die Liste.
/// </summary>
internal sealed class Protokollierer
{
    private readonly object _sperre = new();
    private readonly Abtaster _client = new();
    private readonly Abtaster _server = new();
    private readonly List<Nachricht> _liste = new();
    private readonly MemoryStream _anfrage = new();
    private readonly MemoryStream _antwort = new();
    private byte _anfrageTyp;
    private byte _anfrageStatus;
    private byte _antwortTyp;
    private byte _antwortStatus;
    private int _antwortPakete;
    private bool _aktiv = true;

    public void VomClient(byte[] daten, int anzahl)
    {
        lock (_sperre)
        {
            foreach (var einheit in _client.Einspeisen(daten, anzahl))
            {
                if (!_aktiv) continue;
                if (einheit.Tls) throw new InvalidDataException("TLS-Record im entschluesselten Strom");
                if (einheit.Typ == TdsPaket.Attention)
                {
                    _liste.Add(new Nachricht('c', TdsPaket.Attention, einheit.Status, _antwortPakete, Array.Empty<byte>()));
                    continue;
                }

                if (_anfrage.Length == 0)
                {
                    _anfrageTyp = einheit.Typ;
                    _anfrageStatus = einheit.Status;
                }

                _anfrage.Write(einheit.Nutzlast);
                if (!einheit.Ende) continue;
                var daten2 = _anfrage.ToArray();
                if (_anfrageTyp == TdsPaket.Login7) daten2 = Login7Redaktion.Redigieren(daten2);
                _liste.Add(new Nachricht('c', _anfrageTyp, _anfrageStatus, 0, daten2));
                _anfrage.SetLength(0);
                _antwortPakete = 0;
            }
        }
    }

    public void VomServer(byte[] daten, int anzahl)
    {
        lock (_sperre)
        {
            foreach (var einheit in _server.Einspeisen(daten, anzahl))
            {
                if (!_aktiv) continue;
                if (einheit.Tls) throw new InvalidDataException("TLS-Record im entschluesselten Strom");
                _antwortPakete++;
                if (_antwort.Length == 0)
                {
                    _antwortTyp = einheit.Typ;
                    _antwortStatus = einheit.Status;
                }

                _antwort.Write(einheit.Nutzlast);
                if (!einheit.Ende) continue;
                _liste.Add(new Nachricht('s', _antwortTyp, _antwortStatus, 0, _antwort.ToArray()));
                _antwort.SetLength(0);
            }
        }
    }

    /// <summary>Beendet die Aufnahme; was danach durchgeht (Abmeldung, Aufräumen des Clients), wird nicht gespeichert.</summary>
    public IReadOnlyList<Nachricht> Anhalten()
    {
        lock (_sperre)
        {
            _aktiv = false;
            return _liste.ToArray();
        }
    }
}

/// <summary>
/// Der Strom für den TLS-Handschlag in TDS 7.4: solange <see cref="Umhuellen"/> gilt, liegt jeder Schreibvorgang in Paketen der Art
/// PRELOGIN (0x12) und das Lesen entnimmt deren Nutzlast (MS-TDS 3.3.5.3, Aushandlung der Verschlüsselung); danach reicht er die Bytes
/// roh durch. Der Wechsel ist erst erlaubt, wenn kein gelesener Rest mehr aussteht.
/// </summary>
internal sealed class HandschlagStrom : Stream
{
    private const int MaxNutzlast = 4096 - TdsPaket.KopfLaenge;
    private readonly Stream _innen;
    private byte[] _rest = Array.Empty<byte>();
    private int _position;

    public HandschlagStrom(Stream innen) => _innen = innen;

    public bool Umhuellen { get; private set; } = true;

    public void Roh()
    {
        if (_position < _rest.Length) throw new InvalidDataException("Rest des Handschlags nicht gelesen");
        Umhuellen = false;
    }

    public override bool CanRead => true;

    public override bool CanSeek => false;

    public override bool CanWrite => true;

    public override long Length => throw new NotSupportedException();

    public override long Position
    {
        get => throw new NotSupportedException();
        set => throw new NotSupportedException();
    }

    public override int Read(byte[] buffer, int offset, int count)
    {
        if (!Umhuellen) return _innen.Read(buffer, offset, count);
        while (_position >= _rest.Length)
        {
            var p = TdsPaket.Lesen(_innen);
            if (p == null) return 0;
            if (p.Typ != TdsPaket.Prelogin) throw new InvalidDataException($"Paketart 0x{p.Typ:x2} im Handschlag");
            _rest = p.Nutzlast;
            _position = 0;
        }

        var n = Math.Min(count, _rest.Length - _position);
        Array.Copy(_rest, _position, buffer, offset, n);
        _position += n;
        return n;
    }

    public override void Write(byte[] buffer, int offset, int count)
    {
        if (!Umhuellen)
        {
            _innen.Write(buffer, offset, count);
            return;
        }

        var nachricht = new byte[count];
        Array.Copy(buffer, offset, nachricht, 0, count);
        foreach (var paket in TdsPaket.Zerlegen(TdsPaket.Prelogin, nachricht, MaxNutzlast + TdsPaket.KopfLaenge)) _innen.Write(paket);
        _innen.Flush();
    }

    public override void Flush() => _innen.Flush();

    public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();

    public override void SetLength(long value) => throw new NotSupportedException();
}

/// <summary>
/// Der mitschneidende Proxy mit TLS-Beendigung (SPEC-045, Messung der Anmeldung): lauscht auf einem freien Port von 127.0.0.1, nimmt
/// die erste Verbindung an und verbindet sich mit dem Ziel. PRELOGIN beider Richtungen reicht er unverändert durch; verlangt der
/// Server nicht ENCRYPT_ON (1), endet die Aufnahme mit <see cref="Problem"/>. Danach führt er den TLS-Handschlag mit dem Client
/// (eigenes, im Prozess erzeugtes Zertifikat, der Client muss ihm vertrauen) und, getrennt, mit dem Server (dessen Zertifikat
/// prüft er gegen den Namen <c>zertifikatsName</c> und die Vertrauensanker des Systems); beide mit TLS 1.2 (Setzung). Ab dem
/// Handschlag reicht er die entschlüsselten Pakete unverändert durch und schreibt sie mit (<see cref="Protokollierer"/>); der
/// Client sieht damit zwei Verschlüsselungen hintereinander, der Server eine. Weitere Verbindungen schließt er sofort und zählt sie (<see cref="WeitereVerbindungen"/>).
/// Grenze: der Verkehr ist um einen Zwischenschritt je Richtung verschoben; ein Client, der das Zertifikat des Proxys prüft,
/// verbindet nicht.
/// </summary>
internal sealed class TlsAufnahme : IDisposable
{
    private readonly TcpListener _lauscher = new(IPAddress.Loopback, 0);
    private readonly Protokollierer _protokoll = new();
    private readonly string _host;
    private readonly int _port;
    private readonly string _zertifikatsName;
    private readonly RemoteCertificateValidationCallback? _serverPruefung;
    private readonly X509Certificate2 _zertifikat = EigenesZertifikat();
    private TcpClient? _client;
    private TcpClient? _server;
    private volatile string? _problem;
    private volatile string? _ende;
    private int _weitere;

    public TlsAufnahme(string host, int port, string zertifikatsName, RemoteCertificateValidationCallback? serverPruefung = null)
    {
        _host = host;
        _port = port;
        _zertifikatsName = zertifikatsName;
        _serverPruefung = serverPruefung;
        _lauscher.Start();
        Task.Run(Annehmen);
    }

    public int Port => ((IPEndPoint)_lauscher.LocalEndpoint).Port;

    public string? Problem => _problem;

    /// <summary>Die Zahl der weiteren Verbindungen, die der Client nach der ersten aufbaute; der Proxy schließt sie sofort (Beleg, ob ein Pool die erste wiederverwendet).</summary>
    public int WeitereVerbindungen => Volatile.Read(ref _weitere);

    /// <summary>Warum die Pumpe der ersten Verbindung endete (Ausnahme oder Ende des Stroms), oder null, solange sie läuft.</summary>
    public string? Ende => _ende;

    public static X509Certificate2 EigenesZertifikat()
    {
        using var rsa = RSA.Create(2048);
        var anfrage = new CertificateRequest("CN=tdswire-messproxy", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        anfrage.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        anfrage.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
        anfrage.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") }, false));
        var namen = new SubjectAlternativeNameBuilder();
        namen.AddDnsName("localhost");
        namen.AddIpAddress(IPAddress.Loopback);
        anfrage.CertificateExtensions.Add(namen.Build());
        using var selbst = anfrage.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddDays(1));
        return X509CertificateLoader.LoadPkcs12(selbst.Export(X509ContentType.Pfx), null);
    }

    private static List<byte[]> NachrichtLesen(Stream s)
    {
        var pakete = new List<byte[]>();
        while (true)
        {
            var kopf = new byte[TdsPaket.KopfLaenge];
            var gelesen = 0;
            while (gelesen < kopf.Length)
            {
                var n = s.Read(kopf, gelesen, kopf.Length - gelesen);
                if (n == 0) throw new EndOfStreamException("Strom endet in PRELOGIN");
                gelesen += n;
            }

            var laenge = (kopf[2] << 8) | kopf[3];
            if (laenge < TdsPaket.KopfLaenge) throw new InvalidDataException("Paketlaenge unter dem Kopf");
            var roh = new byte[laenge];
            Array.Copy(kopf, roh, kopf.Length);
            while (gelesen < laenge)
            {
                var n = s.Read(roh, gelesen, laenge - gelesen);
                if (n == 0) throw new EndOfStreamException("Strom endet in PRELOGIN");
                gelesen += n;
            }

            pakete.Add(roh);
            if ((roh[1] & 0x01) != 0) return pakete;
        }
    }

    private void Annehmen()
    {
        try
        {
            _client = _lauscher.AcceptTcpClient();
            Task.Run(WeitereAbweisen);
            _server = new TcpClient();
            _server.Connect(_host, _port);
            var vomClient = _client.GetStream();
            var vomServer = _server.GetStream();

            var anfrage = NachrichtLesen(vomClient);
            foreach (var p in anfrage)
            {
                _protokoll.VomClient(p, p.Length);
                vomServer.Write(p);
            }

            vomServer.Flush();
            var antwort = NachrichtLesen(vomServer);
            foreach (var p in antwort)
            {
                _protokoll.VomServer(p, p.Length);
                vomClient.Write(p);
            }

            vomClient.Flush();
            var option = Abspieler.VerschluesselungOption(antwort.SelectMany(p => p.Skip(TdsPaket.KopfLaenge)).ToArray());
            if (option != 1)
            {
                _problem = $"die PRELOGIN-Antwort nennt ENCRYPTION {option}, die Aufnahme braucht 1 (ENCRYPT_ON)";
                return;
            }

            var amClient = new HandschlagStrom(vomClient);
            var sslClient = new SslStream(amClient, false);
            sslClient.AuthenticateAsServer(new SslServerAuthenticationOptions
            {
                ServerCertificate = _zertifikat,
                ClientCertificateRequired = false,
                EnabledSslProtocols = SslProtocols.Tls12,
            });
            amClient.Roh();

            var amServer = new HandschlagStrom(vomServer);
            var sslServer = new SslStream(amServer, false);
            sslServer.AuthenticateAsClient(new SslClientAuthenticationOptions
            {
                TargetHost = _zertifikatsName,
                EnabledSslProtocols = SslProtocols.Tls12,
                CertificateRevocationCheckMode = X509RevocationMode.NoCheck,
                RemoteCertificateValidationCallback = _serverPruefung,
            });
            amServer.Roh();

            Task.Run(() => Pumpe(sslClient, sslServer, _protokoll.VomClient));
            Task.Run(() => Pumpe(sslServer, sslClient, _protokoll.VomServer));
        }
        catch (Exception e) when (e is SocketException or ObjectDisposedException or IOException or InvalidOperationException
            or AuthenticationException or InvalidDataException)
        {
            _problem ??= e.GetType().Name + ": " + e.Message;
            Schliessen();
        }
    }

    private void WeitereAbweisen()
    {
        try
        {
            while (true)
            {
                _lauscher.AcceptTcpClient().Close();
                Interlocked.Increment(ref _weitere);
            }
        }
        catch (Exception e) when (e is SocketException or ObjectDisposedException or InvalidOperationException)
        {
            // Der Proxy ist beendet.
        }
    }

    // Die Beobachtung geht der Weitergabe voran, wie in Aufnahme.
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

            _ende ??= "Ende des Stroms";
        }
        catch (Exception e) when (e is IOException or ObjectDisposedException or SocketException or InvalidDataException or AuthenticationException)
        {
            // Ende der Verbindung: der Aufrufer sieht das Ergebnis über Anhalten.
            _ende ??= e.GetType().Name;
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

    public IReadOnlyList<Nachricht> Anhalten() => _protokoll.Anhalten();

    public void Dispose()
    {
        _lauscher.Stop();
        Schliessen();
        _zertifikat.Dispose();
    }
}
