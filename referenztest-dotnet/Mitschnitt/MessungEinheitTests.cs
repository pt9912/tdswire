using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Text;
using Xunit;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Tests des Werkzeugs der Messreihe am Draht ohne Referenzinstanz (SPEC-045, TDS-QA-01): Redaktion von LOGIN7, Ablage der Messung,
/// Zusammensetzen der Nachrichten mit Status, Handschlag-Strom und TLS-beendender Proxy gegen einen Server aus der Hand, Zerlegung
/// von LOGINACK, FEATUREEXTACK, SESSIONSTATE und ENVCHANGE sowie des RPC-Kopfs. Sie belegen das Werkzeug, nicht das Verhalten einer
/// Referenzinstanz. Zuordnung `@spec SPEC-045` je Test als Kommentar `// @spec` (`make spec-abdeckung` liest ihn, Kopf von `tools/spec-abdeckung.sh`).
/// </summary>
[Trait("Kategorie", "MitschnittEinheit")]
public class MessungEinheitTests
{
    private const string Kennwort = "Geheim-Kennwort-4711";

    private static byte[] Cat(params byte[][] teile) => teile.SelectMany(t => t).ToArray();

    private static byte[] Bytes(params int[] werte) => werte.Select(w => (byte)w).ToArray();

    private static byte[] U16(int w) => Bytes(w & 0xFF, w >> 8);

    private static byte[] U32(long w) => Bytes((int)(w & 0xFF), (int)((w >> 8) & 0xFF), (int)((w >> 16) & 0xFF), (int)((w >> 24) & 0xFF));

    private static byte[] Text(string t) => Encoding.Unicode.GetBytes(t);

    /// <summary>Eine LOGIN7-Nachricht mit den Textfeldern (Reihenfolge der Tabelle in MS-TDS 2.2.6.4) und einer Merkmalsliste.</summary>
    private static byte[] Login7(string rechner, string benutzer, string kennwort, string anwendung, string server, string datenbank, byte[]? merkmale = null,
        byte[]? sspi = null, string? neuesKennwort = null, string? datenbankDatei = null)
    {
        var werte = new List<(int Angabe, byte[] Bytes, int Zeichen)>
        {
            (36, Text(rechner), rechner.Length), (40, Text(benutzer), benutzer.Length), (44, Redaktion.Verschleiert(Text(kennwort)), kennwort.Length),
            (48, Text(anwendung), anwendung.Length), (52, Text(server), server.Length), (68, Text(datenbank), datenbank.Length),
        };
        if (sspi != null) werte.Add((78, sspi, sspi.Length));
        if (datenbankDatei != null) werte.Add((82, Text(datenbankDatei), datenbankDatei.Length));
        if (neuesKennwort != null) werte.Add((86, Redaktion.Verschleiert(Text(neuesKennwort)), neuesKennwort.Length));
        var kopf = new byte[Login7Redaktion.FesterTeil];
        kopf[4] = 4;
        kopf[7] = 0x74;
        kopf[16] = 0x11;
        kopf[17] = 0x22;
        for (var i = 0; i < 6; i++) kopf[72 + i] = (byte)(0xA0 + i);
        var variabel = new List<byte>();
        foreach (var (angabe, bytes, zeichen) in werte)
        {
            var offset = Login7Redaktion.FesterTeil + variabel.Count;
            kopf[angabe] = (byte)offset;
            kopf[angabe + 1] = (byte)(offset >> 8);
            kopf[angabe + 2] = (byte)zeichen;
            variabel.AddRange(bytes);
        }

        if (merkmale != null)
        {
            var zeiger = Login7Redaktion.FesterTeil + variabel.Count;
            kopf[56] = (byte)zeiger;
            kopf[57] = (byte)(zeiger >> 8);
            kopf[58] = 4;
            variabel.AddRange(U32(zeiger + 4));
            variabel.AddRange(merkmale);
        }

        var nachricht = Cat(kopf, variabel.ToArray());
        Array.Copy(U32(nachricht.Length), 0, nachricht, 0, 4);
        return nachricht;
    }

    private static byte[] Merkmal(int id, params int[] daten) => Cat(Bytes(id), U32(daten.Length), Bytes(daten));

    // ---- Redaktion von LOGIN7 ----------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [InlineData("Geheim-Kennwort-4711")]
    [InlineData("pässwörd-€-😀")]
    [InlineData("Zq9!Kx")]
    public void Login7_redigiert_Kennwort_Benutzer_Rechner_und_Server_und_laesst_die_uebrigen_Felder(string kennwort)
    {
        var merkmale = Cat(Merkmal(1, 0, 0, 0), Bytes(0xFF));
        var roh = Login7("container0815", "sa", kennwort, "Core Microsoft SqlClient Data Provider", "127.0.0.1,5555", "tdswire_live", merkmale);
        Assert.True(Redaktion.Enthaelt(roh, kennwort));

        var ergebnis = Login7Redaktion.Redigieren(roh);

        Assert.Equal(roh.Length, ergebnis.Length);
        Assert.False(Redaktion.Enthaelt(ergebnis, kennwort));
        Assert.DoesNotContain("container0815", Encoding.Unicode.GetString(ergebnis));
        Assert.DoesNotContain("127.0.0.1", Encoding.Unicode.GetString(ergebnis));
        Assert.Contains("Core Microsoft SqlClient Data Provider", Encoding.Unicode.GetString(ergebnis));
        Assert.Contains("tdswire_live", Encoding.Unicode.GetString(ergebnis));
        Assert.Equal(roh[4..8], ergebnis[4..8]);
        Assert.All(ergebnis[72..78], b => Assert.Equal(0, b));
        Assert.All(ergebnis[16..24], b => Assert.Equal(0, b));
        Assert.Equal(new[] { "0x01:3=0x000000" }, Login7Redaktion.Merkmale(ergebnis));
        Assert.Contains("Kennwort=" + kennwort.Length, Login7Redaktion.Zusammenfassung(ergebnis), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Fact]
    public void Login7_redigiert_SSPI_Kennwortwechsel_und_Datenbankdatei()
    {
        var sspi = Encoding.ASCII.GetBytes("NTLMSSP-Token-4711-geheim");
        const string Neu = "Neues-Kennwort-0815";
        const string Datei = @"C:\geheim\daten.mdf";
        var roh = Login7("r", "u", Kennwort, "app", "s", "d", null, sspi, Neu, Datei);
        Assert.True(Redaktion.Enthaelt(roh, Neu));
        Assert.True(roh.AsSpan().IndexOf(sspi) >= 0);

        var ergebnis = Login7Redaktion.Redigieren(roh);

        Assert.Equal(roh.Length, ergebnis.Length);
        Assert.Equal(-1, ergebnis.AsSpan().IndexOf(sspi));
        Assert.False(Redaktion.Enthaelt(ergebnis, Neu));
        Assert.DoesNotContain("geheim", Encoding.Unicode.GetString(ergebnis), StringComparison.Ordinal);
        var sspiOffset = ergebnis[78] | (ergebnis[79] << 8);
        Assert.All(ergebnis[sspiOffset..(sspiOffset + sspi.Length)], b => Assert.Equal((byte)'x', b));
        var neuOffset = ergebnis[86] | (ergebnis[87] << 8);
        var entschleiert = ergebnis[neuOffset..(neuOffset + (Neu.Length * 2))].Select(b => (byte)(((b ^ 0xA5) << 4) | ((b ^ 0xA5) >> 4))).ToArray();
        Assert.Equal(new string('x', Neu.Length), Encoding.Unicode.GetString(entschleiert));
        var dateiOffset = ergebnis[82] | (ergebnis[83] << 8);
        Assert.Equal(new string('x', Datei.Length), Encoding.Unicode.GetString(ergebnis, dateiOffset, Datei.Length * 2));
    }

    // @spec SPEC-045
    [Fact]
    public void Login7_ersetzt_das_Kennwort_in_der_Verschleierung_durch_ein_x_je_Zeichen()
    {
        var ergebnis = Login7Redaktion.Redigieren(Login7("r", "u", "abc", "app", "s", "d"));
        var offset = ergebnis[44] | (ergebnis[45] << 8);
        var entschleiert = ergebnis[offset..(offset + 6)].Select(b => (byte)(((b ^ 0xA5) << 4) | ((b ^ 0xA5) >> 4))).ToArray();
        Assert.Equal("xxx", Encoding.Unicode.GetString(entschleiert));
    }

    // @spec SPEC-045
    [Fact]
    public void Login7_schliesst_bei_zu_kurzer_Nachricht_und_bei_Feld_ausserhalb_ganz()
    {
        var kurz = Bytes(1, 2, 3, 4, 5, 6, 7, 8);
        Assert.All(Login7Redaktion.Redigieren(kurz), b => Assert.Equal((byte)'x', b));

        var roh = Login7("r", "u", Kennwort, "app", "s", "d");
        roh[46] = 0xFF;
        roh[47] = 0x7F;
        var ergebnis = Login7Redaktion.Redigieren(roh);
        Assert.Equal(roh.Length, ergebnis.Length);
        Assert.All(ergebnis, b => Assert.Equal((byte)'x', b));
    }

    // ---- Ablage -----------------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Fact]
    public void Die_Messdatei_geht_durch_Serialisieren_und_Lesen_und_findet_Geheimnisse_und_Namen()
    {
        var datei = new MessDatei { Szenario = "probe" };
        datei.Herkunft.Add(new("instanz", "Testinstanz"));
        datei.Nachrichten.Add(new Nachricht('c', TdsPaket.Batch, 0x09, 0, Text("SELECT 1")));
        datei.Nachrichten.Add(new Nachricht('s', TdsPaket.Antwort, 0x01, 0, Cat(Bytes(0xAB), Text("Server meinhost"))));
        datei.Nachrichten.Add(new Nachricht('c', TdsPaket.Attention, 0x01, 7, Array.Empty<byte>()));

        var zurueck = MessDatei.Lesen(datei.Serialisieren());

        Assert.Equal("probe", zurueck.Szenario);
        Assert.Equal(3, zurueck.Nachrichten.Count);
        Assert.True(zurueck.Nachrichten[0].ResetConnection);
        Assert.False(zurueck.Nachrichten[1].ResetConnection);
        Assert.Equal(7, zurueck.Nachrichten[2].Nach);
        Assert.Null(zurueck.Treffer(Kennwort, "anderer"));
        Assert.Contains("MeinHost", zurueck.Treffer(Kennwort, "MeinHost")!, StringComparison.Ordinal);

        zurueck.Nachrichten.Add(new Nachricht('c', TdsPaket.Batch, 0, 0, Redaktion.Verschleiert(Text(Kennwort))));
        Assert.Contains("Kennwort", zurueck.Treffer(Kennwort)!, StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Theory]
    [InlineData("nachricht x 01 00 0 00")]
    [InlineData("nachricht c 01 00 0")]
    [InlineData("irgendwas")]
    public void Die_Messdatei_lehnt_falsche_Zeilen_ab(string zeile)
    {
        Assert.Throws<InvalidDataException>(() => MessDatei.Lesen(MessDatei.Kennung + "\n" + zeile + "\n"));
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Protokollierer_setzt_Nachrichten_zusammen_haelt_den_Status_des_ersten_Pakets_und_redigiert_LOGIN7()
    {
        var p = new Protokollierer();
        var login = Login7("rechner", "sa", Kennwort, "app", "srv", "db");
        var anfrage = Text("SELECT 1");
        var erstes = TdsPaket.Schreiben(TdsPaket.Batch, false, 1, anfrage[..6]);
        erstes[1] = 0x08;
        var zweites = TdsPaket.Schreiben(TdsPaket.Batch, true, 2, anfrage[6..]);
        var bytes = Cat(TdsPaket.Schreiben(TdsPaket.Login7, true, 1, login), erstes, zweites, TdsPaket.Schreiben(TdsPaket.Attention, true, 1, ReadOnlySpan<byte>.Empty));

        // Bytes in zwei Stücken, mitten im zweiten Paketkopf getrennt.
        var schnitt = TdsPaket.KopfLaenge + login.Length + erstes.Length + 3;
        p.VomClient(bytes[..schnitt], schnitt);
        p.VomClient(bytes[schnitt..], bytes.Length - schnitt);
        var antwort = Cat(TdsPaket.Schreiben(TdsPaket.Antwort, false, 1, Bytes(1, 2)), TdsPaket.Schreiben(TdsPaket.Antwort, true, 2, Bytes(3)));
        p.VomServer(antwort, antwort.Length);
        var liste = p.Anhalten();

        Assert.Equal(new[] { 'c', 'c', 'c', 's' }, liste.Select(n => n.Richtung).ToArray());
        Assert.False(Redaktion.Enthaelt(liste[0].Daten, Kennwort));
        Assert.Equal(TdsPaket.Batch, liste[1].Typ);
        Assert.True(liste[1].ResetConnection);
        Assert.Equal(anfrage, liste[1].Daten);
        Assert.Equal(TdsPaket.Attention, liste[2].Typ);
        Assert.Equal(new byte[] { 1, 2, 3 }, liste[3].Daten);
        p.VomClient(bytes, bytes.Length);
        Assert.Equal(4, p.Anhalten().Count);
    }

    // ---- Handschlag-Strom und TLS-beendender Proxy ------------------------------------------------------------------

    // @spec SPEC-045
    [Fact]
    public void Der_Handschlagstrom_verpackt_in_PRELOGIN_Pakete_und_reicht_danach_roh_durch()
    {
        var wire = new MemoryStream();
        var strom = new HandschlagStrom(wire);
        var gross = new byte[10000];
        new Random(5).NextBytes(gross);
        strom.Write(gross, 0, gross.Length);

        wire.Position = 0;
        var gelesen = new List<Paket>();
        while (TdsPaket.Lesen(wire) is { } p) gelesen.Add(p);
        Assert.All(gelesen, p => Assert.Equal(TdsPaket.Prelogin, p.Typ));
        Assert.True(gelesen.Count >= 3);
        Assert.All(gelesen.SkipLast(1), p => Assert.True(p.Nutzlast.Length <= 4096 - TdsPaket.KopfLaenge));
        Assert.Equal(gross, gelesen.SelectMany(p => p.Nutzlast).ToArray());

        wire.Position = 0;
        var zurueck = new byte[gross.Length];
        var n = 0;
        while (n < zurueck.Length) n += strom.Read(zurueck, n, Math.Min(777, zurueck.Length - n));
        Assert.Equal(gross, zurueck);

        strom.Roh();
        wire.SetLength(0);
        strom.Write(Bytes(1, 2, 3), 0, 3);
        Assert.Equal(new byte[] { 1, 2, 3 }, wire.ToArray());
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Handschlagstrom_verweigert_den_Wechsel_mit_ungelesenem_Rest_und_andere_Paketarten()
    {
        var wire = new MemoryStream(TdsPaket.Schreiben(TdsPaket.Prelogin, true, 1, Bytes(1, 2, 3, 4)));
        var strom = new HandschlagStrom(wire);
        var eins = new byte[1];
        Assert.Equal(1, strom.Read(eins, 0, 1));
        Assert.Throws<InvalidDataException>(() => strom.Roh());

        var falsch = new HandschlagStrom(new MemoryStream(TdsPaket.Schreiben(TdsPaket.Batch, true, 1, Bytes(1))));
        Assert.Throws<InvalidDataException>(() => falsch.Read(eins, 0, 1));
    }

    private static byte[] PreloginMitVerschluesselung(int option)
    {
        // Eine Option (ENCRYPTION, Token 1) und Abschluss: Tabelle 5 + 1 Bytes, Wert dahinter.
        return Cat(Bytes(0x01, 0, 6, 0, 1, 0xFF), Bytes(option));
    }

    private static Paket? PaketLesen(Stream s) => TdsPaket.Lesen(s);

    /// <summary>Server aus der Hand: PRELOGIN-Antwort mit der gewünschten Verschlüsselung, TLS im Handschlag, LOGIN7 → LOGINACK, Batch → DONE.</summary>
    private static Task ServerAusDerHand(TcpListener lauscher, int verschluesselung, List<byte[]> empfangen)
    {
        return Task.Run(() =>
        {
            using var verbindung = lauscher.AcceptTcpClient();
            var roh = verbindung.GetStream();
            var vorspann = PaketLesen(roh)!;
            empfangen.Add(vorspann.Nutzlast);
            var antwort = TdsPaket.Schreiben(TdsPaket.Antwort, true, 1, PreloginMitVerschluesselung(verschluesselung));
            roh.Write(antwort);
            if (verschluesselung != 1) return;
            var handschlag = new HandschlagStrom(roh);
            using var ssl = new SslStream(handschlag, false);
            using var zertifikat = TlsAufnahme.EigenesZertifikat();
            ssl.AuthenticateAsServer(new SslServerAuthenticationOptions { ServerCertificate = zertifikat, EnabledSslProtocols = SslProtocols.Tls12 });
            handschlag.Roh();
            empfangen.Add(PaketLesen(ssl)!.Nutzlast);
            ssl.Write(TdsPaket.Schreiben(TdsPaket.Antwort, true, 1, Cat(Bytes(0xFD), U16(0), U16(0), U32(0), U32(0))));
            var p = PaketLesen(ssl)!;
            empfangen.Add(new[] { p.Status }.Concat(p.Nutzlast).ToArray());
            ssl.Write(TdsPaket.Schreiben(TdsPaket.Antwort, true, 1, Cat(Bytes(0xFD), U16(0x10), U16(0xC1), U32(1), U32(0))));
            try
            {
                PaketLesen(ssl);
            }
            catch (IOException)
            {
                // Der Proxy beendet die Verbindung mit dem Client.
            }
        });
    }

    // @spec SPEC-045
    [Fact]
    public async Task Der_TlsProxy_zeichnet_Anmeldung_Status_und_Antworten_auf_und_haelt_das_Kennwort_aus_der_Ablage()
    {
        var lauscher = new TcpListener(IPAddress.Loopback, 0);
        lauscher.Start();
        var empfangen = new List<byte[]>();
        var server = ServerAusDerHand(lauscher, 1, empfangen);
        using var aufnahme = new TlsAufnahme("127.0.0.1", ((IPEndPoint)lauscher.LocalEndpoint).Port, "localhost", (_, _, _, _) => true);

        var login = Login7("rechner-42", "sa", Kennwort, "app", "127.0.0.1,1", "db");
        using (var client = new TcpClient())
        {
            client.Connect(IPAddress.Loopback, aufnahme.Port);
            var roh = client.GetStream();
            roh.Write(TdsPaket.Schreiben(TdsPaket.Prelogin, true, 1, PreloginMitVerschluesselung(1)));
            var antwort = PaketLesen(roh)!;
            Assert.Equal(1, Abspieler.VerschluesselungOption(antwort.Nutzlast));
            var handschlag = new HandschlagStrom(roh);
            using var ssl = new SslStream(handschlag, false);
            ssl.AuthenticateAsClient(new SslClientAuthenticationOptions
            {
                TargetHost = "localhost",
                EnabledSslProtocols = SslProtocols.Tls12,
                RemoteCertificateValidationCallback = (_, _, _, _) => true,
            });
            handschlag.Roh();
            ssl.Write(TdsPaket.Schreiben(TdsPaket.Login7, true, 1, login));
            Assert.Equal(0xFD, PaketLesen(ssl)!.Nutzlast[0]);
            var erstes = TdsPaket.Schreiben(TdsPaket.Batch, true, 1, Text("SELECT 1"));
            erstes[1] |= 0x08;
            ssl.Write(erstes);
            Assert.Equal(0xFD, PaketLesen(ssl)!.Nutzlast[0]);
            ssl.Flush();
        }

        Assert.Same(server, await Task.WhenAny(server, Task.Delay(TimeSpan.FromSeconds(20))));
        await server;
        Assert.Null(aufnahme.Problem);
        var liste = aufnahme.Anhalten();
        Assert.Equal(new[] { 'c', 's', 'c', 's', 'c', 's' }, liste.Select(n => n.Richtung).ToArray());
        Assert.Equal(new[] { TdsPaket.Prelogin, TdsPaket.Antwort, TdsPaket.Login7, TdsPaket.Antwort, TdsPaket.Batch, TdsPaket.Antwort }, liste.Select(n => n.Typ).ToArray());
        Assert.True(liste[4].ResetConnection);
        Assert.False(liste[2].ResetConnection);

        var datei = new MessDatei { Szenario = "proxy" };
        datei.Nachrichten.AddRange(liste);
        Assert.Null(datei.Treffer(Kennwort, "rechner-42"));
        // Der Server aus der Hand sah das unredigierte LOGIN7: der Proxy verändert den Verkehr nicht.
        Assert.True(Redaktion.Enthaelt(empfangen[1], Kennwort));
        Assert.Equal(Text("SELECT 1"), empfangen[2][1..]);
        Assert.Equal(0x08, empfangen[2][0] & 0x08);
        lauscher.Stop();
    }

    // @spec SPEC-045
    [Fact]
    public async Task Der_TlsProxy_meldet_ein_Problem_wenn_der_Server_nicht_ENCRYPT_ON_antwortet()
    {
        var lauscher = new TcpListener(IPAddress.Loopback, 0);
        lauscher.Start();
        var empfangen = new List<byte[]>();
        var server = ServerAusDerHand(lauscher, 2, empfangen);
        using var aufnahme = new TlsAufnahme("127.0.0.1", ((IPEndPoint)lauscher.LocalEndpoint).Port, "localhost", (_, _, _, _) => true);
        using (var client = new TcpClient())
        {
            client.Connect(IPAddress.Loopback, aufnahme.Port);
            var roh = client.GetStream();
            roh.Write(TdsPaket.Schreiben(TdsPaket.Prelogin, true, 1, PreloginMitVerschluesselung(1)));
            _ = PaketLesen(roh);
        }

        Assert.Same(server, await Task.WhenAny(server, Task.Delay(TimeSpan.FromSeconds(20))));
        await server;
        var frist = DateTime.UtcNow.AddSeconds(10);
        while (aufnahme.Problem == null && DateTime.UtcNow < frist) Thread.Sleep(20);
        Assert.Contains("ENCRYPTION 2", aufnahme.Problem, StringComparison.Ordinal);
        lauscher.Stop();
    }

    // @spec SPEC-045
    [Fact]
    public async Task Der_TlsProxy_schliesst_weitere_Verbindungen_sofort_und_zaehlt_sie()
    {
        var lauscher = new TcpListener(IPAddress.Loopback, 0);
        lauscher.Start();
        using var aufnahme = new TlsAufnahme("127.0.0.1", ((IPEndPoint)lauscher.LocalEndpoint).Port, "localhost", (_, _, _, _) => true);
        using var erste = new TcpClient();
        erste.Connect(IPAddress.Loopback, aufnahme.Port);
        using var amZiel = await lauscher.AcceptTcpClientAsync();
        Assert.Equal(0, aufnahme.WeitereVerbindungen);

        for (var i = 1; i <= 3; i++)
        {
            using var weitere = new TcpClient();
            weitere.Connect(IPAddress.Loopback, aufnahme.Port);
            var frist = DateTime.UtcNow.AddSeconds(10);
            while (aufnahme.WeitereVerbindungen < i && DateTime.UtcNow < frist) await Task.Delay(20);
            Assert.Equal(i, aufnahme.WeitereVerbindungen);
            Assert.Equal(0, await weitere.GetStream().ReadAsync(new byte[1]));
        }

        lauscher.Stop();
    }

    // ---- Zerlegung --------------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Fact]
    public void Der_Leser_zerlegt_LOGINACK_ENVCHANGE_mit_Werten_FEATUREEXTACK_und_SESSIONSTATE()
    {
        byte[] Env(int typ, string neu, string alt) => Cat(Bytes(0xE3), U16(1 + 1 + (neu.Length * 2) + 1 + (alt.Length * 2)), Bytes(typ, neu.Length), Text(neu), Bytes(alt.Length), Text(alt));
        var loginack = Cat(Bytes(0xAD), U16(1 + 4 + 1 + (2 * 3) + 4), Bytes(1), Bytes(0x04, 0, 0, 0x74), Bytes(3), Text("SQL"), Bytes(16, 0, 0x10, 0xE1));
        var features = Cat(Bytes(0xAE), Merkmal(1, 5, 6), Merkmal(9, 1), Bytes(0xFF));
        var zustand = Cat(U32(4 + 1 + 1 + 1 + 3), U32(7), Bytes(1), Bytes(2, 3, 0xAA, 0xBB, 0xCC));
        var langerZustand = Cat(U32(4 + 1 + 1 + 5 + 300), U32(8), Bytes(0), Bytes(3, 0xFF), U32(300), new byte[300]);
        var done = Cat(Bytes(0xFD), U16(0), U16(0), U32(0), U32(0));
        var antwort = TokenLeser.Lesen(Cat(Env(1, "master", ""), Env(2, "us_english", ""), loginack, features, Bytes(0xE4), zustand, Bytes(0xE4), langerZustand, done));

        Assert.Null(antwort.Problem);
        Assert.Equal(new[] { "ENVCHANGE", "ENVCHANGE", "LOGINACK", "FEATUREEXTACK", "SESSIONSTATE", "SESSIONSTATE", "DONE" }, antwort.Folge);
        Assert.Contains(antwort.Einzelheiten, e => e.StartsWith("ENVCHANGE Typ 1,", StringComparison.Ordinal) && e.EndsWith("Text neu 'master', alt ''", StringComparison.Ordinal));
        Assert.Contains("LOGINACK Schnittstelle 1, TDS 0x74000004, Programm 'SQL', Version 16.0.4321", antwort.Einzelheiten);
        Assert.Contains("FEATUREEXTACK Merkmal 0x01, Daten 0x0506", antwort.Einzelheiten);
        Assert.Contains("FEATUREEXTACK Merkmal 0x09, Daten 0x01", antwort.Einzelheiten);
        Assert.Contains(antwort.Einzelheiten, e => e.Contains("Folge 7, wiederherstellbar 1, Zustaende [2:3=0xaabbcc]", StringComparison.Ordinal));
        Assert.Contains(antwort.Einzelheiten, e => e.Contains("Folge 8, wiederherstellbar 0, Zustaende [3:300=", StringComparison.Ordinal));
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Leser_meldet_ein_abgeschnittenes_FEATUREEXTACK_als_Problem_und_zerlegt_ENVCHANGE_Typ_18()
    {
        var abgeschnitten = TokenLeser.Lesen(Cat(Bytes(0xAE, 0x01), U32(10), Bytes(1, 2)));
        Assert.NotNull(abgeschnitten.Problem);

        var reset = Cat(Bytes(0xE3), U16(3), Bytes(18, 0, 0));
        var antwort = TokenLeser.Lesen(reset);
        Assert.Null(antwort.Problem);
        Assert.Equal("ENVCHANGE Typ 18, Rest 0x0000", antwort.Einzelheiten.Single());
        Assert.Equal("ENVCHANGE Typ 18, neuer Wert leer, alter Wert leer", antwort.Umgebung.Single());
    }

    private static byte[] Rpc(int prozedur, params byte[][] parameter) =>
        Cat(U32(22), U32(18), U16(2), new byte[8], U32(1), Bytes(0xFF, 0xFF), U16(prozedur), U16(0), Cat(parameter));

    private static byte[] Param(string name, int status, byte[] typ, byte[] wert) => Cat(Bytes(name.Length), Text(name), Bytes(status), typ, wert);

    private static byte[] IntParam(string name, int status, int? wert) =>
        Param(name, status, Bytes(0x26, 4), wert is { } w ? Cat(Bytes(4), U32(w)) : Bytes(0));

    private static byte[] NvarcharParam(string name, string wert) =>
        Param(name, 0, Cat(Bytes(0xE7), U16(wert.Length * 2), Bytes(9, 4, 0xD0, 0, 52)), Cat(U16(wert.Length * 2), Text(wert)));

    // @spec SPEC-045
    [Fact]
    public void Die_Auswertung_nennt_Parameter_in_der_Reihenfolge_der_Aufrufliste_mit_Richtung()
    {
        var rpc = Rpc(10, NvarcharParam("", "SET @a = 1"), NvarcharParam("", "@a int OUTPUT, @w int, @b int OUTPUT"),
            IntParam("@a", 1, null), IntParam("@w", 0, 9), IntParam("@b", 1, 5));

        var text = MessAuswertung.Rpc(rpc);

        Assert.Equal("sp_ExecuteSql(#10) [0]- nvarchar 10 Zeichen | [1]- nvarchar 36 Zeichen | [2]@a OUTPUT int NULL | [3]@w int 9 | [4]@b OUTPUT int 5", text);
        Assert.Contains("RESETCONNECTION", MessAuswertung.Anfrage(new Nachricht('c', TdsPaket.Rpc, 0x09, 0, rpc)), StringComparison.Ordinal);
        Assert.DoesNotContain("RESETCONNECTION", MessAuswertung.Anfrage(new Nachricht('c', TdsPaket.Rpc, 0x01, 0, rpc)), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Fact]
    public void Die_Auswertung_bricht_bei_einem_unbekannten_Parametertyp_mit_Hinweis_ab_und_liest_Batch_und_PRELOGIN()
    {
        var rpc = Rpc(13, IntParam("@h", 1, null), Param("@x", 0, Bytes(0x7F), new byte[8]));
        Assert.Equal("sp_PrepExec(#13) [0]@h OUTPUT int NULL | (weitere Parameter nicht zerlegt)", MessAuswertung.Rpc(rpc));

        var batch = Cat(U32(22), U32(18), U16(2), new byte[8], U32(1), Text("SELECT 1"));
        Assert.Equal("SELECT 1", MessAuswertung.BatchText(batch));
        Assert.Equal("1=0x01", MessAuswertung.PreloginOptionen(PreloginMitVerschluesselung(1)));
        Assert.Contains("endet unerwartet", MessAuswertung.Rpc(Rpc(10, Bytes(5, 0x40))), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Fact]
    public void Die_Auswertung_fuehrt_die_Anmeldung_als_Austausch_00_und_die_Anfragen_danach()
    {
        var datei = new MessDatei { Szenario = "probe" };
        datei.Nachrichten.Add(new Nachricht('c', TdsPaket.Prelogin, 1, 0, PreloginMitVerschluesselung(1)));
        datei.Nachrichten.Add(new Nachricht('s', TdsPaket.Antwort, 1, 0, PreloginMitVerschluesselung(1)));
        datei.Nachrichten.Add(new Nachricht('c', TdsPaket.Login7, 1, 0, Login7Redaktion.Redigieren(Login7("r", "u", Kennwort, "app", "s", "db"))));
        datei.Nachrichten.Add(new Nachricht('s', TdsPaket.Antwort, 1, 0, Cat(Bytes(0xE3), U16(3), Bytes(18, 0, 0), Bytes(0xFD), U16(0), U16(0), U32(0), U32(0))));
        datei.Nachrichten.Add(new Nachricht('c', TdsPaket.Batch, 0x09, 0, Cat(U32(22), U32(18), U16(2), new byte[8], U32(1), Text("SELECT 1"))));
        datei.Nachrichten.Add(new Nachricht('s', TdsPaket.Antwort, 1, 0, Cat(Bytes(0xFD), U16(0x10), U16(0xC1), U32(1), U32(0))));

        var zeilen = MessAuswertung.Zeilen(datei).ToDictionary(z => z.Kennung, z => z.Text);

        Assert.Equal("1=0x01", zeilen["probe/00/prelogin-client"]);
        Assert.Contains("Merkmale []", zeilen["probe/00/login7"], StringComparison.Ordinal);
        Assert.Equal("ENVCHANGE DONE", zeilen["probe/00/tokenfolge"]);
        Assert.Contains("ENVCHANGE Typ 18", zeilen["probe/00/einzelheiten"], StringComparison.Ordinal);
        Assert.Equal("Art 1 Status 0x09 RESETCONNECTION: SELECT 1", zeilen["probe/01/anfrage"]);
        Assert.Equal("DONE Status COUNT, Befehl 0x00c1, Zeilen 1", zeilen["probe/01/done"]);
        Assert.False(zeilen.ContainsKey("probe/01/abspielen"));
    }
}
