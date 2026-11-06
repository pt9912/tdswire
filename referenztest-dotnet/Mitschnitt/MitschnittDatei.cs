using System.Globalization;
using System.Text;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Ein Ereignis des Mitschnitts. Art `A`: eine Anfrage des Clients (Paketart und Nachricht, Pakete zusammengesetzt); Art `S`:
/// ein ATTENTION des Clients, <see cref="Nach"/> ist die Zahl der Antwortpakete des Servers seit der letzten Anfrage zum
/// Zeitpunkt des ATTENTION; Art `R`: eine Antwortnachricht des Servers (Pakete bis zum Ende-Bit zusammengesetzt).
/// </summary>
internal sealed record Ereignis(char Art, byte Typ, byte[] Daten, int Nach);

/// <summary>Eine Anfrage mit allem, was bis zur nächsten Anfrage geschah.</summary>
internal sealed record Austausch(byte Typ, byte[] Anfrage, IReadOnlyList<int> AttentionNach, IReadOnlyList<byte[]> Antworten);

/// <summary>
/// Die Ablage eines Mitschnitts: Textdatei, eine Zeile je Ereignis, Nutzlasten als Hex. Kopfzeilen `# schluessel: wert`
/// tragen die Herkunft (Instanz, Client, Schema, Methode). Die Datei enthält nur Ereignisse ab der ersten Anfrage nach der
/// Anmeldung: PRELOGIN, TLS-Handschlag und LOGIN7 (mit Kennwort) gelangen nie hinein.
/// </summary>
internal sealed class MitschnittDatei
{
    public const string Kennung = "# tdswire-mitschnitt 1";

    public List<KeyValuePair<string, string>> Herkunft { get; } = new();

    public string Szenario { get; set; } = "";

    public List<Ereignis> Ereignisse { get; } = new();

    public string Serialisieren()
    {
        var sb = new StringBuilder();
        sb.Append(Kennung).Append('\n');
        foreach (var (schluessel, wert) in Herkunft) sb.Append("# ").Append(schluessel).Append(": ").Append(wert).Append('\n');
        sb.Append("szenario ").Append(Szenario).Append('\n');
        foreach (var e in Ereignisse)
        {
            switch (e.Art)
            {
                case 'A': sb.Append("anfrage ").Append(e.Typ.ToString("x2", CultureInfo.InvariantCulture)).Append(' ').Append(Convert.ToHexString(e.Daten).ToLowerInvariant()).Append('\n'); break;
                case 'S': sb.Append("attention nach=").Append(e.Nach.ToString(CultureInfo.InvariantCulture)).Append('\n'); break;
                case 'R': sb.Append("antwort ").Append(Convert.ToHexString(e.Daten).ToLowerInvariant()).Append('\n'); break;
                default: throw new InvalidOperationException("Ereignisart " + e.Art);
            }
        }

        return sb.ToString();
    }

    public static MitschnittDatei Lesen(string text)
    {
        var zeilen = text.Split('\n', StringSplitOptions.RemoveEmptyEntries);
        if (zeilen.Length == 0 || zeilen[0] != Kennung) throw new InvalidDataException("Kennung der Mitschnittdatei fehlt");
        var datei = new MitschnittDatei();
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
            else if (zeile.StartsWith("anfrage ", StringComparison.Ordinal))
            {
                var teile = zeile.Split(' ');
                if (teile.Length != 3) throw new InvalidDataException("anfrage: " + zeile);
                datei.Ereignisse.Add(new Ereignis('A', Convert.ToByte(teile[1], 16), Convert.FromHexString(teile[2]), 0));
            }
            else if (zeile.StartsWith("attention nach=", StringComparison.Ordinal))
            {
                datei.Ereignisse.Add(new Ereignis('S', TdsPaket.Attention, Array.Empty<byte>(), int.Parse(zeile["attention nach=".Length..], CultureInfo.InvariantCulture)));
            }
            else if (zeile.StartsWith("antwort ", StringComparison.Ordinal))
            {
                datei.Ereignisse.Add(new Ereignis('R', TdsPaket.Antwort, Convert.FromHexString(zeile["antwort ".Length..]), 0));
            }
            else
            {
                throw new InvalidDataException("unbekannte Zeile: " + zeile);
            }
        }

        foreach (var e in datei.Ereignisse.Where(e => e.Art == 'A' && !TdsPaket.IstAnfrage(e.Typ)))
            throw new InvalidDataException($"Anfrage der Paketart {e.Typ} gehoert nicht in einen Mitschnitt");
        return datei;
    }

    public IReadOnlyList<Austausch> Austausche()
    {
        var liste = new List<Austausch>();
        byte typ = 0;
        byte[]? anfrage = null;
        var attention = new List<int>();
        var antworten = new List<byte[]>();

        void Abschliessen()
        {
            if (anfrage != null) liste.Add(new Austausch(typ, anfrage, attention.ToArray(), antworten.ToArray()));
            attention = new List<int>();
            antworten = new List<byte[]>();
        }

        foreach (var e in Ereignisse)
        {
            switch (e.Art)
            {
                case 'A':
                    Abschliessen();
                    typ = e.Typ;
                    anfrage = e.Daten;
                    break;
                case 'S': attention.Add(e.Nach); break;
                case 'R': antworten.Add(e.Daten); break;
            }
        }

        Abschliessen();
        return liste;
    }
}

/// <summary>
/// Die Zustandsmaschine der Aufnahme, ohne Netz: nimmt die Bytes beider Richtungen entgegen und erzeugt die Ereignisse
/// ab der ersten Anfrage des Clients nach der Anmeldung. Davor ist der Strom Vorspann (PRELOGIN, TLS-Handschlag, LOGIN7,
/// Antwort der Anmeldung) und wird nicht gespeichert; die Anfrage ist die erste Einheit, die ein TDS-Paket einer Art aus
/// <see cref="TdsPaket.IstAnfrage"/> ist. Ein Strom ohne diese Einheit (vollständig verschlüsselte Sitzung) ergibt keine Ereignisse.
/// </summary>
internal sealed class Mitschreiber
{
    private readonly object _sperre = new();
    private readonly Abtaster _client = new();
    private readonly Abtaster _server = new();
    private readonly List<Ereignis> _ereignisse = new();
    private readonly MemoryStream _anfrage = new();
    private readonly MemoryStream _antwort = new();
    private byte _anfrageTyp;
    private int _antwortPakete;
    private bool _klartext;
    private bool _aktiv = true;

    public bool Klartext => _klartext;

    public void VomClient(byte[] daten, int anzahl)
    {
        lock (_sperre)
        {
            foreach (var einheit in _client.Einspeisen(daten, anzahl))
            {
                if (!_klartext)
                {
                    if (einheit.Tls || !TdsPaket.IstAnfrage(einheit.Typ)) continue;
                    _klartext = true;
                }

                if (!_aktiv) continue;
                if (einheit.Typ == TdsPaket.Attention)
                {
                    _ereignisse.Add(new Ereignis('S', TdsPaket.Attention, Array.Empty<byte>(), _antwortPakete));
                    continue;
                }

                if (_anfrage.Length == 0) _anfrageTyp = einheit.Typ;
                _anfrage.Write(einheit.Nutzlast);
                if (einheit.Ende)
                {
                    _ereignisse.Add(new Ereignis('A', _anfrageTyp, _anfrage.ToArray(), 0));
                    _anfrage.SetLength(0);
                    _antwortPakete = 0;
                }
            }
        }
    }

    public void VomServer(byte[] daten, int anzahl)
    {
        lock (_sperre)
        {
            if (!_klartext) return;
            foreach (var einheit in _server.Einspeisen(daten, anzahl))
            {
                if (!_aktiv) continue;
                _antwortPakete++;
                _antwort.Write(einheit.Nutzlast);
                if (einheit.Ende)
                {
                    _ereignisse.Add(new Ereignis('R', TdsPaket.Antwort, _antwort.ToArray(), 0));
                    _antwort.SetLength(0);
                }
            }
        }
    }

    /// <summary>Beendet die Aufnahme; was danach durchgeht (Abmeldung, Aufräumen des Clients), wird nicht gespeichert.</summary>
    public IReadOnlyList<Ereignis> Anhalten()
    {
        lock (_sperre)
        {
            _aktiv = false;
            return _ereignisse.ToArray();
        }
    }
}
