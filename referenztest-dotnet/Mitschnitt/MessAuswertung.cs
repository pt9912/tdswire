using System.Globalization;
using System.Text;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Liest eine <see cref="MessDatei"/> aus und gibt je Feld eine Zeile `Kennung TAB Wert` aus (SPEC-045): `Szenario/NN/Feld`, NN
/// zählt die Austausche (00 ist die Anmeldung, danach eine Anfrage und alles bis zur nächsten). Die Felder der Antwort kommen
/// aus <see cref="Ergebnis"/> (dieselbe Abbildung wie im Vergleich), dazu `anfrage` (Paketart, Status mit RESETCONNECTION, Text oder
/// Aufruf mit Parametern in der Reihenfolge der Aufrufliste), `einzelheiten` (Werte, die der Vergleich nicht führt: LOGINACK,
/// ENVCHANGE mit Werten, SESSIONSTATE, FEATUREEXTACK) und für die Anmeldung PRELOGIN-Optionen und die Zusammenfassung von LOGIN7.
/// Grenze: der RPC-Zerleger kennt nur die Parametertypen int und nvarchar (NVARCHAR-PLP eingeschlossen); bei einem anderen endet die
/// Liste mit dem Hinweis `(weitere Parameter nicht zerlegt)`.
/// </summary>
internal static class MessAuswertung
{
    private static readonly Dictionary<int, string> ProzedurNamen = new()
    {
        [1] = "sp_Cursor", [2] = "sp_CursorOpen", [3] = "sp_CursorPrepare", [4] = "sp_CursorExecute", [5] = "sp_CursorPrepExec",
        [6] = "sp_CursorUnprepare", [7] = "sp_CursorFetch", [8] = "sp_CursorOption", [9] = "sp_CursorClose", [10] = "sp_ExecuteSql",
        [11] = "sp_Prepare", [12] = "sp_Execute", [13] = "sp_PrepExec", [14] = "sp_PrepExecRpc", [15] = "sp_Unprepare",
    };

    public static IReadOnlyList<(string Kennung, string Text)> Zeilen(MessDatei datei)
    {
        var zeilen = new List<(string, string)>();
        string K(int nr, string feld) => $"{datei.Szenario}/{nr:D2}/{feld}";
        var nachrichten = datei.Nachrichten;
        var i = 0;

        // 00: Anmeldung. PRELOGIN-Anfrage und -Antwort, LOGIN7, Antwort der Anmeldung (bis zur ersten Anfrage nach LOGIN7).
        if (i < nachrichten.Count && nachrichten[i].Typ == TdsPaket.Prelogin)
        {
            zeilen.Add((K(0, "prelogin-client"), PreloginOptionen(nachrichten[i].Daten)));
            i++;
            if (i < nachrichten.Count && nachrichten[i].Richtung == 's')
            {
                zeilen.Add((K(0, "prelogin-server"), PreloginOptionen(nachrichten[i].Daten)));
                i++;
            }
        }

        if (i < nachrichten.Count && nachrichten[i].Typ == TdsPaket.Login7)
        {
            zeilen.Add((K(0, "login7"), Login7Redaktion.Zusammenfassung(nachrichten[i].Daten)));
            i++;
            var antworten = new List<byte[]>();
            while (i < nachrichten.Count && nachrichten[i].Richtung == 's') antworten.Add(nachrichten[i++].Daten);
            zeilen.AddRange(Ergebnis.Zeilen(datei.Szenario, 0, antworten, 0).Where(z => !z.Kennung.EndsWith("/abspielen", StringComparison.Ordinal)));
            zeilen.Add((K(0, "einzelheiten"), Einzelheiten(antworten)));
        }

        var nummer = 0;
        while (i < nachrichten.Count)
        {
            var anfrage = nachrichten[i++];
            if (anfrage.Richtung != 'c') continue;
            nummer++;
            var antworten = new List<byte[]>();
            var attention = new List<int>();
            while (i < nachrichten.Count && (nachrichten[i].Richtung == 's' || nachrichten[i].Typ == TdsPaket.Attention))
            {
                if (nachrichten[i].Richtung == 's') antworten.Add(nachrichten[i].Daten);
                else attention.Add(nachrichten[i].Nach);
                i++;
            }

            zeilen.Add((K(nummer, "anfrage"), Anfrage(anfrage)));
            zeilen.AddRange(Ergebnis.Zeilen(datei.Szenario, nummer, antworten, attention.Count).Where(z => !z.Kennung.EndsWith("/abspielen", StringComparison.Ordinal)));
            zeilen.Add((K(nummer, "einzelheiten"), Einzelheiten(antworten)));
        }

        return zeilen;
    }

    private static string Einzelheiten(IEnumerable<byte[]> antworten)
    {
        var alle = antworten.SelectMany(a => TokenLeser.Lesen(a).Einzelheiten).ToList();
        return alle.Count == 0 ? "-" : string.Join(" ; ", alle);
    }

    public static string PreloginOptionen(byte[] daten)
    {
        var optionen = new List<string>();
        var i = 0;
        while (i + 5 <= daten.Length && daten[i] != 0xFF)
        {
            var offset = (daten[i + 1] << 8) | daten[i + 2];
            var laenge = (daten[i + 3] << 8) | daten[i + 4];
            var wert = offset + laenge <= daten.Length ? "0x" + Convert.ToHexString(daten, offset, laenge).ToLowerInvariant() : "ausserhalb";
            optionen.Add($"{daten[i]}={wert}");
            i += 5;
        }

        return string.Join(" ", optionen);
    }

    public static string Anfrage(Nachricht n)
    {
        var kopf = $"Art {n.Typ} Status 0x{n.Status:x2}{(n.ResetConnection ? " RESETCONNECTION" : string.Empty)}";
        switch (n.Typ)
        {
            case TdsPaket.Attention:
                return $"{kopf} nach {n.Nach} Antwortpaketen";
            case TdsPaket.Batch:
                return $"{kopf}: {Kuerzen(Sauber(BatchText(n.Daten)), 160)}";
            case TdsPaket.Rpc:
                return $"{kopf}: {Rpc(n.Daten)}";
            default:
                return kopf;
        }
    }

    private static string Sauber(string t) => t.Replace("\r", " ", StringComparison.Ordinal).Replace("\n", " ", StringComparison.Ordinal).Replace("\t", " ", StringComparison.Ordinal);

    private static string Kuerzen(string t, int n) => t.Length > n ? t[..n] + "..." : t;

    private static int KopfAbstand(byte[] d) => d.Length >= 4 ? (int)BitConverter.ToUInt32(d, 0) : 0;

    public static string BatchText(byte[] daten)
    {
        var abstand = KopfAbstand(daten);
        return abstand > daten.Length ? "(Kopf ausserhalb)" : Encoding.Unicode.GetString(daten, abstand, (daten.Length - abstand) / 2 * 2);
    }

    public static string Rpc(byte[] d)
    {
        var p = KopfAbstand(d);
        if (p + 2 > d.Length) return "(zu kurz)";
        string name;
        var laenge = d[p] | (d[p + 1] << 8);
        p += 2;
        if (laenge == 0xFFFF)
        {
            var id = d[p] | (d[p + 1] << 8);
            p += 2;
            name = ProzedurNamen.GetValueOrDefault(id, "Prozedur#" + id.ToString(CultureInfo.InvariantCulture)) + $"(#{id})";
        }
        else
        {
            name = Encoding.Unicode.GetString(d, p, laenge * 2);
            p += laenge * 2;
        }

        p += 2; // Optionsbits der Prozedur
        var parameter = new List<string>();
        var nr = 0;
        try
        {
            while (p < d.Length)
            {
                var nl = d[p++];
                var pname = Encoding.Unicode.GetString(d, p, nl * 2);
                p += nl * 2;
                var status = d[p++];
                var typ = d[p++];
                string beschreibung;
                if (typ == 0x26)
                {
                    var max = d[p++];
                    var l = d[p++];
                    beschreibung = l == 0 ? "int NULL" : "int " + (max == l ? Ganz(d, p, l) : "?");
                    p += l;
                }
                else if (typ == 0xE7)
                {
                    var max = d[p] | (d[p + 1] << 8);
                    p += 2 + 5;
                    int l;
                    if (max == 0xFFFF)
                    {
                        var gesamt = BitConverter.ToUInt64(d, p);
                        p += 8;
                        l = 0;
                        while (true)
                        {
                            var teil = (int)BitConverter.ToUInt32(d, p);
                            p += 4;
                            if (teil == 0) break;
                            l += teil;
                            p += teil;
                        }

                        beschreibung = gesamt == ulong.MaxValue ? "nvarchar NULL" : $"nvarchar(max) {l / 2} Zeichen";
                    }
                    else
                    {
                        l = d[p] | (d[p + 1] << 8);
                        p += 2;
                        beschreibung = l == 0xFFFF ? "nvarchar NULL" : $"nvarchar {l / 2} Zeichen";
                        if (l != 0xFFFF) p += l;
                    }
                }
                else
                {
                    parameter.Add("(weitere Parameter nicht zerlegt)");
                    break;
                }

                parameter.Add($"[{nr++}]{(pname.Length > 0 ? pname : "-")} {((status & 1) != 0 ? "OUTPUT " : string.Empty)}{((status & 2) != 0 ? "DEFAULT " : string.Empty)}{beschreibung}");
            }
        }
        catch (Exception e) when (e is IndexOutOfRangeException or ArgumentException)
        {
            parameter.Add("(Parameter endet unerwartet)");
        }

        return name + " " + string.Join(" | ", parameter);
    }

    private static string Ganz(byte[] d, int p, int l) => l switch
    {
        1 => d[p].ToString(CultureInfo.InvariantCulture),
        2 => BitConverter.ToInt16(d, p).ToString(CultureInfo.InvariantCulture),
        4 => BitConverter.ToInt32(d, p).ToString(CultureInfo.InvariantCulture),
        _ => BitConverter.ToInt64(d, p).ToString(CultureInfo.InvariantCulture),
    };
}
