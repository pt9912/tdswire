using System.Data;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Data.SqlClient;
using TdsWire.Referenztests.Mitschnitt;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Die Messreihe zu Massenladen, MERGE, Katalog und Strukturänderung sowie die Zusatzmessungen des Slice
/// slice-qa-vergleichslauf-d-messungen-bulk-merge (SPEC-045, `make vergleichslauf-d LAUF=bulk-2022|bulk-2025`, Kategorie `MessungBulk`).
/// Gleiche Methode wie <see cref="MessungWireTests"/>: je Szenario eine Verbindung des Referenzclients über den TLS-beendenden Proxy
/// (<see cref="TlsAufnahme"/>), `Encrypt=Mandatory`, `TrustServerCertificate=true` nur auf der Strecke zum Proxy, TLS 1.2; abgelegt wird
/// PRELOGIN, LOGIN7 (redigiert) und jede Nachricht (<see cref="MessDatei"/>) unter `Mitschnitt/messung/bulk-merge-katalog/&lt;Stufe&gt;/`.
/// Gegenstand: (1) `SqlBulkCopy` (Paket 0x07 nach `INSERT BULK`), (2) `MERGE … OUTPUT $action` mit Parametern, (3) die Katalogabfragen
/// von d-migrate 1.12.0 (<see cref="DMigrateTexte"/>) und die Strukturänderung, (4) `EXEC sp_unprepare` mit vielen unbekannten Nummern
/// und `curCmd`, (5) das Flag-Feld der RETURNVALUE von Ausgabewerten.
/// Grenzen: Einzelmessung je Szenario und Instanz; der Client ist `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate (der
/// Treiber sendet für `DATE`, `TIME` und `DATETIME2` im `INSERT BULK` die Typen `VARCHAR(n)`, der Referenzclient die Typen der
/// Zieltabelle); der Wortlaut des `MERGE` folgt der Sonde (`MERGE INTO … AS tgt USING (VALUES (@P0,@P1,@P2)) … OUTPUT $action;`), die
/// Klauseln dahinter sind Setzung dieses Messcodes, nicht der Mitschnitt von d-migrate; die Katalogtexte werden als Text mit dem
/// Parameter `@P0` (nvarchar) gesendet, so wie der Treiber sie sendet, aber vom Referenzclient.
/// </summary>
[Trait("Kategorie", "MessungBulk")]
public class MessungBulkTests
{
    private const string DbMessung = "tdswire_messung";
    private const string DbKatalog = "tdswire_katalog";
    private const string DbStruktur = "tdswire_struktur";
    private readonly ITestOutputHelper _ausgabe;

    public MessungBulkTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    // ---- Aufbau (nicht aufgenommen, direkt am Server; der Hash steht im Kopf jeder Datei) -------------------------------

    private static readonly string[] AufbauMessung =
    {
        "CREATE TABLE bulk_typen (Id bigint NOT NULL, Zahl int NULL, Klein smallint NULL, Flag bit NULL, Preis decimal(10,2) NULL, [Text] nvarchar(50) NULL, Datum date NULL, Uhr time NULL, Zeitpunkt datetime2 NULL, CONSTRAINT pk_bulk_typen PRIMARY KEY (Id))",
        "CREATE TABLE bulk_menge (Id bigint NOT NULL, [Text] nvarchar(20) NOT NULL, CONSTRAINT pk_bulk_menge PRIMARY KEY (Id))",
        "CREATE TABLE bulk_leer (Id bigint NOT NULL, [Text] nvarchar(20) NULL, CONSTRAINT pk_bulk_leer PRIMARY KEY (Id))",
        "CREATE TABLE bulk_typ (Id bigint NOT NULL, Zahl int NULL, CONSTRAINT pk_bulk_typ PRIMARY KEY (Id))",
        "CREATE TABLE bulk_laenge (Id bigint NOT NULL, Kurz nvarchar(5) NULL, CONSTRAINT pk_bulk_laenge PRIMARY KEY (Id))",
        "CREATE TABLE bulk_null (Id bigint NOT NULL, Pflicht nvarchar(10) NOT NULL, CONSTRAINT pk_bulk_null PRIMARY KEY (Id))",
        "CREATE TABLE bulk_schluessel (Id bigint NOT NULL, [Text] nvarchar(10) NULL, CONSTRAINT pk_bulk_schluessel PRIMARY KEY (Id))",
        "INSERT INTO bulk_schluessel (Id, [Text]) VALUES (2, N'vorhanden')",
        "CREATE TABLE merge_skip (Id bigint NOT NULL, [Name] nvarchar(10) NULL, Eintritt date NULL, CONSTRAINT pk_merge_skip PRIMARY KEY (Id))",
        "INSERT INTO merge_skip (Id, [Name], Eintritt) VALUES (1, N'alt', '2020-01-01')",
        "CREATE TABLE merge_update (Id bigint NOT NULL, [Name] nvarchar(10) NULL, Eintritt date NULL, CONSTRAINT pk_merge_update PRIMARY KEY (Id))",
        "INSERT INTO merge_update (Id, [Name], Eintritt) VALUES (1, N'alt', '2020-01-01')",
        "CREATE TABLE merge_mehrere (Id bigint NOT NULL, [Name] nvarchar(10) NULL, Eintritt date NULL, CONSTRAINT pk_merge_mehrere PRIMARY KEY (Id))",
        "INSERT INTO merge_mehrere (Id, [Name], Eintritt) VALUES (1, N'alt', '2020-01-01')",
        "CREATE TABLE merge_fehler (Id bigint NOT NULL, [Name] nvarchar(10) NULL, Eintritt date NULL, CONSTRAINT pk_merge_fehler PRIMARY KEY (Id))",
        "INSERT INTO merge_fehler (Id, [Name], Eintritt) VALUES (1, N'alt', '2020-01-01')",
    };

    private static readonly string[] AufbauKatalog =
    {
        "CREATE TABLE katalog_typen (Id bigint NOT NULL, Zahl int NULL, Klein smallint NULL, Winzig tinyint NULL, Flag bit NULL, Uhr time NULL, Uhr3 time(3) NULL, Zeitpunkt datetime2 NULL, Zeitpunkt3 datetime2(3) NULL, Datum date NULL, Preis decimal(10,2) NULL, Gleit float NULL, [Text] nvarchar(200) NULL, Lang nvarchar(max) NULL, Schmal varchar(50) NULL, Zeichen char(3) NULL, Kennung uniqueidentifier NULL, Binaer varbinary(16) NULL, CONSTRAINT pk_katalog_typen PRIMARY KEY (Id))",
        "CREATE TABLE katalog_kunde (Id int NOT NULL, Email nvarchar(100) NULL, CONSTRAINT pk_katalog_kunde PRIMARY KEY (Id), CONSTRAINT uq_katalog_kunde_email UNIQUE (Email))",
        "CREATE TABLE katalog_bestellung (Id int IDENTITY(1,1) NOT NULL, KundeId int NOT NULL, Menge int NOT NULL CONSTRAINT df_bestellung_menge DEFAULT 1, [Status] nvarchar(20) NOT NULL CONSTRAINT df_bestellung_status DEFAULT N'neu', CONSTRAINT pk_katalog_bestellung PRIMARY KEY (Id), CONSTRAINT fk_bestellung_kunde FOREIGN KEY (KundeId) REFERENCES katalog_kunde (Id) ON DELETE CASCADE, CONSTRAINT ck_bestellung_menge CHECK (Menge > 0))",
        "CREATE INDEX ix_bestellung_status ON katalog_bestellung ([Status]) INCLUDE (Menge)",
    };

    private static readonly string[] AufbauStruktur =
    {
        "CREATE TABLE [kunden] ([Id] INT NOT NULL, [Name] NVARCHAR(200) NOT NULL, CONSTRAINT [pk_kunden] PRIMARY KEY ([Id]))",
    };

    private static readonly (string Datenbank, string[] Anweisungen)[] Aufbau =
    {
        (DbMessung, AufbauMessung),
        (DbKatalog, AufbauKatalog),
        (DbStruktur, AufbauStruktur),
    };

    private static void AufbauAusfuehren()
    {
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));
        foreach (var (datenbank, anweisungen) in Aufbau)
        {
            foreach (var text in new[]
            {
                $"IF DB_ID(N'{datenbank}') IS NOT NULL BEGIN ALTER DATABASE [{datenbank}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [{datenbank}]; END",
                $"CREATE DATABASE [{datenbank}]",
                $"USE [{datenbank}]",
            })
            {
                using var befehl = new SqlCommand(text, verbindung);
                befehl.ExecuteNonQuery();
            }

            foreach (var text in anweisungen)
            {
                using var befehl = new SqlCommand(text, verbindung);
                befehl.ExecuteNonQuery();
            }

            using var zurueck = new SqlCommand("USE master", verbindung);
            zurueck.ExecuteNonQuery();
        }
    }

    private static string AufbauHash() =>
        "sha256:" + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(string.Join("\n", Aufbau.SelectMany(a => a.Anweisungen.Prepend(a.Datenbank)))))).ToLowerInvariant();

    // ---- Bausteine --------------------------------------------------------------------------------------------------

    private sealed class Lauf
    {
        public Lauf(string name, Action<string> beobachtung)
        {
            Name = name;
            Beobachtung = beobachtung;
        }

        public string Name { get; }

        public Action<string> Beobachtung { get; }
    }

    private static string Parameterwerte(SqlParameterCollection ps) =>
        string.Join(", ", ps.Cast<SqlParameter>().Where(p => p.Direction != ParameterDirection.Input)
            .Select(p => p.ParameterName + "=" + (p.Value is DBNull ? "NULL" : p.Value)));

    private static string Fehler(SqlException e) =>
        $"Ausnahme: Number {e.Number}, Class {e.Class}, State {e.State}, Fehler {e.Errors.Count}, Meldung '{e.Errors[0].Message}'";

    /// <summary>Führt einen Befehl bis zum Ende aus und beschreibt, was der Client sah: Mengen, Zeilen, die Werte der ersten Spalte der Zeilen (höchstens 5), Ausnahme und Ausgabewerte.</summary>
    private static string Ausfuehren(SqlConnection v, string text, Action<SqlParameterCollection>? parameter = null, CommandType art = CommandType.Text)
    {
        using var befehl = new SqlCommand(text, v) { CommandType = art };
        parameter?.Invoke(befehl.Parameters);
        var mengen = 0;
        var zeilen = 0;
        var erste = new List<string>();
        string ergebnis;
        try
        {
            using (var leser = befehl.ExecuteReader())
            {
                do
                {
                    mengen++;
                    while (leser.Read())
                    {
                        zeilen++;
                        if (erste.Count < 5 && leser.FieldCount > 0) erste.Add(leser.IsDBNull(0) ? "NULL" : Convert.ToString(leser.GetValue(0), System.Globalization.CultureInfo.InvariantCulture) ?? "?");
                    }
                }
                while (leser.NextResult());
            }

            ergebnis = $"Mengen {mengen}, Zeilen {zeilen}{(erste.Count > 0 ? " [" + string.Join("|", erste) + "]" : string.Empty)}, ohne Ausnahme";
        }
        catch (SqlException e)
        {
            ergebnis = $"Mengen {mengen}, Zeilen {zeilen}, " + Fehler(e);
        }

        var ausgabe = Parameterwerte(befehl.Parameters);
        return $"{ergebnis}; Verbindung {v.State}{(ausgabe.Length > 0 ? "; Parameter " + ausgabe : string.Empty)}";
    }

    private static void Mit(Lauf l, SqlConnection v, string beschreibung, string text, Action<SqlParameterCollection>? parameter = null, CommandType art = CommandType.Text) =>
        l.Beobachtung($"{l.Name} {beschreibung}: {Ausfuehren(v, text, parameter, art)}");

    private static string Kopie(SqlConnection v, string ziel, DataTable quelle, int batch = 0, SqlBulkCopyOptions optionen = SqlBulkCopyOptions.CheckConstraints | SqlBulkCopyOptions.FireTriggers)
    {
        try
        {
            using var kopie = new SqlBulkCopy(v, optionen, null) { DestinationTableName = ziel, BatchSize = batch, BulkCopyTimeout = 60 };
            kopie.WriteToServer(quelle);
            return $"gelungen, RowsCopied {kopie.RowsCopied}";
        }
        catch (SqlException e)
        {
            return Fehler(e);
        }
        catch (Exception e) when (e is InvalidOperationException or InvalidCastException or FormatException or OverflowException or ArgumentException)
        {
            return $"{e.GetType().Name}: {e.Message}";
        }
    }

    private static void Bulk(Lauf l, SqlConnection v, string beschreibung, string ziel, DataTable quelle, int batch = 0) =>
        l.Beobachtung($"{l.Name} {beschreibung}: {Kopie(v, ziel, quelle, batch)}; Verbindung {v.State}");

    private static SqlParameter P(SqlParameterCollection ps, string name, SqlDbType typ, object? wert, int groesse = 0, byte genauigkeit = 0, byte skala = 0, ParameterDirection richtung = ParameterDirection.Input)
    {
        var p = groesse > 0 ? ps.Add(name, typ, groesse) : ps.Add(name, typ);
        if (genauigkeit > 0) p.Precision = genauigkeit;
        if (skala > 0) p.Scale = skala;
        p.Direction = richtung;
        p.Value = wert ?? DBNull.Value;
        return p;
    }

    // ---- MERGE (Wortlaut nach der Sonde, Klauseln dahinter Setzung dieses Messcodes) ---------------------------------

    private const string MergeSkip =
        "MERGE INTO [dbo].[{0}] AS tgt USING (VALUES {1}) AS src ([Id],[Name],[Eintritt]) ON tgt.[Id] = src.[Id] "
        + "WHEN NOT MATCHED THEN INSERT ([Id],[Name],[Eintritt]) VALUES (src.[Id],src.[Name],src.[Eintritt]) OUTPUT $action;";

    private const string MergeUpdate =
        "MERGE INTO [dbo].[{0}] AS tgt USING (VALUES {1}) AS src ([Id],[Name],[Eintritt]) ON tgt.[Id] = src.[Id] "
        + "WHEN MATCHED THEN UPDATE SET [Name] = src.[Name], [Eintritt] = src.[Eintritt] "
        + "WHEN NOT MATCHED THEN INSERT ([Id],[Name],[Eintritt]) VALUES (src.[Id],src.[Name],src.[Eintritt]) OUTPUT $action;";

    private static string Werte(int zeilen) =>
        string.Join(",", Enumerable.Range(0, zeilen).Select(i => $"(@P{i * 3},@P{(i * 3) + 1},@P{(i * 3) + 2})"));

    private static void MergeAusfuehren(Lauf l, SqlConnection v, string beschreibung, string vorlage, string tabelle, params (long Id, string? Name, string? Datum)[] zeilen) =>
        Mit(l, v, beschreibung, string.Format(System.Globalization.CultureInfo.InvariantCulture, vorlage, tabelle, Werte(zeilen.Length)), ps =>
        {
            for (var i = 0; i < zeilen.Length; i++)
            {
                P(ps, $"@P{i * 3}", SqlDbType.BigInt, zeilen[i].Id);
                P(ps, $"@P{(i * 3) + 1}", SqlDbType.NVarChar, zeilen[i].Name, 4000);
                P(ps, $"@P{(i * 3) + 2}", SqlDbType.Date, zeilen[i].Datum is null ? null : DateTime.Parse(zeilen[i].Datum!, System.Globalization.CultureInfo.InvariantCulture));
            }
        });

    // ---- Katalog ------------------------------------------------------------------------------------------------------

    private static void KatalogAbfrage(Lauf l, SqlConnection v, int nr, string beschreibung, string text, string? parameter) =>
        Mit(l, v, $"Katalog {nr:D2} {beschreibung}", text, ps =>
        {
            if (parameter is not null) P(ps, "@P0", SqlDbType.NVarChar, parameter, 4000);
        });

    private static void Katalog(Lauf l, SqlConnection v, string tabelle, bool schemaAbfragen)
    {
        for (var i = 0; i < DMigrateTexte.Katalog.Length; i++)
        {
            var text = DMigrateTexte.Katalog[i];
            var nachObjekt = text.Contains("OBJECT_ID( @P0 )", StringComparison.Ordinal);
            if (nachObjekt) KatalogAbfrage(l, v, i, $"{tabelle}", text, $"[dbo].[{tabelle}]");
            else if (schemaAbfragen) KatalogAbfrage(l, v, i, "Schema dbo", text, text.Contains("@P0", StringComparison.Ordinal) ? "dbo" : null);
        }
    }

    // ---- Szenarien --------------------------------------------------------------------------------------------------

    private static readonly (string Name, string Datenbank, Action<Lauf, SqlConnection> Koerper)[] Szenarien = Erzeugen();

    private static DataTable Tabelle(params (string Name, Type Typ)[] spalten)
    {
        var t = new DataTable();
        foreach (var (name, typ) in spalten) t.Columns.Add(name, typ);
        return t;
    }

    private static (string, string, Action<Lauf, SqlConnection>)[] Erzeugen()
    {
        return new (string, string, Action<Lauf, SqlConnection>)[]
        {
            // ---- Liefer-Punkt 1a: Massenladen --------------------------------------------------------------------
            ("bulk-typen-null", DbMessung, (l, v) =>
            {
                var q = Tabelle(("Id", typeof(long)), ("Zahl", typeof(int)), ("Klein", typeof(short)), ("Flag", typeof(bool)), ("Preis", typeof(decimal)),
                    ("Text", typeof(string)), ("Datum", typeof(DateTime)), ("Uhr", typeof(TimeSpan)), ("Zeitpunkt", typeof(DateTime)));
                q.Rows.Add(1L, 10, (short)5, true, 12.34m, "Äpfel", new DateTime(2026, 1, 2), new TimeSpan(13, 14, 15), new DateTime(2026, 1, 2, 13, 14, 15, 123));
                q.Rows.Add(2L, DBNull.Value, DBNull.Value, DBNull.Value, DBNull.Value, DBNull.Value, DBNull.Value, DBNull.Value, DBNull.Value);
                q.Rows.Add(3L, -7, (short)-1, false, 0m, string.Empty, new DateTime(1970, 1, 1), TimeSpan.Zero, new DateTime(1970, 1, 1));
                Bulk(l, v, "drei Zeilen mit allen Typen, eine Zeile nur NULL", "bulk_typen", q);
                Mit(l, v, "Kontrolle", "SELECT COUNT_BIG(*) FROM bulk_typen");
            }),
            ("bulk-mehrere-nachrichten", DbMessung, (l, v) =>
            {
                var q = Tabelle(("Id", typeof(long)), ("Text", typeof(string)));
                for (var i = 1; i <= 2500; i++) q.Rows.Add((long)i, "zeile " + i.ToString(System.Globalization.CultureInfo.InvariantCulture));
                Bulk(l, v, "2500 Zeilen, BatchSize 1000", "bulk_menge", q, 1000);
                Mit(l, v, "Kontrolle", "SELECT COUNT_BIG(*) FROM bulk_menge");
            }),
            ("bulk-leer", DbMessung, (l, v) =>
            {
                Bulk(l, v, "leere Quelle", "bulk_leer", Tabelle(("Id", typeof(long)), ("Text", typeof(string))));
                Mit(l, v, "Kontrolle", "SELECT COUNT_BIG(*) FROM bulk_leer");
            }),
            ("bulk-verstoss-typ", DbMessung, (l, v) =>
            {
                var q = Tabelle(("Id", typeof(long)), ("Zahl", typeof(string)));
                q.Rows.Add(1L, "7");
                q.Rows.Add(2L, "keine Zahl");
                q.Rows.Add(3L, "9");
                Bulk(l, v, "Text 'keine Zahl' in eine int-Spalte, Zeile 2 von 3", "bulk_typ", q);
                Mit(l, v, "Kontrolle", "SELECT COUNT_BIG(*) FROM bulk_typ");
                Mit(l, v, "Folgeabfrage", "SELECT 1 AS eins");
            }),
            ("bulk-verstoss-laenge", DbMessung, (l, v) =>
            {
                var q = Tabelle(("Id", typeof(long)), ("Kurz", typeof(string)));
                q.Rows.Add(1L, "ok");
                q.Rows.Add(2L, "viel zu lang fuer fuenf");
                q.Rows.Add(3L, "ok");
                Bulk(l, v, "23 Zeichen in nvarchar(5), Zeile 2 von 3", "bulk_laenge", q);
                Mit(l, v, "Kontrolle", "SELECT COUNT_BIG(*) FROM bulk_laenge");
                Mit(l, v, "Folgeabfrage", "SELECT 1 AS eins");
            }),
            ("bulk-verstoss-null", DbMessung, (l, v) =>
            {
                var q = Tabelle(("Id", typeof(long)), ("Pflicht", typeof(string)));
                q.Rows.Add(1L, "ok");
                q.Rows.Add(2L, DBNull.Value);
                Bulk(l, v, "NULL in eine NOT-NULL-Spalte, Zeile 2 von 2", "bulk_null", q);
                Mit(l, v, "Kontrolle", "SELECT COUNT_BIG(*) FROM bulk_null");
            }),
            ("bulk-verstoss-schluessel", DbMessung, (l, v) =>
            {
                var q = Tabelle(("Id", typeof(long)), ("Text", typeof(string)));
                q.Rows.Add(1L, "neu");
                q.Rows.Add(2L, "doppelt");
                q.Rows.Add(3L, "neu");
                Bulk(l, v, "Schlüssel 2 ist vorhanden, Zeile 2 von 3", "bulk_schluessel", q);
                Mit(l, v, "Kontrolle", "SELECT COUNT_BIG(*) FROM bulk_schluessel");
                Mit(l, v, "Folgeabfrage", "SELECT 1 AS eins");
            }),

            // ---- Liefer-Punkt 1b: MERGE ---------------------------------------------------------------------------
            ("merge-skip", DbMessung, (l, v) =>
            {
                MergeAusfuehren(l, v, "kein Treffer, Zeile wird eingefügt", MergeSkip, "merge_skip", (2, "neu", "2026-01-02"));
                MergeAusfuehren(l, v, "Treffer, nur NOT MATCHED im Text", MergeSkip, "merge_skip", (1, "andere", "2026-02-03"));
                MergeAusfuehren(l, v, "NULL-Parameter, kein Treffer", MergeSkip, "merge_skip", (3, null, null));
                Mit(l, v, "Kontrolle", "SELECT Id FROM merge_skip ORDER BY Id");
            }),
            ("merge-update", DbMessung, (l, v) =>
            {
                MergeAusfuehren(l, v, "kein Treffer, Zeile wird eingefügt", MergeUpdate, "merge_update", (2, "neu", "2026-01-02"));
                MergeAusfuehren(l, v, "Treffer, Zeile wird geändert", MergeUpdate, "merge_update", (1, "geaendert", "2026-02-03"));
                MergeAusfuehren(l, v, "NULL-Parameter, Treffer", MergeUpdate, "merge_update", (1, null, null));
                Mit(l, v, "Kontrolle", "SELECT Id FROM merge_update ORDER BY Id");
            }),
            ("merge-mehrere-zeilen", DbMessung, (l, v) =>
            {
                MergeAusfuehren(l, v, "drei Zeilen, ein Treffer und zwei neue", MergeUpdate, "merge_mehrere", (1, "a", "2026-01-01"), (5, "b", "2026-01-02"), (6, "c", "2026-01-03"));
                MergeAusfuehren(l, v, "zwei Zeilen, beide Treffer", MergeUpdate, "merge_mehrere", (5, "x", "2026-02-01"), (6, "y", "2026-02-02"));
                Mit(l, v, "Kontrolle", "SELECT Id FROM merge_mehrere ORDER BY Id");
            }),
            ("merge-fehler", DbMessung, (l, v) =>
            {
                MergeAusfuehren(l, v, "Text länger als nvarchar(10)", MergeUpdate, "merge_fehler", (7, "viel zu lang fuer zehn", "2026-01-01"));
                MergeAusfuehren(l, v, "derselbe Schlüssel zweimal in der Quelle", MergeUpdate, "merge_fehler", (8, "a", "2026-01-01"), (8, "b", "2026-01-02"));
                MergeAusfuehren(l, v, "Tabelle fehlt", MergeUpdate, "gibtesnicht", (9, "a", "2026-01-01"));
                Mit(l, v, "Folgeabfrage", "SELECT Id FROM merge_fehler ORDER BY Id");
            }),

            // ---- Liefer-Punkt 2: Katalog und Strukturänderung ---------------------------------------------------
            ("katalog-struktur", DbStruktur, (l, v) =>
            {
                var s = DMigrateTexte.Struktur;
                for (var i = 0; i < s.Length; i++) Mit(l, v, $"Struktur {i} (Text aus dem Mitschnitt von schema migrate)", s[i]);
                Mit(l, v, "CREATE TABLE einer vorhandenen Tabelle", s[3]);
                Mit(l, v, "ADD einer vorhandenen Spalte", s[4]);
                Mit(l, v, "CREATE TABLE ohne Schlüssel", "CREATE TABLE [ohneschluessel] ([Id] INT NOT NULL, [Text] NVARCHAR(10));");
                Mit(l, v, "ALTER TABLE einer unbekannten Tabelle", "ALTER TABLE [gibtesnicht] ADD [X] INT;");
                Mit(l, v, "ADD mehrerer Spalten", "ALTER TABLE [kunden] ADD [A] INT, [B] NVARCHAR(10);");
                Mit(l, v, "ROLLBACK-Text von d-migrate", "IF @@TRANCOUNT > 0 ROLLBACK TRAN");
                KatalogAbfrage(l, v, 2, "Spalten von produkte", DMigrateTexte.Katalog[2], "[dbo].[produkte]");
                KatalogAbfrage(l, v, 4, "Schlüssel von produkte", DMigrateTexte.Katalog[4], "[dbo].[produkte]");
                KatalogAbfrage(l, v, 2, "Spalten von kunden", DMigrateTexte.Katalog[2], "[dbo].[kunden]");
            }),
            ("katalog-abfragen", DbKatalog, (l, v) =>
            {
                Katalog(l, v, "katalog_typen", true);
                Katalog(l, v, "katalog_kunde", false);
                Katalog(l, v, "katalog_bestellung", false);
            }),

            // ---- Liefer-Punkt 3: Zusatzmessungen ---------------------------------------------------------------------
            ("zusatz-unprepare-viele", DbMessung, (l, v) =>
            {
                foreach (var n in new[] { 100, 101, 150 })
                {
                    var text = string.Concat(Enumerable.Range(0, n).Select(i => $"EXEC sp_unprepare {900000 + i};"));
                    Mit(l, v, $"Batch mit {n} unbekannten Nummern", text);
                }

                Mit(l, v, "Folgeabfrage", "SELECT 1 AS eins");
            }),
            ("zusatz-unprepare-curcmd", DbMessung, (l, v) =>
            {
                int Prepexec()
                {
                    using var befehl = new SqlCommand("sp_prepexec", v) { CommandType = CommandType.StoredProcedure };
                    var handle = befehl.Parameters.Add("@handle", SqlDbType.Int);
                    handle.Direction = ParameterDirection.InputOutput;
                    handle.Value = DBNull.Value;
                    befehl.Parameters.Add("@params", SqlDbType.NVarChar, -1).Value = "";
                    befehl.Parameters.Add("@stmt", SqlDbType.NVarChar, -1).Value = "SELECT 1";
                    using var leser = befehl.ExecuteReader();
                    while (leser.Read())
                    {
                    }

                    leser.Close();
                    return (int)handle.Value;
                }

                var h1 = Prepexec();
                Mit(l, v, "Batch EXEC sp_unprepare mit gehaltenem Handle", $"EXEC sp_unprepare {h1}");
                var h2 = Prepexec();
                Mit(l, v, "RPC sp_unprepare mit gehaltenem Handle", "sp_unprepare", ps => P(ps, "@handle", SqlDbType.Int, h2), CommandType.StoredProcedure);
                Mit(l, v, "Batch EXEC sp_unprepare mit unbekannter Nummer", "EXEC sp_unprepare 999999");
                var h3 = Prepexec();
                var h4 = Prepexec();
                Mit(l, v, "Batch mit zwei gehaltenen Handles", $"EXEC sp_unprepare {h3};EXEC sp_unprepare {h4};");
            }),
            ("zusatz-returnvalue-flag", DbMessung, (l, v) =>
            {
                var faelle = new (string Typ, SqlDbType Db, int Groesse, byte Genauigkeit, byte Skala, string Wert)[]
                {
                    ("int", SqlDbType.Int, 0, 0, 0, "7"),
                    ("smallint", SqlDbType.SmallInt, 0, 0, 0, "7"),
                    ("bigint", SqlDbType.BigInt, 0, 0, 0, "5000000000"),
                    ("bit", SqlDbType.Bit, 0, 0, 0, "1"),
                    ("decimal(10,2)", SqlDbType.Decimal, 0, 10, 2, "12.34"),
                    ("float", SqlDbType.Float, 0, 0, 0, "1.5"),
                    ("nvarchar(50)", SqlDbType.NVarChar, 50, 0, 0, "N'abc'"),
                    ("date", SqlDbType.Date, 0, 0, 0, "'2026-01-02'"),
                    ("time", SqlDbType.Time, 0, 0, 7, "'13:14:15'"),
                    ("datetime2", SqlDbType.DateTime2, 0, 0, 7, "'2026-01-02T13:14:15.123'"),
                };
                foreach (var f in faelle)
                {
                    Mit(l, v, $"{f.Typ} OUTPUT mit Wert", $"SET @o = {f.Wert}", ps => P(ps, "@o", f.Db, null, f.Groesse, f.Genauigkeit, f.Skala, ParameterDirection.Output));
                    Mit(l, v, $"{f.Typ} OUTPUT mit NULL gesetzt", "SET @o = NULL", ps => P(ps, "@o", f.Db, null, f.Groesse, f.Genauigkeit, f.Skala, ParameterDirection.Output));
                }

                Mit(l, v, "int OUTPUT nicht zugewiesen", "SELECT 1 AS eins", ps => P(ps, "@o", SqlDbType.Int, null, 0, 0, 0, ParameterDirection.Output));
                Mit(l, v, "nvarchar(50) OUTPUT nicht zugewiesen", "SELECT 1 AS eins", ps => P(ps, "@o", SqlDbType.NVarChar, null, 50, 0, 0, ParameterDirection.Output));
                Mit(l, v, "int INOUT mit NULL, nicht zugewiesen", "SELECT 1 AS eins", ps => P(ps, "@o", SqlDbType.Int, null, 0, 0, 0, ParameterDirection.InputOutput));
                Mit(l, v, "int INOUT mit Wert 4, auf NULL gesetzt", "SET @o = NULL", ps => P(ps, "@o", SqlDbType.Int, 4, 0, 0, 0, ParameterDirection.InputOutput));
            }),
        };
    }

    // ---- Zählung der ERROR-Tokens je Austausch und direkte Zählung großer Stufen ---------------------------------------

    private static IEnumerable<string> FehlerZaehlung(MessDatei datei)
    {
        var nr = 0;
        var fehler = 0;
        var aktiv = false;
        foreach (var n in datei.Nachrichten)
        {
            if (n.Richtung == 'c' && n.Typ != TdsPaket.Prelogin && n.Typ != TdsPaket.Login7)
            {
                if (aktiv) yield return $"{datei.Szenario}/{nr:D2}/fehler-token-anzahl\t{fehler}";
                nr++;
                fehler = 0;
                aktiv = true;
            }
            else if (n.Richtung == 's' && aktiv)
            {
                fehler += TokenLeser.Lesen(n.Daten).Fehler.Count;
            }
        }

        if (aktiv) yield return $"{datei.Szenario}/{nr:D2}/fehler-token-anzahl\t{fehler}";
    }

    private void DirekteZaehlung()
    {
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));
        foreach (var n in new[] { 1, 50, 99, 100, 101, 102, 150, 200, 500, 1000, 2000 })
        {
            var text = string.Concat(Enumerable.Range(0, n).Select(i => $"EXEC sp_unprepare {800000 + i};"));
            using var befehl = new SqlCommand(text, verbindung);
            try
            {
                befehl.ExecuteNonQuery();
                ReferenzZiel.Messung(_ausgabe, $"unprepare-direkt {n} unbekannte Nummern: ohne Ausnahme");
            }
            catch (SqlException e)
            {
                var nummern = e.Errors.Cast<SqlError>().Select(f => f.Number).Distinct().OrderBy(x => x);
                ReferenzZiel.Messung(_ausgabe, $"unprepare-direkt {n} unbekannte Nummern: Fehler {e.Errors.Count}, Nummern {string.Join(",", nummern)}, Class {e.Class}, State {e.State}, Verbindung {verbindung.State}");
            }
        }
    }

    private static string Herkunft(string name) => Environment.GetEnvironmentVariable(name) ?? "nicht angegeben";

    private static string ServerName()
    {
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));
        using var befehl = new SqlCommand("SELECT @@SERVERNAME", verbindung);
        return (string)befehl.ExecuteScalar()!;
    }

    // @spec SPEC-045
    [Fact]
    public void Die_Messreihe_Bulk_Merge_Katalog_laeuft_ueber_den_TlsProxy_und_die_Ablage_ist_frei_von_Zugangsdaten()
    {
        var ausgabe = MitschnittZiel.Umgebung("TDSWIRE_MESSUNG_AUSGABE");
        var kennwort = ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT");
        var (host, port) = MitschnittZiel.Ziel();
        Directory.CreateDirectory(ausgabe);
        AufbauAusfuehren();
        var servername = ServerName();
        var rechner = Environment.MachineName;
        var kennungen = new HashSet<string>();
        var nur = (Environment.GetEnvironmentVariable("TDSWIRE_MESSUNG_NUR") ?? string.Empty).Split(',', StringSplitOptions.RemoveEmptyEntries);
        if (nur.Length == 0 || nur.Contains("zusatz-unprepare-viele")) DirekteZaehlung();

        foreach (var (name, datenbank, koerper) in Szenarien)
        {
            Assert.True(kennungen.Add(name), "doppeltes Szenario " + name);
            if (nur.Length > 0 && !nur.Contains(name)) continue;
            using var aufnahme = new TlsAufnahme(host, port, "localhost");
            var beobachtungen = new List<string>();
            var lauf = new Lauf(name, beobachtungen.Add);
            string packetSize;
            using var verbindung = new SqlConnection(new SqlConnectionStringBuilder
            {
                DataSource = $"tcp:127.0.0.1,{aufnahme.Port}",
                UserID = ReferenzZiel.Benutzer,
                Password = kennwort,
                Encrypt = SqlConnectionEncryptOption.Mandatory,
                TrustServerCertificate = true,
                Pooling = false,
                ConnectRetryCount = 0,
                ConnectTimeout = 30,
                CommandTimeout = 120,
                InitialCatalog = datenbank,
            }.ConnectionString);
            try
            {
                verbindung.Open();
            }
            catch (SqlException e)
            {
                _ausgabe.WriteLine($"messung: client {name} Open scheitert: Number {e.Number}; Proxy Problem: {aufnahme.Problem ?? "-"}");
                throw;
            }

            packetSize = verbindung.PacketSize.ToString(System.Globalization.CultureInfo.InvariantCulture);
            try
            {
                koerper(lauf, verbindung);
            }
            catch (Exception e)
            {
                foreach (var b in beobachtungen) _ausgabe.WriteLine("messung: client " + b);
                _ausgabe.WriteLine($"messung: {name} abgebrochen: {e.GetType().Name}; Proxy: Problem {aufnahme.Problem ?? "-"}, erste Verbindung endete: {aufnahme.Ende ?? "laeuft"}, weitere Verbindungen {aufnahme.WeitereVerbindungen}");
                throw;
            }

            Assert.Null(aufnahme.Problem);
            var nachrichten = aufnahme.Anhalten();

            var datei = new MessDatei { Szenario = name };
            datei.Herkunft.Add(new("instanz", Herkunft("TDSWIRE_HERKUNFT_INSTANZ")));
            datei.Herkunft.Add(new("image", Herkunft("TDSWIRE_HERKUNFT_IMAGE")));
            datei.Herkunft.Add(new("client", $"Microsoft.Data.SqlClient {typeof(SqlConnection).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion?.Split('+')[0]} auf {RuntimeInformation.FrameworkDescription}"));
            datei.Herkunft.Add(new("schema", "Aufbau der Messreihe " + AufbauHash() + ", Datenbank " + datenbank));
            datei.Herkunft.Add(new("methode", $"TLS-beendender Proxy im Container des Clients, Encrypt=Mandatory, TLS 1.2, ConnectRetryCount=0, Pooling=aus, PacketSize {packetSize}; LOGIN7 redigiert (Rechnername, Benutzer, Kennwort, Servername, SSPI, ClientID, Prozess-ID)"));
            datei.Herkunft.Add(new("aufnahme", DateTime.UtcNow.ToString("yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture)));
            datei.Nachrichten.AddRange(nachrichten.Select(n => n.Richtung == 's' ? n with { Daten = Redaktion.NameErsetzen(n.Daten, servername) } : n));
            Assert.True(datei.Nachrichten.Count >= 4, $"{name}: weniger als vier Nachrichten");
            Assert.Contains(datei.Nachrichten, n => n.Typ == TdsPaket.Login7);
            Assert.True(datei.Nachrichten.Sum(n => n.Daten.Length) <= 512 * 1024, $"{name}: die Messung ist groesser als 512 KiB");
            Assert.Null(datei.Treffer(kennwort, servername, rechner));
            var text = datei.Serialisieren();
            _ = MessDatei.Lesen(text);
            File.WriteAllText(Path.Combine(ausgabe, name + ".messung"), text, new UTF8Encoding(false));
            foreach (var b in beobachtungen) _ausgabe.WriteLine("messung: client " + b);
            foreach (var (kennung, wert) in MessAuswertung.Zeilen(datei))
                _ausgabe.WriteLine($"messung: {kennung}\t{(wert.Length > 1500 ? wert[..1500] + "..." : wert)}");
            foreach (var z in FehlerZaehlung(datei)) _ausgabe.WriteLine("messung: " + z);
            _ausgabe.WriteLine($"messung: {name} Groesse der Nachrichten {datei.Nachrichten.Sum(n => n.Daten.Length)} Bytes");
            SqlConnection.ClearAllPools();
        }
    }
}
