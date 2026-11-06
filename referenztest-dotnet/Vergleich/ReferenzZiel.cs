using Microsoft.Data.SqlClient;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Das Ziel des Vergleichslaufs D (`make vergleichslauf-d`, ADR-0008): ein echter SQL Server im Container, nicht der
/// Serverprozess dieses Repos. Ziel, Kennwort des Benutzers `sa` und die Erwartung für `Encrypt=Strict` kommen aus der
/// Umgebung (TDSWIRE_ZIEL als host:port, TDSWIRE_REFERENZ_KENNWORT, TDSWIRE_ERWARTE_STRICT). Das Zertifikat des Servers
/// liegt als Vertrauensanker in SSL_CERT_FILE (Name localhost); außer der Messreihe am Draht (`MessungWireTests.cs`, nur auf der Strecke zum Proxy) setzt kein Test TrustServerCertificate.
/// Eigene Klasse statt `Kompatibilitaet/Ziel.cs`: das Ziel dort hält Benutzer und Kennwort des Testservers fest.
/// </summary>
internal static class ReferenzZiel
{
    public const string Benutzer = "sa";
    public const string UnbekannterBenutzer = "tdswire_unbekannt";

    public static string Umgebung(string name) =>
        Environment.GetEnvironmentVariable(name) ?? throw new InvalidOperationException(name + " fehlt");

    public static SqlConnectionStringBuilder Zeichenfolge(SqlConnectionEncryptOption verschluesselung, string benutzer, string kennwort)
    {
        var ziel = Umgebung("TDSWIRE_ZIEL");
        var trenner = ziel.LastIndexOf(':');
        return new SqlConnectionStringBuilder
        {
            DataSource = $"tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]}",
            UserID = benutzer,
            Password = kennwort,
            Encrypt = verschluesselung,
            HostNameInCertificate = "localhost",
            Pooling = false,
            ConnectRetryCount = 0,
            ConnectTimeout = 15,
            CommandTimeout = 60,
        };
    }

    public static SqlConnectionStringBuilder Tds74(string benutzer, string kennwort) =>
        Zeichenfolge(SqlConnectionEncryptOption.Mandatory, benutzer, kennwort);

    public static SqlConnectionStringBuilder Tds80() =>
        Zeichenfolge(SqlConnectionEncryptOption.Strict, Benutzer, Umgebung("TDSWIRE_REFERENZ_KENNWORT"));

    public static SqlConnection Oeffnen(SqlConnectionStringBuilder zeichenfolge)
    {
        var verbindung = new SqlConnection(zeichenfolge.ConnectionString);
        verbindung.Open();
        return verbindung;
    }

    /// <summary>Eine Zeile `messung: ...` im Testbericht; das Skript des Laufs gibt sie aus, die Ausnahmeliste zitiert sie.</summary>
    public static void Messung(ITestOutputHelper ausgabe, string text) => ausgabe.WriteLine("messung: " + text);

    public static string Beschreibung(SqlException ausnahme) =>
        $"SqlException Number {ausnahme.Number}, Class {ausnahme.Class}, State {ausnahme.State}, Meldung '{ausnahme.Message}'";
}
