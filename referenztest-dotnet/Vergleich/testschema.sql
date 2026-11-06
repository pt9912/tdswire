-- Testschema der Referenzinstanz fuer den Live-Lauf (SPEC-045, `make vergleichslauf-d LAUF=live-2022|live-2025`).
-- Gegenstueck zu den Tabellen des Simulations-Backends in testserver/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsServer.kt
-- (person, typen, breit, kaputt, laufend); beide werden getrennt gepflegt und gemeinsam geaendert (SPEC-045). Die Typen
-- folgen der Typabbildung des Servers (SPEC-003): Int32 -> int, Int64 -> bigint, Bool -> bit, Unicode-Text ->
-- nvarchar(4000), Decimal(10,2) -> decimal(10,2), Date -> date, Time -> time(7), DateTime -> datetime2(7).
-- Der Client fuehrt die Datei in Staplen aus, getrennt durch Zeilen `GO`; sie enthaelt nur Testdaten.
CREATE DATABASE tdswire_live;
GO
USE tdswire_live;
GO
CREATE TABLE person (
  Id int NOT NULL PRIMARY KEY,
  Name nvarchar(4000) NULL,
  Age int NULL,
  Score decimal(10,2) NULL);
INSERT INTO person (Id, Name, Age, Score) VALUES (1, N'Ada', 36, 10.50), (2, N'Bob', NULL, 7.25), (3, N'Cy', 36, NULL);
GO
CREATE TABLE typen (
  Id int NOT NULL PRIMARY KEY,
  Gross bigint NULL,
  Flag bit NULL,
  [Text] nvarchar(4000) NULL,
  Preis decimal(10,2) NULL,
  Tag date NULL,
  Uhr time(7) NULL,
  Zeit datetime2(7) NULL);
INSERT INTO typen (Id, Gross, Flag, [Text], Preis, Tag, Uhr, Zeit) VALUES
  (1, 9000000000, 1, N'ü€😀', 12345678.90, '2026-10-31', '13:45:59.123', '2026-10-31T13:45:59.123'),
  (2, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
GO
CREATE TABLE breit (
  Id int NOT NULL PRIMARY KEY,
  [Text] nvarchar(4000) NULL);
INSERT INTO breit (Id, [Text])
  SELECT n, REPLICATE(N'y', 600) FROM (VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10)) AS z(n);
GO
CREATE TABLE kaputt_basis (
  Id int NOT NULL PRIMARY KEY);
INSERT INTO kaputt_basis (Id) VALUES (1), (2), (3), (4);
GO
-- Die Ansicht liefert drei Zeilen und scheitert an der vierten (Division durch null in der Zeile mit Id 4).
CREATE VIEW kaputt AS
  SELECT Id, CASE WHEN Id <= 3 THEN N'zeile' ELSE CONVERT(nvarchar(10), 1 / (Id - 4)) END AS [Text]
  FROM kaputt_basis;
GO
-- Die Ansicht liefert Zeilen ohne praktisches Ende; die Anfrage endet durch den Abbruch des Clients.
CREATE VIEW laufend AS
  SELECT CONVERT(int, ROW_NUMBER() OVER (ORDER BY (SELECT NULL))) AS Id, CONVERT(nvarchar(4000), N'zeile') AS [Text]
  FROM sys.all_objects AS a CROSS JOIN sys.all_objects AS b CROSS JOIN sys.all_objects AS c;
GO
