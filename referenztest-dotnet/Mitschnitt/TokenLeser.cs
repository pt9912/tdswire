using System.Globalization;
using System.Numerics;
using System.Text;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>Das zerlegte Ergebnis einer Antwort, je Feld des Vergleichs eine Liste von Textzeilen (SPEC-045).</summary>
internal sealed class Antwort
{
    public List<string> Folge { get; } = new();

    public List<string> Metadaten { get; } = new();

    public List<string> ZeilenWerte { get; } = new();

    public int ZeilenAnzahl { get; set; }

    public List<string> Fertig { get; } = new();

    public List<string> Fehler { get; } = new();

    public List<string> Info { get; } = new();

    public List<string> Umgebung { get; } = new();

    public List<string> Rueckgabe { get; } = new();

    public bool AttnQuittung { get; set; }

    public string? Problem { get; set; }

    /// <summary>Die Stelle, an der die Zerlegung endete (Ende des Stroms oder Problem).</summary>
    public int Offset { get; set; }
}

/// <summary>
/// Zerlegt einen Tokenstrom einer Antwort (MS-TDS 2.2.7, TDS 7.4) in die Felder des Vergleichs. Er rechnet nur so weit, wie
/// der Vergleich Werte braucht: Werte erscheinen als Text oder Hex, Längen bestimmen das Ende eines Werts. Ein Token oder ein
/// Typ außerhalb der Liste unten setzt <see cref="Antwort.Problem"/> und beendet die Zerlegung; der Vergleich zeigt das Problem
/// als Abweichung. Grenze: Tokens `TEXT`/`NTEXT`/`IMAGE`, UDT, DATACLASSIFICATION und FEATUREEXTACK sind nicht zerlegt.
/// Vom Server vergebene Werte stehen nur als vorhanden oder leer: Name des Servers in ERROR und INFO, Werte von ENVCHANGE,
/// Rückgabewerte des Parameters `@handle` und eines Rückgabewerts ohne Namen (das Handle von `sp_prepexec`, Setzung).
/// </summary>
internal sealed class TokenLeser
{
    private static readonly Dictionary<byte, string> Typnamen = new()
    {
        [0x1F] = "null", [0x30] = "tinyint", [0x32] = "bit", [0x34] = "smallint", [0x38] = "int", [0x3A] = "smalldatetime",
        [0x3B] = "real", [0x3C] = "money", [0x3D] = "datetime", [0x3E] = "float", [0x7A] = "smallmoney", [0x7F] = "bigint",
        [0x24] = "uniqueidentifier", [0x26] = "intn", [0x68] = "bitn", [0x6D] = "floatn", [0x6E] = "moneyn", [0x6F] = "datetimen",
        [0x37] = "decimal(alt)", [0x3F] = "numeric(alt)", [0x6A] = "decimaln", [0x6C] = "numericn", [0x28] = "date",
        [0x29] = "time", [0x2A] = "datetime2", [0x2B] = "datetimeoffset", [0xE7] = "nvarchar", [0xEF] = "nchar",
        [0xA7] = "varchar", [0xAF] = "char", [0xA5] = "varbinary", [0xAD] = "binary", [0x62] = "sql_variant", [0xF1] = "xml",
    };

    private static readonly Dictionary<byte, int> FesteLaengen = new()
    {
        [0x1F] = 0, [0x30] = 1, [0x32] = 1, [0x34] = 2, [0x38] = 4, [0x3A] = 4, [0x3B] = 4, [0x3C] = 8, [0x3D] = 8,
        [0x3E] = 8, [0x7A] = 4, [0x7F] = 8,
    };

    private sealed record Spalte(string Name, byte Typ, int Laenge, int Genauigkeit, int Skala, bool Nullbar, bool Plp)
    {
        public string Beschreibung =>
            $"{Name}:{Typnamen.GetValueOrDefault(Typ, "typ0x" + Typ.ToString("x2", CultureInfo.InvariantCulture))}(laenge={Laenge},genauigkeit={Genauigkeit},skala={Skala}) nullbar={(Nullbar ? "ja" : "nein")}";
    }

    private sealed class Unzerlegbar : Exception
    {
        public Unzerlegbar(string grund) : base(grund)
        {
        }
    }

    private readonly byte[] _b;
    private readonly bool _cek;
    private int _p;
    private List<Spalte> _spalten = new();

    private TokenLeser(byte[] daten, bool cek)
    {
        _b = daten;
        _cek = cek;
    }

    /// <summary>
    /// Zerlegt die Antwort einer Nachricht. Ob COLMETADATA hinter der Zahl der Spalten eine CekTable (2 Bytes, Anzahl der
    /// Schlüsseleinträge) trägt, hängt von der Verhandlung der Anmeldung ab, die der Mitschnitt nicht enthält: der Leser versucht
    /// erst mit, dann ohne und nimmt den Versuch ohne Problem; scheitern beide, gilt der, der weiter kam.
    /// </summary>
    public static Antwort Lesen(byte[] daten)
    {
        var mit = Versuch(daten, true);
        if (mit.Problem == null) return mit;
        var ohne = Versuch(daten, false);
        return ohne.Problem == null || ohne.Offset > mit.Offset ? ohne : mit;
    }

    private static Antwort Versuch(byte[] daten, bool cek)
    {
        var leser = new TokenLeser(daten, cek);
        var antwort = new Antwort();
        try
        {
            leser.Alle(antwort);
        }
        catch (Unzerlegbar u)
        {
            antwort.Problem = $"{u.Message} bei Offset {leser._p}";
            antwort.Folge.Add("ZERLEGUNG-ABGEBROCHEN");
        }
        catch (Exception e) when (e is IndexOutOfRangeException or ArgumentException or OverflowException)
        {
            antwort.Problem = $"Strom endet unerwartet bei Offset {leser._p}";
            antwort.Folge.Add("ZERLEGUNG-ABGEBROCHEN");
        }

        antwort.Offset = leser._p;
        return antwort;
    }

    private int U8() => _b[_p++];

    private int U16() => U8() | (U8() << 8);

    private long U32() => (uint)(U16() | (U16() << 16));

    private long U64() => (long)(U32() | (U32() << 32));

    private byte[] Nimm(long n)
    {
        if (n < 0 || _p + n > _b.Length) throw new IndexOutOfRangeException();
        var teil = _b[_p..(_p + (int)n)];
        _p += (int)n;
        return teil;
    }

    private string Text16(int zeichen) => Encoding.Unicode.GetString(Nimm(zeichen * 2L));

    private string BVarchar() => Text16(U8());

    private string UsVarchar() => Text16(U16());

    private static string Sauber(string text) =>
        text.Replace("\\", "\\\\", StringComparison.Ordinal).Replace("\t", "\\t", StringComparison.Ordinal)
            .Replace("\r", "\\r", StringComparison.Ordinal).Replace("\n", "\\n", StringComparison.Ordinal);

    private void Alle(Antwort a)
    {
        string? letzterZeilenName = null;
        while (_p < _b.Length)
        {
            var kennung = (byte)U8();
            string name;
            switch (kennung)
            {
                case 0x81:
                    name = "COLMETADATA";
                    ColMetadata(a);
                    break;
                case 0xD1:
                    name = "ROW";
                    Zeile(a, false);
                    break;
                case 0xD2:
                    name = "NBCROW";
                    Zeile(a, true);
                    break;
                case 0xAA:
                    name = "ERROR";
                    Meldung(a.Fehler, true);
                    break;
                case 0xAB:
                    name = "INFO";
                    Meldung(a.Info, false);
                    break;
                case 0xE3:
                    name = "ENVCHANGE";
                    EnvChange(a);
                    break;
                case 0xFD:
                case 0xFE:
                case 0xFF:
                    name = kennung == 0xFD ? "DONE" : kennung == 0xFE ? "DONEPROC" : "DONEINPROC";
                    Done(a, name);
                    break;
                case 0x79:
                    name = "RETURNSTATUS";
                    a.Rueckgabe.Add("RETURNSTATUS " + (int)U32());
                    break;
                case 0xAC:
                    name = "RETURNVALUE";
                    ReturnValue(a);
                    break;
                case 0xA9:
                    name = "ORDER";
                    Nimm(U16());
                    break;
                case 0xA4:
                    name = "TABNAME";
                    Nimm(U16());
                    break;
                case 0xA5:
                    name = "COLINFO";
                    Nimm(U16());
                    break;
                case 0xAD:
                    name = "LOGINACK";
                    Nimm(U16());
                    break;
                case 0xED:
                    name = "SSPI";
                    Nimm(U16());
                    break;
                default:
                    throw new Unzerlegbar($"Token 0x{kennung:x2} ist nicht zerlegbar");
            }

            if (name is "ROW" or "NBCROW")
            {
                if (letzterZeilenName == name) continue;
                a.Folge.Add(name + "+");
                letzterZeilenName = name;
            }
            else
            {
                a.Folge.Add(name);
                letzterZeilenName = null;
            }
        }
    }

    private Spalte TypInfo(string name, bool nullbar)
    {
        var t = (byte)U8();
        if (FesteLaengen.TryGetValue(t, out var fest)) return new Spalte(name, t, fest, 0, 0, nullbar, false);
        switch (t)
        {
            case 0x24 or 0x26 or 0x68 or 0x6D or 0x6E or 0x6F:
                return new Spalte(name, t, U8(), 0, 0, nullbar, false);
            case 0x37 or 0x3F or 0x6A or 0x6C:
                {
                    var laenge = U8();
                    var genauigkeit = U8();
                    return new Spalte(name, t, laenge, genauigkeit, U8(), nullbar, false);
                }

            case 0x28:
                return new Spalte(name, t, 3, 0, 0, nullbar, false);
            case 0x29 or 0x2A or 0x2B:
                return new Spalte(name, t, 0, 0, U8(), nullbar, false);
            case 0xE7 or 0xEF or 0xA7 or 0xAF:
                {
                    var laenge = U16();
                    Nimm(5);
                    return new Spalte(name, t, laenge, 0, 0, nullbar, laenge == 0xFFFF);
                }

            case 0xA5 or 0xAD:
                {
                    var laenge = U16();
                    return new Spalte(name, t, laenge, 0, 0, nullbar, laenge == 0xFFFF);
                }

            case 0x62:
                return new Spalte(name, t, (int)U32(), 0, 0, nullbar, false);
            case 0xF1:
                {
                    if (U8() == 1)
                    {
                        BVarchar();
                        BVarchar();
                        UsVarchar();
                    }

                    return new Spalte(name, t, 0, 0, 0, nullbar, true);
                }

            default:
                throw new Unzerlegbar($"Typ 0x{t:x2} der Spalte {name} ist nicht zerlegbar");
        }
    }

    private void ColMetadata(Antwort a)
    {
        var anzahl = U16();
        if (_cek && U16() != 0) throw new Unzerlegbar("CekTable mit Eintraegen ist nicht zerlegbar");
        _spalten = new List<Spalte>();
        if (anzahl == 0xFFFF)
        {
            a.Metadaten.Add("[]");
            return;
        }

        for (var i = 0; i < anzahl; i++)
        {
            U32();
            var flags = U16();
            var typ = _b[_p];
            if (typ is 0x22 or 0x23 or 0x63 or 0xF0) throw new Unzerlegbar($"Typ 0x{typ:x2} ist nicht zerlegbar");
            var info = TypInfo("", (flags & 0x01) != 0);
            var name = BVarchar();
            _spalten.Add(info with { Name = name });
        }

        a.Metadaten.Add("[" + string.Join("; ", _spalten.Select(s => s.Beschreibung)) + "]");
    }

    private void Zeile(Antwort a, bool komprimiert)
    {
        var nullen = new bool[_spalten.Count];
        if (komprimiert)
        {
            var karte = Nimm((_spalten.Count + 7) / 8);
            for (var i = 0; i < nullen.Length; i++) nullen[i] = (karte[i / 8] & (1 << (i % 8))) != 0;
        }

        var werte = new List<string>();
        for (var i = 0; i < _spalten.Count; i++) werte.Add(nullen[i] ? "NULL" : Wert(_spalten[i]));
        a.ZeilenAnzahl++;
        a.ZeilenWerte.Add("(" + string.Join(",", werte) + ")");
    }

    private string Wert(Spalte s)
    {
        byte[]? daten;
        if (s.Plp)
        {
            var gesamt = U64();
            if (gesamt == -1) return "NULL";
            using var ms = new MemoryStream();
            while (true)
            {
                var teil = U32();
                if (teil == 0) break;
                ms.Write(Nimm(teil));
            }

            daten = ms.ToArray();
        }
        else if (FesteLaengen.TryGetValue(s.Typ, out var fest))
        {
            daten = Nimm(fest);
        }
        else if (s.Typ is 0xE7 or 0xEF or 0xA7 or 0xAF or 0xA5 or 0xAD)
        {
            var laenge = U16();
            if (laenge == 0xFFFF) return "NULL";
            daten = Nimm(laenge);
        }
        else if (s.Typ == 0x62)
        {
            var laenge = U32();
            if (laenge == 0) return "NULL";
            daten = Nimm(laenge);
        }
        else
        {
            var laenge = U8();
            if (laenge == 0) return "NULL";
            daten = Nimm(laenge);
        }

        return Darstellung(s, daten);
    }

    private static string Hex(byte[] daten) => "0x" + Convert.ToHexString(daten).ToLowerInvariant();

    private static string Ganzzahl(byte[] d)
    {
        BigInteger v = d.Length == 1 ? d[0] : new BigInteger(d, isUnsigned: false);
        return v.ToString(CultureInfo.InvariantCulture);
    }

    private static string Darstellung(Spalte s, byte[] d) => s.Typ switch
    {
        0x30 or 0x34 or 0x38 or 0x7F or 0x26 => Ganzzahl(d),
        0x32 or 0x68 => d[0] != 0 ? "1" : "0",
        0x3B or 0x3E or 0x6D => d.Length == 4
            ? BitConverter.ToSingle(d).ToString("R", CultureInfo.InvariantCulture)
            : BitConverter.ToDouble(d).ToString("R", CultureInfo.InvariantCulture),
        0x37 or 0x3F or 0x6A or 0x6C => Dezimal(d, s.Skala),
        0xE7 or 0xEF => "'" + Sauber(Encoding.Unicode.GetString(d)) + "'",
        0xA7 or 0xAF => "'" + Sauber(Encoding.Latin1.GetString(d)) + "'",
        _ => Hex(d),
    };

    private static string Dezimal(byte[] d, int skala)
    {
        var betrag = new BigInteger(d.AsSpan(1), isUnsigned: true);
        var ziffern = betrag.ToString(CultureInfo.InvariantCulture).PadLeft(skala + 1, '0');
        var text = skala == 0 ? ziffern : ziffern[..^skala] + "." + ziffern[^skala..];
        return (d[0] == 0 && betrag != 0 ? "-" : "") + text;
    }

    private void Meldung(List<string> ziel, bool fehler)
    {
        var laenge = U16();
        var ende = _p + laenge;
        var nummer = (int)U32();
        var status = U8();
        var klasse = U8();
        var text = UsVarchar();
        var server = BVarchar();
        var prozedur = BVarchar();
        var zeile = (int)U32();
        _p = ende;
        ziel.Add($"{(fehler ? "Fehler" : "Info")} Nummer {nummer}, Class {klasse}, State {status}, Meldung '{Sauber(text)}', Server {(server.Length > 0 ? "vorhanden" : "leer")}, Prozedur '{Sauber(prozedur)}', Zeile {zeile}");
    }

    private void EnvChange(Antwort a)
    {
        var laenge = U16();
        var ende = _p + laenge;
        var typ = U8();
        var neu = U8();
        // Die Länge steht bei den Textwerten in Zeichen, bei den Bytewerten in Bytes (MS-TDS 2.2.7.9); nur ob ein Wert da ist, geht in den Vergleich.
        var bytesTyp = typ is 7 or 8 or 9 or 10 or 11 or 12 or 15 or 16 or 17 or 18;
        var neuBytes = bytesTyp ? neu : neu * 2;
        var alt = 0;
        if (_p + neuBytes < ende && typ != 20)
        {
            _p += neuBytes;
            alt = U8();
        }

        _p = ende;
        a.Umgebung.Add($"ENVCHANGE Typ {typ}, neuer Wert {(neu > 0 ? "vorhanden" : "leer")}, alter Wert {(alt > 0 ? "vorhanden" : "leer")}");
    }

    private static string Bits(int status)
    {
        var namen = new List<string>();
        if ((status & 0x01) != 0) namen.Add("MORE");
        if ((status & 0x02) != 0) namen.Add("ERROR");
        if ((status & 0x04) != 0) namen.Add("INXACT");
        if ((status & 0x10) != 0) namen.Add("COUNT");
        if ((status & 0x20) != 0) namen.Add("ATTN");
        if ((status & 0x100) != 0) namen.Add("SRVERROR");
        var rest = status & ~(0x01 | 0x02 | 0x04 | 0x10 | 0x20 | 0x100);
        if (rest != 0) namen.Add("0x" + rest.ToString("x", CultureInfo.InvariantCulture));
        return namen.Count == 0 ? "-" : string.Join("|", namen);
    }

    private void Done(Antwort a, string name)
    {
        var status = U16();
        var befehl = U16();
        var zeilen = U64();
        if ((status & 0x20) != 0) a.AttnQuittung = true;
        a.Fertig.Add($"{name} Status {Bits(status)}, Befehl 0x{befehl:x4}, Zeilen {((status & 0x10) != 0 ? zeilen.ToString(CultureInfo.InvariantCulture) : "-")}");
    }

    private void ReturnValue(Antwort a)
    {
        var ordinal = U16();
        var name = BVarchar();
        var status = U8();
        U32();
        var flags = U16();
        var info = TypInfo(name, (flags & 0x01) != 0);
        var wert = Wert(info);
        if (name.Length == 0 || string.Equals(name, "@handle", StringComparison.OrdinalIgnoreCase)) wert = wert == "NULL" ? "NULL" : "<vorhanden>";
        a.Rueckgabe.Add($"RETURNVALUE Ordinal {ordinal}, Name '{name}', Status {status}, Typ {Typnamen.GetValueOrDefault(info.Typ, "?")}, Wert {wert}");
    }
}
