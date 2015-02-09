-- PostgreSQL doesn't support synonyms, so create views that match the SQL Server test synonyms.

CREATE VIEW test.TestTable3 AS
    SELECT * FROM test.TestTable;

CREATE VIEW test.Containers2 AS
    SELECT * FROM core.Containers;

CREATE VIEW test.ContainerAliases2 AS
    SELECT * FROM core.ContainerAliases;

CREATE VIEW test.Users2 AS
    SELECT * FROM core.Users;
