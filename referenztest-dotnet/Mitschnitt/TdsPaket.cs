namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>Ein TDS-Paket: Kopf (8 Bytes) und Nutzlast. Paketarten nach MS-TDS 2.2.3.1.1 (Type).</summary>
internal sealed record Paket(byte Typ, byte Status, byte Nummer, byte[] Nutzlast)
{
    public bool Ende => (Status & 0x01) != 0;

    /// <summary>Das Paket mit Kopf (SPID und Fenster 0) wie auf dem Draht.</summary>
    public byte[] Bytes()
    {
        var paket = TdsPaket.Schreiben(Typ, Ende, Nummer, Nutzlast);
        paket[1] = Status;
        return paket;
    }
}

internal static class TdsPaket
{
    public const int KopfLaenge = 8;
    public const byte Batch = 1;
    public const byte Rpc = 3;
    public const byte Antwort = 4;
    public const byte Attention = 6;
    public const byte Bulk = 7;
    public const byte Verwaltung = 14;
    public const byte Login7 = 16;
    public const byte Prelogin = 18;

    /// <summary>Arten, die eine Anfrage nach der Anmeldung tragen darf; Anmeldung und Verhandlung gehören nie in einen Mitschnitt.</summary>
    public static bool IstAnfrage(byte typ) => typ is Batch or Rpc or Attention or Bulk or Verwaltung;

    public static byte[] Schreiben(byte typ, bool ende, byte nummer, ReadOnlySpan<byte> nutzlast)
    {
        var laenge = KopfLaenge + nutzlast.Length;
        var paket = new byte[laenge];
        paket[0] = typ;
        paket[1] = (byte)(ende ? 1 : 0);
        paket[2] = (byte)(laenge >> 8);
        paket[3] = (byte)laenge;
        paket[6] = nummer;
        nutzlast.CopyTo(paket.AsSpan(KopfLaenge));
        return paket;
    }

    /// <summary>Zerlegt eine Nachricht in Pakete der Gesamtgröße <paramref name="paketGroesse"/>; das letzte trägt das Ende-Bit.</summary>
    public static IEnumerable<byte[]> Zerlegen(byte typ, byte[] nachricht, int paketGroesse)
    {
        var nutzlastGroesse = paketGroesse - KopfLaenge;
        byte nummer = 1;
        var offset = 0;
        do
        {
            var teil = Math.Min(nutzlastGroesse, nachricht.Length - offset);
            var ende = offset + teil >= nachricht.Length;
            yield return Schreiben(typ, ende, nummer++, nachricht.AsSpan(offset, teil));
            offset += teil;
        }
        while (offset < nachricht.Length);
    }

    /// <summary>Liest ein Paket; null, wenn der Strom vor dem ersten Byte endet.</summary>
    public static Paket? Lesen(Stream strom)
    {
        var kopf = new byte[KopfLaenge];
        var gelesen = 0;
        while (gelesen < KopfLaenge)
        {
            var n = strom.Read(kopf, gelesen, KopfLaenge - gelesen);
            if (n == 0)
            {
                if (gelesen == 0) return null;
                throw new EndOfStreamException("Strom endet im Paketkopf");
            }

            gelesen += n;
        }

        var laenge = (kopf[2] << 8) | kopf[3];
        if (laenge < KopfLaenge) throw new InvalidDataException($"Paketlaenge {laenge} unter dem Kopf");
        var nutzlast = new byte[laenge - KopfLaenge];
        var offset = 0;
        while (offset < nutzlast.Length)
        {
            var n = strom.Read(nutzlast, offset, nutzlast.Length - offset);
            if (n == 0) throw new EndOfStreamException("Strom endet in der Nutzlast");
            offset += n;
        }

        return new Paket(kopf[0], kopf[1], kopf[6], nutzlast);
    }
}

/// <summary>Eine Einheit des Stroms zwischen Client und Server: ein TDS-Paket oder ein TLS-Record (Inhaltsart 0x14 bis 0x17).</summary>
internal sealed record Einheit(bool Tls, byte Typ, byte Status, byte[] Roh)
{
    public byte[] Nutzlast => Tls ? Roh[5..] : Roh[TdsPaket.KopfLaenge..];

    public bool Ende => !Tls && (Status & 0x01) != 0;
}

/// <summary>
/// Zerlegt einen Bytestrom in Einheiten, ohne zu verändern, was durchgeht. Ein erstes Byte von 0x14 bis 0x17 beginnt einen
/// TLS-Record (Länge in Byte 3 und 4, Big Endian), jedes andere ein TDS-Paket (Länge in Byte 2 und 3). Das trennt in der
/// Anmeldephase mit Verschlüsselung nur der Anmeldung die Records von den Paketen; eine TDS-Paketart liegt nie in 0x14 bis 0x17.
/// </summary>
internal sealed class Abtaster
{
    private readonly List<byte> _puffer = new();

    public IReadOnlyList<Einheit> Einspeisen(byte[] daten, int anzahl)
    {
        for (var i = 0; i < anzahl; i++) _puffer.Add(daten[i]);
        var einheiten = new List<Einheit>();
        while (_puffer.Count > 0)
        {
            var tls = _puffer[0] is >= 0x14 and <= 0x17;
            int gesamt;
            if (tls)
            {
                if (_puffer.Count < 5) break;
                gesamt = 5 + ((_puffer[3] << 8) | _puffer[4]);
            }
            else
            {
                if (_puffer.Count < TdsPaket.KopfLaenge) break;
                gesamt = (_puffer[2] << 8) | _puffer[3];
                if (gesamt < TdsPaket.KopfLaenge) throw new InvalidDataException($"Paketlaenge {gesamt} unter dem Kopf");
            }

            if (_puffer.Count < gesamt) break;
            var roh = _puffer.GetRange(0, gesamt).ToArray();
            _puffer.RemoveRange(0, gesamt);
            einheiten.Add(new Einheit(tls, roh[0], tls ? (byte)0 : roh[1], roh));
        }

        return einheiten;
    }
}
