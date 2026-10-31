using Microsoft.Data.SqlClient;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Kompatibilitaet;

/// <summary>
/// Das Ziel der Kompatibilitätsläufe: der Serverprozess der Dockerfile-Stage `kompatibilitaetsserver` (Richtlinie
/// REQUIRED, Zertifikat CN=localhost). Das Ziel steht in TDSWIRE_ZIEL (host:port), das Zertifikat liegt als
/// Vertrauensanker in SSL_CERT_FILE. Jede Verbindung prüft die Kette und den Namen (HostNameInCertificate=localhost),
/// kein Test setzt TrustServerCertificate.
/// </summary>
internal static class Ziel
{
    public const string Benutzer = "alice";
    public const string Kennwort = "secret";

    public static SqlConnectionStringBuilder Zeichenfolge(SqlConnectionEncryptOption verschluesselung)
    {
        var ziel = Environment.GetEnvironmentVariable("TDSWIRE_ZIEL")
            ?? throw new InvalidOperationException("TDSWIRE_ZIEL fehlt (host:port)");
        var trenner = ziel.LastIndexOf(':');
        return new SqlConnectionStringBuilder
        {
            DataSource = $"tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]}",
            UserID = Benutzer,
            Password = Kennwort,
            Encrypt = verschluesselung,
            HostNameInCertificate = "localhost",
            Pooling = false,
            ConnectRetryCount = 0,
            ConnectTimeout = 15,
            CommandTimeout = 60,
        };
    }

    public static SqlConnectionStringBuilder Tds74() => Zeichenfolge(SqlConnectionEncryptOption.Mandatory);

    public static SqlConnectionStringBuilder Tds80() => Zeichenfolge(SqlConnectionEncryptOption.Strict);

    public static SqlConnection Oeffnen(SqlConnectionStringBuilder zeichenfolge)
    {
        var verbindung = new SqlConnection(zeichenfolge.ConnectionString);
        verbindung.Open();
        return verbindung;
    }

    public static SqlConnection Oeffnen() => Oeffnen(Tds74());

    /// <summary>Eine Zeile `messung: ...` im Testbericht; die Zeilen sind die Messwerte, die die Matrix zitiert.</summary>
    public static void Messung(ITestOutputHelper ausgabe, string text) => ausgabe.WriteLine("messung: " + text);

    public static string Beschreibung(SqlException ausnahme) =>
        $"SqlException Number {ausnahme.Number}, Class {ausnahme.Class}, State {ausnahme.State}, Meldung '{ausnahme.Message}'";
}
