using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using Xunit;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Tests des Aufnahme- und Vergleichswerkzeugs ohne Server (SPEC-045, TDS-QA-01): Redaktion, Mitschreiber, Dateiform,
/// Zerlegung der Tokens, Abbildung auf die Felder des Vergleichs und das Abspielen gegen einen Server aus der Hand.
/// Sie laufen in der Dockerfile-Stage `test` (`make referenztest-dotnet`, in `make gates`) und belegen das Werkzeug, nicht
/// das Verhalten einer Referenzinstanz oder des Servers dieses Repos. `make spec-abdeckung` liest nur Kotlin-Tests: diese
/// Tests stehen dort nicht in der Matrix; die Zuordnung steht im Kopf hier (`@spec SPEC-045` je Test als Kommentar).
/// </summary>
[Trait("Kategorie", "MitschnittEinheit")]
public class MitschnittEinheitTests
{
    private const string Kennwort = "Geheim-Kennwort-4711";

    // ---- Bausteine --------------------------------------------------------------------------------------------------

    private static byte[] Cat(params byte[][] teile) => teile.SelectMany(t => t).ToArray();

    private static byte[] Bytes(params int[] werte) => werte.Select(w => (byte)w).ToArray();

    private static byte[] U16(int w) => Bytes(w & 0xFF, w >> 8);

    private static byte[] U32(long w) => Bytes((int)(w & 0xFF), (int)((w >> 8) & 0xFF), (int)((w >> 16) & 0xFF), (int)((w >> 24) & 0xFF));

    private static byte[] Text(string t) => Encoding.Unicode.GetBytes(t);

    private static byte[] BVarchar(string t) => Cat(Bytes(t.Length), Text(t));

    private static byte[] UsVarchar(string t) => Cat(U16(t.Length), Text(t));

    private static byte[] ColInt(string name, bool nullbar = false) => Cat(U32(0), U16(nullbar ? 1 : 0), Bytes(0x38), BVarchar(name));

    private static byte[] ColNvarchar(string name) => Cat(U32(0), U16(1), Bytes(0xE7), U16(80), Bytes(9, 4, 0xD0, 0, 52), BVarchar(name));

    private static byte[] ColDecimal(string name, int skala) => Cat(U32(0), U16(1), Bytes(0x6C, 9, 10, skala), BVarchar(name));

    private static byte[] Meta(params byte[][] spalten) => Cat(Bytes(0x81), U16(spalten.Length), Cat(spalten));

    private static byte[] ZeileIntText(int zahl, string? text) =>
        Cat(Bytes(0xD1), U32(zahl), text == null ? U16(0xFFFF) : Cat(U16(text.Length * 2), Text(text)));

    private static byte[] ZeileInt(int zahl) => Cat(Bytes(0xD1), U32(zahl));

    private static byte[] Done(int status, long zeilen, int befehl = 0xC1, int kennung = 0xFD) =>
        Cat(Bytes(kennung), U16(status), U16(befehl), U32(zeilen), U32(0));

    private static byte[] Fehler(int nummer, int klasse, string text, string server = "srv") =>
        Cat(Bytes(0xAA), U16(4 + 1 + 1 + 2 + text.Length * 2 + 1 + server.Length * 2 + 1 + 4), U32(nummer), Bytes(1, klasse), UsVarchar(text), BVarchar(server), BVarchar(""), U32(1));

    private static byte[] Env(int typ, string neu, string alt) =>
        Cat(Bytes(0xE3), U16(1 + 1 + neu.Length * 2 + 1 + alt.Length * 2), Bytes(typ), BVarchar(neu), BVarchar(alt));

    private static byte[] Pakete(byte typ, byte[] nachricht, int groesse = 8000) =>
        TdsPaket.Zerlegen(typ, nachricht, groesse).SelectMany(p => p).ToArray();

    private static byte[] Login7MitKennwort() =>
        Cat(U32(0x100), Redaktion.Verschleiert(Text(Kennwort)), Encoding.ASCII.GetBytes(Convert.ToHexString(SHA256.HashData(Text(Kennwort)))));

    private static byte[] Strom(params byte[][] teile) => Cat(teile);

    private static void Einspeisen(Mitschreiber m, byte[] strom, bool vomClient, int stueck = 1000)
    {
        for (var i = 0; i < strom.Length; i += stueck)
        {
            var teil = strom[i..Math.Min(strom.Length, i + stueck)];
            if (vomClient) m.VomClient(teil, teil.Length);
            else m.VomServer(teil, teil.Length);
        }
    }

    // ---- Redaktion --------------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Fact]
    public void Der_Mitschnitt_enthaelt_weder_Kennwort_noch_Pruefwert_obwohl_der_Rohstrom_beides_traegt()
    {
        var anmeldung = Strom(
            Pakete(TdsPaket.Prelogin, Bytes(0, 0, 0, 0, 0xFF)),
            Bytes(0x16, 3, 3, 0, 4, 1, 2, 3, 4),
            Pakete(TdsPaket.Login7, Login7MitKennwort()));
        var batch = Pakete(TdsPaket.Batch, Text("SELECT 1"));
        var antwort = Pakete(TdsPaket.Antwort, Cat(Meta(ColInt("a")), ZeileIntText(1, null), Done(0x10, 1)));
        var m = new Mitschreiber();
        Einspeisen(m, Strom(anmeldung, batch), true);
        Einspeisen(m, antwort, false);
        var datei = new MitschnittDatei { Szenario = "probe" };
        datei.Ereignisse.AddRange(m.Anhalten());

        var rohstrom = Strom(anmeldung, batch);
        Assert.True(Redaktion.Enthaelt(rohstrom, Kennwort));
        Assert.True(Redaktion.Enthaelt(Encoding.ASCII.GetBytes(Convert.ToHexString(SHA256.HashData(Text(Kennwort)))), Kennwort));
        Assert.Equal(2, datei.Ereignisse.Count);
        Assert.Null(Redaktion.Treffer(datei.Serialisieren(), Kennwort));
    }

    // @spec SPEC-045
    [Theory]
    [InlineData("utf8")]
    [InlineData("utf16")]
    [InlineData("verschleiert")]
    [InlineData("sha256hex")]
    [InlineData("base64")]
    public void Die_Pruefung_findet_das_Kennwort_in_jeder_Form_in_einer_Anfrage(string form)
    {
        var inhalt = form switch
        {
            "utf8" => Encoding.UTF8.GetBytes(Kennwort),
            "utf16" => Text(Kennwort),
            "verschleiert" => Redaktion.Verschleiert(Text(Kennwort)),
            "sha256hex" => Encoding.ASCII.GetBytes(Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(Kennwort)))),
            _ => Encoding.ASCII.GetBytes(Convert.ToBase64String(Text(Kennwort))),
        };
        var datei = new MitschnittDatei { Szenario = "probe" };
        datei.Ereignisse.Add(new Ereignis('A', TdsPaket.Batch, Cat(Bytes(1, 2, 3), inhalt, Bytes(4)), 0));

        Assert.NotNull(Redaktion.Treffer(datei.Serialisieren(), Kennwort));
    }

    // @spec SPEC-045
    [Fact]
    public void Die_Pruefung_weist_ein_leeres_Geheimnis_ab_und_findet_in_kennwortfreier_Ablage_nichts()
    {
        Assert.Throws<ArgumentException>(() => Redaktion.Formen(""));
        var datei = new MitschnittDatei { Szenario = "probe" };
        datei.Ereignisse.Add(new Ereignis('A', TdsPaket.Batch, Text("SELECT Id FROM person"), 0));
        Assert.Null(Redaktion.Treffer(datei.Serialisieren(), Kennwort));
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Servername_im_ERROR_wird_ersetzt_ohne_die_Laenge_zu_aendern()
    {
        var fehler = Fehler(208, 16, "Invalid object name 'x'.", "B429388440C4");
        var ersetzt = Redaktion.NameErsetzen(fehler, "b429388440c4");

        Assert.Equal(fehler.Length, ersetzt.Length);
        Assert.True(Redaktion.Enthaelt(fehler, "b429388440c4") || fehler.AsSpan().IndexOf(Text("B429388440C4")) >= 0);
        Assert.Equal(-1, ersetzt.AsSpan().IndexOf(Text("B429388440C4")));
        Assert.Equal("Fehler Nummer 208, Class 16, State 1, Meldung 'Invalid object name 'x'.', Server vorhanden, Prozedur '', Zeile 1", TokenLeser.Lesen(ersetzt).Fehler.Single());
        Assert.Same(fehler, Redaktion.NameErsetzen(fehler, ""));
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Leser_ueberspringt_SESSIONSTATE_und_nennt_ihn_in_der_Folge()
    {
        var sessionstate = Cat(Bytes(0xE4), U32(7), Bytes(1, 2, 3, 4, 5, 6, 7));
        var a = TokenLeser.Lesen(Cat(sessionstate, Done(0x02, 0)));

        Assert.Null(a.Problem);
        Assert.Equal(new[] { "SESSIONSTATE", "DONE" }, a.Folge);
    }

    // ---- Mitschreiber und Dateiform --------------------------------------------------------------------------------

    // @spec SPEC-045
    [Fact]
    public void Der_Mitschreiber_setzt_Pakete_zusammen_auch_wenn_der_Strom_Byte_fuer_Byte_ankommt()
    {
        var m = new Mitschreiber();
        var anmeldung = Strom(Pakete(TdsPaket.Prelogin, Bytes(0xFF)), Pakete(TdsPaket.Login7, Login7MitKennwort()));
        var langeAnfrage = Pakete(TdsPaket.Batch, Text(new string('x', 3000)), 1000);
        var antwort = Pakete(TdsPaket.Antwort, Cat(Meta(ColInt("a")), ZeileIntText(5, "ab"), Done(0x10, 1)), 40);
        Einspeisen(m, Strom(anmeldung, langeAnfrage), true, 1);
        Einspeisen(m, antwort, false, 1);

        var ereignisse = m.Anhalten();
        Assert.Equal(new[] { 'A', 'R' }, ereignisse.Select(e => e.Art).ToArray());
        Assert.Equal(Text(new string('x', 3000)), ereignisse[0].Daten);
    }

    // @spec SPEC-045
    [Fact]
    public void Eine_vollstaendig_verschluesselte_Sitzung_ergibt_keine_Ereignisse()
    {
        var m = new Mitschreiber();
        Einspeisen(m, Strom(Pakete(TdsPaket.Prelogin, Bytes(0xFF)), Bytes(0x16, 3, 3, 0, 3, 1, 2, 3), Bytes(0x17, 3, 3, 0, 3, 9, 9, 9)), true);
        Einspeisen(m, Bytes(0x17, 3, 3, 0, 3, 7, 7, 7), false);

        Assert.False(m.Klartext);
        Assert.Empty(m.Anhalten());
    }

    // @spec SPEC-045
    [Fact]
    public void Ein_ATTENTION_haelt_die_Zahl_der_Antwortpakete_seit_der_Anfrage_fest()
    {
        var m = new Mitschreiber();
        Einspeisen(m, Strom(Pakete(TdsPaket.Prelogin, Bytes(0xFF)), Pakete(TdsPaket.Batch, Text("SELECT Id FROM laufend"))), true);
        Einspeisen(m, Strom(TdsPaket.Schreiben(TdsPaket.Antwort, false, 1, Bytes(0xD1)), TdsPaket.Schreiben(TdsPaket.Antwort, false, 2, Bytes(0xD1))), false);
        Einspeisen(m, TdsPaket.Schreiben(TdsPaket.Attention, true, 1, ReadOnlySpan<byte>.Empty), true);
        Einspeisen(m, TdsPaket.Schreiben(TdsPaket.Antwort, true, 3, Done(0x20, 0)), false);

        var datei = new MitschnittDatei { Szenario = "probe" };
        datei.Ereignisse.AddRange(m.Anhalten());
        var austausch = Assert.Single(datei.Austausche());
        Assert.Equal(new[] { 2 }, austausch.AttentionNach);
        Assert.Single(austausch.Antworten);
    }

    // @spec SPEC-045
    [Fact]
    public void Die_Dateiform_ist_umkehrbar_und_weist_eine_Anmeldung_als_Anfrage_ab()
    {
        var datei = new MitschnittDatei { Szenario = "probe" };
        datei.Herkunft.Add(new("instanz", "Probe 1"));
        datei.Ereignisse.Add(new Ereignis('A', TdsPaket.Rpc, Bytes(1, 2, 3), 0));
        datei.Ereignisse.Add(new Ereignis('S', TdsPaket.Attention, Array.Empty<byte>(), 4));
        datei.Ereignisse.Add(new Ereignis('R', TdsPaket.Antwort, Bytes(9, 8), 0));

        var zurueck = MitschnittDatei.Lesen(datei.Serialisieren());
        Assert.Equal(datei.Serialisieren(), zurueck.Serialisieren());
        Assert.Equal("Probe 1", zurueck.Herkunft.Single().Value);

        var mitLogin = datei.Serialisieren() + "anfrage 10 00\n";
        Assert.Throws<InvalidDataException>(() => MitschnittDatei.Lesen(mitLogin));
        Assert.Throws<InvalidDataException>(() => MitschnittDatei.Lesen("etwas anderes\n"));
        Assert.Throws<InvalidDataException>(() => MitschnittDatei.Lesen(datei.Serialisieren() + "unbekannt x\n"));
    }

    // ---- Zerlegung der Tokens ---------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Fact]
    public void Der_Leser_zerlegt_Metadaten_Zeilen_Done_Fehler_und_Umgebung_in_die_Felder()
    {
        var strom = Cat(
            Env(1, "tdswire_live", "master"),
            Meta(ColInt("Id"), ColNvarchar("Name"), ColDecimal("Preis", 2)),
            Cat(Bytes(0xD1), U32(1), U16(4), Text("Al"), Bytes(5, 1, 0x2C, 0x01, 0, 0)),
            Cat(Bytes(0xD2), Bytes(0b010), U32(2), Bytes(5, 1, 0x0F, 0, 0, 0)),
            Done(0x10, 2),
            Fehler(208, 16, "Invalid object name 'x'."),
            Done(0x02, 0));

        var a = TokenLeser.Lesen(strom);

        Assert.Null(a.Problem);
        Assert.Equal(new[] { "ENVCHANGE", "COLMETADATA", "ROW+", "NBCROW+", "DONE", "ERROR", "DONE" }, a.Folge);
        Assert.Equal("[Id:int(laenge=4,genauigkeit=0,skala=0) nullbar=nein; Name:nvarchar(laenge=80,genauigkeit=0,skala=0) nullbar=ja; Preis:numericn(laenge=9,genauigkeit=10,skala=2) nullbar=ja]", a.Metadaten.Single());
        Assert.Equal(new[] { "(1,'Al',3.00)", "(2,NULL,0.15)" }, a.ZeilenWerte);
        Assert.Equal("DONE Status COUNT, Befehl 0x00c1, Zeilen 2", a.Fertig[0]);
        Assert.Equal("DONE Status ERROR, Befehl 0x00c1, Zeilen -", a.Fertig[1]);
        Assert.Equal("Fehler Nummer 208, Class 16, State 1, Meldung 'Invalid object name 'x'.', Server vorhanden, Prozedur '', Zeile 1", a.Fehler.Single());
        Assert.Equal("ENVCHANGE Typ 1, neuer Wert vorhanden, alter Wert vorhanden", a.Umgebung.Single());
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Leser_meldet_unbekannten_Token_und_abgeschnittenen_Strom_als_Problem_statt_zu_raten()
    {
        var unbekannt = TokenLeser.Lesen(Cat(Done(0x00, 0), Bytes(0x77, 1, 2)));
        Assert.Contains("Token 0x77", unbekannt.Problem);
        Assert.Contains("ZERLEGUNG-ABGEBROCHEN", unbekannt.Folge);

        var abgeschnitten = TokenLeser.Lesen(Cat(Meta(ColInt("a")), Bytes(0xD1, 1, 0)));
        Assert.Contains("Strom endet unerwartet", abgeschnitten.Problem);

        var typ = TokenLeser.Lesen(Cat(Bytes(0x81), U16(1), U16(0), U32(0), U16(0), Bytes(0xF0)));
        Assert.Contains("Typ 0xf0 ist nicht zerlegbar", typ.Problem);
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Leser_liest_COLMETADATA_mit_und_ohne_CekTable_gleich()
    {
        var ohne = Cat(Meta(ColInt("Id")), ZeileInt(7), Done(0x10, 1));
        var mit = Cat(Bytes(0x81), U16(1), U16(0), ColInt("Id"), ZeileInt(7), Done(0x10, 1));

        Assert.Null(TokenLeser.Lesen(ohne).Problem);
        Assert.Null(TokenLeser.Lesen(mit).Problem);
        Assert.Equal(TokenLeser.Lesen(ohne).Metadaten, TokenLeser.Lesen(mit).Metadaten);
        Assert.Equal(new[] { "(7)" }, TokenLeser.Lesen(mit).ZeilenWerte);
    }

    // @spec SPEC-045
    [Fact]
    public void Der_Leser_nimmt_RETURNSTATUS_und_RETURNVALUE_auf_und_normiert_das_Handle()
    {
        var wert = Cat(Bytes(0xAC), U16(1), BVarchar("@handle"), Bytes(1), U32(0), U16(1), Bytes(0x26, 4), Bytes(4), U32(77));
        var a = TokenLeser.Lesen(Cat(Bytes(0x79), U32(0), wert, Done(0x00, 0, 0xC1, 0xFE)));

        Assert.Equal(new[] { "RETURNSTATUS", "RETURNVALUE", "DONEPROC" }, a.Folge);
        Assert.Equal("RETURNSTATUS 0", a.Rueckgabe[0]);
        Assert.Equal("RETURNVALUE Ordinal 1, Name '@handle', Status 1, Typ intn, Wert <vorhanden>", a.Rueckgabe[1]);

        var ohneName = Cat(Bytes(0xAC), U16(0), BVarchar(""), Bytes(1), U32(0), U16(1), Bytes(0x26, 4), Bytes(4), U32(1));
        Assert.Equal("RETURNVALUE Ordinal 0, Name '', Status 1, Typ intn, Wert <vorhanden>", TokenLeser.Lesen(ohneName).Rueckgabe.Single());
        var benannt = Cat(Bytes(0xAC), U16(2), BVarchar("@id"), Bytes(1), U32(0), U16(1), Bytes(0x26, 4), Bytes(4), U32(7));
        Assert.Equal("RETURNVALUE Ordinal 2, Name '@id', Status 1, Typ intn, Wert 7", TokenLeser.Lesen(benannt).Rueckgabe.Single());
    }

    // ---- Abbildung auf die Felder -----------------------------------------------------------------------------------

    private static byte[] Grundantwort(int wert = 1, int status = 0x10, int skala = 2, int befehl = 0xC1) =>
        Cat(Meta(ColInt("Id"), ColDecimal("Preis", skala)), Cat(Bytes(0xD1), U32(wert), Bytes(5, 1, 0x2C, 0x01, 0, 0)), Done(status, 1, befehl));

    private static IReadOnlyDictionary<string, string> Felder(byte[] antwort, int attention = 0) =>
        Ergebnis.Zeilen("s", 1, new[] { antwort }, attention).ToDictionary(z => z.Kennung, z => z.Text);

    // @spec SPEC-045
    [Theory]
    [InlineData("wert", "s/01/zeilen")]
    [InlineData("status", "s/01/done")]
    [InlineData("skala", "s/01/colmetadata")]
    [InlineData("befehl", "s/01/done")]
    public void Eine_Abweichung_in_einem_Feld_aendert_nur_dieses_Feld(string aenderung, string erwartetesFeld)
    {
        var basis = Felder(Grundantwort());
        var geaendert = Felder(aenderung switch
        {
            "wert" => Grundantwort(wert: 2),
            "status" => Grundantwort(status: 0x11),
            "skala" => Grundantwort(skala: 3),
            _ => Grundantwort(befehl: 0xC2),
        });

        var unterschiede = basis.Keys.Where(k => basis[k] != geaendert[k]).ToList();
        Assert.Contains(erwartetesFeld, unterschiede);
        if (aenderung != "skala") Assert.Equal(new[] { erwartetesFeld }, unterschiede);
    }

    // @spec SPEC-045
    [Fact]
    public void Gleiche_Antworten_ergeben_gleiche_Zeilen_und_ein_Abspielproblem_steht_im_eigenen_Feld()
    {
        Assert.Equal(Felder(Grundantwort()), Felder(Grundantwort()));
        var mitProblem = Ergebnis.Zeilen("s", 1, new[] { Grundantwort() }, 0, "Zeitueberschreitung").ToDictionary(z => z.Kennung, z => z.Text);
        Assert.Equal("Zeitueberschreitung", mitProblem["s/01/abspielen"]);
        Assert.Equal("-", Felder(Grundantwort())["s/01/abspielen"]);
    }

    // @spec SPEC-045
    [Fact]
    public void Bei_ATTENTION_stehen_die_Zeilen_nur_als_vorhanden_und_die_Quittung_wird_gezaehlt()
    {
        var abgebrochen = Cat(Meta(ColInt("a")), ZeileInt(1), Done(0x20, 0));
        var f = Felder(abgebrochen, attention: 2);

        Assert.Equal("vorhanden=ja", f["s/01/zeilen"]);
        Assert.Equal("2", f["s/01/attention-gesendet"]);
        Assert.Equal("1", f["s/01/attention-quittiert"]);
    }

    // ---- Abspielen gegen einen Server aus der Hand ------------------------------------------------------------------

    private static Paket Lies(Stream s) => TdsPaket.Lesen(s) ?? throw new EndOfStreamException();

    private static byte[] NachrichtLesen(Stream s, out byte typ)
    {
        using var ms = new MemoryStream();
        typ = 0;
        while (true)
        {
            var p = Lies(s);
            if (ms.Length == 0) typ = p.Typ;
            ms.Write(p.Nutzlast);
            if (p.Ende) return ms.ToArray();
        }
    }

    private static void Senden(Stream s, byte[] nachricht)
    {
        foreach (var p in TdsPaket.Zerlegen(TdsPaket.Antwort, nachricht, 8000)) s.Write(p);
        s.Flush();
    }

    private static byte[] PreloginAntwort(int verschluesselung) => Bytes(0x01, 0, 6, 0, 1, 0xFF, verschluesselung);

    private static byte[] LoginAntwort() => Cat(Bytes(0xAD), U16(3), Bytes(1, 2, 3), Done(0x00, 0));

    /// <summary>Ein Server aus der Hand: PRELOGIN, LOGIN7, dann eine Antwort je Anfrage; `laufend` streamt bis zum ATTENTION.</summary>
    private static Task Handserver(TcpListener l, int verschluesselung, Func<byte[], Stream, bool> antworten) => Task.Run(() =>
    {
        using var k = l.AcceptTcpClient();
        var s = k.GetStream();
        NachrichtLesen(s, out _);
        Senden(s, PreloginAntwort(verschluesselung));
        if (verschluesselung != 2) return;
        NachrichtLesen(s, out _);
        Senden(s, LoginAntwort());
        while (true)
        {
            byte[] anfrage;
            try
            {
                anfrage = NachrichtLesen(s, out _);
            }
            catch (IOException)
            {
                return;
            }

            if (!antworten(anfrage, s)) return;
        }
    });

    private static void Anmelden(Stream s)
    {
        foreach (var p in TdsPaket.Zerlegen(TdsPaket.Prelogin, Bytes(0xFF), 4096)) s.Write(p);
        NachrichtLesen(s, out _);
        foreach (var p in TdsPaket.Zerlegen(TdsPaket.Login7, Login7MitKennwort(), 4096)) s.Write(p);
        NachrichtLesen(s, out _);
    }

    // @spec SPEC-045
    [Fact]
    public async Task Der_Abspieler_sendet_die_Anfragen_und_liefert_die_Antworten_des_Servers()
    {
        var l = new TcpListener(IPAddress.Loopback, 0);
        l.Start();
        var empfangen = new List<string>();
        var server = Handserver(l, 2, (anfrage, s) =>
        {
            empfangen.Add(Encoding.Unicode.GetString(anfrage));
            Senden(s, Cat(Meta(ColInt("a"), ColNvarchar("t")), ZeileIntText(1, "x"), Done(0x10, 1)));
            return true;
        });
        using var abspieler = new Abspieler();
        var austausch = new Austausch(TdsPaket.Batch, Text("SELECT 1"), Array.Empty<int>(), Array.Empty<byte[]>());
        var lauf = abspieler.Abspielen(new[] { austausch }, "127.0.0.1", ((IPEndPoint)l.LocalEndpoint).Port);

        using var client = new TcpClient();
        client.Connect(IPAddress.Loopback, abspieler.Port);
        Anmelden(client.GetStream());
        var ergebnis = Assert.Single(await lauf);
        client.Close();
        l.Stop();

        Assert.Null(ergebnis.Problem);
        Assert.Equal(new[] { "SELECT 1" }, empfangen);
        Assert.Equal(new[] { "COLMETADATA", "ROW+", "DONE" }, TokenLeser.Lesen(ergebnis.Antworten.Single()).Folge);
        await server;
    }

    // @spec SPEC-045
    [Fact]
    public async Task Der_Abspieler_sendet_ATTENTION_nach_der_aufgenommenen_Paketzahl_und_wartet_auf_die_Quittung()
    {
        var l = new TcpListener(IPAddress.Loopback, 0);
        l.Start();
        var server = Handserver(l, 2, (anfrage, s) =>
        {
            s.Write(TdsPaket.Schreiben(TdsPaket.Antwort, false, 1, Cat(Meta(ColInt("a")), ZeileInt(1))));
            s.Write(TdsPaket.Schreiben(TdsPaket.Antwort, false, 2, ZeileInt(2)));
            s.Flush();
            var attention = Lies(s);
            Assert.Equal(TdsPaket.Attention, attention.Typ);
            Senden(s, Done(0x20, 0));
            return true;
        });
        using var abspieler = new Abspieler();
        var austausch = new Austausch(TdsPaket.Batch, Text("SELECT Id FROM laufend"), new[] { 2 }, Array.Empty<byte[]>());
        var lauf = abspieler.Abspielen(new[] { austausch }, "127.0.0.1", ((IPEndPoint)l.LocalEndpoint).Port);

        using var client = new TcpClient();
        client.Connect(IPAddress.Loopback, abspieler.Port);
        Anmelden(client.GetStream());
        var ergebnis = Assert.Single(await lauf);
        client.Close();
        l.Stop();

        Assert.Null(ergebnis.Problem);
        Assert.Equal(1, ergebnis.Attention);
        Assert.True(TokenLeser.Lesen(ergebnis.Antworten[^1]).AttnQuittung);
        await server;
    }

    // @spec SPEC-045
    [Fact]
    public async Task Der_Abspieler_bricht_ab_wenn_der_Server_Verschluesselung_verlangt()
    {
        var l = new TcpListener(IPAddress.Loopback, 0);
        l.Start();
        var server = Handserver(l, 3, (_, _) => true);
        using var abspieler = new Abspieler();
        var lauf = abspieler.Abspielen(new[] { new Austausch(TdsPaket.Batch, Text("SELECT 1"), Array.Empty<int>(), Array.Empty<byte[]>()) }, "127.0.0.1", ((IPEndPoint)l.LocalEndpoint).Port);

        using var client = new TcpClient();
        client.Connect(IPAddress.Loopback, abspieler.Port);
        var s = client.GetStream();
        foreach (var p in TdsPaket.Zerlegen(TdsPaket.Prelogin, Bytes(0xFF), 4096)) s.Write(p);

        var ausnahme = await Assert.ThrowsAsync<InvalidOperationException>(() => lauf);
        Assert.Contains("ENCRYPTION 3", ausnahme.Message);
        l.Stop();
        await server;
    }

    // @spec SPEC-045
    [Fact]
    public async Task Der_Abspieler_meldet_ein_Problem_wenn_der_Server_nach_der_Anfrage_schliesst()
    {
        var l = new TcpListener(IPAddress.Loopback, 0);
        l.Start();
        var server = Handserver(l, 2, (_, _) => false);
        using var abspieler = new Abspieler();
        var anfragen = new[]
        {
            new Austausch(TdsPaket.Batch, Text("SELECT 1"), Array.Empty<int>(), Array.Empty<byte[]>()),
            new Austausch(TdsPaket.Batch, Text("SELECT 2"), Array.Empty<int>(), Array.Empty<byte[]>()),
        };
        var lauf = abspieler.Abspielen(anfragen, "127.0.0.1", ((IPEndPoint)l.LocalEndpoint).Port);

        using var client = new TcpClient();
        client.Connect(IPAddress.Loopback, abspieler.Port);
        Anmelden(client.GetStream());
        var ergebnisse = await lauf;
        l.Stop();

        Assert.Equal("Verbindung vom Server beendet", ergebnisse[0].Problem);
        Assert.StartsWith("nicht abgespielt", ergebnisse[1].Problem);
        await server;
    }
}
