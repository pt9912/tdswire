using System.Data.Common;

namespace TdsWire.Referenztests.Lasttest;

/// <summary>
/// Die Zeilen des Referenz-Workloads (S1 in docs/plan/referenz-workload.md) auf der Seite des Clients: Zeile n allein aus
/// Seed und n, derselbe SplitMix64-Erzeuger wie `ReferenzZeilen.kt` im Testserver (Kopplung: Konstanten, Reihenfolge der
/// Mischungen und Alphabet gleich; der Export-Test vergleicht die erste und letzte Zeile mit dieser Fassung). Die Typen sind
/// die der Spalten von `ref_zeilen`: BIGINT, INT, DECIMAL(12,2), BIT, DATETIME2(3), NVARCHAR(12), NVARCHAR(40), NVARCHAR(100).
/// </summary>
internal static class ReferenzZeilen
{
    public const int Spalten = 8;
    private const long Goldener = unchecked((long)0x9E3779B97F4A7C15UL);
    private const long Mix1 = unchecked((long)0xBF58476D1CE4E5B9UL);
    private const long Mix2 = unchecked((long)0x94D049BB133111EBUL);
    private const string Alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static readonly string[] Namen = { "Id", "Menge", "Betrag", "Aktiv", "Angelegt", "Code", "Name", "Notiz" };
    public static readonly Type[] Typen =
    {
        typeof(long), typeof(int), typeof(decimal), typeof(bool), typeof(DateTime), typeof(string), typeof(string), typeof(string),
    };

    private static long Mischen(long wert)
    {
        unchecked
        {
            var z = wert;
            z = (z ^ (z >>> 30)) * Mix1;
            z = (z ^ (z >>> 27)) * Mix2;
            return z ^ (z >>> 31);
        }
    }

    private static string Text(long seed, int zeichen)
    {
        var zustand = seed;
        var puffer = new char[zeichen];
        for (var i = 0; i < zeichen; i++)
        {
            zustand = Mischen(zustand);
            puffer[i] = Alphabet[(int)(((zustand % Alphabet.Length) + Alphabet.Length) % Alphabet.Length)];
        }

        return new string(puffer);
    }

    public static object?[] Zeile(long seed, long n)
    {
        unchecked
        {
            var h = Mischen(seed + (n * Goldener));
            return new object?[]
            {
                n,
                (int)(h >>> 33),
                (decimal)((h >>> 8) % 1_000_000_000_000L) / 100m,
                (h & 1L) == 1L,
                new DateTime(2020, 1, 1).AddSeconds((h >>> 12) % 31_536_000L).AddMilliseconds((h >>> 16) % 1000L),
                Text(h, 12),
                Text(Mischen(h), 40),
                n % 10 == 0 ? null : Text(Mischen(Mischen(h)), 100),
            };
        }
    }
}

/// <summary>Ein Leser über die Zeilen von 1 bis `zeilen`, jede erst beim Zug erzeugt: die Quelle von SqlBulkCopy (nichts wird gehalten).</summary>
internal sealed class ZeilenLeser : DbDataReader
{
    private readonly long _seed;
    private readonly long _zeilen;
    private long _n;
    private object?[] _aktuell = new object?[ReferenzZeilen.Spalten];

    public ZeilenLeser(long seed, long zeilen)
    {
        _seed = seed;
        _zeilen = zeilen;
    }

    public override int FieldCount => ReferenzZeilen.Spalten;
    public override bool HasRows => _zeilen > 0;
    public override bool IsClosed => false;
    public override int Depth => 0;
    public override int RecordsAffected => -1;
    public override object this[int ordinal] => GetValue(ordinal);
    public override object this[string name] => GetValue(GetOrdinal(name));

    public override bool Read()
    {
        if (_n >= _zeilen)
        {
            return false;
        }

        _aktuell = ReferenzZeilen.Zeile(_seed, ++_n);
        return true;
    }

    public override bool NextResult() => false;
    public override string GetName(int ordinal) => ReferenzZeilen.Namen[ordinal];
    public override int GetOrdinal(string name) => Array.IndexOf(ReferenzZeilen.Namen, name);
    public override Type GetFieldType(int ordinal) => ReferenzZeilen.Typen[ordinal];
    public override string GetDataTypeName(int ordinal) => ReferenzZeilen.Typen[ordinal].Name;
    public override object GetValue(int ordinal) => _aktuell[ordinal] ?? DBNull.Value;
    public override bool IsDBNull(int ordinal) => _aktuell[ordinal] is null;

    public override int GetValues(object[] values)
    {
        for (var i = 0; i < ReferenzZeilen.Spalten; i++)
        {
            values[i] = GetValue(i);
        }

        return ReferenzZeilen.Spalten;
    }

    public override bool GetBoolean(int ordinal) => (bool)_aktuell[ordinal]!;
    public override long GetInt64(int ordinal) => (long)_aktuell[ordinal]!;
    public override int GetInt32(int ordinal) => (int)_aktuell[ordinal]!;
    public override decimal GetDecimal(int ordinal) => (decimal)_aktuell[ordinal]!;
    public override DateTime GetDateTime(int ordinal) => (DateTime)_aktuell[ordinal]!;
    public override string GetString(int ordinal) => (string)_aktuell[ordinal]!;
    public override byte GetByte(int ordinal) => throw new NotSupportedException();
    public override long GetBytes(int ordinal, long dataOffset, byte[]? buffer, int bufferOffset, int length) => throw new NotSupportedException();
    public override char GetChar(int ordinal) => throw new NotSupportedException();
    public override long GetChars(int ordinal, long dataOffset, char[]? buffer, int bufferOffset, int length) => throw new NotSupportedException();
    public override double GetDouble(int ordinal) => throw new NotSupportedException();
    public override float GetFloat(int ordinal) => throw new NotSupportedException();
    public override Guid GetGuid(int ordinal) => throw new NotSupportedException();
    public override short GetInt16(int ordinal) => throw new NotSupportedException();
    public override System.Collections.IEnumerator GetEnumerator() => throw new NotSupportedException();
}
