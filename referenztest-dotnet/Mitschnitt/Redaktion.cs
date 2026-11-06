using System.Security.Cryptography;
using System.Text;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Prüfung, dass ein Geheimnis (Kennwort) und seine Prüfwerte in keiner Ablage stehen. Die Aufnahme schließt die Anmeldung
/// bauartbedingt aus (siehe <see cref="Mitschreiber"/>); diese Prüfung ist die zweite, unabhängige Hälfte: sie sucht das
/// Geheimnis in den Bytes der Ablage in den Formen, in denen es auf dem Draht oder in einer Datei stehen kann.
/// Gesucht wird: UTF-8, UTF-16LE, die Verschleierung von LOGIN7 (je Byte Nibbles tauschen, XOR 0xA5, MS-TDS 2.2.6.4) und
/// die Hex-, Base64- und Hash-Formen (SHA-1, SHA-256, SHA-512 als Hex) von UTF-8 und UTF-16LE. Grenze: jede andere
/// Umkodierung, die niemand bedacht hat, bleibt ungeprüft.
/// </summary>
internal static class Redaktion
{
    public static byte[] Verschleiert(byte[] utf16le) =>
        utf16le.Select(b => (byte)((((b & 0x0F) << 4) | (b >> 4)) ^ 0xA5)).ToArray();

    public static IReadOnlyList<byte[]> Formen(string geheimnis)
    {
        if (string.IsNullOrEmpty(geheimnis)) throw new ArgumentException("leeres Geheimnis", nameof(geheimnis));
        var utf8 = Encoding.UTF8.GetBytes(geheimnis);
        var utf16 = Encoding.Unicode.GetBytes(geheimnis);
        var formen = new List<byte[]> { utf8, utf16, Verschleiert(utf16) };
        foreach (var quelle in new[] { utf8, utf16 })
        {
            formen.Add(Encoding.ASCII.GetBytes(Convert.ToBase64String(quelle)));
            formen.Add(Encoding.ASCII.GetBytes(Convert.ToHexString(quelle).ToLowerInvariant()));
            formen.Add(Encoding.ASCII.GetBytes(Convert.ToHexString(quelle)));
            foreach (var hash in new[] { SHA1.HashData(quelle), SHA256.HashData(quelle), SHA512.HashData(quelle) })
            {
                formen.Add(hash);
                formen.Add(Encoding.ASCII.GetBytes(Convert.ToHexString(hash).ToLowerInvariant()));
                formen.Add(Encoding.ASCII.GetBytes(Convert.ToHexString(hash)));
            }
        }

        return formen;
    }

    public static bool Enthaelt(byte[] daten, string geheimnis) =>
        Formen(geheimnis).Any(form => daten.AsSpan().IndexOf(form) >= 0);

    /// <summary>
    /// Prüft den Text einer Mitschnittdatei: die Textform selbst (Kopfzeilen) und die Nutzlasten der Zeilen `anfrage` und
    /// `antwort` nach dem Zurückwandeln aus Hex. Gibt die Beschreibung des ersten Treffers zurück oder null.
    /// </summary>
    public static string? Treffer(string dateiText, string geheimnis)
    {
        if (Enthaelt(Encoding.UTF8.GetBytes(dateiText), geheimnis)) return "in der Textform der Datei";
        foreach (var zeile in dateiText.Split('\n'))
        {
            var hex = zeile.StartsWith("anfrage ", StringComparison.Ordinal) ? zeile[(zeile.LastIndexOf(' ') + 1)..]
                : zeile.StartsWith("antwort ", StringComparison.Ordinal) ? zeile["antwort ".Length..]
                : null;
            if (hex == null) continue;
            if (Enthaelt(Convert.FromHexString(hex), geheimnis)) return "in der Nutzlast der Zeile " + zeile[..Math.Min(zeile.Length, 12)];
        }

        return null;
    }
}
